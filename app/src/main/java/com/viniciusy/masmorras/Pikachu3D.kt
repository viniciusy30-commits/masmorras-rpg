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
 * Pikachu em cubos 3D (mesmo mini motor do Mago/Guerreiro: z-buffer por pixel e contorno).
 * Corpo articulado com pernas, braços, cabeça com bochechas que carregam, orelhas de ponta preta e cauda em raio.
 * Cada golpe tem animação própria: Choque do Trovão (ataque), Trovoada, Cauda de Ferro, Investida Trovão,
 * Trovão e Ataque Rápido (o rolamento).
 */
object Pikachu3D {
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

    // ───────────── paleta ─────────────
    private val Y = 0xFFFFDC38.toInt()
    private val Y_L = 0xFFFFEE8A.toInt()
    private val Y_D = 0xFFE9B320.toInt()
    private val BLK = 0xFF1B1B1B.toInt()
    private val WHITE = 0xFFFFFFFF.toInt()
    private val RED = 0xFFE5382B.toInt()
    private val RED_L = 0xFFFF7A66.toInt()
    private val BRN = 0xFF8A5A2B.toInt()
    private val BRN_D = 0xFF5E3B1A.toInt()
    private val STEEL = 0xFFB8C4CE.toInt()
    private val STEEL_D = 0xFF7F8C98.toInt()
    private val ZAP = 0xFFFFE23A.toInt()
    private val ZAP_L = 0xFFFFF7B0.toInt()

    // ───────────── skills (índice 1..4) ─────────────
    // 1 Trovoada, 2 Cauda de Ferro, 3 Investida Trovão, 4 Trovão
    val DUR = floatArrayOf(0f, 0.75f, 0.90f, 1.05f, 0.95f)
    val REL = floatArrayOf(0f, 0.50f, 0.60f, 0.36f, 0.45f)
    const val T_SHOT = 0.32f // ponto (0..1) do ataque básico em que o raio sai

    // ───────────── estado externo (preenchido pelo Player antes de desenhar) ─────────────
    var rollP = -1f        // 0..1 durante o Ataque Rápido; <0 = fora dele
    var inAir = false
    var airVz = 0f
    var airH = 0f
    var skillK = 0
    var skillP = -1f
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
    private var released = false
    private var skRel = false
    private var flashV = 0f
    private var cheekT = 0f
    private var tailT = 0f
    private val ps = FloatArray(12)
    private val pt = FloatArray(12)
    private val mkX = FloatArray(6)
    private val mkY = FloatArray(6)
    private var tailC = 0xFFFFDC38.toInt()
    private var tailCL = 0xFFFFEE8A.toInt()

    private const val NSP = 48
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

    /** Guarda onde um ponto do modelo caiu na tela (mãos, bochechas, ponta da cauda, centro do corpo). */
    private fun mark(x: Float, y: Float, z: Float, id: Int) {
        val m = stack[top]
        val wx = m[0] * x + m[1] * y + m[2] * z + m[9]
        val wy = m[3] * x + m[4] * y + m[5] * z + m[10]
        val wz = m[6] * x + m[7] * y + m[8] * z + m[11]
        mkX[id] = ox + wx * rs
        mkY[id] = oy + (wz * SINP - wy * COSP) * rs
    }

    // ───────────── partes do corpo ─────────────

    private fun leg(side: Float, a: Float, knee: Float, hipY: Float, spread: Float) {
        push()
            translate(side * 2.2f, hipY, 0f)
            rotZ(side * spread)
            rotX(a)
            cube(0f, -1.2f, 0f, 1.7f, 1.3f, 1.8f, Y)
            translate(0f, -2.4f, 0f)
            rotX(knee)
            cube(0f, -0.9f, 0f, 1.5f, 1.0f, 1.6f, Y_D)
            translate(0f, -1.8f, 0f)
            rotX(-(a + knee) * 0.8f)
            cube(0f, -0.1f, 1.1f, 1.7f, 0.7f, 2.6f, Y)
            cube(0f, -0.65f, 1.2f, 1.6f, 0.2f, 2.5f, BRN)
        pop()
    }

    private fun arm(side: Float, sh: Float, spr: Float, el: Float) {
        push()
            translate(side * 3.9f, 5.6f, 0.6f)
            rotZ(side * spr)
            rotX(sh)
            cube(0f, -1.3f, 0f, 1.0f, 1.4f, 1.0f, Y)
            translate(0f, -2.7f, 0f)
            rotX(el)
            cube(0f, -0.9f, 0.1f, 0.95f, 1.0f, 0.95f, Y)
            cube(0f, -2.0f, 0.2f, 0.9f, 0.35f, 0.9f, Y_L)
            mark(0f, -2.4f, 0.4f, if (side > 0f) 0 else 1)
        pop()
    }

    private fun ear(side: Float, spread: Float, back: Float) {
        push()
            translate(side * 3.0f, 6.9f, -0.6f)
            rotZ(-side * (0.40f + spread))
            rotX(-back)
            cube(0f, 2.7f, 0f, 1.25f, 2.7f, 0.7f, Y)
            cube(0f, 6.0f, 0f, 1.3f, 1.5f, 0.74f, BLK)
            cube(0f, 7.3f, 0f, 0.9f, 0.3f, 0.55f, BLK)
        pop()
    }

    private fun head(tilt: Float, mouth: Float, cheek: Float, earSp: Float, earBack: Float) {
        push()
            translate(0f, 7.6f, 0.2f)
            rotX(tilt)
            cube(0f, 3.5f, 0f, 5.0f, 3.0f, 3.9f, Y)
            cube(0f, 3.5f, 0f, 4.2f, 3.5f, 4.1f, Y)
            cube(0f, 3.5f, -0.3f, 4.6f, 3.3f, 4.0f, Y)
            cube(0f, 7.05f, 0.1f, 3.8f, 0.2f, 3.6f, Y_L)
            // olhos
            cube(-2.7f, 4.3f, 4.12f, 0.8f, 1.0f * eyeOpen, 0.1f, BLK)
            cube(2.7f, 4.3f, 4.12f, 0.8f, 1.0f * eyeOpen, 0.1f, BLK)
            if (eyeOpen > 0.5f) {
                cube(-2.9f, 4.9f, 4.2f, 0.3f, 0.35f, 0.06f, WHITE, true)
                cube(2.5f, 4.9f, 4.2f, 0.3f, 0.35f, 0.06f, WHITE, true)
            }
            cube(0f, 3.3f, 4.12f, 0.35f, 0.25f, 0.08f, BLK)
            // boca
            cube(0f, 2.3f, 4.12f, 0.9f + 0.5f * mouth, 0.2f + 0.7f * mouth, 0.08f, BLK)
            if (mouth > 0.3f) cube(0f, 1.95f, 4.14f, 0.55f, 0.3f * mouth, 0.06f, RED_L)
            // bochechas
            for (k in 0..1) {
                val sd = if (k == 0) 1f else -1f
                cube(sd * 3.9f, 2.2f, 3.4f, 1.15f, 1.15f, 0.8f, RED)
                if (cheek > 0.1f) cube(sd * 3.9f, 2.2f, 3.45f, 1.25f, 1.25f, 0.85f, VoxelArt.mix(RED, RED_L, cheek), true)
                mark(sd * 3.9f, 2.2f, 4.3f, if (k == 0) 2 else 3)
            }
            ear(1f, earSp, earBack)
            ear(-1f, earSp, earBack)
        pop()
    }

    /** Cauda em raio (plano vertical virado para a frente/trás); [a] = ângulo da base, [wag] = balanço. */
    private fun tail(a: Float, wag: Float, flex: Float) {
        push()
            translate(0f, 2.2f, -3.1f)
            rotZ(wag + 0.12f)
            rotX(a)
            cube(0.8f, 0.9f, 0f, 1.0f, 0.9f, 0.65f, BRN)
            cube(0.8f, 2.5f, 0f, 1.0f, 0.8f, 0.6f, tailC)
            translate(0f, 2.5f, 0f)
            rotX(flex)
            cube(-0.8f, 1.2f, 0f, 2.6f, 0.7f, 0.6f, tailC)
            cube(-2.2f, 3.5f, 0f, 1.2f, 1.7f, 0.6f, tailC)
            cube(1.4f, 5.4f, 0f, 3.6f, 0.8f, 0.6f, tailC)
            cube(3.2f, 8.4f, 0f, 1.8f, 2.6f, 0.62f, tailCL)
            cube(3.9f, 11.2f, 0f, 1.1f, 0.7f, 0.6f, tailCL)
            mark(3.6f, 11.0f, 0f, 4)
        pop()
    }

    private fun torso() {
        cube(0f, 3.2f, 0f, 3.9f, 3.3f, 3.2f, Y)
        cube(0f, 6.2f, 0f, 3.2f, 1.6f, 2.8f, Y)
        cube(0f, 3.0f, 3.05f, 2.7f, 2.4f, 0.25f, Y_L)
        cube(0f, 5.0f, -3.05f, 3.4f, 0.45f, 0.14f, BRN)
        cube(0f, 3.1f, -3.25f, 3.5f, 0.45f, 0.14f, BRN_D)
    }

    // ───────────── efeitos 2D ─────────────

    private fun bolt(c: Canvas, p: Paint, x0: Float, y0: Float, x1: Float, y1: Float, w: Float, a: Float) {
        val dx = x1 - x0
        val dy = y1 - y0
        val len = max(1f, hypot(dx, dy))
        val nx = -dy / len
        val ny = dx / len
        path.reset()
        path.moveTo(x0, y0)
        for (i in 1..4) {
            val u = i / 4f
            val j = if (i == 4) 0f else (kotlin.random.Random.nextFloat() - 0.5f) * len * 0.45f
            path.lineTo(x0 + dx * u + nx * j, y0 + dy * u + ny * j)
        }
        p.style = Paint.Style.STROKE
        p.strokeCap = Paint.Cap.ROUND
        p.strokeWidth = w * 2.6f
        p.color = VoxelArt.alpha(ZAP, 0.35f * a)
        c.drawPath(path, p)
        p.strokeWidth = w
        p.color = VoxelArt.alpha(ZAP_L, a)
        c.drawPath(path, p)
        p.strokeCap = Paint.Cap.BUTT
        p.style = Paint.Style.FILL
    }

    private fun glowBall(c: Canvas, p: Paint, x: Float, y: Float, r: Float, a: Float) {
        p.style = Paint.Style.FILL
        p.color = VoxelArt.alpha(ZAP, 0.22f * a)
        c.drawCircle(x, y, r * 2.4f, p)
        p.color = VoxelArt.alpha(ZAP_L, 0.45f * a)
        c.drawCircle(x, y, r * 1.5f, p)
        p.color = VoxelArt.alpha(WHITE, 0.95f * a)
        c.drawCircle(x, y, r * 0.8f, p)
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

        val sk = skillK
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
        flashV = max(0f, flashV - dt * 3.5f)
        val bp = clock % 3.7f
        eyeOpen = if (bp < 0.14f) max(0.12f, abs(bp - 0.07f) / 0.07f) else 1f
        val vzN = (airVz / 340f).coerceIn(-1.2f, 1f)
        val still = 1f - max(rollW, airW)
        val mvs = still * blend

        // ── alvos da pose ──
        var lean = 0.05f + 0.10f * spN
        var shL = -0.2f - sin(phase) * 0.6f * mvs
        var shR = -0.2f + sin(phase) * 0.6f * mvs
        var spr = 0.28f + 0.05f * sin(clock * 1.3f)
        var el = -0.25f
        var hd = 0f
        var crouch = 0f
        var mouth = 0f
        var cheek = 0f
        var earB = 0.05f * sin(clock * 1.7f)
        var tailUp = -0.30f
        var zap = 0f
        var flipA = 0f
        var lift = 0f
        var tuck = 0f
        var dashW = 0f
        var steel = 0f
        var ball = 0f

        // no ar: braços abertos
        spr = lerp(spr, 0.95f, airW)
        shL = lerp(shL, -0.55f, airW)
        shR = lerp(shR, -0.55f, airW)
        // Ataque Rápido: corpo deitado para a frente, tudo para trás
        lean = lerp(lean, 1.15f, rollW)
        shL = lerp(shL, 0.9f, rollW)
        shR = lerp(shR, 0.9f, rollW)
        spr = lerp(spr, 0.1f, rollW)
        earB = lerp(earB, 1f, rollW)
        hd = lerp(hd, -0.5f, rollW)
        zap = max(zap, 0.5f * rollW)
        dashW = max(dashW, rollW)

        if (attacking) {
            // Choque do Trovão: junta energia nas mãos e arremessa
            val windW = if (t < T_SHOT) ease(t / T_SHOT) else 0f
            val thrW = if (t >= T_SHOT) 1f - ease((t - T_SHOT) / (1f - T_SHOT)) else 0f
            shL = shL + (-0.9f - shL) * windW + (-1.5f - shL) * thrW
            shR = shR + (-0.9f - shR) * windW + (-1.5f - shR) * thrW
            spr = spr + (0.5f - spr) * windW + (0.12f - spr) * thrW
            el = el + (-1.3f - el) * windW + (-0.05f - el) * thrW
            lean = lean + (-0.05f - lean) * windW + (0.25f - lean) * thrW
            crouch = 0.3f * windW
            cheek = max(windW, thrW)
            mouth = 0.8f * thrW
            zap = max(zap, 0.35f * windW + 0.7f * thrW)
            if (t >= T_SHOT && !released) {
                released = true
                flashV = 1f
                for (k in 0..1) {
                    for (i in 0..5) {
                        val ang = rnd.nextFloat() * TWO_PI
                        val sp0 = (14f + rnd.nextFloat() * 30f) * rs
                        emitSpark(mkX[k], mkY[k], cos(ang) * sp0, sin(ang) * sp0, 0.3f, rs * 40f, if (i % 2 == 0) ZAP_L else ZAP, 1.0f)
                    }
                }
            }
        } else {
            released = false
        }

        when (sk) {
            1 -> { // Trovoada: braços abertos, cabeça para trás, descarga para a frente
                val r = REL[1]
                shL = ph(u, r, shL, -0.25f, -1.55f)
                shR = shL
                spr = ph(u, r, spr, 1.55f, 0.1f)
                el = ph(u, r, el, -0.4f, 0f)
                lean = ph(u, r, lean, -0.12f, 0.28f)
                hd = ph(u, r, 0f, -0.35f, 0.05f)
                mouth = ph(u, r, 0f, 0.5f, 1f)
                cheek = ph(u, r, 0f, 1f, 1f)
                crouch = ph(u, r, 0f, 0.15f, 0f)
                zap = ph(u, r, 0f, 0.8f, 1f)
            }
            2 -> { // Cauda de Ferro: agacha, cambalhota e martelada com a cauda de aço
                val r = REL[2]
                crouch = ph(u, r, 0f, 0.8f, 0.5f)
                shL = -0.9f
                shR = -0.9f
                spr = 0.5f
                el = -1.3f
                steel = if (u < 0.04f) u / 0.04f else if (u > 0.95f) (1f - u) / 0.05f else 1f
                tailUp = ph(u, r, -0.30f, -1.0f, 1.0f)
                val q = ((u - 0.12f) / 0.46f).coerceIn(0f, 1f)
                flipA = TWO_PI * ease(q)
                lift = 7f * sin(q * PI.toFloat())
                tuck = sin(q * PI.toFloat())
                zap = 0.3f
            }
            3 -> { // Investida Trovão: carrega agachado e dispara de cabeça
                val r = REL[3]
                lean = ph(u, r, lean, 0.45f, 1.15f)
                crouch = ph(u, r, 0f, 0.9f, 0.3f)
                shL = ph(u, r, shL, 0.6f, 0.9f)
                shR = shL
                spr = ph(u, r, spr, 0.15f, 0.1f)
                earB = ph(u, r, earB, 0.3f, 1f)
                zap = ph(u, r, 0f, 0.9f, 1f)
                cheek = 1f
                dashW = max(dashW, ph(u, r, 0f, 0.3f, 1f))
                ball = ph(u, r, 0f, 0.5f, 1f)
            }
            4 -> { // Trovão: braços para o céu, depois martela para baixo
                val r = REL[4]
                spr = ph(u, r, spr, 2.9f, 0.15f)
                shL = ph(u, r, shL, 0f, -1.2f)
                shR = shL
                lean = ph(u, r, lean, -0.2f, 0.2f)
                hd = ph(u, r, 0f, -0.55f, 0.2f)
                mouth = ph(u, r, 0f, 0.8f, 0.3f)
                cheek = 1f
                zap = ph(u, r, 0f, 1f, 0.7f)
                lift = ph(u, r, 0f, 1.2f, 0f)
            }
        }
        if (sk != 0 && u >= REL[sk] && !skRel) {
            skRel = true
            flashV = 1f
        }
        if (sk == 0) skRel = false

        // suaviza a pose (golpes mantêm o ritmo; transições entre estados não dão tranco)
        pt[0] = lean; pt[1] = shL; pt[2] = shR; pt[3] = spr; pt[4] = el; pt[5] = hd
        pt[6] = crouch; pt[7] = mouth; pt[8] = cheek; pt[9] = earB; pt[10] = tailUp; pt[11] = zap
        if (first) {
            for (i in 0..11) ps[i] = pt[i]
        } else {
            val k = min(1f, dt * 24f)
            for (i in 0..11) ps[i] += (pt[i] - ps[i]) * k
        }

        // cores da cauda (vira aço na Cauda de Ferro)
        tailC = VoxelArt.mix(Y, STEEL, steel)
        tailCL = VoxelArt.mix(Y_L, 0xFFEAF1F6.toInt(), steel)

        // ── monta o modelo ──
        val bob = abs(sin(phase)) * 0.9f * mvs + sin(clock * 2.2f) * 0.12f * (1f - mvs)
        val crouchT = max(ps[6], landDip * 0.5f)
        val hipY = 4.4f + bob - crouchT * 1.6f - hitKick * 0.4f

        rs = s * PX
        ox = fx
        oy = fy
        n = 0
        top = 0
        zMode = true
        lYc = cos(yaw)
        lYs = sin(yaw)
        identity()
        rotY(yaw)
        translate(0f, lift, -hitKick * 1.2f)
        if (flipA > 0.001f) {
            translate(0f, 5f, 0f)
            rotX(flipA)
            translate(0f, -5f, 0f)
        }

        val wag = sin(clock * 2.3f) * 0.06f + sin(phase) * 0.22f * mvs
        for (k in 0..1) {
            val sd = if (k == 0) 1f else -1f
            val pp = if (sd > 0f) phase else phase + PI.toFloat()
            var a = sin(pp) * 0.75f * mvs - crouchT * 0.7f
            var kn = max(0f, -cos(pp)) * 1.0f * mvs + 0.08f + crouchT * 1.2f
            a = lerp(a, -0.5f + sd * 0.3f - 0.2f * max(0f, vzN), airW)
            kn = lerp(kn, 0.9f, airW)
            val trail = max(rollW, dashW)
            a = lerp(a, 0.95f + sd * 0.12f, trail)
            kn = lerp(kn, 0.5f, trail)
            if (tuck > 0.01f) {
                a = lerp(a, -1.2f + sd * 0.1f, tuck)
                kn = lerp(kn, 1.6f, tuck)
            }
            leg(sd, a, kn, hipY, 0.08f + 0.18f * airW)
        }

        push()
        translate(0f, hipY, 0f)
        rotX(ps[0] - hitKick * 0.3f)
        push()
        torso()
        mark(0f, 3.4f, 0f, 5)
        tail(ps[10], wag, 0.05f)
        arm(1f, ps[1], ps[3], ps[4])
        arm(-1f, ps[2], ps[3], ps[4])
        head(ps[5] - ps[0] * 0.5f, ps[7], ps[8], 0.04f * sin(clock * 1.7f) + 0.2f * ps[9], ps[9] * 1.0f)
        pop()
        pop()

        // ── chão: anel elétrico nas skills de descarga ──
        if (sk == 1 || sk == 4) {
            val g = if (u < REL[sk]) ease(u / REL[sk]) else 1f - ease((u - REL[sk]) / (1f - REL[sk]))
            val rr = (9f + 11f * g) * rs
            p.style = Paint.Style.STROKE
            p.strokeWidth = max(1.6f, rs * 1.4f)
            p.color = VoxelArt.alpha(ZAP, 0.8f * g)
            c.drawOval(fx - rr, fy - rr * SINP, fx + rr, fy + rr * SINP, p)
            p.color = VoxelArt.alpha(ZAP_L, 0.6f * g)
            c.drawOval(fx - rr * 0.65f, fy - rr * 0.65f * SINP, fx + rr * 0.65f, fy + rr * 0.65f * SINP, p)
            p.style = Paint.Style.FILL
        }

        flushZ(c)

        // ── bolas de energia nas mãos ──
        val zp = ps[11]
        if (zp > 0.05f) {
            val a = zp.coerceIn(0f, 1f)
            glowBall(c, p, mkX[0], mkY[0], (0.9f + 1.5f * a) * rs, a)
            glowBall(c, p, mkX[1], mkY[1], (0.9f + 1.5f * a) * rs, a)
        }
        // bochechas carregando
        if (ps[8] > 0.4f) {
            cheekT -= dt
            if (cheekT <= 0f) {
                cheekT = 0.06f
                for (k in 2..3) {
                    emitSpark(
                        mkX[k], mkY[k], (rnd.nextFloat() - 0.5f) * rs * 40f, -rs * (6f + rnd.nextFloat() * 16f),
                        0.22f + rnd.nextFloat() * 0.15f, rs * 30f, if (rnd.nextFloat() > 0.5f) ZAP_L else ZAP, 0.9f
                    )
                }
            }
        }
        // cauda de aço faiscando
        if (steel > 0.3f) {
            tailT -= dt
            if (tailT <= 0f) {
                tailT = 0.05f
                emitSpark(
                    mkX[4], mkY[4], (rnd.nextFloat() - 0.5f) * rs * 30f, (rnd.nextFloat() - 0.5f) * rs * 30f,
                    0.2f, 0f, if (rnd.nextFloat() > 0.5f) WHITE else STEEL_D, 1.0f
                )
            }
        }
        // arcos de energia em volta do corpo
        if (zp > 0.08f) {
            val arcs = (zp * 3f).toInt() + (if (rnd.nextFloat() < (zp * 3f) % 1f) 1 else 0)
            for (i in 0 until arcs) {
                val ang = rnd.nextFloat() * TWO_PI
                val r0 = (5f + rnd.nextFloat() * 3f) * rs
                val r1 = r0 + (9f + rnd.nextFloat() * 11f) * rs * (0.5f + zp)
                bolt(
                    c, p, mkX[5] + cos(ang) * r0, mkY[5] + sin(ang) * r0 * 0.9f,
                    mkX[5] + cos(ang) * r1, mkY[5] + sin(ang) * r1 * 0.9f, max(1.4f, rs * 0.9f), 0.9f
                )
            }
        }
        // esfera elétrica da Investida Trovão
        if (ball > 0.05f) {
            val r = 15f * rs * ball * (0.92f + 0.08f * sin(clock * 40f))
            p.style = Paint.Style.FILL
            p.color = VoxelArt.alpha(ZAP, 0.10f * ball)
            c.drawCircle(mkX[5], mkY[5], r * 1.2f, p)
            p.color = VoxelArt.alpha(ZAP_L, 0.14f * ball)
            c.drawCircle(mkX[5], mkY[5], r, p)
            for (i in 0..2) {
                val a0 = rnd.nextFloat() * TWO_PI
                bolt(
                    c, p, mkX[5] + cos(a0) * r, mkY[5] + sin(a0) * r * 0.9f,
                    mkX[5] + cos(a0 + 1.6f) * r, mkY[5] + sin(a0 + 1.6f) * r * 0.9f, max(1.3f, rs * 0.8f), 0.9f * ball
                )
            }
        }
        // rastros de velocidade do Ataque Rápido e da Investida
        val streak = max(rollW, ball * dashW)
        if (streak > 0.3f) {
            val dx = sin(yaw)
            val dy = cos(yaw) * SINP
            p.style = Paint.Style.STROKE
            p.strokeCap = Paint.Cap.ROUND
            for (i in 0..3) {
                val off = (i - 1.5f) * 3.2f * rs
                val sx0 = mkX[5] - dy * off - dx * 6f * rs
                val sy0 = mkY[5] + dx * off - dy * 6f * rs + (rnd.nextFloat() - 0.5f) * 2f * rs
                val len = (16f + rnd.nextFloat() * 14f) * rs * streak
                p.strokeWidth = max(1.4f, rs * 1.3f)
                p.color = VoxelArt.alpha(if (i % 2 == 0) WHITE else ZAP_L, 0.75f * streak)
                c.drawLine(sx0, sy0, sx0 - dx * len, sy0 - dy * len, p)
            }
            p.strokeCap = Paint.Cap.BUTT
            p.style = Paint.Style.FILL
        }
        // clarão no instante do golpe
        if (flashV > 0.02f) {
            val k = flashV
            p.style = Paint.Style.FILL
            p.color = VoxelArt.alpha(WHITE, 0.45f * k)
            c.drawCircle(mkX[5], mkY[5], rs * (5f + 12f * (1f - k)), p)
            p.color = VoxelArt.alpha(ZAP, 0.30f * k)
            c.drawCircle(mkX[5], mkY[5], rs * (9f + 16f * (1f - k)), p)
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
