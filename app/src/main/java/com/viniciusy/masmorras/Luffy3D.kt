package com.viniciusy.masmorras

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Luffy depois do salto de tempo (chapéu de palha nas costas, cicatriz em X no peito, colete vermelho aberto,
 * faixa amarela e bermuda azul) em cubos 3D, no mesmo mini motor do Mago/Guerreiro/Pikachu:
 * z-buffer por pixel e contorno. Corpo articulado com pernas, braços, cabeça com cabelo espetado e chapéu.
 * O ataque básico é um combo de socos: direita, esquerda e os dois punhos juntos.
 */
object Luffy3D {
    private val V = VoxelArt

    private const val MAX = 1500
    private const val PITCH = 0.8f
    private const val PX = 1.6f
    private val COSP = cos(PITCH)
    private val SINP = sin(PITCH)
    private val TWO_PI = (2.0 * PI).toFloat()

    // luz vindo de cima, esquerda e frente (usada pelo esqueleto)
    private const val LX = -0.45f
    private const val LY = 0.78f
    private const val LZ = 0.43f

    // luz do herói: mais suave e presa ao corpo, para a cor não oscilar quando os membros giram
    private const val NLX = -0.602f
    private const val NLY = 0.702f
    private const val NLZ = 0.381f

    // pilha de matrizes: 9 floats de rotação (linha a linha) + 3 de translação
    private val stack = Array(12) { FloatArray(12) }
    private var top = 0

    // buffers de render
    private val scrX = FloatArray(MAX * 8)
    private val scrY = FloatArray(MAX * 8)
    private val scrD = FloatArray(MAX * 8)   // profundidade (em direção à câmera)
    private val scrH = FloatArray(MAX * 8)   // altura no mundo
    private val faceCol = IntArray(MAX * 6)
    private val faceEm = BooleanArray(MAX * 6)
    private val keys = FloatArray(MAX)
    private val order = IntArray(MAX)
    private var n = 0
    private val path = Path()

    private var rs = 1f
    private var ox = 0f
    private var oy = 0f

    // modo de render: true = z-buffer por pixel (herói), false = ordenação por cubo (esqueletos)
    private var zMode = false
    private var depthBias = 0f // somado à profundidade dos cubos (positivo = mais perto da câmera)
    private var lYc = 1f
    private var lYs = 0f

    // z-buffer do herói (renderizado em 2x e reduzido com filtro = bordas suaves)
    private const val BUF_MAX = 300000
    private const val SS_MAX = 2f
    private const val EDGE_Z = 1.0f
    private val zb = FloatArray(BUF_MAX)
    private val ib = IntArray(BUF_MAX)
    private val cb = IntArray(BUF_MAX)
    private var rW = 0
    private var rH = 0
    private var rBx = 0f
    private var rBy = 0f
    private var rSs = 1f
    private var bmp: Bitmap? = null
    private var bmpW = 0
    private var bmpH = 0
    private val srcR = Rect()
    private val dstR = RectF()
    private val bmpPaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val OFX = intArrayOf(-1, 1, 0, 0, -2, 2, 0, 0)
    private val OFY = intArrayOf(0, 0, -1, 1, 0, 0, -2, 2)

    private val AXIS = intArrayOf(0, 0, 1, 1, 2, 2)
    private val SIGN = floatArrayOf(1f, -1f, 1f, -1f, 1f, -1f)
    private val CORN = intArrayOf(
        1, 3, 7, 5, // +X
        0, 4, 6, 2, // -X
        2, 3, 7, 6, // +Y
        0, 1, 5, 4, // -Y
        4, 5, 7, 6, // +Z
        0, 2, 3, 1  // -Z
    )

    // ───────────── matrizes ─────────────

    private fun identity() {
        val m = stack[top]
        for (i in 0..11) m[i] = 0f
        m[0] = 1f
        m[4] = 1f
        m[8] = 1f
    }

    private fun push() {
        System.arraycopy(stack[top], 0, stack[top + 1], 0, 12)
        top++
    }

    private fun pop() {
        top--
    }

    private fun translate(x: Float, y: Float, z: Float) {
        val m = stack[top]
        m[9] += m[0] * x + m[1] * y + m[2] * z
        m[10] += m[3] * x + m[4] * y + m[5] * z
        m[11] += m[6] * x + m[7] * y + m[8] * z
    }

    private fun mix(i: Int, j: Int, a: Float, b: Float, c: Float, d: Float) {
        val m = stack[top]
        for (r in 0..2) {
            val ci = m[r * 3 + i]
            val cj = m[r * 3 + j]
            m[r * 3 + i] = a * ci + b * cj
            m[r * 3 + j] = c * ci + d * cj
        }
    }

    private fun scale(x: Float, y: Float, z: Float) {
        val m = stack[top]
        for (r in 0..2) {
            m[r * 3] *= x
            m[r * 3 + 1] *= y
            m[r * 3 + 2] *= z
        }
    }

    private fun rotX(a: Float) {
        val c = cos(a)
        val s = sin(a)
        mix(1, 2, c, s, -s, c)
    }

    private fun rotY(a: Float) {
        val c = cos(a)
        val s = sin(a)
        mix(0, 2, c, -s, s, c)
    }

    private fun rotZ(a: Float) {
        val c = cos(a)
        val s = sin(a)
        mix(0, 1, c, s, -s, c)
    }

    // ───────────── cubos ─────────────

    private fun cube(
        cx: Float, cy: Float, cz: Float, hx: Float, hy: Float, hz: Float,
        color: Int, emissive: Boolean = false
    ) {
        if (n >= MAX) return
        val m = stack[top]
        val base = V.tint(color)
        val o = n * 8
        for (i in 0..7) {
            val x = cx + (if ((i and 1) == 0) -hx else hx)
            val y = cy + (if (((i shr 1) and 1) == 0) -hy else hy)
            val z = cz + (if (((i shr 2) and 1) == 0) -hz else hz)
            val wy = m[3] * x + m[4] * y + m[5] * z + m[10]
            val wx = m[0] * x + m[1] * y + m[2] * z + m[9]
            val wz = m[6] * x + m[7] * y + m[8] * z + m[11]
            scrX[o + i] = ox + wx * rs
            scrY[o + i] = oy + (wz * SINP - wy * COSP) * rs
            scrD[o + i] = wy * SINP + wz * COSP + depthBias
            scrH[o + i] = wy
        }
        val cwy = m[3] * cx + m[4] * cy + m[5] * cz + m[10]
        val cwz = m[6] * cx + m[7] * cy + m[8] * cz + m[11]
        keys[n] = cwy * SINP + cwz * COSP
        for (f in 0..5) {
            val k = AXIS[f]
            val sg = SIGN[f]
            val nx = sg * m[k]
            val ny = sg * m[3 + k]
            val nz = sg * m[6 + k]
            val idx = n * 6 + f
            faceEm[idx] = emissive
            if (ny * SINP + nz * COSP > 0.001f) {
                if (emissive) {
                    faceCol[idx] = base
                } else if (zMode) {
                    // normal "de corpo": só o giro do herói, ignora o balanço dos membros
                    var lbx = 0f
                    var lby = 0f
                    var lbz = 0f
                    if (k == 0) {
                        lbx = sg * lYc
                        lbz = -sg * lYs
                    } else if (k == 1) {
                        lby = sg
                    } else {
                        lbx = sg * lYs
                        lbz = sg * lYc
                    }
                    val dl = 0.7f * (lbx * NLX + lby * NLY + lbz * NLZ) + 0.3f * (nx * NLX + ny * NLY + nz * NLZ)
                    val shd = 0.62f + 0.34f * max(0f, dl) + 0.10f * (ny * 0.5f + 0.5f)
                    faceCol[idx] = V.shade(base, shd)
                } else {
                    val dl = nx * LX + ny * LY + nz * LZ
                    faceCol[idx] = V.shade(base, 0.52f + 0.62f * max(0f, dl))
                }
            } else {
                faceCol[idx] = 0
            }
        }
        n++
    }

    // ───────────── render com z-buffer (sem peças piscando) ─────────────

    private fun ensureBmp(w: Int, h: Int): Bitmap {
        val cur = bmp
        if (cur != null && bmpW >= w && bmpH >= h) return cur
        bmpW = max(bmpW, ((w + 63) / 64) * 64)
        bmpH = max(bmpH, ((h + 63) / 64) * 64)
        val nb = Bitmap.createBitmap(bmpW, bmpH, Bitmap.Config.ARGB_8888)
        bmp = nb
        return nb
    }

    private fun tri(i0: Int, i1: Int, i2: Int, col: Int, id: Int, bias: Float, lit: Boolean) {
        val s = rSs
        val ax = (scrX[i0] - rBx) * s
        val ay = (scrY[i0] - rBy) * s
        val qx = (scrX[i1] - rBx) * s
        val qy = (scrY[i1] - rBy) * s
        val rx = (scrX[i2] - rBx) * s
        val ry = (scrY[i2] - rBy) * s
        val area = (qx - ax) * (ry - ay) - (qy - ay) * (rx - ax)
        if (abs(area) < 0.01f) return
        val inv = 1f / area
        val az = scrD[i0] + bias
        val bz = scrD[i1] + bias
        val cz = scrD[i2] + bias
        val ah = scrH[i0]
        val bh = scrH[i1]
        val ch = scrH[i2]
        val x0 = max(0, (min(ax, min(qx, rx)) - 0.5f).toInt())
        val x1 = min(rW - 1, (max(ax, max(qx, rx)) + 0.5f).toInt())
        val y0 = max(0, (min(ay, min(qy, ry)) - 0.5f).toInt())
        val y1 = min(rH - 1, (max(ay, max(qy, ry)) + 0.5f).toInt())
        val cr = (col shr 16) and 255
        val cg = (col shr 8) and 255
        val cbl = col and 255
        for (y in y0..y1) {
            val py = y + 0.5f
            val row = y * rW
            for (x in x0..x1) {
                val px = x + 0.5f
                val w0 = ((qx - px) * (ry - py) - (qy - py) * (rx - px)) * inv
                val w1 = ((rx - px) * (ay - py) - (ry - py) * (ax - px)) * inv
                val w2 = 1f - w0 - w1
                if (w0 < -0.0005f || w1 < -0.0005f || w2 < -0.0005f) continue
                val z = w0 * az + w1 * bz + w2 * cz
                val idx = row + x
                if (z > zb[idx]) {
                    zb[idx] = z
                    ib[idx] = id
                    if (lit) {
                        // escurece de leve perto do chão (oclusão ambiente barata)
                        var hf = (w0 * ah + w1 * bh + w2 * ch + 2f) * 0.0294f
                        if (hf < 0f) hf = 0f else if (hf > 1f) hf = 1f
                        val mm = 0.86f + 0.20f * hf
                        val r = min(255, (cr * mm).toInt())
                        val g = min(255, (cg * mm).toInt())
                        val b = min(255, (cbl * mm).toInt())
                        cb[idx] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
                    } else {
                        cb[idx] = col
                    }
                }
            }
        }
    }

    private fun flushZ(c: Canvas) {
        if (n == 0) return
        var minX = 1e9f
        var minY = 1e9f
        var maxX = -1e9f
        var maxY = -1e9f
        for (i in 0 until n * 8) {
            val x = scrX[i]
            val y = scrY[i]
            if (x < minX) minX = x
            if (x > maxX) maxX = x
            if (y < minY) minY = y
            if (y > maxY) maxY = y
        }
        if (!(maxX > minX) || !(maxY > minY)) return
        val bx0 = floor(minX - 3f)
        val by0 = floor(minY - 3f)
        val wpx = maxX - bx0 + 3f
        val hpx = maxY - by0 + 3f
        if (wpx > 2000f || hpx > 2000f) return
        var ss = kotlin.math.min(SS_MAX * kotlin.math.max(1f, VoxelArt.zoom), 5f)
        val lim = kotlin.math.min(BUF_MAX, VoxelArt.bufLimit)
        if (wpx * hpx * ss * ss > lim) ss = sqrt(lim.toFloat() / (wpx * hpx)) * 0.97f
        val rw = (wpx * ss).toInt() + 1
        val rh = (hpx * ss).toInt() + 1
        if (rw * rh > lim) return
        rW = rw
        rH = rh
        rBx = bx0
        rBy = by0
        rSs = ss
        val size = rw * rh
        java.util.Arrays.fill(zb, 0, size, -1e9f)
        java.util.Arrays.fill(ib, 0, size, 0)
        java.util.Arrays.fill(cb, 0, size, 0)

        // rasteriza todas as faces visíveis; a ordem de desenho só desempata faces coplanares
        for (ci in 0 until n) {
            val o = ci * 8
            val bias = ci * 0.0004f
            for (f in 0..5) {
                val col = faceCol[ci * 6 + f]
                if (col == 0) continue
                val b = f * 4
                val i0 = o + CORN[b]
                val i1 = o + CORN[b + 1]
                val i2 = o + CORN[b + 2]
                val i3 = o + CORN[b + 3]
                val id = ci * 6 + f + 1
                val lit = !faceEm[ci * 6 + f]
                tri(i0, i1, i2, col, id, bias, lit)
                tri(i0, i2, i3, col, id, bias, lit)
            }
        }

        // contorno: forte na silhueta e nos saltos de profundidade, suave nas dobras entre faces
        for (y in 0 until rh) {
            val row = y * rw
            for (x in 0 until rw) {
                val i = row + x
                val id = ib[i]
                if (id == 0) continue
                val z = zb[i]
                var strong = false
                var soft = false
                for (k in 0..7) {
                    val nx = x + OFX[k]
                    val ny = y + OFY[k]
                    var nid = 0
                    var nz = 0f
                    if (nx >= 0 && nx < rw && ny >= 0 && ny < rh) {
                        val j = ny * rw + nx
                        nid = ib[j]
                        nz = zb[j]
                    }
                    if (nid == 0) {
                        strong = true
                        break
                    }
                    if (nid != id) {
                        if (nz < z - EDGE_Z) {
                            strong = true
                            break
                        } else if (k < 4 && nid < id && nz <= z + EDGE_Z) {
                            soft = true
                        }
                    }
                }
                if (strong) {
                    cb[i] = V.shade(cb[i], 0.42f)
                } else if (soft) {
                    cb[i] = V.shade(cb[i], 0.74f)
                }
            }
        }

        val b = ensureBmp(rw, rh)
        b.setPixels(cb, 0, rw, 0, 0, rw, rh)
        srcR.set(0, 0, rw, rh)
        dstR.set(bx0, by0, bx0 + rw / ss, by0 + rh / ss)
        bmpPaint.alpha = (alphaV.coerceIn(0f, 1f) * 255f).toInt()
        c.drawBitmap(b, srcR, dstR, bmpPaint)
    }

    // ───────────── utilidades ─────────────

    private fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t

    private fun mixC(a: Int, b: Int, t: Float): Int {
        val k = t.coerceIn(0f, 1f)
        val r = ((a shr 16 and 255) * (1f - k) + (b shr 16 and 255) * k).toInt()
        val g = ((a shr 8 and 255) * (1f - k) + (b shr 8 and 255) * k).toInt()
        val bl = ((a and 255) * (1f - k) + (b and 255) * k).toInt()
        return (0xFF shl 24) or (r shl 16) or (g shl 8) or bl
    }

    /** Pinta a pele do Luffy para o Gear Second (w = 0 normal, 1 rosa-avermelhado). */
    private fun setSkin(w: Float) {
        SKIN = mixC(SKIN_B, 0xFFF2677A.toInt(), w)
        SKIN_L = mixC(SKIN_LB, 0xFFFF93A0.toInt(), w)
        SKIN_D = mixC(SKIN_DB, 0xFFD6465E.toInt(), w)
        SKIN_DD = mixC(SKIN_DDB, 0xFF9C2A42.toInt(), w)
    }

    private fun ease(t: Float): Float {
        val x = t.coerceIn(0f, 1f)
        return x * x * (3f - 2f * x)
    }

    /** Suavização de 5ª ordem: arranca e chega com velocidade e aceleração zero (volta de elástico sem tranco). */
    private fun smoother(t: Float): Float {
        val x = t.coerceIn(0f, 1f)
        return x * x * x * (x * (x * 6f - 15f) + 10f)
    }

    /**
     * Valor de um ajuste de pose numa skill: vai do repouso ao valor de preparação até o instante [rel],
     * estala para o valor do golpe e depois relaxa de volta ao repouso.
     */
    private fun ph(u: Float, rel: Float, idle: Float, wind: Float, strike: Float): Float {
        if (u < rel) return lerp(idle, wind, ease(u / rel))
        val q = (u - rel) / (1f - rel)
        return if (q < 0.15f) lerp(wind, strike, ease(q / 0.15f)) else lerp(strike, idle, ease((q - 0.15f) / 0.85f))
    }

    // ───────────── modelo do Luffy ─────────────

    // ═══ MODEL BEGIN
    /** true = chapéu de palha na cabeça (visual clássico); false = pendurado nas costas. */
    private const val HAT_ON_HEAD = true

    private val SKIN_B = 0xFFF2BF8C.toInt()
    private val SKIN_LB = 0xFFFBD3A6.toInt()
    private val SKIN_DB = 0xFFD99B68.toInt()
    private val SKIN_DDB = 0xFFB4744A.toInt()
    // Gear Second: a pele fica rosa-avermelhada (sangue bombeado). As cores mudam por quadro em setSkin()
    private var SKIN = SKIN_B
    private var SKIN_L = SKIN_LB
    private var SKIN_D = SKIN_DB
    private var SKIN_DD = SKIN_DDB
    private val HAIR = 0xFF1B1722.toInt()
    private val HAIR_D = 0xFF0C0A12.toInt()
    private val RED = 0xFFD42A2E.toInt()
    private val RED_L = 0xFFEE5048.toInt()
    private val RED_D = 0xFFA51C22.toInt()
    private val RED_DD = 0xFF741317.toInt()
    private val SASH = 0xFFF3C31C.toInt()
    private val SASH_L = 0xFFFFE05A.toInt()
    private val SASH_D = 0xFFC79A10.toInt()
    private val SHORT = 0xFF2C4A86.toInt()
    private val SHORT_L = 0xFF3F66B0.toInt()
    private val SHORT_D = 0xFF1E3260.toInt()
    private val SAND = 0xFF8A5B31.toInt()
    private val SAND_D = 0xFF553517.toInt()
    private val STRAW = 0xFFF1D17A.toInt()
    private val STRAW_L = 0xFFFFE7A6.toInt()
    private val STRAW_D = 0xFFC9A24C.toInt()
    private val CORD = 0xFF8A5A2B.toInt()
    private val XSCAR = 0xFFC96858.toInt()
    private val XSCAR_D = 0xFF9A4438.toInt()
    private val FSCAR = 0xFFB9564A.toInt()
    private val WHITE = 0xFFFFFFFF.toInt()
    private val EYE = 0xFF1B1418.toInt()
    private val MOUTH = 0xFF5E141A.toInt()
    private val TONGUE = 0xFFE06A7A.toInt()
    private val FUR = 0xFFF6F3EC.toInt()
    private val FUR_D = 0xFFD2CEC6.toInt()
    private val SOLE = 0xFFC49A62.toInt()
    private val BUTTON = 0xFFF7CB2B.toInt()
    private val STRAP = 0xFF2E1B0E.toInt()

    /** Mecha de cabelo espetada (3 cubos empilhados, afinando para a ponta). */
    private fun spike(x: Float, y: Float, z: Float, lz: Float, lx: Float, w: Float, h: Float) {
        push()
        translate(x, y, z)
        rotZ(lz)
        rotX(lx)
        cube(0f, h * 0.45f, 0f, w, h * 0.5f, w, HAIR)
        cube(0f, h * 1.05f, 0f, w * 0.66f, h * 0.38f, w * 0.66f, HAIR)
        cube(0f, h * 1.5f, 0f, w * 0.34f, h * 0.22f, w * 0.34f, HAIR_D)
        pop()
    }

    /** Disco aproximado por 4 retângulos girados de 45 graus (aba e copa do chapéu). */
    private fun disc(y: Float, r: Float, hy: Float, col: Int) {
        val b = r * 0.4142f
        for (i in 0..3) {
            push()
            translate(0f, y, 0f)
            rotY(i * 0.7854f)
            cube(0f, 0f, 0f, r, hy, b, col)
            pop()
        }
    }

    /** Chapéu de palha: aba larga, copa redonda e faixa vermelha (o eixo do chapéu é o Y local). */
    private fun strawHat() {
        disc(0.0f, 7.4f, 0.28f, STRAW)
        disc(0.32f, 6.2f, 0.22f, STRAW_L)
        disc(-0.3f, 7.0f, 0.2f, STRAW_D)
        disc(0.9f, 4.5f, 0.5f, STRAW)
        disc(1.9f, 4.35f, 0.55f, STRAW)
        disc(2.9f, 4.0f, 0.55f, STRAW_L)
        disc(3.7f, 3.2f, 0.35f, STRAW_L)
        disc(4.1f, 2.0f, 0.2f, STRAW)
        disc(1.05f, 4.65f, 0.42f, RED)
        disc(1.55f, 4.55f, 0.18f, RED_D)
    }

    /** Chapéu pendurado nas costas, preso pelo cordão no pescoço. */
    private fun hatBack(sw: Float) {
        push()
        translate(0f, 8.6f, -5.4f)
        rotX(-1.45f + sw)
        strawHat()
        pop()
        cube(-2.1f, 12.4f, -1.2f, 0.15f, 2.2f, 0.15f, CORD)
        cube(2.1f, 12.4f, -1.2f, 0.15f, 2.2f, 0.15f, CORD)
        cube(-2.1f, 10.4f, -3.4f, 0.15f, 0.15f, 2.4f, CORD)
        cube(2.1f, 10.4f, -3.4f, 0.15f, 0.15f, 2.4f, CORD)
    }

    private fun head(turn: Float, tilt: Float, shout: Float, hs0: Float, hs1: Float, hatHead: Boolean) {
        push()
        translate(0f, 14.7f, 0f)
        rotY(turn)
        rotX(tilt)
        // cabeça menor em relação ao corpo: proporção de adulto, não de boneco
        scale(0.9f, 0.9f, 0.9f)
        cube(0f, 0.4f, 0f, 2.4f, 1.2f, 2.3f, SKIN_D)
        // crânio largo + mandíbula mais estreita (maçã do rosto marcada)
        cube(0f, 4.8f, 0f, 4.8f, 2.8f, 4.5f, SKIN)
        cube(0f, 1.7f, 0.2f, 4.1f, 1.5f, 4.3f, SKIN)
        cube(0f, 5.7f, 4.53f, 4.5f, 3.4f, 0.06f, SKIN_L)
        cube(0f, 1.8f, 4.53f, 3.9f, 1.3f, 0.06f, SKIN_L)
        cube(0f, 0.55f, 4.2f, 2.2f, 0.3f, 0.25f, SKIN_D)
        cube(-4.95f, 4.5f, 0.3f, 0.35f, 1.3f, 1.1f, SKIN_D)
        cube(4.95f, 4.5f, 0.3f, 0.35f, 1.3f, 1.1f, SKIN_D)
        // olhos grandes e expressivos: contorno escuro, branco, pupila grande e brilho
        cube(-2.2f, 6.5f, 4.6f, 1.5f, 0.17f, 0.06f, HAIR_D)
        cube(2.2f, 6.5f, 4.6f, 1.5f, 0.17f, 0.06f, HAIR_D)
        cube(-2.2f, 5.0f, 4.58f, 1.3f, 1.45f * eyeOpen, 0.06f, HAIR_D)
        cube(2.2f, 5.0f, 4.58f, 1.3f, 1.45f * eyeOpen, 0.06f, HAIR_D)
        cube(-2.2f, 5.0f, 4.62f, 1.12f, 1.27f * eyeOpen, 0.06f, WHITE, true)
        cube(2.2f, 5.0f, 4.62f, 1.12f, 1.27f * eyeOpen, 0.06f, WHITE, true)
        cube(-2.2f, 4.9f, 4.67f, 0.72f, 0.92f * eyeOpen, 0.06f, EYE)
        cube(2.2f, 4.9f, 4.67f, 0.72f, 0.92f * eyeOpen, 0.06f, EYE)
        cube(-2.45f, 5.35f, 4.73f, 0.25f, 0.28f * eyeOpen, 0.05f, WHITE, true)
        cube(1.95f, 5.35f, 4.73f, 0.25f, 0.28f * eyeOpen, 0.05f, WHITE, true)
        // sobrancelhas grossas, inclinadas para baixo no meio (cara de determinado)
        push()
        translate(-2.3f, 6.95f, 4.62f)
        rotZ(-0.08f)
        cube(0f, 0f, 0f, 1.5f, 0.26f, 0.08f, HAIR_D)
        pop()
        push()
        translate(2.3f, 6.95f, 4.62f)
        rotZ(0.08f)
        cube(0f, 0f, 0f, 1.5f, 0.26f, 0.08f, HAIR_D)
        pop()
        // cicatriz embaixo do olho esquerdo (lado direito da tela)
        cube(2.2f, 3.4f, 4.62f, 1.05f, 0.17f, 0.05f, FSCAR)
        cube(1.65f, 3.0f, 4.62f, 0.12f, 0.42f, 0.05f, FSCAR)
        cube(2.2f, 3.0f, 4.62f, 0.12f, 0.42f, 0.05f, FSCAR)
        cube(2.75f, 3.0f, 4.62f, 0.12f, 0.42f, 0.05f, FSCAR)
        // bochechas coradas
                // nariz pequeno
        cube(0f, 3.7f, 4.65f, 0.55f, 0.8f, 0.3f, SKIN_D)
        cube(0f, 3.15f, 4.9f, 0.5f, 0.3f, 0.2f, SKIN_L)
        // sorrisão de orelha a orelha
        val mh = 0.6f + 0.95f * shout
        val my = 1.95f - 0.25f * shout
        cube(0f, my, 4.58f, 2.2f, mh * 0.8f, 0.06f, MOUTH)
        cube(0f, my + mh * 0.8f - 0.3f, 4.65f, 2.0f, 0.3f, 0.06f, WHITE)
        cube(0f, my - mh + 0.42f, 4.65f, 1.5f, 0.32f, 0.06f, TONGUE)
        cube(-2.3f, my + mh * 0.8f, 4.62f, 0.25f, 0.45f, 0.06f, MOUTH)
        cube(2.3f, my + mh * 0.8f, 4.62f, 0.25f, 0.45f, 0.06f, MOUTH)
        // cabelo preto bagunçado
        cube(0f, 7.1f, -0.3f, 4.6f, 0.6f, 4.5f, HAIR)
        cube(0f, 4.5f, -4.65f, 4.9f, 2.6f, 0.6f, HAIR)
        cube(-4.85f, 5.9f, -0.3f, 0.4f, 1.4f, 4.2f, HAIR_D)
        cube(4.85f, 5.9f, -0.3f, 0.4f, 1.4f, 4.2f, HAIR_D)
        cube(-4.85f, 5.4f, 2.4f, 0.2f, 1.5f, 1.0f, HAIR_D)
        cube(4.85f, 5.4f, 2.4f, 0.2f, 1.5f, 1.0f, HAIR_D)
        // franja picotada (fica acima das sobrancelhas)
        cube(0f, 8.9f, 4.35f, 4.85f, 0.8f, 0.55f, HAIR)
        cube(-3.8f, 8.2f, 4.7f, 0.95f, 0.6f, 0.35f, HAIR)
        cube(-1.9f, 8.4f, 4.75f, 0.9f, 0.45f, 0.35f, HAIR)
        cube(0.2f, 8.3f, 4.78f, 1.0f, 0.6f, 0.35f, HAIR)
        cube(2.2f, 8.4f, 4.75f, 0.9f, 0.5f, 0.35f, HAIR)
        cube(3.9f, 8.2f, 4.7f, 0.95f, 0.6f, 0.35f, HAIR)
        if (!hatHead) {
            spike(-3.4f, 10.0f, 1.2f, 0.5f, 0.2f - hs0, 1.4f, 2.4f)
            spike(3.4f, 10.0f, 1.4f, -0.5f, 0.25f - hs0, 1.4f, 2.2f)
            spike(0.2f, 10.0f, 2.8f, 0.1f, 0.6f - hs0, 1.5f, 2.0f)
            spike(-1.9f, 10.1f, -0.8f, 0.3f, -0.15f - hs0, 1.5f, 3.1f)
            spike(1.8f, 10.1f, -1.0f, -0.25f, -0.2f - hs0, 1.5f, 3.0f)
            spike(0.0f, 9.8f, -3.0f, 0.0f, -0.8f - hs0, 2.0f, 2.6f)
            spike(-3.7f, 9.6f, -2.6f, 0.65f, -0.55f - hs0, 1.6f, 2.3f)
            spike(3.8f, 9.6f, -2.5f, -0.65f, -0.55f - hs0, 1.6f, 2.3f)
        } else {
            // mechas espetadas que escapam por baixo do chapéu (laterais e nuca)
            cube(-4.95f, 4.6f, 1.6f, 0.25f, 1.1f, 0.9f, HAIR_D)
            cube(4.95f, 4.6f, 1.6f, 0.25f, 1.1f, 0.9f, HAIR_D)
        }
        // mechas da nuca
        if (!hatHead) {
            spike(-2.5f, 4.9f, -5.2f, 0.1f, -1.4f - hs1, 1.4f, 2.6f)
            spike(2.5f, 4.6f, -5.2f, -0.1f, -1.45f - hs1, 1.4f, 2.4f)
            spike(0.0f, 3.2f, -5.2f, 0.0f, -1.5f - hs1, 1.5f, 2.4f)
        }
        if (hatHead) {
            // chapéu de palha inclinado para trás: deixa o rosto à mostra
            push()
            translate(0f, 9.1f, -0.2f)
            rotX(-0.3f)
            strawHat()
            pop()
        }
        pop()
    }

    /** Quadril: bermuda azul, faixa amarela com nó e as pontas balançando, barriga. */
    private fun torsoLow(sA: Float, sB: Float) {
        cube(0f, 0.1f, 0f, 5.5f, 1.7f, 3.5f, SHORT)
        cube(0f, 0.0f, 3.55f, 4.4f, 1.3f, 0.08f, SHORT_L)
        cube(0f, 0.2f, -3.55f, 5.0f, 1.3f, 0.08f, SHORT_D)
        cube(0f, 3.1f, 0f, 5.75f, 1.45f, 3.75f, SASH)
        cube(0f, 3.1f, 3.78f, 5.6f, 1.3f, 0.07f, SASH_L)
        cube(0f, 4.5f, 0f, 5.8f, 0.17f, 3.8f, SASH_D)
        cube(0f, 1.7f, 0f, 5.8f, 0.17f, 3.8f, SASH_D)
        cube(-3.6f, 3.1f, 3.84f, 0.1f, 1.1f, 0.05f, SASH_D)
        cube(-0.9f, 3.1f, 3.84f, 0.1f, 1.1f, 0.05f, SASH_D)
        cube(5.0f, 3.1f, 3.0f, 0.1f, 1.1f, 0.05f, SASH_D)
        // nó da faixa na frente (lado esquerdo), com as pontas balançando
        cube(2.5f, 3.1f, 4.0f, 1.0f, 1.2f, 0.5f, SASH_D)
        cube(2.5f, 3.1f, 4.35f, 0.6f, 0.8f, 0.2f, SASH)
        push()
        translate(2.2f, 2.0f, 4.1f)
        rotX(sA)
        cube(0f, -3.4f, 0f, 1.0f, 3.5f, 0.25f, SASH)
        cube(0.3f, -3.4f, 0.27f, 0.15f, 3.2f, 0.04f, SASH_L)
        cube(0f, -6.9f, 0f, 1.0f, 0.28f, 0.28f, SASH_D)
        pop()
        push()
        translate(3.2f, 2.0f, 4.1f)
        rotX(sB)
        rotZ(-0.22f)
        cube(0f, -2.4f, 0f, 0.8f, 2.5f, 0.23f, SASH_D)
        pop()
        // costas da barriga cobertas pela camisa (evita aparecer pele por trás quando o peito gira ou inclina)
        cube(0f, 6.1f, -3.42f, 4.9f, 2.0f, 0.16f, RED)
        cube(0f, 4.2f, -3.42f, 4.9f, 0.14f, 0.18f, RED_D)
        // laterais da barriga na cor do colete
        cube(-4.8f, 6.3f, 0f, 0.14f, 1.9f, 3.35f, RED)
        cube(4.8f, 6.3f, 0f, 0.14f, 1.9f, 3.35f, RED)
        // barriga: tanquinho de seis gomos
        cube(0f, 6.1f, 0f, 4.7f, 1.9f, 3.3f, SKIN)
        cube(0f, 6.1f, 3.32f, 4.4f, 1.7f, 0.06f, SKIN_L)
        for (r in 0..2) {
            val ay = 5.0f + r * 1.25f
            cube(-1.85f, ay, 3.37f, 1.6f, 0.5f, 0.05f, SKIN_L)
            cube(1.85f, ay, 3.37f, 1.6f, 0.5f, 0.05f, SKIN_L)
        }
        cube(0f, 6.2f, 3.4f, 0.08f, 1.8f, 0.05f, SKIN_DD)
        cube(0f, 5.62f, 3.4f, 3.6f, 0.08f, 0.05f, SKIN_DD)
        cube(0f, 6.87f, 3.4f, 3.6f, 0.08f, 0.05f, SKIN_DD)
        cube(-4.5f, 6.0f, 2.2f, 0.25f, 1.6f, 1.2f, SKIN_D)
        cube(4.5f, 6.0f, 2.2f, 0.25f, 1.6f, 1.2f, SKIN_D)
    }

    /** Peito, cicatriz em X, colete vermelho aberto, pescoço e chapéu nas costas. */
    private fun torsoUp(vF: Float, vB: Float, hatSw: Float, hatHead: Boolean) {
        // peito
        cube(0f, 10.7f, 0f, 5.8f, 2.8f, 3.7f, SKIN)
        cube(-2.9f, 11.4f, 3.72f, 2.6f, 1.6f, 0.08f, SKIN_L)
        cube(2.9f, 11.4f, 3.72f, 2.6f, 1.6f, 0.08f, SKIN_L)
        cube(0f, 11.0f, 3.76f, 0.07f, 1.9f, 0.05f, SKIN_DD)
        // laterais do peito na cor do colete
        cube(-5.88f, 10.5f, 0f, 0.1f, 3.6f, 3.75f, RED)
        cube(5.88f, 10.5f, 0f, 0.1f, 3.6f, 3.75f, RED)
        cube(-5.9f, 10.5f, 0f, 0.08f, 0.14f, 3.8f, RED_D)
        cube(5.9f, 10.5f, 0f, 0.08f, 0.14f, 3.8f, RED_D)
        // cicatriz em X, com pontos de sutura
        push()
        translate(0f, 9.9f, 3.84f)
        rotZ(0.64f)
        cube(0f, 0f, 0f, 0.3f, 4.8f, 0.06f, XSCAR)
        cube(0f, 1.6f, 0.03f, 0.9f, 0.1f, 0.04f, XSCAR_D)
        cube(0f, -0.4f, 0.03f, 0.9f, 0.1f, 0.04f, XSCAR_D)
        cube(0f, -2.4f, 0.03f, 0.9f, 0.1f, 0.04f, XSCAR_D)
        pop()
        push()
        translate(0f, 9.9f, 3.86f)
        rotZ(-0.64f)
        cube(0f, 0f, 0f, 0.3f, 4.8f, 0.06f, XSCAR)
        cube(0f, 1.6f, 0.03f, 0.9f, 0.1f, 0.04f, XSCAR_D)
        cube(0f, -0.4f, 0.03f, 0.9f, 0.1f, 0.04f, XSCAR_D)
        cube(0f, -2.4f, 0.03f, 0.9f, 0.1f, 0.04f, XSCAR_D)
        pop()
        // colete vermelho aberto: costas, alças que cobrem os ombros e painéis da frente
        cube(0f, 9.8f, -3.9f, 6.0f, 3.9f, 0.5f, RED)
        cube(0f, 10.0f, -4.42f, 0.1f, 3.5f, 0.05f, RED_DD)
        cube(-2.0f, 10.5f, -4.42f, 0.08f, 2.8f, 0.05f, RED_D)
        cube(2.0f, 10.5f, -4.42f, 0.08f, 2.8f, 0.05f, RED_D)
        cube(-4.2f, 13.7f, 0f, 1.7f, 0.62f, 3.9f, RED)
        cube(4.2f, 13.7f, 0f, 1.7f, 0.62f, 3.9f, RED)
        cube(-4.2f, 14.3f, 0f, 1.5f, 0.06f, 3.7f, RED_L)
        cube(4.2f, 14.3f, 0f, 1.5f, 0.06f, 3.7f, RED_L)
        cube(-4.4f, 10.5f, 3.85f, 1.5f, 3.0f, 0.45f, RED)
        cube(4.4f, 10.5f, 3.85f, 1.5f, 3.0f, 0.45f, RED)
        cube(-2.95f, 10.5f, 3.9f, 0.12f, 3.0f, 0.5f, RED_D)
        cube(2.95f, 10.5f, 3.9f, 0.12f, 3.0f, 0.5f, RED_D)
        cube(-4.6f, 11.4f, 4.32f, 0.75f, 1.9f, 0.05f, RED_L)
        cube(-3.55f, 12.3f, 4.4f, 0.32f, 0.32f, 0.07f, BUTTON)
        cube(-3.55f, 11.0f, 4.4f, 0.32f, 0.32f, 0.07f, BUTTON)
        cube(-3.55f, 9.7f, 4.4f, 0.32f, 0.32f, 0.07f, BUTTON)
        cube(4.6f, 11.4f, 4.32f, 0.75f, 1.9f, 0.05f, RED_L)
        push()
        translate(-4.4f, 7.5f, 3.85f)
        rotX(vF)
        cube(0f, -1.1f, 0f, 1.5f, 1.2f, 0.4f, RED)
        cube(0f, -2.2f, 0f, 1.5f, 0.14f, 0.44f, RED_D)
        pop()
        push()
        translate(4.4f, 7.5f, 3.85f)
        rotX(vF)
        cube(0f, -1.1f, 0f, 1.5f, 1.2f, 0.4f, RED)
        cube(0f, -2.2f, 0f, 1.5f, 0.14f, 0.44f, RED_D)
        pop()
        push()
        translate(0f, 5.9f, -3.9f)
        rotX(vB)
        cube(0f, -1.0f, -0.1f, 6.0f, 1.1f, 0.4f, RED)
        cube(0f, -2.05f, -0.1f, 6.0f, 0.14f, 0.44f, RED_D)
        pop()
        // pescoço e cordão do chapéu
        cube(0f, 14.4f, 0f, 2.2f, 0.9f, 2.2f, SKIN_D)
        cube(0f, 13.9f, 0f, 2.35f, 0.12f, 2.35f, CORD)
        if (!hatHead) {
            hatBack(hatSw)
        }
    }

    /**
     * Braço de borracha (side = +1 esquerdo / -1 direito). Ângulo negativo = para a frente.
     * ext = quanto o braço estica (1 = tamanho normal); esticado, ele afina e o punho incha um pouco.
     */
    private fun arm(side: Float, sh: Float, shZ: Float, el: Float, tw: Float, id: Int, ext: Float) {
        val e = max(1f, ext)
        val th = if (e <= 1.01f) 1f else 1f - 0.2f * min(1f, (e - 1f) / 6f)   // esticado afina só um pouco: continua grosso como o ombro
        val fs = 1f + 0.3f * min(1f, (e - 1f) / 3f)
        push()
        translate(side * 8.1f, 12.3f, 0f)
        projectTo(0f, 0f, 0f, id + 4)
        rotZ(side * (shZ + 0.1f))
        rotX(sh)
        rotY(tw)
        // ombro / deltoide (não estica)
        cube(0f, -0.4f, 0f, 2.35f, 2.2f, 2.3f, RED)
        cube(side * 0.5f, 0.4f, 0.4f, 1.6f, 0.9f, 1.6f, RED_L)
        // braço
        val h1 = 2.75f * e
        cube(0f, 0.25f - h1, 0f, 2.0f * th, h1, 2.0f * th, RED)
        cube(side * 0.2f * th, -0.2f - 1.5f * e, 1.3f * th, 1.45f * th, 1.5f * e, 0.75f * th, RED_L)
        cube(0f, 0.25f - h1 * 1.9f, 0f, 2.05f * th, 0.12f, 2.05f * th, RED_D)
        translate(0f, 0.2f - 2f * h1, 0f)
        rotX(el)
        // antebraço
        val h2 = 2.65f * e
        cube(0f, 0.25f - h2, 0f, 1.7f * th, h2, 1.7f * th, SKIN)
        cube(0f, 0.25f - h2 * 0.8f, 0f, 2.0f * th, h2 * 0.8f, 2.0f * th, RED)
        cube(0f, 0.1f - 1.1f * e, 1.35f * th, 1.3f * th, 1.1f * e, 0.55f * th, RED_L)
        cube(0f, 0.25f - h2 * 1.6f, 0f, 2.1f * th, 0.28f, 2.1f * th, RED_D)
        translate(0f, 0.3f - 2f * h2, 0f)
        // punho fechado
        cube(0f, -1.4f * fs, 0.1f, 2.0f * fs, 1.55f * fs, 2.0f * fs, SKIN)
        cube(0f, -1.0f * fs, 2.05f * fs, 1.75f * fs, 1.05f * fs, 0.1f, SKIN_L)
        cube(0f, -2.95f * fs, 0.1f, 1.8f * fs, 0.12f, 1.8f * fs, SKIN_D)
        cube(0f, -1.35f * fs, 2.12f * fs, 1.7f * fs, 0.07f, 0.05f, SKIN_DD)
        cube(-0.6f * fs, -1.0f * fs, 2.12f * fs, 0.07f, 1.0f * fs, 0.05f, SKIN_DD)
        cube(0.6f * fs, -1.0f * fs, 2.12f * fs, 0.07f, 1.0f * fs, 0.05f, SKIN_DD)
        projectTo(0f, -1.6f, 0.4f, id)
        pop()
    }

    /** Perna: coxa (bermuda azul em cima), joelho, canela e pé de sandália. Ângulo positivo = para trás. */
    private fun leg(side: Float, a: Float, knee: Float, foot: Float, hipY: Float) {
        push()
        translate(side * 3.2f, hipY, 0f)
        rotZ(side * gLegSplay + (if (side < 0f) gLatR else gLatL))
        scale(0.9f, gLegLen, 1f)
        rotX(a)
        cube(0f, -2.9f, 0f, 2.75f, 3.1f, 2.85f, SHORT)
        cube(side * 0.9f, -2.7f, 2.9f, 1.4f, 2.5f, 0.06f, SHORT_L)
        cube(0f, -5.95f, 0f, 2.85f, 0.22f, 2.95f, SHORT_D)
        cube(0f, -6.9f, 0f, 2.9f, 0.95f, 3.0f, SHORT)
        cube(side * 0.9f, -6.9f, 3.02f, 1.4f, 0.8f, 0.05f, SHORT_L)
        translate(0f, -7.6f, 0f)
        rotX(knee)
        // barra de pelo branco da bermuda
        cube(0f, -0.3f, 0f, 2.75f, 0.75f, 2.85f, FUR)
        cube(0f, -0.95f, 0f, 2.55f, 0.28f, 2.65f, FUR_D)
        for (q in 0..3) {
            cube(-1.9f + q * 1.27f, -1.3f, 2.75f, 0.5f, 0.38f, 0.22f, FUR)
            cube(-1.3f + q * 1.27f, -1.35f, -2.75f, 0.5f, 0.34f, 0.22f, FUR_D)
        }
        cube(-2.75f, -1.2f, 0.4f, 0.22f, 0.4f, 0.9f, FUR)
        cube(2.75f, -1.2f, -0.4f, 0.22f, 0.4f, 0.9f, FUR)
        cube(0f, -2.9f, 0f, 1.8f, 3.0f, 1.9f, SKIN)
        cube(0f, -1.8f, 1.95f, 1.1f, 1.4f, 0.05f, SKIN_L)
        translate(0f, -5.9f, 0f)
        rotX(foot)
        cube(0f, -0.2f, 1.0f, 1.65f, 0.4f, 2.4f, SKIN)
        cube(0f, 0.0f, 0.5f, 1.75f, 0.55f, 1.0f, SKIN_D)
        cube(0f, -1.0f, 1.3f, 2.05f, 0.4f, 3.4f, SAND_D)
        cube(0f, -0.62f, 1.3f, 1.95f, 0.1f, 3.3f, SOLE)
        push()
        translate(-0.8f, 0.24f, 2.25f)
        rotY(0.82f)
        cube(0f, 0f, 0f, 0.15f, 0.17f, 1.12f, STRAP)
        pop()
        push()
        translate(0.8f, 0.24f, 2.25f)
        rotY(-0.82f)
        cube(0f, 0f, 0f, 0.15f, 0.17f, 1.12f, STRAP)
        pop()
        cube(0f, 0.2f, 3.05f, 0.17f, 0.25f, 0.2f, STRAP)
        cube(-1.62f, -0.2f, 1.5f, 0.1f, 0.45f, 0.17f, STRAP)
        cube(1.62f, -0.2f, 1.5f, 0.1f, 0.45f, 0.17f, STRAP)
        cube(0f, -0.45f, 3.7f, 1.7f, 0.3f, 0.45f, SKIN_D)
        pop()
    }

    private fun skeleton(
        hip: Float, hipTw: Float, lean: Float, chestTw: Float, breath: Float,
        aR: Float, kR: Float, fR: Float, aL: Float, kL: Float, fL: Float,
        vF: Float, vB: Float, sA: Float, sB: Float, hatSw: Float,
        turn: Float, tilt: Float, shout: Float, hs0: Float, hs1: Float,
        rsh: Float, rshZ: Float, rel: Float, rtw: Float,
        lsh: Float, lshZ: Float, lel: Float, ltw: Float, hatHead: Boolean,
        extR: Float, extL: Float
    ) {
        push()
        translate(0f, hip, 0f)
        rotY(gLegSpin)
        translate(0f, -hip, 0f)
        leg(-1f, aR, kR, fR, hip)
        leg(1f, aL, kL, fL, hip)
        pop()
        push()
        translate(0f, hip, 0f)
        rotY(hipTw)
        rotX(lean)
        torsoLow(sA, sB)
        push()
        rotY(chestTw)
        translate(0f, breath, 0f)
        torsoUp(vF, vB, hatSw, hatHead)
        projectTo(0f, 10f, 0f, 3)
        arm(-1f, rsh, rshZ, rel, rtw, 0, extR)
        arm(1f, lsh, lshZ, lel, ltw, 1, extL)
        projectTo(0f, 19.4f, 2f, 2)
        head(turn, tilt, shout, hs0, hs1, hatHead)
        pop()
        pop()
    }
    // ═══ MODEL END

    // ───────────── marcas de tela (modelo → 2D) ─────────────

    private fun mark(x: Float, y: Float, z: Float, id: Int) {
        val m = stack[top]
        val wx = m[0] * x + m[1] * y + m[2] * z + m[9]
        val wy = m[3] * x + m[4] * y + m[5] * z + m[10]
        val wz = m[6] * x + m[7] * y + m[8] * z + m[11]
        mkX[id] = ox + wx * rs
        mkY[id] = oy + (wz * SINP - wy * COSP) * rs
    }

    private fun projectTo(x: Float, y: Float, z: Float, id: Int) = mark(x, y, z, id)

    // ───────────── estado externo (preenchido pelo Player antes de desenhar) ─────────────
    var rollP = -1f        // >= 0 enquanto o dash está ativo; <0 = fora dele
    var dashPh = 0         // 0 fora, 1 preparação (carrega), 2 voo em arco, 3 rolagem e levantar
    var dashU = 0f         // fase 1: segundos de preparação; fases 2 e 3: progresso 0..1
    var dashCharge = 0f    // 0..1: quanto o botão ficou segurado
    var dashFlip = false   // toque rápido: mortal para a frente que cai de pé (sem girar nem rolar)
    var inAir = false
    var airVz = 0f
    var airH = 0f
    var swing = 0          // 0 soco direito, 1 soco esquerdo
    var punchLen = 150f    // (reserva) distância (px) até o alvo, usada só se não houver alvo na tela
    // alvo do soco, em px de TELA a partir do pé do herói (já sem o squash): o braço é calculado para o punho cair exatamente aí
    var punchOn = false
    var punchTX = 0f       // x do peito do alvo
    var punchTY = 0f       // y do peito do alvo
    var punchGY = 0f       // y do chão do alvo (dá a altura do peito)
    var punchInset = 0f    // px: o punho para na superfície do corpo, não no centro
    // âncora das mãos no chão durante a carga do dash (px de tela a partir do pé do herói)
    var anchorOn = false
    var anchorTX = 0f
    var anchorTY = 0f
    var dashVX = 0f        // velocidade do herói no mundo (px/s): move o corpo para os lados com as mãos presas
    var dashVY = 0f
    var alphaV = 1f
    // Gomu Gomu no Gatling (preenchido pelo Player): progresso 0..1 da skill (<0 = fora dela) e o retângulo na frente
    var skillP = -1f
    var gatActive = false
    var gatDX = 0f         // direção do retângulo (unitária, no mundo)
    var gatDY = 1f
    var gatLen = 240f      // comprimento do retângulo (px)
    var gatHalfW = 64f     // meia largura (px)
    var gatSqX = 1f        // squash do canvas (para converter px do mundo em px do desenho)
    var gatSqY = 1f
    var gatPtN = 0         // inimigos dentro do retângulo (frente, lado) em px: os punhos miram neles
    val gatPtF = FloatArray(6)
    val gatPtU = FloatArray(6)
    // Gomu Gomu no Gear Second (preenchido pelo Player): progresso 0..1 da animação de bombeamento (<0 = fora dela)
    // e intensidade do buff (0..1, apaga no último segundo)
    var g2P = -1f
    var g2Act = 0f
    var heli = false       // pulo duplo: helicóptero ativo
    var heliT = 0f         // segundos desde a soltura do pulo carregado
    var heliCharge = -1f   // segundos segurando o pulo no chão (<0 = não está carregando)
    var wx = 0f            // posição do corpo no mundo (px), para o rastro não depender da câmera
    var wy = 0f

    // parâmetros das pernas (preenchidos em draw() antes de montar o esqueleto)
    private var gLegSpin = 0f
    private var gLegSplay = 0f
    private var gLegLen = 1f
    private var gLatL = 0f   // passo lateral da perna esquerda (rad, + = para a esquerda do herói)
    private var gLatR = 0f

    /** Instante (0..1 da animação) em que o punho esticado chega ao alvo. */
    const val T_HIT = 0.42f

    /** Fim da preparação do soco (PW) e fim da segurada no alvo = início da volta do braço (PR), 0..1 da animação. */
    private const val PW = 0.30f
    private const val PR = 0.50f

    /** Pitch do corpo no fim do voo (um pouco de mergulho de cabeça), de onde a rolagem continua. */
    private const val DIVE_PITCH = 1.7907964f

    /** Altura do pivô do corpo (unidades do modelo) em torno do qual ele gira deitado / rola. */
    private const val PIV = 16f

    /** Distância (em unidades do modelo) à frente onde as mãos se agarram no chão. */
    private const val DASH_ANCHOR = 46f

    /** Comprimento do braço normal (ombro até a base do punho), em unidades do modelo. */
    private const val ARM_L = 13.25f

    /** Rad. de passo por unidade de modelo puxada (um passo ≈ 12 unidades: o pé acompanha o corpo). */
    private const val STEP_K = 0.26f
    private const val HELI_WIND_MAX = 2.7f   // quanto o quadril enrola (rad) enquanto sobe, antes de soltar
    private const val HELI_WIND_T = 0.17f   // segundos que o quadril leva para enrolar (rápido)
    private const val HELI_REL_T = 0.20f    // segundos depois de soltar o botão em que o quadril se solta no ar
    private const val HELI_OMEGA = 25f      // rad/s das pernas girando

    fun impactAt(@Suppress("UNUSED_PARAMETER") sw: Int): Float = T_HIT

    private const val HAND_Y = 2.0f      // altura do centro do punho apoiado no chão (unidades do modelo)
    private const val HAND_SEP = 5.0f     // meia distância entre os dois punhos presos no chão
    private const val LAT_K = 0.22f       // rad de passo lateral por unidade de modelo andada para o lado

    private var pmOk = false      // alvo do soco guardado (travado depois do impacto)
    private var pmX = 0f
    private var pmY = 0f
    private var pmZ = 0f
    private var gatSplay = 0f
    private var ikSh = 0f
    private var ikShZ = 0f
    private var ikExt = 1f

    /** Extensão para o CENTRO do punho ficar a [len] unidades do ombro (o punho incha ao esticar). */
    private fun extCenter(len: Float): Float {
        var e = 1f
        for (i in 0..2) {
            val fs = 1f + 0.3f * min(1f, (e - 1f) / 3f)
            e = max(1f, (len + 0.5f - 1.4f * fs) / 10.8f)
        }
        return e
    }

    /**
     * Cinemática inversa do braço: acha ombro (ikSh), abertura lateral (ikShZ) e extensão (ikExt) para o centro do punho
     * cair no ponto (tx,ty,tz) do modelo-mundo (relativo ao pé do herói), dada a pose real do corpo naquele instante.
     * side: +1 esquerdo / -1 direito. a = yaw. O cotovelo fica reto.
     */
    private fun solveArm(side: Float, tx: Float, ty: Float, tz: Float, hipH: Float, leanE: Float, zOff: Float, a: Float) {
        val ca = cos(a)
        val sa = sin(a)
        val xs = side * 8.1f
        val ys = hipH + 12.3f * cos(leanE)
        val zs = 12.3f * sin(leanE) + zOff
        val shx = xs * ca + zs * sa
        val shz = -xs * sa + zs * ca
        val vx = tx - shx
        val vy = ty - ys
        val vz = tz - shz
        val u = vx * ca - vz * sa
        val f = vx * sa + vz * ca
        val len = max(0.001f, sqrt(u * u + vy * vy + f * f))
        val ux = u / len
        val uy = vy / len
        val uz = f / len
        val cl = cos(leanE)
        val sl = sin(leanE)
        val dcy = cl * uy + sl * uz
        val dcz = -sl * uy + cl * uz
        ikSh = -asin(dcz.coerceIn(-1f, 1f))
        ikShZ = side * atan2(ux, -dcy) - 0.1f
        ikExt = extCenter(len)
    }

    /**
     * Igual ao solveArm, mas com a torção do peito [chestTw] (o quadril fica sem torção). Também escolhe a ramificação
     * contínua dos ângulos: com o tronco inclinado, um soco quase horizontal fica "acima" do horizonte do tronco e a
     * solução comum vira o ombro ~165 graus e dá a volta em +-pi quando o alvo cruza o eixo central (pulo no braço).
     */
    private fun solveArmT(side: Float, tx: Float, ty: Float, tz: Float, hipH: Float, leanE: Float, zOff: Float, a: Float, chestTw: Float) {
        val ca = cos(a)
        val sa = sin(a)
        val cl = cos(leanE)
        val sl = sin(leanE)
        val ct = cos(chestTw)
        val st = sin(chestTw)
        val xs = side * 8.1f
        val xsE = xs * ct
        val ysE = hipH + 12.3f * cl + xs * st * sl
        val zsE = 12.3f * sl - xs * st * cl + zOff
        val shx = xsE * ca + zsE * sa
        val shz = -xsE * sa + zsE * ca
        val vx = tx - shx
        val vy = ty - ysE
        val vz = tz - shz
        val u = vx * ca - vz * sa
        val f = vx * sa + vz * ca
        val len = max(0.001f, sqrt(u * u + vy * vy + f * f))
        val ux = u / len
        val uy = vy / len
        val uz = f / len
        val dcy = cl * uy + sl * uz
        val dcz = -sl * uy + cl * uz
        val uxc = ux * ct - dcz * st
        val dczc = ux * st + dcz * ct
        var sh = -asin(dczc.coerceIn(-1f, 1f))
        var ang = atan2(uxc, -dcy)
        val pi = PI.toFloat()
        if (ang > pi / 2f) {
            ang -= pi
            sh = if (sh < 0f) -pi - sh else pi - sh
        } else if (ang < -pi / 2f) {
            ang += pi
            sh = if (sh < 0f) -pi - sh else pi - sh
        }
        ikSh = sh
        ikShZ = side * ang - 0.1f
        ikExt = extCenter(len)
    }

    /** Quanto o braço precisa esticar para o punho alcançar [len] unidades do modelo a partir do ombro. */
    private fun extFor(len: Float): Float = max(1f, (len - 2.8f) / 10.8f)

    // ───────────── Gomu Gomu no Gear Second ─────────────
    // Linha do tempo (s), como no anime: 0 a 0,5 abaixa de pernas bem abertas e apoia o punho esquerdo no chão |
    // G2_P0 a G2_PUMP bombeia com o braço direito junto à coxa (frequência crescente), o corpo treme, a pele esquenta
    // e sai vapor | G2_PUMP: explosão de vapor + anel de choque, levanta, peito estufado e braços abertos |
    // relaxa até G2_DUR. Depois o buff fica ligado: pele rosa, aura, vapor fino atrás do corpo e rastros ao correr.
    const val G2_DUR = 2.8f
    const val G2_P0 = 0.5f
    const val G2_PUMP = 2.15f

    /** Ciclos de bombeamento acumulados em [tg] segundos (a frequência sobe com o tempo). */
    fun g2Cyc(tg: Float): Float {
        val u = (tg - G2_P0).coerceAtLeast(0f)
        return 2.6f * u + 0.8f * u * u
    }

    private const val NST = 150
    private val stX = FloatArray(NST)
    private val stY = FloatArray(NST)
    private val stVx = FloatArray(NST)
    private val stVy = FloatArray(NST)
    private val stL = FloatArray(NST)
    private val stM = FloatArray(NST)
    private val stS = FloatArray(NST)
    private val stK = IntArray(NST)     // 0 = nuvem de vapor, 1 = risco de velocidade
    private var stNext = 0
    private var stAcc = 0f
    private var stStreakAcc = 0f
    private var g2W = 0f                // peso visual suavizado (tinta da pele, aura)
    private var g2Burst = false
    private var g2Ring = -1f
    private var pwx = 0f
    private var pwy = 0f

    private fun emitSteam(x: Float, y: Float, vx: Float, vy: Float, life: Float, size: Float, kind: Int = 0) {
        val i = stNext
        stNext = (stNext + 1) % NST
        stX[i] = x
        stY[i] = y
        stVx[i] = vx
        stVy[i] = vy
        stL[i] = life
        stM[i] = life
        stS[i] = size
        stK[i] = kind
    }

    // ───────────── Gomu Gomu no Gatling ─────────────
    // Linha do tempo (s): preparação até GAT_WIND; socos alternados, um impacto a cada GAT_DT a partir de GAT_T0
    // (cada braço soca a cada GAT_CYC = 2 * GAT_DT); o último soco de cada braço volta direto ao repouso (GAT_RET),
    // em etapas (comprimento primeiro, ombro depois), enquanto o corpo relaxa: nada corta de repente.
    // Barragem: GAT_N socos * GAT_DT = 3,0 s (antes 1,6 s), 20 impactos por segundo (antes 10).
    // GAT_DUR = último impacto (GAT_T0 + (GAT_N - 1) * GAT_DT = 3,45 s) + 0,58 s de volta ao repouso.
    const val GAT_DUR = 4.03f
    const val GAT_T0 = 0.50f
    const val GAT_DT = 0.05f
    const val GAT_N = 60
    private const val GAT_CYC = 2f * GAT_DT
    private const val GAT_GHOST_RATE = 120f   // braços-fantasma por segundo espalhados no retângulo (só visual)
    private const val GAT_HF = 0.34f     // fração do ciclo até o punho chegar ao alvo
    private const val GAT_HOLD = 0.08f   // fração do ciclo segurando no alvo
    private const val GAT_WIND = 0.36f
    private const val GAT_RET = 0.55f
    private const val G_SH = 0.8f        // braço armado (guarda) entre um soco e outro
    private const val G_Z = 0.4f
    private const val G_EL = -2.3f

    /** Instante (s) em que o soco [i] acerta; o GameView aplica o dano do soco [i] nesse instante. */
    fun gatHit(i: Int): Float = GAT_T0 + i * GAT_DT

    private fun gatStart(i: Int): Float = GAT_T0 + i * GAT_DT - GAT_HF * GAT_CYC

    private fun frac(x: Float): Float = x - floor(x)

    /** Px por unidade do modelo com a escala [s] do desenho. */
    private fun rs0(s: Float): Float = s * PX * MS

    private fun rn(i: Int): Float {
        val v = sin(i * 12.9898f + 78.233f) * 43758.547f
        return v - floor(v)
    }

    private val gatTI = intArrayOf(-1, -1)      // soco atual de cada braço (alvo travado durante o soco)
    private val gatTX = FloatArray(2)
    private val gatTY = FloatArray(2)
    private val gatTZ = FloatArray(2)
    private val gatTF = FloatArray(2)
    private val gatTU = FloatArray(2)
    private val gatFxD = intArrayOf(-1, -1)     // último soco de cada braço que já soltou o efeito de impacto
    private val gatR = FloatArray(2)            // 0..1: quanto o punho já saiu (rastro de velocidade)
    private var gaSh = 0f
    private var gaZ = 0f
    private var gaEl = 0f
    private var gaExt = 1f

    // efeitos 2D: estrela de impacto (0) e punho-fantasma (1), em px relativos ao pé
    private const val NG = 160
    private val gfX = FloatArray(NG)
    private val gfY = FloatArray(NG)
    private val gfSX = FloatArray(NG)   // ombro de onde sai o braço-fantasma (tipo 2), em px relativos ao pé
    private val gfSY = FloatArray(NG)
    private var gatGhAcc = 0f           // acumulador do enxame (braços-fantasma por quadro, independente do FPS)
    private val gfL = FloatArray(NG)
    private val gfM = FloatArray(NG)
    private val gfK = IntArray(NG)
    private val gfR = FloatArray(NG)
    private var gfNext = 0

    private fun emitG(kind: Int, x: Float, y: Float, life: Float, rot: Float, sx: Float = 0f, sy: Float = 0f) {
        val i = gfNext
        gfNext = (gfNext + 1) % NG
        gfX[i] = x
        gfY[i] = y
        gfSX[i] = sx
        gfSY[i] = sy
        gfL[i] = life
        gfM[i] = life
        gfK[i] = kind
        gfR[i] = rot
    }

    /**
     * Escolhe e trava o alvo do soco [i]. Com inimigos no retângulo, 1 soco em cada 3 mira neles; todos os outros (e todos,
     * se não houver ninguém) caem numa sequência R2 de baixa discrepância: cada soco vai para um ponto novo e, juntos,
     * os socos cobrem o retângulo inteiro (da frente do Luffy até o fim, de um lado ao outro), sem formar buracos.
     */
    private fun gatLock(a: Int, i: Int, rsE: Float) {
        val f: Float
        var u: Float
        if (gatPtN > 0 && i % 3 == 0) {
            val j = (i / 3) % gatPtN
            f = gatPtF[j] + (rn(3 * i + 4) - 0.5f) * 10f
            u = gatPtU[j] + (rn(3 * i + 5) - 0.5f) * 10f
        } else {
            f = gatLen * (0.20f + 0.80f * frac(0.5f + i * 0.7548777f))
            u = gatHalfW * 0.92f * (2f * frac(0.3f + i * 0.5698403f) - 1f)
            u += (if (a == 0) -0.15f else 0.15f) * gatHalfW
            u = u.coerceIn(-0.92f * gatHalfW, 0.92f * gatHalfW)
        }
        gatTI[a] = i
        gatTF[a] = f
        gatTU[a] = u
        val wxo = gatDX * f + gatDY * u          // esquerda do Luffy = (dy, -dx)
        val wyo = gatDY * f - gatDX * u
        gatTX[a] = wxo / gatSqX / rsE
        gatTY[a] = 17f + 4f * rn(3 * i + 3)      // sempre abaixo do ombro: IK contínua
        gatTZ[a] = wyo / gatSqY / (rsE * SINP)
    }

    /**
     * Pose de um braço no Gatling (a = 0 direito, 1 esquerdo). Resultado em gaSh/gaZ/gaEl/gaExt.
     * Ciclo: braço armado -> o punho sai (comprimento estala, ombro gira com ease no tempo) -> segura -> encolhe pela
     * linha do soco e só depois gira o ombro de volta. O último soco volta direto ao repouso.
     */
    private fun gatArm(a: Int, tg: Float, hipG: Float, leanG: Float, zG: Float, chest: Float, rSh: Float, rZ: Float, rEl: Float, rsE: Float) {
        val side = if (a == 0) -1f else 1f
        val wA = ease(tg / GAT_WIND)
        val s0 = gatStart(a)
        val last = a + 2 * ((GAT_N - 1 - a) / 2)
        gatR[a] = 0f
        if (tg < s0) {
            gaSh = lerp(rSh, G_SH, wA)
            gaZ = lerp(rZ, G_Z, wA)
            gaEl = lerp(rEl, G_EL, wA)
            gaExt = 1f
            return
        }
        val k = floor((tg - s0) / GAT_CYC).toInt()
        val i = min(a + 2 * k, last)
        val tau = (tg - gatStart(i)) / GAT_CYC
        if (gatTI[a] != i) gatLock(a, i, rsE)
        solveArmT(side, gatTX[a], gatTY[a], gatTZ[a], hipG, leanG, zG, yaw, chest)
        val shI = ikSh
        val zI = ikShZ
        val e = ikExt
        if (i < last || tau < GAT_HF + GAT_HOLD) {
            val out = tau < GAT_HF + GAT_HOLD
            val r = if (tau < GAT_HF) {
                val x = (tau / GAT_HF).coerceIn(0f, 1f)
                1f - (1f - x) * (1f - x)
            } else if (out) 1f
            else 1f - smoother((tau - GAT_HF - GAT_HOLD) / (1f - GAT_HF - GAT_HOLD))
            val ra: Float
            val rb: Float
            if (out) {
                ra = ease(tau / (GAT_HF * 0.75f))
                rb = ease(tau / (GAT_HF * 0.9f))
            } else {
                ra = ease(min(1f, r * 1.9f))
                rb = ease(min(1f, r * 1.5f))
            }
            gaExt = 1f + (e - 1f) * r * r
            gaSh = lerp(G_SH, shI, ra)
            gaZ = lerp(G_Z, zI, ra)
            gaEl = lerp(G_EL, 0f, rb)
            if (out) gatR[a] = r
        } else {
            val q = ((tg - (gatStart(i) + (GAT_HF + GAT_HOLD) * GAT_CYC)) / GAT_RET).coerceIn(0f, 1f)
            val we = smoother(q / 0.75f)
            val wa = smoother((q - 0.2f) / 0.8f)
            gaSh = lerp(shI, rSh, wa)
            gaZ = lerp(zI, rZ, wa)
            gaEl = lerp(0f, rEl, wa)
            gaExt = lerp(e, 1f, we)
        }
    }

    // ───────────── estado de animação ─────────────
    private const val MS = 0.8f   // escala do modelo (altura parecida com a dos outros heróis)
    private const val NP = 19
    private var yaw = 0f
    private var airW = 0f
    private var rollW = 0f
    private var blend = 0f
    private var clock = 0f
    private var lastNs = 0L
    private var prevPhase = 0f
    private var spd = 0f
    private var prevFlash = 0f
    private var hitKick = 0f
    private var landDip = 0f
    private var airTimer = 0f
    private var wasAir = false
    private var eyeOpen = 1f
    private var released = false
    private var dashBurst = false
    private var touchBurst = false
    private var prevDashPh = 0
    private var lastPitch2 = DIVE_PITCH
    private var lastSpin2 = 0f
    private var touchPitch = DIVE_PITCH
    private var touchSpin = 0f
    private var spinEnd = 0f
    private var liftS = 0f
    private var lastExt1 = 1f
    private var liftOn = false
    private var lastBodyZ1 = 0f
    private var lastRsh1 = -1.2f
    private var lastLean1 = 0f
    private var lastCrouch1 = 0f
    private var lastTilt1 = 0f
    private var lastPull = 0f
    private var latS = 0f       // velocidade lateral suavizada (unid. do modelo/s, + = esquerda)
    private var latW = 0f       // fase do passo lateral
    private var lastShZ1 = 0.05f
    private var stepW = 0f       // fase do passo ao andar de costas puxando o corpo
    private var heliAng = 0f
    private var heliOmega = 0f
    private var heliAirW = 0f   // peso da pose "no ar" das pernas/pés (esticadas)
    private var heliArmW = 0f   // peso dos braços abertos
    private var heliArcA = 0f   // intensidade dos arcos de vento
    private var heliReleased = false  // o quadril já se soltou (começou a hélice)
    private var wasHeli = false
    private var landSoft = false  // pouso do helicóptero: afunda e levanta devagar
    private val heliRect = RectF()

    // rastro do voo: histórico de posições (coordenadas do mundo) com idade
    private const val NT = 40
    private const val TR_LIFE = 0.32f
    private val trX = FloatArray(NT)
    private val trY = FloatArray(NT)
    private val trA = FloatArray(NT)
    private var trHead = 0
    private var trN = 0
    private var trPrevOn = false
    private val ps = FloatArray(NP)
    private val pt = FloatArray(NP)
    private val mkX = FloatArray(6)
    private val mkY = FloatArray(6)

    private const val NSP = 64
    private val spX = FloatArray(NSP)
    private val spY = FloatArray(NSP)
    private val spVx = FloatArray(NSP)
    private val spVy = FloatArray(NSP)
    private val spL = FloatArray(NSP)
    private val spM = FloatArray(NSP)
    private val spG = FloatArray(NSP)
    private val spC = IntArray(NSP)
    private val spZ = FloatArray(NSP)
    private var spNext = 0

    private fun emitSpark(x: Float, y: Float, vx: Float, vy: Float, life: Float, g: Float, col: Int, size: Float) {
        val i = spNext
        spNext = (spNext + 1) % NSP
        spX[i] = x
        spY[i] = y
        spVx[i] = vx
        spVy[i] = vy
        spL[i] = life
        spM[i] = life
        spG[i] = g
        spC[i] = col
        spZ[i] = size
    }

    /**
     * Interpola o ciclo do soco: repouso → preparação suave (até PW) → golpe estalado (até T_HIT, começa rápido e
     * freia no alvo) → segura no alvo (até PR, sem parar de repente) → volta suave ao repouso (smoother: sem tranco).
     */
    private fun k5(t: Float, a: Float, b: Float, c: Float, d: Float, e: Float): Float {
        return if (t < PW) lerp(a, b, ease(t / PW))
        else if (t < T_HIT) {
            val q = (t - PW) / (T_HIT - PW)
            lerp(b, c, 1f - (1f - q) * (1f - q))
        }
        else if (t < PR) lerp(c, d, ease((t - T_HIT) / (PR - T_HIT)))
        else lerp(d, e, smoother((t - PR) / (1f - PR)))
    }

    /** Igual ao k5, mas a volta usa o peso [w] dado (o braço volta em etapas: primeiro o comprimento, depois o ombro). */
    private fun k5x(t: Float, a: Float, b: Float, c: Float, d: Float, e: Float, w: Float): Float {
        return if (t < PR) k5(t, a, b, c, d, e) else lerp(d, e, w)
    }

    // pontos do corpo (z para frente, y para cima) relativos ao pivô: em pé e encolhido (bola da rolagem)
    private val BZ0 = floatArrayOf(0f, -5f, 6f, 5f, -5f, 4f, -5f, 3f, 6f, -2f)
    private val BY0 = floatArrayOf(27f, 19f, 19f, 8f, 5f, -2f, -2f, -9f, -16f, -16f)
    private val BZ1 = floatArrayOf(5f, -2f, 7f, 8f, -9f, 8f, -8f, 8f, 7f, -3f)
    private val BY1 = floatArrayOf(11f, 9f, 10f, 3f, 2f, -2f, -2f, 2f, -8f, -8f)

    /**
     * Quanto o modelo precisa subir (+) ou descer (-) para o ponto mais baixo do corpo, girado em [pitch],
     * ficar rente ao chão (deitado ele fica mais baixo que em pé; encolhido, a bola é pequena).
     */
    private fun pivotLift(pitch: Float, tuck: Float): Float {
        val c = cos(pitch)
        val sn = sin(pitch)
        var low = 1e9f
        for (i in BZ0.indices) {
            val z = lerp(BZ0[i], BZ1[i], tuck)
            val y = lerp(BY0[i], BY1[i], tuck)
            val yy = c * y - sn * z
            if (yy < low) low = yy
        }
        return -low - PIV
    }

    // ───────────── desenho principal ─────────────

    fun draw(
        c: Canvas, p: Paint, fx: Float, fy: Float, s: Float, phase: Float,
        moving: Boolean, faceX: Float, faceY: Float, attack: Float
    ) {
        val now = System.nanoTime()
        val first = lastNs == 0L
        val dt = if (first) 0.016f else ((now - lastNs) / 1_000_000_000f).coerceIn(0.001f, 0.1f)
        lastNs = now
        clock += dt
        val rnd = kotlin.random.Random

        // ── Gear Second: tinta da pele (sobe durante o bombeamento, depois segue o buff) ──
        val tg2 = if (g2P >= 0f) g2P * G2_DUR else -1f
        val tintW = if (tg2 >= 0f) ease((tg2 - G2_P0) / (G2_PUMP - G2_P0)) else g2Act
        g2W = if (first) tintW else g2W + (tintW - g2W) * min(1f, dt * 12f)
        setSkin(g2W)

        // ── mira em coordenadas do modelo ──
        // O modelo é achatado na vertical (y da tela = z*SINP - altura*COSP), então um alvo em px de tela precisa ser
        // convertido de volta: assim o punho (ou a mão presa no chão) cai exatamente no ponto da tela.
        val rsE = s * PX * MS
        val tAtk = if (attack > 0.001f) 1f - attack else -1f
        if (tAtk < 0.03f) pmOk = false
        val gatOn = skillP >= 0f && gatActive && rollP < 0f && !heli && heliCharge < 0f
        var aimOn = false
        var aimPunch = false
        var aimX = 0f
        var aimY = 0f
        var aimZ = 0f
        if (rollP >= 0f) {
            if (dashPh == 1 && anchorOn) {
                aimOn = true
                aimX = anchorTX / rsE
                aimY = HAND_Y
                aimZ = (anchorTY / rsE + aimY * COSP) / SINP
            }
        } else if (gatOn) {
            // Gatling: o corpo se vira para o meio do retângulo (mesma conversão tela -> modelo do soco)
            aimOn = true
            aimX = gatDX * 0.6f * gatLen / gatSqX / rsE
            aimZ = gatDY * 0.6f * gatLen / gatSqY / (rsE * SINP)
        } else if (tAtk >= 0f && !heli && heliCharge < 0f) {
            if (tAtk < T_HIT && punchOn) {
                // até o impacto o punho persegue o alvo; guarda o ponto a cada quadro
                var py = ((punchGY - punchTY) / (rsE * COSP)).coerceIn(16f, 46f)   // altura do peito do alvo
                var px = punchTX / rsE
                var pz = (punchTY / rsE + py * COSP) / SINP
                val dist0 = hypot(px, pz)
                if (dist0 > 1f) {
                    val kk = max(0.2f, (dist0 - punchInset / rsE) / dist0)   // para na superfície do corpo
                    px *= kk
                    pz *= kk
                }
                pmX = px
                pmY = py
                pmZ = pz
                pmOk = true
            }
            if (pmOk) {
                // depois do impacto o alvo fica TRAVADO onde o punho bateu: o inimigo ser empurrado ou morrer
                // não arrasta nem teleporta mais o braço (era isso que dava o tranco na volta)
                aimOn = true
                aimPunch = true
                aimX = pmX
                aimY = pmY
                aimZ = pmZ
            }
        }
        val hasAimYaw = aimOn && hypot(aimX, aimZ) > 4f

        // direção suave (com mira: o corpo se vira para o ponto exato)
        if (hasAimYaw || abs(faceX) + abs(faceY) > 0.01f) {
            val target = if (hasAimYaw) atan2(aimX, aimZ) else atan2(faceX, faceY)
            if (first) {
                yaw = target
            } else {
                var d = target - yaw
                while (d > PI.toFloat()) d -= TWO_PI
                while (d < -PI.toFloat()) d += TWO_PI
                yaw += d * min(1f, dt * (if (attack > 0.001f || aimOn) 30f else 16f))
            }
        }
        blend += ((if (moving) 1f else 0f) - blend) * min(1f, dt * 12f)
        val dPh = abs(phase - prevPhase)
        prevPhase = phase
        val spdTgt = if (first) 0f else (dPh / dt / 9.6f).coerceIn(0f, 1.6f)
        spd += (spdTgt - spd) * min(1f, dt * 10f)
        val spN = min(spd, 1f)

        val t = if (attack > 0.001f) 1f - attack else -1f
        val attacking = t >= 0f
        val rolling = rollP >= 0f
        val airCand = inAir && !rolling && (airH > 8f || airVz > 60f)
        airTimer = if (airCand) airTimer + dt else 0f
        val airT = if (airTimer > 0.04f) 1f else 0f
        // o dash entra de uma vez e só se dissolve nos últimos 18% do "levantar" (a pose já é a de repouso)
        val rollTgt = if (rolling) (if (dashPh == 3 && dashFlip && dashU > 0.55f) ((1f - dashU) / 0.45f).coerceIn(0f, 1f) else if (dashPh == 3 && dashU > 0.82f) ((1f - dashU) / 0.18f).coerceIn(0f, 1f) else 1f) else 0f
        if (first) {
            airW = airT
            rollW = rollTgt
        } else {
            airW += (airT - airW) * min(1f, dt * 14f)
            rollW += (rollTgt - rollW) * min(1f, dt * 26f)
        }
        if (wasAir && airW < 0.4f) {
            wasAir = false
            landDip = max(landDip, 1f)
        } else if (airW > 0.7f) {
            wasAir = true
        }
        val fl = VoxelArt.flash
        if (!first && fl > prevFlash + 0.25f) hitKick = 1f
        prevFlash = fl
        hitKick = max(0f, hitKick - dt * 4.5f)
        landDip = max(0f, landDip - dt * (if (landSoft) 2.6f else 5.5f))
        if (landDip <= 0f) landSoft = false
        val bp = clock % 3.4f
        eyeOpen = if (bp < 0.14f) max(0.12f, abs(bp - 0.07f) / 0.07f) else 1f
        val vzN = (airVz / 340f).coerceIn(-1.2f, 1f)
        val still = 1f - max(rollW, airW)
        val mvs = still * blend
        val bob = abs(sin(phase)) * 0.9f * mvs + sin(clock * 2.2f) * 0.12f * (1f - mvs)

        // ── alvos da pose (repouso, caminhada, ar, rolamento) ──
        var lean = 0.05f + 0.12f * spN
        var hipTw = 0f
        var chestTw = -sin(phase) * 0.16f * mvs
        var rsh = -0.1f + sin(phase) * 0.75f * mvs
        var lsh = -0.1f - sin(phase) * 0.75f * mvs
        var rshZ = 0.14f + 0.03f * sin(clock * 1.4f)
        var lshZ = 0.14f + 0.03f * sin(clock * 1.4f + 1f)
        var rel = -0.3f - 0.55f * mvs
        var lel = -0.3f - 0.55f * mvs
        var hd = 0f
        var tilt = 0f
        var shout = 0f
        var crouch = 0f
        var legB = 0f

        // no ar: braços abertos para cima, como quem comemora
        rshZ = lerp(rshZ, 1.25f, airW)
        lshZ = lerp(lshZ, 1.25f, airW)
        rsh = lerp(rsh, -0.35f, airW)
        lsh = lerp(lsh, -0.35f, airW)
        rel = lerp(rel, -0.2f, airW)
        lel = lerp(lel, -0.2f, airW)
        shout = max(shout, 0.5f * airW)
        // ── dash estilingue (Gomu Gomu no Rocket) ──
        // fase 1: as mãos esticam e agarram o chão à frente; segurar o botão puxa o corpo mais para trás (carga)
        // fase 2: lançado em arco, deitado e girando em torno do próprio eixo, braços à frente
        // fase 3: pousa de mergulho, faz uma rolagem para a frente e levanta de pé
        val rsNow = s * PX * MS
        var extR = 1f
        var extL = 1f
        var bodyZ = 0f
        var legW = 0f
        var legA = 0f
        var legK = 0f
        var bPitch = 0f
        var bSpin = 0f
        var bTuck = 0f
        var stepAmt = 0f
        var plant = false      // fase 1 do dash: mãos presas no chão (IK)
        var punchIK = false    // soco calculado por IK (pose exata, sem suavização)
        if (rolling) {
            val du = dashU
            val cc = dashCharge
            var dLean = 0.05f
            var dCrouch = 0f
            var dRsh = -0.1f
            var dRshZ = 0.05f
            var dRel = -0.05f
            var dExt = 1f
            var dTilt = 0f
            when (dashPh) {
                1 -> {
                    val u = ease(du / Player.LUFFY_WIND_MIN)
                    val ccS = ease(cc)            // a carga entra devagar: a puxada não dá tranco ao passar do preparo para a carga
                    dLean = 0.8f * u + 0.18f * ccS
                    dCrouch = 1.0f * u + 0.3f * ccS
                    val pull = 6f * u + 11f * ccS
                    bodyZ = -pull + sin(clock * 55f) * 0.5f * cc   // treme quando está carregado
                    // andando de costas: os passos acompanham a distância puxada (pé não desliza no chão)
                    if (prevDashPh != 1) {
                        stepW = 0f
                        lastPull = 0f
                    }
                    val dPull = pull - lastPull
                    stepW += dPull * STEP_K
                    lastPull = pull
                    // parou de puxar: termina o passo e assenta com um pé à frente e outro atrás (postura firme, sem pé no ar)
                    val hold = (1f - abs(dPull) / (dt * 4f)).coerceIn(0f, 1f)
                    if (hold > 0f) {
                        val tgt = (Math.round(stepW / PI.toFloat() - 0.5f) + 0.5f) * PI.toFloat()
                        stepW += (tgt - stepW) * min(1f, dt * 7f) * hold
                    }
                    stepAmt = 1f
                    plant = true       // os braços são resolvidos por IK depois (mãos paradas no chão)
                    dTilt = -0.2f * u
                    lastLean1 = dLean
                    lastCrouch1 = dCrouch
                    lastTilt1 = dTilt
                    lastBodyZ1 = -pull
                    lastPitch2 = DIVE_PITCH
                    lastSpin2 = 0f
                }
                2 -> {
                    val turns = if (cc > 0.45f) 2f else 1f
                    bPitch = (PI.toFloat() / 2f) * ease(du / 0.2f) + 0.22f * ease((du - 0.78f) / 0.22f)
                    bSpin = TWO_PI * turns * ease((du - 0.1f) / 0.8f)
                    lastPitch2 = bPitch
                    lastSpin2 = bSpin
                    val sn = ease(du / 0.22f)
                    dLean = lerp(lastLean1, 0.05f, sn)
                    dCrouch = lerp(lastCrouch1, 0f, sn)
                    dRsh = lerp(lastRsh1, -2.95f, sn)
                    dRshZ = lerp(lastShZ1, 0.1f, sn)
                    dRel = -0.05f
                    dExt = lerp(lastExt1, 1f, ease(du / 0.18f))   // as mãos soltam o chão e os braços voltam como elástico
                    dTilt = lerp(lastTilt1, -1.15f, sn)
                    // o corpo é puxado para frente pela borracha: o recuo some em ~0,1 s (efeito estilingue)
                    bodyZ = lastBodyZ1 * (1f - ease(du / 0.16f))
                    legW = ease(du / 0.15f)
                    legA = 0.08f
                    legK = 0.25f
                    if (dashFlip) {
                        // mortal: o corpo gira para a frente (pitch 0→2π), encolhido no meio do giro e aberto antes de pousar
                        bPitch = TWO_PI * ease((du - 0.06f) / 0.82f)
                        bSpin = 0f
                        lastPitch2 = bPitch
                        lastSpin2 = 0f
                        bTuck = ease((du - 0.08f) / 0.22f) * (1f - ease((du - 0.62f) / 0.26f))
                        val sn2 = ease(du / 0.2f)
                        dLean = lerp(lerp(lastLean1, 0.05f, sn2), 0.9f, bTuck)
                        dCrouch = lerp(lerp(lastCrouch1, 0f, sn2), 1.2f, bTuck)
                        dRsh = lerp(lerp(lastRsh1, -2.9f, sn2), -0.9f, bTuck)
                        dRshZ = 0.12f
                        dRel = lerp(-0.05f, -2.3f, bTuck)
                        dTilt = lerp(lastTilt1, 0f, sn2)
                        legW = ease(du / 0.12f)
                        val reach = ease((du - 0.8f) / 0.2f)   // estica as pernas à frente para pousar com os pés
                        legA = lerp(0.1f, -1.35f, bTuck) - 0.3f * reach
                        legK = lerp(0.3f, 1.95f, bTuck) + 0.1f * reach
                    }
                }
                else -> {
                    if (prevDashPh != 3) {
                        touchPitch = lastPitch2
                        touchSpin = lastSpin2
                        spinEnd = Math.ceil(((lastSpin2 - 0.01f) / TWO_PI).toDouble()).toFloat() * TWO_PI   // termina o giro para a frente
                    }
                    val stand = ease((du - 0.68f) / 0.32f)
                    val cr = ease(du / 0.18f) * (1f - stand)
                    bTuck = ease(du / 0.18f) * (1f - ease((du - 0.5f) / 0.24f))
                    bPitch = lerp(touchPitch, TWO_PI, ease(du / 0.72f))
                    bSpin = lerp(touchSpin, spinEnd, ease(du / 0.25f))
                    val fold = ease((du - 0.12f) / 0.14f)    // braços: esticados à frente → recolhidos
                    val unfold = ease((du - 0.55f) / 0.35f)  // soltam ao levantar
                    dLean = lerp(0.05f, 0.95f, bTuck) + 0.35f * cr * (1f - bTuck)
                    dCrouch = 1.25f * bTuck + 0.9f * cr * (1f - bTuck)
                    dRsh = lerp(lerp(-2.95f, -0.9f, fold), -0.1f, unfold)
                    dRshZ = 0.1f
                    dRel = lerp(lerp(-0.05f, -2.3f, fold), -0.3f, unfold)
                    dTilt = lerp(-1.15f, 0.5f, ease(du / 0.14f)) * (1f - stand)
                    legW = 1f - ease((du - 0.5f) / 0.25f)
                    legA = lerp(0.08f, -1.45f, ease(du / 0.16f))
                    legK = lerp(0.25f, 2.0f, ease(du / 0.16f))
                    if (dashFlip) {
                        // pouso de pé do mortal: amortece nos joelhos até ~38% e levanta; os braços descem atrasados (follow-through)
                        val dn = ease(du / 0.38f)
                        val upk = ease((du - 0.38f) / 0.62f)
                        val dip = dn * (1f - upk)
                        val armT = ease((du - 0.1f) / 0.85f)
                        val swg = sin(PI.toFloat() * armT)
                        bTuck = 0f
                        bPitch = TWO_PI
                        bSpin = 0f
                        dLean = 0.38f * dip
                        dCrouch = 1.15f * dip
                        dRsh = lerp(-2.9f, -0.1f, armT) + 0.2f * swg
                        dRshZ = lerp(0.12f, 0.14f, armT)
                        dRel = lerp(-0.05f, -0.3f, armT) - 0.25f * swg
                        dTilt = 0.15f * dip
                        legW = 1f - ease((du - 0.25f) / 0.6f)
                        legA = lerp(-0.3f, -0.4f * dip, ease(du / 0.15f))
                        legK = lerp(0.4f, 0.12f + 1.25f * dip, ease(du / 0.15f))
                    }
                }
            }
            lean = lerp(lean, dLean, rollW)
            crouch = lerp(crouch, dCrouch, rollW)
            rsh = lerp(rsh, dRsh, rollW)
            lsh = lerp(lsh, dRsh, rollW)
            rshZ = lerp(rshZ, dRshZ, rollW)
            lshZ = lerp(lshZ, dRshZ, rollW)
            rel = lerp(rel, dRel, rollW)
            lel = lerp(lel, dRel, rollW)
            tilt = lerp(tilt, dTilt, rollW)
            shout = max(shout, (if (dashPh == 3) 0.3f else 0.8f) * rollW)
            extR = lerp(1f, dExt, rollW)
            extL = extR
            bodyZ *= rollW
        }
        prevDashPh = if (rolling) dashPh else 0
        // mãos presas no chão (fase 1): o corpo se mexe (puxa, treme, anda de lado), as mãos NÃO.
        // Os braços são resolvidos a cada quadro com a pose real do corpo, esticando/girando para manter o punho no ponto do chão.
        if (plant && aimOn) {
            val crT = max(crouch, ease(landDip) * 0.5f * (if (landSoft) 1.7f else 1f))
            val hipH = 14.8f + bob - crT * 3f - hitKick * 0.4f
            val leanE = lean - hitKick * 0.3f
            val zOff = bodyZ - hitKick * 1.2f
            val lx = cos(yaw)
            val lz = -sin(yaw)
            solveArm(-1f, aimX - lx * HAND_SEP, aimY, aimZ - lz * HAND_SEP, hipH, leanE, zOff, yaw)
            val shR = ikSh
            val zR = ikShZ
            val eR = ikExt
            solveArm(1f, aimX + lx * HAND_SEP, aimY, aimZ + lz * HAND_SEP, hipH, leanE, zOff, yaw)
            val shL = ikSh
            val zL = ikShZ
            val eL = ikExt
            rsh = lerp(rsh, shR, rollW)
            lsh = lerp(lsh, shL, rollW)
            rshZ = lerp(rshZ, zR, rollW)
            lshZ = lerp(lshZ, zL, rollW)
            rel = lerp(rel, 0f, rollW)
            lel = lerp(lel, 0f, rollW)
            extR = lerp(1f, eR, rollW)
            extL = lerp(1f, eL, rollW)
            lastExt1 = (eR + eL) * 0.5f
            lastRsh1 = (shR + shL) * 0.5f
            lastShZ1 = (zR + zL) * 0.5f
        }
        val lgR = rollW * legW

        // ── pulo carregado: Gomu Gomu no Helicóptero ──
        // segurar (chão): agacha fundo, braços para trás, parado (não anda)
        // soltar: salta alto; enquanto sobe, SÓ o quadril enrola rápido para um lado (pernas juntas, peito de frente)
        // e então solta de uma vez: as pernas abrem na horizontal e giram como hélice
        // descendo: perto do chão a hélice desacelera, fecha, e os joelhos amortecem o pouso
        var hDown = 0f
        var hSpin = 0f
        var hSplay = 0f
        var hLen = 1f
        var hHipTw = 0f
        var hAirTgt = 0f
        var hArmTgt = 0f
        var hCompact = 0f
        if (!rolling && heliCharge >= 0f) {
            val u = ease(heliCharge / 0.35f)
            val hold = ease((heliCharge - 0.35f) / 0.3f)
            heliAng = 0f
            heliOmega = 0f
            heliReleased = false
            crouch = max(crouch, 1.15f * u) + sin(clock * 42f) * 0.025f * hold   // treme de força quando carregado
            lean = lerp(lean, 0.34f, u)
            rsh = lerp(rsh, 0.75f, u)       // braços puxados para trás, tomando impulso
            lsh = lerp(lsh, 0.75f, u)
            rshZ = lerp(rshZ, 0.3f, u)
            lshZ = lerp(lshZ, 0.3f, u)
            rel = lerp(rel, -0.9f, u)
            lel = lerp(lel, -0.9f, u)
            tilt = lerp(tilt, -0.15f, u)
            shout = max(shout, 0.35f * u)
        } else if (!rolling && heli) {
            val ht = heliT
            if (ht < HELI_REL_T) {
                // sobe com as pernas juntas: só o quadril enrola, rápido (o peito continua de frente)
                heliReleased = false
                val w = ease(ht / HELI_WIND_T)
                heliAng = -HELI_WIND_MAX * w
                heliOmega = 0f
                hSpin = heliAng
                hHipTw = 0.6f * heliAng
                hAirTgt = 0.65f
                hCompact = ease(ht / 0.08f)
            } else {
                // solta de uma vez: o quadril desenrola e a hélice já sai rápida, abrindo as pernas
                val rt = ht - HELI_REL_T
                if (!heliReleased) {
                    heliReleased = true
                    heliOmega = HELI_OMEGA * 1.8f
                }
                hDown = if (airVz < 0f) 1f - ease(airH / 60f) else 0f
                val dn = ease(hDown)
                val open = 1f - dn
                val tgtW = HELI_OMEGA * (1f - 0.85f * dn)
                heliOmega += (tgtW - heliOmega) * min(1f, dt * (if (rt < 0.3f) 5f else 9f))
                var sp = heliOmega
                if (hDown > 0.02f) {
                    // perto do chão: para exatamente de frente (múltiplo de 2π), de forma suave
                    val nr = Math.ceil((heliAng / TWO_PI).toDouble()).toFloat() * TWO_PI
                    val cap = max(0f, nr - heliAng) * (5f + 12f * hDown)
                    sp = lerp(sp, min(sp, cap), ease(hDown * 4f))
                }
                heliAng += sp * dt
                hSpin = heliAng
                hHipTw = 0.6f * -HELI_WIND_MAX * (1f - ease(rt / 0.14f))
                hSplay = 1.42f * ease((rt - 0.02f) / 0.2f) * open
                hLen = lerp(1f, lerp(1.08f, 1.38f, ease(rt / 0.26f)), open)
                hAirTgt = open
                hArmTgt = open
                hCompact = 1f - ease(rt / 0.1f)
                // joelhos se preparam para amortecer antes de tocar o chão
                crouch = max(crouch, 0.45f * dn)
            }
        } else {
            heliAng = 0f
            heliOmega = 0f
            heliReleased = false
        }
        heliAirW += (hAirTgt - heliAirW) * min(1f, dt * 14f)
        heliArmW += (hArmTgt - heliArmW) * min(1f, dt * 14f)
        val arcTgt = if (!rolling && heli && heliT >= HELI_REL_T) min(1f, heliOmega / HELI_OMEGA) * (1f - ease(hDown)) else 0f
        heliArcA += (arcTgt - heliArcA) * min(1f, dt * 16f)
        gLegSpin = hSpin
        gLegSplay = hSplay + gatSplay
        gLegLen = hLen
        // o quadril gira, mas o peito continua de frente (contra-giro)
        hipTw += hHipTw
        chestTw -= hHipTw
        if (hCompact > 0.001f) {
            // enquanto só o quadril gira: braços recolhidos junto do corpo
            rshZ = lerp(rshZ, 0.5f, hCompact)
            lshZ = lerp(lshZ, 0.5f, hCompact)
            rsh = lerp(rsh, -0.3f, hCompact)
            lsh = lerp(lsh, -0.3f, hCompact)
            rel = lerp(rel, -0.7f, hCompact)
            lel = lerp(lel, -0.7f, hCompact)
        }
        if (heliArmW > 0.001f) {
            // braços abertos para o alto, boca aberta (grita o golpe)
            rshZ = lerp(rshZ, 1.45f, heliArmW)
            lshZ = lerp(lshZ, 1.45f, heliArmW)
            rsh = lerp(rsh, -0.15f, heliArmW)
            lsh = lerp(lsh, -0.15f, heliArmW)
            rel = lerp(rel, -0.1f, heliArmW)
            lel = lerp(lel, -0.1f, heliArmW)
            lean = lerp(lean, 0.0f, heliArmW)
            shout = max(shout, 0.9f * heliArmW)
        }
        // ao pousar do helicóptero: afunda nos joelhos e levanta devagar
        val heliNow = heli && !rolling
        if (wasHeli && !heliNow) {
            landDip = 1f
            landSoft = true
        }
        wasHeli = heliNow

        // ── soco: Gomu Gomu no Pistol direto no alvo ──
        // preparação: o corpo enrola para trás, afunda, o braço dobra junto ao peito e o outro protege o rosto
        // golpe: quadril, peito e ombro desenrolam em sequência e o braço dispara até a distância exata do alvo
        // volta: o braço segura estendido um instante e encolhe como elástico
        val extReach = extFor(punchLen / rsNow)
        var punchSide = 0 // 1 direito, 2 esquerdo
        if (g2Act > 0.5f && attacking && rollW < 0.5f && heliCharge < 0f && !heli) {
            // Gear Second: o braço só levanta de leve e volta, rapidíssimo. O soco NÃO estica até o alvo (o impacto aparece
            // direto no inimigo, dá a impressão de velocidade que nem dá para ver o golpe).
            val sdp = if (swing == 0) 1f else -1f
            val f = sin(PI.toFloat() * (t / 0.8f).coerceIn(0f, 1f))
            val mSh0 = if (sdp > 0f) rsh else lsh
            val mEl0 = if (sdp > 0f) rel else lel
            val mZ0 = if (sdp > 0f) rshZ else lshZ
            val mSh = lerp(mSh0, -0.95f, f)
            val mEl = lerp(mEl0, -1.45f, f)
            val mZ = lerp(mZ0, 0.22f, f)
            if (sdp > 0f) { rsh = mSh; rel = mEl; rshZ = mZ } else { lsh = mSh; lel = mEl; lshZ = mZ }
            lean = lerp(lean, lean + 0.16f, f)
            chestTw = lerp(chestTw, chestTw - 0.14f * sdp, f)
            crouch = lerp(crouch, max(crouch, 0.2f), f)
            if (t >= 0.38f && !released) {
                released = true
                val k = if (sdp > 0f) 0 else 1
                for (i in 0..2) {
                    val ang = rnd.nextFloat() * TWO_PI
                    val sp0 = (16f + rnd.nextFloat() * 24f) * rs
                    emitSteam(mkX[k], mkY[k], cos(ang) * sp0, sin(ang) * sp0 - 8f * rs, 0.35f + rnd.nextFloat() * 0.2f, (1.4f + rnd.nextFloat() * 1.2f) * rs)
                }
            }
        } else if (attacking && rollW < 0.5f && heliCharge < 0f && !heli) {
            val sdp = if (swing == 0) 1f else -1f
            punchSide = if (swing == 0) 1 else 2
            val m0 = if (sdp > 0f) rsh else lsh
            val g0 = if (sdp > 0f) lsh else rsh
            val mZ0 = if (sdp > 0f) rshZ else lshZ
            val gZ0 = if (sdp > 0f) lshZ else rshZ
            val mE0 = if (sdp > 0f) rel else lel
            val gE0 = if (sdp > 0f) lel else rel
            // valores do braço no impacto (c) e na segurada (d): por padrão o antigo (sem alvo na tela) ...
            var cSh = -1.55f
            var dSh = -1.5f
            var cZ = 0.0f
            var dZ = 0.02f
            var cEl = -0.05f
            var dEl = -0.15f
            var cExt = extReach
            var dExtP = extReach * 0.97f
            var cCh = -0.3f * sdp
            var dCh = -0.25f * sdp
            var cHp = 0.3f * sdp
            var dHp = 0.25f * sdp
            if (aimPunch) {
                // ... com alvo: IK com a pose exata do impacto (inclinação 0,36 / agachado 0,3 / corpo +4) e da segurada.
                // Peito e quadril não torcem no golpe, então o braço sai reto do ombro até o peito do inimigo.
                val side = -sdp
                solveArmT(side, aimX, aimY, aimZ, 14.8f + bob - 0.3f * 3f - hitKick * 0.4f, 0.36f - hitKick * 0.3f, 4f - hitKick * 1.2f, yaw, 0f)
                cSh = ikSh; cZ = ikShZ; cExt = ikExt
                solveArmT(side, aimX, aimY, aimZ, 14.8f + bob - 0.25f * 3f - hitKick * 0.4f, 0.30f - hitKick * 0.3f, 3f - hitKick * 1.2f, yaw, 0f)
                dSh = ikSh; dZ = ikShZ; dExtP = ikExt
                cEl = 0f; dEl = 0f
                cCh = 0f; dCh = 0f; cHp = 0f; dHp = 0f
                punchIK = true
            }
            // volta em etapas: o braço encolhe primeiro pela linha do soco (retE) e só então o ombro relaxa (retA),
            // senão o braço ainda comprido varreria um arco enorme (efeito chicote)
            val qR = ((t - PR) / (1f - PR)).coerceIn(0f, 1f)
            val retE = smoother(qR / 0.75f)
            val retA = smoother((qR - 0.2f) / 0.8f)
            val mSh = k5x(t, m0, 1.1f, cSh, dSh, m0, retA)
            val mZ = k5x(t, mZ0, 0.55f, cZ, dZ, mZ0, retA)
            val mEl = k5x(t, mE0, -2.5f, cEl, dEl, mE0, retA)
            val gSh = k5(t, g0, -1.0f, -0.5f, -0.6f, g0)
            val gZ = k5(t, gZ0, 0.25f, 0.3f, 0.3f, gZ0)
            val gEl = k5(t, gE0, -2.1f, -2.2f, -2.1f, gE0)
            val mExt = k5x(t, 1f, 1f, cExt, dExtP, 1f, retE)
            if (sdp > 0f) {
                rsh = mSh; rshZ = mZ; rel = mEl; extR = mExt
                lsh = gSh; lshZ = gZ; lel = gEl
            } else {
                lsh = mSh; lshZ = mZ; lel = mEl; extL = mExt
                rsh = gSh; rshZ = gZ; rel = gEl
            }
            // peito + quadril somam zero no golpe: o braço aponta exatamente para o alvo (só a preparação gira o corpo)
            chestTw = k5(t, chestTw, -0.75f * sdp, cCh, dCh, chestTw)
            hipTw = k5(t, hipTw, -0.4f * sdp, cHp, dHp, hipTw)
            lean = k5(t, lean, -0.1f, 0.36f, 0.3f, lean)
            crouch = k5(t, crouch, 0.5f, 0.3f, 0.25f, crouch)
            tilt = k5(t, tilt, 0.12f, -0.12f, -0.08f, tilt)
            legB = k5(t, 0f, 0.55f * sdp, 1f * sdp, 0.9f * sdp, 0f)
            bodyZ = k5(t, bodyZ, -2f, 4f, 3f, bodyZ)
            shout = k5(t, shout, 0.25f, 1f, 0.8f, shout)
            if (t >= T_HIT && !released) {
                released = true
                val k = punchSide - 1
                for (i in 0..8) {
                    val ang = rnd.nextFloat() * TWO_PI
                    val sp0 = (14f + rnd.nextFloat() * 34f) * rs
                    emitSpark(mkX[k], mkY[k], cos(ang) * sp0, sin(ang) * sp0, 0.3f, rs * 40f, if (i % 2 == 0) 0xFFFFFFFF.toInt() else 0xFFFFC23A.toInt(), 1.0f)
                }
                if (g2W > 0.3f) {
                    // Gear Second: o soco sai soltando vapor
                    for (i in 0..5) {
                        val ang = rnd.nextFloat() * TWO_PI
                        val sp0 = (20f + rnd.nextFloat() * 40f) * rs
                        emitSteam(mkX[k], mkY[k], cos(ang) * sp0, sin(ang) * sp0 - 10f * rs, 0.4f + rnd.nextFloat() * 0.3f, (2.4f + rnd.nextFloat() * 2f) * rs)
                    }
                }
            }
        } else {
            released = false
        }

        // ── Gomu Gomu no Gatling ──
        // preparação: agacha de pernas abertas, os dois braços armados | barragem: socos alternados em pontos do retângulo,
        // o peito torce para o lado do braço que soca e o corpo "bombeia" a cada impacto | fim: o último soco de cada braço
        // volta ao repouso em etapas enquanto o corpo relaxa. Tudo é função contínua do tempo (peso wB): começa e termina na pose de repouso.
        var gatIK = false
        gatSplay = 0f
        if (gatOn && rollW < 0.5f) {
            gatIK = true
            val tg = skillP * GAT_DUR
            val tb = gatHit(GAT_N - 1)
            val wB = ease(tg / GAT_WIND) * (1f - smoother((tg - tb) / (GAT_DUR - tb)))
            val env = ease((tg - (GAT_T0 - 0.12f)) / 0.12f) * (1f - ease((tg - tb) / 0.28f))
            val wave = cos(PI.toFloat() * (tg - GAT_T0) / GAT_DT)                     // +1 impacto direito, -1 esquerdo
            val pump = 0.5f + 0.5f * cos(TWO_PI * (tg - GAT_T0) / GAT_DT)             // 1 em cada impacto
            val ant = if (tg < GAT_WIND) sin(PI.toFloat() * ease(tg / GAT_WIND)) else 0f
            val rSh0 = rsh
            val rZ0 = rshZ
            val rEl0 = rel
            val lSh0 = lsh
            val lZ0 = lshZ
            val lEl0 = lel
            lean = lerp(lean, 0.26f + 0.04f * pump * env, wB) - 0.14f * ant
            crouch = lerp(crouch, 0.55f + 0.05f * pump * env, wB) + 0.18f * ant
            bodyZ = lerp(bodyZ, 1.5f + 0.8f * pump * env, wB) - 1.5f * ant
            chestTw = chestTw * (1f - wB) + wB * env * 0.22f * wave
            hipTw *= (1f - wB)
            tilt = lerp(tilt, 0.1f, wB)
            shout = max(shout, 0.95f * wB)
            legB = 0f
            gatSplay = 0.20f * wB
            // pose exata do corpo neste quadro (a mesma que o esqueleto vai usar): a IK acerta o punho no ponto
            val crG = max(crouch, ease(landDip) * 0.5f * (if (landSoft) 1.7f else 1f))
            val hipG = 14.8f + bob - crG * 3f - hitKick * 0.4f
            val leanG = lean - hitKick * 0.3f
            val zG = bodyZ - hitKick * 1.2f
            gatArm(0, tg, hipG, leanG, zG, chestTw, rSh0, rZ0, rEl0, rsE)
            rsh = gaSh; rshZ = gaZ; rel = gaEl; extR = gaExt
            gatArm(1, tg, hipG, leanG, zG, chestTw, lSh0, lZ0, lEl0, rsE)
            lsh = gaSh; lshZ = gaZ; lel = gaEl; extL = gaExt
            // impacto de cada soco: estrela no ponto + punhos-fantasma (só efeito visual)
            for (a in 0..1) {
                val ti = gatTI[a]
                if (ti >= 0 && gatFxD[a] != ti && tg >= gatHit(ti)) {
                    gatFxD[a] = ti
                    val rx = (gatDX * gatTF[a] + gatDY * gatTU[a]) / gatSqX
                    val ry = (gatDY * gatTF[a] - gatDX * gatTU[a]) / gatSqY - 15f * rs0(s)
                    emitG(0, rx, ry, 0.18f, rnd.nextFloat() * 1.0472f)
                    for (q in 0..2) {
                        val ang = rnd.nextFloat() * TWO_PI
                        val sp0 = (14f + rnd.nextFloat() * 30f) * rs0(s)
                        emitSpark(fx + rx, fy + ry, cos(ang) * sp0, sin(ang) * sp0, 0.22f, rs0(s) * 36f, if (q % 2 == 0) 0xFFFFFFFF.toInt() else 0xFFFFC23A.toInt(), 0.9f)
                    }
                }
            }
            // enxame: como no anime, vários braços esticados saem dos ombros e socam pontos espalhados por TODO o
            // retângulo ao mesmo tempo (só visual; o dano é de gatTick). A quantidade é por segundo, não por quadro.
            gatGhAcc += dt * GAT_GHOST_RATE * env
            var gn = 0
            while (gatGhAcc >= 1f && gn < 8) {
                gatGhAcc -= 1f
                gn++
                val ga = if (rnd.nextBoolean()) 1 else 0
                val gf: Float
                val gu: Float
                if (gatPtN > 0 && rnd.nextFloat() < 0.3f) {
                    val j = rnd.nextInt(gatPtN)
                    gf = (gatPtF[j] + (rnd.nextFloat() - 0.5f) * 16f).coerceIn(0.1f * gatLen, gatLen)
                    gu = (gatPtU[j] + (rnd.nextFloat() - 0.5f) * 16f).coerceIn(-0.95f * gatHalfW, 0.95f * gatHalfW)
                } else {
                    gf = gatLen * (0.1f + 0.9f * rnd.nextFloat())
                    gu = gatHalfW * 0.95f * (2f * rnd.nextFloat() - 1f)
                }
                val gx = (gatDX * gf + gatDY * gu) / gatSqX
                val gy = (gatDY * gf - gatDX * gu) / gatSqY - 15f * rs0(s)
                emitG(2, gx, gy, 0.07f + 0.05f * rnd.nextFloat(), 0f, mkX[4 + ga] - ox, mkY[4 + ga] - oy)
            }
        } else {
            gatTI[0] = -1; gatTI[1] = -1
            gatFxD[0] = -1; gatFxD[1] = -1
            gatR[0] = 0f; gatR[1] = 0f
            gatGhAcc = 0f
        }

        // ── Gomu Gomu no Gear Second: bombeamento ──
        // agacha de pernas bem abertas, inclinado para a frente, com o punho esquerdo apoiado no chão (IK, não escorrega);
        // o braço direito bombeia ao lado da coxa como um pistão (frequência crescente) e o corpo treme. Na explosão
        // ele levanta, estufa o peito e abre os braços.
        var g2IK = false
        if (tg2 >= 0f && rollW < 0.5f && !gatOn) {
            g2IK = true
            val tt = tg2
            val inW = ease(tt / 0.5f)
            val outW = 1f - smoother((tt - 2.45f) / (G2_DUR - 2.45f))
            val wB = inW * outW
            val ph = TWO_PI * g2Cyc(min(tt, G2_PUMP))
            val pumpAmt = ease((tt - G2_P0) / 0.2f) * (1f - ease((tt - G2_PUMP) / 0.1f))
            val flex = ease((tt - G2_PUMP) / 0.2f)
            val wP = ease((tt - 0.08f) / 0.42f) * (1f - ease((tt - G2_PUMP) / 0.15f))   // punho no chão
            val sn = sin(ph)
            val sn2 = sin(ph + 1.3f)
            val trem = sin(clock * 70f) * pumpAmt
            lean = lerp(lean, lerp(0.50f + 0.05f * sn * pumpAmt, -0.14f, flex), wB)
            crouch = lerp(crouch, lerp(1.0f + 0.07f * abs(sn) * pumpAmt, 0.1f, flex), wB)
            bodyZ = lerp(bodyZ, lerp(1.2f - 0.7f * pumpAmt * (0.5f + 0.5f * sn), 0.6f, flex), wB)
            chestTw = lerp(chestTw, 0.12f * sn * pumpAmt, wB)
            hipTw = lerp(hipTw, 0.04f * trem, wB)
            tilt = lerp(tilt, 0.05f * sn2 * pumpAmt + 0.02f * trem, wB)
            shout = max(shout, wB * (0.5f * pumpAmt + flex))
            legB = lerp(legB, 0.3f * pumpAmt, wB)
            gatSplay = lerp(0.40f, 0.22f, flex) * wB
            // braço direito: pistão ao lado da coxa
            rsh = lerp(rsh, lerp(0.15f + 0.7f * sn * pumpAmt, -0.25f, flex), wB)
            rshZ = lerp(rshZ, lerp(0.2f, 1.0f, flex), wB)
            rel = lerp(rel, lerp(-0.9f - 0.9f * sn2 * pumpAmt, -0.35f, flex), wB)
            // braço esquerdo: punho no chão à frente e ao lado (IK), depois abre
            lsh = lerp(lsh, lerp(-0.4f, -0.25f, flex), wB)
            lshZ = lerp(lshZ, lerp(0.3f, 1.0f, flex), wB)
            lel = lerp(lel, lerp(-1.2f, -0.35f, flex), wB)
            if (wP > 0.01f) {
                val crG = max(crouch, ease(landDip) * 0.5f * (if (landSoft) 1.7f else 1f))
                val hipG = 14.8f + bob - crG * 3f - hitKick * 0.4f
                val leanG = lean - hitKick * 0.3f
                val zG = bodyZ - hitKick * 1.2f
                val lx = cos(yaw)
                val lz = -sin(yaw)
                val fwx = sin(yaw)
                val fwz = cos(yaw)
                val gx = lx * 12f + fwx * 11f
                val gz = lz * 12f + fwz * 11f
                solveArmT(1f, gx, HAND_Y, gz, hipG, leanG, zG, yaw, chestTw)
                lsh = lerp(lsh, ikSh, wP)
                lshZ = lerp(lshZ, ikShZ, wP)
                lel = lerp(lel, 0f, wP)
                extL = lerp(1f, ikExt, wP)
            }
        }

        // suaviza a pose (o golpe mantém o ritmo; transições entre estados não dão tranco)
        pt[0] = lean; pt[1] = hipTw; pt[2] = chestTw; pt[3] = rsh; pt[4] = rshZ; pt[5] = rel
        pt[6] = 0f; pt[7] = lsh; pt[8] = lshZ; pt[9] = lel; pt[10] = 0f; pt[11] = hd
        pt[12] = tilt; pt[13] = shout; pt[14] = crouch; pt[15] = legB
        pt[16] = extR; pt[17] = extL; pt[18] = bodyZ
        if (first) {
            for (i in 0 until NP) ps[i] = pt[i]
        } else {
            var k = min(1f, dt * (if (attacking) 34f else 18f))
            // soco por IK e mãos presas no chão usam a pose exata (qualquer atraso faria o punho / as mãos escorregarem)
            if (punchIK || gatIK || g2IK) k = 1f
            else if (plant && aimOn) k = lerp(k, 1f, ((rollW - 0.5f) * 2f).coerceIn(0f, 1f))
            for (i in 0 until NP) ps[i] += (pt[i] - ps[i]) * k
            // o esticar do braço não pode atrasar: o punho tem que chegar junto com o golpe
            ps[16] = pt[16]
            ps[17] = pt[17]
        }

        // ── monta o modelo ──
        val crouchT = max(ps[14], ease(landDip) * 0.5f * (if (landSoft) 1.7f else 1f))
        val hip = 14.8f + bob - crouchT * 3.0f - hitKick * 0.4f
        val lb = ps[15]

        rs = s * PX * MS
        ox = fx
        oy = fy
        n = 0
        top = 0
        zMode = true
        lYc = cos(yaw)
        lYs = sin(yaw)
        identity()
        rotY(yaw)
        translate(0f, 0f, -hitKick * 1.2f + ps[18])
        // dash: o corpo inteiro deita (pitch) e gira em torno do próprio eixo (spin) / rola (pitch até 2π)
        if (rolling && (bPitch != 0f || bSpin != 0f)) {
            val lf = pivotLift(bPitch, bTuck)
            liftS = if (liftOn) liftS + (lf - liftS) * min(1f, dt * 22f) else lf
            liftOn = true
            translate(0f, liftS, 0f)
            translate(0f, PIV, 0f)
            rotZ(bSpin)
            rotX(bPitch)
            translate(0f, -PIV, 0f)
        } else {
            liftOn = false
        }

        // passos laterais: com as mãos presas o corpo anda um pouco para os lados, e as pernas dão passos de lado
        val mvx = dashVX / rsE
        val mvz = dashVY / (rsE * SINP)
        val latV = mvx * cos(yaw) - mvz * sin(yaw)   // + = para a esquerda do herói
        latS += (latV - latS) * min(1f, dt * 10f)
        val latDir = (latS / max(abs(latS), 5f)).coerceIn(-1f, 1f)
        val latAmt = (abs(latS) / 14f).coerceIn(0f, 1f) * stepAmt * rollW
        latW += abs(latS) * dt * LAT_K
        gLatL = 0f
        gLatR = 0f

        // pernas
        var aL = 0f
        var kL = 0f
        var fL = 0f
        var aR = 0f
        var kR = 0f
        var fR = 0f
        for (k in 0..1) {
            val sd = if (k == 0) 1f else -1f
            val pp = if (sd > 0f) phase else phase + PI.toFloat()
            var a = sin(pp) * 0.8f * mvs - crouchT * 0.6f
            var kn = max(0f, -cos(pp)) * 1.1f * mvs + 0.1f + crouchT * 1.3f
            a = lerp(a, -0.5f + sd * 0.3f - 0.2f * max(0f, vzN), airW)
            kn = lerp(kn, 0.9f, airW)
            a = lerp(a, legA + sd * 0.06f, lgR)
            kn = lerp(kn, legK, lgR)
            if (stepAmt > 0.001f) {
                // andar de costas com as mãos no chão: fase invertida (o pé varre para frente em relação ao corpo)
                val pp2 = -stepW + (if (sd > 0f) 0f else PI.toFloat())
                val lift = max(0f, -cos(pp2))
                val aS = -crouchT * 0.6f + sin(pp2) * 0.4f
                val kS = 0.1f + crouchT * 1.3f + lift * sqrt(lift) * 0.55f   // passos curtos e rentes ao chão
                a = lerp(a, aS, stepAmt * rollW)
                kn = lerp(kn, kS, stepAmt * rollW)
            }
            if (latAmt > 0.001f) {
                // um pé sai para o lado enquanto o outro se apoia; o que sai levanta o joelho (passo curto)
                val pl = latW + (if (sd > 0f) 0f else PI.toFloat())
                val latAng = latDir * 0.34f * latAmt * sin(pl)
                val lf = max(0f, cos(pl)) * latAmt
                kn += lf * sqrt(lf) * 0.6f
                if (k == 0) gLatL = latAng else gLatR = latAng
            }
            if (heliAirW > 0.001f) {
                a = lerp(a, 0f, heliAirW)
                kn = lerp(kn, 0.05f, heliAirW)
            }
            // base do soco: perna da frente adiantada, a de trás esticada
            a -= sd * 0.6f * lb
            kn += 0.5f * max(0f, sd * lb) + 0.2f * max(0f, -sd * lb)
            val ft = -(a + kn) * 0.8f * (1f - 0.8f * heliAirW)
            if (k == 0) {
                aL = a
                kL = kn
                fL = ft
            } else {
                aR = a
                kR = kn
                fR = ft
            }
        }

        val spk = spN * (if (rolling) 0.4f else 1f)
        val vF = 0.06f + spk * (0.28f + 0.1f * sin(phase * 2f)) + hitKick * 0.2f
        val vB = 0.08f + spk * 0.25f * sin(phase * 2f + 1f)
        val sA = 0.12f + spk * 0.4f * sin(phase + 0.5f) + 0.05f * sin(clock * 2f)
        val sB = 0.08f + spk * 0.4f * sin(phase + 1.5f) + 0.04f * sin(clock * 2.4f)
        val hatSw = 0.04f + spk * 0.22f * sin(phase * 2f) + hitKick * 0.3f - rollW * 0.4f
        val hs0 = 0.05f + spk * 0.25f + 0.03f * sin(clock * 3f) + airW * 0.3f
        val hs1 = 0.1f + spk * 0.45f + 0.04f * sin(clock * 2.6f)
        val breath = sin(clock * 2.2f) * 0.1f

        skeleton(
            hip, ps[1], ps[0] - hitKick * 0.3f, ps[2], breath,
            aR, kR, fR, aL, kL, fL,
            vF, vB, sA, sB, hatSw,
            ps[11] - ps[2] * 0.6f, ps[12] - ps[0] * 0.5f, ps[13], hs0, hs1,
            ps[3], ps[4], ps[5], ps[6],
            ps[7], ps[8], ps[9], ps[10], HAT_ON_HEAD,
            ps[16], ps[17]
        )

        // ── rastro do voo: fitas que seguem o caminho real do corpo e somem aos poucos (atrás do modelo) ──
        val trOn = rolling && dashPh == 2
        if (trOn && !trPrevOn) trN = 0
        trPrevOn = trOn
        if (trN > 0) {
            for (i in 0 until trN) {
                val ix = (trHead - 1 - i + NT) % NT
                trA[ix] += dt
            }
            while (trN > 0 && trA[(trHead - trN + NT) % NT] > TR_LIFE) trN--
        }
        if (trOn) {
            val cyc = fy - (PIV + liftS) * COSP * rs
            val sxw = wx
            val syw = wy + (cyc - fy)
            if (trN > 0) {
                val last = (trHead - 1 + NT) % NT
                if (hypot(sxw - trX[last], syw - trY[last]) > 260f) trN = 0
            }
            trX[trHead] = sxw
            trY[trHead] = syw
            trA[trHead] = 0f
            trHead = (trHead + 1) % NT
            trN = min(trN + 1, NT)
        }
        if (trN >= 2) {
            val offX = fx - wx
            val offY = fy - wy
            val nw = (trHead - 1 + NT) % NT
            val od = (trHead - trN + NT) % NT
            var ddx = trX[nw] - trX[od]
            var ddy = trY[nw] - trY[od]
            val dl = sqrt(ddx * ddx + ddy * ddy).coerceAtLeast(0.001f)
            ddx /= dl
            ddy /= dl
            val nx = -ddy
            val ny = ddx
            p.style = Paint.Style.STROKE
            p.strokeCap = Paint.Cap.ROUND
            for (pass in 0..3) {
                for (j in 0 until trN - 1) {
                    val ia = (trHead - 1 - j + NT) % NT
                    val ib = (trHead - 2 - j + NT) % NT
                    val qb = (trA[ib] / TR_LIFE).coerceIn(0f, 1f)
                    if (pass >= 2 && qb > 0.85f) continue
                    val fade = 1f - qb
                    var x0 = trX[ia] + offX
                    var y0 = trY[ia] + offY
                    var x1 = trX[ib] + offX
                    var y1 = trY[ib] + offY
                    when (pass) {
                        0 -> {
                            p.strokeWidth = max(1.5f, 11f * rs * fade)
                            p.color = VoxelArt.alpha(0xFFFFE08A.toInt(), 0.22f * fade)
                        }
                        1 -> {
                            p.strokeWidth = max(1.2f, 3.6f * rs * fade)
                            p.color = VoxelArt.alpha(0xFFFFFFFF.toInt(), 0.8f * fade)
                        }
                        else -> {
                            val sg = if (pass == 2) -1f else 1f
                            val oa = sg * (3f + 5f * (j.toFloat() / trN)) * rs
                            val ob = sg * (3f + 5f * ((j + 1f) / trN)) * rs
                            x0 += nx * oa
                            y0 += ny * oa
                            x1 += nx * ob
                            y1 += ny * ob
                            p.strokeWidth = max(1f, 1.4f * rs * fade)
                            p.color = VoxelArt.alpha(0xFFFFFFFF.toInt(), 0.5f * fade)
                        }
                    }
                    c.drawLine(x0, y0, x1, y1, p)
                }
            }
            p.strokeCap = Paint.Cap.BUTT
            p.style = Paint.Style.FILL
        }

        // ── Gatling: braços-fantasma ficam ATRÁS do corpo (a cabeça/chapéu cobre), vermelhos como o braço real ──
        if (gatIK) {
            for (i in 0 until NG) {
                if (gfL[i] <= 0f || gfK[i] != 2) continue
                val a = (gfL[i] / gfM[i]).coerceIn(0f, 1f)
                val cx = fx + gfX[i]
                val cy = fy + gfY[i]
                val sx = fx + gfSX[i]
                val sy = fy + gfSY[i]
                p.strokeCap = Paint.Cap.ROUND
                p.style = Paint.Style.STROKE
                p.strokeWidth = max(2.2f, rs * 3.8f)
                p.color = VoxelArt.alpha(RED_D, 0.6f * a)
                c.drawLine(sx, sy, cx, cy, p)
                p.strokeWidth = max(1.4f, rs * 2.3f)
                p.color = VoxelArt.alpha(RED, 0.75f * a)
                c.drawLine(sx, sy, cx, cy, p)
                p.strokeCap = Paint.Cap.BUTT
                val h = rs * 3.2f
                p.style = Paint.Style.FILL
                p.color = VoxelArt.alpha(SKIN, 0.85f * a)
                c.drawRect(cx - h, cy - h, cx + h, cy + h, p)
                p.style = Paint.Style.STROKE
                p.strokeWidth = max(1f, rs * 0.9f)
                p.color = VoxelArt.alpha(SKIN_DD, 0.9f * a)
                c.drawRect(cx - h, cy - h, cx + h, cy + h, p)
                p.style = Paint.Style.FILL
            }
        }

        // ── Gear Second: aura vermelha e anel no chão (atrás do corpo) + anel de choque da explosão ──
        if (tg2 >= G2_PUMP && !g2Burst) {
            g2Burst = true
            g2Ring = 0f
            val bcy = fy - 13f * COSP * rs
            for (i in 0..27) {
                val ang = rnd.nextFloat() * TWO_PI
                val sp0 = (30f + rnd.nextFloat() * 70f) * rs
                emitSteam(fx + cos(ang) * 9f * rs, bcy + sin(ang) * 9f * rs, cos(ang) * sp0, sin(ang) * sp0 * 0.6f - 14f * rs, 0.6f + rnd.nextFloat() * 0.4f, (2f + rnd.nextFloat() * 2f) * rs)
            }
        }
        if (tg2 < 0f && g2Act <= 0f) g2Burst = false
        if (g2W > 0.02f) {
            val ccx = fx
            val ccy = fy - 13f * COSP * rs
            val pul = 1f + 0.06f * sin(clock * 9f)
            p.style = Paint.Style.FILL
            p.color = VoxelArt.alpha(0xFFFF4D5E.toInt(), 0.07f * g2W)
            c.drawCircle(ccx, ccy, 25f * rs * pul, p)
            p.color = VoxelArt.alpha(0xFFFF7C86.toInt(), 0.09f * g2W)
            c.drawCircle(ccx, ccy, 17f * rs * pul, p)
            heliRect.set(fx - 14f * rs * pul, fy - 14f * rs * pul * SINP, fx + 14f * rs * pul, fy + 14f * rs * pul * SINP)
            p.style = Paint.Style.STROKE
            p.strokeWidth = max(1.5f, 1.6f * rs)
            p.color = VoxelArt.alpha(0xFFFF8A94.toInt(), 0.5f * g2W)
            c.drawOval(heliRect, p)
            p.style = Paint.Style.FILL
        }
        if (g2Ring >= 0f) {
            g2Ring += dt
            val q = g2Ring / 0.55f
            if (q >= 1f) {
                g2Ring = -1f
            } else {
                val rr = (10f + 56f * ease(q)) * rs
                p.style = Paint.Style.FILL
                p.color = VoxelArt.alpha(0xFFFF7C86.toInt(), 0.28f * (1f - q))   // brilho atrás do corpo
                c.drawCircle(fx, fy - 13f * COSP * rs, (22f + 30f * q) * rs, p)
                p.style = Paint.Style.STROKE
                for (i in 0..1) {
                    val r2 = rr * (1f - 0.22f * i)
                    heliRect.set(fx - r2, fy - r2 * SINP, fx + r2, fy + r2 * SINP)
                    p.strokeWidth = max(1.5f, (5f - 4f * q) * rs * (1f - 0.4f * i))
                    p.color = VoxelArt.alpha(if (i == 0) 0xFFFFFFFF.toInt() else 0xFFFF7C86.toInt(), 0.85f * (1f - q))
                    c.drawOval(heliRect, p)
                }
                p.style = Paint.Style.FILL
            }
        }

        // ── Gear Second: vapor fino e rastros de velocidade, SEMPRE atrás do corpo (nunca tampam o Luffy) ──
        val pumping = tg2 in G2_P0..G2_PUMP
        val hasPrev = !first && !(pwx == 0f && pwy == 0f)
        val vwx = if (hasPrev) (wx - pwx) / dt else 0f
        val vwy = if (hasPrev) (wy - pwy) / dt else 0f
        pwx = wx
        pwy = wy
        val sp2 = hypot(vwx, vwy)
        val rate = if (pumping) 8f + 34f * ease((tg2 - G2_P0) / (G2_PUMP - G2_P0))
        else if (g2W > 0.05f) 14f * g2W
        else 0f
        if (rate > 0f) {
            stAcc += dt * rate
            var cnt = 0
            while (stAcc >= 1f && cnt < 4) {
                stAcc -= 1f
                cnt++
                val ex: Float
                val ey: Float
                val pick = rnd.nextFloat()
                if (pick < 0.35f) {
                    val a2 = rnd.nextInt(2)                  // ombros
                    ex = mkX[4 + a2]
                    ey = mkY[4 + a2]
                } else if (pick < 0.5f) {
                    val a2 = rnd.nextInt(2)                  // punhos
                    ex = mkX[a2]
                    ey = mkY[a2]
                } else {
                    // lados do corpo e acima da cabeça: o que ficaria na frente do tronco nem é emitido
                    val sgn = if (rnd.nextBoolean()) 1f else -1f
                    ex = fx + sgn * (7f + rnd.nextFloat() * 4f) * rs
                    ey = fy - (4f + rnd.nextFloat() * 24f) * COSP * rs
                }
                val dirO = if (ex >= fx) 1f else -1f
                emitSteam(ex, ey, dirO * (4f + rnd.nextFloat() * 10f) * rs, -(14f + rnd.nextFloat() * 22f) * rs, 0.7f + rnd.nextFloat() * 0.5f, (1.5f + rnd.nextFloat() * 1.7f) * rs)
            }
        } else {
            stAcc = 0f
        }
        if (g2Act > 0.5f && sp2 > 80f) {
            stStreakAcc += dt * 32f
            val ux = vwx / sp2
            val uy = vwy / sp2
            var cn2 = 0
            while (stStreakAcc >= 1f && cn2 < 3) {
                stStreakAcc -= 1f
                cn2++
                val sx = fx + (rnd.nextFloat() - 0.5f) * 16f * rs - ux * 8f * rs
                val sy = fy - (3f + rnd.nextFloat() * 22f) * COSP * rs - uy * 4f * rs
                val v0 = (200f + rnd.nextFloat() * 160f) * rs
                emitSteam(sx, sy, -ux * v0, -uy * v0, 0.16f + rnd.nextFloat() * 0.08f, 1f, 1)
            }
        } else {
            stStreakAcc = 0f
        }
        for (i in 0 until NST) {
            if (stL[i] <= 0f) continue
            stL[i] -= dt
            val a = (stL[i] / stM[i]).coerceIn(0f, 1f)
            if (stK[i] == 0) {
                // nuvem pixelada: sobe devagar, balança de leve, cresce e some; dois quadradinhos arredondados
                stX[i] += (stVx[i] + sin(clock * 5f + i) * 5f * rs) * dt
                stY[i] += stVy[i] * dt
                stVx[i] *= max(0f, 1f - dt * 1.4f)
                stVy[i] *= max(0f, 1f - dt * 0.8f)
                val r = stS[i] * (1f + 1.1f * (1f - a))
                val al = 0.34f * a * min(1f, (1f - a) * 6f + 0.35f)
                p.style = Paint.Style.FILL
                p.color = VoxelArt.alpha(0xFFFFE9EC.toInt(), al)
                heliRect.set(stX[i] - r, stY[i] - r, stX[i] + r, stY[i] + r)
                c.drawRoundRect(heliRect, r * 0.35f, r * 0.35f, p)
                p.color = VoxelArt.alpha(0xFFFFFFFF.toInt(), al * 0.8f)
                heliRect.set(stX[i] - r * 0.5f, stY[i] - r * 0.55f, stX[i] + r * 0.4f, stY[i] + r * 0.3f)
                c.drawRoundRect(heliRect, r * 0.2f, r * 0.2f, p)
            } else {
                stX[i] += stVx[i] * dt
                stY[i] += stVy[i] * dt
                p.style = Paint.Style.STROKE
                p.strokeCap = Paint.Cap.ROUND
                p.strokeWidth = max(1f, rs * 0.9f)
                p.color = VoxelArt.alpha(if (i % 3 == 0) 0xFFFFC2CA.toInt() else 0xFFFFFFFF.toInt(), 0.55f * a)
                c.drawLine(stX[i], stY[i], stX[i] - stVx[i] * 0.06f, stY[i] - stVy[i] * 0.06f, p)
                p.strokeCap = Paint.Cap.BUTT
                p.style = Paint.Style.FILL
            }
        }
        p.style = Paint.Style.FILL

        flushZ(c)

        // ── helicóptero: arcos de vento girando em volta das pernas ──
        if (heliArcA > 0.04f) {
            val hcx = fx
            val hcy = fy - (hip + 1f) * COSP * rs
            val rx = (3.2f + 14f * gLegLen * sin(gLegSplay)) * 1.05f * rs
            val ry = rx * SINP
            p.style = Paint.Style.STROKE
            p.strokeCap = Paint.Cap.ROUND
            val ha = -heliAng * 57.29578f
            for (i in 0..2) {
                val rr = if (i == 1) 0.78f else 1f
                heliRect.set(hcx - rx * rr, hcy - ry * rr, hcx + rx * rr, hcy + ry * rr)
                p.strokeWidth = max(1.3f, rs * (if (i == 1) 1.1f else 1.5f))
                p.color = VoxelArt.alpha(if (i == 1) 0xFFFFE08A.toInt() else 0xFFFFFFFF.toInt(), 0.55f * heliArcA)
                c.drawArc(heliRect, ha + i * 120f, 62f, false, p)
                c.drawArc(heliRect, ha + i * 120f + 180f, 40f, false, p)
            }
            p.strokeCap = Paint.Cap.BUTT
            p.style = Paint.Style.FILL
        }

        // ── efeitos do soco: rastro de velocidade e explosão no ponto de impacto ──
        if (attacking && punchSide != 0 && rollW < 0.5f) {
            val g = if (t < PW) 0f else if (t < T_HIT) ease((t - PW) / (T_HIT - PW)) else max(0f, 1f - (t - T_HIT) / 0.28f)
            if (g > 0.02f) {
                val k = punchSide - 1
                val fxk = mkX[k]
                val fyk = mkY[k]
                var dx = mkX[k + 4] - fxk
                var dy = mkY[k + 4] - fyk
                val dl = sqrt(dx * dx + dy * dy)
                if (dl > 0.001f) {
                    dx /= dl
                    dy /= dl
                }
                p.strokeCap = Paint.Cap.ROUND
                p.style = Paint.Style.STROKE
                for (i in 0..2) {
                    val off = (i - 1f) * 2.2f * rs
                    val len = (12f + 10f * (1f - abs(i - 1f) * 0.4f)) * rs * g
                    val x0 = fxk - dy * off
                    val y0 = fyk + dx * off
                    p.strokeWidth = max(1.4f, rs * 1.3f)
                    p.color = VoxelArt.alpha(if (i == 1) 0xFFFFFFFF.toInt() else 0xFFFFE08A.toInt(), 0.75f * g)
                    c.drawLine(x0, y0, x0 + dx * len, y0 + dy * len, p)
                }
                // explosão no impacto: núcleo, anel e raios
                val imp = if (t >= T_HIT && t < T_HIT + 0.17f) 1f - (t - T_HIT) / 0.17f else 0f
                if (imp > 0.02f) {
                    val cx = fxk - dx * 2f * rs
                    val cy = fyk - dy * 2f * rs
                    val grow = 1f - imp
                    p.style = Paint.Style.FILL
                    p.color = VoxelArt.alpha(0xFFFFFFFF.toInt(), 0.6f * imp)
                    c.drawCircle(cx, cy, rs * (3f + 7f * grow), p)
                    p.color = VoxelArt.alpha(0xFFFFB02E.toInt(), 0.38f * imp)
                    c.drawCircle(cx, cy, rs * (5f + 11f * grow), p)
                    p.style = Paint.Style.STROKE
                    p.strokeWidth = max(1.2f, rs * 1.2f)
                    p.color = VoxelArt.alpha(0xFFFFE08A.toInt(), 0.8f * imp)
                    c.drawCircle(cx, cy, rs * (8f + 16f * grow), p)
                    for (i in 0..5) {
                        val ang = i * 1.0472f + 0.4f
                        val r0 = rs * (9f + 8f * grow)
                        val r1 = rs * (14f + 14f * grow)
                        c.drawLine(cx + cos(ang) * r0, cy + sin(ang) * r0, cx + cos(ang) * r1, cy + sin(ang) * r1, p)
                    }
                }
                p.strokeCap = Paint.Cap.BUTT
                p.style = Paint.Style.FILL
            }
        }

        // ── Gatling: rastro de velocidade nos punhos que saem + estrelas de impacto e punhos-fantasma ──
        if (gatIK) {
            p.strokeCap = Paint.Cap.ROUND
            p.style = Paint.Style.STROKE
            for (a in 0..1) {
                val g = gatR[a]
                if (g < 0.05f) continue
                val fxk = mkX[a]
                val fyk = mkY[a]
                var dx = mkX[a + 4] - fxk
                var dy = mkY[a + 4] - fyk
                val dl = sqrt(dx * dx + dy * dy)
                if (dl < 0.001f) continue
                dx /= dl
                dy /= dl
                for (i in 0..2) {
                    val off = (i - 1f) * 2.6f * rs
                    val len = (16f + 14f * (1f - abs(i - 1f) * 0.4f)) * rs * g
                    val x0 = fxk - dy * off
                    val y0 = fyk + dx * off
                    p.strokeWidth = max(1.4f, rs * 1.4f)
                    p.color = VoxelArt.alpha(if (i == 1) 0xFFFFFFFF.toInt() else 0xFFFFE08A.toInt(), 0.7f * g)
                    c.drawLine(x0, y0, x0 + dx * len, y0 + dy * len, p)
                }
            }
            p.strokeCap = Paint.Cap.BUTT
            p.style = Paint.Style.FILL
        }
        for (i in 0 until NG) {
            if (gfL[i] <= 0f) continue
            gfL[i] -= dt
            val a = (gfL[i] / gfM[i]).coerceIn(0f, 1f)
            val cx = fx + gfX[i]
            val cy = fy + gfY[i]
            if (gfK[i] == 2) {
                // desenhado antes do corpo (ver acima): aqui só envelhece
            } else if (gfK[i] == 1) {
                // punho-fantasma: quadradinho de pele com contorno que some rápido
                val h = rs * 4.2f
                p.style = Paint.Style.FILL
                p.color = VoxelArt.alpha(0xFFF2BF8C.toInt(), 0.75f * a)
                c.drawRect(cx - h, cy - h, cx + h, cy + h, p)
                p.style = Paint.Style.STROKE
                p.strokeWidth = max(1f, rs * 0.9f)
                p.color = VoxelArt.alpha(0xFF8A4A2A.toInt(), 0.8f * a)
                c.drawRect(cx - h, cy - h, cx + h, cy + h, p)
                p.style = Paint.Style.FILL
            } else {
                val grow = 1f - a
                p.style = Paint.Style.FILL
                p.color = VoxelArt.alpha(0xFFFFFFFF.toInt(), 0.65f * a)
                c.drawCircle(cx, cy, rs * (2.5f + 5f * grow), p)
                p.color = VoxelArt.alpha(0xFFFFB02E.toInt(), 0.35f * a)
                c.drawCircle(cx, cy, rs * (4f + 9f * grow), p)
                p.style = Paint.Style.STROKE
                p.strokeCap = Paint.Cap.ROUND
                p.strokeWidth = max(1.2f, rs * 1.2f)
                p.color = VoxelArt.alpha(0xFFFFE08A.toInt(), 0.85f * a)
                for (k in 0..5) {
                    val ang = k * 1.0472f + gfR[i]
                    val r0 = rs * (5f + 6f * grow)
                    val r1 = rs * (10f + 12f * grow)
                    c.drawLine(cx + cos(ang) * r0, cy + sin(ang) * r0, cx + cos(ang) * r1, cy + sin(ang) * r1, p)
                }
                p.strokeCap = Paint.Cap.BUTT
                p.style = Paint.Style.FILL
            }
        }

        // ── dash estilingue: poeira ao prender as mãos, faíscas de carga, rastro no voo e poeira da rolagem ──
        if (rolling) {
            when (dashPh) {
                1 -> {
                    if (dashU >= 0.18f && !dashBurst) {
                        dashBurst = true
                        for (k in 0..1) {
                            for (i in 0..8) {
                                val ang = rnd.nextFloat() * TWO_PI
                                val sp0 = (16f + rnd.nextFloat() * 36f) * rs
                                emitSpark(mkX[k], mkY[k], cos(ang) * sp0, sin(ang) * sp0 * 0.6f - 10f * rs, 0.36f, rs * 50f, if (i % 2 == 0) 0xFFE9D9B0.toInt() else 0xFFFFC23A.toInt(), 1.1f)
                            }
                        }
                    }
                    if (dashCharge > 0.02f && rnd.nextFloat() < dt * (12f + 36f * dashCharge)) {
                        val k = rnd.nextInt(2)
                        val ang = rnd.nextFloat() * TWO_PI
                        val sp0 = (10f + rnd.nextFloat() * 22f) * rs
                        emitSpark(mkX[k], mkY[k], cos(ang) * sp0, sin(ang) * sp0 - 8f * rs, 0.3f, rs * 30f, if (rnd.nextBoolean()) 0xFFFFFFFF.toInt() else 0xFFFFC23A.toInt(), 0.9f)
                    }
                }
                2 -> {
                    // faíscas soltas ao longo do voo (o rastro principal é o histórico desenhado atrás do corpo)
                    if (rnd.nextFloat() < dt * 28f) {
                        val cy2 = fy - (PIV + liftS) * COSP * rs
                        emitSpark(fx + (rnd.nextFloat() - 0.5f) * 8f * rs, cy2 + (rnd.nextFloat() - 0.5f) * 8f * rs, 0f, 0f, 0.22f, 0f, 0xFFFFFFFF.toInt(), 0.8f)
                    }
                }
                else -> {
                    if (!touchBurst) {
                        touchBurst = true
                        for (i in 0..9) {
                            emitSpark(fx + (rnd.nextFloat() - 0.5f) * 10f * rs, fy - rs, (rnd.nextFloat() - 0.5f) * 70f * rs, -(10f + rnd.nextFloat() * 32f) * rs, 0.42f, rs * 45f, if (i % 2 == 0) 0xFFE9D9B0.toInt() else 0xFFB8B0A8.toInt(), 1.2f)
                        }
                    }
                    if (!dashFlip && dashU < 0.7f && rnd.nextFloat() < dt * 30f) {
                        emitSpark(fx + (rnd.nextFloat() - 0.5f) * 12f * rs, fy - rs, (rnd.nextFloat() - 0.5f) * 40f * rs, -(6f + rnd.nextFloat() * 18f) * rs, 0.34f, rs * 40f, 0xFFD8CFB8.toInt(), 1.0f)
                    }
                }
            }
        }
        if (!rolling || dashPh == 2) dashBurst = false
        if (!rolling || dashPh != 3) touchBurst = false

        // ── partículas ──
        p.style = Paint.Style.FILL
        for (i in 0 until NSP) {
            if (spL[i] <= 0f) continue
            spL[i] -= dt
            spX[i] += spVx[i] * dt
            spY[i] += spVy[i] * dt
            spVy[i] += spG[i] * dt
            val a = (spL[i] / spM[i]).coerceIn(0f, 1f)
            val sz = max(1.2f, rs * spZ[i]) * (0.5f + a * 0.5f)
            p.color = (((a * 255f).toInt()) shl 24) or (spC[i] and 0x00FFFFFF)
            c.drawRect(spX[i] - sz, spY[i] - sz, spX[i] + sz, spY[i] + sz, p)
        }

        p.style = Paint.Style.FILL
    }
}
