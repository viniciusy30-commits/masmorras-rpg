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
 * Explorador estilo Indiana Jones (chapéu fedora, jaqueta de couro, camisa cáqui, cinto, coldre, bolsa a tiracolo,
 * botas e chicote) em cubos 3D, no mesmo mini motor do Luffy/Mago/Guerreiro: z-buffer por pixel e contorno.
 * O ataque básico é um combo de chicote: estalo de direita, estalo de esquerda (revés) e chicotada por cima.
 * O botão de rolar faz uma cambalhota para a frente.
 */
object Explorer3D {
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
    private val SKIN = 0xFFE8B98A.toInt()
    private val SKIN_L = 0xFFF4CFA4.toInt()
    private val SKIN_D = 0xFFC48F62.toInt()
    private val SKIN_DD = 0xFF9E6A44.toInt()
    private val STUB = 0xFF8A6A55.toInt()
    private val HAIR = 0xFF2A1B12.toInt()
    private val HAIR_D = 0xFF170E09.toInt()
    private val FELT = 0xFF8A6238.toInt()
    private val FELT_L = 0xFFA37A4A.toInt()
    private val FELT_D = 0xFF5E4024.toInt()
    private val BAND = 0xFF3A2414.toInt()
    private val JK = 0xFF8B5A2B.toInt()
    private val JK_L = 0xFFA9743C.toInt()
    private val JK_D = 0xFF5F3C1B.toInt()
    private val JK_DD = 0xFF45290F.toInt()
    private val SHIRT = 0xFFD9C9A0.toInt()
    private val SHIRT_L = 0xFFEADCB6.toInt()
    private val SHIRT_D = 0xFFB5A57C.toInt()
    private val PANTS = 0xFF7A6A4E.toInt()
    private val PANTS_L = 0xFF8F7E5F.toInt()
    private val PANTS_D = 0xFF5B4D37.toInt()
    private val BELT = 0xFF3B2616.toInt()
    private val GOLD = 0xFFD9A93A.toInt()
    private val BOOT = 0xFF4B2F1B.toInt()
    private val BOOT_L = 0xFF664227.toInt()
    private val BOOT_D = 0xFF33200F.toInt()
    private val SOLE = 0xFF24160B.toInt()
    private val SATCHEL = 0xFF7D5A33.toInt()
    private val SATCHEL_D = 0xFF5B3F22.toInt()
    private val STRAP = 0xFF4E3419.toInt()
    private val WHITE = 0xFFFFFFFF.toInt()
    private val EYE = 0xFF3A2414.toInt()
    private val MOUTH = 0xFF5E2A2A.toInt()
    private val FSCAR = 0xFFC98873.toInt()

    // cores do chicote (desenhado em 2D por cima do modelo)
    private val WHIP = 0xFF6B4423.toInt()
    private val WHIP_L = 0xFFB98A52.toInt()
    private val WHIP_O = 0xFF24150A.toInt()

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

    /** Fedora: aba larga, copa com vinco no topo e fita escura (o eixo do chapéu é o Y local). */
    private fun fedora() {
        disc(0.0f, 7.3f, 0.26f, FELT)
        disc(0.3f, 6.4f, 0.2f, FELT_L)
        disc(-0.28f, 7.0f, 0.18f, FELT_D)
        disc(1.0f, 4.75f, 0.5f, FELT)
        disc(2.0f, 4.65f, 0.55f, FELT)
        disc(3.0f, 4.5f, 0.55f, FELT_L)
        disc(3.9f, 4.25f, 0.4f, FELT_L)
        disc(1.1f, 4.95f, 0.4f, BAND)
        // vinco no topo e beliscões na frente
        cube(0f, 4.3f, 0f, 0.9f, 0.3f, 3.9f, FELT_D)
        cube(-1.7f, 3.6f, 4.2f, 0.45f, 1.1f, 0.3f, FELT_D)
        cube(1.7f, 3.6f, 4.2f, 0.45f, 1.1f, 0.3f, FELT_D)
        // fivela dourada discreta na fita
        cube(0f, 1.1f, 5.0f, 0.5f, 0.35f, 0.1f, GOLD)
    }

    private fun head(turn: Float, tilt: Float, smirk: Float, hatT: Float) {
        push()
        translate(0f, 14.7f, 0f)
        rotY(turn)
        rotX(tilt)
        scale(0.9f, 0.9f, 0.9f)
        // pescoço, crânio e mandíbula
        cube(0f, 0.4f, 0f, 2.4f, 1.2f, 2.3f, SKIN_D)
        cube(0f, 5.6f, 0f, 4.8f, 3.6f, 4.5f, SKIN)
        cube(0f, 1.7f, 0.2f, 4.1f, 1.5f, 4.3f, SKIN)
        cube(0f, 5.7f, 4.53f, 4.5f, 3.4f, 0.06f, SKIN_L)
        cube(0f, 1.8f, 4.53f, 3.9f, 1.3f, 0.06f, SKIN_L)
        // barba por fazer
        cube(0f, 1.3f, 4.56f, 3.8f, 1.15f, 0.05f, STUB)
        cube(0f, 0.6f, 4.45f, 2.3f, 0.3f, 0.2f, STUB)
        // orelhas
        cube(-4.95f, 4.5f, 0.3f, 0.35f, 1.3f, 1.1f, SKIN_D)
        cube(4.95f, 4.5f, 0.3f, 0.35f, 1.3f, 1.1f, SKIN_D)
        // olhos: contorno escuro, branco, pupila e brilho
        cube(-2.2f, 6.4f, 4.6f, 1.4f, 0.15f, 0.06f, HAIR_D)
        cube(2.2f, 6.4f, 4.6f, 1.4f, 0.15f, 0.06f, HAIR_D)
        cube(-2.2f, 5.2f, 4.58f, 1.25f, 1.0f * eyeOpen, 0.06f, WHITE)
        cube(2.2f, 5.2f, 4.58f, 1.25f, 1.0f * eyeOpen, 0.06f, WHITE)
        cube(-2.1f, 5.15f, 4.65f, 0.8f, 0.9f * eyeOpen, 0.06f, EYE)
        cube(2.1f, 5.15f, 4.65f, 0.8f, 0.9f * eyeOpen, 0.06f, EYE)
        cube(-2.4f, 5.7f, 4.73f, 0.26f, 0.28f * eyeOpen, 0.05f, WHITE)
        cube(1.8f, 5.7f, 4.73f, 0.26f, 0.28f * eyeOpen, 0.05f, WHITE)
        // sobrancelhas grossas: a direita levanta no sorriso de canto
        push()
        translate(-2.3f, 7.15f, 4.62f)
        rotZ(-0.12f)
        cube(0f, 0f, 0f, 1.8f, 0.42f, 0.08f, HAIR_D)
        pop()
        push()
        translate(2.3f, 7.2f + 0.5f * smirk, 4.62f)
        rotZ(0.12f + 0.15f * smirk)
        cube(0f, 0f, 0f, 1.8f, 0.42f, 0.08f, HAIR_D)
        pop()
        // nariz reto
        cube(0f, 3.9f, 4.7f, 0.7f, 1.1f, 0.4f, SKIN_D)
        cube(0f, 3.1f, 4.95f, 0.6f, 0.35f, 0.22f, SKIN_L)
        // sorriso de canto
        cube(-0.2f, 2.1f, 4.58f, 2.3f, 0.28f, 0.06f, MOUTH)
        cube(2.5f, 2.2f + 0.35f * smirk, 4.6f, 0.6f, 0.28f, 0.06f, MOUTH)
        // cicatriz no queixo
        cube(-1.3f, 1.0f, 4.6f, 0.85f, 0.14f, 0.05f, FSCAR)
        cube(-1.3f, 0.9f, 4.6f, 0.1f, 0.35f, 0.05f, FSCAR)
        // cabelo escuro (a maior parte fica embaixo do chapéu)
        cube(0f, 8.9f, -0.3f, 4.6f, 1.0f, 4.6f, HAIR)
        cube(0f, 5.7f, -4.65f, 5.0f, 4.4f, 0.6f, HAIR)
        cube(0f, 2.8f, -4.75f, 4.2f, 1.6f, 0.4f, HAIR)
        cube(-4.85f, 7.4f, -0.3f, 0.4f, 1.9f, 4.2f, HAIR_D)
        cube(4.85f, 7.4f, -0.3f, 0.4f, 1.9f, 4.2f, HAIR_D)
        cube(-4.9f, 4.2f, 2.8f, 0.25f, 1.4f, 0.7f, HAIR_D)
        cube(4.9f, 4.2f, 2.8f, 0.25f, 1.4f, 0.7f, HAIR_D)
        // fedora, um pouco inclinado para trás para o rosto aparecer
        push()
        translate(0f, 10.2f, 0.15f)
        rotX(-0.08f + hatT)
        fedora()
        pop()
        pop()
    }

    /** Quadril: calça, cinto com fivela, coldre, barriga com camisa e a bolsa a tiracolo balançando. */
    private fun torsoLow(satSw: Float) {
        cube(0f, 0.1f, 0f, 5.5f, 1.7f, 3.5f, PANTS)
        cube(0f, 0.0f, 3.55f, 4.4f, 1.3f, 0.08f, PANTS_L)
        cube(0f, 0.2f, -3.55f, 5.0f, 1.3f, 0.08f, PANTS_D)
        // cinto de couro com fivela dourada
        cube(0f, 2.6f, 0f, 5.75f, 0.85f, 3.75f, BELT)
        cube(0f, 2.6f, 3.78f, 0.95f, 0.7f, 0.1f, GOLD)
        cube(0f, 2.6f, 3.85f, 0.45f, 0.35f, 0.06f, BELT)
        // camisa cáqui
        cube(0f, 5.6f, 0f, 4.7f, 2.2f, 3.3f, SHIRT)
        cube(0f, 5.6f, 3.32f, 4.4f, 2.0f, 0.06f, SHIRT_L)
        cube(0f, 4.6f, 3.4f, 0.14f, 0.14f, 0.05f, SHIRT_D)
        cube(0f, 5.8f, 3.4f, 0.14f, 0.14f, 0.05f, SHIRT_D)
        cube(0f, 7.0f, 3.4f, 0.14f, 0.14f, 0.05f, SHIRT_D)
        // coldre no quadril direito
        cube(-5.5f, 1.0f, 1.0f, 0.75f, 1.9f, 1.5f, BELT)
        cube(-5.5f, -1.1f, 1.0f, 0.6f, 1.0f, 1.2f, BELT)
        cube(-5.0f, 3.4f, 0.8f, 0.5f, 0.9f, 0.5f, JK_DD)
        // bolsa a tiracolo no quadril esquerdo
        push()
        translate(5.7f, 3.0f, -0.6f)
        rotZ(satSw)
        cube(0.9f, -2.2f, 0f, 1.1f, 2.1f, 3.0f, SATCHEL)
        cube(0.9f, -0.35f, 0f, 1.2f, 0.8f, 3.1f, SATCHEL_D)
        cube(2.05f, -1.6f, 0f, 0.1f, 0.5f, 0.55f, GOLD)
        cube(2.05f, -3.0f, 0f, 0.1f, 0.15f, 2.6f, SATCHEL_D)
        pop()
    }

    /** Peito com camisa, jaqueta de couro aberta, lapelas, bolsos, alça da bolsa cruzada e pescoço. */
    private fun torsoUp(vF: Float, vB: Float) {
        // camisa à mostra no meio do peito
        cube(0f, 10.7f, 0f, 5.8f, 2.8f, 3.7f, SHIRT)
        cube(0f, 11.2f, 3.74f, 2.9f, 2.0f, 0.06f, SHIRT_L)
        cube(0f, 11.0f, 3.78f, 0.07f, 2.4f, 0.05f, SHIRT_D)
        // jaqueta: costas, ombros e painéis da frente
        cube(0f, 9.8f, -3.9f, 6.0f, 3.9f, 0.5f, JK)
        cube(0f, 10.0f, -4.42f, 0.1f, 3.5f, 0.05f, JK_DD)
        cube(-5.3f, 13.7f, 0f, 2.7f, 0.62f, 4.0f, JK)
        cube(5.3f, 13.7f, 0f, 2.7f, 0.62f, 4.0f, JK)
        cube(-5.3f, 14.3f, 0f, 2.5f, 0.06f, 3.8f, JK_L)
        cube(5.3f, 14.3f, 0f, 2.5f, 0.06f, 3.8f, JK_L)
        cube(-4.4f, 10.5f, 3.85f, 1.5f, 3.0f, 0.45f, JK)
        cube(4.4f, 10.5f, 3.85f, 1.5f, 3.0f, 0.45f, JK)
        cube(-2.95f, 10.5f, 3.9f, 0.12f, 3.0f, 0.5f, JK_D)
        cube(2.95f, 10.5f, 3.9f, 0.12f, 3.0f, 0.5f, JK_D)
        // lapelas e gola
        cube(-3.0f, 12.9f, 3.95f, 1.0f, 1.3f, 0.4f, JK_L)
        cube(3.0f, 12.9f, 3.95f, 1.0f, 1.3f, 0.4f, JK_L)
        cube(0f, 14.0f, -2.2f, 3.0f, 0.6f, 1.4f, JK_D)
        // bolsos
        cube(-4.5f, 8.6f, 4.32f, 0.9f, 0.85f, 0.05f, JK_D)
        cube(4.5f, 8.6f, 4.32f, 0.9f, 0.85f, 0.05f, JK_D)
        // alça da bolsa cruzando o peito (ombro direito até o quadril esquerdo)
        push()
        translate(0f, 9.4f, 0f)
        rotZ(0.78f)
        cube(0f, 0f, 0f, 0.55f, 6.0f, 4.5f, STRAP)
        pop()
        // pescoço
        cube(0f, 14.4f, 0f, 2.2f, 0.9f, 2.2f, SKIN_D)
        // abas da jaqueta balançando (frente e costas)
        push()
        translate(-4.4f, 7.5f, 3.85f)
        rotX(vF)
        cube(0f, -1.8f, 0f, 1.5f, 1.9f, 0.4f, JK)
        cube(0f, -3.6f, 0f, 1.5f, 0.14f, 0.44f, JK_D)
        pop()
        push()
        translate(4.4f, 7.5f, 3.85f)
        rotX(vF)
        cube(0f, -1.8f, 0f, 1.5f, 1.9f, 0.4f, JK)
        cube(0f, -3.6f, 0f, 1.5f, 0.14f, 0.44f, JK_D)
        pop()
        push()
        translate(0f, 6.3f, -3.9f)
        rotX(vB)
        cube(0f, -1.8f, -0.1f, 6.0f, 1.9f, 0.4f, JK)
        cube(0f, -3.65f, -0.1f, 6.0f, 0.14f, 0.44f, JK_D)
        pop()
    }

    /**
     * Braço (side = +1 esquerdo / -1 direito). Ângulo negativo = para a frente.
     * O direito segura o cabo do chicote; a marca [id] fica na ponta do cabo (de onde sai a corda).
     */
    private fun arm(side: Float, sh: Float, shZ: Float, el: Float, tw: Float, id: Int) {
        push()
        translate(side * 7.1f, 12.3f, 0f)
        projectTo(0f, 0f, 0f, id + 4)
        rotZ(side * shZ)
        rotX(sh)
        rotY(tw)
        // ombro e braço (mangas da jaqueta)
        cube(0f, -0.4f, 0f, 2.35f, 2.2f, 2.3f, JK)
        cube(side * 0.5f, 0.4f, 0.4f, 1.6f, 0.9f, 1.6f, JK_L)
        cube(0f, -2.5f, 0f, 1.95f, 2.75f, 1.95f, JK)
        cube(side * 0.2f, -1.7f, 1.2f, 1.45f, 1.5f, 0.75f, JK_L)
        translate(0f, -5.3f, 0f)
        rotX(el)
        // antebraço com punho dobrado
        cube(0f, -2.4f, 0f, 1.85f, 2.65f, 1.85f, JK)
        cube(0f, -2.0f, 1.2f, 1.2f, 1.3f, 0.55f, JK_L)
        cube(0f, -4.7f, 0f, 1.95f, 0.4f, 1.95f, JK_D)
        translate(0f, -5.0f, 0f)
        // mão
        cube(0f, -1.2f, 0.1f, 1.6f, 1.3f, 1.7f, SKIN)
        cube(0f, -0.9f, 1.7f, 1.35f, 0.95f, 0.1f, SKIN_L)
        cube(0f, -2.5f, 0.1f, 1.5f, 0.12f, 1.5f, SKIN_D)
        if (side < 0f) {
            // cabo de couro do chicote com anéis dourados
            cube(0f, -1.3f, 2.2f, 0.55f, 0.55f, 2.2f, BELT)
            cube(0f, -1.3f, 1.2f, 0.62f, 0.62f, 0.15f, GOLD)
            cube(0f, -1.3f, 3.0f, 0.62f, 0.62f, 0.15f, GOLD)
            projectTo(0f, -1.3f, 4.5f, id)
        } else {
            projectTo(0f, -1.4f, 0.4f, id)
        }
        pop()
    }

    /** Perna: calça, joelho, canela e bota alta de couro. Ângulo positivo = para trás. */
    private fun leg(side: Float, a: Float, knee: Float, foot: Float, hipY: Float) {
        push()
        translate(side * 2.6f, hipY, 0f)
        rotX(a)
        cube(0f, -2.9f, 0f, 2.75f, 3.1f, 2.85f, PANTS)
        cube(side * 0.9f, -2.7f, 2.9f, 1.4f, 2.5f, 0.06f, PANTS_L)
        cube(0f, -6.9f, 0f, 2.2f, 0.95f, 2.3f, PANTS_D)
        translate(0f, -7.6f, 0f)
        rotX(knee)
        cube(0f, -2.9f, 0f, 2.0f, 3.0f, 2.1f, PANTS)
        cube(0f, -4.2f, 0f, 2.25f, 1.9f, 2.35f, BOOT)
        cube(0f, -2.3f, 0f, 2.3f, 0.2f, 2.4f, BOOT_D)
        cube(0f, -3.9f, 2.4f, 1.3f, 1.2f, 0.05f, BOOT_L)
        translate(0f, -5.9f, 0f)
        rotX(foot)
        cube(0f, -0.2f, 1.0f, 2.0f, 0.65f, 2.6f, BOOT)
        cube(0f, -0.9f, 1.3f, 2.05f, 0.25f, 3.3f, SOLE)
        cube(0f, -0.3f, 3.9f, 1.85f, 0.55f, 0.9f, BOOT_L)
        pop()
    }

    private fun skeleton(
        hip: Float, hipTw: Float, lean: Float, chestTw: Float, breath: Float,
        aR: Float, kR: Float, fR: Float, aL: Float, kL: Float, fL: Float,
        vF: Float, vB: Float, satSw: Float, hatT: Float,
        turn: Float, tilt: Float, smirk: Float,
        rsh: Float, rshZ: Float, rel: Float, lsh: Float, lshZ: Float, lel: Float
    ) {
        leg(-1f, aR, kR, fR, hip)
        leg(1f, aL, kL, fL, hip)
        push()
        translate(0f, hip, 0f)
        rotY(hipTw)
        rotX(lean)
        torsoLow(satSw)
        push()
        rotY(chestTw)
        translate(0f, breath, 0f)
        torsoUp(vF, vB)
        projectTo(0f, 10f, 0f, 3)
        arm(-1f, rsh, rshZ, rel, 0f, 0)
        arm(1f, lsh, lshZ, lel, 0f, 1)
        projectTo(0f, 19.4f, 2f, 2)
        head(turn, tilt, smirk, hatT)
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
    var rollP = -1f        // 0..1 durante a cambalhota; <0 = fora dela
    var inAir = false
    var airVz = 0f
    var airH = 0f
    var swing = 0          // 0 estalo de direita, 1 estalo de revés, 2 chicotada por cima
    var alphaV = 1f

    /** Instante (0..1 da animação) em que a corda chega ao alcance máximo e estala. */
    const val T_HIT = 0.30f

    fun impactAt(@Suppress("UNUSED_PARAMETER") sw: Int): Float = T_HIT

    // ───────────── estado de animação ─────────────
    private const val MS = 0.8f   // escala do modelo (altura parecida com a dos outros heróis)
    private const val NP = 16
    private const val I_LEAN = 0
    private const val I_HIPTW = 1
    private const val I_CHEST = 2
    private const val I_RSH = 3
    private const val I_RSHZ = 4
    private const val I_REL = 5
    private const val I_LSH = 6
    private const val I_LSHZ = 7
    private const val I_LEL = 8
    private const val I_TILT = 9
    private const val I_SMIRK = 10
    private const val I_CROUCH = 11
    private const val I_LEGB = 12
    private const val I_WEXT = 13
    private const val I_WHANG = 14
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
    private var rollPuff = false
    private var crackT = 0f
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

    /** Interpola o ciclo do golpe: repouso → preparação (0.18) → estalo (0.30) → sustentação (0.55) → volta ao repouso. */
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

        // ── alvos da pose (repouso, caminhada, ar, cambalhota) ──
        var lean = 0.05f + 0.12f * spN
        var hipTw = 0f
        var chestTw = -sin(phase) * 0.16f * mvs
        var rsh = -0.1f + sin(phase) * 0.75f * mvs
        var lsh = -0.1f - sin(phase) * 0.75f * mvs
        var rshZ = 0.14f + 0.03f * sin(clock * 1.4f)
        var lshZ = 0.14f + 0.03f * sin(clock * 1.4f + 1f)
        var rel = -0.3f - 0.55f * mvs
        var lel = -0.3f - 0.55f * mvs
        var tilt = 0f
        var smirk = 0.3f
        var crouch = 0f
        var legB = 0f
        var wExt = 0f
        var wHang = 1f

        // no ar: braços abertos, chicote solto
        rshZ = lerp(rshZ, 1.1f, airW)
        lshZ = lerp(lshZ, 1.1f, airW)
        rsh = lerp(rsh, -0.5f, airW)
        lsh = lerp(lsh, -0.35f, airW)
        rel = lerp(rel, -0.3f, airW)
        lel = lerp(lel, -0.2f, airW)
        smirk = max(smirk, 0.6f * airW)

        // cambalhota para a frente, encolhido e protegendo a cabeça
        if (rolling) {
            lean = lerp(lean, 0.5f, rollW)
            crouch = lerp(crouch, 0.7f, rollW)
            rsh = lerp(rsh, -1.1f, rollW)
            lsh = lerp(lsh, -1.1f, rollW)
            rshZ = lerp(rshZ, 0.25f, rollW)
            lshZ = lerp(lshZ, 0.25f, rollW)
            rel = lerp(rel, -2.0f, rollW)
            lel = lerp(lel, -2.0f, rollW)
            tilt = lerp(tilt, 0.5f, rollW)
            smirk = max(smirk, 0.8f * rollW)
        }

        // ── combo de chicote ──
        if (attacking && rollW < 0.5f) {
            wHang = 0f
            if (swing == 0) {
                // estalo de direita: o braço vai atrás e a corda corta à frente
                rsh = k5(t, rsh, 0.9f, -1.58f, -1.45f, rsh)
                rshZ = k5(t, rshZ, 0.25f, 0.0f, 0.02f, rshZ)
                rel = k5(t, rel, -2.4f, -0.05f, -0.2f, rel)
                lsh = k5(t, lsh, -0.9f, -0.75f, -0.85f, lsh)
                lel = k5(t, lel, -2.0f, -2.1f, -2.0f, lel)
                lshZ = k5(t, lshZ, 0.2f, 0.25f, 0.25f, lshZ)
                chestTw = k5(t, chestTw, -0.5f, 0.6f, 0.5f, chestTw)
                hipTw = k5(t, hipTw, -0.25f, 0.3f, 0.25f, hipTw)
                lean = k5(t, lean, -0.02f, 0.28f, 0.22f, lean)
                legB = k5(t, 0f, 0.4f, 1f, 0.9f, 0f)
                smirk = k5(t, smirk, 0.5f, 1f, 0.8f, smirk)
                wExt = k5(t, 0f, -0.45f, 1f, 0.75f, 0.1f)
            } else if (swing == 1) {
                // revés: o braço cruza o corpo e a corda varre de dentro para fora
                rsh = k5(t, rsh, -0.5f, -1.5f, -1.35f, rsh)
                rshZ = k5(t, rshZ, -0.55f, 0.55f, 0.5f, rshZ)
                rel = k5(t, rel, -1.9f, -0.1f, -0.3f, rel)
                lsh = k5(t, lsh, 0.5f, 0.6f, 0.5f, lsh)
                lel = k5(t, lel, -0.8f, -0.8f, -0.8f, lel)
                lshZ = k5(t, lshZ, 0.5f, 0.7f, 0.6f, lshZ)
                chestTw = k5(t, chestTw, 0.5f, -0.6f, -0.5f, chestTw)
                hipTw = k5(t, hipTw, 0.25f, -0.3f, -0.25f, hipTw)
                lean = k5(t, lean, -0.02f, 0.28f, 0.22f, lean)
                legB = k5(t, 0f, -0.4f, -1f, -0.9f, 0f)
                smirk = k5(t, smirk, 0.5f, 1f, 0.8f, smirk)
                wExt = k5(t, 0f, -0.45f, 1f, 0.75f, 0.1f)
            } else {
                // chicotada por cima: braço para o alto e a corda desce estalando no chão
                rsh = k5(t, rsh, -2.9f, -1.25f, -1.1f, rsh)
                rshZ = k5(t, rshZ, 0.2f, 0.05f, 0.05f, rshZ)
                rel = k5(t, rel, -1.7f, -0.1f, -0.25f, rel)
                lsh = k5(t, lsh, -0.5f, -0.6f, -0.6f, lsh)
                lel = k5(t, lel, -1.2f, -1.2f, -1.2f, lel)
                lshZ = k5(t, lshZ, 0.3f, 0.3f, 0.3f, lshZ)
                chestTw = k5(t, chestTw, 0f, 0f, 0f, chestTw)
                lean = k5(t, lean, -0.15f, 0.42f, 0.34f, lean)
                crouch = k5(t, 0f, 0.15f, 0.35f, 0.3f, 0f)
                tilt = k5(t, tilt, 0.1f, -0.1f, -0.05f, tilt)
                smirk = k5(t, smirk, 0.6f, 1f, 0.9f, smirk)
                wExt = k5(t, 0f, -0.5f, 1f, 0.9f, 0.1f)
            }
        }

        // suaviza a pose (o golpe mantém o ritmo; transições entre estados não dão tranco)
        pt[I_LEAN] = lean
        pt[I_HIPTW] = hipTw
        pt[I_CHEST] = chestTw
        pt[I_RSH] = rsh
        pt[I_RSHZ] = rshZ
        pt[I_REL] = rel
        pt[I_LSH] = lsh
        pt[I_LSHZ] = lshZ
        pt[I_LEL] = lel
        pt[I_TILT] = tilt
        pt[I_SMIRK] = smirk
        pt[I_CROUCH] = crouch
        pt[I_LEGB] = legB
        pt[I_WEXT] = wExt
        pt[I_WHANG] = wHang
        if (first) {
            for (i in 0 until NP) ps[i] = pt[i]
        } else {
            val k = min(1f, dt * (if (attacking) 34f else 18f))
            for (i in 0 until NP) ps[i] += (pt[i] - ps[i]) * k
            // a corda não pode atrasar: ela precisa estar esticada junto com o estalo
            ps[I_WEXT] = pt[I_WEXT]
        }

        // ── monta o modelo ──
        val bob = abs(sin(phase)) * 0.9f * mvs + sin(clock * 2.2f) * 0.12f * (1f - mvs)
        val crouchT = max(ps[I_CROUCH], landDip * 0.5f)
        val hip = 14.8f + bob - crouchT * 3.0f - hitKick * 0.4f
        val lb = ps[I_LEGB]

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
        if (rolling) {
            // cambalhota completa para a frente em torno do centro do corpo
            translate(0f, 10f + sin(rollP * PI.toFloat()) * 3f, 0f)
            rotX(TWO_PI * ease(rollP))
            translate(0f, -10f, 0f)
        }

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
            // cambalhota: joelhos junto ao peito
            a = lerp(a, -1.6f, rollW)
            kn = lerp(kn, 2.2f, rollW)
            // base do golpe: perna da frente adiantada, a de trás esticada
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
        val satSw = 0.1f + spk * 0.3f * sin(phase * 2f + 0.5f) + hitKick * 0.25f
        val hatT = 0.02f + spk * 0.05f * sin(phase * 2f) - airW * 0.1f + hitKick * 0.1f
        val breath = sin(clock * 2.2f) * 0.1f

        skeleton(
            hip, ps[I_HIPTW], ps[I_LEAN] - hitKick * 0.3f, ps[I_CHEST], breath,
            aR, kR, fR, aL, kL, fL,
            vF, vB, satSw, hatT,
            -ps[I_CHEST] * 0.6f, ps[I_TILT] - ps[I_LEAN] * 0.5f, ps[I_SMIRK],
            ps[I_RSH], ps[I_RSHZ], ps[I_REL], ps[I_LSH], ps[I_LSHZ], ps[I_LEL]
        )

        flushZ(c)

        // ── chicote: corda desenhada em 2D a partir da ponta do cabo ──
        val hx0 = mkX[0]
        val hy0 = mkY[0]
        val fxs = sin(yaw)
        val fys = cos(yaw) * 0.8f
        val perpX = cos(yaw)
        val perpY = -sin(yaw) * 0.8f
        val reach = if (swing == 2) Player.EXPL_REACH_3 else Player.EXPL_REACH
        val ext = ps[I_WEXT]
        val ext01 = ext.coerceIn(0f, 1f)
        val hang = ps[I_WHANG].coerceIn(0f, 1f)
        val handH = fy - hy0
        val sgn = if (swing == 1) -1f else 1f
        val segs = 16
        var prevX = hx0
        var prevY = hy0
        var tipX = hx0
        var tipY = hy0
        p.strokeCap = Paint.Cap.ROUND
        p.style = Paint.Style.STROKE
        for (i in 1..segs) {
            val u = i / segs.toFloat()
            // forma pendurada: a corda cai do cabo balançando de leve
            val hxp = hx0 + (sin(u * 2.2f) * 2.2f + sin(phase) * 2.5f * u * spN) * rs
            val hyp = hy0 + u * (15f + 4f * u) * rs
            // forma estendida: ondula e vai ficando reta até o estalo
            val along = u * reach * ext
            val wave = sgn * (10f * (1f - ext01 * 0.92f) + 1.2f) * sin(u * 6.0f + clock * 20f * (1f - ext01)) * u
            val hgt = if (swing == 2) handH * (1f - ext01 * Math.pow(u.toDouble(), 1.4).toFloat()) + max(0f, -ext) * u * 10f
            else handH * (1f - 0.12f * u * u) + max(0f, -ext) * u * 6f
            val ex = hx0 + along * fxs + wave * perpX
            val ey = fy + along * fys + wave * perpY - hgt
            val px = lerp(ex, hxp, hang)
            val py = lerp(ey, hyp, hang)
            val w = max(1.4f, rs * (1.05f - 0.55f * u))
            p.strokeWidth = w + 1.6f
            p.color = VoxelArt.alpha(WHIP_O, 0.9f)
            c.drawLine(prevX, prevY, px, py, p)
            p.strokeWidth = w
            p.color = if (i % 4 == 0) WHIP_L else WHIP
            c.drawLine(prevX, prevY, px, py, p)
            prevX = px
            prevY = py
            tipX = px
            tipY = py
        }
        p.strokeCap = Paint.Cap.BUTT
        p.style = Paint.Style.FILL

        // estalo na ponta: faíscas, clarão e (na chicotada por cima) poeira do chão
        if (attacking && rollW < 0.5f) {
            if (t >= T_HIT && !released) {
                released = true
                crackT = 0.16f
                for (i in 0..7) {
                    val ang = rnd.nextFloat() * TWO_PI
                    val sp0 = (14f + rnd.nextFloat() * 34f) * rs
                    emitSpark(tipX, tipY, cos(ang) * sp0, sin(ang) * sp0, 0.3f, rs * 40f, if (i % 2 == 0) 0xFFFFFFFF.toInt() else 0xFFFFC23A.toInt(), 1.0f)
                }
                if (swing == 2) {
                    for (i in 0..5) {
                        val ang = rnd.nextFloat() * TWO_PI
                        val sp0 = (10f + rnd.nextFloat() * 26f) * rs
                        emitSpark(tipX, tipY, cos(ang) * sp0, sin(ang) * sp0 * 0.5f - 8f * rs, 0.4f, rs * 30f, 0xFFE9D9B0.toInt(), 1.2f)
                    }
                }
            }
        } else {
            released = false
        }
        if (crackT > 0f) {
            crackT -= dt
            val q = (crackT / 0.16f).coerceIn(0f, 1f)
            p.color = VoxelArt.alpha(0xFFFFFFFF.toInt(), 0.8f * q)
            c.drawCircle(tipX, tipY, rs * (1.5f + 4.5f * (1f - q)), p)
            p.color = VoxelArt.alpha(0xFFFFB02E.toInt(), 0.45f * q)
            c.drawCircle(tipX, tipY, rs * (2.5f + 7f * (1f - q)), p)
        }

        // ── poeira na largada da cambalhota ──
        if (rolling) {
            if (rollP > 0.05f && !rollPuff) {
                rollPuff = true
                for (i in 0..6) {
                    val ang = rnd.nextFloat() * TWO_PI
                    val sp0 = (12f + rnd.nextFloat() * 26f) * rs
                    emitSpark(fx, fy - 2f * rs, cos(ang) * sp0, sin(ang) * sp0 * 0.5f - 6f * rs, 0.36f, rs * 30f, 0xFFE9D9B0.toInt(), 1.2f)
                }
            }
        } else {
            rollPuff = false
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
