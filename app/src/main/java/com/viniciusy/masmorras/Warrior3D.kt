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
import kotlin.math.acos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Mini motor 3D de cubos (estilo Minecraft Dungeons) desenhado em Canvas.
 * O Guerreiro é um esqueleto de partes articuladas (pernas com joelho, quadril/peito com
 * contra-rotação, cabeça, braços, escudo, espada, capa, penacho e tabardo em molas).
 * Cada cubo tem 3 faces visíveis com iluminação própria.
 */
object Warrior3D {
    private val V = VoxelArt

    private const val MAX = 640
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

    // paleta
    private val STEEL = 0xFFAEB9C2.toInt()
    private val STEEL_L = 0xFFD9E1E7.toInt()
    private val STEEL_D = 0xFF6F7C86.toInt()
    private val GOLD = 0xFFF2B72E.toInt()
    private val RED = 0xFFC62828.toInt()
    private val RED_D = 0xFF8E1B1B.toInt()
    private val LEATHER = 0xFF5A3A26.toInt()
    private val LEATHER_L = 0xFF7A5238.toInt()
    private val LEATHER_D = 0xFF3A2416.toInt()
    private val SKIN = 0xFFF1C27D.toInt()
    private val SKIN_D = 0xFFC98F55.toInt()
    private val PANTS = 0xFF2F3A44.toInt()
    private val STEEL_DD = 0xFF4A555E.toInt()
    private val GOLD_D = 0xFFB88413.toInt()
    private val BLADE = 0xFFDDE6EC.toInt()
    private val EYE = 0xFF1B1B1B.toInt()
    private val CREAM = 0xFFF5ECD0.toInt()
    private val SHAFT = 0xFF8A5A33.toInt()
    private val SHAFT_L = 0xFFAD7842.toInt()
    private val SHAFT_D = 0xFF5E3B20.toInt()
    private val NAVY = 0xFF2B3440.toInt()
    private val GOLD_L = 0xFFFFE08A.toInt()
    private val RED_L = 0xFFE04545.toInt()
    private val FUR = 0xFFF0EBDF.toInt()
    private val FUR_D = 0xFFBAB2A2.toInt()
    private val BEARD = 0xFF5A3A22.toInt()
    private val CHAIN = 0xFF8A95A5.toInt()
    private val SCAR = 0xFFD9988A.toInt()
    private val WHITE = 0xFFFFFFFF.toInt()
    // gema e runa pulsam (atualizadas a cada quadro em draw)
    private var gemC = 0xFFFF3A44.toInt()
    private var runeC = 0xFFFFA860.toInt()

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

    // estado externo (preenchido pelo Player antes de desenhar)
    var rollP = -1f
    var inAir = false
    var airVz = 0f
    var swing = 0       // 0 corte diagonal, 1 corte vertical (volta), 2 estocada, 3 golpe forte
    var charge = -1f    // 0..1 enquanto segura o ataque; <0 = sem carga
    var airH = 0f       // altura (px) acima do chão
    var weapon = 0      // 0 espada e escudo, 1 lança (duas mãos)
    var spearGone = false // lança arremessada: mãos vazias

    // estado de animação
    private var yaw = 0f
    private var airW = 0f   // 0..1 suavizado: quanto o herói está "no ar"
    private var rollW = 0f  // 0..1 suavizado: quanto a pose de rolamento está ativa
    private var blend = 0f
    private var clock = 0f
    private var lastNs = 0L

    // rastro da espada
    private val trX = FloatArray(24)
    private val trY = FloatArray(24)
    private val trMx = FloatArray(24)
    private val trMy = FloatArray(24)
    private var trN = 0
    private val TRAIL_MAX = 10
    private var tipX = 0f
    private var tipY = 0f
    private var midX = 0f
    private var midY = 0f

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
            scrD[o + i] = wy * SINP + wz * COSP
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

    private fun projectTo(x: Float, y: Float, z: Float, out: Int) {
        val m = stack[top]
        val wx = m[0] * x + m[1] * y + m[2] * z + m[9]
        val wy = m[3] * x + m[4] * y + m[5] * z + m[10]
        val wz = m[6] * x + m[7] * y + m[8] * z + m[11]
        val px = ox + wx * rs
        val py = oy + (wz * SINP - wy * COSP) * rs
        if (out == 0) {
            tipX = px
            tipY = py
        } else {
            midX = px
            midY = py
        }
    }

    private fun flushPainter(c: Canvas, p: Paint) {
        for (i in 0 until n) order[i] = i
        for (i in 1 until n) {
            val oi = order[i]
            val k = keys[oi]
            var j = i - 1
            while (j >= 0 && keys[order[j]] > k) {
                order[j + 1] = order[j]
                j--
            }
            order[j + 1] = oi
        }
        val lw = max(0.7f, rs * 0.4f)
        p.strokeJoin = Paint.Join.ROUND
        for (ii in 0 until n) {
            val ci = order[ii]
            val o = ci * 8
            for (f in 0..5) {
                val col = faceCol[ci * 6 + f]
                if (col == 0) continue
                val b = f * 4
                path.reset()
                path.moveTo(scrX[o + CORN[b]], scrY[o + CORN[b]])
                path.lineTo(scrX[o + CORN[b + 1]], scrY[o + CORN[b + 1]])
                path.lineTo(scrX[o + CORN[b + 2]], scrY[o + CORN[b + 2]])
                path.lineTo(scrX[o + CORN[b + 3]], scrY[o + CORN[b + 3]])
                path.close()
                p.style = Paint.Style.FILL
                p.color = col
                c.drawPath(path, p)
                p.style = Paint.Style.STROKE
                p.strokeWidth = lw
                p.color = (V.shade(col, 0.55f) and 0x00FFFFFF) or 0xB0000000.toInt()
                c.drawPath(path, p)
            }
        }
        p.style = Paint.Style.FILL
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
        var ss = SS_MAX
        if (wpx * hpx * ss * ss > BUF_MAX) ss = sqrt(BUF_MAX.toFloat() / (wpx * hpx)) * 0.97f
        val rw = (wpx * ss).toInt() + 1
        val rh = (hpx * ss).toInt() + 1
        if (rw * rh > BUF_MAX) return
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
        c.drawBitmap(b, srcR, dstR, bmpPaint)
    }

    private fun emitDust(x: Float, y: Float, vx: Float, vy: Float, size: Float) {
        val i = dNext
        dNext = (dNext + 1) % ND
        dX[i] = x
        dY[i] = y
        dVx[i] = vx
        dVy[i] = vy
        dM[i] = 0.38f + kotlin.random.Random.nextFloat() * 0.2f
        dL[i] = dM[i]
        dS[i] = size
    }

    // ───────────── utilidades de animação ─────────────

    private fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t

    private fun ease(t: Float): Float {
        val x = t.coerceIn(0f, 1f)
        return x * x * (3f - 2f * x)
    }

    // ───────────── partes do corpo ─────────────

    private fun rgb(r: Int, g: Int, b: Int): Int = (0xFF shl 24) or (r shl 16) or (g shl 8) or b

    private fun leg(side: Float, a: Float, knee: Float, hipY: Float, foot: Float) {
        push()
            translate(side * 3.4f, hipY, 0f)
            rotX(a)
            cube(0f, -2.7f, 0f, 2.55f, 2.9f, 2.6f, PANTS)
            cube(0f, -2.2f, 0.35f, 2.75f, 2.5f, 2.65f, STEEL_D)
            cube(0f, -2f, 2.85f, 1.5f, 2.3f, 0.25f, STEEL)
            cube(0f, -1.8f, 3.1f, 0.6f, 1.8f, 0.12f, STEEL_L)
            cube(side * 2.85f, -2.4f, 0.1f, 0.3f, 2.3f, 2.4f, STEEL_L)
            cube(0f, -0.2f, 0f, 2.95f, 0.5f, 2.95f, GOLD)
            cube(0f, -4.9f, 2.7f, 1.9f, 0.3f, 0.2f, GOLD_D)
            cube(0f, -5.2f, 0f, 2.7f, 0.35f, 2.7f, GOLD_D)
            translate(0f, -5.6f, 0f)
            rotX(knee)
            cube(0f, 0.3f, 2.2f, 2.5f, 1.7f, 1.1f, GOLD)
            cube(0f, 0.3f, 3.2f, 1.6f, 1.1f, 0.4f, STEEL_L)
            cube(0f, 0.5f, 3.8f, 0.5f, 0.5f, 0.55f, STEEL_L)
            cube(0f, -2.7f, 0f, 2.35f, 2.8f, 2.4f, STEEL)
            cube(0f, -2.9f, 2.4f, 1.1f, 2.4f, 0.3f, STEEL_L)
            cube(0f, -1.7f, 2.7f, 1.6f, 0.25f, 0.18f, GOLD_D)
            cube(0f, -3.9f, 2.7f, 1.5f, 0.25f, 0.18f, GOLD_D)
            cube(side * 2.45f, -2.6f, 0.2f, 0.2f, 2.5f, 2f, STEEL_L)
            cube(0f, -5.2f, 0f, 2.6f, 0.4f, 2.65f, GOLD_D)
            translate(0f, -5.4f, 0f)
            rotX(foot)
            cube(0f, 0.2f, 1.2f, 2.7f, 1.5f, 3.7f, LEATHER)
            cube(0f, -1.2f, 1.3f, 2.85f, 0.3f, 3.9f, LEATHER_D)
            cube(0f, 0.9f, 4.3f, 2.3f, 0.9f, 0.9f, STEEL_D)
            cube(0f, 1.5f, 4f, 1.3f, 0.3f, 0.8f, STEEL)
            cube(0f, 1.9f, -0.1f, 2.9f, 0.45f, 2.9f, GOLD)
            cube(0f, 0.9f, -2.4f, 2f, 1f, 0.5f, LEATHER_D)
        pop()
    }

    private fun pauldron(side: Float) {
        cube(side * 0.8f, 1.5f, 0f, 3.1f, 1f, 3.2f, STEEL_L)
        cube(side * 1.5f, 0.1f, 0f, 2.8f, 0.9f, 3f, STEEL)
        cube(side * 2f, -1f, 0f, 2.4f, 0.8f, 2.7f, STEEL_D)
        cube(side * 3.3f, -1.8f, 0f, 2f, 0.6f, 2.5f, STEEL_DD)
        cube(side * 0.8f, 2.6f, 0f, 3.15f, 0.22f, 3.25f, GOLD)
        cube(side * 3.4f, 0.1f, 0f, 0.28f, 2f, 3.1f, GOLD)
        cube(side * 1.9f, 3.3f, 0f, 0.55f, 1f, 0.55f, STEEL_L)
        cube(side * 1.9f, 4.6f, 0f, 0.3f, 0.7f, 0.3f, STEEL_L)
        cube(side * 1.9f, 5.5f, 0f, 0.18f, 0.5f, 0.18f, WHITE, true)
        cube(side * 1f, 2.9f, 2.5f, 0.32f, 0.32f, 0.25f, GOLD_L)
        cube(side * 1f, 2.9f, -2.5f, 0.32f, 0.32f, 0.25f, GOLD_L)
    }

    private fun armBase(el: Float) {
        cube(0f, -2.7f, 0f, 2f, 2.9f, 2f, LEATHER_D)
        cube(0f, -1.4f, 0f, 2.2f, 0.4f, 2.2f, GOLD)
        cube(0f, -3.9f, 0f, 2.35f, 0.3f, 2.35f, STEEL_D)
        translate(0f, -5.7f, 0f)
        rotX(el)
        cube(0f, -0.1f, 0f, 2.15f, 0.9f, 2.15f, STEEL_D)
        cube(0f, 0.4f, -2.3f, 0.9f, 1f, 0.8f, STEEL)
        cube(0f, 0.5f, -3.1f, 0.4f, 0.5f, 0.6f, STEEL_L)
        cube(0f, -2.7f, 0f, 2.25f, 2.7f, 2.25f, STEEL)
        cube(0f, -2.7f, 2.3f, 1f, 2.2f, 0.25f, STEEL_L)
        cube(0f, -1.5f, -2.35f, 1.3f, 0.3f, 0.2f, GOLD_D)
        cube(0f, -3.9f, -2.35f, 1.3f, 0.3f, 0.2f, GOLD_D)
        cube(0f, -5.1f, 0f, 2.4f, 0.4f, 2.4f, GOLD)
        cube(0f, -6.1f, 0f, 1.85f, 1.3f, 1.85f, LEATHER_L)
        cube(0f, -6.2f, 1.7f, 1.5f, 0.5f, 0.4f, LEATHER)
        cube(0f, -5.6f, 0f, 2.1f, 0.5f, 2.1f, STEEL_D)
        cube(0f, -7.3f, 1.2f, 1.6f, 0.3f, 0.5f, GOLD_D)
    }

    private fun sword(glow: Float, wrist: Float) {
        translate(0f, -6.1f, 0f)
        rotX(1.9f + wrist)
        cube(0f, 0.4f, 0f, 0.85f, 2.2f, 0.85f, LEATHER)
        cube(0f, -0.6f, 0f, 0.95f, 0.18f, 0.95f, LEATHER_D)
        cube(0f, 0.4f, 0f, 0.95f, 0.18f, 0.95f, LEATHER_D)
        cube(0f, 1.4f, 0f, 0.95f, 0.18f, 0.95f, LEATHER_D)
        cube(0f, -2.5f, 0f, 1.3f, 0.8f, 1.3f, GOLD)
        cube(0f, -3.5f, 0f, 0.8f, 0.55f, 0.8f, GOLD_D)
        cube(0f, -2.6f, 1.35f, 0.5f, 0.4f, 0.3f, gemC, true)
        cube(0f, 2.5f, 0f, 1.5f, 0.4f, 1.5f, GOLD_D)
        cube(0f, 3.1f, 0f, 4.6f, 0.6f, 1.5f, GOLD)
        cube(-4.7f, 3.9f, 0f, 0.6f, 0.9f, 1.2f, GOLD)
        cube(4.7f, 3.9f, 0f, 0.6f, 0.9f, 1.2f, GOLD)
        cube(-5.5f, 4.9f, 0f, 0.5f, 0.7f, 1f, GOLD_D)
        cube(5.5f, 4.9f, 0f, 0.5f, 0.7f, 1f, GOLD_D)
        cube(0f, 3.2f, 1.6f, 1f, 0.8f, 0.3f, gemC, true)
        cube(0f, 14.4f, 0f, 1f, 10.9f, 1.7f, BLADE)
        cube(0f, 14.4f, 0f, 1.12f, 9.8f, 0.55f, STEEL)
        cube(0f, 14.4f, 1.9f, 0.55f, 10.4f, 0.28f, STEEL_L)
        cube(0f, 14.4f, -1.9f, 0.55f, 10.4f, 0.28f, STEEL_D)
        cube(0f, 26.2f, 0.2f, 0.8f, 0.9f, 1.2f, BLADE)
        cube(0f, 27.5f, 0.1f, 0.5f, 0.5f, 0.8f, STEEL_L)
        cube(0f, 28.4f, 0.1f, 0.28f, 0.4f, 0.45f, WHITE)
        val g = glow.coerceIn(0f, 1f)
        val gc = rgb(255, (150 + 80 * g).toInt(), (60 + 90 * g).toInt())
        cube(0f, 8.5f, 2.15f, 0.45f, 0.9f, 0.14f, gc, true)
        cube(0f, 12.1f, 2.15f, 0.45f, 0.9f, 0.14f, gc, true)
        cube(0f, 15.7f, 2.15f, 0.45f, 0.9f, 0.14f, gc, true)
        cube(0f, 19.3f, 2.15f, 0.45f, 0.9f, 0.14f, gc, true)
        cube(0f, 22.9f, 2.15f, 0.45f, 0.9f, 0.14f, gc, true)
        cube(0f, 14.4f, 2.1f, 0.2f, 10f, 0.1f, WHITE, true)
    }

    private fun rightArm(sh: Float, shZ: Float, el: Float, wrist: Float, glow: Float) {
        push()
        translate(-8.4f, 13.2f, 0f)
        pauldron(-1f)
        rotZ(shZ)
        rotX(sh)
        armBase(el)
        sword(glow, wrist)
        projectTo(0f, 27.5f, 0.2f, 0)
        projectTo(0f, 12f, 0.5f, 1)
        pop()
    }

    private fun leftArm(sh: Float, shZ: Float, el: Float) {
        push()
        translate(8.4f, 13.2f, 0f)
        pauldron(1f)
        rotZ(shZ)
        rotX(sh)
        armBase(el)
        translate(0f, -5.8f, 0f)
        rotX(-(sh + el))
        shield()
        pop()
    }

    private fun shield() {
        cube(0.3f, 0.6f, 0.6f, 4.8f, 5.4f, 0.9f, STEEL_DD)
        cube(0.3f, -5f, 0.6f, 3.6f, 1.2f, 0.9f, STEEL_DD)
        cube(0.3f, -6.5f, 0.6f, 2.2f, 0.6f, 0.9f, STEEL_DD)
        cube(0.3f, -7.4f, 0.6f, 0.9f, 0.4f, 0.9f, STEEL_DD)
        cube(0.3f, 0.6f, 1.25f, 4.1f, 4.8f, 0.6f, RED)
        cube(0.3f, -5f, 1.25f, 3f, 1f, 0.6f, RED)
        cube(0.3f, -6.4f, 1.25f, 1.7f, 0.5f, 0.6f, RED)
        cube(-2.4f, 2.6f, 1.3f, 1.5f, 2.8f, 0.55f, RED_L)
        cube(0.3f, 6.3f, 1.2f, 4.9f, 0.45f, 1f, GOLD)
        cube(-4.45f, 0.8f, 1.2f, 0.4f, 5.3f, 1f, GOLD)
        cube(5.05f, 0.8f, 1.2f, 0.4f, 5.3f, 1f, GOLD)
        cube(-3f, -4.9f, 1.2f, 0.4f, 1.4f, 1f, GOLD)
        cube(3.6f, -4.9f, 1.2f, 0.4f, 1.4f, 1f, GOLD)
        cube(-1.3f, -6.7f, 1.2f, 0.4f, 0.9f, 1f, GOLD)
        cube(1.9f, -6.7f, 1.2f, 0.4f, 0.9f, 1f, GOLD)
        cube(0.3f, -7.8f, 1.2f, 0.6f, 0.4f, 1f, GOLD)
        cube(0.3f, 0f, 1.95f, 0.7f, 4.6f, 0.2f, CREAM)
        cube(0.3f, 2f, 1.95f, 3.2f, 0.7f, 0.2f, CREAM)
        cube(0.3f, 1.2f, 2.15f, 2f, 2f, 0.45f, GOLD)
        cube(0.3f, 1.2f, 2.55f, 1.3f, 1.3f, 0.3f, GOLD_L)
        cube(0.3f, 1.2f, 2.85f, 0.65f, 0.65f, 0.25f, gemC, true)
        cube(-3.6f, 5.3f, 1.95f, 0.4f, 0.4f, 0.25f, GOLD_L)
        cube(4.2f, 5.3f, 1.95f, 0.4f, 0.4f, 0.25f, GOLD_L)
        cube(-3.6f, -3.3f, 1.95f, 0.4f, 0.4f, 0.25f, GOLD_L)
        cube(4.2f, -3.3f, 1.95f, 0.4f, 0.4f, 0.25f, GOLD_L)
        cube(0.3f, -8.9f, 0.6f, 0.5f, 0.7f, 0.8f, STEEL_L)
        cube(0.3f, 0.6f, -0.4f, 2.2f, 3f, 0.45f, LEATHER_D)
    }

    private fun head(turn: Float, tilt: Float, p0: Float, p1: Float) {
        push()
            translate(0f, 14.2f, 0f)
            rotY(turn)
            rotX(tilt)
            cube(0f, 0.3f, 0f, 2.3f, 1f, 2.3f, SKIN_D)
            cube(0f, -0.2f, 0f, 3.9f, 1.3f, 3.7f, STEEL_D)
            cube(0f, 0.95f, 0f, 4f, 0.3f, 3.8f, GOLD)
            cube(0f, 5.2f, 0f, 5f, 5f, 4.8f, SKIN)
            cube(-2f, 4.7f, 4.85f, 1.05f, 1f * eyeOpen, 0.12f, EYE)
            cube(2f, 4.7f, 4.85f, 1.05f, 1f * eyeOpen, 0.12f, EYE)
            cube(-2.1f, 4.9f, 4.95f, 0.55f, 0.4f * eyeOpen, 0.1f, WHITE, true)
            cube(1.9f, 4.9f, 4.95f, 0.55f, 0.4f * eyeOpen, 0.1f, WHITE, true)
            cube(0f, 3.3f, 5f, 0.6f, 0.7f, 0.3f, SKIN_D)
            cube(0f, 1.7f, 4.85f, 1.5f, 0.3f, 0.12f, SKIN_D)
            cube(-2f, 6f, 4.95f, 1.5f, 0.4f, 0.15f, BEARD)
            cube(2f, 6f, 4.95f, 1.5f, 0.4f, 0.15f, BEARD)
            cube(0f, 1.9f, 4.7f, 3.2f, 0.9f, 0.5f, BEARD)
            cube(0f, 0.8f, 4.4f, 2.5f, 0.8f, 0.5f, BEARD)
            cube(0f, 2.7f, 4.9f, 1.5f, 0.3f, 0.25f, BEARD)
            cube(-4.9f, 2.2f, 2.4f, 0.2f, 2.2f, 2.2f, BEARD)
            cube(4.9f, 2.2f, 2.4f, 0.2f, 2.2f, 2.2f, BEARD)
            cube(-3f, 3.4f, 4.9f, 0.18f, 1.7f, 0.12f, SCAR)
            cube(0f, 8.6f, 0f, 5.5f, 2.9f, 5.4f, STEEL)
            cube(0f, 11f, 0f, 4.7f, 0.8f, 4.7f, STEEL_L)
            cube(0f, 11.8f, 0f, 3.1f, 0.5f, 3.1f, STEEL_L)
            cube(0f, 6.3f, 0f, 5.7f, 0.4f, 5.5f, GOLD)
            cube(0f, 7f, 5.4f, 5.6f, 0.55f, 0.45f, STEEL_D)
            cube(0f, 4.8f, 5.3f, 0.55f, 2.4f, 0.4f, STEEL)
            cube(-4.2f, 7f, 5.75f, 0.32f, 0.32f, 0.2f, GOLD_L)
            cube(4.2f, 7f, 5.75f, 0.32f, 0.32f, 0.2f, GOLD_L)
            cube(0f, 8.7f, 5.5f, 1.25f, 1.25f, 0.12f, GOLD)
            cube(0f, 8.7f, 5.6f, 0.8f, 0.8f, 0.15f, gemC, true)
            cube(-5.4f, 4.6f, 0.3f, 0.55f, 2.5f, 3.9f, STEEL)
            cube(-5.7f, 3f, 0.3f, 0.35f, 0.9f, 2.6f, STEEL_D)
            cube(-5.75f, 4.6f, 3.9f, 0.2f, 2.4f, 0.2f, GOLD)
            cube(-5.9f, 7.8f, 0f, 0.3f, 1f, 3f, GOLD)
            cube(-6.6f, 9.2f, -0.5f, 0.5f, 1f, 2.6f, GOLD)
            cube(-7.6f, 10.6f, -0.9f, 0.4f, 0.9f, 2f, GOLD)
            cube(-8.4f, 12f, -1.2f, 0.35f, 0.8f, 1.4f, GOLD_D)
            cube(-9f, 13.2f, -1.5f, 0.3f, 0.7f, 0.9f, GOLD_D)
            cube(5.4f, 4.6f, 0.3f, 0.55f, 2.5f, 3.9f, STEEL)
            cube(5.7f, 3f, 0.3f, 0.35f, 0.9f, 2.6f, STEEL_D)
            cube(5.75f, 4.6f, 3.9f, 0.2f, 2.4f, 0.2f, GOLD)
            cube(5.9f, 7.8f, 0f, 0.3f, 1f, 3f, GOLD)
            cube(6.6f, 9.2f, -0.5f, 0.5f, 1f, 2.6f, GOLD)
            cube(7.6f, 10.6f, -0.9f, 0.4f, 0.9f, 2f, GOLD)
            cube(8.4f, 12f, -1.2f, 0.35f, 0.8f, 1.4f, GOLD_D)
            cube(9f, 13.2f, -1.5f, 0.3f, 0.7f, 0.9f, GOLD_D)
            cube(0f, 11.7f, 0f, 1f, 0.9f, 5.3f, STEEL_D)
            cube(0f, 12.4f, 0f, 0.6f, 0.5f, 4.8f, GOLD)
            cube(0f, 11.9f, 3f, 0.5f, 0.4f, 1f, GOLD)
            push()
                translate(0f, 12.4f, -4.5f)
                rotX(0.45f + p0)
                cube(0f, 0.8f, -0.6f, 1.1f, 0.9f, 2.8f, RED_L)
                cube(0f, 0.2f, 0.3f, 1.3f, 1f, 0.8f, GOLD)
                translate(0f, 0.4f, -3.4f)
                rotX(0.5f + p1)
                cube(0f, 0f, -1.6f, 1f, 0.9f, 2.8f, RED)
                translate(0f, 0f, -4.4f)
                rotX(0.5f + p1 * 1.3f)
                cube(0f, -0.3f, -1.2f, 0.9f, 0.8f, 2f, RED_D)
            pop()
        pop()
    }

    private fun torso(chestTw: Float, c0: Float, c1: Float, c2: Float, tabA: Float, breath: Float) {
        cube(0f, 0.6f, 0f, 5.6f, 1.4f, 3.5f, LEATHER)
        cube(0f, 0.8f, 3.55f, 1.9f, 1.4f, 0.5f, GOLD)
        cube(0f, 0.8f, 4f, 1.1f, 0.9f, 0.25f, GOLD_L)
        cube(0f, 0.8f, 4.25f, 0.5f, 0.5f, 0.15f, gemC, true)
        cube(-5.7f, 0.4f, 0f, 0.6f, 1.3f, 3.2f, LEATHER_D)
        cube(-3.6f, 0.8f, 3.6f, 0.4f, 0.4f, 0.2f, GOLD)
        cube(-3.2f, -1.6f, 3.4f, 2.4f, 2.8f, 0.45f, STEEL_D)
        cube(-3.2f, -1.2f, 3.85f, 2f, 2.2f, 0.25f, STEEL)
        cube(-3.2f, -3.2f, 3.85f, 1.7f, 0.45f, 0.3f, GOLD)
        cube(-5.9f, -1.6f, 0.5f, 0.4f, 2.6f, 2.6f, STEEL_D)
        cube(-6.2f, -1f, 0.5f, 0.15f, 1.8f, 2.2f, GOLD_D)
        cube(5.7f, 0.4f, 0f, 0.6f, 1.3f, 3.2f, LEATHER_D)
        cube(3.6f, 0.8f, 3.6f, 0.4f, 0.4f, 0.2f, GOLD)
        cube(3.2f, -1.6f, 3.4f, 2.4f, 2.8f, 0.45f, STEEL_D)
        cube(3.2f, -1.2f, 3.85f, 2f, 2.2f, 0.25f, STEEL)
        cube(3.2f, -3.2f, 3.85f, 1.7f, 0.45f, 0.3f, GOLD)
        cube(5.9f, -1.6f, 0.5f, 0.4f, 2.6f, 2.6f, STEEL_D)
        cube(6.2f, -1f, 0.5f, 0.15f, 1.8f, 2.2f, GOLD_D)
        cube(-4.6f, -1.2f, 3.9f, 1.5f, 1.4f, 0.7f, LEATHER_L)
        cube(-4.6f, -0.2f, 4.3f, 1.6f, 0.4f, 0.3f, LEATHER_D)
        cube(-4.6f, -0.9f, 4.65f, 0.4f, 0.5f, 0.12f, GOLD)
        cube(4.8f, -1.8f, 4.2f, 0.8f, 1.3f, 0.8f, gemC, true)
        cube(4.8f, -0.3f, 4.2f, 0.5f, 0.35f, 0.5f, LEATHER)
        cube(4.8f, -0.6f, 4.2f, 1f, 0.22f, 1f, GOLD_D)
        cube(0f, -1.8f, -3.4f, 4.8f, 2.4f, 0.35f, CHAIN)
        push()
            translate(0f, -0.6f, 3.9f)
            rotX(tabA)
            cube(0f, -2.6f, 0.2f, 2.4f, 2.8f, 0.3f, RED)
            cube(0f, -2f, 0.55f, 1f, 1f, 0.15f, GOLD_L)
            cube(0f, -2f, 0.7f, 0.45f, 0.45f, 0.1f, gemC, true)
            cube(0f, -5.4f, 0.2f, 2.4f, 0.4f, 0.4f, GOLD)
            cube(0f, -5.9f, 0.2f, 1.8f, 0.3f, 0.3f, RED_D)
            cube(-2.2f, -3f, 0.45f, 0.2f, 2.5f, 0.12f, GOLD)
            cube(2.2f, -3f, 0.45f, 0.2f, 2.5f, 0.12f, GOLD)
        pop()
        cube(0f, 3.8f, 0f, 5.2f, 2.2f, 3.5f, STEEL_D)
        cube(0f, 2.6f, 3.4f, 4.8f, 0.45f, 0.4f, STEEL)
        cube(0f, 4.2f, 3.4f, 4.6f, 0.45f, 0.4f, STEEL)
        cube(0f, 5.8f, 3.4f, 4.4f, 0.45f, 0.4f, STEEL)
        cube(0f, 3.3f, 3.75f, 4.6f, 0.12f, 0.12f, GOLD_D)
        cube(0f, 4.9f, 3.75f, 4.4f, 0.12f, 0.12f, GOLD_D)
        cube(0f, 6.5f, 3.75f, 4.2f, 0.12f, 0.12f, GOLD_D)
        cube(-5.3f, 3.8f, 0f, 0.25f, 2.2f, 3f, GOLD_D)
        cube(5.3f, 3.8f, 0f, 0.25f, 2.2f, 3f, GOLD_D)
        push()
            rotY(chestTw)
            translate(0f, breath, 0f)
            cube(0f, 9.8f, 0f, 6.9f, 5f, 3.9f, STEEL)
            cube(0f, 10.5f, 3.9f, 5.5f, 3.8f, 0.6f, STEEL_L)
            cube(0f, 10.5f, 4.45f, 0.5f, 3.8f, 0.2f, STEEL)
            cube(-2.8f, 11.6f, 4.5f, 2f, 1.6f, 0.12f, WHITE)
            cube(2.8f, 11.6f, 4.5f, 2f, 1.6f, 0.12f, WHITE)
            cube(0f, 13.9f, 3.7f, 6.3f, 0.5f, 0.8f, GOLD)
            cube(0f, 7.4f, 3.9f, 6.2f, 0.35f, 0.7f, GOLD)
            cube(0f, 10.2f, 4.65f, 1.6f, 1.6f, 0.15f, GOLD)
            cube(0f, 10.2f, 4.85f, 0.95f, 0.95f, 0.12f, RED_D)
            cube(0f, 10.2f, 4.95f, 0.5f, 0.5f, 0.1f, gemC, true)
            cube(0f, 9f, 4.55f, 0.8f, 1f, 0.12f, RED)
            cube(0f, 8.1f, 4.55f, 0.35f, 0.6f, 0.1f, GOLD_D)
            cube(-5.6f, 8.2f, 4.1f, 0.3f, 0.3f, 0.2f, GOLD_L)
            cube(-5.6f, 12.8f, 4.1f, 0.3f, 0.3f, 0.2f, GOLD_L)
            cube(5.6f, 8.2f, 4.1f, 0.3f, 0.3f, 0.2f, GOLD_L)
            cube(5.6f, 12.8f, 4.1f, 0.3f, 0.3f, 0.2f, GOLD_L)
            cube(0f, 10f, -3.95f, 5.6f, 4.4f, 0.5f, STEEL_D)
            cube(0f, 10.5f, -4.35f, 2.6f, 3.2f, 0.2f, STEEL)
            cube(0f, 10.5f, -4.6f, 0.5f, 4.6f, 0.2f, GOLD_D)
            cube(0f, 14.4f, 0f, 3.4f, 0.9f, 3f, STEEL_D)
            cube(0f, 14.2f, 0f, 7.6f, 0.7f, 4.4f, FUR)
            cube(0f, 13.7f, 0.3f, 7.9f, 0.5f, 4.6f, FUR_D)
            cube(-6.6f, 13.3f, 4.4f, 1.1f, 0.9f, 0.5f, FUR_D)
            cube(-6.6f, 13.5f, -4.5f, 1.1f, 0.9f, 0.5f, FUR_D)
            cube(-4.4f, 13.3f, 4.4f, 1.1f, 0.9f, 0.5f, FUR)
            cube(-4.4f, 13.5f, -4.5f, 1.1f, 0.9f, 0.5f, FUR)
            cube(-2.2f, 13.3f, 4.4f, 1.1f, 0.9f, 0.5f, FUR_D)
            cube(-2.2f, 13.5f, -4.5f, 1.1f, 0.9f, 0.5f, FUR_D)
            cube(0f, 13.3f, 4.4f, 1.1f, 0.9f, 0.5f, FUR)
            cube(0f, 13.5f, -4.5f, 1.1f, 0.9f, 0.5f, FUR)
            cube(2.2f, 13.3f, 4.4f, 1.1f, 0.9f, 0.5f, FUR_D)
            cube(2.2f, 13.5f, -4.5f, 1.1f, 0.9f, 0.5f, FUR_D)
            cube(4.4f, 13.3f, 4.4f, 1.1f, 0.9f, 0.5f, FUR)
            cube(4.4f, 13.5f, -4.5f, 1.1f, 0.9f, 0.5f, FUR)
            cube(6.6f, 13.3f, 4.4f, 1.1f, 0.9f, 0.5f, FUR_D)
            cube(6.6f, 13.5f, -4.5f, 1.1f, 0.9f, 0.5f, FUR_D)
            cube(-7.6f, 13.9f, 0f, 1.8f, 1f, 2.6f, FUR)
            cube(7.6f, 13.9f, 0f, 1.8f, 1f, 2.6f, FUR_D)
            push()
                translate(0f, 13.6f, -4.1f)
                rotX(c0)
                cube(0f, -2.9f, -0.4f, 6f, 3.1f, 0.4f, RED_D)
                cube(0f, 0.9f, 0.1f, 6.7f, 0.95f, 1.2f, STEEL_D)
                cube(-5.2f, 0.6f, 0.1f, 1f, 1f, 1.4f, GOLD)
                cube(5.2f, 0.6f, 0.1f, 1f, 1f, 1.4f, GOLD)
                cube(-5.2f, 0.9f, 1.5f, 0.6f, 0.6f, 0.3f, GOLD_L)
                cube(5.2f, 0.9f, 1.5f, 0.6f, 0.6f, 0.3f, GOLD_L)
                translate(0f, -5.8f, 0f)
                rotX(c1)
                cube(0f, -2.9f, -0.4f, 6.3f, 3f, 0.4f, RED)
                cube(0f, -2.5f, -0.9f, 1.8f, 1.8f, 0.15f, GOLD)
                cube(0f, -2.5f, -1f, 1f, 1f, 0.12f, RED_D)
                cube(0f, -2.5f, -1.1f, 0.45f, 0.45f, 0.1f, gemC, true)
                cube(-6.2f, -2.9f, -0.4f, 0.25f, 3f, 0.5f, GOLD_D)
                cube(6.2f, -2.9f, -0.4f, 0.25f, 3f, 0.5f, GOLD_D)
                translate(0f, -5.8f, 0f)
                rotX(c2)
                cube(0f, -2.6f, -0.4f, 6f, 2.6f, 0.4f, RED_D)
                cube(0f, -5.3f, -0.4f, 6f, 0.4f, 0.5f, GOLD)
                cube(-6f, -2.6f, -0.4f, 0.25f, 2.6f, 0.5f, GOLD_D)
                cube(6f, -2.6f, -0.4f, 0.25f, 2.6f, 0.5f, GOLD_D)
                cube(-4.2f, -6.7f, -0.4f, 1.3f, 1.2f, 0.4f, RED)
                cube(-1.4f, -6.7f, -0.4f, 1.3f, 1.2f, 0.4f, RED_D)
                cube(1.4f, -6.7f, -0.4f, 1.3f, 1.2f, 0.4f, RED)
                cube(4.2f, -6.7f, -0.4f, 1.3f, 1.2f, 0.4f, RED_D)
            pop()
        pop()
    }

    // ───────────── lança (duas mãos) ─────────────

    private var wShown = 0
    private var swapU = -1f
    private val sPo = FloatArray(11)
    private val sS = FloatArray(11)
    private val lastSp = FloatArray(11)
    private val sI = FloatArray(11)
    private val tR = FloatArray(3)
    private val tL = FloatArray(3)
    private val ikO = FloatArray(3)

    // chaves: mão de trás (x,y,z), mão da frente (x,y,z), giro do peito, agachar, inclinar, quadril, deslize da haste
    private val SK_I = floatArrayOf(-2.911f, 9.65f, 5.415f, 3.053f, 12.516f, 10.349f, -0.537f, 0.1f, 0f, 0.317f, 0f)
    // agachado/mergulho: mesmas poses de antes, deslocadas junto com a nova guarda
    private val SK_TUCK = floatArrayOf(-3.111f, 8.15f, 5.915f, 3.153f, 10.016f, 9.349f, 0f, 0.3f, 0f, 0f, -2f)
    private val SK_DIP = floatArrayOf(-3.111f, 5.15f, 4.915f, 2.953f, 6.516f, 8.849f, 0f, 0.5f, 0f, 0f, -4f)
    // preparação (W), meio do golpe (M) e fim do golpe (E): a mão da frente aponta a lança para a frente
    // poses geradas por tools/spear_keys.py: as duas mãos sempre agarram a haste (deslize limitado) e ficam ao alcance dos braços
    // 0 estocada rápida, 1 varrida lateral, 2 investida forte, 3 arremesso
    private val SK_W = arrayOf(
        floatArrayOf(-5.698f, 10.88f, 0.694f, 2.037f, 11.224f, 4.438f, -1f, 0.3f, -0.15f, -0.5f, -2f),
        floatArrayOf(-2.593f, 11.01f, 2.291f, 4.401f, 11.182f, 7.292f, -2.1f, 0.35f, -0.1f, -1.6f, 0f),
        floatArrayOf(-6.966f, 11.2f, 1.519f, 1.561f, 10.77f, 2.554f, -1.25f, 0.62f, -0.2f, -0.7f, -3f),
        floatArrayOf(-9.3f, 20.0f, -5.0f, 3.0f, 14.5f, 9.0f, -0.2f, 0.35f, -0.3f, -0.4f, -12f)
    )
    private val SK_M = arrayOf(
        floatArrayOf(-1.79f, 11.06f, 2.884f, 5.15f, 11.404f, 7.952f, -0.9f, 0.45f, 0.2f, -0.3f, 1.5f),
        floatArrayOf(-1.78f, 11f, 2.873f, 5.215f, 11f, 7.875f, -0.95f, 0.5f, 0.15f, -0.6f, 1f),
        floatArrayOf(-1.789f, 11f, 2.885f, 5.156f, 11f, 7.957f, -0.9f, 0.78f, 0.3f, -0.35f, 2f),
        floatArrayOf(-8.5f, 20.5f, 2.0f, 1.0f, 14.0f, 10.5f, 0f, 0.5f, 0.1f, 0f, -12f)
    )
    private val SK_E = arrayOf(
        floatArrayOf(0.916f, 11.1f, 5.107f, 7.651f, 11.272f, 10.452f, -0.9f, 0.55f, 0.35f, 0.1f, 4f),
        floatArrayOf(-2.187f, 11.04f, 2.581f, 4.803f, 11.384f, 7.58f, 0.15f, 0.45f, 0.2f, 0.1f, 1f),
        floatArrayOf(1.362f, 11.44f, 5.321f, 8.182f, 12.127f, 10.515f, -0.92f, 0.85f, 0.55f, 0.15f, 5f),
        floatArrayOf(-7.0f, 19.0f, 9.0f, 2.0f, 12.5f, 9.0f, 0.2f, 0.65f, 0.45f, 0.3f, -12f)
    )
    // tempos (preparação, golpe, fim) — manter igual a Player.strikeDelay()
    private val SK_T = arrayOf(
        floatArrayOf(0.10f, 0.30f, 0.55f),
        floatArrayOf(0.14f, 0.38f, 0.62f),
        floatArrayOf(0.20f, 0.42f, 0.66f),
        floatArrayOf(0.08f, 0.30f, 0.62f)
    )
    private val SK_L = floatArrayOf(3.5f, 2.5f, 7.0f, 4.5f)
    // arremesso: orientação da haste (guinada, inclinação) nas poses W, M, E — independe da mão da frente
    private val TH_W = floatArrayOf(0.20f, 0.12f)
    private val TH_M = floatArrayOf(0.0f, 0.06f)
    private val TH_E = floatArrayOf(0.10f, 0.0f)
    private var thMix = 0f
    private var thPsi = 0f
    private var thTheta = 0f
    // mãos fechadas em guarda (quando a lança está arremessada)
    private val SK_FIST = floatArrayOf(-4.4f, 10.0f, 5.6f, 4.4f, 10.0f, 5.6f, -0.1f, 0.3f, 0f, 0.1f, 0f)

    private fun mixS(a: FloatArray, b: FloatArray, u: Float) {
        for (i in 0..10) sPo[i] = lerp(a[i], b[i], u)
    }

    private fun spearPose(attacking: Boolean, t: Float, sw: Int, tW: Float, tS: Float, tH: Float, phase: Float, mvs: Float) {
        System.arraycopy(SK_I, 0, sI, 0, 11)
        sI[1] += sin(clock * 1.3f) * 0.25f * (1f - mvs) + sin(phase * 2f) * 0.5f * mvs
        sI[4] += sin(clock * 1.3f) * 0.25f * (1f - mvs) + sin(phase * 2f) * 0.5f * mvs
        sI[2] += sin(phase) * 0.8f * mvs
        sI[5] += sin(phase) * 0.8f * mvs
        if (attacking) {
            val w = SK_W[sw]
            val e = SK_E[sw]
            if (t < tW) {
                mixS(sS, w, ease(t / tW))
            } else if (t < tS) {
                var u = (t - tW) / (tS - tW)
                u = u * u * (3f - 2f * u)
                // curva por três pontos (preparação, meio, fim) para a ponta seguir um arco limpo
                val m = SK_M[sw]
                val cw = 2f * (u - 0.5f) * (u - 1f)
                val cm = -4f * u * (u - 1f)
                val ce = 2f * u * (u - 0.5f)
                for (i in 0..10) sPo[i] = w[i] * cw + m[i] * cm + e[i] * ce
            } else if (t < tH) {
                val u = (t - tS) / (tH - tS)
                val o = 0.05f * sin(u * PI.toFloat())
                for (i in 0..10) sPo[i] = e[i] + (e[i] - w[i]) * o
            } else {
                mixS(e, sI, ease((t - tH) / (1f - tH)))
            }
        } else if (chargeW > 0.01f) {
            mixS(sI, SK_W[3], ease(chargeW))
            val ch = max(0f, charge)
            sPo[2] += sin(clock * 47f) * 0.12f * ch - 1.3f * ch
            sPo[5] += sin(clock * 43f) * 0.12f * ch - 0.7f * ch
            sPo[7] += 0.12f * ch
        } else {
            System.arraycopy(sI, 0, sPo, 0, 11)
        }
        // orientação do arremesso (só segurar/lançar)
        thMix = 0f
        if (attacking && sw == 3) {
            if (t < tW) {
                thPsi = TH_W[0]; thTheta = TH_W[1]
            } else if (t < tS) {
                var u = (t - tW) / (tS - tW)
                u = u * u * (3f - 2f * u)
                val cw = 2f * (u - 0.5f) * (u - 1f)
                val cm = -4f * u * (u - 1f)
                val ce = 2f * u * (u - 0.5f)
                thPsi = TH_W[0] * cw + TH_M[0] * cm + TH_E[0] * ce
                thTheta = TH_W[1] * cw + TH_M[1] * cm + TH_E[1] * ce
            } else {
                thPsi = TH_E[0]; thTheta = TH_E[1]
            }
            thMix = if (t < tH) 1f else 1f - ease((t - tH) / (1f - tH))
        } else if (chargeW > 0.01f && !attacking) {
            thPsi = TH_W[0]; thTheta = TH_W[1]
            thMix = ease(chargeW)
            // a mão de trás sai primeiro para o lado, depois sobe: a lança nunca cruza a cabeça
            sPo[0] = lerp(sI[0], SK_W[3][0], ease(min(1f, chargeW * 2.2f)))
        }
        if (spearEmptyW > 0.01f && !attacking) {
            for (i in 0..10) sPo[i] = lerp(sPo[i], SK_FIST[i], ease(spearEmptyW))
        }
    }

    /** Limita o alvo da mão ao alcance do braço (ombro em sx,13.2,0). */
    private fun clampReach(sx: Float, x: Float, y: Float, z: Float, out: FloatArray) {
        val dx = x - sx
        val dy = y - 13.2f
        val d = sqrt(dx * dx + dy * dy + z * z)
        val mx = 11.3f
        val k = if (d > mx) mx / d else 1f
        out[0] = sx + dx * k
        out[1] = 13.2f + dy * k
        out[2] = z * k
    }

    /** Dois ossos (5.7 + 6.1) até o alvo relativo ao ombro → sh, shZ, el. */
    private fun ik(x: Float, y: Float, z: Float) {
        val l1 = 5.7f
        val l2 = 6.1f
        val h = hypot(x, y).coerceAtLeast(0.0001f)
        val shZ = atan2(x, -y)
        val d = hypot(h, z).coerceIn(abs(l1 - l2) + 0.5f, (l1 + l2) * 0.985f)
        val phi = atan2(-z, h)
        val a1 = acos(((l1 * l1 + d * d - l2 * l2) / (2f * l1 * d)).coerceIn(-1f, 1f))
        val a2 = acos(((l2 * l2 + d * d - l1 * l1) / (2f * l2 * d)).coerceIn(-1f, 1f))
        ikO[0] = phi + a1
        ikO[1] = shZ
        ikO[2] = -(a1 + a2)
    }

    /** Aplica uma base (colunas x, y, z no quadro atual) à matriz do topo. */
    private fun applyBasis(xx: Float, xy: Float, xz: Float, yx: Float, yy: Float, yz: Float, zx: Float, zy: Float, zz: Float) {
        val m = stack[top]
        for (r in 0..2) {
            val a = m[r * 3]
            val b = m[r * 3 + 1]
            val c = m[r * 3 + 2]
            m[r * 3] = a * xx + b * xy + c * xz
            m[r * 3 + 1] = a * yx + b * yy + c * yz
            m[r * 3 + 2] = a * zx + b * zy + c * zz
        }
    }

    /** Ombreira acompanha o braço: gira (em parte) do braço caído até a direção do úmero, em torno do eixo perpendicular. */
    private fun pauldronFollow(side: Float, ux: Float, uy: Float, uz: Float) {
        push()
        val c0 = (-uy).coerceIn(-1f, 1f)
        val ang = acos(c0) * 0.8f
        var ax = -uz
        var az = ux
        val al = sqrt(ax * ax + az * az)
        if (al > 0.0001f && ang > 0.001f) {
            ax /= al; az /= al
            val c = cos(ang)
            val sn = sin(ang)
            val k = 1f - c
            // Rodrigues, eixo (ax, 0, az): imagens dos eixos X, Y, Z
            applyBasis(
                c + ax * ax * k, az * sn, ax * az * k,
                -az * sn, c, ax * sn,
                ax * az * k, -ax * sn, c + az * az * k
            )
        }
        pauldron(side)
        pop()
    }

    /** Braço de dois ossos até a mão, com o cotovelo apontando para baixo e para fora (vetor-polo). */
    private fun spearArm(side: Float, hx: Float, hy: Float, hz: Float) {
        push()
        translate(side * 8.4f, 13.2f, 0f)
        val l1 = 5.7f
        val l2 = 6.1f
        val rx = hx - side * 8.4f
        val ry = hy - 13.2f
        val rz = hz
        val rl = sqrt(rx * rx + ry * ry + rz * rz).coerceAtLeast(0.0001f)
        val d = rl.coerceIn(1.0f, 11.6f)
        val nx = rx / rl
        val ny = ry / rl
        val nz = rz / rl
        val a = (l1 * l1 - l2 * l2 + d * d) / (2f * d)
        val rr = sqrt(max(0f, l1 * l1 - a * a))
        // polo: cotovelo para baixo, para fora e um pouco para trás
        var px = side * 0.6f
        var py = -1f
        var pz = -0.3f
        val pd = px * nx + py * ny + pz * nz
        px -= pd * nx; py -= pd * ny; pz -= pd * nz
        val pl = sqrt(px * px + py * py + pz * pz).coerceAtLeast(0.0001f)
        px /= pl; py /= pl; pz /= pl
        val ex = nx * a + px * rr
        val ey = ny * a + py * rr
        val ez = nz * a + pz * rr
        val ux = ex / l1
        val uy = ey / l1
        val uz = ez / l1
        pauldronFollow(side, ux, uy, uz)
        val fx = (nx * d - ex) / l2
        val fy = (ny * d - ey) / l2
        val fz = (nz * d - ez) / l2
        // eixo X local = u × f (o antebraço dobra só em torno dele)
        var xx = uy * fz - uz * fy
        var xy = uz * fx - ux * fz
        var xz = ux * fy - uy * fx
        var xl = sqrt(xx * xx + xy * xy + xz * xz)
        if (xl < 0.001f) {
            xx = uy * 1f - uz * 0f
            xy = uz * 0f - ux * 1f
            xz = 0f
            xl = sqrt(xx * xx + xy * xy + xz * xz).coerceAtLeast(0.0001f)
        }
        xx /= xl; xy /= xl; xz /= xl
        val yx = -ux
        val yy = -uy
        val yz = -uz
        var zx = xy * yz - xz * yy
        var zy = xz * yx - xx * yz
        var zz = xx * yy - xy * yx
        if (fx * zx + fy * zy + fz * zz < 0f) {
            xx = -xx; xy = -xy; xz = -xz
            zx = -zx; zy = -zy; zz = -zz
        }
        val fyL = fx * yx + fy * yy + fz * yz
        val fzL = fx * zx + fy * zy + fz * zz
        val elb = atan2(-fzL, -fyL)
        applyBasis(xx, xy, xz, yx, yy, yz, zx, zy, zz)
        armBase(elb)
        pop()
    }

    private fun spearRig() {
        clampReach(-8.4f, sPo[0], sPo[1], sPo[2], tR)
        clampReach(8.4f, sPo[3], sPo[4], sPo[5], tL)
        spearArm(-1f, tR[0], tR[1], tR[2])
        spearArm(1f, tL[0], tL[1], tL[2])
        var dx = tL[0] - tR[0]
        var dy = tL[1] - tR[1]
        var dz = tL[2] - tR[2]
        var len = sqrt(dx * dx + dy * dy + dz * dz)
        if (len < 2.5f) { dx = 0.3f; dy = 0.25f; dz = 1f; len = sqrt(dx * dx + dy * dy + dz * dz) }
        dx /= len; dy /= len; dz /= len
        var psi = atan2(dx, dz)
        var theta = asinSafe(dy)
        if (thMix > 0.001f) {
            var dp = thPsi - psi
            while (dp > PI.toFloat()) dp -= TWO_PI
            while (dp < -PI.toFloat()) dp += TWO_PI
            psi += dp * thMix
            theta += (thTheta - theta) * thMix
        }
        push()
        translate(tR[0], tR[1], tR[2])
        rotY(psi)
        rotX(-theta)
        translate(0f, 0f, sPo[10])
        if (!spearGone) spearModel()
        projectTo(0f, 0f, 38f, 0)
        projectTo(0f, 0f, 24f, 1)
        pop()
    }

    private fun asinSafe(v: Float): Float = atan2(v, sqrt(max(0f, 1f - v * v)))

    private fun spearModel() {
        val wv = sp[3] * 0.8f
        cube(0f, 0f, 10f, 0.55f, 0.55f, 20f, SHAFT)
        cube(-0.3f, 0.3f, 10f, 0.2f, 0.2f, 19.5f, SHAFT_L)
        cube(0f, 0f, -4f, 0.62f, 0.62f, 0.25f, SHAFT_D)
        cube(0f, 0f, 3f, 0.62f, 0.62f, 0.25f, SHAFT_D)
        cube(0f, 0f, 10f, 0.62f, 0.62f, 0.25f, SHAFT_D)
        cube(0f, 0f, 17f, 0.62f, 0.62f, 0.25f, SHAFT_D)
        cube(0f, 0f, 24f, 0.62f, 0.62f, 0.25f, SHAFT_D)
        cube(0f, 0f, 31f, 0.62f, 0.62f, 0.25f, SHAFT_D)
        cube(0f, 0f, 0f, 0.72f, 0.72f, 2.2f, LEATHER_D)
        cube(0f, 0f, 9.5f, 0.72f, 0.72f, 2.2f, LEATHER_D)
        cube(0f, 0f, 14.5f, 0.8f, 0.8f, 0.35f, GOLD)
        cube(0f, 0f, 19.5f, 0.8f, 0.8f, 0.35f, GOLD)
        cube(0f, 0f, 24.5f, 0.8f, 0.8f, 0.35f, GOLD)
        cube(0f, 0f, -10.3f, 0.85f, 0.85f, 0.7f, GOLD)
        cube(0f, 0f, -11.4f, 0.45f, 0.45f, 0.9f, STEEL_D)
        cube(0f, 0f, -12.7f, 0.28f, 0.28f, 0.9f, STEEL_L)
        cube(0f, 0f, 30.4f, 0.95f, 0.95f, 0.5f, GOLD)
        cube(0f, 0f, 31.4f, 0.8f, 0.8f, 0.5f, GOLD_D)
        cube(0f, -1.6f - wv, 29f, 0.28f, 1.6f, 1.1f, RED_L)
        cube(0f, -3.4f - wv, 29f, 0.28f, 0.7f, 0.8f, RED_D)
        cube(wv * 0.6f, -1.2f, 27.2f, 0.25f, 1.2f, 0.8f, RED_D)
        cube(-wv * 0.5f, -2.2f - wv, 26f, 0.22f, 1f, 0.7f, RED)
        cube(0f, 0f, 32.6f, 0.75f, 0.75f, 1.2f, STEEL_D)
        cube(-1.5f, 0f, 33.4f, 0.5f, 0.3f, 0.9f, STEEL_DD)
        cube(1.5f, 0f, 33.4f, 0.5f, 0.3f, 0.9f, STEEL_DD)
        cube(-2.1f, 0f, 34.3f, 0.35f, 0.28f, 0.7f, STEEL_D)
        cube(2.1f, 0f, 34.3f, 0.35f, 0.28f, 0.7f, STEEL_D)
        cube(0f, 0f, 34.2f, 1.25f, 0.32f, 1.1f, BLADE)
        cube(0f, 0f, 35.9f, 1.85f, 0.32f, 1.1f, BLADE)
        cube(0f, 0f, 37.6f, 2.1f, 0.32f, 1f, BLADE)
        cube(0f, 0f, 39.2f, 1.7f, 0.32f, 1f, BLADE)
        cube(0f, 0f, 40.7f, 1.2f, 0.32f, 0.9f, BLADE)
        cube(0f, 0f, 42f, 0.7f, 0.32f, 0.8f, STEEL_L)
        cube(0f, 0f, 43.2f, 0.3f, 0.3f, 0.7f, STEEL_L)
        cube(0f, 0f, 44f, 0.15f, 0.2f, 0.5f, WHITE)
        cube(0f, 0f, 38f, 0.3f, 0.6f, 5.2f, STEEL)
        cube(0f, 0.65f, 38.5f, 0.12f, 0.1f, 4.4f, WHITE, true)
        cube(0f, 0.7f, 35.5f, 0.55f, 0.1f, 0.35f, runeC, true)
        cube(0f, 0.7f, 37.5f, 0.55f, 0.1f, 0.35f, runeC, true)
        cube(0f, 0.7f, 39.5f, 0.55f, 0.1f, 0.35f, runeC, true)
    }

    // ───────────── animação (molas + ataque) ─────────────

    // molas: 0 inclinação, 1 banking, 2-4 capa, 5-6 penacho, 7 tabardo, 8 cabeça
    private val sp = FloatArray(10)
    private val sv = FloatArray(10)
    private var combo = 0
    private var chargeW = 0f
    private var spearEmptyW = 0f
    private var sparkT = 0f
    private val lastPose = FloatArray(8)
    private val kS = FloatArray(8)

    // keyframes por golpe: sh, z, el, tw, wrist, crouch, lean, hip
    // calculados para a lâmina apontar na direção certa em cada ponto do arco (W preparação, M meio, E fim)
    private val K_W = arrayOf(
        floatArrayOf(-1.658f, 0.528f, -1.387f, -0.4f, 0.879f, 0.15f, -0.18f, 0.35f),
        floatArrayOf(0.742f, 0.185f, -2.462f, 0.827f, 1.6f, 0.55f, 0.32f, -0.45f),
        floatArrayOf(-3.1f, 1.2f, -1.611f, 0.686f, 1.6f, 0.4f, -0.12f, -0.55f),
        floatArrayOf(-2.539f, 0.091f, -0.441f, -0.267f, 0.721f, 0.3f, -0.2f, 0.3f)
    )
    private val K_M = arrayOf(
        floatArrayOf(-0.83f, 0.35f, -1.692f, 0.111f, 1.6f, 0.325f, 0.05f, -0.025f),
        floatArrayOf(-0.478f, 0.26f, -1.807f, 0.207f, 1.6f, 0.35f, 0.16f, -0.15f),
        floatArrayOf(-1.122f, 1.046f, -0.985f, -0.037f, 1.467f, 0.5f, 0.09f, -0.05f),
        floatArrayOf(-1.67f, -0.132f, -1.048f, -0.126f, 1.6f, 0.575f, 0.15f, 0f)
    )
    private val K_E = arrayOf(
        floatArrayOf(0.512f, 0.631f, -2.13f, 0.498f, 1.344f, 0.5f, 0.28f, -0.4f),
        floatArrayOf(-1.188f, 0.734f, -1.636f, -0.236f, 1.6f, 0.15f, 0f, 0.15f),
        floatArrayOf(-0.995f, 1.2f, 0.1f, -0.15f, 1.6f, 0.6f, 0.3f, 0.45f),
        floatArrayOf(1f, -0.7f, -2.473f, 0.3f, 1.6f, 0.85f, 0.5f, -0.3f)
    )
    // fim da preparação, fim do impacto, fim do segurar
    private val K_T = arrayOf(
        floatArrayOf(0.20f, 0.46f, 0.62f),
        floatArrayOf(0.14f, 0.40f, 0.58f),
        floatArrayOf(0.22f, 0.46f, 0.64f),
        floatArrayOf(0.08f, 0.34f, 0.64f)
    )
    private val K_L = floatArrayOf(3.0f, 2.2f, 5.5f, 5.0f)
    private var prevAtk = 0f
    private var prevPhase = 0f
    private var spd = 0f
    private var wasAir = false
    private var wasRolling = false
    private var landDip = 0f
    private var airTimer = 0f
    private var prevFlash = 0f
    private var hitKick = 0f
    private var eyeOpen = 1f
    private var prevSin = 0f
    private var dustT = 0f

    // poeira dos passos / aterrissagem / rolamento
    private const val ND = 16
    private val dX = FloatArray(ND)
    private val dY = FloatArray(ND)
    private val dVx = FloatArray(ND)
    private val dVy = FloatArray(ND)
    private val dL = FloatArray(ND)
    private val dM = FloatArray(ND)
    private val dS = FloatArray(ND)
    private var dNext = 0

    // keyframes do golpe: sh, z, el, tw, wrist, crouch, lean, hip
    private val kI = FloatArray(8)
    private val kW = FloatArray(8)
    private val kE = FloatArray(8)
    private val kO = FloatArray(8)

    // faíscas da lâmina
    private const val NSP = 18
    private val spX = FloatArray(NSP)
    private val spY = FloatArray(NSP)
    private val spVx = FloatArray(NSP)
    private val spVy = FloatArray(NSP)
    private val spL = FloatArray(NSP)
    private var spNext = 0

    private fun spring(i: Int, target: Float, k: Float, d: Float, dt: Float) {
        sv[i] += ((target - sp[i]) * k - sv[i] * d) * dt
        sp[i] += sv[i] * dt
    }

    private fun setK(
        a: FloatArray, sh: Float, z: Float, el: Float, tw: Float,
        wr: Float, cr: Float, lean: Float, hip: Float
    ) {
        a[0] = sh; a[1] = z; a[2] = el; a[3] = tw
        a[4] = wr; a[5] = cr; a[6] = lean; a[7] = hip
    }

    private fun mixK(a: FloatArray, b: FloatArray, u: Float) {
        for (i in 0..7) kO[i] = lerp(a[i], b[i], u)
    }

    private fun emitSpark(x: Float, y: Float) {
        val i = spNext
        spNext = (spNext + 1) % NSP
        val r = kotlin.random.Random
        val k = rs * 38f
        spX[i] = x
        spY[i] = y
        spVx[i] = (r.nextFloat() - 0.5f) * 2f * k
        spVy[i] = (-0.2f - r.nextFloat() * 0.9f) * k
        spL[i] = 0.22f + r.nextFloat() * 0.2f
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
        val sdt = min(dt, 0.04f)
        if (first) wShown = weapon
        if (weapon != wShown && swapU < 0f) swapU = 0f
        if (swapU >= 0f) {
            swapU += dt / 0.45f
            if (swapU >= 0.5f && wShown != weapon) {
                wShown = weapon
                for (i in 0..5) emitSpark(fx + (kotlin.random.Random.nextFloat() - 0.5f) * 14f * s * PX, fy - 14f * s * PX)
            }
            if (swapU >= 1f) swapU = -1f
        }
        val swapDip = if (swapU >= 0f) sin(swapU * PI.toFloat()) else 0f

        // direção suave (guarda a velocidade angular para o banking)
        var yawStep = 0f
        if (abs(faceX) + abs(faceY) > 0.01f) {
            val target = atan2(faceX, faceY)
            if (first) {
                yaw = target
            } else {
                var d = target - yaw
                while (d > PI.toFloat()) d -= TWO_PI
                while (d < -PI.toFloat()) d += TWO_PI
                yawStep = d * min(1f, dt * 16f)
                yaw += yawStep
            }
        }
        val yawVel = yawStep / dt
        blend += ((if (moving) 1f else 0f) - blend) * min(1f, dt * 12f)

        // velocidade estimada pela fase da caminhada
        val dPh = abs(phase - prevPhase)
        prevPhase = phase
        val spdTgt = if (first) 0f else (dPh / dt / 9.6f).coerceIn(0f, 1.6f)
        spd += (spdTgt - spd) * min(1f, dt * 10f)
        val spN = min(spd, 1f)

        // fase do golpe: 0..1 (negativo = sem ataque)
        val t = if (attack > 0.001f) 1f - attack else -1f
        val attacking = t >= 0f
        val sw = swing.coerceIn(0, 3)
        if (attack > prevAtk + 0.3f) {
            // golpe novo: começa a partir da pose atual (é isso que encadeia o combo)
            combo = sw
            System.arraycopy(lastPose, 0, kS, 0, 8)
            System.arraycopy(lastSp, 0, sS, 0, 11)
            sv[2] += 2.5f; sv[3] += 3.5f; sv[4] += 4.5f
            sv[5] += 2.0f; sv[6] += 3.0f; sv[7] += 2.0f
            if (sw == 3) { sv[2] += 3f; sv[3] += 4f; sv[4] += 5f }
        }
        prevAtk = attack

        val rolling = rollP >= 0f
        // só conta como "no ar" quando está de fato alto ou subindo (degraus pequenos não ativam a pose de pulo)
        val airCand = inAir && !rolling && (airH > 8f || airVz > 60f)
        airTimer = if (airCand) airTimer + dt else 0f
        val airT = if (airTimer > 0.04f) 1f else 0f
        val rollTgt = if (rolling) min(rollP / 0.15f, (1f - rollP) / 0.2f).coerceIn(0f, 1f) else 0f
        if (first) {
            airW = airT
            rollW = rollTgt
        } else {
            airW += (airT - airW) * min(1f, dt * (if (airT > airW) 16f else 11f))
            rollW += (rollTgt - rollW) * min(1f, dt * 26f)
        }
        var landPuff = false
        var takeoffPuff = false
        if (wasAir && airW < 0.4f) {
            // aterrissagem: capa sobe, corpo amortece
            sv[2] -= 3f; sv[3] -= 4f; sv[4] -= 5f; sv[0] += 2.2f; sv[7] -= 3f
            wasAir = false
            landPuff = true
            landDip = 1f
        } else if (airW > 0.7f) {
            if (!wasAir) takeoffPuff = true
            wasAir = true
        }
        // rolamento: poeira ao começar e agachada ao levantar
        if (rolling && !wasRolling) takeoffPuff = true
        if (!rolling && wasRolling) landDip = max(landDip, 0.65f)
        wasRolling = rolling
        // reação ao tomar dano: corpo recua, cabeça e capa chicoteiam
        val fl = V.flash
        if (!first && fl > prevFlash + 0.25f) {
            hitKick = 1f
            sv[0] -= 6f; sv[2] += 4f; sv[3] += 5f; sv[4] += 6f; sv[7] += 3f; sv[8] += 6f
        }
        prevFlash = fl
        hitKick = max(0f, hitKick - dt * 4.5f)
        landDip = max(0f, landDip - dt * 5.5f)
        // piscar de olhos
        val bp = clock % 3.6f
        eyeOpen = if (bp < 0.14f) max(0.12f, abs(bp - 0.07f) / 0.07f) else 1f
        val vzN = (airVz / 340f).coerceIn(-1.2f, 1f)
        val tuck = max(rollW, airW * (0.30f + 0.35f * max(0f, vzN) - 0.2f * max(0f, -vzN)))
        val still = 1f - max(rollW, airW)
        val mvs = still * (if (attacking) blend * 0.35f else blend)

        // ── pose do braço da espada ──
        val idleSh = -0.5f + sin(phase) * 0.18f * mvs + sin(clock * 1.3f) * 0.04f * (1f - mvs)
        val idleZ = -0.12f
        val idleEl = -0.8f
        var sh = idleSh
        var shZ = idleZ
        var el = idleEl
        var tw = 0f
        var lunge = 0f
        var wrist = 0f
        var crouch = 0f
        var leanAtk = 0f
        var hipTw = 0f
        chargeW += ((if (charge >= 0f && !attacking) 1f else 0f) - chargeW) * min(1f, dt * 9f)
        spearEmptyW += ((if (spearGone && !attacking) 1f else 0f) - spearEmptyW) * min(1f, dt * 10f)
        val spear = wShown == 1
        val KT = if (spear) SK_T else K_T
        val KL = if (spear) SK_L else K_L
        val tW = KT[sw][0]
        val tS = KT[sw][1]
        val tH = KT[sw][2]
        if (attacking) {
            setK(kI, idleSh, idleZ, idleEl, 0f, 0f, 0f, 0f, 0f)
            if (t < tW) {
                mixK(kS, K_W[sw], ease(t / tW))
            } else if (t < tS) {
                var u = (t - tW) / (tS - tW)
                u = u * u * (3f - 2f * u)
                val kw = K_W[sw]
                val km = K_M[sw]
                val ke = K_E[sw]
                val cw = 2f * (u - 0.5f) * (u - 1f)
                val cm = -4f * u * (u - 1f)
                val ce = 2f * u * (u - 0.5f)
                for (i in 0..7) kO[i] = kw[i] * cw + km[i] * cm + ke[i] * ce
            } else if (t < tH) {
                val u = (t - tS) / (tH - tS)
                val o = 0.06f * sin(u * PI.toFloat())
                for (i in 0..7) kO[i] = K_E[sw][i] + (K_E[sw][i] - K_W[sw][i]) * o
            } else {
                mixK(K_E[sw], kI, ease((t - tH) / (1f - tH)))
            }
            sh = kO[0]; shZ = kO[1]; el = kO[2]; tw = kO[3]
            wrist = kO[4]; crouch = kO[5]; leanAtk = kO[6]; hipTw = kO[7]
            val la = tW - 0.04f
            val lb = tH + 0.05f
            if (t > la && t < lb) {
                lunge = sin(((t - la) / (lb - la)) * PI.toFloat()) * KL[sw]
            }
        } else if (chargeW > 0.01f) {
            // segurando: espada erguida, corpo agachado e tremendo de energia
            setK(kI, idleSh, idleZ, idleEl, 0f, 0f, 0f, 0f, 0f)
            mixK(kI, K_W[3], ease(chargeW))
            val ch = max(0f, charge)
            sh = kO[0] + sin(clock * 47f) * 0.025f * ch
            shZ = kO[1]; el = kO[2]
            tw = kO[3] + sin(clock * 39f) * 0.03f * ch
            wrist = kO[4]; crouch = kO[5] + 0.12f * ch; leanAtk = kO[6]; hipTw = kO[7]
        }
        lastPose[0] = sh; lastPose[1] = shZ; lastPose[2] = el; lastPose[3] = tw
        lastPose[4] = wrist; lastPose[5] = crouch; lastPose[6] = leanAtk; lastPose[7] = hipTw
        if (spear) {
            spearPose(attacking, t, sw, tW, tS, tH, phase, mvs)
            System.arraycopy(sPo, 0, lastSp, 0, 11)
            tw = sPo[6]; crouch = sPo[7]; leanAtk = sPo[8]; hipTw = sPo[9]
            val tk = max(rollW, if (attacking) 0f else airW * 0.8f)
            if (tk > 0.001f) for (i in 0..10) sPo[i] = lerp(sPo[i], SK_TUCK[i], tk)
        }
        if (swapDip > 0.001f) {
            if (spear) {
                for (i in 0..10) sPo[i] = lerp(sPo[i], SK_DIP[i], swapDip)
            } else {
                sh = lerp(sh, 0.5f, swapDip)
                el = lerp(el, -0.2f, swapDip)
                wrist = lerp(wrist, -0.3f, swapDip)
            }
        }
        if (rollW > 0.001f) {
            sh = lerp(sh, -1.2f, rollW)
            shZ = lerp(shZ, 0.2f, rollW)
            el = lerp(el, -1.9f, rollW)
            wrist = lerp(wrist, 0.3f, rollW)
        }
        if (airW > 0.001f && !attacking) {
            sh = lerp(sh, -2.0f - 0.25f * vzN, airW)
            shZ = lerp(shZ, 0.5f, airW)
            el = lerp(el, -0.6f, airW)
        }
        var shieldSh = -0.3f - sin(phase) * 0.22f * mvs - (if (attacking) 0.2f else 0f)
        shieldSh = lerp(shieldSh, -1.0f, rollW)
        shieldSh = lerp(shieldSh, -1.1f - 0.2f * vzN, airW)

        // ── molas (capa, penacho, tabardo, cabeça, inclinação) ──
        val bankT = (-yawVel * 0.03f).coerceIn(-0.22f, 0.22f) * spN
        spring(0, 0.13f * spN, 90f, 12f, sdt)
        spring(1, bankT, 80f, 9f, sdt)
        val capeBase = 0.14f + 0.30f * spN + airW * (0.35f - 0.3f * vzN) + lunge * 0.05f +
            0.10f * sin(clock * 3f + phase * 2f) * (0.4f + spN)
        spring(2, capeBase, 38f, 5.5f, sdt)
        spring(3, capeBase * 0.8f + 0.08f, 28f, 4.5f, sdt)
        spring(4, capeBase * 0.7f + 0.10f, 20f, 3.8f, sdt)
        spring(5, 0.18f * spN, 55f, 6f, sdt)
        spring(6, 0.22f * spN, 40f, 4.5f, sdt)
        spring(7, 0.10f + 0.45f * spN, 60f, 7f, sdt)
        spring(8, (yawVel * 0.03f).coerceIn(-0.35f, 0.35f), 70f, 9f, sdt)

        val bob = (0.5f + 0.5f * cos(phase * 2f)) * 1.3f * mvs + sin(clock * 2.2f) * 0.35f * (1f - mvs)
        val breath = sin(clock * 2.2f) * 0.28f * (1f - mvs * 0.6f)
        val sway = sin(phase) * 0.9f * mvs + sin(clock * 0.9f) * 0.35f * (1f - mvs)
        // rolamento e aterrissagem: quadril baixo e joelhos dobrados
        crouch = max(crouch, max(rollW * 0.9f, landDip * 0.9f))
        val lean = sp[0] + leanAtk * 0.5f - hitKick * 0.3f + rollW * 0.25f + landDip * 0.2f
        val hipY = 12.6f + bob - crouch * 3.2f - hitKick * 0.9f
        val wob = sin(phase) * 0.14f * mvs
        val chestTw = tw - hipTw * 0.5f - wob - sin(phase) * 0.22f * mvs
        val glow = 0.35f + 0.15f * sin(clock * 4f) + (if (attacking) 0.5f else 0f) +
            chargeW * (0.5f + 0.4f * sin(clock * 18f))

        val gp = 0.5f + 0.5f * sin(clock * 3.1f) + (if (attacking) 0.6f else 0f) + chargeW * 0.8f
        val gq = gp.coerceIn(0f, 1f)
        gemC = rgb((200 + 55 * gq).toInt(), (30 + 60 * gq).toInt(), (40 + 50 * gq).toInt())
        val rq = (0.55f + 0.45f * sin(clock * 5.3f + 1f) + chargeW * 0.5f).coerceIn(0f, 1f)
        runeC = rgb(255, (130 + 110 * rq).toInt(), (50 + 120 * rq).toInt())

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
        rotZ(sp[1])
        translate(sway * 0.5f, 0f, lunge - hitKick * 1.6f)
        if (rolling) {
            // 10% agacha, 80% gira (começa e termina suave), 10% levanta
            val r = ((rollP - 0.10f) / 0.80f).coerceIn(0f, 1f)
            val ang = (r * 0.6f + r * r * (3f - 2f * r) * 0.4f) * TWO_PI
            val lift = 4.5f * sin(r * PI.toFloat())
            translate(0f, 10f + lift, 0f)
            rotX(ang)
            translate(0f, -11f, 0f)
        }

        // pernas: swing + joelho dobrando na passada + passo no ataque
        for (k in 0..1) {
            val sd = if (k == 0) 1f else -1f
            val pp = if (sd > 0f) phase else phase + PI.toFloat()
            var a = sin(pp) * 0.85f * mvs
            var kn = max(0f, -cos(pp)) * 1.1f * mvs + 0.10f + crouch * 0.9f
            val ft = max(0f, cos(pp)) * 0.35f * mvs + airW * (0.55f * max(0f, vzN) - 0.25f * max(0f, -vzN))
            if (attacking) {
                val front = (combo % 2 == 0) == (sd > 0f)
                val lk = lunge / KL[sw]
                if (front) {
                    a -= 0.55f * lk
                } else {
                    a += 0.45f * lk
                    kn += 0.35f * lk
                }
            }
            a = a * (1f - airW) - tuck + sd * 0.3f * airW
            kn = (kn + tuck * 1.5f + (if (sd > 0f) 0.5f else -0.1f) * airW).coerceAtLeast(0f)
            leg(sd, a, kn, hipY, ft)
        }

        // quadril → peito (contra-rotação) → cabeça e braços
        push()
        translate(0f, hipY, 0f)
        rotY(hipTw * 0.5f + wob)
        rotX(lean)
        push()
        torso(chestTw, sp[2], sp[3], sp[4], sp[7], breath)
        push()
        rotY(chestTw)
        translate(0f, breath, 0f)
        head(-chestTw * 0.7f + sp[8], -lean * 0.6f, sp[5], sp[6])
        if (spear) {
            spearRig()
        } else {
            rightArm(sh, shZ, el, wrist, glow)
            leftArm(shieldSh, 0.12f, -1.4f)
        }
        pop()
        pop()
        pop()

        // ── poeira dos passos, da aterrissagem e do rolamento (atrás do herói) ──
        val sph = sin(phase)
        val rnd = kotlin.random.Random
        if (mvs > 0.5f && spN > 0.45f && ((sph > 0f) != (prevSin > 0f))) {
            emitDust(
                fx - sin(yaw) * 4f * rs + (rnd.nextFloat() - 0.5f) * 3f * rs,
                fy - cos(yaw) * 4f * rs * SINP,
                (rnd.nextFloat() - 0.5f) * rs * 10f, -rs * (5f + rnd.nextFloat() * 7f), rs * (1.3f + rnd.nextFloat() * 0.8f)
            )
        }
        prevSin = sph
        if (takeoffPuff) {
            for (i in 0..4) {
                val a = i * 1.25f
                emitDust(fx + cos(a) * 2.5f * rs, fy + sin(a) * 1.2f * rs, cos(a) * rs * 20f, sin(a) * rs * 7f - rs * 2f, rs * 1.8f)
            }
        }
        if (landPuff) {
            for (i in 0..6) {
                val a = i * 0.9f
                emitDust(fx + cos(a) * 3f * rs, fy + sin(a) * 1.5f * rs, cos(a) * rs * 26f, sin(a) * rs * 9f - rs * 3f, rs * 2.2f)
            }
        }
        if (rollW > 0.6f) {
            dustT -= dt
            if (dustT <= 0f) {
                dustT = 0.05f
                emitDust(fx + (rnd.nextFloat() - 0.5f) * 4f * rs, fy, (rnd.nextFloat() - 0.5f) * rs * 14f, -rs * 6f, rs * 2.4f)
            }
        }
        p.style = Paint.Style.FILL
        for (i in 0 until ND) {
            if (dL[i] <= 0f) continue
            dL[i] -= dt
            dX[i] += dVx[i] * dt
            dY[i] += dVy[i] * dt
            val damp = min(1f, dt * 5f)
            dVx[i] -= dVx[i] * damp
            dVy[i] -= dVy[i] * damp
            val a = (dL[i] / dM[i]).coerceIn(0f, 1f)
            p.color = (((a * 0.34f * 255f).toInt()) shl 24) or 0x00D9CDB4
            c.drawCircle(dX[i], dY[i], dS[i] * (1.6f - 0.6f * a), p)
        }

        // ── faíscas da carga (o indicador fica no botão de ataque) ──
        if (chargeW > 0.05f && !attacking) {
            val ch = max(0f, charge)
            val rr = (10f + 16f * ch) * rs
            if (ch > 0.15f) {
                sparkT -= dt
                if (sparkT <= 0f) {
                    sparkT = 0.07f
                    emitSpark(fx + (kotlin.random.Random.nextFloat() - 0.5f) * rr * 1.6f, fy)
                }
            }
        }
        flushZ(c)

        // ── rastro da espada ──
        if (!attacking) {
            trN = 0
        } else {
            if (t >= tW && t <= tH + 0.05f && !(spear && sw == 3)) {
                if (trN == TRAIL_MAX) {
                    System.arraycopy(trX, 1, trX, 0, TRAIL_MAX - 1)
                    System.arraycopy(trY, 1, trY, 0, TRAIL_MAX - 1)
                    System.arraycopy(trMx, 1, trMx, 0, TRAIL_MAX - 1)
                    System.arraycopy(trMy, 1, trMy, 0, TRAIL_MAX - 1)
                    trN = TRAIL_MAX - 1
                }
                trX[trN] = tipX
                trY[trN] = tipY
                trMx[trN] = midX
                trMy[trN] = midY
                trN++
                if (t >= tS - 0.04f && t <= tS + 0.14f) {
                    emitSpark(tipX, tipY)
                    emitSpark(tipX, tipY)
                    if (sw == 3) {
                        emitSpark(tipX, tipY)
                        emitSpark(tipX, tipY)
                    }
                }
            }
            val fade = min(1f, attack * 4f) * 0.8f
            for (i in 1 until trN) {
                val a = i.toFloat() / trN.toFloat()
                path.reset()
                path.moveTo(trX[i - 1], trY[i - 1])
                path.lineTo(trX[i], trY[i])
                path.lineTo(trMx[i], trMy[i])
                path.lineTo(trMx[i - 1], trMy[i - 1])
                path.close()
                p.style = Paint.Style.FILL
                p.color = (((a * a * 0.22f * fade * 255f).toInt()) shl 24) or 0x00FFB74D
                c.drawPath(path, p)
                p.style = Paint.Style.STROKE
                p.strokeWidth = max(1.6f, rs * 1.0f)
                p.strokeCap = Paint.Cap.ROUND
                p.color = (((a * a * 0.14f * fade * 255f).toInt()) shl 24) or 0x00FFE9A8
                c.drawLine(trX[i - 1], trY[i - 1], trX[i], trY[i], p)
                p.strokeWidth = max(1f, rs * 0.55f)
                p.color = (((a * a * 0.55f * fade * 255f).toInt()) shl 24) or 0x00FFFFFF
                c.drawLine(trX[i - 1], trY[i - 1], trX[i], trY[i], p)
            }
            // estocada: riscos de velocidade e clarão na ponta da lança
            if (spear && sw != 1 && sw != 3 && t >= tS - 0.06f && t <= tS + 0.22f) {
                val k = 1f - ((t - (tS - 0.06f)) / 0.28f).coerceIn(0f, 1f)
                var vx = tipX - midX
                var vy = tipY - midY
                val vl = hypot(vx, vy).coerceAtLeast(0.001f)
                vx /= vl
                vy /= vl
                p.style = Paint.Style.STROKE
                p.strokeCap = Paint.Cap.ROUND
                for (i in -1..1) {
                    val ox = -vy * i * 3.4f * rs
                    val oy = vx * i * 3.4f * rs
                    val len = (30f - abs(i) * 9f) * rs
                    p.strokeWidth = max(1.2f, rs * (if (i == 0) 1.2f else 0.65f))
                    p.color = (((k * 0.85f * 255f).toInt()) shl 24) or 0x00FFF2C0
                    c.drawLine(tipX + ox - vx * len * 0.15f, tipY + oy - vy * len * 0.15f, tipX + ox - vx * len * 1.5f, tipY + oy - vy * len * 1.5f, p)
                }
                p.style = Paint.Style.FILL
                p.color = (((k * 0.7f * 255f).toInt()) shl 24) or 0x00FFFFFF
                c.drawCircle(tipX, tipY, rs * (1.5f + 3.5f * k), p)
            }
            p.style = Paint.Style.FILL
            p.strokeCap = Paint.Cap.BUTT
        }

        // ── onda de choque do golpe forte ──
        if (attacking && sw == 3 && !spear && t >= tS) {
            val u = ((t - tS) / (1f - tS)).coerceIn(0f, 1f)
            val cxw = fx + sin(yaw) * 14f * rs
            val cyw = fy + cos(yaw) * 14f * SINP * rs
            val rr = (6f + 38f * (1f - (1f - u) * (1f - u))) * rs
            val al = (1f - u)
            p.style = Paint.Style.STROKE
            p.strokeWidth = max(3f, rs * 3.2f * al)
            p.color = (((al * 0.9f * 255f).toInt()) shl 24) or 0x00FFE9A8
            c.drawOval(cxw - rr, cyw - rr * SINP, cxw + rr, cyw + rr * SINP, p)
            p.strokeWidth = max(2f, rs * 1.4f)
            p.color = (((al * 0.7f * 255f).toInt()) shl 24) or 0x00FF8F00
            c.drawOval(cxw - rr * 0.75f, cyw - rr * 0.75f * SINP, cxw + rr * 0.75f, cyw + rr * 0.75f * SINP, p)
            p.style = Paint.Style.FILL
        }

        // ── faíscas ──
        p.style = Paint.Style.FILL
        for (i in 0 until NSP) {
            if (spL[i] <= 0f) continue
            spL[i] -= dt
            spX[i] += spVx[i] * dt
            spY[i] += spVy[i] * dt
            spVy[i] += rs * 120f * dt
            val a = (spL[i] / 0.3f).coerceIn(0f, 1f)
            val sz = max(1.2f, rs * 0.9f) * (0.5f + a * 0.5f)
            p.color = (((a * 255f).toInt()) shl 24) or (if (a > 0.55f) 0x00FFFFFF else 0x00FFB74D)
            c.drawRect(spX[i] - sz, spY[i] - sz, spX[i] + sz, spY[i] + sz, p)
        }
    }

    // ───────────── ESQUELETO ─────────────

    private val BONE = 0xFFE8E4D4.toInt()
    private val BONE_D = 0xFFC2BCA6.toInt()
    private val KING_BONE = 0xFFD9CDB2.toInt()
    private val KING_BONE_D = 0xFFB3A688.toInt()
    private val DARK = 0xFF16131C.toInt()
    private val RUST = 0xFF93A0A8.toInt()
    private val RUST_D = 0xFF6B5446.toInt()
    private val PURPLE = 0xFF5B21B6.toInt()
    private val PURPLE_D = 0xFF3B1478.toInt()
    private val KING_BLADE = 0xFF7E57C2.toInt()

    private fun skLeg(side: Float, a: Float, hipY: Float, bone: Int, boneD: Int) {
        push()
        translate(side * 2.6f, hipY, 0f)
        rotX(a)
        cube(0f, -2.8f, 0f, 1.35f, 2.8f, 1.35f, bone)
        cube(0f, -5.6f, 0f, 1.7f, 0.8f, 1.7f, boneD)
        translate(0f, -5.6f, 0f)
        rotX(max(0f, a) * 1.1f + 0.06f)
        cube(0f, -2.5f, 0f, 1.2f, 2.5f, 1.2f, bone)
        translate(0f, -5.0f, 0f)
        rotX(-max(0f, a) * 0.5f)
        cube(0f, 0f, 1.0f, 1.8f, 0.9f, 2.7f, bone)
        pop()
    }

    private fun skRib(y: Float, w: Float, bone: Int) {
        cube(0f, y, 0.9f, w, 0.5f, 1.2f, bone)
        cube(-w, y, 0.2f, 0.45f, 0.5f, 1.5f, bone)
        cube(w, y, 0.2f, 0.45f, 0.5f, 1.5f, bone)
    }

    private fun skHead(tipo: Int, bone: Int, boneD: Int, turn: Float, tilt: Float) {
        push()
        translate(0f, 11.9f, 0f)
        rotY(turn)
        rotX(tilt)
        cube(0f, 4.6f, 0f, 4.5f, 4.5f, 4.3f, bone)
        cube(0f, 1.0f, 0.3f, 3.7f, 1.0f, 3.5f, boneD)
        cube(-1.9f, 5.4f, 4.35f, 1.15f, 1.25f, 0.15f, DARK)
        cube(1.9f, 5.4f, 4.35f, 1.15f, 1.25f, 0.15f, DARK)
        val glow = if (tipo == 2) 0xFFD1A3FF.toInt() else 0xFFFF7043.toInt()
        cube(-1.9f, 5.4f, 4.5f, 0.55f, 0.6f, 0.1f, glow, true)
        cube(1.9f, 5.4f, 4.5f, 0.55f, 0.6f, 0.1f, glow, true)
        cube(0f, 3.6f, 4.35f, 0.5f, 0.7f, 0.15f, DARK)
        cube(-1.4f, 1.3f, 3.85f, 0.2f, 0.7f, 0.1f, DARK)
        cube(0f, 1.3f, 3.85f, 0.2f, 0.7f, 0.1f, DARK)
        cube(1.4f, 1.3f, 3.85f, 0.2f, 0.7f, 0.1f, DARK)
        cube(2.9f, 7.4f, 4.35f, 0.7f, 0.8f, 0.12f, boneD)
        if (tipo == 1) {
            cube(0f, 8.6f, 0f, 4.9f, 1.8f, 4.7f, STEEL_D)
            cube(0f, 7.0f, 4.7f, 4.9f, 0.4f, 0.3f, STEEL)
            cube(0f, 5.2f, 4.65f, 0.45f, 2.2f, 0.3f, STEEL_D)
            cube(0f, 10.6f, 0f, 0.7f, 0.8f, 4.0f, RUST_D)
        } else if (tipo == 2) {
            cube(0f, 9.6f, 0f, 4.8f, 0.9f, 4.6f, GOLD)
            cube(-3.4f, 11.4f, 3.2f, 0.7f, 1.0f, 0.7f, GOLD)
            cube(0f, 11.9f, 3.6f, 0.7f, 1.4f, 0.7f, GOLD)
            cube(3.4f, 11.4f, 3.2f, 0.7f, 1.0f, 0.7f, GOLD)
            cube(-3.4f, 11.4f, -3.2f, 0.7f, 1.0f, 0.7f, GOLD)
            cube(3.4f, 11.4f, -3.2f, 0.7f, 1.0f, 0.7f, GOLD)
            cube(0f, 9.7f, 4.7f, 0.7f, 0.7f, 0.2f, 0xFFB388FF.toInt(), true)
        }
        pop()
    }

    private fun skArm(
        side: Float, sh: Float, shZ: Float, el: Float, bone: Int, boneD: Int,
        sword: Boolean, bladeLen: Float, bladeCol: Int, wrist: Float, shield: Boolean
    ) {
        push()
        translate(side * 5.6f, 10.6f, 0f)
        cube(0f, 0.2f, 0f, 1.5f, 0.9f, 1.5f, boneD)
        rotZ(shZ)
        rotX(sh)
        cube(0f, -2.5f, 0f, 1.15f, 2.6f, 1.15f, bone)
        translate(0f, -5.0f, 0f)
        rotX(el)
        cube(0f, 0f, 0f, 1.5f, 0.7f, 1.5f, boneD)
        cube(0f, -2.4f, 0f, 1.05f, 2.5f, 1.05f, bone)
        cube(0f, -5.2f, 0f, 1.25f, 0.8f, 1.25f, bone)
        if (sword) {
            translate(0f, -5.2f, 0f)
            rotX(1.9f + wrist)
            cube(0f, 0.4f, 0f, 0.8f, 2.0f, 0.8f, LEATHER_D)
            cube(0f, 3.0f, 0f, 3.2f, 0.6f, 1.1f, RUST_D)
            cube(0f, 3.6f + bladeLen * 0.5f, 0f, 0.7f, bladeLen * 0.5f, 1.5f, bladeCol)
            cube(0f, 3.6f + bladeLen * 0.55f, 1.55f, 0.35f, bladeLen * 0.4f, 0.15f, RUST_D)
            cube(0f, 3.9f + bladeLen, 0.3f, 0.55f, 0.8f, 1.0f, bladeCol)
        }
        if (shield) {
            translate(0f, -5.2f, 0f)
            rotX(-(sh + el))
            cube(0f, 0.5f, 0.6f, 4.2f, 4.8f, 0.7f, STEEL_D)
            cube(0f, 0.5f, 1.2f, 3.5f, 4.1f, 0.5f, LEATHER_L)
            cube(0f, 0.8f, 1.8f, 1.0f, 1.0f, 0.4f, STEEL)
        }
        pop()
    }

    /** Esqueleto em cubos 3D. tipo 0 = comum, 1 = de elmo e escudo, 2 = rei esqueleto. */
    fun drawSkeleton(
        c: Canvas, p: Paint, fx: Float, fy: Float, s: Float, phase: Float,
        yawA: Float, tipo: Int, attack: Float
    ) {
        val king = tipo == 2
        val bone = if (king) KING_BONE else BONE
        val boneD = if (king) KING_BONE_D else BONE_D
        val walk = sin(phase)
        val clk = V.time
        val t = if (attack > 0.001f) 1f - attack else -1f
        val attacking = t >= 0f
        val mv = if (attacking) 0.5f else 1f

        var sh = -0.35f - walk * 0.45f
        var shZ = -0.1f
        var el = -0.55f
        var tw = 0f
        var lunge = 0f
        var wrist = 0f
        if (attacking) {
            val wSh = -2.5f
            val wZ = 0.5f
            val wEl = -0.8f
            val wTw = -0.5f
            val eSh = -0.5f
            val eZ = 0.3f
            val eEl = -0.3f
            val eTw = 0.5f
            val iSh = -0.35f
            val iZ = -0.1f
            val iEl = -0.55f
            if (t < 0.3f) {
                val u = ease(t / 0.3f)
                sh = lerp(iSh, wSh, u)
                shZ = lerp(iZ, wZ, u)
                el = lerp(iEl, wEl, u)
                tw = lerp(0f, wTw, u)
                wrist = lerp(0f, -0.3f, u)
            } else if (t < 0.5f) {
                var u = (t - 0.3f) / 0.2f
                u = 1f - (1f - u) * (1f - u)
                sh = lerp(wSh, eSh, u)
                shZ = lerp(wZ, eZ, u)
                el = lerp(wEl, eEl, u)
                tw = lerp(wTw, eTw, u)
                wrist = lerp(-0.3f, 0.35f, u)
            } else {
                val u = ease((t - 0.5f) / 0.5f)
                sh = lerp(eSh, iSh, u)
                shZ = lerp(eZ, iZ, u)
                el = lerp(eEl, iEl, u)
                tw = lerp(eTw, 0f, u)
                wrist = lerp(0.35f, 0f, u)
            }
            if (t in 0.25f..0.7f) {
                lunge = sin(((t - 0.25f) / 0.45f) * PI.toFloat()) * 2.5f
            }
        }
        val bob = abs(walk) * 1.1f * mv + sin(clk * 2.2f) * 0.2f
        val lean = 0.12f + lunge * 0.05f
        val leftSh = -0.2f + walk * 0.45f * mv
        val bladeLen = if (king) 17f else 13f
        val bladeCol = if (king) KING_BLADE else RUST

        rs = s * PX
        ox = fx
        oy = fy
        n = 0
        top = 0
        zMode = false
        identity()
        rotY(yawA)
        translate(0f, 0f, lunge)

        skLeg(1f, walk * 0.7f * mv, 11.5f + bob, bone, boneD)
        skLeg(-1f, -walk * 0.7f * mv, 11.5f + bob, bone, boneD)

        push()
        translate(0f, 11.5f + bob, 0f)
        rotX(lean)
        rotY(tw)
        cube(0f, 0.9f, 0f, 3.5f, 0.9f, 2.1f, boneD)
        cube(0f, 6.0f, -0.3f, 0.9f, 5.2f, 0.9f, bone)
        cube(0f, 7.0f, -1.2f, 3.4f, 4.0f, 0.3f, DARK)
        skRib(3.6f, 3.2f, bone)
        skRib(5.4f, 3.5f, bone)
        skRib(7.2f, 3.6f, bone)
        skRib(9.0f, 3.4f, bone)
        cube(0f, 6.8f, 2.0f, 0.5f, 3.2f, 0.35f, bone)
        cube(0f, 11.2f, 0f, 5.0f, 0.7f, 1.3f, bone)
        if (tipo == 0) {
            cube(0f, -1.2f, 2.3f, 2.6f, 2.2f, 0.3f, 0xFF5D4B3A.toInt())
            cube(0f, 1.2f, 2.3f, 3.6f, 0.4f, 0.4f, LEATHER_D)
        } else if (tipo == 1) {
            cube(0f, 8.2f, 2.3f, 4.0f, 3.0f, 0.4f, STEEL_D)
            cube(0f, 8.2f, 2.65f, 3.2f, 2.3f, 0.2f, RUST)
        } else {
            cube(0f, -1.2f, 2.3f, 2.8f, 2.4f, 0.3f, PURPLE_D)
            cube(0f, -3.4f, 2.3f, 2.8f, 0.3f, 0.35f, GOLD)
            cube(-5.4f, 11.0f, 0f, 2.0f, 1.1f, 1.9f, GOLD)
            cube(5.4f, 11.0f, 0f, 2.0f, 1.1f, 1.9f, GOLD)
            push()
            translate(0f, 11.2f, -2.4f)
            rotX(0.12f + 0.12f * sin(clk * 3f + phase))
            cube(0f, -5.5f, -0.4f, 4.9f, 5.5f, 0.4f, PURPLE)
            translate(0f, -11f, 0f)
            rotX(0.1f + 0.1f * sin(clk * 4f + phase))
            cube(0f, -2.6f, -0.4f, 5.0f, 2.7f, 0.4f, PURPLE_D)
            cube(0f, -5.2f, -0.4f, 5.0f, 0.3f, 0.5f, GOLD)
            pop()
        }
        skHead(tipo, bone, boneD, -tw * 0.6f, -lean * 0.8f)
        skArm(-1f, sh, shZ, el, bone, boneD, true, bladeLen, bladeCol, wrist, false)
        skArm(1f, leftSh, 0.1f, -0.4f, bone, boneD, false, 0f, 0, 0f, tipo == 1)
        pop()

        flushPainter(c, p)
    }
}
