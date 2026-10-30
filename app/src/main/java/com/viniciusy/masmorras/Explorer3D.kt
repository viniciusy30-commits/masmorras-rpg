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
 * Explorador estilo Indiana Jones (fedora, jaqueta de couro em camadas, camisa cáqui, cinto, bolsa a tiracolo,
 * botas e chicote) em cubos 3D, no mesmo mini motor do Luffy/Mago/Guerreiro: z-buffer por pixel e contorno.
 * A corda do chicote também é 3D (cubos trançados no mesmo z-buffer do corpo), então nunca aparece por cima do modelo.
 * O ataque básico é um combo de chicote: estalo lateral, corte em diagonal e chicotada por cima.
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
    private val STITCH = 0xFF3A210D.toInt()
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

    // cores do chicote (corda 3D de couro trançado)
    private val WHIP = 0xFF6B4423.toInt()
    private val WHIP_L = 0xFFA87A45.toInt()
    private val WHIP_T = 0xFFD8BE86.toInt()
    private val WHIP_HOT = 0xFFFFF4C8.toInt()

    // medidas da jaqueta (meia-largura, meia-profundidade, bordas de baixo e de cima dos painéis)
    private const val JW = 5.8f
    private const val JD = 4.0f
    private const val JB = 6.0f
    private const val JT = 13.2f

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

    /** Faixa diagonal da alça (retângulo fino girado, colado na superfície da roupa na profundidade z). */
    private fun strapSeg(x0: Float, y0: Float, x1: Float, y1: Float, z: Float, col: Int) {
        val dx = x1 - x0
        val dy = y1 - y0
        val len = sqrt(dx * dx + dy * dy)
        push()
        translate((x0 + x1) * 0.5f, (y0 + y1) * 0.5f, z)
        rotZ(atan2(-dx, dy))
        cube(0f, 0f, 0f, 0.62f, len * 0.5f + 0.05f, 0.12f, col)
        pop()
    }

    /**
     * Quadril: calça, cinto com fivela, barriga de camisa e a bolsa pendurada nas costas.
     * Cada camada encosta na vizinha sem atravessá-la: calça (até 1.8), cinto (1.8..3.2), camisa (3.2..7.0).
     */
    private fun torsoLow(satSw: Float) {
        // calça
        cube(0f, 0.2f, 0f, 5.4f, 1.6f, 3.4f, PANTS)
        cube(0f, 0.2f, 3.43f, 4.2f, 1.3f, 0.05f, PANTS_L)
        cube(0f, 0.2f, -3.43f, 4.8f, 1.3f, 0.05f, PANTS_D)
        // cinto de couro com fivela dourada e bolsinho atrás
        cube(0f, 2.5f, 0f, 5.55f, 0.7f, 3.55f, BELT)
        cube(0f, 2.5f, 3.6f, 0.95f, 0.6f, 0.08f, GOLD)
        cube(0f, 2.5f, 3.68f, 0.5f, 0.32f, 0.04f, BELT)
        cube(-3.6f, 2.05f, -3.95f, 1.2f, 0.85f, 0.42f, SATCHEL_D)
        cube(-3.6f, 2.7f, -4.0f, 1.25f, 0.2f, 0.45f, STRAP)
        // barriga (camisa cáqui)
        cube(0f, 5.1f, 0f, 4.7f, 1.9f, 3.3f, SHIRT)
        cube(0f, 5.1f, 3.34f, 1.9f, 1.8f, 0.05f, SHIRT_L)
        cube(0f, 5.1f, 3.4f, 0.07f, 1.8f, 0.03f, SHIRT_D)
        cube(0f, 3.9f, 3.43f, 0.2f, 0.2f, 0.04f, GOLD)
        cube(0f, 5.5f, 3.43f, 0.2f, 0.2f, 0.04f, GOLD)
        // bolsa de lona nas costas, esquerda: presa por cima, balança só para fora do corpo
        push()
        translate(2.7f, 6.3f, -4.1f)
        rotX(satSw)
        cube(0f, -3.0f, -1.6f, 3.1f, 3.0f, 1.6f, SATCHEL)
        cube(0f, -1.4f, -3.3f, 3.2f, 1.5f, 0.12f, SATCHEL_D)
        cube(0f, -0.12f, -1.6f, 3.2f, 0.12f, 1.75f, SATCHEL_D)
        cube(0f, -2.9f, -3.45f, 0.5f, 0.55f, 0.06f, GOLD)
        cube(-1.9f, -4.6f, -3.28f, 0.9f, 1.0f, 0.1f, SATCHEL_D)
        cube(1.9f, -4.6f, -3.28f, 0.9f, 1.0f, 0.1f, SATCHEL_D)
        cube(0f, -6.05f, -1.6f, 3.2f, 0.12f, 1.7f, STRAP)
        pop()
    }

    /**
     * Peito: camisa por baixo e a jaqueta como casca (costas, laterais, frentes abertas e ombreira em cima),
     * tudo encostado e nunca atravessado. A alça cruza o peito colada na superfície, em degraus por camada.
     */
    private fun torsoUp(vF: Float) {
        // camisa do peito
        cube(0f, 10.1f, 0f, 4.7f, 3.1f, 3.3f, SHIRT)
        cube(0f, 10.1f, 3.34f, 1.9f, 3.0f, 0.05f, SHIRT_L)
        cube(0f, 10.1f, 3.4f, 0.07f, 3.0f, 0.03f, SHIRT_D)
        cube(0f, 8.2f, 3.43f, 0.2f, 0.2f, 0.04f, GOLD)
        cube(0f, 11.6f, 3.43f, 0.2f, 0.2f, 0.04f, GOLD)
        // costas da jaqueta
        cube(0f, 8.1f, -JD + 0.4f, JW, 5.1f, 0.4f, JK)
        cube(0f, 8.1f, -JD - 0.02f, 0.06f, 4.8f, 0.03f, STITCH)
        cube(0f, 3.1f, -JD + 0.4f, JW, 0.1f, 0.43f, JK_D)
        // laterais
        cube(-JW + 0.4f, 9.6f, 0f, 0.4f, 3.6f, JD - 0.8f, JK)
        cube(JW - 0.4f, 9.6f, 0f, 0.4f, 3.6f, JD - 0.8f, JK)
        // frentes abertas
        cube(-4.2f, 9.6f, JD - 0.4f, 1.6f, 3.6f, 0.4f, JK)
        cube(4.2f, 9.6f, JD - 0.4f, 1.6f, 3.6f, 0.4f, JK)
        cube(-2.75f, 9.6f, JD - 0.4f, 0.15f, 3.6f, 0.42f, JK_D)
        cube(2.75f, 9.6f, JD - 0.4f, 0.15f, 3.6f, 0.42f, JK_D)
        // bolsos do peito com aba e botão
        cube(-4.3f, 10.5f, JD + 0.05f, 1.15f, 0.95f, 0.06f, JK_D)
        cube(-4.3f, 11.25f, JD + 0.1f, 1.2f, 0.2f, 0.06f, JK_L)
        cube(-4.3f, 10.7f, JD + 0.14f, 0.18f, 0.18f, 0.04f, GOLD)
        cube(4.3f, 10.5f, JD + 0.05f, 1.15f, 0.95f, 0.06f, JK_D)
        cube(4.3f, 11.25f, JD + 0.1f, 1.2f, 0.2f, 0.06f, JK_L)
        cube(4.3f, 10.7f, JD + 0.14f, 0.18f, 0.18f, 0.04f, GOLD)
        // ombreira: três peças deixando o buraco do pescoço
        cube(-3.95f, 13.45f, 0f, 1.95f, 0.25f, JD, JK)
        cube(3.95f, 13.45f, 0f, 1.95f, 0.25f, JD, JK)
        cube(0f, 13.45f, -3.1f, 2.0f, 0.25f, 0.9f, JK)
        cube(-3.95f, 13.72f, 0f, 1.7f, 0.04f, JD - 0.3f, JK_L)
        cube(3.95f, 13.72f, 0f, 1.7f, 0.04f, JD - 0.3f, JK_L)
        // gola alta atrás e dos lados, lapelas viradas na frente
        cube(0f, 14.1f, -2.55f, 3.1f, 0.55f, 0.5f, JK_D)
        cube(-2.65f, 13.95f, -0.5f, 0.5f, 0.45f, 2.0f, JK_D)
        cube(2.65f, 13.95f, -0.5f, 0.5f, 0.45f, 2.0f, JK_D)
        push()
        translate(-2.6f, 13.8f, 2.7f)
        rotZ(0.35f)
        cube(0f, 0f, 0f, 0.5f, 0.6f, 0.8f, JK_L)
        pop()
        push()
        translate(2.6f, 13.8f, 2.7f)
        rotZ(-0.35f)
        cube(0f, 0f, 0f, 0.5f, 0.6f, 0.8f, JK_L)
        pop()
        // gola da camisa e pescoço
        cube(-1.1f, 13.6f, 2.5f, 0.9f, 0.4f, 0.4f, SHIRT_L)
        cube(1.1f, 13.6f, 2.5f, 0.9f, 0.4f, 0.4f, SHIRT_L)
        cube(0f, 14.1f, 0f, 1.9f, 1.0f, 1.9f, SKIN_D)
        // alça da bolsa: ombro direito -> quadril esquerdo, em degraus colados na jaqueta
        cube(-4.4f, 13.82f, 0f, 0.62f, 0.1f, JD + 0.12f, STRAP)
        cube(-4.4f, 13.5f, JD + 0.1f, 0.62f, 0.4f, 0.12f, STRAP)
        strapSeg(-4.4f, 13.2f, -2.6f, 12.0f, JD + 0.1f, STRAP)
        strapSeg(-2.6f, 12.0f, 2.6f, 8.38f, 3.5f, STRAP)
        strapSeg(2.6f, 8.38f, 5.8f, 6.15f, JD + 0.1f, STRAP)
        strapSeg(-4.4f, 13.4f, 5.2f, 6.6f, -JD - 0.1f, STRAP)
        cube(-4.4f, 13.5f, -JD - 0.1f, 0.62f, 0.4f, 0.12f, STRAP)
        // abas da jaqueta na frente: só balançam para fora, nunca para dentro do cinto
        push()
        translate(-4.2f, JB, JD - 0.4f)
        rotX(-vF)
        cube(0f, -1.5f, 0f, 1.6f, 1.5f, 0.4f, JK)
        cube(0f, -3.0f, 0f, 1.6f, 0.1f, 0.43f, JK_D)
        pop()
        push()
        translate(4.2f, JB, JD - 0.4f)
        rotX(-vF)
        cube(0f, -1.5f, 0f, 1.6f, 1.5f, 0.4f, JK)
        cube(0f, -3.0f, 0f, 1.6f, 0.1f, 0.43f, JK_D)
        pop()
    }

    /**
     * Braço (side = +1 esquerdo / -1 direito). Ângulo negativo = para a frente.
     * O ombro fica afastado da jaqueta: o giro do bloco do ombro nunca alcança o tronco.
     * O direito segura o cabo do chicote ao longo do antebraço; as marcas 4 e 5 ficam na ponta do cabo.
     */
    private fun arm(side: Float, sh: Float, shZ: Float, el: Float) {
        push()
        translate(side * 7.9f, 12.0f, 0f)
        rotZ(side * shZ)
        rotX(sh)
        // ombro (cap) e braço
        cube(0f, 0f, 0f, 1.8f, 1.55f, 1.85f, JK)
        cube(0f, 1.6f, 0f, 1.55f, 0.08f, 1.6f, JK_L)
        cube(0f, -3.0f, 0f, 1.65f, 2.45f, 1.7f, JK)
        cube(0f, -3.0f, 1.72f, 0.06f, 2.3f, 0.03f, STITCH)
        cube(side * 1.62f, -3.0f, 0f, 0.05f, 2.2f, 0.06f, STITCH)
        // cotovelo
        cube(0f, -5.4f, 0f, 1.5f, 0.85f, 1.5f, JK)
        translate(0f, -5.4f, 0f)
        rotX(el)
        // antebraço com punho de couro escuro
        cube(0f, -2.3f, 0f, 1.55f, 2.35f, 1.6f, JK)
        cube(0f, -2.3f, 1.62f, 1.1f, 1.3f, 0.05f, JK_L)
        cube(0f, -4.75f, 0f, 1.68f, 0.3f, 1.72f, JK_D)
        translate(0f, -5.0f, 0f)
        // mão fechada
        cube(0f, -1.4f, 0.05f, 1.45f, 1.2f, 1.5f, SKIN)
        cube(0f, -1.3f, 1.55f, 1.2f, 1.0f, 0.06f, SKIN_L)
        cube(0f, -0.9f, 1.62f, 1.1f, 0.05f, 0.03f, SKIN_DD)
        cube(0f, -1.5f, 1.62f, 1.1f, 0.05f, 0.03f, SKIN_DD)
        cube(side * 1.5f, -1.0f, 0.5f, 0.3f, 0.7f, 0.7f, SKIN_D)
        if (side < 0f) {
            // cabo de couro com anéis dourados, saindo da mão ao longo do antebraço
            cube(0f, -3.55f, 0.05f, 0.5f, 1.95f, 0.5f, BELT)
            cube(0f, -3.0f, 0.05f, 0.58f, 0.1f, 0.58f, GOLD)
            cube(0f, -4.2f, 0.05f, 0.58f, 0.1f, 0.58f, GOLD)
            cube(0f, -5.55f, 0.05f, 0.62f, 0.1f, 0.62f, GOLD)
            mark(0f, -5.7f, 0.05f, 4)
            mark(0f, -9.7f, 0.05f, 5)
        }
        pop()
    }

    /** Perna: calça, joelho, canela e bota alta de couro com fivelas. Ângulo positivo = para trás. */
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
        cube(0f, -3.4f, 2.42f, 1.6f, 0.14f, 0.06f, BOOT_D)
        cube(0f, -4.6f, 2.42f, 1.6f, 0.14f, 0.06f, BOOT_D)
        cube(0f, -4.0f, 2.45f, 0.3f, 0.3f, 0.05f, GOLD)
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
        vF: Float, satSw: Float, hatT: Float,
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
        translate(0f, 4f, 0f)
        rotY(chestTw)
        translate(0f, breath - 4f, 0f)
        torsoUp(vF)
        arm(-1f, rsh, rshZ, rel)
        arm(1f, lsh, lshZ, lel)
        head(turn, tilt, smirk, hatT)
        pop()
        pop()
    }
    // ═══ MODEL END

    // ───────────── marcas (pontos do modelo → mundo) ─────────────

    private fun mark(x: Float, y: Float, z: Float, id: Int) {
        val m = stack[top]
        mkWX[id] = m[0] * x + m[1] * y + m[2] * z + m[9]
        mkWY[id] = m[3] * x + m[4] * y + m[5] * z + m[10]
        mkWZ[id] = m[6] * x + m[7] * y + m[8] * z + m[11]
    }

    private var lastNs = 0L

    // ═══ LOGIC BEGIN
    // ───────────── estado externo (preenchido pelo Player antes de desenhar) ─────────────
    var rollP = -1f        // 0..1 durante a cambalhota; <0 = fora dela
    var inAir = false
    var airVz = 0f
    var airH = 0f
    var swing = 0          // 0 estalo lateral, 1 corte em diagonal, 2 chicotada por cima
    var alphaV = 1f

    // pendurado no chicote (balanço): preenchido pelo Player. hangDX/hangDY = ponto de ancoragem em pixels de tela, a partir dos pés
    var hangOn = false
    var hangGrow = 0f
    var hangDX = 0f
    var hangDY = 0f
    var hangVF = 0f        // velocidade do balanço para a frente do herói (-1..1)
    var hangVS = 0f        // velocidade do balanço para o lado (-1..1)

    /** Instante (0..1 da animação) em que a corda chega ao alcance máximo e estala (golpes 0 e 1). */
    const val T_HIT = 0.30f

    private val IMPACT = floatArrayOf(0.30f, 0.30f, 0.36f)

    fun impactAt(sw: Int): Float = IMPACT[if (sw in 0..2) sw else 0]

    private const val PIF = 3.14159265f
    private const val MS = 0.8f   // escala do modelo (altura parecida com a dos outros heróis)
    private const val NPAR = 16
    private const val NKEY = 6
    private const val ZK = 1.116f // o chão do jogo é achatado em 0.8 na tela; o motor 3D achata em sin(pitch)

    // parâmetros da pose: 0 lean, 1 hipTw, 2 chest, 3 rsh, 4 rshZ, 5 rel, 6 lsh, 7 lshZ, 8 lel,
    // 9 tilt, 10 hturn, 11 smirk, 12 crouch, 13 legB, 14 lunge, 15 rise
    private const val P_LEAN = 0
    private const val P_HIPTW = 1
    private const val P_CHEST = 2
    private const val P_RSH = 3
    private const val P_RSHZ = 4
    private const val P_REL = 5
    private const val P_LSH = 6
    private const val P_LSHZ = 7
    private const val P_LEL = 8
    private const val P_TILT = 9
    private const val P_HTURN = 10
    private const val P_SMIRK = 11
    private const val P_CROUCH = 12
    private const val P_LEGB = 13
    private const val P_LUNGE = 14
    private const val P_RISE = 15

    // tempos dos keyframes de cada golpe (repouso, preparação, golpe, sustentação, relaxe, repouso)
    private val SW_T = floatArrayOf(
        0.00f, 0.12f, 0.21f, 0.33f, 0.62f, 1.00f,
        0.00f, 0.12f, 0.21f, 0.33f, 0.62f, 1.00f,
        0.00f, 0.15f, 0.28f, 0.38f, 0.64f, 1.00f
    )

    // curva entre keyframes: 0 suave, 1 acelera (golpe), 2 desacelera (sustentação), 3 linear
    private val SW_MD = intArrayOf(
        0, 1, 2, 0, 2, 0,
        0, 1, 2, 0, 2, 0,
        0, 1, 2, 0, 2, 0
    )

    private val SW_POSE = floatArrayOf(
        // ── golpe 0: estalo lateral (o braço vai atrás e a corda corta à frente) ──
        0.05f, 0.00f, 0.00f, -0.10f, 0.14f, -0.30f, -0.10f, 0.14f, -0.30f, 0.00f, 0.00f, 0.3f, 0.00f, 0.0f, 0.0f, 0f,
        -0.08f, -0.30f, -0.70f, 0.80f, 0.60f, -1.40f, -0.95f, 0.25f, -1.25f, 0.05f, 0.40f, 0.6f, 0.15f, 0.5f, -1.0f, 0f,
        0.24f, 0.00f, 0.10f, -1.52f, 0.08f, -0.12f, 0.25f, 0.50f, -1.30f, -0.05f, -0.10f, 1.0f, 0.32f, 1.0f, 3.2f, 0.3f,
        0.30f, 0.05f, 0.18f, -1.64f, 0.05f, -0.06f, 0.35f, 0.55f, -1.20f, -0.06f, -0.16f, 1.0f, 0.36f, 1.0f, 4.0f, 0f,
        0.20f, 0.03f, 0.08f, -1.30f, 0.10f, -0.40f, 0.00f, 0.30f, -0.90f, 0.00f, -0.05f, 0.8f, 0.20f, 0.8f, 2.4f, 0f,
        0.05f, 0.00f, 0.00f, -0.10f, 0.14f, -0.30f, -0.10f, 0.14f, -0.30f, 0.00f, 0.00f, 0.3f, 0.00f, 0.0f, 0.0f, 0f,
        // ── golpe 1: corte em diagonal (do alto à direita para baixo) ──
        0.05f, 0.00f, 0.00f, -0.10f, 0.14f, -0.30f, -0.10f, 0.14f, -0.30f, 0.00f, 0.00f, 0.3f, 0.00f, 0.0f, 0.0f, 0f,
        -0.12f, -0.20f, -0.50f, -2.55f, 0.50f, -1.25f, -0.40f, 0.50f, -0.80f, -0.10f, 0.30f, 0.6f, 0.05f, 0.4f, -0.6f, 0.3f,
        0.36f, 0.00f, 0.12f, -1.25f, 0.10f, -0.15f, 0.35f, 0.50f, -1.00f, 0.10f, -0.05f, 1.0f, 0.40f, 1.0f, 3.0f, 0f,
        0.44f, 0.05f, 0.20f, -1.00f, 0.00f, -0.10f, 0.45f, 0.55f, -0.90f, 0.12f, -0.10f, 1.0f, 0.50f, 1.0f, 3.8f, 0f,
        0.30f, 0.03f, 0.10f, -0.70f, 0.10f, -0.50f, 0.10f, 0.30f, -0.70f, 0.00f, 0.00f, 0.8f, 0.30f, 0.8f, 2.2f, 0f,
        0.05f, 0.00f, 0.00f, -0.10f, 0.14f, -0.30f, -0.10f, 0.14f, -0.30f, 0.00f, 0.00f, 0.3f, 0.00f, 0.0f, 0.0f, 0f,
        // ── golpe 2: chicotada por cima (os dois braços sobem, o corpo desce junto com a corda) ──
        0.05f, 0.00f, 0.00f, -0.10f, 0.14f, -0.30f, -0.10f, 0.14f, -0.30f, 0.00f, 0.00f, 0.3f, 0.00f, 0.0f, 0.0f, 0f,
        -0.25f, 0.00f, -0.10f, -3.15f, 0.30f, -1.55f, -0.60f, 0.45f, -1.00f, -0.18f, 0.00f, 0.7f, 0.00f, 0.3f, -1.6f, 0.9f,
        0.50f, 0.00f, 0.05f, -1.45f, 0.08f, -0.15f, 0.35f, 0.45f, -0.70f, 0.12f, 0.00f, 1.0f, 0.45f, 1.0f, 3.6f, 0f,
        0.64f, 0.05f, 0.10f, -1.05f, 0.00f, -0.15f, 0.50f, 0.50f, -0.60f, 0.20f, 0.00f, 1.0f, 0.62f, 1.0f, 4.6f, 0f,
        0.42f, 0.03f, 0.05f, -0.80f, 0.10f, -0.40f, 0.20f, 0.35f, -0.70f, 0.05f, 0.00f, 0.8f, 0.40f, 0.8f, 3.0f, 0f,
        0.05f, 0.00f, 0.00f, -0.10f, 0.14f, -0.30f, -0.10f, 0.14f, -0.30f, 0.00f, 0.00f, 0.3f, 0.00f, 0.0f, 0.0f, 0f
    )

    // forma da corda por golpe: tempos, direção horizontal (rad, + = esquerda), elevação (rad), folga e comprimento
    private val RT_T = floatArrayOf(
        0.00f, 0.11f, 0.19f, 0.27f, 0.50f, 1.00f,
        0.00f, 0.11f, 0.19f, 0.27f, 0.50f, 1.00f,
        0.00f, 0.14f, 0.26f, 0.34f, 0.56f, 1.00f
    )
    private val RT_MD = intArrayOf(
        0, 0, 0, 0, 0, 0,
        0, 0, 0, 0, 0, 0,
        0, 0, 0, 0, 0, 0
    )
    private val RT_TH = floatArrayOf(
        -1.90f, -1.90f, 0.17f, 0.17f, 0.17f, 0.17f,
        -0.90f, -0.90f, 0.22f, 0.22f, 0.22f, 0.22f,
        0.15f, 0.15f, 0.15f, 0.15f, 0.15f, 0.15f
    )
    private val RT_PH = floatArrayOf(
        0.50f, 0.50f, -0.06f, -0.08f, -0.08f, -0.08f,
        1.30f, 1.30f, -0.10f, -0.10f, -0.10f, -0.10f,
        1.90f, 2.15f, -0.32f, -0.32f, -0.32f, -0.32f
    )
    private val RT_SL = floatArrayOf(
        1.00f, 1.00f, 0.00f, 0.00f, 0.00f, 0.06f,
        1.00f, 1.00f, 0.00f, 0.00f, 0.00f, 0.06f,
        1.00f, 1.00f, 0.00f, 0.00f, 0.00f, 0.06f
    )
    private val RT_LL = floatArrayOf(
        0.45f, 0.60f, 0.92f, 1.00f, 1.00f, 1.00f,
        0.45f, 0.60f, 0.95f, 1.00f, 1.00f, 1.00f,
        0.45f, 0.65f, 0.95f, 1.00f, 1.00f, 1.00f
    )

    // ───────────── estado de animação ─────────────
    private var yaw = 0f
    private var airW = 0f
    private var rollW = 0f
    private var blend = 0f
    private var clock = 0f
    private var prevPhase = 0f
    private var spd = 0f
    private var prevFlash = 0f
    private var hitKick = 0f
    private var landDip = 0f
    private var airTimer = 0f
    private var wasAir = false
    private var eyeOpen = 1f
    private var released = false
    private var hgW = 0f
    private var rollLand = false
    private var rollPuff = false
    private var crackT = 0f
    private var crackX = 0f
    private var crackY = 0f
    private var crackZ = 0f
    private var ringT = 0f
    private var ringX = 0f
    private var ringY = 0f
    private var ringBig = false
    private var kSeg = 0
    private var kU = 0f
    private val ps = FloatArray(NPAR)
    private val pt = FloatArray(NPAR)
    private val fp = FloatArray(NPAR)
    private val mkWX = FloatArray(8)
    private val mkWY = FloatArray(8)
    private val mkWZ = FloatArray(8)

    // corda do chicote: RN segmentos; v* = corda pendurada (simulação), a* = forma do golpe, r* = resultado
    private const val RN = 22
    private const val HANG_LEN = 12f
    private const val ROPE_G = 150f
    private const val ROPE_DAMP = 0.986f
    private const val LAG = 0.10f
    private val vX = FloatArray(RN + 1)
    private val vY = FloatArray(RN + 1)
    private val vZ = FloatArray(RN + 1)
    private val vpX = FloatArray(RN + 1)
    private val vpY = FloatArray(RN + 1)
    private val vpZ = FloatArray(RN + 1)
    private val aX = FloatArray(RN + 1)
    private val aY = FloatArray(RN + 1)
    private val aZ = FloatArray(RN + 1)
    private val rX = FloatArray(RN + 1)
    private val rY = FloatArray(RN + 1)
    private val rZ = FloatArray(RN + 1)
    private var ropeInit = false

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

    private fun rndf(): Float = kotlin.random.Random.nextFloat()

    private fun cl(x: Float, a: Float, b: Float): Float {
        if (x < a) return a
        if (x > b) return b
        return x
    }

    private fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t

    private fun ease(t: Float): Float {
        val x = cl(t, 0f, 1f)
        return x * x * (3f - 2f * x)
    }

    private fun sx(wx: Float): Float = ox + wx * rs

    private fun sy(wy: Float, wz: Float): Float = oy + (wz * SINP - wy * COSP) * rs

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

    /** Localiza o segmento da tabela [tb] (NKEY tempos a partir de [base]) e deixa o ponto em kSeg/kU. */
    private fun seek(tb: FloatArray, md: IntArray, base: Int, t: Float) {
        var k = 0
        for (j in 1 until NKEY - 1) {
            if (t >= tb[base + j]) k = j
        }
        val t0 = tb[base + k]
        val t1 = tb[base + k + 1]
        val u = cl((t - t0) / (t1 - t0), 0f, 1f)
        val m = md[base + k]
        var e = ease(u)
        if (m == 1) e = u * u
        if (m == 2) e = 1f - (1f - u) * (1f - u)
        if (m == 3) e = u
        kSeg = k
        kU = e
    }

    private fun tabv(vals: FloatArray, base: Int): Float = lerp(vals[base + kSeg], vals[base + kSeg + 1], kU)

    /** Cubo esticado entre dois pontos do mundo (usado nos segmentos da corda). */
    private fun segCube(ax: Float, ay: Float, az: Float, bx: Float, by: Float, bz: Float, hw: Float, col: Int) {
        val dx = bx - ax
        val dy = by - ay
        val dz = bz - az
        val len = sqrt(dx * dx + dy * dy + dz * dz)
        if (len < 0.02f) return
        val zx = dx / len
        val zy = dy / len
        val zz = dz / len
        var upx = 0f
        var upy = 1f
        if (abs(zy) > 0.92f) {
            upx = 1f
            upy = 0f
        }
        var xx = upy * zz
        var xy = -upx * zz
        var xz = upx * zy - upy * zx
        val xl = sqrt(xx * xx + xy * xy + xz * xz)
        xx = xx / xl
        xy = xy / xl
        xz = xz / xl
        val yx = zy * xz - zz * xy
        val yy = zz * xx - zx * xz
        val yz = zx * xy - zy * xx
        push()
        val m = stack[top]
        m[0] = xx
        m[3] = xy
        m[6] = xz
        m[1] = yx
        m[4] = yy
        m[7] = yz
        m[2] = zx
        m[5] = zy
        m[8] = zz
        m[9] = (ax + bx) * 0.5f
        m[10] = (ay + by) * 0.5f
        m[11] = (az + bz) * 0.5f
        cube(0f, 0f, 0f, hw, hw, len * 0.5f + hw * 0.4f, col)
        pop()
    }

    /** Quanto o corpo está encolhido na cambalhota (0..1): fecha rápido, segura e abre no fim. */
    private fun rollTuck(q: Float): Float = ease((q - 0.03f) / 0.17f) * (1f - ease((q - 0.80f) / 0.18f))

    /** Fração do giro completo: começa devagar (impulso), acelera no meio e desacelera ao levantar. */
    private fun rollSpin(q: Float): Float {
        val u = cl((q - 0.10f) / 0.70f, 0f, 1f)
        return 0.35f * u + 0.65f * ease(u)
    }

    /** Corda esticada da mão até o ponto de ancoragem no céu (sobe em 0.16s com uma onda), com leve folga no meio. */
    private fun hangRope() {
        val hx = mkWX[4]
        val hy = mkWY[4]
        val hz = mkWZ[4]
        val ax = hangDX / rs
        val ay = (hz * SINP - hangDY / rs) / COSP
        val g = ease(hangGrow)
        val tx = hx + (ax - hx) * g
        val ty = hy + (ay - hy) * g
        for (i in 0..RN) {
            val u = i.toFloat() / RN.toFloat()
            val bell = sin(u * PIF)
            val wob = (1f - g) * 3.5f * sin(u * 9f - clock * 34f) * bell
            rX[i] = hx + (tx - hx) * u + wob
            rY[i] = hy + (ty - hy) * u - 0.35f * bell * (0.4f + 0.6f * (1f - g))
            rZ[i] = hz
        }
    }

    /** Altura (em unidades do modelo) da perna esticada: usada para a pelve acompanhar o chão em qualquer postura. */
    private fun legExt(a: Float, kn: Float): Float = 7.6f * cos(a) + 5.9f * cos(a + kn)

    /**
     * Corda: pendurada (simulação com inércia, gruda no cabo) ou em golpe (curva guiada por tabelas, com a ponta
     * atrasada em relação ao cabo para formar a onda do estalo). Os dois se misturam do cabo para a ponta.
     */
    private fun ropeUpdate(dt: Float, t: Float, sw: Int, reachU: Float) {
        val hx = mkWX[4]
        val hy = mkWY[4]
        val hz = mkWZ[4]
        var d0x = mkWX[5] - hx
        var d0y = mkWY[5] - hy
        var d0z = mkWZ[5] - hz
        val d0l = sqrt(d0x * d0x + d0y * d0y + d0z * d0z)
        if (d0l > 0.0001f) {
            d0x = d0x / d0l
            d0y = d0y / d0l
            d0z = d0z / d0l
        } else {
            d0x = 0f
            d0y = -1f
            d0z = 0f
        }
        val dsH = HANG_LEN / RN
        val gdt = cl(dt, 0.004f, 0.033f)
        if (!ropeInit) {
            ropeInit = true
            for (i in 0..RN) {
                vX[i] = hx
                vY[i] = hy - i * dsH
                vZ[i] = hz
                vpX[i] = vX[i]
                vpY[i] = vY[i]
                vpZ[i] = vZ[i]
            }
        }
        // corda pendurada: verlet com gravidade, comprimento fixo e o primeiro elo na direção do cabo
        for (sub in 0..1) {
            val h = gdt * 0.5f
            vX[0] = hx
            vY[0] = hy
            vZ[0] = hz
            vpX[0] = hx
            vpY[0] = hy
            vpZ[0] = hz
            for (i in 1..RN) {
                val nx = vX[i] + (vX[i] - vpX[i]) * ROPE_DAMP
                val ny = vY[i] + (vY[i] - vpY[i]) * ROPE_DAMP - ROPE_G * h * h
                val nz = vZ[i] + (vZ[i] - vpZ[i]) * ROPE_DAMP
                vpX[i] = vX[i]
                vpY[i] = vY[i]
                vpZ[i] = vZ[i]
                vX[i] = nx
                vY[i] = ny
                vZ[i] = nz
            }
            for (q in 0..5) {
                vX[1] = hx + d0x * dsH
                vY[1] = hy + d0y * dsH
                vZ[1] = hz + d0z * dsH
                for (i in 1 until RN) {
                    val ex = vX[i + 1] - vX[i]
                    val ey = vY[i + 1] - vY[i]
                    val ez = vZ[i + 1] - vZ[i]
                    val el = sqrt(ex * ex + ey * ey + ez * ez)
                    if (el > 0.0001f) {
                        val df = (el - dsH) / el * 0.5f
                        vX[i] += ex * df
                        vY[i] += ey * df
                        vZ[i] += ez * df
                        vX[i + 1] -= ex * df
                        vY[i + 1] -= ey * df
                        vZ[i + 1] -= ez * df
                    }
                }
                for (i in 2..RN) {
                    if (vY[i] < 0.4f) vY[i] = 0.4f
                }
            }
        }
        // direção da frente e da esquerda do herói no chão
        val fwx = sin(yaw)
        val fwz = cos(yaw)
        val lfx = cos(yaw)
        val lfz = -sin(yaw)
        val base = sw * NKEY
        aX[0] = hx
        aY[0] = hy
        aZ[0] = hz
        if (t >= 0f) {
            seek(RT_T, RT_MD, base, t)
            val ds = tabv(RT_LL, base) * reachU / RN
            var cx = hx
            var cy = hy
            var cz = hz
            for (i in 1..RN) {
                val u = i.toFloat() / RN.toFloat()
                val tau = cl(t - LAG * u, 0f, 1f)
                seek(RT_T, RT_MD, base, tau)
                val th = tabv(RT_TH, base)
                val ph = tabv(RT_PH, base)
                val sl = tabv(RT_SL, base)
                val ph2 = ph - sl * (0.25f + 1.45f * u)
                val cp = cos(ph2)
                val rip = 0.03f + 0.10f * sl
                val wv = rip * sin(u * 11f - clock * 38f)
                var tx = fwx * cp * cos(th) + lfx * (cp * sin(th) + wv)
                var ty = sin(ph2) + rip * 0.6f * sin(u * 9f - clock * 31f)
                var tz = fwz * cp * cos(th) + lfz * (cp * sin(th) + wv)
                if (i <= 3) {
                    val bb = i.toFloat() / 4f
                    tx = lerp(d0x, tx, bb)
                    ty = lerp(d0y, ty, bb)
                    tz = lerp(d0z, tz, bb)
                }
                val tl = sqrt(tx * tx + ty * ty + tz * tz)
                cx += tx / tl * ds
                cy += ty / tl * ds
                cz += tz / tl * ds * ZK
                if (cy < 0.4f) cy = 0.4f
                aX[i] = cx
                aY[i] = cy
                aZ[i] = cz
            }
        }
        for (i in 0..RN) {
            var w = 0f
            if (t >= 0f) {
                val u = i.toFloat() / RN.toFloat()
                w = ease((t - 0.05f * u) / 0.10f) * (1f - ease((t - 0.60f - 0.12f * u) / 0.28f))
            }
            rX[i] = lerp(vX[i], aX[i], w)
            rY[i] = lerp(vY[i], aY[i], w)
            rZ[i] = lerp(vZ[i], aZ[i], w)
        }
    }

    /** Atualiza a pose, monta o modelo (corpo + corda) no buffer de cubos e solta os efeitos. */
    private fun animate(
        dt: Float, first: Boolean, s: Float, fx: Float, fy: Float, phase: Float,
        moving: Boolean, faceX: Float, faceY: Float, attack: Float
    ) {
        clock += dt

        // direção suave
        if (abs(faceX) + abs(faceY) > 0.01f) {
            val target = atan2(faceX, faceY)
            if (first) {
                yaw = target
            } else {
                var d = target - yaw
                while (d > PIF) d -= 2f * PIF
                while (d < -PIF) d += 2f * PIF
                yaw += d * min(1f, dt * 16f)
            }
        }
        blend += ((if (moving) 1f else 0f) - blend) * min(1f, dt * 12f)
        val dPh = abs(phase - prevPhase)
        prevPhase = phase
        val spdTgt = if (first) 0f else cl(dPh / dt / 9.6f, 0f, 1.6f)
        spd += (spdTgt - spd) * min(1f, dt * 10f)
        val spN = min(spd, 1f)

        val t = if (attack > 0.001f) 1f - attack else -1f
        val attacking = t >= 0f
        val sw = if (swing in 0..2) swing else 0
        val rolling = rollP >= 0f
        val airCand = inAir && !rolling && (airH > 8f || airVz > 60f)
        airTimer = if (airCand) airTimer + dt else 0f
        val airT = if (airTimer > 0.04f) 1f else 0f
        val rollTgt = if (rolling) rollTuck(rollP) else 0f
        val hanging = hangOn && !rolling
        hgW = if (first) (if (hanging) 1f else 0f) else hgW + ((if (hanging) 1f else 0f) - hgW) * min(1f, dt * 12f)
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
        val vzN = cl(airVz / 340f, -1.2f, 1f)
        val still = 1f - max(rollW, airW)
        val mvs = still * blend

        // ── alvos da pose (repouso, caminhada, ar, cambalhota) ──
        var lean = 0.05f + 0.12f * spN
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

        // no ar: braços abertos
        rshZ = lerp(rshZ, 1.1f, airW)
        lshZ = lerp(lshZ, 1.1f, airW)
        rsh = lerp(rsh, -0.5f, airW)
        lsh = lerp(lsh, -0.35f, airW)
        rel = lerp(rel, -0.3f, airW)
        lel = lerp(lel, -0.2f, airW)
        smirk = max(smirk, 0.6f * airW)

        // cambalhota para a frente, encolhido e protegendo a cabeça
        if (rolling) {
            lean = lerp(lean, 0.62f, rollW)
            crouch = lerp(crouch, 0.8f, rollW)
            rsh = lerp(rsh, -1.35f, rollW)
            lsh = lerp(lsh, -1.35f, rollW)
            rshZ = lerp(rshZ, 0.3f, rollW)
            lshZ = lerp(lshZ, 0.3f, rollW)
            rel = lerp(rel, -2.25f, rollW)
            lel = lerp(lel, -2.25f, rollW)
            tilt = lerp(tilt, 0.75f, rollW)
            smirk = max(smirk, 0.8f * rollW)
            // levantando: o corpo ainda agachado, braços abrem para equilibrar e a cabeça volta
            if (rollP > 0.78f) {
                val rf = sin(PIF * cl((rollP - 0.78f) / 0.22f, 0f, 1f))
                crouch = max(crouch, 0.6f * (1f - ease((rollP - 0.78f) / 0.22f)))
                rshZ = lerp(rshZ, 0.75f, rf)
                lshZ = lerp(lshZ, 0.75f, rf)
                rsh = lerp(rsh, -0.5f, rf)
                lsh = lerp(lsh, -0.4f, rf)
                rel = lerp(rel, -0.5f, rf)
                lel = lerp(lel, -0.5f, rf)
                lean = lerp(lean, 0.3f, rf)
            }
        }

        // pendurado no chicote: braço direito esticado para a corda, o outro aberto, pernas soltas, corpo inclinado pelo balanço
        if (hgW > 0.001f) {
            rsh = lerp(rsh, -3.05f, hgW)
            rshZ = lerp(rshZ, 0.1f, hgW)
            rel = lerp(rel, -0.1f, hgW)
            lsh = lerp(lsh, -0.45f - 0.6f * hangVF, hgW)
            lshZ = lerp(lshZ, 0.85f + 0.3f * abs(hangVS), hgW)
            lel = lerp(lel, -0.6f, hgW)
            lean = lerp(lean, -0.1f - 0.3f * hangVF, hgW)
            tilt = lerp(tilt, -0.12f, hgW)
            chestTw = lerp(chestTw, 0f, hgW)
            smirk = max(smirk, hgW)
        }

        pt[P_LEAN] = lean
        pt[P_HIPTW] = 0f
        pt[P_CHEST] = chestTw
        pt[P_RSH] = rsh
        pt[P_RSHZ] = rshZ
        pt[P_REL] = rel
        pt[P_LSH] = lsh
        pt[P_LSHZ] = lshZ
        pt[P_LEL] = lel
        pt[P_TILT] = tilt
        pt[P_HTURN] = -chestTw * 0.6f
        pt[P_SMIRK] = smirk
        pt[P_CROUCH] = crouch
        pt[P_LEGB] = 0f
        pt[P_LUNGE] = 0f
        pt[P_RISE] = 0f
        if (first) {
            for (i in 0 until NPAR) ps[i] = pt[i]
        } else {
            val k = min(1f, dt * 18f)
            for (i in 0 until NPAR) ps[i] += (pt[i] - ps[i]) * k
        }

        // ── combo de chicote: keyframes de antecipação, golpe, sustentação e relaxe sobre a pose base ──
        var wS = 0f
        if (attacking && rollW < 0.5f && hgW < 0.5f) {
            wS = min(ease(t / 0.09f), 1f - ease((t - 0.62f) / 0.38f))
            seek(SW_T, SW_MD, sw * NKEY, t)
            val kb = (sw * NKEY + kSeg) * NPAR
            for (i in 0 until NPAR) {
                val sv = lerp(SW_POSE[kb + i], SW_POSE[kb + NPAR + i], kU)
                fp[i] = lerp(ps[i], sv, wS)
            }
        } else {
            for (i in 0 until NPAR) fp[i] = ps[i]
        }

        // ── pernas e altura do quadril (a pelve acompanha o chão em qualquer postura) ──
        val crouchT = max(fp[P_CROUCH], landDip * 0.5f)
        val lb = fp[P_LEGB]
        var aL = 0f
        var kL = 0f
        var fL = 0f
        var aR = 0f
        var kR = 0f
        var fR = 0f
        for (k in 0..1) {
            val sd = if (k == 0) 1f else -1f
            val pp = if (sd > 0f) phase else phase + PIF
            var a = sin(pp) * 0.8f * mvs - crouchT * 0.6f
            var kn = max(0f, -cos(pp)) * 1.1f * mvs + 0.1f + crouchT * 1.3f
            a = lerp(a, -0.5f + sd * 0.3f - 0.2f * max(0f, vzN), airW)
            kn = lerp(kn, 0.9f, airW)
            // cambalhota: joelhos junto ao peito
            a = lerp(a, -1.6f, rollW)
            kn = lerp(kn, 2.2f, rollW)
            // pendurado: pernas soltas, arrastadas para trás do balanço
            a = lerp(a, 0.3f + 0.5f * hangVF + sd * 0.12f, hgW)
            kn = lerp(kn, 0.55f + 0.35f * abs(hangVF), hgW)
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
        val bob = abs(sin(phase)) * 0.9f * mvs + sin(clock * 2.2f) * 0.12f * (1f - mvs)
        val hipGround = 1.35f + max(legExt(aL, kL), legExt(aR, kR)) + bob - hitKick * 0.4f
        val hipAir = 14.8f + bob - crouchT * 3.0f - hitKick * 0.4f
        val hip = lerp(lerp(hipGround, hipAir, airW), 8.8f, rollW)

        val spk = spN * (if (rolling) 0.4f else 1f)
        val vF = 0.05f + spk * (0.16f + 0.08f * abs(sin(phase * 2f))) + hitKick * 0.2f
        val satSw = 0.04f + spk * 0.12f * (0.5f + 0.5f * sin(phase * 2f + 0.5f)) + hitKick * 0.2f
        val hatT = 0.02f + spk * 0.05f * sin(phase * 2f) - airW * 0.1f + hitKick * 0.1f
        val breath = sin(clock * 2.2f) * 0.1f

        // ── monta o modelo ──
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
        translate(0f, fp[P_RISE], fp[P_LUNGE] - hitKick * 1.2f)
        if (rolling) {
            // cambalhota completa para a frente em torno do centro do corpo
            // (o corpo encolhido gira em volta do próprio centro e faz um pequeno arco, sem afundar no chão)
            translate(0f, 12f + rollW * (2.5f + sin(PIF * cl((rollP - 0.05f) / 0.8f, 0f, 1f)) * 3.5f), 0f)
            rotX(2f * PIF * rollSpin(rollP))
            translate(0f, -12f, 0f)
        }
        skeleton(
            hip, fp[P_HIPTW], fp[P_LEAN] - hitKick * 0.3f, fp[P_CHEST], breath,
            aR, kR, fR, aL, kL, fL,
            vF, satSw, hatT,
            fp[P_HTURN], fp[P_TILT] - fp[P_LEAN] * 0.5f, fp[P_SMIRK],
            fp[P_RSH], fp[P_RSHZ], fp[P_REL], fp[P_LSH], fp[P_LSHZ], fp[P_LEL]
        )

        // ── chicote: corda 3D em coordenadas do mundo (entra no mesmo z-buffer do corpo) ──
        top = 0
        identity()
        val reach = if (sw == 2) Player.EXPL_REACH_3 else Player.EXPL_REACH
        val reachU = max(24f, reach / rs - 8f)
        if (hangOn && hgW > 0.02f) {
            hangRope()
            ropeInit = false
        } else {
            ropeUpdate(dt, if (attacking && rollW < 0.5f && hgW < 0.5f) t else -1f, sw, reachU)
        }
        val tx0 = rX[RN]
        val ty0 = rY[RN]
        val tz0 = rZ[RN]

        // estalo na ponta
        val hit = IMPACT[sw]
        if (attacking && rollW < 0.5f && hgW < 0.5f) {
            if (t >= hit && !released) {
                released = true
                crackT = 0.16f
                crackX = tx0
                crackY = ty0
                crackZ = tz0
                ringT = 0.34f
                ringX = sx(tx0)
                ringY = sy(0.4f, tz0)
                ringBig = sw == 2
                for (i in 0..9) {
                    val ang = rndf() * 2f * PIF
                    val sp0 = (14f + rndf() * 38f) * rs
                    emitSpark(sx(tx0), sy(ty0, tz0), cos(ang) * sp0, sin(ang) * sp0, 0.32f, rs * 40f, if (i % 2 == 0) 0xFFFFFFFF.toInt() else 0xFFFFC23A.toInt(), 1.0f)
                }
                for (i in 0..(if (sw == 2) 9 else 4)) {
                    val ang = rndf() * 2f * PIF
                    val sp0 = (10f + rndf() * 26f) * rs
                    emitSpark(sx(tx0), sy(0.4f, tz0), cos(ang) * sp0, sin(ang) * sp0 * 0.5f - 8f * rs, 0.45f, rs * 30f, 0xFFE9D9B0.toInt(), 1.3f)
                }
            }
        } else {
            released = false
        }
        var heat = 0f
        if (crackT > 0f) {
            crackT -= dt
            heat = cl(crackT / 0.16f, 0f, 1f)
        }

        // cubos da corda: trançado alternando tons, ponta de fiapos clara, clarão quente no estalo
        for (i in 1..RN) {
            val u = i.toFloat() / RN.toFloat()
            var col = if (i % 2 == 0) WHIP else WHIP_L
            if (i > RN - 3) col = WHIP_T
            if (heat > 0f) col = V.mix(col, WHIP_HOT, heat * (0.25f + 0.75f * u))
            segCube(rX[i - 1], rY[i - 1], rZ[i - 1], rX[i], rY[i], rZ[i], 0.52f - 0.24f * u, col)
        }
        // garra do chicote presa no céu: anel de couro e brilho dourado pulsando
        if (hangOn && hgW > 0.02f && hangGrow > 0.9f) {
            val gl = 0.5f + 0.5f * sin(clock * 6f)
            val kx = rX[RN]
            val ky = rY[RN]
            val kz = rZ[RN]
            cube(kx, ky, kz, 1.2f, 0.5f, 1.2f, BELT)
            cube(kx, ky, kz, 1.3f, 0.15f, 1.3f, GOLD)
            val sz2 = 1.6f + 1.2f * gl
            cube(kx, ky + 0.9f, kz, sz2, 0.22f, 0.22f, WHIP_HOT, true)
            cube(kx, ky + 0.9f, kz, 0.22f, sz2, 0.22f, WHIP_HOT, true)
            cube(kx, ky + 0.9f, kz, 0.22f, 0.22f, sz2, WHIP_HOT, true)
        }
        // clarão em estrela (cubos emissivos, com oclusão correta)
        if (heat > 0f) {
            val q = 1f - heat
            val sz = 0.5f + 3.2f * q
            val th = 0.45f * heat
            val flashCol = 0xFFFFF2B0.toInt()
            cube(crackX, crackY, crackZ, sz, th, th, flashCol, true)
            cube(crackX, crackY, crackZ, th, sz, th, flashCol, true)
            cube(crackX, crackY, crackZ, th, th, sz, flashCol, true)
        }

        // ── poeira na largada da cambalhota ──
        if (rolling) {
            if (rollP > 0.05f && !rollPuff) {
                rollPuff = true
                for (i in 0..6) {
                    val ang = rndf() * 2f * PIF
                    val sp0 = (12f + rndf() * 26f) * rs
                    emitSpark(fx, fy - 2f * rs, cos(ang) * sp0, sin(ang) * sp0 * 0.5f - 6f * rs, 0.36f, rs * 30f, 0xFFE9D9B0.toInt(), 1.2f)
                }
            }
            if (rollP > 0.84f && !rollLand) {
                rollLand = true
                for (i in 0..8) {
                    val ang = rndf() * 2f * PIF
                    val sp0 = (14f + rndf() * 30f) * rs
                    emitSpark(fx, fy - 2f * rs, cos(ang) * sp0, sin(ang) * sp0 * 0.5f - 6f * rs, 0.42f, rs * 30f, 0xFFE9D9B0.toInt(), 1.4f)
                }
            }
        } else {
            rollPuff = false
            rollLand = false
        }

        // ── partículas ──
        for (i in 0 until NSP) {
            if (spL[i] <= 0f) continue
            spL[i] -= dt
            spX[i] += spVx[i] * dt
            spY[i] += spVy[i] * dt
            spVy[i] += spG[i] * dt
        }
        if (ringT > 0f) ringT -= dt
    }
    // ═══ LOGIC END

    // ───────────── desenho principal ─────────────

    fun draw(
        c: Canvas, p: Paint, fx: Float, fy: Float, s: Float, phase: Float,
        moving: Boolean, faceX: Float, faceY: Float, attack: Float
    ) {
        val now = System.nanoTime()
        val first = lastNs == 0L
        val dt = if (first) 0.016f else cl((now - lastNs) / 1_000_000_000f, 0.001f, 0.1f)
        lastNs = now

        animate(dt, first, s, fx, fy, phase, moving, faceX, faceY, attack)
        flushZ(c)

        // anel de poeira no chão no instante do estalo
        p.style = Paint.Style.FILL
        if (ringT > 0f) {
            val q = cl(1f - ringT / 0.34f, 0f, 1f)
            val rr = rs * (3f + (if (ringBig) 15f else 9f) * q)
            p.style = Paint.Style.STROKE
            p.strokeWidth = max(1.2f, rs * 0.7f * (1f - q))
            p.color = V.alpha(0xFFE9D9B0.toInt(), 0.7f * (1f - q))
            c.drawOval(ringX - rr, ringY - rr * 0.5f, ringX + rr, ringY + rr * 0.5f, p)
            p.style = Paint.Style.FILL
        }

        // partículas (faíscas e poeira)
        for (i in 0 until NSP) {
            if (spL[i] <= 0f) continue
            val a = cl(spL[i] / spM[i], 0f, 1f)
            val sz = max(1.2f, rs * spZ[i]) * (0.5f + a * 0.5f)
            p.color = (((a * 255f).toInt()) shl 24) or (spC[i] and 0x00FFFFFF)
            c.drawRect(spX[i] - sz, spY[i] - sz, spX[i] + sz, spY[i] + sz, p)
        }
    }
}
