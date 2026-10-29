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
 * Ichigo em prismas 3D (mesmo mini motor do Pikachu, agora com faces afuniladas e normais reais por face).
 * Forma Bankai (Tensa Zangetsu, da luta contra o Aizen) e forma Vasto Lorde, com poses e skills próprias.
 */
object Ichigo3D {
    private val V = VoxelArt

    private const val MAX = 2600
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

    // ───────────── cubos e prismas ─────────────

    // cantos locais do prisma atual (ordem do cubo: bit0 = x, bit1 = y, bit2 = z) e normal de face
    private val lcX = FloatArray(8)
    private val lcY = FloatArray(8)
    private val lcZ = FloatArray(8)
    private var nlx = 0f
    private var nly = 0f
    private var nlz = 0f

    /** Sombra com leve tom frio: as áreas escuras ficam arroxeadas em vez de só pretas. */
    private fun lit(base: Int, shd: Float): Int {
        val cool = 1f - shd
        val r = (((base shr 16) and 255) * shd + 10f * cool).toInt().coerceIn(0, 255)
        val g = (((base shr 8) and 255) * shd + 8f * cool).toInt().coerceIn(0, 255)
        val b = ((base and 255) * shd + 26f * cool).toInt().coerceIn(0, 255)
        return (0xFF shl 24) or (r shl 16) or (g shl 8) or b
    }

    private fun crossN(ux: Float, uy: Float, uz: Float, vx: Float, vy: Float, vz: Float): Boolean {
        val x = uy * vz - uz * vy
        val y = uz * vx - ux * vz
        val z = ux * vy - uy * vx
        val l = sqrt(x * x + y * y + z * z)
        if (l < 1e-5f) return false
        nlx = x / l
        nly = y / l
        nlz = z / l
        return true
    }

    /** Normal local (unitária, para fora) da face de cantos a0..a3; false se a face colapsou numa ponta. */
    private fun faceNormal(a0: Int, a1: Int, a2: Int, a3: Int, ccx: Float, ccy: Float, ccz: Float): Boolean {
        var ok = crossN(
            lcX[a2] - lcX[a0], lcY[a2] - lcY[a0], lcZ[a2] - lcZ[a0],
            lcX[a3] - lcX[a1], lcY[a3] - lcY[a1], lcZ[a3] - lcZ[a1]
        )
        if (!ok) {
            ok = crossN(
                lcX[a1] - lcX[a0], lcY[a1] - lcY[a0], lcZ[a1] - lcZ[a0],
                lcX[a3] - lcX[a0], lcY[a3] - lcY[a0], lcZ[a3] - lcZ[a0]
            )
        }
        if (!ok) {
            ok = crossN(
                lcX[a2] - lcX[a1], lcY[a2] - lcY[a1], lcZ[a2] - lcZ[a1],
                lcX[a3] - lcX[a1], lcY[a3] - lcY[a1], lcZ[a3] - lcZ[a1]
            )
        }
        if (!ok) return false
        val mx = (lcX[a0] + lcX[a1] + lcX[a2] + lcX[a3]) * 0.25f - ccx
        val my = (lcY[a0] + lcY[a1] + lcY[a2] + lcY[a3]) * 0.25f - ccy
        val mz = (lcZ[a0] + lcZ[a1] + lcZ[a2] + lcZ[a3]) * 0.25f - ccz
        if (nlx * mx + nly * my + nlz * mz < 0f) {
            nlx = -nlx
            nly = -nly
            nlz = -nlz
        }
        return true
    }

    private fun cube(
        cx: Float, cy: Float, cz: Float, hx: Float, hy: Float, hz: Float,
        color: Int, emissive: Boolean = false
    ) {
        prism(cx, cy, cz, hx, hy, hz, 1f, 1f, 0f, 0f, color, emissive)
    }

    /**
     * Prisma afunilado: a base (-Y) tem meia-largura hx/hz; o topo (+Y) é a base vezes tx/tz e deslocado em offX/offZ.
     * Com tx/tz pequenos vira espinho, chifre ou lâmina; com tx/tz grandes a ponta fica embaixo (garra, trapo).
     */
    private fun prism(
        cx: Float, cy: Float, cz: Float, hx: Float, hy: Float, hz: Float,
        tx: Float, tz: Float, offX: Float, offZ: Float,
        color: Int, emissive: Boolean = false
    ) {
        if (n >= MAX) return
        val m = stack[top]
        val base = V.tint(color)
        val o = n * 8
        var ccx = 0f
        var ccy = 0f
        var ccz = 0f
        for (i in 0..7) {
            val sxn = if ((i and 1) == 0) -1f else 1f
            val syn = if (((i shr 1) and 1) == 0) -1f else 1f
            val szn = if (((i shr 2) and 1) == 0) -1f else 1f
            val x = if (syn > 0f) cx + offX + sxn * hx * tx else cx + sxn * hx
            val y = if (syn > 0f) cy + hy else cy - hy
            val z = if (syn > 0f) cz + offZ + szn * hz * tz else cz + szn * hz
            lcX[i] = x
            lcY[i] = y
            lcZ[i] = z
            ccx += x
            ccy += y
            ccz += z
            val wx = m[0] * x + m[1] * y + m[2] * z + m[9]
            val wy = m[3] * x + m[4] * y + m[5] * z + m[10]
            val wz = m[6] * x + m[7] * y + m[8] * z + m[11]
            scrX[o + i] = ox + wx * rs
            scrY[o + i] = oy + (wz * SINP - wy * COSP) * rs
            scrD[o + i] = wy * SINP + wz * COSP + depthBias
            scrH[o + i] = wy
        }
        ccx *= 0.125f
        ccy *= 0.125f
        ccz *= 0.125f
        for (f in 0..5) {
            val idx = n * 6 + f
            faceEm[idx] = emissive
            faceCol[idx] = 0
            val b = f * 4
            if (!faceNormal(CORN[b], CORN[b + 1], CORN[b + 2], CORN[b + 3], ccx, ccy, ccz)) continue
            val nx = m[0] * nlx + m[1] * nly + m[2] * nlz
            val ny = m[3] * nlx + m[4] * nly + m[5] * nlz
            val nz = m[6] * nlx + m[7] * nly + m[8] * nlz
            if (ny * SINP + nz * COSP <= 0.001f) continue
            if (emissive) {
                faceCol[idx] = base
            } else {
                val nn = sqrt(nx * nx + ny * ny + nz * nz).coerceAtLeast(0.0001f)
                // normal "de corpo": só o giro do herói, ignora o balanço dos membros
                val lbx = nlx * lYc + nlz * lYs
                val lbz = -nlx * lYs + nlz * lYc
                val dl = 0.7f * (lbx * NLX + nly * NLY + lbz * NLZ) + 0.3f * ((nx * NLX + ny * NLY + nz * NLZ) / nn)
                val shd = 0.62f + 0.34f * max(0f, dl) + 0.10f * (ny / nn * 0.5f + 0.5f)
                faceCol[idx] = lit(base, shd)
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

    // ───────────── paleta ─────────────
    private val SKIN = 0xFFF1C27D.toInt()
    private val SKIN_D = 0xFFC98F55.toInt()
    private val BLK = 0xFF17141E.toInt()
    private val BLK_L = 0xFF2E2938.toInt()
    private val BLK_D = 0xFF0A080E.toInt()
    private val HAIR = 0xFFFF8A1F.toInt()
    private val HAIR_L = 0xFFFFB347.toInt()
    private val HAIR_D = 0xFFD2600A.toInt()
    private val RED = 0xFFC8102E.toInt()
    private val RED_L = 0xFFFF4B5C.toInt()
    private val RED_D = 0xFF720A1B.toInt()
    private val BONE = 0xFFF2EFE8.toInt()
    private val BONE_L = 0xFFFFFFFF.toInt()
    private val BONE_D = 0xFFC4BEB2.toInt()
    private val BONE_S = 0xFF8F889C.toInt()
    private val WHITE = 0xFFFFFFFF.toInt()
    private val GOLD = 0xFFFFC21A.toInt()
    private val TABI = 0xFFE9E4D8.toInt()
    private val CHAIN = 0xFF9AA0A8.toInt()
    private val BROWN = 0xFF5A3418.toInt()
    private val EDGE = 0xFFFF2A3F.toInt()
    private val EDGE_L = 0xFFFFD0D6.toInt()
    private val DARKFX = 0xFF1A0610.toInt()

    // ───────────── skills ─────────────
    // Bankai: 1 Getsuga Tenshou, 2 Shunpo, 3 Redemoinho Getsuga, 4 Vasto Lorde (transformação)
    // Vasto Lorde: 1 Cero, 2 Sonído (garras), 3 Grito Hollow, 4 Mugetsu (final, encerra a forma)
    val DUR_N = floatArrayOf(0f, 0.70f, 0.55f, 0.95f, 1.70f)
    val REL_N = floatArrayOf(0f, 0.46f, 0.30f, 0.42f, 0.58f)
    val DUR_H = floatArrayOf(0f, 0.85f, 0.60f, 0.95f, 1.50f)
    val REL_H = floatArrayOf(0f, 0.52f, 0.30f, 0.46f, 0.60f)

    fun dur(h: Boolean, k: Int): Float = if (h) DUR_H[k.coerceIn(1, 4)] else DUR_N[k.coerceIn(1, 4)]
    fun rel(h: Boolean, k: Int): Float = if (h) REL_H[k.coerceIn(1, 4)] else REL_N[k.coerceIn(1, 4)]

    /** Ponto (0..1) de cada golpe do combo em que a lâmina (ou garra) acerta. */
    private val IMPACT = floatArrayOf(0.50f, 0.50f, 0.56f)
    fun impactAt(sw: Int): Float = IMPACT[sw.coerceIn(0, 2)]

    // ───────────── estado externo (preenchido pelo Player antes de desenhar) ─────────────
    var rollP = -1f
    var inAir = false
    var airVz = 0f
    var airH = 0f
    var skillK = 0
    var skillP = -1f
    var skillHol = false
    var hollowOn = false
    var swing = 0
    var alphaV = 1f

    // ───────────── estado de animação ─────────────
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
    private var skRel = false
    private var flashV = 0f
    private var auraT = 0f
    private var prevHol = false
    private var hdS = 0f
    private var mouthS = 0f
    private var auraS = 0f
    private var glowS = 0f
    private var chargeS = 0f
    private var colA = 0f
    private var spinA = 0f
    private var kitL = 0f
    private const val NP = 12
    private val ps = FloatArray(NP)
    private val pt = FloatArray(NP)
    private val mkX = FloatArray(8)
    private val mkY = FloatArray(8)

    // fita do golpe: últimas posições da ponta e da base da lâmina
    private const val TR = 9
    private val trTX = FloatArray(TR)
    private val trTY = FloatArray(TR)
    private val trBX = FloatArray(TR)
    private val trBY = FloatArray(TR)
    private var trN = 0
    private var trHead = 0

    private const val NSP = 72
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

    private fun mark(x: Float, y: Float, z: Float, id: Int) {
        val m = stack[top]
        val wx = m[0] * x + m[1] * y + m[2] * z + m[9]
        val wy = m[3] * x + m[4] * y + m[5] * z + m[10]
        val wz = m[6] * x + m[7] * y + m[8] * z + m[11]
        mkX[id] = ox + wx * rs
        mkY[id] = oy + (wz * SINP - wy * COSP) * rs
    }

    // ───────────── poses ─────────────
    // índices: 0 inclinação, 1 giro do tronco, 2-5 braço da espada (ombro, abertura, cotovelo, pulso),
    // 6-8 outro braço (ombro, abertura, cotovelo), 9 agachar, 10 giro completo, 11 altura do salto

    private fun kf(
        a0: Float, a1: Float, a2: Float, a3: Float, a4: Float, a5: Float,
        a6: Float, a7: Float, a8: Float, a9: Float, a10: Float, a11: Float
    ): FloatArray = floatArrayOf(a0, a1, a2, a3, a4, a5, a6, a7, a8, a9, a10, a11)

    private val IDLE_N = kf(0.06f, 0f, -0.35f, 0.18f, -0.75f, 0.10f, -0.10f, 0.22f, -0.45f, 0f, 0f, 0f)
    private val IDLE_H = kf(0.20f, 0f, -0.10f, 0.32f, -0.45f, 0f, -0.10f, 0.32f, -0.45f, 0.15f, 0f, 0f)

    // combo de 3 golpes: [preparo, impacto, continuação]
    private val ATK_N = arrayOf(
        arrayOf( // corte diagonal de cima para baixo
            kf(-0.10f, 0.75f, -2.50f, 0.30f, -0.40f, -0.10f, -0.60f, 0.55f, -0.80f, 0.10f, 0f, 0f),
            kf(0.40f, -0.65f, -1.00f, 0.12f, -0.55f, 0.15f, -0.35f, 0.65f, -0.60f, 0.25f, 0f, 0f),
            kf(0.30f, -0.95f, -0.55f, 0.30f, -0.50f, 0.20f, -0.30f, 0.70f, -0.60f, 0.20f, 0f, 0f)
        ),
        arrayOf( // corte horizontal de volta
            kf(0.10f, -0.95f, -1.35f, 0.95f, -0.35f, -0.10f, -0.20f, 0.50f, -0.50f, 0.10f, 0f, 0f),
            kf(0.20f, 0.95f, -1.50f, 0.15f, -0.20f, -0.05f, -0.20f, 0.60f, -0.50f, 0.20f, 0f, 0f),
            kf(0.20f, 1.20f, -1.30f, 0.30f, -0.30f, 0f, -0.20f, 0.60f, -0.50f, 0.15f, 0f, 0f)
        ),
        arrayOf( // golpe pesado por cima, com salto
            kf(-0.30f, 0f, -2.70f, 0.20f, -0.40f, -0.10f, -2.40f, 0.30f, -0.40f, 0f, 0f, 1.2f),
            kf(0.55f, 0f, -1.00f, 0.10f, -0.60f, 0.20f, -0.90f, 0.30f, -0.80f, 0.50f, 0f, 0f),
            kf(0.45f, 0f, -0.80f, 0.15f, -0.60f, 0.30f, -0.80f, 0.30f, -0.80f, 0.45f, 0f, 0f)
        )
    )
    private val ATK_H = arrayOf(
        arrayOf( // garra direita
            kf(0.10f, 0.90f, -2.20f, 0.80f, -0.30f, 0f, 0.10f, 0.40f, -0.40f, 0.20f, 0f, 0f),
            kf(0.50f, -0.80f, -1.35f, 0.15f, -0.25f, 0f, -0.50f, 0.50f, -0.50f, 0.35f, 0f, 0f),
            kf(0.40f, -1.10f, -0.90f, 0.20f, -0.30f, 0f, -0.40f, 0.50f, -0.50f, 0.25f, 0f, 0f)
        ),
        arrayOf( // garra esquerda
            kf(0.10f, -0.90f, 0.10f, 0.40f, -0.40f, 0f, -2.20f, 0.80f, -0.30f, 0.20f, 0f, 0f),
            kf(0.50f, 0.80f, -0.50f, 0.50f, -0.50f, 0f, -1.35f, 0.15f, -0.25f, 0.35f, 0f, 0f),
            kf(0.40f, 1.10f, -0.40f, 0.50f, -0.50f, 0f, -0.90f, 0.20f, -0.30f, 0.25f, 0f, 0f)
        ),
        arrayOf( // bote com as duas garras
            kf(-0.20f, 0f, -2.80f, 0.90f, -0.30f, 0f, -2.80f, 0.90f, -0.30f, 0.10f, 0f, 1.4f),
            kf(0.80f, 0f, -1.20f, 0.20f, -0.30f, 0f, -1.20f, 0.20f, -0.30f, 0.60f, 0f, 0f),
            kf(0.60f, 0f, -0.90f, 0.30f, -0.30f, 0f, -0.90f, 0.30f, -0.30f, 0.40f, 0f, 0f)
        )
    )
    private val SK_N = arrayOf(
        arrayOf( // Getsuga Tenshou
            kf(-0.30f, 0.50f, -2.90f, 0.50f, -0.30f, -0.10f, -2.00f, 0.70f, -0.40f, 0.15f, 0f, 0.3f),
            kf(0.55f, -0.30f, -1.10f, 0.10f, -0.45f, 0.10f, -0.50f, 0.60f, -0.60f, 0.35f, 0f, 0f),
            kf(0.40f, -0.40f, -0.80f, 0.15f, -0.50f, 0.20f, -0.40f, 0.50f, -0.50f, 0.25f, 0f, 0f)
        ),
        arrayOf( // Shunpo
            kf(0.75f, 0.40f, -0.50f, 0.50f, -0.50f, 0.30f, 0.40f, 0.60f, -0.50f, 0.90f, 0f, 0f),
            kf(1.00f, -0.30f, -1.55f, 0.10f, -0.10f, 0.10f, 0.50f, 0.80f, -0.30f, 0.50f, 0f, 0f),
            kf(0.35f, -0.60f, -0.90f, 0.20f, -0.50f, 0.20f, -0.20f, 0.50f, -0.40f, 0.20f, 0f, 0f)
        ),
        arrayOf( // Redemoinho Getsuga (o giro é somado à parte)
            kf(0.20f, 1.00f, -1.50f, 1.30f, -0.10f, 0f, -0.50f, 1.30f, -0.10f, 0.50f, 0f, 0f),
            kf(0.25f, -1.00f, -1.50f, 1.30f, -0.10f, 0f, -0.50f, 1.30f, -0.10f, 0.40f, 0f, 0f),
            kf(0.10f, -0.30f, -0.80f, 0.40f, -0.50f, 0.10f, -0.30f, 0.50f, -0.50f, 0.20f, 0f, 0f)
        ),
        arrayOf( // transformação em Vasto Lorde
            kf(0.70f, 0f, -0.40f, 0.50f, -0.50f, 0.30f, -0.30f, 0.50f, -0.50f, 1.00f, 0f, 0f),
            kf(-0.45f, 0f, -0.90f, 1.20f, -0.50f, 0f, -0.90f, 1.20f, -0.50f, 0f, 0f, 0.8f),
            kf(0.20f, 0f, -0.30f, 0.60f, -0.40f, 0f, -0.30f, 0.60f, -0.40f, 0.20f, 0f, 0f)
        )
    )
    private val SK_H = arrayOf(
        arrayOf( // Cero
            kf(-0.20f, 0.20f, -1.60f, 0.35f, -0.20f, 0f, 0.10f, 0.40f, -0.40f, 0.25f, 0f, 0f),
            kf(0.35f, -0.10f, -1.55f, 0.05f, 0f, 0f, 0f, 0.40f, -0.40f, 0.35f, 0f, 0f),
            kf(0.25f, 0f, -1.20f, 0.10f, -0.10f, 0f, 0f, 0.40f, -0.40f, 0.25f, 0f, 0f)
        ),
        arrayOf( // Sonído com garras
            kf(0.90f, 0.50f, -0.50f, 0.60f, -0.40f, 0f, 0.30f, 0.60f, -0.40f, 1.00f, 0f, 0f),
            kf(1.10f, -0.50f, -1.50f, 0.20f, -0.20f, 0f, -1.50f, 0.20f, -0.20f, 0.60f, 0f, 0f),
            kf(0.50f, -0.20f, -0.80f, 0.30f, -0.30f, 0f, -0.80f, 0.30f, -0.30f, 0.30f, 0f, 0f)
        ),
        arrayOf( // Grito Hollow
            kf(-0.45f, 0f, -0.50f, 1.50f, -0.20f, 0f, -0.50f, 1.50f, -0.20f, 0.20f, 0f, 0f),
            kf(-0.40f, 0f, -0.90f, 1.70f, -0.10f, 0f, -0.90f, 1.70f, -0.10f, 0f, 0f, 0.5f),
            kf(0.10f, 0f, -0.30f, 0.80f, -0.30f, 0f, -0.30f, 0.80f, -0.30f, 0.15f, 0f, 0f)
        ),
        arrayOf( // Mugetsu
            kf(-0.35f, 0f, -3.00f, 0.50f, -0.20f, 0f, -3.00f, 0.50f, -0.20f, 0f, 0f, 1.6f),
            kf(0.70f, 0f, -1.10f, 0.10f, -0.20f, 0f, -1.10f, 0.10f, -0.20f, 0.60f, 0f, 0f),
            kf(0.40f, 0f, -0.70f, 0.20f, -0.30f, 0f, -0.70f, 0.20f, -0.30f, 0.30f, 0f, 0f)
        )
    )

    private fun mixTo(dst: FloatArray, a: FloatArray, b: FloatArray, t: Float) {
        for (i in 0 until NP) dst[i] = a[i] + (b[i] - a[i]) * t
    }

    /** Preenche [pt] com a pose do instante [t] (0..1) de uma sequência preparo → impacto → continuação → repouso. */
    private fun seqPose(keys: Array<FloatArray>, idle: FloatArray, tw: Float, ti: Float, te: Float, t: Float) {
        if (t < tw) {
            mixTo(pt, idle, keys[0], ease(t / tw))
        } else if (t < ti) {
            mixTo(pt, keys[0], keys[1], ease((t - tw) / (ti - tw)))
        } else if (t < te) {
            mixTo(pt, keys[1], keys[2], ease((t - ti) / (te - ti)))
        } else {
            mixTo(pt, keys[2], idle, ease((t - te) / (1f - te)))
        }
    }

    // ───────────── partes do corpo ─────────────

    private var cw0 = 0f   // balanço do casaco (atrás)
    private var cw1 = 0f
    private var cw2 = 0f
    private var hsw = 0f   // balanço dos cabelos
    private var chn = 0f   // balanço da corrente
    private var glow = 0f  // brilho da espada / aura
    private var charge = 0f // energia acumulada (Cero, Getsuga)
    private var mouth = 0f
    private var hol = false

    /** Espinho de ponta fina: nasce em (x,y,z) e aponta para +Y depois de girar [rx] e [rz]. */
    private fun spike(x: Float, y: Float, z: Float, rx: Float, rz: Float, len: Float, w: Float, col: Int) {
        push()
            translate(x, y, z)
            rotZ(rz)
            rotX(rx)
            prism(0f, len * 0.5f, 0f, w, len * 0.5f, w, 0.12f, 0.12f, 0f, 0f, col)
        pop()
    }

    private fun leg(side: Float, a: Float, knee: Float, foot: Float, hipY: Float) {
        push()
            translate(side * 2.7f, hipY, 0f)
            rotX(a)
            prism(0f, -2.7f, 0f, 1.9f, 2.9f, 2.0f, 1.25f, 1.2f, 0f, 0f, BLK)
            prism(side * 1.2f, -2.4f, 0f, 0.5f, 2.5f, 1.9f, 1.0f, 1.0f, 0f, 0f, BLK_L)
            translate(0f, -5.6f, 0f)
            rotX(knee)
            prism(0f, -2.7f, 0f, 1.6f, 2.8f, 1.7f, 1.2f, 1.2f, 0f, 0f, BLK)
            cube(0f, -4.9f, 0f, 1.95f, 0.5f, 2.05f, if (hol) BONE_D else TABI)
            translate(0f, -5.4f, 0f)
            rotX(foot)
            if (hol) {
                cube(0f, 0.1f, 1.0f, 1.7f, 0.9f, 2.4f, BONE)
                for (i in -1..1) spike(i * 0.95f, -0.2f, 3.0f, 1.5708f, i * -0.12f, 1.6f, 0.4f, BLK_D)
                spike(0f, 0.3f, -1.4f, -1.5708f, 0f, 1.0f, 0.4f, BLK_D)
            } else {
                cube(0f, 0.2f, 1.2f, 1.75f, 0.95f, 2.6f, TABI)
                cube(0f, -0.95f, 1.3f, 1.85f, 0.3f, 3.0f, BLK_D)
                prism(0f, 0.3f, 3.7f, 1.2f, 0.6f, 0.6f, 0.7f, 0.6f, 0f, 0f, TABI)
                cube(0f, 0.9f, 0f, 1.9f, 0.18f, 1.9f, BLK_D)
            }
        pop()
    }

    /** Parte de baixo do corpo: cinto, abdômen e as pontas rasgadas do casaco. */
    private fun torsoLow(sk: Float) {
        // cinto e laço
        cube(0f, 0.8f, 0f, 4.9f, 1.0f, 3.1f, BLK_D)
        cube(0f, 0.8f, 3.15f, 1.2f, 0.9f, 0.3f, RED_D)
        cube(0f, 0.2f, 3.3f, 0.35f, 1.3f, 0.2f, RED)
        if (hol) {
            cube(0f, 3.9f, 0f, 3.5f, 2.3f, 2.3f, BONE)
            for (i in 0..2) cube(0f, 2.8f + i * 1.2f, 2.35f, 2.7f - i * 0.2f, 0.12f, 0.1f, BONE_S)
            cube(0f, 3.9f, 2.4f, 0.12f, 2.1f, 0.1f, BONE_S)
            // trapos negros no quadril
            for (sd in intArrayOf(-1, 1)) {
                push()
                    translate(sd * 4.6f, 0.4f, 0f)
                    rotZ(sd * 0.12f)
                    rotX(cw1 * 0.8f)
                    prism(0f, -3.5f, 0f, 0.35f, 3.5f, 1.6f, 1.0f, 2.2f, 0f, 0f, BLK)
                pop()
            }
        } else {
            cube(0f, 3.9f, 0f, 4.5f, 2.3f, 2.9f, BLK)
            cube(0f, 3.9f, 2.95f, 3.6f, 0.12f, 0.1f, BLK_L)
            cube(0f, 3.9f, -0.0f, 4.7f, 0.2f, 3.1f, BLK_L)
        }
        // casaco rasgado atrás: três tiras de cada lado, em segmentos
        if (!hol) {
            for (i in -2..2) {
                push()
                    translate(i * 1.9f, 1.0f, -2.9f)
                    rotX(cw0 + 0.06f * i * i)
                    rotZ(i * 0.05f)
                    val len = 4.6f + (2 - kotlin.math.abs(i)) * 0.7f
                    prism(0f, -len, -0.15f, 0.95f, len, 0.32f, 1.9f, 1f, 0f, 0f, if (i % 2 == 0) BLK else BLK_L)
                    cube(0f, -len * 2f + 0.2f, -0.1f, 1.05f, 0.18f, 0.36f, RED_D)
                    translate(0f, -len * 2f, 0f)
                    rotX(cw1 + 0.08f * i)
                    prism(0f, -2.4f, 0f, 0.1f, 2.4f, 0.28f, 8f, 1f, 0f, 0f, BLK_D)
                pop()
            }
            // abas laterais
            for (sd in intArrayOf(-1, 1)) {
                push()
                    translate(sd * 5.0f, 1.0f, 0.2f)
                    rotZ(sd * (0.10f + 0.12f * sk))
                    rotX(cw2 * 0.6f)
                    prism(0f, -4.4f, 0f, 0.3f, 4.4f, 2.0f, 1.0f, 1.5f, 0f, 0f, BLK)
                    cube(0f, -8.7f, 0f, 0.34f, 0.18f, 2.4f, RED_D)
                pop()
            }
            // faixa frontal
            push()
                translate(0f, 1.0f, 3.2f)
                rotX(-cw2 * 0.5f - 0.05f)
                prism(0f, -3.3f, 0f, 1.5f, 3.3f, 0.25f, 1.5f, 1f, 0f, 0f, BLK_L)
                cube(0f, -6.5f, 0f, 1.6f, 0.15f, 0.28f, RED_D)
            pop()
        }
    }

    /** Peito, ombros e pescoço. */
    private fun torsoUp(breath: Float) {
        if (hol) {
            prism(0f, 9.0f, 0f, 3.9f, 3.2f, 2.7f, 1.2f, 1.1f, 0f, 0f, BONE)
            for (i in 0..2) cube(0f, 7.6f + i * 1.45f, 2.75f, 3.1f - i * 0.15f, 0.16f, 0.1f, BONE_S)
            cube(0f, 9.4f, 2.75f, 0.14f, 3.0f, 0.1f, BONE_S)
            // buraco de Hollow no peito
            cube(0f, 8.6f, 2.8f, 1.55f, 1.55f, 0.1f, RED, true)
            cube(0f, 8.6f, 2.9f, 1.2f, 1.2f, 0.1f, BLK_D)
            cube(0f, 8.6f, 3.0f, 0.55f, 0.55f, 0.08f, RED_D)
            // espinhos nas costas
            for (i in 0..3) {
                push()
                    translate(0f, 5.4f + i * 2.2f, -2.7f)
                    rotX(-(1.0f + 0.1f * i) - hsw * 0.2f)
                    prism(0f, 1.1f, 0f, 0.7f, 1.1f, 0.5f, 0.2f, 0.2f, 0f, 0f, if (i % 2 == 0) BONE else BONE_D)
                pop()
            }
            cube(0f, 12.0f, 0f, 2.2f, 0.9f, 1.8f, BONE_D)
        } else {
            prism(0f, 9.1f, 0f, 4.5f, 3.3f, 3.0f, 1.12f, 1.0f, 0f, 0f, BLK)
            // decote em V mostrando o peito
            prism(0f, 9.5f, 2.95f, 0.9f, 3.0f, 0.25f, 2.4f, 1.0f, 0f, 0f, SKIN)
            cube(-1.1f, 9.6f, 3.12f, 0.12f, 1.4f, 0.06f, SKIN_D)
            cube(1.1f, 9.6f, 3.12f, 0.12f, 1.4f, 0.06f, SKIN_D)
            // lapelas
            for (sd in intArrayOf(-1, 1)) {
                push()
                    translate(sd * 2.2f, 9.6f, 3.05f)
                    rotZ(-sd * 0.32f)
                    prism(0f, 0f, 0f, 0.7f, 3.4f, 0.28f, 1.0f, 1.0f, 0f, 0f, BLK_L)
                    cube(sd * 0.55f, 0f, 0.1f, 0.12f, 3.2f, 0.1f, RED_D)
                pop()
            }
            // gola alta atrás
            cube(0f, 12.7f, -2.4f, 3.6f, 0.9f, 1.0f, BLK_L)
            cube(0f, 12.9f, -2.9f, 3.2f, 0.4f, 0.4f, RED_D)
            // cinto do peito (reiatsu)
            cube(0f, 5.9f, 0f, 4.6f, 0.4f, 3.1f, BLK_D)
        }
        cube(0f, 12.5f + breath * 0.3f, 0f, 1.9f, 0.9f, 1.9f, if (hol) BONE_D else SKIN_D)
    }

    /** Espada na mão, ponta para -Y: Tensa Zangetsu, negra, com fio vermelho e a corrente no pomo. */
    private fun sword() {
        val e = VoxelArt.mix(EDGE, EDGE_L, glow)
        // empunhadura e anéis vermelhos
        cube(0f, -1.1f, 0f, 0.52f, 2.4f, 0.52f, BLK_D)
        cube(0f, 0.1f, 0f, 0.64f, 0.28f, 0.64f, RED)
        cube(0f, -1.1f, 0f, 0.62f, 0.25f, 0.62f, RED_D)
        cube(0f, -2.3f, 0f, 0.64f, 0.28f, 0.64f, RED)
        // guarda: lua crescente negra
        cube(0f, -3.6f, 0f, 0.78f, 0.38f, 1.9f, BLK_L)
        prism(0f, -3.3f, 1.9f, 0.3f, 0.2f, 0.2f, 0.5f, 0.5f, 0f, 0.7f, BLK_L)
        prism(0f, -3.3f, -1.9f, 0.3f, 0.2f, 0.2f, 0.5f, 0.5f, 0f, -0.7f, BLK_L)
        cube(0f, -3.6f, 1.95f, 0.5f, 0.2f, 0.12f, RED)
        // pomo
        cube(0f, 1.1f, 0f, 0.7f, 0.5f, 0.7f, BLK_L)
        cube(0f, 1.0f, 0.72f, 0.3f, 0.3f, 0.06f, RED_L, true)
        // corrente presa ao pomo, balançando
        push()
            translate(0f, 1.6f, 0f)
            for (i in 0 until 7) {
                rotX(0.22f + chn * 0.5f + 0.06f * i)
                rotZ(0.10f * kotlin.math.sin(clock * 3f + i))
                cube(0f, 0.55f, 0f, if (i % 2 == 0) 0.22f else 0.12f, 0.55f, if (i % 2 == 0) 0.12f else 0.22f, CHAIN)
                translate(0f, 1.05f, 0f)
            }
            cube(0f, 0.6f, 0f, 0.5f, 0.6f, 0.12f, BLK_L)
            cube(0f, 1.4f, 0f, 0.14f, 0.5f, 0.1f, RED)
        pop()
        // lâmina negra em segmentos
        prism(0f, -6.3f, 0f, 0.32f, 2.5f, 0.98f, 1.0f, 1.0f, 0f, 0f, BLK)
        cube(0f, -6.3f, 1.0f, 0.12f, 2.4f, 0.07f, e, true)
        cube(0f, -6.3f, -1.0f, 0.12f, 2.4f, 0.07f, RED_D)
        cube(0f, -6.3f, 0f, 0.38f, 2.3f, 0.2f, BLK_L)
        prism(0f, -11.6f, 0f, 0.30f, 2.8f, 0.95f, 1.0f, 1.0f, 0f, 0f, BLK)
        cube(0f, -11.6f, 0.98f, 0.12f, 2.7f, 0.07f, e, true)
        cube(0f, -11.6f, -0.98f, 0.12f, 2.7f, 0.07f, RED_D)
        cube(0f, -11.6f, 0f, 0.36f, 2.6f, 0.2f, BLK_L)
        push()
            translate(0f, -14.4f, 0f)
            rotX(0.05f)
            prism(0f, -2.4f, 0f, 0.28f, 2.4f, 0.92f, 1.0f, 1.0f, 0f, 0f, BLK)
            cube(0f, -2.4f, 0.95f, 0.12f, 2.3f, 0.07f, e, true)
            cube(0f, -2.4f, -0.95f, 0.12f, 2.3f, 0.07f, RED_D)
            translate(0f, -4.8f, 0f)
            prism(0f, -1.5f, 0.15f, 0.04f, 1.5f, 0.1f, 9f, 9f, 0f, 0f, BLK)
            cube(0f, -1.5f, 0.5f, 0.08f, 1.2f, 0.06f, EDGE_L, true)
            mark(0f, -3.0f, 0.2f, 2)
        pop()
        mark(0f, -3.6f, 0f, 3)
        // brilho (Getsuga acumulando)
        if (glow > 0.45f) {
            cube(0f, -11f, 0f, 0.6f, 9f, 1.35f, VoxelArt.mix(RED_D, EDGE, glow), true)
        }
    }

    /** Garras do Vasto Lorde, apontando para -Y. */
    private fun claws(id: Int) {
        for (i in -1..1) {
            prism(i * 0.75f, -1.9f, 0.3f, 0.12f, 1.5f, 0.12f, 4.5f, 4.5f, 0f, 0f, BLK_D)
        }
        prism(-1.2f, -0.8f, 0.8f, 0.1f, 1.0f, 0.1f, 4f, 4f, 0f, 0f, BLK_D)
        mark(0f, -3.4f, 0.3f, id)
    }

    private fun arm(side: Float, sh: Float, spr: Float, el: Float, wr: Float, hasSword: Boolean, markId: Int) {
        push()
            translate(side * 6.6f, 11.3f, 0f)
            rotZ(side * spr)
            rotX(sh)
            if (hol) {
                prism(0f, 0.3f, 0f, 2.4f, 0.8f, 2.4f, 1.0f, 1.0f, 0f, 0f, BONE_D)
                prism(side * 0.2f, 1.1f, 0f, 0.5f, 0.8f, 0.5f, 0.2f, 0.2f, side * 0.4f, 0f, BONE)
                prism(0f, -2.7f, 0f, 1.35f, 2.8f, 1.35f, 1.15f, 1.15f, 0f, 0f, BONE)
                cube(0f, -5.2f, 0f, 1.5f, 0.3f, 1.5f, BONE_S)
                translate(0f, -5.4f, 0f)
                rotX(el)
                prism(0f, -2.9f, 0f, 1.2f, 3.0f, 1.2f, 1.2f, 1.2f, 0f, 0f, BONE)
                cube(0f, -5.9f, 0f, 1.4f, 0.3f, 1.4f, BLK)
                // espinho de osso no cotovelo
                prism(0f, 0.2f, -1.2f, 0.4f, 0.4f, 0.4f, 0.2f, 0.2f, 0f, -0.7f, BONE_L)
                translate(0f, -6.1f, 0f)
                rotX(wr)
                cube(0f, -0.4f, 0f, 1.15f, 1.2f, 1.15f, BONE)
                claws(markId)
            } else {
                // ombreira rasgada
                prism(0f, 0.4f, 0f, 2.6f, 0.9f, 2.6f, 1.0f, 1.0f, 0f, 0f, BLK_L)
                prism(side * 1.4f, -0.2f, 0f, 1.2f, 0.4f, 2.4f, 1.0f, 1.0f, 0f, 0f, BLK)
                // manga larga no alto do braço
                prism(0f, -2.6f, 0f, 1.65f, 2.7f, 1.7f, 1.25f, 1.25f, 0f, 0f, BLK)
                cube(0f, -5.2f, 0f, 1.95f, 0.3f, 1.95f, RED_D)
                translate(0f, -5.4f, 0f)
                rotX(el)
                // antebraço descoberto, com braçadeira
                prism(0f, -2.9f, 0f, 1.25f, 2.9f, 1.25f, 1.2f, 1.2f, 0f, 0f, SKIN)
                cube(0f, -4.9f, 0f, 1.5f, 0.9f, 1.5f, BLK_L)
                cube(0f, -5.5f, 0f, 1.58f, 0.15f, 1.58f, RED)
                // tiras da manga rasgada
                for (i in -1..1) {
                    prism(i * 0.9f, -1.0f, -0.3f, 0.12f, 1.1f, 0.5f, 5.5f, 1f, 0f, 0f, BLK)
                }
                translate(0f, -6.0f, 0f)
                rotX(wr)
                cube(0f, -0.5f, 0f, 1.25f, 1.2f, 1.25f, SKIN)
                cube(0f, -1.0f, 0.5f, 1.0f, 0.5f, 0.7f, SKIN_D)
                mark(0f, -1.3f, 0f, markId)
                if (hasSword) sword()
            }
        pop()
    }

    /** Cabeça do Ichigo: cabelo laranja espetado, olhar fechado, sobrancelhas franzidas. */
    private fun headN(tilt: Float, h0: Float) {
        push()
            translate(0f, 13.0f, 0f)
            rotX(tilt)
            // crânio e maxilar
            cube(0f, 4.3f, 0f, 3.9f, 3.9f, 3.8f, SKIN)
            prism(0f, 1.3f, 0.5f, 2.8f, 1.4f, 3.0f, 1.38f, 1.25f, 0f, 0f, SKIN)
            cube(0f, 0.0f, 0.2f, 1.9f, 0.5f, 1.9f, SKIN_D)
            cube(-4.0f, 3.8f, 0f, 0.25f, 0.9f, 0.7f, SKIN)
            cube(4.0f, 3.8f, 0f, 0.25f, 0.9f, 0.7f, SKIN)
            // olhos
            val ey = 0.62f * eyeOpen
            for (j in 0..1) {
                val sd = if (j == 0) 1f else -1f
                cube(sd * 1.95f, 4.3f, 3.82f, 1.05f, ey, 0.1f, WHITE)
                cube(sd * 1.85f, 4.3f, 3.9f, 0.55f, ey, 0.1f, BROWN)
                if (eyeOpen > 0.5f) cube(sd * 1.7f, 4.5f, 3.97f, 0.2f, 0.2f, 0.06f, BONE_L, true)
                push()
                    translate(sd * 1.95f, 5.5f, 3.9f)
                    rotZ(sd * 0.38f)
                    cube(0f, 0f, 0f, 1.45f, 0.3f, 0.12f, HAIR_D)
                pop()
                cube(sd * 1.95f, 3.45f, 3.82f, 0.9f, 0.1f, 0.08f, SKIN_D)
            }
            cube(0f, 3.1f, 4.0f, 0.4f, 0.8f, 0.35f, SKIN_D)
            cube(0f, 1.7f, 3.85f, 0.95f, 0.12f, 0.1f, 0xFF7A3A3A.toInt())
            // cabelo: capa e volume
            prism(0f, 7.2f, -0.2f, 4.2f, 1.2f, 4.1f, 0.95f, 0.95f, 0f, 0f, HAIR)
            cube(0f, 5.2f, -3.6f, 4.0f, 3.2f, 0.8f, HAIR_D)
            cube(-3.95f, 6.2f, -0.5f, 0.5f, 2.0f, 3.3f, HAIR)
            cube(3.95f, 6.2f, -0.5f, 0.5f, 2.0f, 3.3f, HAIR)
            // franja: pontas caindo sobre a testa
            for (i in -3..3) {
                push()
                    translate(i * 1.15f, 8.2f, 3.4f)
                    rotX(-0.5f - 0.05f * kotlin.math.abs(i) + h0 * 0.3f)
                    rotZ(-i * 0.06f)
                    val l = 2.4f + (3 - kotlin.math.abs(i)) * 0.35f
                    prism(0f, -l * 0.5f, 0f, 0.12f, l * 0.5f, 0.2f, 7f, 2.0f, 0f, 0f, if (i % 2 == 0) HAIR else HAIR_L)
                pop()
            }
            // costeletas
            for (j in 0..1) {
                val sd = if (j == 0) 1f else -1f
                push()
                    translate(sd * 4.1f, 6.0f, 1.8f)
                    rotZ(sd * 0.18f)
                    prism(0f, -1.7f, 0f, 0.12f, 1.7f, 0.25f, 3.5f, 2.2f, 0f, 0f, HAIR)
                pop()
            }
            // espetos do topo, balançando com o movimento
            for (i in -3..3) {
                push()
                    translate(i * 1.15f, 8.2f, -0.3f - 0.1f * kotlin.math.abs(i))
                    rotZ(-i * 0.16f)
                    rotX(-0.22f - hsw * 0.5f + 0.04f * i)
                    val hgt = 1.9f + (3 - kotlin.math.abs(i)) * 0.55f
                    prism(0f, hgt * 0.5f, 0f, 0.85f, hgt * 0.5f, 0.85f, 0.14f, 0.14f, 0f, 0f, if (i % 2 == 0) HAIR_L else HAIR)
                pop()
            }
            // espetos de trás, longos
            for (i in -2..2) {
                push()
                    translate(i * 1.5f, 6.2f, -4.0f)
                    rotZ(-i * 0.14f)
                    rotX(0.85f + hsw * 0.6f + 0.05f * i)
                    prism(0f, -1.9f, 0f, 0.14f, 1.9f, 0.3f, 6f, 2.6f, 0f, 0f, if (i % 2 == 0) HAIR_D else HAIR)
                pop()
            }
            mark(0f, 4.0f, 4.0f, 5)
        pop()
    }

    /** Cabeça do Vasto Lorde: máscara de osso com marcas vermelhas, chifres negros e crina longa. */
    private fun headH(tilt: Float, jaw: Float) {
        push()
            translate(0f, 13.0f, 0f)
            rotX(tilt)
            cube(0f, 4.4f, 0.1f, 4.2f, 4.2f, 4.0f, BONE)
            cube(0f, 8.6f, 0f, 3.4f, 0.4f, 3.2f, BONE_L)
            // placa do rosto
            prism(0f, 3.5f, 3.95f, 3.5f, 3.1f, 0.35f, 0.92f, 1.0f, 0f, 0f, BONE_L)
            cube(0f, 6.4f, 4.15f, 3.7f, 0.35f, 0.15f, BONE_D)
            cube(0f, 2.1f, 4.2f, 3.2f, 0.12f, 0.1f, BONE_S)
            // olhos: cavidade negra com pupila dourada
            for (j in 0..1) {
                val sd = if (j == 0) 1f else -1f
                push()
                    translate(sd * 1.95f, 4.5f, 4.3f)
                    rotZ(sd * 0.28f)
                    cube(0f, 0f, 0f, 1.4f, 0.85f * eyeOpen, 0.1f, BLK_D)
                    cube(sd * -0.1f, 0f, 0.08f, 0.55f, 0.6f * eyeOpen, 0.08f, GOLD, true)
                pop()
                // marcas vermelhas descendo do olho
                cube(sd * 2.1f, 2.4f, 4.3f, 0.22f, 1.7f, 0.08f, RED)
                cube(sd * 3.0f, 3.0f, 4.1f, 0.18f, 1.1f, 0.08f, RED)
                cube(sd * 1.3f, 6.2f, 4.2f, 0.9f, 0.14f, 0.08f, RED)
            }
            cube(0f, 3.2f, 4.3f, 0.3f, 0.6f, 0.1f, BONE_S)
            // mandíbula com dentes; abre no grito
            push()
                translate(0f, 1.6f, 1.2f)
                rotX(jaw * 0.55f)
                prism(0f, -0.2f, 1.2f, 2.7f, 1.0f, 2.6f, 1.25f, 1.1f, 0f, 0f, BONE)
                for (i in -3..3) cube(i * 0.62f, 0.7f, 3.85f, 0.2f, 0.5f, 0.1f, BONE_L)
                if (jaw > 0.25f) cube(0f, 0.9f, 3.7f, 2.0f, 0.4f * jaw, 0.1f, RED_L, true)
            pop()
            for (i in -3..3) cube(i * 0.62f, 1.9f, 4.2f, 0.2f, 0.4f, 0.1f, BONE_D)
            // chifres: três segmentos curvos para trás e para cima
            for (j in 0..1) {
                val sd = if (j == 0) 1f else -1f
                push()
                    translate(sd * 2.8f, 8.3f, -0.6f)
                    rotZ(-sd * 0.55f)
                    rotX(-0.30f)
                    prism(0f, 1.7f, 0f, 1.0f, 1.7f, 0.9f, 0.7f, 0.7f, 0f, 0f, BONE)
                    translate(0f, 3.4f, 0f)
                    rotZ(-sd * 0.25f)
                    rotX(-0.45f)
                    prism(0f, 1.9f, 0f, 0.7f, 1.9f, 0.65f, 0.7f, 0.7f, 0f, 0f, BLK_L)
                    translate(0f, 3.8f, 0f)
                    rotZ(-sd * 0.15f)
                    rotX(-0.55f)
                    prism(0f, 2.2f, 0f, 0.5f, 2.2f, 0.45f, 0.1f, 0.1f, 0f, 0f, BLK)
                pop()
            }
            // crina negra descendo pelas costas
            for (i in -2..2) {
                push()
                    translate(i * 1.5f, 7.6f, -3.6f)
                    rotZ(-i * 0.12f)
                    rotX(0.5f + hsw * 0.9f)
                    prism(0f, -3.2f, 0f, 0.4f, 3.2f, 0.45f, 2.3f, 1.8f, 0f, 0f, BLK_L)
                    translate(0f, -6.4f, 0f)
                    rotX(0.35f + hsw * 0.9f)
                    prism(0f, -3.2f, 0f, 0.14f, 3.2f, 0.25f, 3.0f, 2.2f, 0f, 0f, BLK)
                pop()
            }
            mark(0f, 3.6f, 4.4f, 5)
        pop()
    }

    // ───────────── montagem do corpo ─────────────

    private const val HIP = 12.1f
    private var hipNow = HIP
    private var liftNow = 0f

    private fun buildModel(phase: Float, mvs: Float, crouchT: Float, vzN: Float, breath: Float) {
        n = 0
        top = 0
        zMode = true
        lYc = cos(yaw)
        lYs = sin(yaw)
        identity()
        rotY(yaw + spinA)
        translate(0f, liftNow, -hitKick * 1.2f)
        for (kk in 0..1) {
            val sd = if (kk == 0) 1f else -1f
            val pp = if (sd > 0f) phase else phase + PI.toFloat()
            var a = sin(pp) * 0.75f * mvs - crouchT * 0.55f
            var kn = max(0f, -cos(pp)) * 1.0f * mvs + 0.08f + crouchT * 1.0f
            a = lerp(a, -0.5f + sd * 0.3f - 0.2f * max(0f, vzN), airW)
            kn = lerp(kn, 0.9f, airW)
            a = lerp(a, 0.9f + sd * 0.1f, rollW)
            kn = lerp(kn, 0.4f, rollW)
            leg(sd, a, kn, -(a + kn) * 0.8f, hipNow)
        }
        push()
        translate(0f, hipNow, 0f)
        rotY(ps[1] * 0.4f)
        rotX(ps[0] * 0.55f - hitKick * 0.2f)
        torsoLow(ps[3])
        push()
        rotY(ps[1] * 0.6f)
        rotX(ps[0] * 0.45f)
        torsoUp(breath)
        mark(0f, 8.5f, 0f, 4)
        if (hol) headH(hdS - ps[0] * 0.4f, mouthS) else headN(hdS - ps[0] * 0.4f, hsw)
        arm(1f, ps[2], ps[3], ps[4], ps[5], !hol, 0)
        arm(-1f, ps[6], ps[7], ps[8], 0f, false, 1)
        pop()
        pop()
    }

    // ───────────── efeitos 2D ─────────────

    private fun bolt(c: Canvas, p: Paint, x0: Float, y0: Float, x1: Float, y1: Float, w: Float, a: Float, outer: Int, inner: Int) {
        val dx = x1 - x0
        val dy = y1 - y0
        val len = max(1f, hypot(dx, dy))
        val nx = -dy / len
        val ny = dx / len
        path.reset()
        path.moveTo(x0, y0)
        for (i in 1..4) {
            val u = i / 4f
            val j = if (i == 4) 0f else (kotlin.random.Random.nextFloat() - 0.5f) * len * 0.4f
            path.lineTo(x0 + dx * u + nx * j, y0 + dy * u + ny * j)
        }
        p.style = Paint.Style.STROKE
        p.strokeCap = Paint.Cap.ROUND
        p.strokeWidth = w * 2.6f
        p.color = V.alpha(outer, 0.45f * a)
        c.drawPath(path, p)
        p.strokeWidth = w
        p.color = V.alpha(inner, a)
        c.drawPath(path, p)
        p.strokeCap = Paint.Cap.BUTT
        p.style = Paint.Style.FILL
    }

    private fun orb(c: Canvas, p: Paint, x: Float, y: Float, r: Float, a: Float, outer: Int, mid: Int, core: Int) {
        p.style = Paint.Style.FILL
        p.color = V.alpha(DARKFX, 0.40f * a)
        c.drawCircle(x, y, r * 2.4f, p)
        p.color = V.alpha(outer, 0.45f * a)
        c.drawCircle(x, y, r * 1.8f, p)
        p.color = V.alpha(mid, 0.70f * a)
        c.drawCircle(x, y, r * 1.2f, p)
        p.color = V.alpha(core, 0.95f * a)
        c.drawCircle(x, y, r * 0.7f, p)
    }

    private fun trail(c: Canvas, p: Paint, a: Float, outer: Int, inner: Int) {
        if (trN < 2) return
        p.style = Paint.Style.FILL
        for (i in 1 until trN) {
            val i0 = (trHead - i + TR * 2) % TR
            val i1 = (trHead - i - 1 + TR * 2) % TR
            val fade = 1f - i.toFloat() / trN.toFloat()
            path.reset()
            path.moveTo(trTX[i0], trTY[i0])
            path.lineTo(trTX[i1], trTY[i1])
            path.lineTo(trBX[i1], trBY[i1])
            path.lineTo(trBX[i0], trBY[i0])
            path.close()
            p.color = V.alpha(outer, 0.60f * a * fade)
            c.drawPath(path, p)
            // fio claro na ponta
            p.style = Paint.Style.STROKE
            p.strokeWidth = max(1.4f, rs * 0.9f)
            p.color = V.alpha(inner, 0.85f * a * fade)
            c.drawLine(trTX[i0], trTY[i0], trTX[i1], trTY[i1], p)
            p.style = Paint.Style.FILL
        }
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
        hol = hollowOn

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

        val sk = skillK
        val skH = skillHol
        val u = if (sk != 0) skillP.coerceIn(0f, 1f) else 0f
        val t = if (attack > 0.001f && sk == 0) 1f - attack else -1f
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
        flashV = max(0f, flashV - dt * 3.2f)
        if (!first && hol != prevHol) flashV = 1f
        prevHol = hol
        val bp = clock % 3.9f
        eyeOpen = if (bp < 0.14f) max(0.12f, abs(bp - 0.07f) / 0.07f) else 1f
        val vzN = (airVz / 340f).coerceIn(-1.2f, 1f)
        val still = 1f - max(rollW, airW)
        val mvs = still * blend

        // ── pose alvo ──
        val idle = if (hol) IDLE_H else IDLE_N
        for (i in 0 until NP) pt[i] = idle[i]
        pt[0] += 0.10f * spN
        pt[6] += -sin(phase) * 0.55f * mvs
        pt[2] += sin(phase) * 0.18f * mvs
        pt[3] = lerp(pt[3], 0.85f, airW)
        pt[7] = lerp(pt[7], 0.85f, airW)
        pt[6] = lerp(pt[6], -0.5f, airW)
        pt[0] = lerp(pt[0], 1.1f, rollW)
        pt[2] = lerp(pt[2], 0.55f, rollW)
        pt[3] = lerp(pt[3], 0.10f, rollW)
        pt[4] = lerp(pt[4], -0.20f, rollW)
        pt[5] = lerp(pt[5], 0.20f, rollW)
        pt[6] = lerp(pt[6], 0.70f, rollW)
        pt[7] = lerp(pt[7], 0.10f, rollW)

        var mTgt = 0f
        var gTgt = 0f
        var aTgt = if (hol) 0.45f else 0.12f
        var cTgt = 0f
        var hdx = 0f
        var spinT = 0f
        var trailOn = false
        var tipId = 2

        if (attacking) {
            val sw = swing.coerceIn(0, 2)
            val ti = IMPACT[sw]
            seqPose(if (hol) ATK_H[sw] else ATK_N[sw], idle, 0.30f, ti, ti + 0.22f, t)
            trailOn = t > 0.26f && t < ti + 0.20f
            gTgt = if (hol) 0f else 0.35f
            aTgt = max(aTgt, 0.4f)
            if (hol) tipId = if (sw == 1) 1 else 0
        }
        if (sk != 0) {
            val rl = rel(skH, sk)
            val keys = if (skH) SK_H[sk - 1] else SK_N[sk - 1]
            seqPose(keys, idle, rl * 0.62f, rl, rl + (1f - rl) * 0.32f, u)
            val pre = if (u < rl) ease(u / rl) else 1f - ease((u - rl) / (1f - rl))
            if (!skH) {
                if (sk == 1) {
                    gTgt = pre
                    aTgt = 0.3f + 0.7f * pre
                    cTgt = pre
                    trailOn = u > rl * 0.55f && u < rl + 0.22f
                } else if (sk == 2) {
                    aTgt = 0.6f
                    gTgt = 0.6f
                    trailOn = u > rl * 0.6f && u < rl + 0.3f
                } else if (sk == 3) {
                    spinT = TWO_PI * 2f * ease(((u - 0.06f) / 0.5f).coerceIn(0f, 1f))
                    gTgt = pre
                    aTgt = 0.5f + 0.5f * pre
                    trailOn = u > 0.08f && u < 0.62f
                } else {
                    aTgt = 0.3f + 0.7f * pre
                    mTgt = pre
                    hdx = -0.5f * pre
                    cTgt = pre
                }
            } else {
                if (sk == 1) {
                    cTgt = pre
                    mTgt = 0.8f * pre
                    aTgt = 0.6f + 0.4f * pre
                    hdx = -0.2f * pre
                } else if (sk == 2) {
                    aTgt = 0.8f
                    trailOn = u > rl * 0.6f && u < rl + 0.3f
                    tipId = 0
                } else if (sk == 3) {
                    mTgt = pre
                    hdx = -0.6f * pre
                    aTgt = 0.5f + 0.5f * pre
                } else {
                    cTgt = pre
                    mTgt = 0.6f * pre
                    aTgt = 0.6f + 0.6f * pre
                    hdx = -0.4f * pre
                }
            }
        }
        if (sk != 0 && u >= rel(skH, sk) && !skRel) {
            skRel = true
            flashV = 1f
        }
        if (sk == 0) skRel = false
        spinA = spinT

        // suaviza a pose (o giro completo não é suavizado)
        if (first) {
            for (i in 0 until NP) ps[i] = pt[i]
        } else {
            val kk = min(1f, dt * 24f)
            for (i in 0 until NP) if (i != 10) ps[i] += (pt[i] - ps[i]) * kk
        }
        val k2 = min(1f, dt * 12f)
        mouthS += (mTgt - mouthS) * k2
        glowS += (gTgt - glowS) * k2
        auraS += (aTgt - auraS) * k2
        chargeS += (cTgt - chargeS) * k2
        hdS += (hdx - hdS) * k2
        glow = glowS

        // balanço do casaco, cabelos e corrente
        cw0 = 0.25f + 0.10f * sin(clock * 2.4f) + 0.55f * spN + 0.45f * rollW + 0.2f * airW
        cw1 = 0.15f + 0.12f * sin(clock * 2.9f + 1f) + 0.45f * spN + 0.3f * rollW
        cw2 = 0.10f + 0.08f * sin(clock * 2.1f + 2f) + 0.30f * spN
        hsw = 0.10f * sin(clock * 2.0f) + 0.28f * spN + 0.2f * rollW - hitKick * 0.2f
        chn = sin(clock * 3.1f) * 0.3f + sin(phase) * 0.4f * mvs

        val bob = abs(sin(phase)) * 0.9f * mvs + sin(clock * 2.2f) * 0.14f * (1f - mvs)
        val crouchT = max(ps[9], landDip * 0.5f)
        hipNow = HIP + bob - crouchT * 1.5f - hitKick * 0.4f
        liftNow = ps[11]
        val breath = sin(clock * 2.0f) * 0.25f

        rs = s * PX
        ox = fx
        oy = fy

        // ── chão: anéis das skills ──
        val ringOn = (!skH && (sk == 3 || sk == 4)) || (skH && (sk == 3 || sk == 4))
        if (ringOn) {
            val rl = rel(skH, sk)
            val g = if (u < rl) ease(u / rl) else 1f - ease((u - rl) / (1f - rl))
            val big = if (skH && sk == 4) 2.2f else 1f
            val rr = (9f + 13f * g) * rs * big
            p.style = Paint.Style.STROKE
            p.strokeWidth = max(1.8f, rs * 1.6f)
            p.color = V.alpha(if (skH) BONE else RED, 0.85f * g)
            c.drawOval(fx - rr, fy - rr * SINP, fx + rr, fy + rr * SINP, p)
            p.color = V.alpha(DARKFX, 0.7f * g)
            c.drawOval(fx - rr * 0.7f, fy - rr * 0.7f * SINP, fx + rr * 0.7f, fy + rr * 0.7f * SINP, p)
            p.style = Paint.Style.FILL
        }

        // ── imagens residuais do Shunpo / Sonído ──
        val sh = (sk == 2 && u > rel(skH, 2) - 0.2f && u < rel(skH, 2) + 0.4f) || rollW > 0.5f
        if (sh) {
            val dx = sin(yaw)
            val dy = cos(yaw) * SINP
            val aSave = alphaV
            for (gi in 2 downTo 1) {
                val off = gi * 13f * rs
                ox = fx - dx * off
                oy = fy - dy * off
                alphaV = aSave * (if (gi == 1) 0.34f else 0.17f)
                buildModel(phase, mvs, crouchT, vzN, breath)
                flushZ(c)
            }
            alphaV = aSave
            ox = fx
            oy = fy
        }

        buildModel(phase, mvs, crouchT, vzN, breath)
        flushZ(c)

        // ── fita do golpe ──
        val tpx = if (hol) mkX[tipId] else mkX[2]
        val tpy = if (hol) mkY[tipId] else mkY[2]
        val bpx = if (hol) tpx + (mkX[4] - tpx) * 0.45f else tpx + (mkX[3] - tpx) * 0.55f
        val bpy = if (hol) tpy + (mkY[4] - tpy) * 0.45f else tpy + (mkY[3] - tpy) * 0.55f
        if (trailOn) {
            trTX[trHead] = tpx
            trTY[trHead] = tpy
            trBX[trHead] = bpx
            trBY[trHead] = bpy
            trHead = (trHead + 1) % TR
            if (trN < TR - 1) trN++
        } else if (trN > 0) {
            trN--
        }
        trail(c, p, 1f, if (hol) BONE_D else RED, if (hol) WHITE else EDGE_L)

        // ── energia acumulada ──
        if (chargeS > 0.04f) {
            val a = chargeS.coerceIn(0f, 1f)
            if (!hol && sk == 1) {
                orb(c, p, mkX[2], mkY[2], (1.0f + 2.4f * a) * rs, a, RED, RED_L, WHITE)
            } else if (!hol && sk == 4) {
                orb(c, p, mkX[5], mkY[5], (0.9f + 2.2f * a) * rs, a, BONE_S, BONE, WHITE)
            } else if (hol && sk == 1) {
                orb(c, p, mkX[0], mkY[0], (1.4f + 3.6f * a) * rs * (0.95f + 0.05f * sin(clock * 40f)), a, RED, RED_L, WHITE)
            } else if (hol && sk == 4) {
                orb(c, p, mkX[4], mkY[4] - 14f * rs, (2.0f + 6.0f * a) * rs, a, RED, RED_L, WHITE)
            }
        }

        // ── aura de reiatsu: chamas rubro-negras ──
        if (auraS > 0.06f) {
            auraT -= dt
            if (auraT <= 0f) {
                auraT = 0.045f / (0.4f + auraS)
                val n0 = if (auraS > 0.6f) 2 else 1
                for (i in 0 until n0) {
                    val cl = when (rnd.nextInt(4)) {
                        0 -> RED
                        1 -> RED_L
                        2 -> DARKFX
                        else -> if (hol) BONE else EDGE
                    }
                    emitSpark(
                        mkX[4] + (rnd.nextFloat() - 0.5f) * 11f * rs, mkY[4] + (rnd.nextFloat() * 8f - 2f) * rs,
                        (rnd.nextFloat() - 0.5f) * rs * 14f, -rs * (14f + rnd.nextFloat() * 26f * auraS),
                        0.35f + rnd.nextFloat() * 0.3f, -rs * 4f, cl, 1.0f + auraS * 0.8f
                    )
                }
            }
            if (auraS > 0.55f && rnd.nextFloat() < auraS * 0.5f) {
                val ang = rnd.nextFloat() * TWO_PI
                val r0 = (5f + rnd.nextFloat() * 3f) * rs
                val r1 = r0 + (8f + rnd.nextFloat() * 10f) * rs * auraS
                bolt(
                    c, p, mkX[4] + cos(ang) * r0, mkY[4] + sin(ang) * r0 * 0.9f,
                    mkX[4] + cos(ang) * r1, mkY[4] + sin(ang) * r1 * 0.9f, max(1.3f, rs * 0.8f), 0.9f, DARKFX, RED_L
                )
            }
        }

        // ── pilar de luz da transformação ──
        if (!skH && sk == 4) {
            val rl = rel(false, 4)
            val g = if (u < rl) ease(u / rl) * 0.5f else 1f - ease((u - rl) / (1f - rl))
            if (g > 0.02f) {
                val w0 = (4f + 6f * g) * rs
                p.style = Paint.Style.FILL
                p.color = V.alpha(DARKFX, 0.45f * g)
                c.drawRect(fx - w0 * 1.5f, fy - 95f * rs, fx + w0 * 1.5f, fy, p)
                p.color = V.alpha(RED, 0.45f * g)
                c.drawRect(fx - w0, fy - 95f * rs, fx + w0, fy, p)
                p.color = V.alpha(WHITE, 0.55f * g)
                c.drawRect(fx - w0 * 0.4f, fy - 95f * rs, fx + w0 * 0.4f, fy, p)
            }
        }

        // ── rastros de velocidade (Shunpo / Sonído) ──
        val streak = max(rollW, if (sh) 0.8f else 0f)
        if (streak > 0.3f) {
            val dx = sin(yaw)
            val dy = cos(yaw) * SINP
            p.style = Paint.Style.STROKE
            p.strokeCap = Paint.Cap.ROUND
            for (i in 0..3) {
                val off = (i - 1.5f) * 3.4f * rs
                val sx0 = mkX[4] - dy * off - dx * 6f * rs
                val sy0 = mkY[4] + dx * off - dy * 6f * rs + (rnd.nextFloat() - 0.5f) * 2f * rs
                val len = (18f + rnd.nextFloat() * 16f) * rs * streak
                p.strokeWidth = max(1.4f, rs * 1.3f)
                p.color = V.alpha(if (i % 2 == 0) DARKFX else if (hol) BONE else RED_L, 0.8f * streak)
                c.drawLine(sx0, sy0, sx0 - dx * len, sy0 - dy * len, p)
            }
            p.strokeCap = Paint.Cap.BUTT
            p.style = Paint.Style.FILL
        }

        // ── clarão no instante do golpe / da troca de forma ──
        if (flashV > 0.02f) {
            val kk = flashV
            p.style = Paint.Style.FILL
            p.color = V.alpha(WHITE, 0.50f * kk)
            c.drawCircle(mkX[4], mkY[4], rs * (6f + 14f * (1f - kk)), p)
            p.color = V.alpha(if (hol) RED else EDGE, 0.34f * kk)
            c.drawCircle(mkX[4], mkY[4], rs * (10f + 20f * (1f - kk)), p)
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
