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
import kotlin.math.asin
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Arqueiro 3D (estilo Minecraft Dungeons) no mesmo motor de cubos do Mago: capuz verde com pena, aljava nas costas,
 * arco com limbos articulados que flexionam, braços por IK (mão de trás busca a flecha na aljava e puxa a corda),
 * salto-dash com cambalhota que planta uma armadilha, Chuva de Flechas e Flecha Trovão.
 */
object Archer3D {
    private val V = VoxelArt

    private const val MAX = 760
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
        bmpPaint.alpha = (alphaV.coerceIn(0f, 1f) * 255f).toInt()
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

    // ───────────── paleta do arqueiro ─────────────
    private val TUNIC = 0xFF3E7B3A.toInt()
    private val TUNIC_L = 0xFF5A9C4C.toInt()
    private val TUNIC_D = 0xFF2B5A2A.toInt()
    private val TUNIC_DD = 0xFF1D3F1F.toInt()
    private val CLOAK = 0xFF35603A.toInt()
    private val CLOAK_L = 0xFF4A7D4B.toInt()
    private val CLOAK_D = 0xFF24442B.toInt()
    private val LEATHER = 0xFF6E4727.toInt()
    private val LEATHER_L = 0xFF93633A.toInt()
    private val LEATHER_D = 0xFF45301B.toInt()
    private val PANTS = 0xFF4A3A2A.toInt()
    private val PANTS_L = 0xFF5F4B37.toInt()
    private val BOOT = 0xFF5A3A22.toInt()
    private val BOOT_L = 0xFF7A5232.toInt()
    private val FUR = 0xFFE9E0C8.toInt()
    private val FUR_D = 0xFFC2B796.toInt()
    private val BRASS = 0xFFD4A94A.toInt()
    private val BRASS_L = 0xFFFFE08A.toInt()
    private val BRASS_D = 0xFF9C7A28.toInt()
    private val SKIN = 0xFFF1C27D.toInt()
    private val SKIN_D = 0xFFC98F55.toInt()
    private val HAIR = 0xFFB4622D.toInt()
    private val HAIR_D = 0xFF7E421C.toInt()
    private val SCARF = 0xFFC2463C.toInt()
    private val SCARF_L = 0xFFE2675A.toInt()
    private val SCARF_D = 0xFF8E2C2A.toInt()
    private val BOW = 0xFF8E5A2B.toInt()
    private val BOW_L = 0xFFBC8647.toInt()
    private val BOW_D = 0xFF5E3A1C.toInt()
    private val STRING = 0xFFEFE8D2.toInt()
    private val SHAFT = 0xFFCDA46A.toInt()
    private val STEEL = 0xFFB7C2CC.toInt()
    private val STEEL_L = 0xFFEAF1F6.toInt()
    private val STEEL_D = 0xFF7C8A96.toInt()
    private val RED = 0xFFD64545.toInt()
    private val WHITE = 0xFFFFFFFF.toInt()
    private val EYE = 0xFF1B1B1B.toInt()
    private val GLOW = 0xFF7CFF8A.toInt()
    private val GLOW_L = 0xFFD6FFD9.toInt()
    private val GLOW_D = 0xFF2FBF4C.toInt()

    // cores que variam a cada quadro (verde da floresta, ou elétrico no Flecha Trovão)
    private var magC = 0xFF7CFF8A.toInt()
    private var magL = 0xFFD6FFD9.toInt()
    private var eyeC = 0xFF7CFF8A.toInt()
    private var eyeOpen = 1f
    private var arrowOn = 0f
    private var nockD = 2f
    private var bowFlex = 0f
    private var handGlow = 0f

    private fun leg(side: Float, a: Float, knee: Float, hipY: Float, foot: Float, spread: Float) {
        push()
        translate(side * 3f, hipY, 0f)
        rotZ(side * spread)
        rotX(a)
        cube(0f, -2.7f, 0f, 2.1f, 2.9f, 2.2f, PANTS)
        cube(0f, -2.7f, 2.25f, 0.9f, 2.5f, 0.12f, PANTS_L)
        cube(0f, -5.4f, 0f, 2.25f, 0.3f, 2.35f, LEATHER_D)
        translate(0f, -5.6f, 0f)
        rotX(knee)
        cube(0f, -0.1f, 2.15f, 1.5f, 1.0f, 0.4f, LEATHER_L)
        cube(0f, -0.1f, 2.5f, 0.5f, 0.4f, 0.1f, BRASS)
        cube(0f, -3.1f, 0f, 2.0f, 2.7f, 2.05f, BOOT)
        cube(0f, -0.7f, 0f, 2.4f, 0.8f, 2.45f, FUR)
        cube(0f, -0.2f, 0f, 2.45f, 0.25f, 2.5f, FUR_D)
        cube(0f, -3.4f, 2.1f, 1.3f, 0.3f, 0.12f, LEATHER_D)
        cube(0f, -3.4f, 2.2f, 0.4f, 0.3f, 0.1f, BRASS)
        cube(0f, -4.7f, 2.1f, 1.3f, 0.3f, 0.12f, LEATHER_D)
        cube(0f, -4.7f, 2.2f, 0.4f, 0.3f, 0.1f, BRASS)
        cube(side * 2.05f, -3f, 0f, 0.1f, 2.3f, 1.4f, BOOT_L)
        translate(0f, -5.4f, 0f)
        rotX(foot)
        cube(0f, 0.2f, 1.2f, 2.3f, 1.4f, 3.2f, BOOT)
        cube(0f, -1.1f, 1.3f, 2.45f, 0.3f, 3.4f, LEATHER_D)
        cube(0f, 0.9f, 4.3f, 1.6f, 0.8f, 0.9f, BOOT_L)
        cube(0f, 0.5f, 5.0f, 1.2f, 0.7f, 0.4f, BOOT)
        cube(0f, 0.9f, -2.2f, 1.8f, 0.9f, 0.5f, LEATHER_D)
        pop()
    }

    private fun shoulderPad(side: Float, big: Float) {
        cube(side * 0.4f, 1.0f, 0f, 2.5f, 0.8f, 2.6f, LEATHER)
        cube(side * 0.4f, 1.8f, 0f, 2.6f, 0.2f, 2.7f, LEATHER_L)
        if (big > 0.5f) {
            cube(side * 0.4f, 2.15f, 0f, 2.75f, 0.4f, 2.8f, FUR)
            cube(side * 0.4f, 2.5f, 0f, 2.3f, 0.25f, 2.3f, FUR_D)
            cube(side * 2.6f, 0.4f, 0f, 0.4f, 1.0f, 2.5f, LEATHER_D)
        } else {
            cube(side * 2.6f, 0.4f, 0f, 0.4f, 1.0f, 2.5f, LEATHER_D)
        }
    }

    private fun upperArm() {
        cube(0f, -2.7f, 0f, 1.95f, 2.9f, 1.95f, TUNIC)
        cube(0f, -1.2f, 2.0f, 0.8f, 1.2f, 0.1f, TUNIC_L)
        cube(0f, -5.6f, 0f, 2.15f, 0.35f, 2.15f, LEATHER_D)
    }

    private fun foreArm(left: Float, gl: Float) {
        cube(0f, -2.4f, 0f, 2.35f, 2.4f, 2.35f, TUNIC_L)
        cube(0f, -5.9f, 0f, 2.55f, 2.2f, 2.55f, LEATHER)
        cube(0f, -4.2f, 0f, 2.65f, 0.3f, 2.65f, LEATHER_D)
        cube(0f, -5.0f, 2.6f, 1.0f, 0.3f, 0.12f, BRASS)
        cube(0f, -6.6f, 2.6f, 1.0f, 0.3f, 0.12f, BRASS)
        cube(0f, -5.8f, 2.65f, 0.35f, 1.0f, 0.1f, LEATHER_L)
        if (left > 0.5f) {
            cube(0f, -8.0f, 0f, 1.6f, 1.4f, 1.6f, LEATHER_D)
            cube(0f, -7.3f, 0f, 1.75f, 0.3f, 1.75f, BRASS_D)
        } else {
            cube(0f, -8.0f, 0f, 1.5f, 1.3f, 1.5f, SKIN)
            cube(0f, -7.4f, 0f, 1.7f, 0.35f, 1.7f, LEATHER_D)
            cube(0f, -8.9f, 0.9f, 1.3f, 0.5f, 0.7f, SKIN_D)
        }
        if (gl > 0.04f) {
            cube(0f, -8.0f, 0.5f, 0.9f + 0.8f * gl, 0.9f + 0.8f * gl, 0.9f + 0.8f * gl, magL, true)
        }
    }

    private fun arm(side: Float, sx: Float, z: Float, sh: Float, roll: Float, el: Float, gl: Float) {
        val isLeft = if (side > 0f) 1f else 0f
        push()
        translate(sx, 12.8f, 0f)
        shoulderPad(side, isLeft)
        rotZ(z)
        rotX(sh)
        rotY(roll)
        upperArm()
        translate(0f, -5.7f, 0f)
        rotX(el)
        foreArm(isLeft, gl)
        pop()
    }

    private fun bowModel(flex: Float, gl: Float) {
        // empunhadura
        cube(-0.3f, 0f, 0f, 0.65f, 2.5f, 0.6f, BOW_D)
        cube(-0.3f, 0f, 0.05f, 0.8f, 1.4f, 0.72f, LEATHER)
        cube(-0.3f, 0.5f, 0.05f, 0.85f, 0.15f, 0.76f, LEATHER_D)
        cube(-0.3f, -0.5f, 0.05f, 0.85f, 0.15f, 0.76f, LEATHER_D)
        cube(-0.3f, 1.55f, 0f, 0.8f, 0.3f, 0.7f, BRASS)
        cube(-0.3f, -1.55f, 0f, 0.8f, 0.3f, 0.7f, BRASS)
        cube(0.45f, 0.6f, 0f, 0.25f, 0.5f, 0.45f, BRASS_D)
        // limbo de cima
        push()
        translate(0f, 2.4f, 0f)
        rotX(-(0.10f + 0.34f * flex))
        cube(-0.3f, 2.0f, 0f, 0.6f, 2.1f, 0.52f, BOW)
        cube(-0.3f, 2.0f, 0.5f, 0.25f, 1.9f, 0.1f, BOW_L)
        cube(-0.3f, 3.4f, 0f, 0.62f, 0.25f, 0.54f, BRASS_D)
        translate(0f, 4.2f, 0f)
        rotX(-(0.16f + 0.42f * flex))
        cube(-0.3f, 2.1f, 0f, 0.52f, 2.3f, 0.46f, BOW)
        cube(-0.3f, 2.1f, 0.45f, 0.2f, 2.1f, 0.1f, BOW_L)
        translate(0f, 4.4f, 0f)
        rotX(0.62f + 0.25f * flex)
        cube(-0.3f, 1.0f, 0f, 0.42f, 1.1f, 0.4f, BOW_D)
        cube(-0.3f, 2.2f, 0f, 0.55f, 0.3f, 0.5f, BRASS)
        if (gl > 0.04f) {
            cube(-0.3f, 2.4f, 0f, 0.5f + 0.4f * gl, 0.5f + 0.4f * gl, 0.5f + 0.4f * gl, magC, true)
        }
        projectTo(-0.3f, 2.3f, 0f, 0)
        pop()
        // limbo de baixo
        push()
        translate(0f, -2.4f, 0f)
        rotX(0.10f + 0.34f * flex)
        cube(-0.3f, -2.0f, 0f, 0.6f, 2.1f, 0.52f, BOW)
        cube(-0.3f, -2.0f, 0.5f, 0.25f, 1.9f, 0.1f, BOW_L)
        cube(-0.3f, -3.4f, 0f, 0.62f, 0.25f, 0.54f, BRASS_D)
        translate(0f, -4.2f, 0f)
        rotX(0.16f + 0.42f * flex)
        cube(-0.3f, -2.1f, 0f, 0.52f, 2.3f, 0.46f, BOW)
        cube(-0.3f, -2.1f, 0.45f, 0.2f, 2.1f, 0.1f, BOW_L)
        translate(0f, -4.4f, 0f)
        rotX(-(0.62f + 0.25f * flex))
        cube(-0.3f, -1.0f, 0f, 0.42f, 1.1f, 0.4f, BOW_D)
        cube(-0.3f, -2.2f, 0f, 0.55f, 0.3f, 0.5f, BRASS)
        if (gl > 0.04f) {
            cube(-0.3f, -2.4f, 0f, 0.5f + 0.4f * gl, 0.5f + 0.4f * gl, 0.5f + 0.4f * gl, magC, true)
        }
        projectTo(-0.3f, -2.3f, 0f, 1)
        pop()
        // ponto de encaixe (onde a mão puxa a corda)
        projectTo(0.35f, 0f, -nockD, 2)
        projectTo(0.35f, 0f, -nockD + 15.8f, 3)
    }

    private fun arrowModel(gl: Float) {
        val len = 15f
        val z0 = -nockD
        cube(0.35f, 0f, z0 + len * 0.5f, 0.2f, 0.2f, len * 0.5f, SHAFT)
        cube(0.35f, 0.14f, z0 + len * 0.5f, 0.1f, 0.08f, len * 0.5f, BRASS_L)
        cube(0.35f, 0f, z0 + 0.3f, 0.3f, 0.3f, 0.35f, BRASS_D)
        cube(0.35f, 0f, z0 + 2.2f, 1.0f, 0.07f, 1.5f, RED)
        cube(0.35f, 0f, z0 + 2.2f, 0.07f, 1.0f, 1.5f, WHITE)
        cube(0.35f, 0f, z0 + 3.9f, 0.6f, 0.07f, 0.7f, RED)
        cube(0.35f, 0f, z0 + len + 0.4f, 0.36f, 0.36f, 0.5f, STEEL)
        cube(0.35f, 0f, z0 + len + 1.1f, 0.24f, 0.24f, 0.5f, STEEL_L)
        cube(0.35f, 0f, z0 + len + 1.7f, 0.12f, 0.12f, 0.3f, WHITE)
        if (gl > 0.04f) {
            cube(0.35f, 0f, z0 + len + 0.9f, 0.5f + 0.5f * gl, 0.5f + 0.5f * gl, 1.0f + 0.6f * gl, magC, true)
            cube(0.35f, 0f, z0 + len * 0.5f, 0.3f + 0.3f * gl, 0.3f + 0.3f * gl, len * 0.5f, magC, true)
        }
    }

    private fun quiver(sw: Float, arrows: Float) {
        push()
        translate(-2.3f, 9.2f, -5.2f)
        rotZ(0.34f)
        rotX(sw)
        cube(0f, 0f, 0f, 1.9f, 5.4f, 1.8f, LEATHER)
        cube(0f, 0f, 1.85f, 1.3f, 4.8f, 0.1f, LEATHER_L)
        cube(0f, 3.4f, 1.95f, 1.5f, 0.3f, 0.1f, LEATHER_D)
        cube(0f, -1.0f, 1.95f, 1.5f, 0.3f, 0.1f, LEATHER_D)
        cube(0f, 5.4f, 0f, 2.1f, 0.5f, 1.95f, BRASS)
        cube(0f, 5.45f, 0f, 1.5f, 0.1f, 1.3f, LEATHER_D)
        cube(0f, -5.4f, 0f, 2.0f, 0.45f, 1.9f, LEATHER_D)
        cube(0f, -5.9f, 0f, 1.0f, 0.4f, 0.9f, BRASS_D)
        cube(0f, 1.6f, 1.95f, 0.9f, 0.9f, 0.12f, BRASS)
        cube(0f, 1.6f, 2.05f, 0.4f, 0.4f, 0.1f, magC, true)
        for (k in 0..4) {
            if (k.toFloat() < arrows) {
                val ax = -1.0f + k * 0.5f
                val az = if (k % 2 == 0) 0.5f else -0.4f
                cube(ax, 7.2f, az, 0.18f, 2.2f, 0.18f, SHAFT)
                val fl = if (k % 3 == 0) RED else if (k % 3 == 1) WHITE else GLOW_D
                cube(ax, 9.3f, az, 0.7f, 1.1f, 0.07f, fl)
                cube(ax, 9.3f, az, 0.07f, 1.1f, 0.7f, WHITE)
            }
        }
        pop()
    }

    private fun torso(chestTw: Float, c0: Float, c1: Float, c2: Float, flapF: Float, flapB: Float, breath: Float, quiverSw: Float, scarfSw: Float, arrows: Float) {
        // quadril e cinto
        cube(0f, -1.6f, 0f, 5.4f, 2.2f, 3.3f, PANTS)
        cube(0f, 0.6f, 0f, 5.3f, 1.1f, 3.5f, LEATHER)
        cube(0f, 0.6f, 3.55f, 1.2f, 0.9f, 0.3f, BRASS)
        cube(0f, 0.6f, 3.85f, 0.6f, 0.5f, 0.15f, BRASS_L)
        cube(0f, 1.2f, 3.6f, 5.2f, 0.18f, 0.12f, LEATHER_D)
        // bolsa no quadril direito e adaga no esquerdo
        cube(-5.7f, -0.7f, 1.2f, 0.9f, 1.5f, 1.7f, LEATHER_L)
        cube(-5.7f, 0.4f, 1.2f, 1.0f, 0.4f, 1.8f, LEATHER_D)
        cube(-6.6f, -0.2f, 1.2f, 0.12f, 0.4f, 0.4f, BRASS)
        push()
        translate(5.8f, 0.3f, -0.4f)
        rotZ(-0.1f)
        cube(0.2f, -2.6f, 0f, 0.65f, 2.4f, 0.95f, LEATHER_D)
        cube(0.2f, -4.9f, 0f, 0.5f, 0.5f, 0.75f, BRASS)
        cube(0.2f, 1.2f, 0f, 0.5f, 1.0f, 0.65f, LEATHER)
        cube(0.2f, 2.3f, 0f, 0.8f, 0.25f, 0.9f, BRASS)
        pop()
        // saiotes da túnica (molas)
        push()
        translate(0f, 0.8f, 3.6f)
        rotX(flapF)
        cube(0f, -2.8f, 0f, 3.4f, 2.8f, 0.4f, TUNIC)
        cube(0f, -5.4f, 0.1f, 3.5f, 0.35f, 0.35f, BRASS_D)
        cube(0f, -1.6f, 0.45f, 0.3f, 0.3f, 0.1f, GLOW_D, true)
        pop()
        push()
        translate(0f, 0.8f, -3.6f)
        rotX(flapB)
        cube(0f, -3.0f, 0f, 4.6f, 3.0f, 0.4f, TUNIC_D)
        cube(0f, -5.8f, -0.1f, 4.7f, 0.35f, 0.35f, BRASS_D)
        pop()
        for (k in 0..1) {
            val sd = if (k == 0) 1f else -1f
            push()
            translate(sd * 5.4f, 0.6f, 0f)
            rotZ(sd * (0.06f + flapF * 0.25f))
            cube(0f, -2.4f, 0f, 0.4f, 2.4f, 3.2f, TUNIC_L)
            cube(0f, -4.9f, 0f, 0.45f, 0.3f, 3.3f, BRASS_D)
            pop()
        }
        // peito
        push()
        rotY(chestTw)
        translate(0f, breath, 0f)
        cube(0f, 5.2f, 0f, 4.7f, 0.9f, 3.2f, TUNIC_D)
        cube(0f, 9.8f, 0f, 5.5f, 4.8f, 3.4f, TUNIC)
        cube(0f, 10.0f, 3.45f, 4.3f, 4.0f, 0.2f, LEATHER)
        cube(0f, 10.0f, 3.62f, 3.9f, 3.6f, 0.08f, LEATHER_L)
        cube(0f, 7.2f, 3.5f, 4.4f, 0.3f, 0.3f, LEATHER_D)
        cube(0f, 12.9f, 3.5f, 4.2f, 0.25f, 0.28f, LEATHER_D)
        for (k in 0..2) {
            cube(0f, 8.6f + k * 1.5f, 3.75f, 0.9f, 0.25f, 0.1f, LEATHER_D)
            cube(-0.6f, 8.6f + k * 1.5f, 3.78f, 0.12f, 0.35f, 0.08f, BRASS)
            cube(0.6f, 8.6f + k * 1.5f, 3.78f, 0.12f, 0.35f, 0.08f, BRASS)
        }
        cube(2.0f, 11.6f, 3.8f, 0.55f, 0.55f, 0.12f, BRASS)
        cube(2.0f, 11.6f, 3.9f, 0.3f, 0.3f, 0.1f, magC, true)
        // bandoleira da aljava (diagonal)
        push()
        translate(0f, 10.0f, 3.75f)
        rotZ(0.62f)
        cube(0f, 0f, 0f, 0.8f, 7.6f, 0.14f, LEATHER_D)
        cube(0f, 0f, 0.16f, 0.2f, 7.6f, 0.05f, BRASS_D)
        pop()
        push()
        translate(0f, 10.0f, -3.55f)
        rotZ(-0.62f)
        cube(0f, 0f, 0f, 0.8f, 7.6f, 0.14f, LEATHER_D)
        pop()
        // manto dos ombros, cachecol e capuz dobrado
        cube(0f, 13.6f, 0f, 6.7f, 0.9f, 4.0f, CLOAK_D)
        cube(0f, 13.0f, 0f, 6.9f, 0.25f, 4.2f, CLOAK_L)
        cube(0f, 13.1f, 4.25f, 0.9f, 0.9f, 0.3f, BRASS)
        cube(0f, 13.1f, 4.55f, 0.4f, 0.4f, 0.12f, magC, true)
        cube(0f, 14.6f, 0f, 3.9f, 0.9f, 3.5f, SCARF)
        cube(0f, 14.65f, 3.55f, 3.4f, 0.6f, 0.15f, SCARF_L)
        cube(-2.9f, 13.8f, 3.7f, 1.0f, 1.0f, 0.6f, SCARF_D)
        push()
        translate(0f, 14.4f, -3.4f)
        rotX(-(0.25f + scarfSw))
        cube(-1.3f, -2.2f, -0.2f, 0.95f, 2.4f, 0.3f, SCARF)
        translate(0f, -4.6f, 0f)
        rotX(-(0.15f + scarfSw * 0.9f))
        cube(-1.3f, -1.6f, -0.2f, 0.95f, 1.8f, 0.3f, SCARF_D)
        cube(-1.3f, -3.5f, -0.2f, 0.5f, 0.3f, 0.3f, SCARF)
        pop()
        // capa curta em três segmentos
        push()
        translate(0f, 13.3f, -4.0f)
        rotX(c0)
        cube(0f, -2.7f, -0.4f, 5.7f, 2.9f, 0.35f, CLOAK_D)
        cube(0f, 0.9f, 0.1f, 6.3f, 0.8f, 1.0f, CLOAK)
        cube(-4.4f, 0.7f, 0.9f, 0.5f, 0.5f, 0.4f, BRASS_L)
        cube(4.4f, 0.7f, 0.9f, 0.5f, 0.5f, 0.4f, BRASS_L)
        translate(0f, -5.4f, 0f)
        rotX(c1)
        cube(0f, -2.5f, -0.4f, 5.9f, 2.7f, 0.35f, CLOAK)
        cube(-3.0f, -1.5f, -0.85f, 0.4f, 0.4f, 0.1f, GLOW_D, true)
        cube(2.6f, -3.4f, -0.85f, 0.35f, 0.35f, 0.1f, GLOW, true)
        cube(-5.8f, -2.5f, -0.4f, 0.25f, 2.7f, 0.5f, CLOAK_L)
        cube(5.8f, -2.5f, -0.4f, 0.25f, 2.7f, 0.5f, CLOAK_L)
        translate(0f, -5.2f, 0f)
        rotX(c2)
        cube(0f, -2.0f, -0.4f, 5.6f, 2.0f, 0.35f, CLOAK_D)
        cube(-4.2f, -4.6f, -0.4f, 1.3f, 1.1f, 0.35f, CLOAK)
        cube(-1.4f, -4.9f, -0.4f, 1.3f, 1.4f, 0.35f, CLOAK_D)
        cube(1.4f, -4.6f, -0.4f, 1.3f, 1.1f, 0.35f, CLOAK)
        cube(4.2f, -5.0f, -0.4f, 1.3f, 1.5f, 0.35f, CLOAK_D)
        pop()
        // aljava nas costas
        quiver(quiverSw, arrows)
        pop()
    }

    private fun head(turn: Float, tilt: Float, tail0: Float, tail1: Float, fth: Float) {
        push()
        translate(0f, 14.2f, 0f)
        rotY(turn)
        rotX(tilt)
        cube(0f, 0.3f, 0f, 2.3f, 1f, 2.3f, SKIN_D)
        // rosto
        cube(0f, 5f, 0f, 4.4f, 4.5f, 4.2f, SKIN)
        cube(-2f, 5.7f, 4.25f, 1.0f, 0.9f * eyeOpen, 0.12f, EYE)
        cube(2f, 5.7f, 4.25f, 1.0f, 0.9f * eyeOpen, 0.12f, EYE)
        cube(-2f, 5.6f, 4.36f, 0.55f, 0.5f * eyeOpen, 0.08f, eyeC, true)
        cube(2f, 5.6f, 4.36f, 0.55f, 0.5f * eyeOpen, 0.08f, eyeC, true)
        cube(-2f, 7.0f, 4.3f, 1.5f, 0.45f, 0.2f, HAIR_D)
        cube(2f, 7.0f, 4.3f, 1.5f, 0.45f, 0.2f, HAIR_D)
        cube(0f, 4.4f, 4.45f, 0.7f, 0.9f, 0.45f, SKIN_D)
        // mascara de pano cobrindo a boca
        cube(0f, 2.2f, 4.2f, 4.5f, 1.6f, 0.35f, CLOAK_D)
        cube(0f, 3.6f, 4.3f, 4.3f, 0.25f, 0.3f, CLOAK_L)
        cube(0f, 1.0f, 4.2f, 3.6f, 0.5f, 0.3f, TUNIC_DD)
        // franja
        cube(-3.0f, 8.2f, 4.1f, 1.7f, 0.7f, 0.3f, HAIR)
        cube(0.6f, 8.4f, 4.15f, 1.3f, 0.6f, 0.3f, HAIR)
        cube(2.9f, 8.0f, 4.1f, 1.5f, 0.8f, 0.3f, HAIR_D)
        // capuz: topo, laterais, costas e aba da frente
        cube(0f, 10.3f, -0.3f, 5.1f, 0.9f, 5.0f, CLOAK)
        cube(0f, 11.3f, -0.5f, 4.1f, 0.8f, 4.1f, CLOAK_L)
        cube(0f, 12.0f, -0.9f, 2.9f, 0.5f, 3.0f, CLOAK)
        cube(-5.0f, 5.4f, -0.6f, 0.95f, 5.0f, 4.7f, CLOAK)
        cube(5.0f, 5.4f, -0.6f, 0.95f, 5.0f, 4.7f, CLOAK)
        cube(0f, 5.0f, -5.0f, 5.2f, 5.6f, 0.9f, CLOAK_D)
        cube(0f, 9.4f, 4.4f, 5.2f, 0.9f, 1.0f, CLOAK_D)
        cube(0f, 8.8f, 4.9f, 5.0f, 0.3f, 0.3f, CLOAK_L)
        cube(-5.0f, 0.6f, 1.0f, 1.0f, 1.0f, 3.6f, CLOAK_D)
        cube(5.0f, 0.6f, 1.0f, 1.0f, 1.0f, 3.6f, CLOAK_D)
        cube(-5.5f, 7.5f, 1.2f, 0.1f, 0.8f, 0.8f, BRASS)
        // ponta do capuz em mola
        push()
        translate(0f, 11.0f, -4.4f)
        rotX(-(0.7f + tail0))
        cube(0f, 0.8f, 0f, 2.6f, 1.2f, 1.6f, CLOAK)
        translate(0f, 0f, -2.4f)
        rotX(-(0.5f + tail1))
        cube(0f, 0f, -1.0f, 1.6f, 0.9f, 1.4f, CLOAK_D)
        cube(0f, 0f, -2.6f, 0.7f, 0.5f, 0.5f, SCARF)
        pop()
        // pena vermelha em mola
        push()
        translate(-5.4f, 8.0f, 0.6f)
        rotZ(0.75f + fth)
        rotX(-0.25f)
        cube(0f, 2.6f, 0f, 0.14f, 2.8f, 0.14f, WHITE)
        cube(-0.4f, 2.4f, 0f, 0.5f, 2.2f, 0.12f, RED)
        cube(0.4f, 2.1f, 0f, 0.4f, 1.8f, 0.1f, SCARF_L)
        cube(0f, 5.4f, 0f, 0.3f, 0.5f, 0.12f, RED)
        pop()
        pop()
    }


    // ───────────── estado externo (preenchido pelo Player antes de desenhar) ─────────────
    var inAir = false
    var airH = 0f
    var airVz = 0f
    var hopP = -1f              // 0..1 durante o salto-dash; <0 = sem salto
    var trapReq = false         // o Player liga isso no instante em que a armadilha é solta
    var skillK = 0              // 0 nada, 1 chuva de flechas, 2 flecha trovão
    var skillP = -1f            // 0..1 durante a skill; <0 = sem skill
    var alphaV = 1f

    // tempos do disparo normal (fração da animação): pegar a flecha, encaixar, puxar, soltar, fim — manter T_SHOT igual a Player.shotDelay()
    private const val N_Q = 0.10f
    private const val N_N = 0.22f
    private const val N_D = 0.36f
    const val T_SHOT = 0.50f
    private const val N_E = 0.74f
    // chuva de flechas
    const val RAIN_DUR = 0.95f
    const val RAIN_REL = 0.60f
    // flecha trovão
    const val THUNDER_DUR = 1.15f
    const val THUNDER_REL = 0.70f

    // ───────────── estado de animação ─────────────
    private var yaw = 0f
    private var airW = 0f
    private var hopW = 0f
    private var aimW = 0f
    private var elevS = 0f
    private var blend = 0f
    private var clock = 0f
    private var lastNs = 0L
    private var prevAtk = 0f
    private var prevSkP = -1f
    private var wasCasting = false
    private var released = false
    private var prevPhase = 0f
    private var spd = 0f
    private var wasAir = false
    private var wasHop = false
    private var landDip = 0f
    private var airTimer = 0f
    private var prevFlash = 0f
    private var hitKick = 0f
    private var prevSin = 0f
    private var dustT = 0f
    private var recoilV = 0f
    private var flashV = 0f
    private var vibA = 0f
    private var flexX = 0f
    private var flexV = 0f
    private var volleyT = 0f
    private var boltT = 0f
    private var gatherT = 0f
    private var arcT = 0f
    private var leafT = 0f
    private var castKind = 0

    // molas: 0 inclinação, 1 banking, 2-4 capa, 5 aljava, 6 cachecol, 7 saiote da frente, 8 cabeça, 9 ponta do capuz, 10 saiote de trás, 11 pena
    private val sp = FloatArray(12)
    private val sv = FloatArray(12)

    // projeções de pontos do arco: 0 ponta de cima, 1 ponta de baixo, 2 encaixe da corda, 3 ponta da flecha
    private val pjX = FloatArray(4)
    private val pjY = FloatArray(4)

    // resultado do IK de braço
    private var ikZ = 0f
    private var ikSh = 0f
    private var ikRoll = 0f
    private var ikEl = 0f
    private var ikHX = 0f
    private var ikHY = 0f
    private var ikHZ = 0f
    // vetor rotacionado (saída dos helpers rotV*)
    private var ovX = 0f
    private var ovY = 0f
    private var ovZ = 0f

    // poeira dos passos / pouso / salto
    private const val ND = 16
    private val dX = FloatArray(ND)
    private val dY = FloatArray(ND)
    private val dVx = FloatArray(ND)
    private val dVy = FloatArray(ND)
    private val dL = FloatArray(ND)
    private val dM = FloatArray(ND)
    private val dS = FloatArray(ND)
    private var dNext = 0

    // faíscas, folhas e partículas
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

    private fun spring(i: Int, target: Float, k: Float, d: Float, dt: Float) {
        sv[i] += ((target - sp[i]) * k - sv[i] * d) * dt
        sp[i] += sv[i] * dt
    }

    private fun cl(x: Float, a: Float, b: Float): Float = max(a, min(b, x))

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

    private fun projectTo(x: Float, y: Float, z: Float, out: Int) {
        val m = stack[top]
        val wx = m[0] * x + m[1] * y + m[2] * z + m[9]
        val wy = m[3] * x + m[4] * y + m[5] * z + m[10]
        val wz = m[6] * x + m[7] * y + m[8] * z + m[11]
        pjX[out] = ox + wx * rs
        pjY[out] = oy + (wz * SINP - wy * COSP) * rs
    }

    // rotações de vetor (mesma convenção das matrizes rotX/rotY/rotZ)
    private fun rotVX(x: Float, y: Float, z: Float, a: Float) {
        val c = cos(a)
        val s = sin(a)
        ovX = x
        ovY = c * y - s * z
        ovZ = s * y + c * z
    }

    private fun rotVY(x: Float, y: Float, z: Float, a: Float) {
        val c = cos(a)
        val s = sin(a)
        ovX = c * x + s * z
        ovY = y
        ovZ = -s * x + c * z
    }

    private fun rotVZ(x: Float, y: Float, z: Float, a: Float) {
        val c = cos(a)
        val s = sin(a)
        ovX = c * x - s * y
        ovY = s * x + c * y
        ovZ = z
    }

    /** IK de dois ossos: acha os ângulos do braço para a mão chegar em (tx,ty,tz); (px,py,pz) diz para onde o cotovelo aponta. */
    private fun solveArm(sx: Float, sy: Float, sz: Float, tx: Float, ty: Float, tz: Float, px: Float, py: Float, pz: Float) {
        val a = 5.7f
        val b = 7.6f
        val dx = tx - sx
        val dy = ty - sy
        val dz = tz - sz
        val dl = max(0.001f, sqrt(dx * dx + dy * dy + dz * dz))
        val ux = dx / dl
        val uy = dy / dl
        val uz = dz / dl
        val dd = cl(dl, abs(a - b) + 0.2f, a + b - 0.05f)
        ikHX = sx + ux * dd
        ikHY = sy + uy * dd
        ikHZ = sz + uz * dd
        val ca = cl((a * a + dd * dd - b * b) / (2f * a * dd), -1f, 1f)
        val sa = sqrt(1f - ca * ca)
        val pu = px * ux + py * uy + pz * uz
        var qx = px - pu * ux
        var qy = py - pu * uy
        var qz = pz - pu * uz
        val ql = sqrt(qx * qx + qy * qy + qz * qz)
        if (ql < 0.001f) {
            qx = 0f
            qy = -1f
            qz = 0f
        } else {
            qx /= ql
            qy /= ql
            qz /= ql
        }
        val ex = sx + a * (ca * ux + sa * qx)
        val ey = sy + a * (ca * uy + sa * qy)
        val ez = sz + a * (ca * uz + sa * qz)
        val d1x = (ex - sx) / a
        val d1y = (ey - sy) / a
        val d1z = (ez - sz) / a
        val d2x = (ikHX - ex) / b
        val d2y = (ikHY - ey) / b
        val d2z = (ikHZ - ez) / b
        ikSh = -asin(cl(d1z, -1f, 1f))
        ikZ = atan2(d1x, -d1y)
        val cz = cos(ikZ)
        val szn = sin(ikZ)
        val ax = cz * d2x + szn * d2y
        val ay = -szn * d2x + cz * d2y
        val az = d2z
        val cs = cos(ikSh)
        val ss = sin(ikSh)
        val lx = ax
        val ly = cs * ay + ss * az
        val lz = -ss * ay + cs * az
        ikEl = acos(cl(-ly, -1f, 1f))
        ikRoll = atan2(-lx, -lz)
    }


    // ───────────── efeitos 2D ─────────────

    /** Raio em zigue-zague entre dois pontos (cintila a cada quadro). */
    private fun zap(c: Canvas, p: Paint, x0: Float, y0: Float, x1: Float, y1: Float, w: Float, col: Int, a: Float) {
        val dx = x1 - x0
        val dy = y1 - y0
        val len = max(1f, sqrt(dx * dx + dy * dy))
        val nx = -dy / len
        val ny = dx / len
        val segs = 6
        val rnd = kotlin.random.Random
        var px = x0
        var py = y0
        p.style = Paint.Style.STROKE
        p.strokeCap = Paint.Cap.ROUND
        for (i in 1..segs) {
            val u = i.toFloat() / segs
            val jit = if (i == segs) 0f else (rnd.nextFloat() - 0.5f) * len * 0.22f
            val qx = x0 + dx * u + nx * jit
            val qy = y0 + dy * u + ny * jit
            p.strokeWidth = w * 2.6f
            p.color = (((a * 0.35f * 255f).toInt()) shl 24) or (col and 0x00FFFFFF)
            c.drawLine(px, py, qx, qy, p)
            p.strokeWidth = w
            p.color = (((a * 255f).toInt()) shl 24) or 0x00FFFFFF
            c.drawLine(px, py, qx, qy, p)
            px = qx
            py = qy
        }
        p.strokeCap = Paint.Cap.BUTT
        p.style = Paint.Style.FILL
    }

    /** Anel mágico no chão: arcos girando e pontas de flecha apontando para fora. */
    private fun ring(c: Canvas, p: Paint, cx: Float, cy: Float, ca: Float, r0: Float, col: Int, colL: Int, spin: Float) {
        val rr = r0 * rs
        val ry = rr * SINP
        p.style = Paint.Style.STROKE
        p.strokeWidth = max(1.6f, rs * 1.1f)
        p.color = (((ca * 0.75f * 255f).toInt()) shl 24) or (col and 0x00FFFFFF)
        c.drawOval(cx - rr, cy - ry, cx + rr, cy + ry, p)
        p.strokeWidth = max(1.2f, rs * 0.7f)
        p.color = (((ca * 0.55f * 255f).toInt()) shl 24) or (colL and 0x00FFFFFF)
        c.drawOval(cx - rr * 0.7f, cy - ry * 0.7f, cx + rr * 0.7f, cy + ry * 0.7f, p)
        p.strokeCap = Paint.Cap.ROUND
        p.strokeWidth = max(1.8f, rs * 1.2f)
        p.color = (((ca * 0.95f * 255f).toInt()) shl 24) or (colL and 0x00FFFFFF)
        for (k in 0..7) {
            val ang = clock * spin + k * 0.7854f
            val c0 = cos(ang)
            val s0 = sin(ang)
            c.drawLine(cx + c0 * rr * 0.80f, cy + s0 * ry * 0.80f, cx + c0 * rr * 1.08f, cy + s0 * ry * 1.08f, p)
            c.drawLine(cx + c0 * rr * 1.08f, cy + s0 * ry * 1.08f, cx + cos(ang + 0.16f) * rr * 0.98f, cy + sin(ang + 0.16f) * ry * 0.98f, p)
            c.drawLine(cx + c0 * rr * 1.08f, cy + s0 * ry * 1.08f, cx + cos(ang - 0.16f) * rr * 0.98f, cy + sin(ang - 0.16f) * ry * 0.98f, p)
        }
        p.strokeCap = Paint.Cap.BUTT
        p.style = Paint.Style.FILL
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
        val rnd = kotlin.random.Random

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

        val dPh = abs(phase - prevPhase)
        prevPhase = phase
        val spdTgt = if (first) 0f else (dPh / dt / 9.6f).coerceIn(0f, 1.6f)
        spd += (spdTgt - spd) * min(1f, dt * 10f)
        val spN = min(spd, 1f)

        // ── qual gesto: 1 tiro normal, 3 chuva de flechas (skillK 1), 4 flecha trovão (skillK 2) ──
        val skilling = skillK > 0 && skillP >= 0f
        var t = -1f
        var kind = 0
        var tQ = N_Q
        var tN = N_N
        var tD = N_D
        var tR = T_SHOT
        var tE = N_E
        if (skilling) {
            t = skillP
            kind = skillK + 2
            if (skillK == 1) {
                tQ = 0.10f
                tN = 0.20f
                tD = 0.50f
                tR = RAIN_REL
                tE = 0.84f
            } else {
                tQ = 0.08f
                tN = 0.16f
                tD = 0.62f
                tR = THUNDER_REL
                tE = 0.86f
            }
        } else if (attack > 0.001f) {
            t = 1f - attack
            kind = 1
        }
        val casting = t >= 0f
        val newCast = casting && (!wasCasting || (!skilling && attack > prevAtk + 0.3f) || (skilling && skillP < prevSkP - 0.3f))
        if (newCast) {
            released = false
            sv[2] += 2f; sv[3] += 3f; sv[4] += 3f
            sv[5] += 2f; sv[9] += 3f
        }
        if (!casting) released = false
        wasCasting = casting
        prevAtk = attack
        prevSkP = if (skilling) skillP else -1f
        castKind = kind

        // ── estados de movimento: ar, salto-dash, dano ──
        val hopping = hopP >= 0f
        val airCand = inAir && !hopping && (airH > 8f || airVz > 60f)
        airTimer = if (airCand) airTimer + dt else 0f
        val airT = if (airTimer > 0.04f) 1f else 0f
        val hopTgt = if (hopping) 1f else 0f
        if (first) {
            airW = airT
            hopW = hopTgt
        } else {
            airW += (airT - airW) * min(1f, dt * (if (airT > airW) 16f else 11f))
            hopW += (hopTgt - hopW) * min(1f, dt * 30f)
        }
        var landPuff = false
        var takeoffPuff = false
        if (wasAir && airW < 0.4f) {
            sv[2] -= 3f; sv[3] -= 4f; sv[4] -= 4f; sv[0] += 2.2f; sv[7] -= 3f; sv[9] -= 3f; sv[5] -= 2f
            wasAir = false
            landPuff = true
            landDip = 1f
        } else if (airW > 0.7f) {
            if (!wasAir) takeoffPuff = true
            wasAir = true
        }
        if (hopping && !wasHop) takeoffPuff = true
        if (!hopping && wasHop) {
            landPuff = true
            landDip = max(landDip, 0.9f)
            sv[2] -= 4f; sv[3] -= 5f; sv[4] -= 5f; sv[9] -= 4f
        }
        wasHop = hopping
        val fl = V.flash
        if (!first && fl > prevFlash + 0.25f) {
            hitKick = 1f
            sv[0] -= 6f; sv[2] += 4f; sv[3] += 5f; sv[4] += 6f; sv[7] += 3f; sv[8] += 6f; sv[9] += 6f
        }
        prevFlash = fl
        hitKick = max(0f, hitKick - dt * 4.5f)
        landDip = max(0f, landDip - dt * 5.5f)
        flashV = max(0f, flashV - dt * 3.5f)
        recoilV = max(0f, recoilV - dt * 5f)
        vibA = max(0f, vibA - dt * 4.5f)
        volleyT = max(0f, volleyT - dt * 2.2f)
        boltT = max(0f, boltT - dt * 5f)
        val bp = clock % 3.7f
        eyeOpen = if (bp < 0.14f) max(0.12f, abs(bp - 0.07f) / 0.07f) else 1f
        val vzN = (airVz / 340f).coerceIn(-1.2f, 1f)
        val tuck = max(hopW * 0.9f, airW * (0.30f + 0.35f * max(0f, vzN) - 0.2f * max(0f, -vzN)))
        val still = 1f - max(hopW, airW)
        val mvs = still * (if (casting) blend * 0.3f else blend)

        // ── linha do tempo do gesto: mira, flecha na aljava, puxada, elevação ──
        var quiverT = 0f
        var drawT = 0f
        var elevT = 0f
        var crouchT = 0f
        var chargeG = 0f
        var lunge = 0f
        if (casting) {
            if (t < tQ) {
                quiverT = ease(t / tQ)
            } else if (t < tN) {
                quiverT = 1f - ease((t - tQ) / (tN - tQ))
            }
            if (t >= tN && t < tD) drawT = ease((t - tN) / (tD - tN))
            if (t >= tD && t < tR) drawT = 1f
            if (kind == 3) {
                elevT = 1.05f * ease(t / tD)
                if (t > tE) elevT = 1.05f * (1f - ease((t - tE) / (1f - tE)))
                crouchT = 0.25f
            } else if (kind == 4) {
                crouchT = 0.45f * ease(t / tD)
                lunge = if (t >= tR) -sin(min(1f, (t - tR) / 0.2f) * PI.toFloat()) * 1.8f else 0f
            } else {
                crouchT = 0.2f
            }
            chargeG = if (kind >= 3) drawT else 0f
            if (t >= tR && !released) {
                released = true
                flashV = 1f
                recoilV = 1f
                vibA = 1f
                if (kind == 3) volleyT = 1f
                if (kind == 4) boltT = 1f
                sv[0] -= 2f; sv[2] += 3f; sv[3] += 4f; sv[4] += 5f; sv[9] += 4f; sv[5] += 3f
                sv[6] += 4f
            }
        }
        val aimTarget = if (casting && t < tE) 1f else 0f
        aimW += (aimTarget - aimW) * min(1f, dt * (if (aimTarget > aimW) 22f else 7f))
        elevS += (elevT - elevS) * min(1f, dt * 14f)

        // ── cores: verde da floresta; trovão fica amarelo-elétrico ──
        val pulse = 0.5f + 0.5f * sin(clock * 3.4f)
        if (kind == 4) {
            magC = 0xFFFFF27A.toInt()
            magL = 0xFFC9F6FF.toInt()
            eyeC = 0xFFFFF27A.toInt()
        } else {
            val q = (0.4f + 0.3f * pulse + (if (kind == 3) 0.5f * drawT else 0f)).coerceIn(0f, 1f)
            magC = (0xFF shl 24) or ((90 + 50 * q).toInt() shl 16) or ((230 + 25 * q).toInt() shl 8) or (110 + 40 * q).toInt()
            magL = (0xFF shl 24) or ((200 + 55 * q).toInt() shl 16) or (255 shl 8) or (205 + 40 * q).toInt()
            eyeC = magC
        }
        eyeOpen *= (1f - 0.45f * aimW)
        val bowGlow = if (kind == 3) 0.35f + 0.65f * drawT else if (kind == 4) 0.25f + 0.75f * drawT else 0.12f * aimW
        arrowOn = if (casting && t > tQ && t < tR) 1f else 0f

        // ── molas ──
        val bankT = (-yawVel * 0.03f).coerceIn(-0.22f, 0.22f) * spN
        spring(0, 0.10f * spN, 90f, 12f, sdt)
        spring(1, bankT, 80f, 9f, sdt)
        val capeBase = 0.12f + 0.30f * spN + hopW * 0.9f + airW * (0.35f - 0.3f * vzN) +
            0.09f * sin(clock * 3f + phase * 2f) * (0.4f + spN)
        spring(2, capeBase, 36f, 5.5f, sdt)
        spring(3, capeBase * 0.8f + 0.08f, 27f, 4.5f, sdt)
        spring(4, capeBase * 0.7f + 0.10f, 19f, 3.8f, sdt)
        spring(5, 0.04f * spN + 0.10f * airW, 55f, 6f, sdt)
        spring(6, 0.18f * spN + 0.35f * airW + 0.5f * hopW, 40f, 4.5f, sdt)
        spring(7, 0.03f - 0.2f * mvs * abs(sin(phase)) - 0.1f * airW, 60f, 7f, sdt)
        spring(8, (yawVel * 0.03f).coerceIn(-0.35f, 0.35f), 70f, 9f, sdt)
        spring(9, 0.10f + 0.30f * spN + 0.25f * airW + 0.5f * hopW, 30f, 4f, sdt)
        spring(10, 0.06f + 0.5f * spN + 0.5f * airW + 0.6f * hopW, 45f, 5.5f, sdt)
        spring(11, 0.05f + 0.25f * spN + 0.3f * hopW, 50f, 5f, sdt)
        // limbos do arco: mola com sobra (a corda estala ao soltar)
        flexV += ((drawT - flexX) * 520f - flexV * 13f) * sdt
        flexX += flexV * sdt

        val bob = (0.5f + 0.5f * cos(phase * 2f)) * 1.2f * mvs + sin(clock * 2.2f) * 0.3f * (1f - mvs)
        val breath = sin(clock * 2.2f) * 0.26f * (1f - mvs * 0.6f)
        val crouch = max(max(crouchT * aimW, landDip * 0.9f), hopW * 0.55f)
        val leanBack = if (kind == 3) -0.22f * aimW else 0f
        val lean = sp[0] + leanBack - hitKick * 0.3f + landDip * 0.2f - recoilV * 0.12f + hopW * 0.35f + aimW * 0.05f
        val hipY = 12.6f + bob - crouch * 3f - hitKick * 0.9f
        val wob = sin(phase) * 0.10f * mvs
        val twIdle = -0.10f - sin(phase) * 0.20f * mvs
        val chestTw = twIdle + (-1.2f - twIdle) * aimW - recoilV * 0.18f * aimW

        // ── alvos das mãos (referencial do quadril; o peito gira em chestTw) ──
        val dirY = sin(elevS)
        val dirZ = cos(elevS)
        val ax0 = -3.4f
        val ay0 = 16.4f + 1.2f * sin(elevS)
        val az0 = 3.2f
        val gAimX = ax0
        val gAimY = ay0 + dirY * 13.8f
        val gAimZ = az0 + dirZ * 13.8f + 0.9f * recoilV
        val swg = sin(phase) * 4.5f * mvs
        val stp = abs(sin(phase)) * 0.8f * mvs
        var gRX = 9.0f
        var gRY = 8.4f + stp
        var gRZ = 5.6f - swg * 0.5f
        var rRX = -8.6f
        var rRY = 8.0f + stp
        var rRZ = 2.4f + swg
        if (airW > 0.001f) {
            gRX = lerp(gRX, 8.8f, airW); gRY = lerp(gRY, 13.6f, airW); gRZ = lerp(gRZ, 4.5f, airW)
            rRX = lerp(rRX, -8.8f, airW); rRY = lerp(rRY, 12.6f, airW); rRZ = lerp(rRZ, 0.5f, airW)
        }
        if (hopW > 0.001f) {
            gRX = lerp(gRX, 7.4f, hopW); gRY = lerp(gRY, 9.4f, hopW); gRZ = lerp(gRZ, 5.4f, hopW)
            rRX = lerp(rRX, -6.6f, hopW); rRY = lerp(rRY, 9.2f, hopW); rRZ = lerp(rRZ, 4.2f, hopW)
        }
        val gX = lerp(gRX, gAimX, aimW)
        val gY = lerp(gRY, gAimY, aimW)
        val gZ = lerp(gRZ, gAimZ, aimW)
        val b1 = lerp(0.5f, -elevS - 0.12f * recoilV, aimW)
        val b2 = lerp(-0.3f, 0f, aimW)
        val nD = 2f + 11.6f * drawT
        // posição do encaixe da corda no referencial do quadril
        rotVZ(0.35f, 0f, -nD, b2)
        val n1x = ovX
        val n1y = ovY
        val n1z = ovZ
        rotVX(n1x, n1y, n1z, b1)
        val nkX = gX + ovX
        val nkY = gY + ovY
        val nkZ = gZ + ovZ - 3.4f * recoilV
        val hRX = lerp(rRX, nkX, aimW)
        val hRY = lerp(rRY, nkY, aimW)
        val hRZ = lerp(rRZ, nkZ, aimW)
        // hip -> peito: gira por -chestTw e tira a respiração
        rotVY(gX, gY, gZ, -chestTw)
        val gCX = ovX
        val gCY = ovY - breath
        val gCZ = ovZ
        rotVY(hRX, hRY, hRZ, -chestTw)
        var rCX = ovX
        var rCY = ovY - breath
        var rCZ = ovZ
        // buscando a flecha na aljava (nas costas, por cima do ombro direito)
        rCX = lerp(rCX, -5.2f, quiverT)
        rCY = lerp(rCY, 17.0f, quiverT)
        rCZ = lerp(rCZ, -5.0f, quiverT)
        solveArm(7.2f, 12.8f, 0f, gCX, gCY, gCZ, 0.7f, -0.9f, 0f)
        val zL = ikZ
        val shL = ikSh
        val rollL = ikRoll
        val elL = ikEl
        val hLX = ikHX
        val hLY = ikHY
        val hLZ = ikHZ
        solveArm(-7.2f, 12.8f, 0f, rCX, rCY, rCZ, lerp(0.2f, -0.5f, aimW), lerp(-0.3f, 0.5f, aimW), -1f)
        val zR = ikZ
        val shR = ikSh
        val rollR = ikRoll
        val elR = ikEl
        val hRXs = ikHX
        val hRYs = ikHY
        val hRZs = ikHZ

        // ── montagem do corpo ──
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
        translate(0f, 0f, lunge - hitKick * 1.6f)
        if (hopping) {
            val r = ((hopP - 0.08f) / 0.80f).coerceIn(0f, 1f)
            val ang = (r * 0.55f + r * r * (3f - 2f * r) * 0.45f) * TWO_PI
            val lift = 3.5f * sin(r * PI.toFloat())
            translate(0f, 10f + lift, 0f)
            rotX(ang)
            translate(0f, -11f, 0f)
        }

        // pernas: passada, postura aberta ao mirar, encolhidas no ar e na cambalhota
        val spreadAim = 0.26f * aimW
        for (k in 0..1) {
            val sd = if (k == 0) 1f else -1f
            val pp = if (sd > 0f) phase else phase + PI.toFloat()
            var a = sin(pp) * 0.7f * mvs
            var kn = max(0f, -cos(pp)) * 1.1f * mvs + 0.08f + crouch * 0.9f
            var ft = max(0f, cos(pp)) * 0.4f * mvs + airW * (0.55f * max(0f, vzN) - 0.25f * max(0f, -vzN))
            a -= sd * 0.2f * aimW
            if (sd > 0f) kn += 0.25f * aimW
            a = a * (1f - airW) - tuck + sd * 0.3f * airW
            kn = (kn + tuck * 1.6f + (if (sd > 0f) 0.5f else -0.1f) * airW).coerceAtLeast(0f)
            if (hopW > 0.001f) {
                a = lerp(a, -1.15f + sd * 0.1f, hopW)
                kn = lerp(kn, 1.9f, hopW)
                ft = lerp(ft, 0.5f, hopW)
            }
            leg(sd, a, kn, hipY, ft, spreadAim + 0.04f)
        }

        push()
        translate(0f, hipY, 0f)
        rotY(wob)
        rotX(lean)
        push()
        val arrowsLeft = 5f - (if (casting && t > tR) 1f else 0f) * 0f
        torso(chestTw, sp[2], sp[3], sp[4], sp[7].coerceIn(-0.2f, 0.2f), sp[10], breath, sp[5], sp[6], arrowsLeft)
        push()
        rotY(chestTw)
        translate(0f, breath, 0f)
        head(-chestTw * 0.92f + sp[8], -lean * 0.6f - elevS * 0.5f * aimW, sp[9], sp[9] * 0.8f, sp[11])
        arm(-1f, -7.2f, zR, shR, rollR, elR, 0f)
        arm(1f, 7.2f, zL, shL, rollL, elL, if (casting && kind >= 3) chargeG * 0.8f else 0f)
        // arco na mão esquerda (alinhado ao quadril, não ao peito)
        push()
        translate(hLX, hLY, hLZ)
        rotY(-chestTw)
        rotX(b1)
        rotZ(b2)
        rotY(-0.6f * (1f - aimW))
        nockD = nD + sin(clock * 58f) * 1.3f * vibA
        bowModel(flexX.coerceIn(-0.35f, 1.15f), bowGlow)
        if (arrowOn > 0.5f) arrowModel(bowGlow)
        pop()
        pop()
        pop()
        pop()

        // anéis mágicos no chão enquanto a skill está sendo preparada
        val ringA = if (kind >= 3) (if (t < tE) 1f else max(0f, 1f - (t - tE) / (1f - tE))) else 0f
        // corda: por baixo do corpo se ele está de costas; por cima se de frente
        val stringFront = cos(yaw) > 0.15f
        if (!stringFront) drawString(c, p, kind, drawT)

        // poeira dos passos, pouso e salto
        val sph = sin(phase)
        if (mvs > 0.5f && spN > 0.45f && ((sph > 0f) != (prevSin > 0f))) {
            emitDust(
                fx - sin(yaw) * 4f * rs + (rnd.nextFloat() - 0.5f) * 3f * rs,
                fy - cos(yaw) * 4f * rs * SINP,
                (rnd.nextFloat() - 0.5f) * rs * 10f, -rs * (5f + rnd.nextFloat() * 7f), rs * (1.3f + rnd.nextFloat() * 0.8f)
            )
        }
        prevSin = sph
        if (takeoffPuff) {
            for (i in 0..6) {
                val a = i * 0.9f
                emitDust(fx + cos(a) * 2.5f * rs, fy + sin(a) * 1.2f * rs, cos(a) * rs * 24f, sin(a) * rs * 8f - rs * 2f, rs * 1.9f)
            }
        }
        if (landPuff) {
            for (i in 0..8) {
                val a = i * 0.7f
                emitDust(fx + cos(a) * 3f * rs, fy + sin(a) * 1.5f * rs, cos(a) * rs * 30f, sin(a) * rs * 10f - rs * 3f, rs * 2.3f)
            }
        }
        if (hopping) {
            dustT -= dt
            if (dustT <= 0f) {
                dustT = 0.04f
                emitDust(fx + (rnd.nextFloat() - 0.5f) * 5f * rs, fy, (rnd.nextFloat() - 0.5f) * rs * 14f, -rs * 6f, rs * 2.2f)
            }
        }
        // armadilha plantada: anel de faíscas ao redor dos pés
        if (trapReq) {
            trapReq = false
            for (i in 0..13) {
                val ang = i * 0.4488f
                emitSpark(
                    fx + cos(ang) * 5f * rs, fy + sin(ang) * 3f * rs,
                    cos(ang) * 36f * rs, sin(ang) * 18f * rs - 8f * rs, 0.35f + rnd.nextFloat() * 0.15f, 30f * rs,
                    if (i % 2 == 0) 0xFFD6FFD9.toInt() else 0xFF7CFF8A.toInt(), 1.0f
                )
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

        // anel no chão (antes do corpo, para ficar embaixo dele)
        if (ringA > 0.03f) {
            val col = if (kind == 4) 0xFFFFF27A.toInt() else 0xFF7CFF8A.toInt()
            val colL = if (kind == 4) 0xFFC9F6FF.toInt() else 0xFFD6FFD9.toInt()
            ring(c, p, fx, fy, ringA, 15f + 6f * ringA, col, colL, if (kind == 4) 3.6f else 1.8f)
        }

        // rastro de vento do salto (linhas atrás do corpo)
        if (hopping) {
            val u = hopP.coerceIn(0f, 1f)
            val bx = -sin(yaw)
            val by = -cos(yaw) * SINP
            p.style = Paint.Style.STROKE
            p.strokeCap = Paint.Cap.ROUND
            for (k in 0..3) {
                val off = (k - 1.5f) * 4.5f * rs
                val len = (16f + 10f * k) * rs
                val a = 0.5f * (1f - u * 0.6f) / (1f + k * 0.5f)
                p.strokeWidth = max(1.5f, rs * (1.6f - k * 0.25f))
                p.color = (((a * 255f).toInt()) shl 24) or 0x00D6FFD9
                val sx0 = fx + bx * 6f * rs - by * off * 0.4f
                val sy0 = fy - 14f * rs + by * 2f * rs + off * 0.8f - airH * 0.3f
                c.drawLine(sx0, sy0, sx0 + bx * len, sy0 + by * len, p)
            }
            p.strokeCap = Paint.Cap.BUTT
            p.style = Paint.Style.FILL
        }

        flushZ(c)

        if (stringFront) drawString(c, p, kind, drawT)

        // ── brilho do arco, centelhas elétricas e partículas de folhas ──
        if (casting && kind >= 3) {
            val g = if (kind == 4) magC else GLOW
            // energia sendo sugada para a ponta da flecha enquanto carrega
            if (t < tR) {
                gatherT -= dt
                if (gatherT <= 0f) {
                    gatherT = 0.03f
                    val ang = rnd.nextFloat() * TWO_PI
                    val dist = (10f + rnd.nextFloat() * 10f) * rs
                    val life = 0.2f
                    emitSpark(
                        pjX[3] + cos(ang) * dist, pjY[3] + sin(ang) * dist * 0.8f,
                        -cos(ang) * dist / life, -sin(ang) * dist * 0.8f / life,
                        life, 0f, if (rnd.nextFloat() > 0.5f) magL else g, 0.9f
                    )
                }
            }
            if (kind == 4 && drawT > 0.05f && t < tR) {
                // arcos elétricos pulando entre as pontas do arco e a flecha
                arcT -= dt
                val aa = 0.45f + 0.55f * drawT
                zap(c, p, pjX[0], pjY[0], pjX[3], pjY[3], max(1.2f, rs * 0.7f), 0xFFFFF27A.toInt(), aa)
                zap(c, p, pjX[1], pjY[1], pjX[3], pjY[3], max(1.2f, rs * 0.7f), 0xFFC9F6FF.toInt(), aa)
                if (rnd.nextFloat() < 0.5f) {
                    zap(c, p, pjX[2], pjY[2], pjX[3] + (rnd.nextFloat() - 0.5f) * 14f * rs, pjY[3] - rnd.nextFloat() * 14f * rs, max(1f, rs * 0.5f), 0xFFFFF27A.toInt(), aa)
                }
            }
        }
        // folhas e faíscas ao redor do arqueiro enquanto prepara a chuva
        if (casting && kind == 3 && t < tR) {
            leafT -= dt
            if (leafT <= 0f) {
                leafT = 0.05f
                val ang = rnd.nextFloat() * TWO_PI
                emitSpark(
                    fx + cos(ang) * 14f * rs, fy - 4f * rs + sin(ang) * 6f * rs,
                    -cos(ang) * rs * 5f, -rs * (14f + rnd.nextFloat() * 16f), 0.7f + rnd.nextFloat() * 0.4f, -rs * 4f,
                    if (rnd.nextFloat() > 0.5f) 0xFF7CFF8A.toInt() else 0xFFD6FFD9.toInt(), 0.9f
                )
            }
        }
        if (!casting && mvs < 0.3f && !hopping) {
            leafT -= dt
            if (leafT <= 0f) {
                leafT = 0.7f + rnd.nextFloat() * 0.8f
                emitSpark(
                    fx + (rnd.nextFloat() - 0.5f) * 12f * rs, fy - 26f * rs,
                    (rnd.nextFloat() - 0.5f) * rs * 6f, -rs * (3f + rnd.nextFloat() * 3f), 0.9f, rs * 6f,
                    0xFF8FE88F.toInt(), 0.65f
                )
            }
        }
        // ── disparo: clarão, anel de choque, faíscas ──
        if (casting && released && flashV > 0.02f) {
            val tipx = pjX[3]
            val tipy = pjY[3]
            if (flashV > 0.96f) {
                val n0 = if (kind == 4) 22 else if (kind == 3) 16 else 10
                for (i in 0 until n0) {
                    val ang = rnd.nextFloat() * TWO_PI
                    val sp0 = (14f + rnd.nextFloat() * (if (kind == 4) 60f else 34f)) * rs
                    emitSpark(
                        tipx, tipy, cos(ang) * sp0, sin(ang) * sp0 - rs * 6f,
                        0.28f + rnd.nextFloat() * 0.22f, rs * 70f,
                        if (i % 3 == 0) 0xFFFFFFFF.toInt() else if (i % 3 == 1) magL else magC, 1.1f
                    )
                }
                if (kind == 3) {
                    // a salva sobe: três flechas brilhantes riscando o céu
                    for (k in -1..1) {
                        emitSpark(tipx, tipy, k * rs * 14f, -rs * 340f, 0.5f, 0f, magL, 1.5f)
                        emitSpark(tipx, tipy, k * rs * 14f, -rs * 260f, 0.55f, 0f, magC, 1.2f)
                    }
                }
            }
            val r0 = (3.2f + 2.6f * flashV * (if (kind == 4) 2.2f else 1f)) * rs
            p.style = Paint.Style.FILL
            p.color = (((0.10f + 0.2f * flashV).coerceAtMost(0.5f) * 255f).toInt() shl 24) or (magC and 0x00FFFFFF)
            c.drawCircle(tipx, tipy, r0 * 2.6f, p)
            p.color = (((0.20f + 0.3f * flashV).coerceAtMost(0.7f) * 255f).toInt() shl 24) or (magL and 0x00FFFFFF)
            c.drawCircle(tipx, tipy, r0 * 1.5f, p)
            p.color = (((0.35f + 0.4f * flashV).coerceAtMost(0.9f) * 255f).toInt() shl 24) or 0x00FFFFFF
            c.drawCircle(tipx, tipy, r0 * 0.7f, p)
            val u = 1f - flashV
            val rr = (4f + (if (kind == 4) 46f else 24f) * (1f - (1f - u) * (1f - u))) * rs
            p.style = Paint.Style.STROKE
            p.strokeWidth = max(2f, rs * 2.2f * flashV)
            p.color = (((flashV * 0.9f * 255f).toInt()) shl 24) or (magL and 0x00FFFFFF)
            c.drawCircle(tipx, tipy, rr, p)
            p.strokeWidth = max(1.4f, rs * 1.1f)
            p.color = (((flashV * 0.7f * 255f).toInt()) shl 24) or (magC and 0x00FFFFFF)
            c.drawCircle(tipx, tipy, rr * 0.7f, p)
            p.style = Paint.Style.FILL
        }
        if (boltT > 0.05f) {
            // raios saindo da ponta do arco no disparo do trovão
            for (k in 0..4) {
                val ang = (k - 2) * 0.55f + (rnd.nextFloat() - 0.5f) * 0.3f - 1.57f
                val len = (22f + rnd.nextFloat() * 26f) * rs * (1.2f - boltT * 0.4f)
                zap(c, p, pjX[3], pjY[3], pjX[3] + cos(ang) * len, pjY[3] + sin(ang) * len, max(1.4f, rs * 0.9f), 0xFFFFF27A.toInt(), boltT)
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

    /** Corda do arco (linha 2D entre as pontas e o encaixe) com brilho quando a skill está carregada. */
    private fun drawString(c: Canvas, p: Paint, kind: Int, drawT: Float) {
        p.style = Paint.Style.STROKE
        p.strokeCap = Paint.Cap.ROUND
        val w = max(1f, rs * 0.38f)
        if (kind >= 3 && drawT > 0.05f) {
            p.strokeWidth = w * 3.2f
            p.color = (((0.35f * drawT * 255f).toInt()) shl 24) or (magC and 0x00FFFFFF)
            c.drawLine(pjX[0], pjY[0], pjX[2], pjY[2], p)
            c.drawLine(pjX[2], pjY[2], pjX[1], pjY[1], p)
        }
        p.strokeWidth = w
        p.color = V.tint(STRING)
        c.drawLine(pjX[0], pjY[0], pjX[2], pjY[2], p)
        c.drawLine(pjX[2], pjY[2], pjX[1], pjY[1], p)
        p.strokeCap = Paint.Cap.BUTT
        p.style = Paint.Style.FILL
    }
}
