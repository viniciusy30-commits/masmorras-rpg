package com.viniciusy.masmorras

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import kotlin.math.PI
import kotlin.math.abs
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

    private fun ease(t: Float): Float {
        val x = t.coerceIn(0f, 1f)
        return x * x * (3f - 2f * x)
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
    private val SKIN = 0xFFF2BF8C.toInt()
    private val SKIN_L = 0xFFFBD3A6.toInt()
    private val SKIN_D = 0xFFD99B68.toInt()
    private val SKIN_DD = 0xFFB4744A.toInt()
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
        cube(0f, 0.4f, 0f, 2.4f, 1.2f, 2.3f, SKIN_D)
        // cabeça redonda de menino
        cube(0f, 4.7f, 0f, 4.8f, 4.5f, 4.5f, SKIN)
        cube(0f, 4.7f, 4.53f, 4.5f, 4.2f, 0.06f, SKIN_L)
        cube(-4.95f, 4.5f, 0.3f, 0.35f, 1.3f, 1.1f, SKIN_D)
        cube(4.95f, 4.5f, 0.3f, 0.35f, 1.3f, 1.1f, SKIN_D)
        // olhos grandes e redondos
        cube(-2.2f, 5.1f, 4.58f, 1.3f, 1.25f * eyeOpen, 0.06f, WHITE)
        cube(2.2f, 5.1f, 4.58f, 1.3f, 1.25f * eyeOpen, 0.06f, WHITE)
        cube(-2.15f, 5.05f, 4.64f, 0.86f, 1.12f * eyeOpen, 0.06f, EYE)
        cube(2.15f, 5.05f, 4.64f, 0.86f, 1.12f * eyeOpen, 0.06f, EYE)
        cube(-2.5f, 5.65f, 4.72f, 0.3f, 0.32f * eyeOpen, 0.05f, WHITE)
        cube(1.8f, 5.65f, 4.72f, 0.3f, 0.32f * eyeOpen, 0.05f, WHITE)
        // sobrancelhas grossas e determinadas
        push()
        translate(-2.2f, 6.95f, 4.6f)
        rotZ(-0.16f)
        cube(0f, 0f, 0f, 1.6f, 0.34f, 0.07f, HAIR_D)
        pop()
        push()
        translate(2.2f, 6.95f, 4.6f)
        rotZ(0.16f)
        cube(0f, 0f, 0f, 1.6f, 0.34f, 0.07f, HAIR_D)
        pop()
        // cicatriz embaixo do olho esquerdo
        cube(2.2f, 3.55f, 4.6f, 1.0f, 0.14f, 0.05f, FSCAR)
        cube(1.7f, 3.2f, 4.6f, 0.1f, 0.36f, 0.05f, FSCAR)
        cube(2.7f, 3.2f, 4.6f, 0.1f, 0.36f, 0.05f, FSCAR)
        // nariz
        cube(0f, 3.6f, 4.65f, 0.6f, 0.85f, 0.3f, SKIN_D)
        cube(0f, 3.1f, 4.9f, 0.55f, 0.32f, 0.2f, SKIN_L)
        // sorrisão
        val mh = 0.6f + 0.95f * shout
        val my = 2.1f - 0.25f * shout
        cube(0f, my, 4.58f, 3.1f, mh, 0.06f, MOUTH)
        cube(0f, my + mh - 0.34f, 4.64f, 2.9f, 0.34f, 0.06f, WHITE)
        cube(0f, my - mh + 0.4f, 4.64f, 1.5f, 0.32f, 0.06f, TONGUE)
        cube(-3.15f, my + mh - 0.05f, 4.62f, 0.3f, 0.55f, 0.06f, MOUTH)
        cube(3.15f, my + mh - 0.05f, 4.62f, 0.3f, 0.55f, 0.06f, MOUTH)
        // cabelo preto bagunçado
        cube(0f, 9.35f, -0.3f, 5.0f, 1.05f, 4.9f, HAIR)
        cube(0f, 5.7f, -4.65f, 5.0f, 4.4f, 0.6f, HAIR)
        cube(-4.85f, 7.4f, -0.3f, 0.4f, 1.9f, 4.2f, HAIR_D)
        cube(4.85f, 7.4f, -0.3f, 0.4f, 1.9f, 4.2f, HAIR_D)
        cube(-4.85f, 5.4f, 2.4f, 0.2f, 1.5f, 1.0f, HAIR_D)
        cube(4.85f, 5.4f, 2.4f, 0.2f, 1.5f, 1.0f, HAIR_D)
        // franja picotada (fica acima das sobrancelhas)
        cube(0f, 8.7f, 4.35f, 4.85f, 0.95f, 0.55f, HAIR)
        cube(-3.8f, 7.95f, 4.7f, 0.95f, 0.75f, 0.35f, HAIR)
        cube(-1.9f, 8.1f, 4.75f, 0.9f, 0.55f, 0.35f, HAIR)
        cube(0.2f, 7.85f, 4.78f, 1.0f, 0.8f, 0.35f, HAIR)
        cube(2.2f, 8.05f, 4.75f, 0.9f, 0.6f, 0.35f, HAIR)
        cube(3.9f, 7.9f, 4.7f, 0.95f, 0.75f, 0.35f, HAIR)
        if (!hatHead) {
            spike(-3.4f, 10.0f, 1.2f, 0.5f, 0.2f - hs0, 1.4f, 2.4f)
            spike(3.4f, 10.0f, 1.4f, -0.5f, 0.25f - hs0, 1.4f, 2.2f)
            spike(0.2f, 10.0f, 2.8f, 0.1f, 0.6f - hs0, 1.5f, 2.0f)
            spike(-1.9f, 10.1f, -0.8f, 0.3f, -0.15f - hs0, 1.5f, 3.1f)
            spike(1.8f, 10.1f, -1.0f, -0.25f, -0.2f - hs0, 1.5f, 3.0f)
            spike(0.0f, 9.8f, -3.0f, 0.0f, -0.8f - hs0, 2.0f, 2.6f)
            spike(-3.7f, 9.6f, -2.6f, 0.65f, -0.55f - hs0, 1.6f, 2.3f)
            spike(3.8f, 9.6f, -2.5f, -0.65f, -0.55f - hs0, 1.6f, 2.3f)
        }
        // mechas da nuca
        spike(-2.5f, 4.9f, -5.2f, 0.1f, -1.4f - hs1, 1.4f, 2.6f)
        spike(2.5f, 4.6f, -5.2f, -0.1f, -1.45f - hs1, 1.4f, 2.4f)
        spike(0.0f, 3.2f, -5.2f, 0.0f, -1.5f - hs1, 1.5f, 2.4f)
        if (hatHead) {
            push()
            translate(0f, 9.4f, 0.5f)
            rotX(-0.14f)
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
        cube(0f, -2.3f, 0f, 0.75f, 2.4f, 0.25f, SASH)
        cube(0f, -4.5f, 0f, 0.75f, 0.28f, 0.28f, SASH_D)
        pop()
        push()
        translate(3.2f, 2.0f, 4.1f)
        rotX(sB)
        rotZ(-0.22f)
        cube(0f, -1.6f, 0f, 0.62f, 1.7f, 0.23f, SASH_D)
        pop()
        // barriga
        cube(0f, 6.1f, 0f, 4.7f, 1.9f, 3.3f, SKIN)
        cube(0f, 6.1f, 3.32f, 4.4f, 1.7f, 0.06f, SKIN_L)
        cube(0f, 6.2f, 3.38f, 0.07f, 1.6f, 0.05f, SKIN_DD)
        cube(0f, 6.9f, 3.38f, 3.6f, 0.07f, 0.05f, SKIN_DD)
    }

    /** Peito, cicatriz em X, colete vermelho aberto, pescoço e chapéu nas costas. */
    private fun torsoUp(vF: Float, vB: Float, hatSw: Float, hatHead: Boolean) {
        // peito
        cube(0f, 10.7f, 0f, 5.8f, 2.8f, 3.7f, SKIN)
        cube(-2.9f, 11.4f, 3.72f, 2.6f, 1.6f, 0.08f, SKIN_L)
        cube(2.9f, 11.4f, 3.72f, 2.6f, 1.6f, 0.08f, SKIN_L)
        cube(0f, 11.0f, 3.76f, 0.07f, 1.9f, 0.05f, SKIN_DD)
        // cicatriz em X, com pontos de sutura
        push()
        translate(0f, 9.9f, 3.84f)
        rotZ(0.64f)
        cube(0f, 0f, 0f, 0.42f, 5.0f, 0.06f, XSCAR)
        cube(0f, 1.6f, 0.03f, 0.9f, 0.1f, 0.04f, XSCAR_D)
        cube(0f, -0.4f, 0.03f, 0.9f, 0.1f, 0.04f, XSCAR_D)
        cube(0f, -2.4f, 0.03f, 0.9f, 0.1f, 0.04f, XSCAR_D)
        pop()
        push()
        translate(0f, 9.9f, 3.86f)
        rotZ(-0.64f)
        cube(0f, 0f, 0f, 0.42f, 5.0f, 0.06f, XSCAR)
        cube(0f, 1.6f, 0.03f, 0.9f, 0.1f, 0.04f, XSCAR_D)
        cube(0f, -0.4f, 0.03f, 0.9f, 0.1f, 0.04f, XSCAR_D)
        cube(0f, -2.4f, 0.03f, 0.9f, 0.1f, 0.04f, XSCAR_D)
        pop()
        // colete vermelho aberto: costas, alças que cobrem os ombros e painéis da frente
        cube(0f, 9.8f, -3.9f, 6.0f, 3.9f, 0.5f, RED)
        cube(0f, 10.0f, -4.42f, 0.1f, 3.5f, 0.05f, RED_DD)
        cube(-2.0f, 10.5f, -4.42f, 0.08f, 2.8f, 0.05f, RED_D)
        cube(2.0f, 10.5f, -4.42f, 0.08f, 2.8f, 0.05f, RED_D)
        cube(-5.3f, 13.7f, 0f, 2.7f, 0.62f, 4.0f, RED)
        cube(5.3f, 13.7f, 0f, 2.7f, 0.62f, 4.0f, RED)
        cube(-5.3f, 14.3f, 0f, 2.5f, 0.06f, 3.8f, RED_L)
        cube(5.3f, 14.3f, 0f, 2.5f, 0.06f, 3.8f, RED_L)
        cube(-4.4f, 10.5f, 3.85f, 1.5f, 3.0f, 0.45f, RED)
        cube(4.4f, 10.5f, 3.85f, 1.5f, 3.0f, 0.45f, RED)
        cube(-2.95f, 10.5f, 3.9f, 0.12f, 3.0f, 0.5f, RED_D)
        cube(2.95f, 10.5f, 3.9f, 0.12f, 3.0f, 0.5f, RED_D)
        cube(-4.6f, 11.4f, 4.32f, 0.75f, 1.9f, 0.05f, RED_L)
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

    /** Braço (side = +1 esquerdo / -1 direito): ombro, cotovelo e punho fechado. Ângulo negativo = para a frente. */
    private fun arm(side: Float, sh: Float, shZ: Float, el: Float, tw: Float, id: Int) {
        push()
        translate(side * 7.1f, 12.3f, 0f)
        projectTo(0f, 0f, 0f, id + 4)
        rotZ(side * shZ)
        rotX(sh)
        rotY(tw)
        cube(0f, -2.5f, 0f, 1.85f, 2.75f, 1.85f, SKIN)
        cube(side * 0.2f, -1.7f, 1.2f, 1.45f, 1.5f, 0.75f, SKIN_L)
        translate(0f, -5.3f, 0f)
        rotX(el)
        cube(0f, -2.4f, 0f, 1.7f, 2.65f, 1.7f, SKIN)
        cube(0f, -1.2f, 1.2f, 1.2f, 1.3f, 0.55f, SKIN_L)
        translate(0f, -5.0f, 0f)
        // punho fechado
        cube(0f, -1.4f, 0.1f, 2.0f, 1.55f, 2.0f, SKIN)
        cube(0f, -1.0f, 2.05f, 1.75f, 1.05f, 0.1f, SKIN_L)
        cube(0f, -2.95f, 0.1f, 1.8f, 0.12f, 1.8f, SKIN_D)
        cube(0f, -1.35f, 2.12f, 1.7f, 0.07f, 0.05f, SKIN_DD)
        cube(-0.6f, -1.0f, 2.12f, 0.07f, 1.0f, 0.05f, SKIN_DD)
        cube(0.6f, -1.0f, 2.12f, 0.07f, 1.0f, 0.05f, SKIN_DD)
        projectTo(0f, -1.6f, 0.4f, id)
        pop()
    }

    /** Perna: coxa (bermuda azul em cima), joelho, canela e pé de sandália. Ângulo positivo = para trás. */
    private fun leg(side: Float, a: Float, knee: Float, foot: Float, hipY: Float) {
        push()
        translate(side * 2.6f, hipY, 0f)
        rotX(a)
        cube(0f, -2.9f, 0f, 2.75f, 3.1f, 2.85f, SHORT)
        cube(side * 0.9f, -2.7f, 2.9f, 1.4f, 2.5f, 0.06f, SHORT_L)
        cube(0f, -5.95f, 0f, 2.85f, 0.22f, 2.95f, SHORT_D)
        cube(0f, -6.9f, 0f, 2.0f, 0.95f, 2.1f, SKIN)
        translate(0f, -7.6f, 0f)
        rotX(knee)
        cube(0f, -2.9f, 0f, 1.8f, 3.0f, 1.9f, SKIN)
        cube(0f, -1.8f, 1.95f, 1.1f, 1.4f, 0.05f, SKIN_L)
        translate(0f, -5.9f, 0f)
        rotX(foot)
        cube(0f, -0.2f, 1.0f, 1.65f, 0.4f, 2.4f, SKIN)
        cube(0f, 0.0f, 0.5f, 1.75f, 0.55f, 1.0f, SKIN_D)
        cube(0f, -0.85f, 1.2f, 1.85f, 0.25f, 3.1f, SAND_D)
        cube(0f, 0.2f, 1.0f, 1.8f, 0.14f, 0.35f, SAND)
        cube(0f, -0.45f, 3.7f, 1.7f, 0.3f, 0.45f, SKIN_D)
        pop()
    }

    private fun skeleton(
        hip: Float, hipTw: Float, lean: Float, chestTw: Float, breath: Float,
        aR: Float, kR: Float, fR: Float, aL: Float, kL: Float, fL: Float,
        vF: Float, vB: Float, sA: Float, sB: Float, hatSw: Float,
        turn: Float, tilt: Float, shout: Float, hs0: Float, hs1: Float,
        rsh: Float, rshZ: Float, rel: Float, rtw: Float,
        lsh: Float, lshZ: Float, lel: Float, ltw: Float, hatHead: Boolean
    ) {
        leg(-1f, aR, kR, fR, hip)
        leg(1f, aL, kL, fL, hip)
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
        arm(-1f, rsh, rshZ, rel, rtw, 0)
        arm(1f, lsh, lshZ, lel, ltw, 1)
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
    var rollP = -1f        // 0..1 durante o rolamento; <0 = fora dele
    var inAir = false
    var airVz = 0f
    var airH = 0f
    var swing = 0          // 0 soco direito, 1 soco esquerdo, 2+ soco duplo
    var alphaV = 1f

    /** Instante (0..1 da animação) em que o punho acerta. */
    const val T_HIT = 0.22f

    // ───────────── estado de animação ─────────────
    private const val MS = 0.8f   // escala do modelo (altura parecida com a dos outros heróis)
    private const val NP = 16
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
    private val ps = FloatArray(NP)
    private val pt = FloatArray(NP)
    private val mkX = FloatArray(6)
    private val mkY = FloatArray(6)

    private const val NSP = 40
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

    /** Interpola o ciclo do soco: repouso → preparação (0.18) → golpe (0.30) → extensão (0.55) → volta ao repouso. */
    private fun k5(t: Float, a: Float, b: Float, c: Float, d: Float, e: Float): Float {
        return if (t < 0.18f) lerp(a, b, ease(t / 0.18f))
        else if (t < 0.30f) lerp(b, c, ease((t - 0.18f) / 0.12f))
        else if (t < 0.55f) lerp(c, d, (t - 0.30f) / 0.25f)
        else lerp(d, e, ease((t - 0.55f) / 0.45f))
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

        // direção suave
        if (abs(faceX) + abs(faceY) > 0.01f) {
            val target = atan2(faceX, faceY)
            if (first) {
                yaw = target
            } else {
                var d = target - yaw
                while (d > PI.toFloat()) d -= TWO_PI
                while (d < -PI.toFloat()) d += TWO_PI
                yaw += d * min(1f, dt * 16f)
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
        val rollTgt = if (rolling) min(rollP / 0.12f, (1f - rollP) / 0.2f).coerceIn(0f, 1f) else 0f
        if (first) {
            airW = airT
            rollW = rollTgt
        } else {
            airW += (airT - airW) * min(1f, dt * 14f)
            rollW += (rollTgt - rollW) * min(1f, dt * 26f)
        }
        if (wasAir && airW < 0.4f) {
            wasAir = false
            landDip = 1f
        } else if (airW > 0.7f) {
            wasAir = true
        }
        val fl = VoxelArt.flash
        if (!first && fl > prevFlash + 0.25f) hitKick = 1f
        prevFlash = fl
        hitKick = max(0f, hitKick - dt * 4.5f)
        landDip = max(0f, landDip - dt * 5.5f)
        val bp = clock % 3.4f
        eyeOpen = if (bp < 0.14f) max(0.12f, abs(bp - 0.07f) / 0.07f) else 1f
        val vzN = (airVz / 340f).coerceIn(-1.2f, 1f)
        val still = 1f - max(rollW, airW)
        val mvs = still * blend

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
        // rolamento: corpo deitado para a frente, braços para trás
        lean = lerp(lean, 1.12f, rollW)
        rsh = lerp(rsh, 0.95f, rollW)
        lsh = lerp(lsh, 0.95f, rollW)
        rshZ = lerp(rshZ, 0.12f, rollW)
        lshZ = lerp(lshZ, 0.12f, rollW)
        rel = lerp(rel, -0.4f, rollW)
        lel = lerp(lel, -0.4f, rollW)
        tilt = lerp(tilt, -0.5f, rollW)

        // ── socos ──
        var punchSide = 0 // 1 direito, 2 esquerdo, 3 os dois
        if (attacking && rollW < 0.5f) {
            if (swing == 0) {
                // soco direto de direita
                punchSide = 1
                rsh = k5(t, rsh, 0.45f, -1.58f, -1.45f, rsh)
                rshZ = k5(t, rshZ, 0.25f, 0.0f, 0.02f, rshZ)
                rel = k5(t, rel, -2.4f, -0.05f, -0.2f, rel)
                lsh = k5(t, lsh, -0.9f, -0.75f, -0.85f, lsh)
                lel = k5(t, lel, -2.0f, -2.1f, -2.0f, lel)
                lshZ = k5(t, lshZ, 0.2f, 0.25f, 0.25f, lshZ)
                chestTw = k5(t, chestTw, -0.5f, 0.6f, 0.5f, chestTw)
                hipTw = k5(t, hipTw, -0.25f, 0.3f, 0.25f, hipTw)
                lean = k5(t, lean, -0.02f, 0.28f, 0.22f, lean)
                legB = k5(t, 0f, 0.4f, 1f, 0.9f, 0f)
                shout = k5(t, shout, 0.3f, 1f, 0.7f, shout)
            } else if (swing == 1) {
                // soco direto de esquerda
                punchSide = 2
                lsh = k5(t, lsh, 0.45f, -1.58f, -1.45f, lsh)
                lshZ = k5(t, lshZ, 0.25f, 0.0f, 0.02f, lshZ)
                lel = k5(t, lel, -2.4f, -0.05f, -0.2f, lel)
                rsh = k5(t, rsh, -0.9f, -0.75f, -0.85f, rsh)
                rel = k5(t, rel, -2.0f, -2.1f, -2.0f, rel)
                rshZ = k5(t, rshZ, 0.2f, 0.25f, 0.25f, rshZ)
                chestTw = k5(t, chestTw, 0.5f, -0.6f, -0.5f, chestTw)
                hipTw = k5(t, hipTw, 0.25f, -0.3f, -0.25f, hipTw)
                lean = k5(t, lean, -0.02f, 0.28f, 0.22f, lean)
                legB = k5(t, 0f, -0.4f, -1f, -0.9f, 0f)
                shout = k5(t, shout, 0.3f, 1f, 0.7f, shout)
            } else {
                // finalização: os dois punhos juntos, com o corpo inteiro
                punchSide = 3
                rsh = k5(t, rsh, 0.7f, -1.58f, -1.5f, rsh)
                lsh = k5(t, lsh, 0.7f, -1.58f, -1.5f, lsh)
                rshZ = k5(t, rshZ, 0.5f, 0.1f, 0.1f, rshZ)
                lshZ = k5(t, lshZ, 0.5f, 0.1f, 0.1f, lshZ)
                rel = k5(t, rel, -2.6f, -0.05f, -0.15f, rel)
                lel = k5(t, lel, -2.6f, -0.05f, -0.15f, lel)
                chestTw = k5(t, chestTw, 0f, 0f, 0f, chestTw)
                lean = k5(t, lean, -0.12f, 0.42f, 0.34f, lean)
                crouch = k5(t, 0f, 0.5f, 0.3f, 0.25f, 0f)
                tilt = k5(t, tilt, 0.1f, -0.1f, -0.05f, tilt)
                shout = k5(t, shout, 0.5f, 1f, 0.85f, shout)
            }
            if (t >= T_HIT && !released) {
                released = true
                for (k in 0..1) {
                    if ((k == 0 && (punchSide and 1) != 0) || (k == 1 && (punchSide and 2) != 0)) {
                        for (i in 0..6) {
                            val ang = rnd.nextFloat() * TWO_PI
                            val sp0 = (14f + rnd.nextFloat() * 30f) * rs
                            emitSpark(mkX[k], mkY[k], cos(ang) * sp0, sin(ang) * sp0, 0.28f, rs * 40f, if (i % 2 == 0) 0xFFFFFFFF.toInt() else 0xFFFFC23A.toInt(), 1.0f)
                        }
                    }
                }
            }
        } else {
            released = false
        }

        // suaviza a pose (o golpe mantém o ritmo; transições entre estados não dão tranco)
        pt[0] = lean; pt[1] = hipTw; pt[2] = chestTw; pt[3] = rsh; pt[4] = rshZ; pt[5] = rel
        pt[6] = 0f; pt[7] = lsh; pt[8] = lshZ; pt[9] = lel; pt[10] = 0f; pt[11] = hd
        pt[12] = tilt; pt[13] = shout; pt[14] = crouch; pt[15] = legB
        if (first) {
            for (i in 0 until NP) ps[i] = pt[i]
        } else {
            val k = min(1f, dt * (if (attacking) 34f else 18f))
            for (i in 0 until NP) ps[i] += (pt[i] - ps[i]) * k
        }

        // ── monta o modelo ──
        val bob = abs(sin(phase)) * 0.9f * mvs + sin(clock * 2.2f) * 0.12f * (1f - mvs)
        val crouchT = max(ps[14], landDip * 0.5f)
        val hip = 14.8f + bob - crouchT * 3.0f - hitKick * 0.4f - rollW * 3.5f
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
        translate(0f, 0f, -hitKick * 1.2f)

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
            a = lerp(a, 0.95f + sd * 0.12f, rollW)
            kn = lerp(kn, 0.5f, rollW)
            // base do soco: perna da frente adiantada, a de trás esticada
            a -= sd * 0.6f * lb
            kn += 0.5f * max(0f, sd * lb) + 0.2f * max(0f, -sd * lb)
            val ft = -(a + kn) * 0.8f
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
            ps[7], ps[8], ps[9], ps[10], false
        )

        flushZ(c)

        // ── efeitos do soco: rastro de velocidade e explosão no ponto de impacto ──
        if (attacking && punchSide != 0 && rollW < 0.5f) {
            val g = if (t < 0.18f) 0f else if (t < 0.30f) ease((t - 0.18f) / 0.12f) else max(0f, 1f - (t - 0.30f) / 0.28f)
            if (g > 0.02f) {
                p.strokeCap = Paint.Cap.ROUND
                for (k in 0..1) {
                    if (!((k == 0 && (punchSide and 1) != 0) || (k == 1 && (punchSide and 2) != 0))) continue
                    val fxk = mkX[k]
                    val fyk = mkY[k]
                    var dx = mkX[k + 4] - fxk
                    var dy = mkY[k + 4] - fyk
                    val dl = sqrt(dx * dx + dy * dy)
                    if (dl > 0.001f) {
                        dx /= dl
                        dy /= dl
                    }
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
                    p.strokeCap = Paint.Cap.BUTT
                    p.style = Paint.Style.FILL
                    // explosão no impacto
                    val imp = if (t >= T_HIT && t < 0.45f) 1f - (t - T_HIT) / (0.45f - T_HIT) else 0f
                    if (imp > 0.02f) {
                        p.color = VoxelArt.alpha(0xFFFFFFFF.toInt(), 0.55f * imp)
                        c.drawCircle(fxk - dx * 2f * rs, fyk - dy * 2f * rs, rs * (3f + 8f * (1f - imp)), p)
                        p.color = VoxelArt.alpha(0xFFFFB02E.toInt(), 0.38f * imp)
                        c.drawCircle(fxk - dx * 2f * rs, fyk - dy * 2f * rs, rs * (5f + 12f * (1f - imp)), p)
                    }
                    p.strokeCap = Paint.Cap.ROUND
                }
                p.strokeCap = Paint.Cap.BUTT
                p.style = Paint.Style.FILL
            }
        }

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
    }
}
