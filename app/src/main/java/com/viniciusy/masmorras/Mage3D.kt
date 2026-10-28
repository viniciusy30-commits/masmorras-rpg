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
 * Mini motor 3D de cubos (estilo Minecraft Dungeons) desenhado em Canvas, igual ao do Guerreiro.
 * O Mago é um esqueleto de partes articuladas: pernas com joelho, quadril/peito com contra-rotação,
 * cabeça, dois braços, cajado com orbe flutuante, manto e capa longa, barba e ponta do chapéu em molas.
 * O feitiço tem preparação (cajado erguido, energia sendo sugada), disparo (clarão, onda e faíscas) e recuperação.
 */
object Mage3D {
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
        if (n >= MAX || Roupas.ocultar) return
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

    // ───────────── paleta do mago ─────────────
    private val ROBE: Int get() = HeroLook.tecido(0xFF2E5CB8.toInt())
    private val ROBE_L: Int get() = HeroLook.tecido(0xFF4C80DE.toInt())
    private val ROBE_D: Int get() = HeroLook.tecido(0xFF1F3F86.toInt())
    private val ROBE_DD: Int get() = HeroLook.tecido(0xFF142A5E.toInt())
    private val GOLD: Int get() = HeroLook.metal(0xFFF2B72E.toInt())
    private val GOLD_L: Int get() = HeroLook.metal(0xFFFFE08A.toInt())
    private val GOLD_D: Int get() = HeroLook.metal(0xFFB88413.toInt())
    private val LEATHER = 0xFF5A3A26.toInt()
    private val LEATHER_L = 0xFF7A5238.toInt()
    private val LEATHER_D = 0xFF3A2416.toInt()
    private val SKIN: Int get() = HeroLook.skin(0xFFF1C27D.toInt())
    private val SKIN_D: Int get() = HeroLook.skinD(0xFFC98F55.toInt())
    private val HAIR: Int get() = HeroLook.hair(0xFFEDEFF3.toInt())
    private val HAIR_D: Int get() = HeroLook.hairD(0xFFB9C1CE.toInt())
    private val SHAFT = 0xFF8A5A33.toInt()
    private val SHAFT_L = 0xFFAD7842.toInt()
    private val SHAFT_D = 0xFF5E3B20.toInt()
    private val PANTS = 0xFF1B2547.toInt()
    private val EYE = 0xFF1B1B1B.toInt()
    private val WHITE = 0xFFFFFFFF.toInt()
    private val ARC = 0xFF5FD4FF.toInt()
    private val ARC_L = 0xFFC4F4FF.toInt()
    private val ARC_D = 0xFF2A8FD6.toInt()
    private val VIO = 0xFF9B6BFF.toInt()
    private val VIO_D = 0xFF5B34B8.toInt()
    private val TOME = 0xFF7A1F2B.toInt()
    private val TOME_D = 0xFF4E1019.toInt()
    private val GLASS = 0xFFB7E6F2.toInt()
    // cores que pulsam (atualizadas a cada quadro em draw)
    private var orbC = 0xFF5FD4FF.toInt()
    private var orbL = 0xFFC4F4FF.toInt()
    private var eyeC = 0xFF5FD4FF.toInt()

    // ───────────── estado externo (preenchido pelo Player antes de desenhar) ─────────────
    var rollP = -1f
    var inAir = false
    var airVz = 0f
    var airH = 0f
    var flying = false          // voando (segurando o botão de pular)
    var tpP = -1f               // 0..1 durante o teleporte; <0 = sem teleporte
    var tpOffX = 0f             // origem do teleporte (px de tela, relativa ao herói)
    var tpOffY = 0f
    var alphaV = 1f             // transparência do herói (materialização)

    // ───────────── estado de animação ─────────────
    private var yaw = 0f
    private var airW = 0f
    private var rollW = 0f
    private var blend = 0f
    private var clock = 0f
    private var lastNs = 0L
    private var tipX = 0f
    private var tipY = 0f
    private var midX = 0f
    private var midY = 0f

    // rastro arcano do orbe
    private val trX = FloatArray(24)
    private val trY = FloatArray(24)
    private var trN = 0
    private const val TRAIL_MAX = 12

    // molas: 0 inclinação, 1 banking, 2-4 capa, 5-6 barba, 7 saia da frente, 8 cabeça, 9 ponta do chapéu, 10 saia de trás
    private val sp = FloatArray(12)
    private val sv = FloatArray(12)
    private var castW = 0f
    private var flyW = 0f
    private var prevTp = -1f
    private var jetT = 0f
    private var released = false
    private var flashV = 0f
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
    private var moteT = 0f
    private var gatherT = 0f
    private var orbS = 1f
    private var orbBob = 0f
    private var handGlow = 0f
    private var crystalBob = 0f

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

    // faíscas e partículas arcanas
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

    // chaves do feitiço: sh, shZ, el, wrist, tw, crouch, lean, hipTw, lsh, lshZ, lel
    private const val NK = 11
    private val kS = FloatArray(NK)
    private val kI = FloatArray(NK)
    private val kO = FloatArray(NK)
    private val lastPose = FloatArray(NK)
    // W: cajado erguido sobre a cabeça, mão livre junta a energia
    private val K_W = floatArrayOf(-2.2f, 0.12f, -0.8f, 3.1f, -0.45f, 0.18f, -0.22f, 0.35f, -1.35f, 0.35f, -0.45f)
    // M: instante do disparo, cajado aponta para a frente
    private val K_M = floatArrayOf(-1.45f, 0.08f, -0.1f, 2.6f, 0.35f, 0.5f, 0.28f, -0.25f, -0.5f, 0.3f, -0.2f)
    // E: fim do movimento
    private val K_E = floatArrayOf(-1.2f, 0.1f, -0.3f, 2.3f, 0.2f, 0.45f, 0.22f, -0.12f, -0.3f, 0.2f, -0.3f)
    // tempos (fração da animação): fim da preparação, disparo, fim do movimento — manter igual a Player.castDelay()
    const val T_W = 0.26f
    const val T_M = 0.34f
    private const val T_E = 0.56f

    private fun spring(i: Int, target: Float, k: Float, d: Float, dt: Float) {
        sv[i] += ((target - sp[i]) * k - sv[i] * d) * dt
        sp[i] += sv[i] * dt
    }

    private fun mixK(a: FloatArray, b: FloatArray, u: Float) {
        for (i in 0 until NK) kO[i] = lerp(a[i], b[i], u)
    }

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

    // ───────────── partes do corpo ─────────────

    private fun leg(side: Float, a: Float, knee: Float, hipY: Float, foot: Float) {
        push()
            translate(side * 3f * Roupas.quad(Classe.MAGO), hipY, 0f)
            rotX(a)
            Roupas.perna(hg, Classe.MAGO)
            Roupas.coxa(hg, Classe.MAGO, side)
            Roupas.ocultar = HeroLook.pernas > 0
            cube(0f, -2.7f, 0f, 2.1f, 2.9f, 2.2f, PANTS)
            translate(0f, -5.6f, 0f)
            rotX(knee)
            Roupas.canela(hg, Classe.MAGO, side)
            cube(0f, -2.7f, 0f, 1.95f, 2.8f, 2f, LEATHER)
            cube(0f, -0.5f, 0f, 2.25f, 0.5f, 2.3f, LEATHER_L)
            cube(0f, -0.5f, 2.35f, 0.9f, 0.4f, 0.12f, GOLD)
            cube(0f, -4.6f, 2.05f, 1.2f, 0.3f, 0.12f, LEATHER_D)
            Roupas.ocultar = false
            translate(0f, -5.4f, 0f)
            rotX(foot)
            cube(0f, 0.2f, 1.2f, 2.3f, 1.4f, 3.2f, LEATHER)
            cube(0f, -1.1f, 1.3f, 2.45f, 0.3f, 3.4f, LEATHER_D)
            cube(0f, 0.9f, 4.4f, 1.6f, 0.8f, 0.9f, LEATHER_L)
            cube(0f, 1.9f, 5.3f, 0.9f, 0.6f, 0.8f, LEATHER)
            cube(0f, 0.9f, -2.2f, 1.8f, 0.9f, 0.5f, LEATHER_D)
        pop()
    }

    private fun sleeve(el: Float) {
        push()
        Roupas.braco(hg, Classe.MAGO)
        cube(0f, -2.7f, 0f, 1.9f, 2.9f, 1.9f, ROBE)
        cube(0f, -5.6f, 0f, 2.15f, 0.35f, 2.15f, GOLD)
        pop()
        translate(0f, -5.7f, 0f)
        rotX(el)
        push()
        Roupas.braco(hg, Classe.MAGO)
        cube(0f, -3.4f, 0f, 2.5f, 3.2f, 2.5f, ROBE_L)
        cube(0f, -3.4f, 2.55f, 1f, 2.4f, 0.15f, ROBE)
        cube(0f, -6.7f, 0f, 2.8f, 0.4f, 2.8f, GOLD)
        cube(0f, -6.3f, 0f, 2.3f, 0.3f, 2.3f, ROBE_DD)
        cube(0f, -7.6f, 0f, 1.35f, 1.2f, 1.35f, SKIN)
        pop()
    }

    private fun shoulderPad(side: Float) {
        if (Roupas.ombreira(hg, Classe.MAGO, side)) return
        if (!HeroLook.ombro) return
        cube(side * 0.6f, 1.1f, 0f, 3.0f, 1.0f, 3.1f, ROBE_L)
        cube(side * 0.6f, 2.05f, 0f, 3.1f, 0.2f, 3.2f, GOLD)
        cube(side * 2.4f, 0.2f, 0f, 0.5f, 1.5f, 3f, GOLD_D)
    }

    private fun staff(wrist: Float) {
        translate(0f, -7.6f, 0f)
        rotX(wrist)
        cube(0f, 6f, 0f, 0.65f, 16f, 0.65f, SHAFT)
        cube(0f, 6f, 0.62f, 0.3f, 15.5f, 0.1f, SHAFT_L)
        cube(0f, 0f, 0f, 0.95f, 1.6f, 0.95f, LEATHER)
        cube(0f, 0f, 0f, 1f, 0.2f, 1f, LEATHER_D)
        cube(0f, -10.2f, 0f, 0.85f, 0.5f, 0.85f, GOLD_D)
        cube(0f, -4.5f, 0f, 0.95f, 0.3f, 0.95f, GOLD)
        cube(0f, 3.6f, 0f, 0.95f, 0.3f, 0.95f, GOLD)
        cube(0f, 10f, 0f, 0.95f, 0.3f, 0.95f, GOLD_D)
        cube(0f, 17.5f, 0f, 0.9f, 1.2f, 0.9f, SHAFT_L)
        cube(0f, 20f, 0f, 1.4f, 0.5f, 1.4f, GOLD)
        cube(0f, 21f, 0f, 1.7f, 0.3f, 1.7f, GOLD_D)
        cube(1.6f, 22.8f, 0f, 0.3f, 1.8f, 0.3f, GOLD_L)
        cube(-1.6f, 22.8f, 0f, 0.3f, 1.8f, 0.3f, GOLD_L)
        cube(0f, 22.8f, 1.6f, 0.3f, 1.8f, 0.3f, GOLD_L)
        cube(0f, 22.8f, -1.6f, 0.3f, 1.8f, 0.3f, GOLD_L)
        cube(1.2f, 24.9f, 0f, 0.3f, 0.35f, 0.3f, GOLD_L)
        cube(-1.2f, 24.9f, 0f, 0.3f, 0.35f, 0.3f, GOLD_L)
        cube(0f, 24.9f, 1.2f, 0.3f, 0.35f, 0.3f, GOLD_L)
        cube(0f, 24.9f, -1.2f, 0.3f, 0.35f, 0.3f, GOLD_L)
        // orbe flutuante: duas gemas girando em eixos diferentes
        val orbY = 23.4f + orbBob
        val os = orbS
        push()
            translate(0f, orbY, 0f)
            push()
                rotY(clock * 1.7f)
                rotX(0.6f)
                cube(0f, 0f, 0f, 1.7f * os, 1.7f * os, 1.7f * os, orbC, true)
            pop()
            push()
                rotY(-clock * 1.1f + 0.8f)
                rotZ(0.7f)
                cube(0f, 0f, 0f, 1.25f * os, 1.25f * os, 1.25f * os, orbL, true)
            pop()
        pop()
        // satélites em órbita
        val rad = 3.2f * (0.75f + 0.25f * os)
        for (k in 0..2) {
            val ang = clock * (2.6f + castW * 3f) + k * 2.094f
            cube(cos(ang) * rad, orbY + sin(clock * 3f + k) * 0.8f, sin(ang) * rad, 0.38f, 0.38f, 0.38f, orbL, true)
        }
        projectTo(0f, orbY, 0f, 0)
        projectTo(0f, 0f, 0f, 1)
    }

    private fun rightArm(sh: Float, shZ: Float, el: Float, wrist: Float) {
        push()
        translate(-7.2f * Roupas.ombrosK(Classe.MAGO), 12.8f, 0f)
        shoulderPad(-1f)
        rotZ(shZ)
        rotX(sh)
        sleeve(el)
        staff(wrist)
        pop()
    }

    private fun leftArm(sh: Float, shZ: Float, el: Float) {
        push()
        translate(7.2f * Roupas.ombrosK(Classe.MAGO), 12.8f, 0f)
        shoulderPad(1f)
        // cristal flutuante sobre o ombro
        cube(1.9f, 4.6f + crystalBob, 0f, 0.7f, 1.2f, 0.7f, orbC, true)
        cube(1.9f, 4.6f + crystalBob, 0f, 0.35f, 1.5f, 0.35f, orbL, true)
        rotZ(shZ)
        rotX(sh)
        sleeve(el)
        if (handGlow > 0.04f) {
            val g = handGlow
            cube(0f, -9.2f, 0.4f, 0.9f + 0.7f * g, 0.9f + 0.7f * g, 0.9f + 0.7f * g, orbL, true)
        }
        pop()
    }

    private val hg = object : HeroGfx {
        override fun cube(cx: Float, cy: Float, cz: Float, hx: Float, hy: Float, hz: Float, color: Int, emissive: Boolean) {
            val o = Roupas.ocultar
            Roupas.ocultar = false
            this@Mage3D.cube(cx, cy, cz, hx, hy, hz, color, emissive)
            Roupas.ocultar = o
        }
        override fun push() = this@Mage3D.push()
        override fun pop() = this@Mage3D.pop()
        override fun translate(x: Float, y: Float, z: Float) = this@Mage3D.translate(x, y, z)
        override fun rotX(a: Float) = this@Mage3D.rotX(a)
        override fun rotY(a: Float) = this@Mage3D.rotY(a)
        override fun rotZ(a: Float) = this@Mage3D.rotZ(a)
        override fun scale(x: Float, y: Float, z: Float) = this@Mage3D.scale(x, y, z)
    }

    private fun head(turn: Float, tilt: Float, b0: Float, b1: Float, h0: Float, h1: Float) {
        push()
            translate(0f, 14.2f, 0f)
            rotY(turn)
            rotX(tilt)
            Roupas.cabecaEsc(hg, Classe.MAGO)
            cube(0f, 0.3f, 0f, 2.3f, 1f, 2.3f, SKIN_D)
            // rosto
            cube(0f, 5f, 0f, 4.4f, 4.5f, 4.2f, SKIN)
            cube(-2f, 5.2f, 4.25f, 1f, 0.9f * eyeOpen, 0.12f, EYE)
            cube(2f, 5.2f, 4.25f, 1f, 0.9f * eyeOpen, 0.12f, EYE)
            cube(-2f, 5.1f, 4.36f, 0.55f, 0.5f * eyeOpen, 0.08f, HeroLook.eye(eyeC), true)
            cube(2f, 5.1f, 4.36f, 0.55f, 0.5f * eyeOpen, 0.08f, HeroLook.eye(eyeC), true)
            cube(-2f, 7f, 4.3f, 1.7f, 0.5f, 0.2f, HAIR)
            cube(2f, 7f, 4.3f, 1.7f, 0.5f, 0.2f, HAIR)
            cube(0f, 3.8f, 4.45f, 0.8f, 1f, 0.5f, SKIN_D)
            // bigode
            if (HeroLook.barba == 0) {
                cube(-1.8f, 2.9f, 4.5f, 1.9f, 0.55f, 0.4f, HAIR)
                cube(1.8f, 2.9f, 4.5f, 1.9f, 0.55f, 0.4f, HAIR)
                cube(-3.6f, 2.2f, 4.3f, 0.5f, 0.8f, 0.3f, HAIR_D)
                cube(3.6f, 2.2f, 4.3f, 0.5f, 0.8f, 0.3f, HAIR_D)

            }
            // cabelo
            if (HeroLook.cabelo == 0) {
                cube(-4.5f, 4.8f, 0f, 0.5f, 3.2f, 3.6f, HAIR)
                cube(4.5f, 4.8f, 0f, 0.5f, 3.2f, 3.6f, HAIR)
                cube(0f, 4f, -4.3f, 4.2f, 4f, 0.5f, HAIR)
                cube(0f, 0.9f, -4.1f, 3f, 1.4f, 0.5f, HAIR_D)

            }
            // barba comprida em molas
            if (HeroLook.barba == 0) {
                cube(0f, 1.4f, 4.3f, 3f, 1.2f, 0.5f, HAIR)
                push()
                    translate(0f, 1.0f, 4.4f)
                    rotX(-0.05f + b0)
                    cube(0f, -2f, 0.3f, 2.6f, 2.2f, 0.6f, HAIR)
                    translate(0f, -4.2f, 0f)
                    rotX(b1)
                    cube(0f, -2.2f, 0.3f, 2f, 2.2f, 0.55f, HAIR_D)
                    translate(0f, -4.4f, 0f)
                    rotX(b1 * 1.2f)
                    cube(0f, -1.8f, 0.3f, 1.2f, 1.8f, 0.5f, HAIR)
                pop()

            }
            // chapéu pontudo: aba, faixa, copa e ponta em molas
            if (HeroLook.elmo) {
                cube(0f, 9.7f, -0.4f, 8.2f, 0.45f, 6.4f, ROBE_D)
                cube(0f, 9.7f, -0.4f, 8.35f, 0.2f, 6.55f, GOLD_D)
                cube(0f, 10.9f, 0f, 5.4f, 0.7f, 5.4f, ROBE_DD)
                cube(0f, 10.9f, 0f, 5.5f, 0.3f, 5.5f, GOLD)
                cube(0f, 10.9f, 5.5f, 1.2f, 1f, 0.2f, GOLD)
                cube(0f, 10.9f, 5.7f, 0.6f, 0.6f, 0.12f, orbC, true)
                cube(0f, 13f, -0.2f, 4.7f, 1.7f, 4.7f, ROBE)
                cube(0f, 14.9f, -0.5f, 3.9f, 1.7f, 3.9f, ROBE_L)
                cube(0f, 16.7f, -0.9f, 3f, 1.6f, 3f, ROBE)
                cube(-1.5f, 13.3f, 4.55f, 0.35f, 0.35f, 0.1f, ARC, true)
                cube(1.7f, 12.5f, 4.55f, 0.3f, 0.3f, 0.1f, ARC_L, true)
                cube(0.6f, 15.1f, 3.5f, 0.3f, 0.3f, 0.1f, ARC_L, true)
                push()
                    translate(0f, 18f, -1.4f)
                    rotX(-(0.5f + h0))
                    cube(0f, 1.5f, 0f, 2.2f, 1.6f, 2.2f, ROBE_D)
                    translate(0f, 3f, 0f)
                    rotX(-(0.6f + h1))
                    cube(0f, 1.3f, 0f, 1.5f, 1.4f, 1.5f, ROBE)
                    translate(0f, 2.6f, 0f)
                    rotX(-(0.7f + h1 * 1.3f))
                    cube(0f, 1f, 0f, 0.9f, 1.1f, 0.9f, ROBE_D)
                    cube(0f, 2.5f, 0f, 0.55f, 0.55f, 0.55f, orbL, true)
                pop()

            }
        HeroKit.cabeca(hg, Classe.MAGO)
        pop()
    }

    /** Asas de luz (só aparecem voando): quatro penas emissivas por lado que batem. */
    private fun wings(w: Float, flap: Float) {
        for (k in 0..1) {
            val sd = if (k == 0) 1f else -1f
            push()
                translate(sd * 3f, 11.5f, -4.8f)
                rotY(-sd * 0.45f)
                cube(0f, 0f, 0f, 0.8f * w, 0.8f * w, 0.8f * w, GOLD)
                for (i in 0..3) {
                    val len = (12f - i * 1.8f) * w
                    val col = if (i == 0) ARC_L else if (i == 1) ARC else if (i == 2) ARC_D else VIO
                    push()
                        rotZ(-sd * (0.35f + flap + i * 0.33f))
                        cube(0f, len * 0.5f + 0.8f, 0f, 1.1f * w, len * 0.5f, 0.22f, col, true)
                        cube(0f, len + 1.2f, 0f, 0.7f * w, 0.5f * w, 0.22f, ARC_L, true)
                    pop()
                }
            pop()
        }
    }

    private fun torso(chestTw: Float, c0: Float, c1: Float, c2: Float, skF: Float, skB: Float, flare: Float, breath: Float, bookSw: Float, wingW: Float, flap: Float) {
        push()
        Roupas.cintura(hg, Classe.MAGO)
        HeroKit.cauda(hg, Classe.MAGO)
        Roupas.abdomen(hg, Classe.MAGO)
        Roupas.ocultar = HeroLook.peito > 0
        // cinto, fivela, grimório e poção
        cube(0f, 0.6f, 0f, 5.2f, 1.2f, 3.4f, LEATHER)
        cube(0f, 0.7f, 3.45f, 1.6f, 1.1f, 0.5f, GOLD)
        cube(0f, 0.7f, 3.95f, 0.9f, 0.7f, 0.2f, GOLD_L)
        cube(0f, 0.7f, 4.15f, 0.4f, 0.4f, 0.12f, orbC, true)
        Roupas.ocultar = false
        push()
            translate(5.7f, 0.2f, 0.4f)
            rotZ(bookSw)
            cube(0.3f, -2.2f, 0f, 0.9f, 2.2f, 1.9f, TOME)
            cube(0.3f, -2.2f, 1.95f, 0.5f, 1.6f, 0.1f, GOLD)
            cube(1.25f, -2.2f, 0f, 0.1f, 2f, 1.7f, GOLD_L)
            cube(0.3f, -2.2f, 2.05f, 0.25f, 0.25f, 0.1f, orbC, true)
            cube(0.3f, -4.3f, 0f, 0.8f, 0.2f, 1.8f, TOME_D)
        pop()
        cube(-5.3f, -0.1f, 2.2f, 0.75f, 1.3f, 0.75f, GLASS)
        cube(-5.3f, -0.5f, 2.2f, 0.6f, 0.8f, 0.6f, VIO, true)
        cube(-5.3f, 1.5f, 2.2f, 0.4f, 0.4f, 0.4f, LEATHER_L)
        cube(-5.3f, 0.9f, 2.2f, 0.85f, 0.15f, 0.85f, GOLD_D)
        Roupas.ocultar = HeroLook.peito > 0
        // saia da túnica: painéis da frente, de trás e dos lados
        cube(0f, -4.2f, 0f, 6.2f, 4.2f, 3.5f, ROBE_DD)
        push()
            translate(0f, 0.8f, 3.9f)
            rotX(skF)
            cube(0f, -4.5f, 0f, 6.6f, 4.6f, 0.45f, ROBE)
            cube(0f, -4.4f, 0.5f, 0.9f, 4.3f, 0.12f, GOLD)
            cube(0f, -8.9f, 0.3f, 6.7f, 0.35f, 0.35f, GOLD)
            cube(-3.2f, -6.9f, 0.5f, 0.35f, 0.35f, 0.1f, ARC, true)
            cube(3.2f, -6.9f, 0.5f, 0.35f, 0.35f, 0.1f, ARC, true)
            cube(-3.2f, -3.4f, 0.5f, 0.25f, 0.25f, 0.1f, ARC_L, true)
            cube(3.2f, -3.4f, 0.5f, 0.25f, 0.25f, 0.1f, ARC_L, true)
        pop()
        push()
            translate(0f, 0.8f, -3.9f)
            rotX(skB)
            cube(0f, -4.5f, 0f, 6.6f, 4.6f, 0.45f, ROBE_D)
            cube(0f, -8.9f, -0.3f, 6.7f, 0.35f, 0.35f, GOLD)
            cube(0f, -4.5f, -0.5f, 0.35f, 4.2f, 0.12f, GOLD_D)
        pop()
        for (k in 0..1) {
            val sd = if (k == 0) 1f else -1f
            push()
                translate(sd * 6.4f, 0.8f, 0f)
                rotZ(sd * flare)
                cube(0f, -4.5f, 0f, 0.45f, 4.6f, 3.7f, ROBE_L)
                cube(0f, -8.9f, 0f, 0.5f, 0.35f, 3.8f, GOLD)
            pop()
        }
        // peito
        cube(0f, 3.8f, 0f, 5f, 2.2f, 3.3f, ROBE)
        cube(0f, 3.8f, 3.35f, 4.8f, 0.3f, 0.2f, GOLD_D)
        pop()
        push()
            rotY(chestTw)
            translate(0f, breath, 0f)
            Roupas.peitoEsc(hg, Classe.MAGO)
            Roupas.peito(hg, Classe.MAGO)
            Roupas.capa(hg, Classe.MAGO)
            Roupas.ocultar = HeroLook.peito > 0
            cube(0f, 9.8f, 0f, 5.7f, 4.8f, 3.5f, ROBE)
            cube(0f, 10f, 3.55f, 4.7f, 4.2f, 0.15f, ROBE_L)
            cube(0f, 14.3f, 0f, 3.4f, 0.9f, 3f, ROBE_D)
            // faixa em diagonal
            push()
                translate(0f, 9.6f, 3.75f)
                rotZ(0.75f)
                cube(0f, 0f, 0f, 0.9f, 6.2f, 0.18f, LEATHER)
                cube(0f, 0f, 0.2f, 0.25f, 6.2f, 0.06f, GOLD_D)
            pop()
            // emblema de estrela
            cube(0f, 10.4f, 3.75f, 1.4f, 1.4f, 0.15f, GOLD)
            cube(0f, 10.4f, 3.9f, 0.7f, 0.7f, 0.12f, orbC, true)
            cube(0f, 11.7f, 3.8f, 0.3f, 0.5f, 0.1f, orbL, true)
            cube(-5.6f, 8.4f, 3.7f, 0.3f, 0.3f, 0.2f, GOLD_L)
            cube(5.6f, 8.4f, 3.7f, 0.3f, 0.3f, 0.2f, GOLD_L)
            // manto dos ombros
            cube(0f, 13.9f, 0f, 6.8f, 0.8f, 4.1f, ROBE_DD)
            cube(0f, 13.3f, 0f, 7f, 0.25f, 4.3f, GOLD)
            cube(0f, 13.4f, 4.2f, 1.2f, 1f, 0.3f, GOLD)
            cube(0f, 13.4f, 4.5f, 0.5f, 0.5f, 0.15f, orbC, true)
            cube(-3.6f, 14.2f, 3.5f, 0.3f, 0.3f, 0.12f, ARC_L, true)
            cube(3.6f, 14.2f, 3.5f, 0.3f, 0.3f, 0.12f, ARC_L, true)
            Roupas.ocultar = false
            if (wingW > 0.05f) wings(wingW, flap)
            Roupas.ocultar = HeroLook.peito > 0
            // capuz dobrado nas costas
            cube(0f, 13f, -3.2f, 3.8f, 1.7f, 1.4f, ROBE_D)
            cube(0f, 13.6f, -4.2f, 3f, 1.1f, 0.5f, ROBE_DD)
            // capa longa em três segmentos
            Roupas.ocultar = HeroLook.capa > 0
            push()
                translate(0f, 13.3f, -3.9f)
                rotX(c0)
                cube(0f, -2.9f, -0.4f, 5.9f, 3.1f, 0.35f, ROBE_DD)
                cube(0f, 0.9f, 0.1f, 6.5f, 0.8f, 1f, GOLD_D)
                cube(-4.6f, 0.7f, 0.9f, 0.5f, 0.5f, 0.4f, GOLD_L)
                cube(4.6f, 0.7f, 0.9f, 0.5f, 0.5f, 0.4f, GOLD_L)
                cube(0f, -2.2f, -0.85f, 1.4f, 1.4f, 0.1f, GOLD)
                cube(0f, -2.2f, -0.95f, 0.8f, 0.8f, 0.1f, orbC, true)
                translate(0f, -5.8f, 0f)
                rotX(c1)
                cube(0f, -2.9f, -0.4f, 6.2f, 3f, 0.35f, ROBE_D)
                cube(-3.2f, -1.5f, -0.85f, 0.4f, 0.4f, 0.1f, ARC, true)
                cube(2.6f, -3.8f, -0.85f, 0.35f, 0.35f, 0.1f, ARC_L, true)
                cube(-6.1f, -2.9f, -0.4f, 0.25f, 3f, 0.5f, GOLD_D)
                cube(6.1f, -2.9f, -0.4f, 0.25f, 3f, 0.5f, GOLD_D)
                translate(0f, -5.8f, 0f)
                rotX(c2)
                cube(0f, -2.6f, -0.4f, 5.9f, 2.6f, 0.35f, VIO_D)
                cube(0f, -5.3f, -0.4f, 5.9f, 0.4f, 0.5f, GOLD)
                cube(-5.9f, -2.6f, -0.4f, 0.25f, 2.6f, 0.5f, GOLD_D)
                cube(5.9f, -2.6f, -0.4f, 0.25f, 2.6f, 0.5f, GOLD_D)
                cube(1.2f, -2f, -0.85f, 0.4f, 0.4f, 0.1f, ARC_L, true)
                cube(-1.8f, -3.6f, -0.85f, 0.35f, 0.35f, 0.1f, ARC, true)
                cube(-4.2f, -6.7f, -0.4f, 1.3f, 1.2f, 0.35f, ROBE_D)
                cube(-1.4f, -6.7f, -0.4f, 1.3f, 1.2f, 0.35f, VIO_D)
                cube(1.4f, -6.7f, -0.4f, 1.3f, 1.2f, 0.35f, ROBE_D)
                cube(4.2f, -6.7f, -0.4f, 1.3f, 1.2f, 0.35f, VIO_D)
            pop()
        pop()
    }

    private fun rune(c: Canvas, p: Paint, cx: Float, cy: Float, ca: Float, r0: Float) {
        val rr = r0 * rs
        val ry = rr * SINP
        p.style = Paint.Style.STROKE
        p.strokeWidth = max(1.6f, rs * 1.1f)
        p.color = (((ca * 0.75f * 255f).toInt()) shl 24) or 0x005FD4FF
        c.drawOval(cx - rr, cy - ry, cx + rr, cy + ry, p)
        p.strokeWidth = max(1.2f, rs * 0.7f)
        p.color = (((ca * 0.6f * 255f).toInt()) shl 24) or 0x009B6BFF
        c.drawOval(cx - rr * 0.72f, cy - ry * 0.72f, cx + rr * 0.72f, cy + ry * 0.72f, p)
        p.strokeWidth = max(1.8f, rs * 1.3f)
        p.strokeCap = Paint.Cap.ROUND
        p.color = (((ca * 0.9f * 255f).toInt()) shl 24) or 0x00C4F4FF
        for (k in 0..5) {
            val ang = clock * 1.8f + k * 1.0472f
            val cx0 = cos(ang)
            val sy0 = sin(ang)
            c.drawLine(cx + cx0 * rr * 0.80f, cy + sy0 * ry * 0.80f, cx + cx0 * rr * 0.96f, cy + sy0 * ry * 0.96f, p)
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

        // fase do feitiço: 0..1 (negativo = sem ataque)
        val t = if (attack > 0.001f) 1f - attack else -1f
        val attacking = t >= 0f
        if (attack > prevAtk + 0.3f) {
            // feitiço novo: começa a partir da pose atual
            System.arraycopy(lastPose, 0, kS, 0, NK)
            released = false
            sv[2] += 2f; sv[3] += 3f; sv[4] += 4f
            sv[5] += 1.5f; sv[9] += 3f
        }
        prevAtk = attack
        if (!attacking) released = false

        val rolling = rollP >= 0f
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
            // aterrissagem: manto sobe, corpo amortece
            sv[2] -= 3f; sv[3] -= 4f; sv[4] -= 5f; sv[0] += 2.2f; sv[7] -= 3f; sv[9] -= 4f
            wasAir = false
            landPuff = true
            landDip = 1f
        } else if (airW > 0.7f) {
            if (!wasAir) takeoffPuff = true
            wasAir = true
        }
        if (rolling && !wasRolling) takeoffPuff = true
        if (!rolling && wasRolling) landDip = max(landDip, 0.65f)
        wasRolling = rolling
        // reação ao tomar dano: corpo recua, chapéu e capa chicoteiam
        val fl = V.flash
        if (!first && fl > prevFlash + 0.25f) {
            hitKick = 1f
            sv[0] -= 6f; sv[2] += 4f; sv[3] += 5f; sv[4] += 6f; sv[7] += 3f; sv[8] += 6f; sv[9] += 6f
        }
        prevFlash = fl
        hitKick = max(0f, hitKick - dt * 4.5f)
        landDip = max(0f, landDip - dt * 5.5f)
        flashV = max(0f, flashV - dt * 3.5f)
        // piscar de olhos
        val bp = clock % 3.9f
        eyeOpen = if (bp < 0.14f) max(0.12f, abs(bp - 0.07f) / 0.07f) else 1f
        val vzN = (airVz / 340f).coerceIn(-1.2f, 1f)
        val tuck = max(rollW, airW * (0.30f + 0.35f * max(0f, vzN) - 0.2f * max(0f, -vzN)))
        val still = 1f - max(rollW, airW)
        val mvs = still * (if (attacking) blend * 0.35f else blend)
        val lmv = still * blend
        castW += ((if (attacking) 1f else 0f) - castW) * min(1f, dt * (if (attacking) 14f else 4f))
        val flyOn = flying && !rolling
        flyW += ((if (flyOn) 1f else 0f) - flyW) * min(1f, dt * (if (flyOn) 6f else 5f))
        val flap = sin(clock * (9f + 5f * blend)) * (0.18f + 0.12f * blend)

        // ── poses (cajado na mão direita, mão esquerda livre) ──
        kI[0] = -0.55f + sin(phase) * 0.15f * mvs + sin(clock * 1.3f) * 0.04f * (1f - mvs)
        kI[1] = -0.10f
        kI[2] = -0.9f
        kI[3] = 1.6f
        kI[4] = 0f; kI[5] = 0f; kI[6] = 0f; kI[7] = 0f
        kI[8] = -0.2f - sin(phase) * 0.45f * mvs + sin(clock * 1.1f + 1f) * 0.03f
        kI[9] = 0.12f
        kI[10] = -0.5f - 0.3f * mvs
        var lunge = 0f
        if (attacking) {
            if (t < T_W) {
                mixK(kS, K_W, ease(t / T_W))
            } else if (t < T_M) {
                mixK(K_W, K_M, ease((t - T_W) / (T_M - T_W)))
            } else if (t < T_E) {
                mixK(K_M, K_E, ease((t - T_M) / (T_E - T_M)))
            } else {
                mixK(K_E, kI, ease((t - T_E) / (1f - T_E)))
            }
            val la = T_W - 0.04f
            if (t > la && t < T_E) {
                lunge = -sin(((t - la) / (T_E - la)) * PI.toFloat()) * 1.3f
            }
        } else {
            for (i in 0 until NK) kO[i] = kI[i]
        }
        var sh = kO[0]
        var shZ = kO[1]
        var el = kO[2]
        var wrist = kO[3]
        val tw = kO[4]
        var crouch = kO[5]
        val leanAtk = kO[6]
        val hipTw = kO[7]
        var lsh = kO[8]
        var lshZ = kO[9]
        var lel = kO[10]
        for (i in 0 until NK) lastPose[i] = kO[i]
        if (rollW > 0.001f) {
            sh = lerp(sh, -1.2f, rollW)
            shZ = lerp(shZ, 0.2f, rollW)
            el = lerp(el, -1.9f, rollW)
            wrist = lerp(wrist, 3.3f, rollW)
            lsh = lerp(lsh, -1.0f, rollW)
            lshZ = lerp(lshZ, 0.2f, rollW)
            lel = lerp(lel, -1.5f, rollW)
        }
        if (airW > 0.001f && !attacking) {
            sh = lerp(sh, -2.0f - 0.25f * vzN, airW)
            shZ = lerp(shZ, 0.5f, airW)
            el = lerp(el, -0.6f, airW)
            wrist = lerp(wrist, 2.9f, airW)
            lsh = lerp(lsh, -1.9f - 0.2f * vzN, airW)
            lshZ = lerp(lshZ, 0.5f, airW)
            lel = lerp(lel, -0.4f, airW)
        }

        if (flyW > 0.001f && !attacking) {
            // voo: braço do cajado aponta para a frente, a outra mão varre para trás
            sh = lerp(sh, -1.7f, flyW)
            shZ = lerp(shZ, 0.15f, flyW)
            el = lerp(el, -0.25f, flyW)
            wrist = lerp(wrist, 2.2f, flyW)
            lsh = lerp(lsh, 0.5f, flyW)
            lshZ = lerp(lshZ, 0.6f, flyW)
            lel = lerp(lel, -0.2f, flyW)
        }
        // ── molas (manto, barba, saia, chapéu, cabeça, inclinação) ──
        val bankT = (-yawVel * 0.03f).coerceIn(-0.22f, 0.22f) * spN
        spring(0, 0.12f * spN, 90f, 12f, sdt)
        spring(1, bankT, 80f, 9f, sdt)
        val capeBase = 0.12f + 0.30f * spN + flyW * 0.7f + airW * (0.35f - 0.3f * vzN) +
            0.09f * sin(clock * 3f + phase * 2f) * (0.4f + spN)
        spring(2, capeBase, 36f, 5.5f, sdt)
        spring(3, capeBase * 0.8f + 0.08f, 27f, 4.5f, sdt)
        spring(4, capeBase * 0.7f + 0.10f, 19f, 3.8f, sdt)
        spring(5, 0.12f * spN + 0.2f * airW, 55f, 6f, sdt)
        spring(6, 0.2f * spN + 0.25f * airW, 40f, 4.5f, sdt)
        spring(7, 0.03f - 0.2f * mvs * abs(sin(phase)) - 0.1f * airW, 60f, 7f, sdt)
        spring(8, (yawVel * 0.03f).coerceIn(-0.35f, 0.35f), 70f, 9f, sdt)
        spring(9, 0.12f + 0.35f * spN + 0.25f * airW + 0.2f * castW + 0.4f * flyW, 30f, 4f, sdt)
        spring(10, 0.06f + 0.5f * spN + 0.5f * airW + 0.6f * flyW, 45f, 5.5f, sdt)

        val bob = (0.5f + 0.5f * cos(phase * 2f)) * 1.2f * mvs + sin(clock * 2.2f) * 0.3f * (1f - mvs)
        val breath = sin(clock * 2.2f) * 0.26f * (1f - mvs * 0.6f)
        val sway = sin(phase) * 0.8f * mvs + sin(clock * 0.9f) * 0.3f * (1f - mvs)
        crouch = max(crouch, max(rollW * 0.9f, landDip * 0.9f))
        val lean = sp[0] + leanAtk * 0.5f - hitKick * 0.3f + rollW * 0.25f + landDip * 0.2f + flyW * (0.18f + 0.45f * blend)
        val hipY = 12.6f + bob - crouch * 3f - hitKick * 0.9f
        val wob = sin(phase) * 0.12f * mvs
        val chestTw = tw - hipTw * 0.5f - wob - sin(phase) * 0.18f * mvs

        // ── orbe, cristal, olhos e mão brilhando ──
        var os = 1f + 0.06f * sin(clock * 3.2f)
        if (attacking) {
            os = if (t < T_M) {
                0.85f + 0.95f * ease(t / T_M)
            } else if (t < T_M + 0.10f) {
                lerp(1.8f, 0.45f, ease((t - T_M) / 0.10f))
            } else {
                lerp(0.45f, 1f, ease((t - T_M - 0.10f) / (1f - T_M - 0.10f)))
            }
        }
        orbS = os
        orbBob = sin(clock * 2.6f) * 0.7f + sin(phase * 2f) * 0.5f * mvs
        crystalBob = sin(clock * 2.1f + 1.3f) * 0.8f
        handGlow = castW * (0.6f + 0.4f * sin(clock * 20f))
        val oq = (0.5f + 0.5f * sin(clock * 3.6f) + castW * 0.6f + flyW * 0.5f).coerceIn(0f, 1f)
        orbC = rgb((70 + 60 * oq).toInt(), (190 + 50 * oq).toInt(), 255)
        orbL = rgb((225 + 30 * oq).toInt(), 255, 255)
        val eq = (0.4f + 0.3f * sin(clock * 2f) + castW * 0.7f).coerceIn(0f, 1f)
        eyeC = rgb((60 + 130 * eq).toInt(), (170 + 85 * eq).toInt(), 255)

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
            val r = ((rollP - 0.10f) / 0.80f).coerceIn(0f, 1f)
            val ang = (r * 0.6f + r * r * (3f - 2f * r) * 0.4f) * TWO_PI
            val lift = 4.5f * sin(r * PI.toFloat())
            translate(0f, 10f + lift, 0f)
            rotX(ang)
            translate(0f, -11f, 0f)
        }

        // pernas: passada curta (a túnica limita o passo) + postura firme ao conjurar
        val lk = if (attacking) (crouch / 0.5f).coerceIn(0f, 1f) else 0f
        for (k in 0..1) {
            val sd = if (k == 0) 1f else -1f
            val pp = if (sd > 0f) phase else phase + PI.toFloat()
            var a = sin(pp) * 0.62f * lmv
            var kn = max(0f, -cos(pp)) * 0.95f * lmv + 0.08f + crouch * 0.9f
            var ft = max(0f, cos(pp)) * 0.35f * lmv + airW * (0.55f * max(0f, vzN) - 0.25f * max(0f, -vzN))
            if (sd > 0f) {
                a -= 0.35f * lk
            } else {
                a += 0.3f * lk
                kn += 0.3f * lk
            }
            a = a * (1f - airW) - tuck + sd * 0.3f * airW
            kn = (kn + tuck * 1.5f + (if (sd > 0f) 0.5f else -0.1f) * airW).coerceAtLeast(0f)
            if (flyW > 0.001f) {
                a = lerp(a, 0.5f + sd * 0.12f + 0.06f * sin(clock * 3f + sd), flyW)
                kn = lerp(kn, 0.25f, flyW)
                ft = lerp(ft, 0.7f, flyW)
            }
            leg(sd, a, kn, hipY, ft)
        }

        // quadril → peito (contra-rotação) → cabeça e braços
        push()
        translate(0f, hipY, 0f)
        rotY(hipTw * 0.5f + wob)
        rotX(lean)
        push()
        val flare = 0.04f + 0.10f * spN + 0.3f * airW
        val bookSw = (0.05f + 0.12f * spN) * sin(clock * 2.2f) - 0.2f * sp[7]
        torso(chestTw, sp[2], sp[3], sp[4], sp[7].coerceIn(-0.2f, 0.2f), sp[10], flare, breath, bookSw, flyW, flap)
        Roupas.ocultar = false
        push()
        rotY(chestTw)
        translate(0f, breath, 0f)
        head(-chestTw * 0.7f + sp[8], -lean * 0.6f, min(sp[5], 0.25f), min(sp[6], 0.3f), sp[9], sp[9] * 0.8f)
        rightArm(sh, shZ, el, wrist)
        leftArm(lsh, lshZ, lel)
        pop()
        pop()
        pop()

        // ── anéis rúnicos no chão: feitiço (sob os pés) e voo (no chão, sob o herói no ar) ──
        if (castW > 0.03f) rune(c, p, fx, fy, castW, 13f + 5f * castW)
        if (flyW > 0.03f) rune(c, p, fx, fy + airH, flyW * 0.9f, 17f)

        // ── teleporte: sumiço na origem e materialização no destino ──
        val tpOn = tpP >= 0f && tpP < 1f
        if (tpOn && prevTp < 0f) {
            val rr0 = kotlin.random.Random
            for (i in 0..15) {
                val ang = rr0.nextFloat() * TWO_PI
                val sp1 = (10f + rr0.nextFloat() * 30f) * rs
                emitSpark(
                    fx + tpOffX, fy + tpOffY - 6f * rs, cos(ang) * sp1, sin(ang) * sp1 * 0.6f - rs * 30f,
                    0.4f + rr0.nextFloat() * 0.25f, rs * 40f,
                    if (i % 2 == 0) 0xFFC4F4FF.toInt() else 0xFF5FD4FF.toInt(), 1.1f
                )
            }
            for (i in 0..11) {
                val ang = i * 0.5236f
                emitSpark(
                    fx + cos(ang) * 22f * rs, fy - 6f * rs + sin(ang) * 15f * rs,
                    -cos(ang) * 60f * rs, -sin(ang) * 40f * rs, 0.3f, 0f,
                    if (i % 2 == 0) 0xFFFFFFFF.toInt() else 0xFFB596FF.toInt(), 1f
                )
            }
            trN = 0
        }
        prevTp = if (tpOn) tpP else -1f
        if (tpOn) {
            val u = tpP
            val inv = 1f - u
            val ox0 = fx + tpOffX
            val oy0 = fy + tpOffY
            p.style = Paint.Style.STROKE
            p.strokeCap = Paint.Cap.ROUND
            // rastro entre a origem e o destino
            p.strokeWidth = max(2f, rs * 2.6f * inv)
            p.color = (((inv * inv * 0.35f * 255f).toInt()) shl 24) or 0x005FD4FF
            c.drawLine(ox0, oy0 - 8f * rs, fx, fy - 8f * rs, p)
            p.strokeWidth = max(1f, rs * 0.9f)
            p.color = (((inv * inv * 0.8f * 255f).toInt()) shl 24) or 0x00FFFFFF
            c.drawLine(ox0, oy0 - 8f * rs, fx, fy - 8f * rs, p)
            // origem: anel que se abre e pilar de luz que fecha
            val ro = (6f + 24f * u) * rs
            p.strokeWidth = max(1.6f, rs * 1.6f)
            p.color = (((inv * 0.85f * 255f).toInt()) shl 24) or 0x005FD4FF
            c.drawOval(ox0 - ro, oy0 - ro * SINP, ox0 + ro, oy0 + ro * SINP, p)
            p.color = (((inv * 0.7f * 255f).toInt()) shl 24) or 0x009B6BFF
            c.drawOval(ox0 - ro * 0.65f, oy0 - ro * 0.65f * SINP, ox0 + ro * 0.65f, oy0 + ro * 0.65f * SINP, p)
            p.style = Paint.Style.FILL
            val wcol = (10f * inv * inv + 1f) * rs
            p.color = (((inv * 0.25f * 255f).toInt()) shl 24) or 0x005FD4FF
            c.drawRect(ox0 - wcol * 1.8f, oy0 - 46f * rs * (0.5f + 0.5f * inv), ox0 + wcol * 1.8f, oy0, p)
            p.color = (((inv * 0.6f * 255f).toInt()) shl 24) or 0x00DFF8FF
            c.drawRect(ox0 - wcol * 0.7f, oy0 - 46f * rs * (0.5f + 0.5f * inv), ox0 + wcol * 0.7f, oy0, p)
            // destino: anel que se fecha sobre o herói
            val rd0 = (26f - 20f * u) * rs
            p.style = Paint.Style.STROKE
            p.strokeWidth = max(1.6f, rs * 1.8f)
            p.color = (((min(1f, inv * 1.6f) * 0.85f * 255f).toInt()) shl 24) or 0x00C4F4FF
            c.drawOval(fx - rd0, fy - rd0 * SINP, fx + rd0, fy + rd0 * SINP, p)
            p.strokeCap = Paint.Cap.BUTT
            p.style = Paint.Style.FILL
        }

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

        flushZ(c)

        // ── voo: jatos de energia saindo dos pés ──
        if (flyW > 0.5f) {
            jetT -= dt
            if (jetT <= 0f) {
                jetT = 0.035f
                for (k in 0..1) {
                    emitSpark(
                        fx + (rnd.nextFloat() - 0.5f) * 6f * rs, fy + rnd.nextFloat() * 2f * rs,
                        (rnd.nextFloat() - 0.5f) * rs * 14f, rs * (30f + rnd.nextFloat() * 40f),
                        0.28f + rnd.nextFloat() * 0.2f, 0f,
                        if (rnd.nextFloat() > 0.5f) 0xFF8FE3FF.toInt() else 0xFFB596FF.toInt(), 0.9f
                    )
                }
            }
        }
        // clarão da materialização
        if (tpOn && tpP < 0.6f) {
            val k = 1f - tpP / 0.6f
            p.style = Paint.Style.FILL
            p.color = (((k * 0.55f * 255f).toInt()) shl 24) or 0x00FFFFFF
            c.drawCircle(fx, fy - 14f * rs, rs * (6f + 14f * (1f - k)), p)
            p.color = (((k * 0.3f * 255f).toInt()) shl 24) or 0x005FD4FF
            c.drawCircle(fx, fy - 14f * rs, rs * (12f + 18f * (1f - k)), p)
        }

        // ── partículas: poeira arcana subindo do orbe, energia sendo sugada na preparação, explosão no disparo ──
        val orbOk = !rolling
        if (orbOk) {
            if (attacking && t < T_M) {
                gatherT -= dt
                if (gatherT <= 0f) {
                    gatherT = 0.028f
                    val ang = rnd.nextFloat() * TWO_PI
                    val dist = (9f + rnd.nextFloat() * 9f) * rs
                    val life = 0.17f
                    emitSpark(
                        tipX + cos(ang) * dist, tipY + sin(ang) * dist * 0.8f,
                        -cos(ang) * dist / life, -sin(ang) * dist * 0.8f / life,
                        life, 0f, if (rnd.nextFloat() > 0.5f) 0xFFC4F4FF.toInt() else 0xFF5FD4FF.toInt(), 0.9f
                    )
                }
            } else if (!attacking) {
                moteT -= dt
                if (moteT <= 0f) {
                    moteT = if (mvs > 0.5f) 0.10f else 0.32f
                    emitSpark(
                        tipX + (rnd.nextFloat() - 0.5f) * 3f * rs, tipY + (rnd.nextFloat() - 0.5f) * 3f * rs,
                        (rnd.nextFloat() - 0.5f) * rs * 8f, -rs * (3f + rnd.nextFloat() * 5f),
                        0.55f + rnd.nextFloat() * 0.4f, -rs * 6f,
                        if (rnd.nextFloat() > 0.6f) 0xFFFFFFFF.toInt() else 0xFF8FE3FF.toInt(), 0.7f
                    )
                }
            }
            if (attacking && !released && t >= T_M) {
                released = true
                flashV = 1f
                for (i in 0..13) {
                    val ang = rnd.nextFloat() * TWO_PI
                    val sp0 = (14f + rnd.nextFloat() * 34f) * rs
                    emitSpark(
                        tipX, tipY, cos(ang) * sp0, sin(ang) * sp0 - rs * 6f,
                        0.28f + rnd.nextFloat() * 0.2f, rs * 70f,
                        if (i % 3 == 0) 0xFFFFFFFF.toInt() else if (i % 3 == 1) 0xFF8FE3FF.toInt() else 0xFFB596FF.toInt(), 1.1f
                    )
                }
                sv[0] -= 2f; sv[2] += 3f; sv[3] += 4f; sv[4] += 5f; sv[9] += 4f
            }
        }

        // ── rastro arcano do cajado ──
        if (!attacking) {
            trN = 0
        } else {
            if (t >= T_W - 0.06f && t <= T_E) {
                if (trN == TRAIL_MAX) {
                    System.arraycopy(trX, 1, trX, 0, TRAIL_MAX - 1)
                    System.arraycopy(trY, 1, trY, 0, TRAIL_MAX - 1)
                    trN = TRAIL_MAX - 1
                }
                trX[trN] = tipX
                trY[trN] = tipY
                trN++
            }
            val fade = min(1f, attack * 4f)
            p.strokeCap = Paint.Cap.ROUND
            for (i in 1 until trN) {
                val a = i.toFloat() / trN.toFloat()
                p.style = Paint.Style.STROKE
                p.strokeWidth = max(2.4f, rs * 3.4f * a)
                p.color = (((a * a * 0.22f * fade * 255f).toInt()) shl 24) or 0x005FD4FF
                c.drawLine(trX[i - 1], trY[i - 1], trX[i], trY[i], p)
                p.strokeWidth = max(1f, rs * 1.1f)
                p.color = (((a * a * 0.7f * fade * 255f).toInt()) shl 24) or 0x00DFF8FF
                c.drawLine(trX[i - 1], trY[i - 1], trX[i], trY[i], p)
            }
            p.strokeCap = Paint.Cap.BUTT
        }

        // ── brilho do orbe e anel de choque do disparo ──
        if (orbOk) {
            val r0 = (3.1f * os + 2.6f * flashV) * rs
            p.style = Paint.Style.FILL
            p.color = (((0.10f + 0.10f * castW + 0.2f * flashV).coerceAtMost(0.5f) * 255f).toInt() shl 24) or 0x005FD4FF
            c.drawCircle(tipX, tipY, r0 * 2.6f, p)
            p.color = (((0.18f + 0.12f * castW + 0.25f * flashV).coerceAtMost(0.6f) * 255f).toInt() shl 24) or 0x008FE3FF
            c.drawCircle(tipX, tipY, r0 * 1.6f, p)
            p.color = (((0.30f + 0.3f * flashV).coerceAtMost(0.8f) * 255f).toInt() shl 24) or 0x00FFFFFF
            c.drawCircle(tipX, tipY, r0 * 0.7f, p)
            if (flashV > 0.02f) {
                val u = 1f - flashV
                val rr = (4f + 24f * (1f - (1f - u) * (1f - u))) * rs
                p.style = Paint.Style.STROKE
                p.strokeWidth = max(2f, rs * 2.2f * flashV)
                p.color = (((flashV * 0.9f * 255f).toInt()) shl 24) or 0x00C4F4FF
                c.drawCircle(tipX, tipY, rr, p)
                p.strokeWidth = max(1.4f, rs * 1.1f)
                p.color = (((flashV * 0.7f * 255f).toInt()) shl 24) or 0x009B6BFF
                c.drawCircle(tipX, tipY, rr * 0.7f, p)
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
