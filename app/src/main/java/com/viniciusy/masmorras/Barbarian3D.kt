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
 * Bárbaro 3D de cubos (mesmo motor do Guerreiro e do Mago): esqueleto articulado com pernas, quadril/peito em
 * contra-rotação, cabeça com elmo de chifres, capa de pele, barba e cabelo em molas e dois machados de guerra.
 * O combo tem 3 golpes: machado direito, machado esquerdo e batida dupla com choque no chão.
 */
object Barbarian3D {
    private val V = VoxelArt

    private const val MAX = 520
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

    private fun projectTo(x: Float, y: Float, z: Float, out: Int) {
        val m = stack[top]
        val wx = m[0] * x + m[1] * y + m[2] * z + m[9]
        val wy = m[3] * x + m[4] * y + m[5] * z + m[10]
        val wz = m[6] * x + m[7] * y + m[8] * z + m[11]
        ptX[out] = ox + wx * rs
        ptY[out] = oy + (wz * SINP - wy * COSP) * rs
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

    // ───────────── utilidades ─────────────

    private fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t

    private fun ease(t: Float): Float {
        val x = t.coerceIn(0f, 1f)
        return x * x * (3f - 2f * x)
    }

    private fun rgb(r: Int, g: Int, b: Int): Int = (0xFF shl 24) or (r shl 16) or (g shl 8) or b

    // ───────────── estado externo (preenchido pelo Player antes de desenhar) ─────────────
    var rollP = -1f
    var inAir = false
    var airVz = 0f
    var airH = 0f
    var swing = 0               // 0 machado direito, 1 machado esquerdo, 2 batida dupla
    var alphaV = 1f
    var leapCharge = -1f        // 0..1 enquanto carrega o salto; <0 = sem carga
    var leaping = false         // no ar durante o salto carregado
    var slamReq = false         // pedido de efeito de pouso (o jogo marca ao aterrissar do salto)

    // ───────────── estado de animação ─────────────
    private var yaw = 0f
    private var airW = 0f
    private var rollW = 0f
    private var blend = 0f
    private var clock = 0f
    private var lastNs = 0L
    private var eyeOpen = 1f
    private var prevAtk = 0f
    private var prevPhase = 0f
    private var spd = 0f
    private var wasAir = false
    private var wasRolling = false
    private var landDip = 0f
    private var airTimer = 0f
    private var prevFlash = 0f
    private var hitKick = 0f
    private var prevSin = 0f
    private var dustT = 0f
    private var released = false
    private var shockT = -1f
    private var shockSw = 0
    private var curSwing = 0
    private var furyW = 0f
    private var axeP = 0
    private var chW = 0f
    private var leapW = 0f
    private var slamFire = false
    private var shockMax = 26f
    private var shockOff = 15f
    // pontas das lâminas projetadas na tela: 0/1 = machado direito (topo/base do fio), 2/3 = esquerdo
    private val ptX = FloatArray(4)
    private val ptY = FloatArray(4)

    // rastro do fio dos machados: TRAIL_MAX quadros, 2 pontas por machado
    private const val TRAIL_MAX = 10
    private val trX = Array(4) { FloatArray(TRAIL_MAX) }
    private val trY = Array(4) { FloatArray(TRAIL_MAX) }
    private var trN = 0

    // molas: 0 inclinação, 1 banking, 2-4 capa, 5-6 barba, 7-8 cabelo, 9 saia da frente, 10 saia de trás, 11 cabeça
    private val sp = FloatArray(12)
    private val sv = FloatArray(12)

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

    // faíscas e brasas
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

    // chaves do golpe: rsh, rshZ, rel, rth, rtw, lsh, lshZ, lel, lth, ltw, tw, crouch, lean, hipTw
    // (rth/lth = inclinação absoluta do machado: 0 aponta para cima, 1.57 para a frente, 3.14 para baixo)
    private const val NK = 14
    private val kS = FloatArray(NK)
    private val kI = FloatArray(NK)
    private val kO = FloatArray(NK)
    private val lastPose = FloatArray(NK)

    private fun mir(k: FloatArray): FloatArray = floatArrayOf(
        k[5], -k[6], k[7], k[8], -k[9], k[0], -k[1], k[2], k[3], -k[4], -k[10], k[11], k[12], -k[13]
    )

    private fun both(rsh: Float, rshZ: Float, rel: Float, rth: Float, rtw: Float, tw: Float, crouch: Float, lean: Float): FloatArray =
        floatArrayOf(rsh, rshZ, rel, rth, rtw, rsh, -rshZ, rel, rth, -rtw, tw, crouch, lean, 0f)

    // golpe 0: machado direito em diagonal (de cima à direita para baixo à esquerda); golpe 1 é o espelho
    private val K0W = floatArrayOf(-2.9f, 0.45f, -1.5f, -0.5f, -0.2f, -0.3f, 0.5f, -1.2f, 0.5f, 0.7f, -0.55f, 0.15f, -0.25f, 0.4f)
    private val K0M = floatArrayOf(-1.3f, 0.75f, -0.7f, 1.4f, 0f, -0.1f, 0.6f, -1.1f, 0.6f, 0.7f, 0.25f, 0.45f, 0.2f, -0.1f)
    private val K0E = floatArrayOf(-0.5f, 1.0f, -0.3f, 2.2f, 0f, 0.2f, 0.7f, -0.9f, 0.7f, 0.7f, 0.65f, 0.6f, 0.35f, -0.4f)
    // golpe 2: batida dupla, os dois machados sobem juntos e descem no chão
    private val K2W = both(-2.9f, 0.25f, -1.1f, -0.2f, -0.3f, 0f, 0.1f, -0.3f)
    private val K2M = both(-1.7f, 0.2f, -0.5f, 1.5f, -0.2f, 0f, 0.5f, 0.35f)
    private val K2E = both(-1.0f, 0.3f, -0.3f, 2.4f, -0.1f, 0f, 0.65f, 0.5f)
    private val KW = arrayOf(K0W, mir(K0W), K2W)
    private val KM = arrayOf(K0M, mir(K0M), K2M)
    private val KE = arrayOf(K0E, mir(K0E), K2E)

    // tempos (fração da animação) por golpe: fim da preparação, impacto, fim do movimento.
    // Manter igual a Player.barbDelay() / attackRate.
    private val TW = floatArrayOf(0.22f, 0.22f, 0.30f)
    private val TM = floatArrayOf(0.36f, 0.36f, 0.44f)
    private val TE = floatArrayOf(0.58f, 0.58f, 0.64f)

    /** Fração da animação em que o golpe [sw] acerta. */
    fun impactAt(sw: Int): Float = TM[sw.coerceIn(0, 2)]

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

    // ───────────── paleta do bárbaro ─────────────
    private val SKIN = 0xFFD99A5E.toInt()
    private val SKIN_L = 0xFFEBB77C.toInt()
    private val SKIN_D = 0xFFB07440.toInt()
    private val SKIN_DD = 0xFF8A5530.toInt()
    private val SCAR = 0xFFE8B8A0.toInt()
    private val HAIR = 0xFFB8421C.toInt()
    private val HAIR_L = 0xFFE0702E.toInt()
    private val HAIR_D = 0xFF7E2A12.toInt()
    private val PAINT = 0xFFC62828.toInt()
    private val PAINT_D = 0xFF8E1B1B.toInt()
    private val LEATHER = 0xFF5A3A26.toInt()
    private val LEATHER_L = 0xFF7A5238.toInt()
    private val LEATHER_D = 0xFF3A2416.toInt()
    private val PANTS = 0xFF4A3426.toInt()
    private val PANTS_L = 0xFF5E4533.toInt()
    private val IRON = 0xFF8A929B.toInt()
    private val IRON_L = 0xFFB7BEC6.toInt()
    private val IRON_D = 0xFF4E555C.toInt()
    private val IRON_DD = 0xFF33383D.toInt()
    private val BLADE = 0xFFC9D3DA.toInt()
    private val BLADE_L = 0xFFF0F5F8.toInt()
    private val BLADE_D = 0xFF8794A0.toInt()
    private val BONE = 0xFFE8DFC8.toInt()
    private val BONE_L = 0xFFF8F2E2.toInt()
    private val BONE_D = 0xFFB9AE92.toInt()
    private val FUR = 0xFF8D8576.toInt()
    private val FUR_L = 0xFFB3AB99.toInt()
    private val FUR_D = 0xFF5E584D.toInt()
    private val FUR_DD = 0xFF433F37.toInt()
    private val WOOD = 0xFF8A5A33.toInt()

    private val WOOD_L = 0xFFAD7842.toInt()
    private val WOOD_D = 0xFF5E3B20.toInt()
    private val RED = 0xFFB3261E.toInt()
    private val RED_D = 0xFF7E1A14.toInt()
    private val EYE = 0xFF1B1B1B.toInt()
    private val WHITE = 0xFFFFFFFF.toInt()
    private val FURY = 0xFFFF6A1F.toInt()
    private val FURY_L = 0xFFFFC27A.toInt()
    // cores que pulsam com a fúria (atualizadas a cada quadro em draw)
    private var furyC = 0xFFFF6A1F.toInt()
    private var furyL = 0xFFFFC27A.toInt()
    private var eyeC = 0xFFFFE9C0.toInt()

    private fun leg(side: Float, a: Float, knee: Float, hipY: Float, foot: Float) {
        push()
            translate(side * 3.7f, hipY, 0f)
            rotX(a)
            cube(0f, -2.7f, 0f, 2.8f, 2.9f, 2.8f, PANTS)
            cube(0f, -0.4f, 0f, 3.0f, 0.7f, 3.0f, FUR)
            cube(0f, -1.1f, 0.15f, 3.05f, 0.35f, 3.05f, FUR_D)
            cube(0f, -3.9f, 0f, 2.95f, 0.3f, 2.95f, LEATHER_D)
            cube(0f, -3.9f, 2.98f, 0.5f, 0.45f, 0.12f, IRON)
            cube(side * 2.85f, -2.7f, 0f, 0.15f, 2.1f, 1.3f, LEATHER_L)
            cube(0f, -2.3f, 2.85f, 1.4f, 1.5f, 0.12f, PANTS_L)
            cube(-0.4f, -2.3f, 2.98f, 0.7f, 0.12f, 0.06f, LEATHER_D)
            translate(0f, -5.6f, 0f)
            rotX(knee)
            cube(0f, 0.2f, 2.3f, 2.3f, 1.5f, 0.9f, IRON_D)
            cube(0f, 0.3f, 3.2f, 1.5f, 1.0f, 0.4f, IRON)
            cube(0f, 0.4f, 3.8f, 0.45f, 0.45f, 0.7f, BONE)
            cube(0f, -2.7f, 0f, 2.55f, 2.8f, 2.6f, LEATHER)
            cube(0f, -2.6f, 2.62f, 1.3f, 2.3f, 0.2f, IRON_D)

            cube(0f, -2.6f, 2.84f, 0.55f, 2.0f, 0.1f, IRON)
            cube(0f, -1.2f, 0f, 2.7f, 0.3f, 2.75f, LEATHER_D)
            cube(0f, -3.7f, 0f, 2.7f, 0.3f, 2.75f, LEATHER_D)
            cube(side * 2.6f, -2.7f, 0f, 0.15f, 2.4f, 2.0f, LEATHER_L)
            cube(0f, -5.2f, 0f, 2.95f, 0.7f, 3.0f, FUR)
            cube(0f, -5.8f, 0.2f, 3.0f, 0.4f, 3.05f, FUR_D)
            translate(0f, -5.4f, 0f)
            rotX(foot)
            cube(0f, 0.2f, 1.2f, 2.8f, 1.5f, 3.8f, LEATHER)
            cube(0f, -1.2f, 1.3f, 2.95f, 0.3f, 4.0f, LEATHER_D)
            cube(0f, 1.0f, 4.7f, 2.3f, 0.9f, 0.9f, IRON_D)
            cube(0f, 1.9f, 0f, 2.9f, 0.4f, 2.9f, LEATHER_D)
            cube(0f, 0.9f, -2.4f, 2f, 1f, 0.5f, LEATHER_D)
        pop()
    }

    private fun pauldron(side: Float) {
        cube(side * 0.9f, 2.2f, 0f, 2.7f, 0.9f, 2.9f, FUR)
        cube(side * 1.3f, 1.2f, 0f, 3.4f, 0.9f, 3.6f, FUR_D)
        cube(side * 2.9f, 0.1f, 0f, 2.6f, 0.9f, 3.4f, FUR)
        cube(side * 3.6f, -1.0f, 0f, 2.1f, 0.7f, 3.1f, FUR_D)
        cube(side * 0.4f, 3.1f, 0.8f, 1.2f, 0.5f, 1.3f, FUR_L)
        cube(side * 2.2f, 2.9f, -1.0f, 1.1f, 0.45f, 1.1f, FUR_L)
        cube(side * 3.6f, 1.6f, 2.2f, 0.9f, 0.5f, 0.9f, FUR_L)
        cube(side * 3.7f, 1.4f, -2.3f, 0.9f, 0.5f, 0.9f, FUR)
        cube(side * 1.2f, 2.0f, 3.2f, 1.0f, 0.5f, 0.5f, FUR_L)
        cube(side * 1.4f, 2.0f, -3.1f, 1.0f, 0.5f, 0.5f, FUR)
        cube(side * 4.2f, 0.9f, 0f, 0.5f, 1.8f, 2.6f, LEATHER)
        cube(side * 4.65f, 0.9f, 0f, 0.15f, 1.4f, 2.1f, LEATHER_L)
        cube(side * 2.5f, 3.4f, 0.2f, 0.65f, 1.0f, 0.65f, IRON)
        cube(side * 2.5f, 4.7f, 0.2f, 0.4f, 0.6f, 0.4f, IRON_L)
        cube(side * 5.0f, 1.9f, 0.2f, 0.8f, 0.45f, 0.45f, IRON)
        cube(side * 6.0f, 2.0f, 0.2f, 0.5f, 0.3f, 0.3f, IRON_L)
    }

    private fun armBase(side: Float, el: Float) {
        cube(0f, -2.7f, 0f, 2.6f, 2.9f, 2.6f, SKIN)
        cube(0f, -2.1f, 0.5f, 2.7f, 1.7f, 2.4f, SKIN_L)
        cube(side * 2.62f, -2.4f, 0f, 0.1f, 1.8f, 1.6f, SKIN_D)
        cube(0f, -4.3f, 0f, 2.85f, 0.55f, 2.85f, IRON_D)
        cube(side * 3.2f, -4.3f, 0f, 0.55f, 0.35f, 0.35f, IRON)
        cube(0f, -4.3f, 2.9f, 0.7f, 0.35f, 0.12f, IRON_L)
        translate(0f, -5.7f, 0f)
        rotX(el)
        cube(0f, -0.1f, 0f, 2.4f, 1.0f, 2.4f, SKIN_D)
        cube(0f, -2.5f, 0f, 2.35f, 2.6f, 2.35f, SKIN)
        cube(0f, -1.9f, 0f, 2.45f, 0.3f, 2.45f, LEATHER_D)
        cube(0f, -4.0f, 0f, 2.7f, 1.7f, 2.7f, IRON_D)
        cube(0f, -4.0f, 2.75f, 1.4f, 1.2f, 0.12f, IRON)
        cube(0f, -4.0f, 2.88f, 0.5f, 0.5f, 0.1f, furyC, true)
        cube(side * 2.9f, -3.3f, 0f, 0.55f, 0.35f, 0.35f, IRON)
        cube(side * 2.9f, -4.7f, 0f, 0.55f, 0.35f, 0.35f, IRON)
        cube(0f, -5.9f, 0f, 2.2f, 0.4f, 2.2f, LEATHER)
        cube(0f, -6.8f, 0f, 1.9f, 1.0f, 1.9f, SKIN)
        cube(0f, -6.8f, 1.6f, 1.7f, 0.9f, 0.35f, SKIN_D)
    }

    /** Machado de guerra de uma mão. Origem na mão, o cabo sobe pelo +Y e o fio aponta para +Z. */
    private fun axe() {
        cube(0f, 6.5f, 0f, 0.8f, 10.7f, 0.8f, WOOD)
        cube(0f, 6.5f, 0.78f, 0.3f, 10.3f, 0.1f, WOOD_L)
        cube(0f, 6.5f, -0.78f, 0.3f, 10.3f, 0.1f, WOOD_D)
        cube(0f, -1.5f, 0f, 1.0f, 0.5f, 1.0f, LEATHER_D)
        cube(0f, -0.4f, 0f, 1.0f, 0.5f, 1.0f, LEATHER)
        cube(0f, 0.7f, 0f, 1.0f, 0.5f, 1.0f, LEATHER_D)
        cube(0f, 1.8f, 0f, 1.0f, 0.5f, 1.0f, LEATHER)
        cube(0f, -4.4f, 0f, 1.05f, 0.5f, 1.05f, IRON_D)
        cube(0f, -5.4f, 0f, 0.5f, 0.7f, 0.5f, IRON)
        cube(0f, -6.3f, 0f, 0.28f, 0.5f, 0.28f, IRON_L)
        cube(0f, 3.3f, 0f, 0.95f, 0.3f, 0.95f, IRON_D)
        cube(0f, 3.4f, 1.0f, 0.2f, 0.9f, 0.2f, LEATHER_D)
        cube(0f, 2.6f, 1.0f, 0.3f, 0.6f, 0.3f, BONE)
        cube(0f, 9.4f, 0f, 1.05f, 0.5f, 1.05f, IRON_D)
        cube(0f, 15.6f, 0f, 1.05f, 0.5f, 1.05f, IRON_D)
        cube(0f, 12.5f, 0.4f, 1.0f, 3.2f, 1.3f, IRON_D)
        cube(0f, 12.6f, 2.8f, 0.7f, 3.4f, 1.3f, BLADE_D)
        cube(0f, 12.7f, 4.4f, 0.65f, 4.4f, 1.0f, BLADE)
        cube(0f, 12.8f, 5.6f, 0.55f, 5.4f, 0.65f, BLADE)
        cube(0f, 12.9f, 6.5f, 0.45f, 6.0f, 0.4f, BLADE_D)
        cube(0f, 12.9f, 7.0f, 0.3f, 6.2f, 0.15f, BLADE_L)
        cube(0f, 19.0f, 6.1f, 0.5f, 0.9f, 0.6f, BLADE)
        cube(0f, 20.0f, 6.15f, 0.35f, 0.55f, 0.45f, BLADE_L)
        cube(0f, 6.7f, 6.1f, 0.5f, 0.9f, 0.6f, BLADE)
        cube(0f, 5.7f, 6.15f, 0.35f, 0.55f, 0.45f, BLADE_L)
        cube(0f, 12.7f, 3.8f, 0.72f, 0.2f, 0.6f, IRON_DD)
        cube(0f, 16.3f, 3.0f, 0.72f, 0.2f, 0.5f, IRON_DD)
        cube(0f, 9.1f, 3.0f, 0.72f, 0.2f, 0.5f, IRON_DD)
        cube(0.74f, 12.8f, 4.6f, 0.06f, 2.1f, 0.55f, furyC, true)
        cube(-0.74f, 12.8f, 4.6f, 0.06f, 2.1f, 0.55f, furyC, true)

        cube(0.76f, 12.8f, 4.6f, 0.04f, 1.0f, 0.28f, furyL, true)
        cube(-0.76f, 12.8f, 4.6f, 0.04f, 1.0f, 0.28f, furyL, true)
        cube(0f, 12.5f, -1.5f, 0.55f, 1.4f, 0.9f, IRON_D)
        cube(0f, 12.5f, -2.7f, 0.4f, 0.9f, 0.7f, IRON)
        cube(0f, 12.5f, -3.6f, 0.25f, 0.4f, 0.5f, IRON_L)
        cube(0f, 18.0f, 0f, 0.55f, 0.6f, 0.55f, IRON)
        cube(0f, 19.1f, 0f, 0.3f, 0.9f, 0.3f, IRON_L)
        projectTo(0f, 20.0f, 6.15f, axeP)
        projectTo(0f, 5.7f, 6.15f, axeP + 1)
    }

    private fun rightArm(sh: Float, shZ: Float, el: Float, grip: Float, tw: Float) {
        axeP = 0
        push()
        translate(-9.6f, 13.4f, 0f)
        pauldron(-1f)
        rotZ(shZ)
        rotX(sh)
        armBase(-1f, el)
        translate(0f, -6.8f, 0f)
        rotX(grip)
        rotY(tw)
        axe()
        pop()
    }

    private fun leftArm(sh: Float, shZ: Float, el: Float, grip: Float, tw: Float) {
        axeP = 2
        push()
        translate(9.6f, 13.4f, 0f)
        pauldron(1f)
        wolfHead()
        rotZ(shZ)
        rotX(sh)
        armBase(1f, el)
        translate(0f, -6.8f, 0f)
        rotX(grip)
        rotY(tw)
        axe()
        pop()
    }

    /** Cabeça de lobo sobre o ombro esquerdo. */
    private fun wolfHead() {
        cube(1.8f, 3.6f, 1.2f, 2.3f, 1.5f, 2.2f, FUR_D)

        cube(1.8f, 3.2f, 3.9f, 1.4f, 1.0f, 1.6f, FUR)
        cube(1.8f, 3.5f, 5.6f, 0.55f, 0.55f, 0.3f, EYE)
        cube(1.8f, 2.1f, 4.2f, 1.0f, 0.3f, 1.3f, FUR_L)
        cube(0.9f, 2.0f, 5.0f, 0.2f, 0.5f, 0.18f, BONE_L)
        cube(2.7f, 2.0f, 5.0f, 0.2f, 0.5f, 0.18f, BONE_L)
        cube(0.4f, 5.6f, 0.4f, 0.55f, 1.2f, 0.5f, FUR_D)
        cube(3.2f, 5.6f, 0.4f, 0.55f, 1.2f, 0.5f, FUR_D)
        cube(0.4f, 5.4f, 0.8f, 0.3f, 0.8f, 0.2f, FUR_DD)
        cube(3.2f, 5.4f, 0.8f, 0.3f, 0.8f, 0.2f, FUR_DD)
        cube(0.1f, 3.9f, 2.9f, 0.3f, 0.3f, 0.12f, furyC, true)
        cube(3.5f, 3.9f, 2.9f, 0.3f, 0.3f, 0.12f, furyC, true)
        cube(1.8f, 1.9f, -1.4f, 2.0f, 1.2f, 0.9f, FUR)
    }

    private fun horn(sd: Float) {
        push()
            translate(sd * 5.0f, 8.8f, 0.2f)
            rotZ(-sd * 1.05f)
            cube(0f, 0.9f, 0f, 1.5f, 1.3f, 1.5f, BONE_D)
            cube(0f, 0.1f, 0f, 1.6f, 0.3f, 1.6f, IRON)
            translate(0f, 2.4f, 0f)
            rotZ(sd * 0.3f)
            rotX(0.1f)
            cube(0f, 1.6f, 0f, 1.15f, 1.7f, 1.15f, BONE)
            cube(0f, 0.2f, 0f, 1.25f, 0.25f, 1.25f, IRON)
            translate(0f, 3.3f, 0f)
            rotZ(sd * 0.45f)
            rotX(0.1f)
            cube(0f, 1.4f, 0f, 0.85f, 1.5f, 0.85f, BONE)
            translate(0f, 2.9f, 0f)
            rotZ(sd * 0.45f)
            rotX(0.1f)

            cube(0f, 1.1f, 0f, 0.55f, 1.2f, 0.55f, BONE_L)
            translate(0f, 2.3f, 0f)
            rotZ(sd * 0.4f)
            cube(0f, 0.8f, 0f, 0.3f, 0.9f, 0.3f, BONE_L)
        pop()
    }

    private fun head(turn: Float, tilt: Float, b0: Float, b1: Float, h0: Float, h1: Float) {
        push()
            translate(0f, 14.4f, 0f)
            rotY(turn)
            rotX(tilt)
            cube(0f, 0.3f, 0f, 2.6f, 1.2f, 2.5f, SKIN_D)
            cube(0f, 5.0f, 0f, 4.8f, 4.7f, 4.6f, SKIN)
            cube(0f, 5.0f, 4.65f, 4.5f, 4.4f, 0.06f, SKIN_L)
            cube(-2.2f, 4.8f, 4.7f, 1.05f, 0.9f * eyeOpen, 0.12f, EYE)
            cube(2.2f, 4.8f, 4.7f, 1.05f, 0.9f * eyeOpen, 0.12f, EYE)
            cube(-2.2f, 4.8f, 4.8f, 0.55f, 0.45f * eyeOpen, 0.1f, eyeC, true)
            cube(2.2f, 4.8f, 4.8f, 0.55f, 0.45f * eyeOpen, 0.1f, eyeC, true)
            cube(-2.3f, 5.9f, 4.75f, 1.5f, 0.45f, 0.18f, SKIN_DD)
            cube(2.3f, 5.9f, 4.75f, 1.5f, 0.45f, 0.18f, SKIN_DD)
            cube(0f, 3.4f, 4.9f, 0.7f, 1.2f, 0.45f, SKIN_D)
            cube(0f, 2.3f, 4.75f, 1.3f, 0.2f, 0.12f, SKIN_DD)
            cube(-2.8f, 3.3f, 4.74f, 0.22f, 1.5f, 0.08f, PAINT)
            cube(-3.6f, 3.3f, 4.74f, 0.22f, 1.5f, 0.08f, PAINT)
            cube(2.8f, 3.3f, 4.74f, 0.22f, 1.5f, 0.08f, PAINT)
            cube(3.6f, 3.3f, 4.74f, 0.22f, 1.5f, 0.08f, PAINT)
            cube(0f, 6.5f, 4.74f, 2.6f, 0.28f, 0.08f, PAINT)
            cube(0f, 7.2f, 4.74f, 1.4f, 0.22f, 0.08f, PAINT_D)
            cube(2.9f, 4.3f, 4.76f, 0.15f, 1.3f, 0.08f, SCAR)
            cube(0f, 1.1f, 4.55f, 4.0f, 1.5f, 0.55f, HAIR_D)
            cube(0f, 2.1f, 4.4f, 3.4f, 0.5f, 0.5f, HAIR)

            cube(-1.7f, 2.9f, 4.85f, 1.7f, 0.42f, 0.38f, HAIR)
            cube(1.7f, 2.9f, 4.85f, 1.7f, 0.42f, 0.38f, HAIR)
            cube(-4.75f, 2.4f, 1.8f, 0.3f, 2.3f, 2.6f, HAIR_D)
            cube(4.75f, 2.4f, 1.8f, 0.3f, 2.3f, 2.6f, HAIR_D)
            cube(-4.8f, 5.6f, -1.0f, 0.4f, 2.4f, 3.4f, HAIR)
            cube(4.8f, 5.6f, -1.0f, 0.4f, 2.4f, 3.4f, HAIR)
            cube(0f, 4.2f, -4.7f, 4.6f, 4.4f, 0.55f, HAIR)
            cube(0f, 1.2f, -4.6f, 3.4f, 1.3f, 0.5f, HAIR_D)
            cube(0f, 8.5f, 0f, 5.25f, 2.1f, 5.05f, IRON_D)
            cube(0f, 6.5f, 0f, 5.35f, 0.5f, 5.15f, LEATHER)
            cube(0f, 6.5f, 5.2f, 4.9f, 0.5f, 0.16f, IRON)
            cube(0f, 10.8f, 0f, 4.6f, 0.7f, 4.4f, IRON_D)
            cube(0f, 11.7f, -0.2f, 3.5f, 0.6f, 3.4f, IRON)
            cube(0f, 12.5f, -0.4f, 0.55f, 0.6f, 3.6f, IRON_L)
            cube(0f, 8.7f, 5.1f, 4.4f, 1.3f, 0.12f, IRON)
            cube(0f, 9.6f, 5.15f, 0.5f, 0.9f, 0.1f, IRON_L)
            cube(-2.6f, 6.5f, 5.35f, 0.3f, 0.3f, 0.12f, IRON_L)
            cube(2.6f, 6.5f, 5.35f, 0.3f, 0.3f, 0.12f, IRON_L)
            cube(0f, 6.5f, 5.35f, 0.3f, 0.3f, 0.12f, IRON_L)
            cube(0f, 8.9f, 5.2f, 0.9f, 0.9f, 0.1f, BONE)
            cube(0f, 8.9f, 5.28f, 0.4f, 0.4f, 0.08f, furyC, true)
            horn(1f)
            horn(-1f)
            push()
                translate(0f, 1.5f, 5.2f)
                rotX(-0.05f + b0)
                cube(-1.3f, -1.7f, 0.1f, 0.9f, 1.9f, 0.55f, HAIR_D)
                cube(1.3f, -1.7f, 0.1f, 0.9f, 1.9f, 0.55f, HAIR_D)
                cube(0f, -1.0f, 0f, 1.0f, 1.2f, 0.5f, HAIR)
                translate(0f, -3.6f, 0f)
                rotX(b1)
                cube(-1.3f, -1.5f, 0.1f, 0.75f, 1.6f, 0.5f, HAIR)
                cube(1.3f, -1.5f, 0.1f, 0.75f, 1.6f, 0.5f, HAIR)

                cube(0f, -0.6f, 0f, 0.7f, 0.7f, 0.45f, HAIR_D)
                translate(0f, -3.2f, 0f)
                rotX(b1 * 1.2f)
                cube(-1.3f, -0.9f, 0.1f, 0.5f, 1.0f, 0.4f, HAIR_D)
                cube(1.3f, -0.9f, 0.1f, 0.5f, 1.0f, 0.4f, HAIR_D)
                cube(-1.3f, -2.1f, 0.1f, 0.65f, 0.3f, 0.65f, IRON)
                cube(1.3f, -2.1f, 0.1f, 0.65f, 0.3f, 0.65f, IRON)
            pop()
            push()
                translate(0f, 9.0f, -5.0f)
                rotX(0.35f + h0)
                cube(0f, -1.6f, -0.2f, 3.0f, 2.2f, 1.0f, HAIR)
                cube(0f, -1.0f, -1.1f, 1.3f, 1.8f, 0.4f, HAIR_L)
                translate(0f, -3.8f, 0f)
                rotX(0.25f + h1)
                cube(0f, -2.2f, -0.2f, 2.6f, 2.3f, 0.9f, HAIR_D)
                cube(0f, -2.0f, -1.0f, 1.0f, 1.8f, 0.4f, HAIR)
                translate(0f, -4.4f, 0f)
                rotX(0.3f + h1 * 1.3f)
                cube(0f, -1.6f, -0.2f, 1.9f, 1.7f, 0.8f, HAIR)
                cube(0f, -3.4f, -0.2f, 0.8f, 0.5f, 0.5f, HAIR_D)
            pop()
        pop()
    }

    private fun torso(chestTw: Float, c0: Float, c1: Float, c2: Float, kF: Float, kB: Float, breath: Float) {
        cube(0f, 0.6f, 0f, 6.3f, 1.5f, 3.9f, LEATHER)
        cube(0f, 2.0f, 0f, 6.35f, 0.2f, 3.95f, LEATHER_L)
        cube(0f, 0.8f, 3.95f, 2.2f, 1.5f, 0.5f, IRON_D)
        cube(0f, 0.8f, 4.45f, 1.5f, 1.15f, 0.22f, BONE)
        cube(-0.55f, 1.0f, 4.7f, 0.4f, 0.4f, 0.08f, EYE)
        cube(0.55f, 1.0f, 4.7f, 0.4f, 0.4f, 0.08f, EYE)

        cube(0f, 0.3f, 4.7f, 0.7f, 0.3f, 0.08f, BONE_D)
        cube(-3.6f, 0.8f, 3.95f, 0.4f, 0.4f, 0.2f, IRON)
        cube(3.6f, 0.8f, 3.95f, 0.4f, 0.4f, 0.2f, IRON)
        cube(-6.5f, 0.0f, 0.5f, 0.9f, 1.8f, 2.4f, LEATHER_D)
        cube(-6.95f, 0.0f, 0.5f, 0.15f, 1.4f, 1.9f, LEATHER_L)
        cube(-6.9f, 1.6f, 0.5f, 0.2f, 0.4f, 0.7f, IRON)
        cube(6.6f, -1.3f, 0.6f, 0.9f, 2.0f, 1.0f, BONE)
        cube(6.6f, 0.3f, 0.6f, 1.0f, 0.4f, 1.1f, IRON_D)
        cube(6.6f, -3.2f, 0.6f, 0.5f, 0.45f, 0.5f, BONE_D)
        cube(4.7f, -1.6f, 4.0f, 0.9f, 1.0f, 0.8f, BONE)
        cube(4.7f, -1.5f, 4.82f, 0.25f, 0.25f, 0.08f, EYE)
        cube(5.3f, -1.5f, 4.82f, 0.25f, 0.25f, 0.08f, EYE)
        cube(5.0f, 0.3f, 4.0f, 0.2f, 0.9f, 0.2f, LEATHER_D)
        push()
            translate(0f, -0.6f, 4.0f)
            rotX(kF)
            cube(0f, -2.8f, 0.2f, 3.2f, 3.0f, 0.35f, LEATHER_D)
            cube(0f, -2.4f, 0.6f, 0.9f, 2.4f, 0.12f, RED)
            cube(0f, -2.4f, 0.75f, 0.35f, 2.0f, 0.08f, RED_D)
            cube(0f, -2.2f, 0.85f, 0.5f, 0.5f, 0.08f, furyC, true)
            cube(0f, -5.9f, 0.3f, 3.3f, 0.5f, 0.5f, FUR)
            cube(-2.4f, -6.5f, 0.3f, 0.6f, 0.5f, 0.4f, FUR_D)
            cube(-0.8f, -6.6f, 0.3f, 0.6f, 0.5f, 0.4f, FUR)
            cube(0.8f, -6.6f, 0.3f, 0.6f, 0.5f, 0.4f, FUR_D)
            cube(2.4f, -6.5f, 0.3f, 0.6f, 0.5f, 0.4f, FUR)
            cube(-3.1f, -2.6f, 0.5f, 0.15f, 2.4f, 0.1f, LEATHER_L)
            cube(3.1f, -2.6f, 0.5f, 0.15f, 2.4f, 0.1f, LEATHER_L)
        pop()
        push()
            translate(0f, -0.6f, -4.0f)
            rotX(kB)
            cube(0f, -2.8f, -0.2f, 3.4f, 3.0f, 0.35f, LEATHER_D)

            cube(0f, -5.9f, -0.3f, 3.5f, 0.5f, 0.5f, FUR_D)
            cube(-2.4f, -6.5f, -0.3f, 0.6f, 0.5f, 0.4f, FUR)
            cube(0f, -6.6f, -0.3f, 0.6f, 0.5f, 0.4f, FUR_D)
            cube(2.4f, -6.5f, -0.3f, 0.6f, 0.5f, 0.4f, FUR)
        pop()
        cube(0f, 3.8f, 0f, 5.9f, 2.2f, 3.7f, SKIN)
        cube(0f, 3.8f, 3.7f, 5.6f, 2.0f, 0.12f, SKIN_L)
        cube(0f, 2.6f, 3.78f, 1.5f, 0.7f, 0.12f, SKIN_L)
        cube(0f, 4.2f, 3.78f, 1.5f, 0.7f, 0.12f, SKIN_L)
        cube(0f, 5.7f, 3.78f, 1.5f, 0.55f, 0.12f, SKIN_L)
        cube(-2.7f, 2.6f, 3.78f, 1.3f, 0.7f, 0.12f, SKIN_L)
        cube(2.7f, 2.6f, 3.78f, 1.3f, 0.7f, 0.12f, SKIN_L)
        cube(-2.7f, 4.2f, 3.78f, 1.3f, 0.7f, 0.12f, SKIN_L)
        cube(2.7f, 4.2f, 3.78f, 1.3f, 0.7f, 0.12f, SKIN_L)
        cube(-2.7f, 5.7f, 3.78f, 1.3f, 0.55f, 0.12f, SKIN_L)
        cube(2.7f, 5.7f, 3.78f, 1.3f, 0.55f, 0.12f, SKIN_L)
        cube(0f, 3.4f, 3.8f, 0.1f, 2.5f, 0.1f, SKIN_DD)
        cube(0f, 3.4f, 3.8f, 5.6f, 0.08f, 0.1f, SKIN_DD)
        cube(0f, 5.0f, 3.8f, 5.6f, 0.08f, 0.1f, SKIN_DD)
        cube(-3.0f, 3.2f, 3.84f, 0.12f, 1.7f, 0.08f, SCAR)
        cube(-2.5f, 3.9f, 3.84f, 0.5f, 0.12f, 0.08f, SCAR)
        cube(-5.95f, 3.8f, 0f, 0.25f, 2.2f, 3.5f, SKIN_D)
        cube(5.95f, 3.8f, 0f, 0.25f, 2.2f, 3.5f, SKIN_D)
        push()
            rotY(chestTw)
            translate(0f, breath, 0f)
            cube(0f, 9.8f, 0f, 7.4f, 5.0f, 4.2f, SKIN)
            cube(-3.6f, 10.6f, 4.25f, 3.4f, 2.4f, 0.5f, SKIN_L)
            cube(3.6f, 10.6f, 4.25f, 3.4f, 2.4f, 0.5f, SKIN_L)
            cube(0f, 10.6f, 4.3f, 0.12f, 2.4f, 0.5f, SKIN_DD)
            cube(-3.6f, 8.1f, 4.4f, 3.2f, 0.15f, 0.4f, SKIN_DD)
            cube(3.6f, 8.1f, 4.4f, 3.2f, 0.15f, 0.4f, SKIN_DD)

            cube(5.2f, 11.2f, 4.8f, 0.14f, 1.4f, 0.1f, SCAR)
            cube(-4.8f, 9.4f, 4.8f, 0.14f, 1.1f, 0.1f, SCAR)
            push()
                translate(0f, 10.2f, 4.85f)
                rotZ(0.72f)
                cube(0f, 0f, 0f, 0.95f, 7.9f, 0.16f, LEATHER)
                cube(0f, 0f, 0.18f, 0.25f, 7.9f, 0.06f, LEATHER_L)
                cube(0f, 3.4f, 0.2f, 0.45f, 0.45f, 0.1f, IRON)
                cube(0f, -3.4f, 0.2f, 0.45f, 0.45f, 0.1f, IRON)
            pop()
            push()
                translate(0f, 10.2f, 4.9f)
                rotZ(-0.72f)
                cube(0f, 0f, 0f, 0.95f, 7.9f, 0.16f, LEATHER)
                cube(0f, 0f, 0.18f, 0.25f, 7.9f, 0.06f, LEATHER_L)
                cube(0f, 3.4f, 0.2f, 0.45f, 0.45f, 0.1f, IRON)
                cube(0f, -3.4f, 0.2f, 0.45f, 0.45f, 0.1f, IRON)
            pop()
            cube(0f, 10.2f, 5.1f, 1.6f, 1.6f, 0.15f, IRON_D)
            cube(0f, 10.2f, 5.25f, 1.1f, 1.1f, 0.12f, IRON)
            cube(0f, 10.2f, 5.35f, 0.6f, 0.6f, 0.1f, furyC, true)
            cube(0f, 13.0f, 4.45f, 3.0f, 0.2f, 0.15f, LEATHER_D)
            cube(-2.4f, 12.1f, 4.5f, 0.3f, 0.9f, 0.25f, BONE)
            cube(-1.2f, 11.7f, 4.55f, 0.35f, 1.3f, 0.25f, BONE_L)
            cube(0f, 11.5f, 4.6f, 0.4f, 1.5f, 0.25f, BONE)
            cube(1.2f, 11.7f, 4.55f, 0.35f, 1.3f, 0.25f, BONE_L)
            cube(2.4f, 12.1f, 4.5f, 0.3f, 0.9f, 0.25f, BONE)
            cube(0f, 10.0f, -4.25f, 6.8f, 4.5f, 0.5f, SKIN_D)
            cube(0f, 11.0f, -4.8f, 5.6f, 2.0f, 0.15f, SKIN)
            cube(0f, 9.0f, -4.8f, 0.14f, 3.4f, 0.1f, SKIN_DD)
            cube(-3.5f, 12.0f, -4.85f, 0.14f, 1.2f, 0.1f, SCAR)
            cube(0f, 14.4f, 0f, 4.2f, 1.0f, 3.4f, SKIN_D)

            cube(0f, 14.1f, 0f, 7.9f, 0.7f, 4.6f, FUR)
            cube(0f, 13.6f, 0.3f, 8.1f, 0.5f, 4.8f, FUR_D)
            cube(-6.4f, 13.2f, 4.6f, 1.2f, 0.9f, 0.5f, FUR_D)
            cube(-4.2f, 13.2f, 4.6f, 1.2f, 0.9f, 0.5f, FUR)
            cube(-2.1f, 13.2f, 4.6f, 1.1f, 0.9f, 0.5f, FUR_D)
            cube(0f, 13.2f, 4.6f, 1.1f, 0.9f, 0.5f, FUR)
            cube(2.1f, 13.2f, 4.6f, 1.1f, 0.9f, 0.5f, FUR_D)
            cube(4.2f, 13.2f, 4.6f, 1.2f, 0.9f, 0.5f, FUR)
            cube(6.4f, 13.2f, 4.6f, 1.2f, 0.9f, 0.5f, FUR_D)
            push()
                translate(0f, 13.8f, -4.4f)
                rotX(c0)
                cube(0f, -2.9f, -0.4f, 7.2f, 3.1f, 0.5f, FUR_D)
                cube(0f, 0.9f, 0.1f, 7.6f, 0.9f, 1.3f, FUR)
                cube(-4.6f, -1.2f, -0.95f, 1.3f, 1.4f, 0.2f, FUR)
                cube(3.2f, -2.2f, -0.95f, 1.5f, 1.2f, 0.2f, FUR_L)
                cube(0f, -0.6f, -0.9f, 1.4f, 0.4f, 0.2f, LEATHER_D)
                cube(0f, -0.6f, -1.1f, 0.5f, 0.5f, 0.1f, IRON)
                translate(0f, -5.8f, 0f)
                rotX(c1)
                cube(0f, -2.9f, -0.4f, 7.0f, 3.0f, 0.5f, FUR)
                cube(-2.6f, -1.6f, -0.95f, 1.3f, 1.2f, 0.2f, FUR_L)
                cube(2.4f, -3.4f, -0.95f, 1.4f, 1.3f, 0.2f, FUR_D)
                cube(-6.9f, -2.9f, -0.4f, 0.3f, 3.0f, 0.6f, FUR_DD)
                cube(6.9f, -2.9f, -0.4f, 0.3f, 3.0f, 0.6f, FUR_DD)
                translate(0f, -5.8f, 0f)
                rotX(c2)
                cube(0f, -2.4f, -0.4f, 6.6f, 2.4f, 0.5f, FUR_D)
                cube(-6.6f, -2.4f, -0.4f, 0.3f, 2.4f, 0.6f, FUR_DD)
                cube(6.6f, -2.4f, -0.4f, 0.3f, 2.4f, 0.6f, FUR_DD)
                cube(-5.3f, -5.4f, -0.4f, 1.3f, 1.3f, 0.45f, FUR)
                cube(-2.6f, -5.9f, -0.4f, 1.3f, 1.6f, 0.45f, FUR_D)

                cube(0f, -5.4f, -0.4f, 1.3f, 1.3f, 0.45f, FUR)
                cube(2.6f, -5.9f, -0.4f, 1.3f, 1.6f, 0.45f, FUR_D)
                cube(5.3f, -5.4f, -0.4f, 1.3f, 1.3f, 0.45f, FUR)
            pop()
        pop()
    }

    /** Monta o corpo inteiro: pernas, quadril, peito (contra-rotação), cabeça e os dois braços. */
    private fun skeleton(
        hipY: Float, hipTw: Float, wob: Float, lean: Float, chestTw: Float, breath: Float,
        aR: Float, kR: Float, fR: Float, aL: Float, kL: Float, fL: Float,
        c0: Float, c1: Float, c2: Float, kF: Float, kB: Float,
        headTurn: Float, headTilt: Float, b0: Float, b1: Float, h0: Float, h1: Float,
        rsh: Float, rshZ: Float, rel: Float, rgr: Float, rtw: Float, lsh: Float, lshZ: Float, lel: Float, lgr: Float, ltw: Float
    ) {
        leg(1f, aL, kL, hipY, fL)
        leg(-1f, aR, kR, hipY, fR)
        push()
        translate(0f, hipY, 0f)
        rotY(hipTw * 0.5f + wob)
        rotX(lean)
        push()
        torso(chestTw, c0, c1, c2, kF, kB, breath)
        push()
        rotY(chestTw)
        translate(0f, breath, 0f)
        head(headTurn, headTilt, b0, b1, h0, h1)
        rightArm(rsh, rshZ, rel, rgr, rtw)
        leftArm(lsh, lshZ, lel, lgr, ltw)
        pop()
        pop()
        pop()
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

        // fase do golpe: 0..1 (negativo = sem ataque)
        val t = if (attack > 0.001f) 1f - attack else -1f
        val attacking = t >= 0f
        if (attack > prevAtk + 0.3f) {
            // golpe novo: começa a partir da pose atual
            System.arraycopy(lastPose, 0, kS, 0, NK)
            curSwing = swing.coerceIn(0, 2)
            released = false
            trN = 0
            sv[2] += 2f; sv[3] += 3f; sv[4] += 4f
            sv[5] += 2f; sv[6] += 3f; sv[7] += 3f; sv[8] += 4f
        }
        prevAtk = attack
        if (!attacking) released = false
        val sw = curSwing
        val tW = TW[sw]
        val tM = TM[sw]
        val tE = TE[sw]

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
            // aterrissagem: capa sobe, corpo amortece
            sv[2] -= 3f; sv[3] -= 4f; sv[4] -= 5f; sv[0] += 2.2f; sv[9] -= 3f; sv[10] -= 4f
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
        // reação ao tomar dano: corpo recua, capa e cabelo chicoteiam
        val fl = V.flash
        if (!first && fl > prevFlash + 0.25f) {
            hitKick = 1f
            sv[0] -= 6f; sv[2] += 4f; sv[3] += 5f; sv[4] += 6f; sv[7] += 5f; sv[8] += 6f; sv[11] += 6f
        }
        prevFlash = fl
        hitKick = max(0f, hitKick - dt * 4.5f)
        landDip = max(0f, landDip - dt * 5.5f)
        // piscar de olhos
        val bp = clock % 3.9f
        eyeOpen = if (bp < 0.14f) max(0.12f, abs(bp - 0.07f) / 0.07f) else 1f
        val vzN = (airVz / 340f).coerceIn(-1.2f, 1f)
        val tuck = max(rollW, airW * (0.30f + 0.35f * max(0f, vzN) - 0.2f * max(0f, -vzN)))
        val still = 1f - max(rollW, airW)
        val mvs = still * (if (attacking) blend * 0.35f else blend)
        furyW += ((if (attacking) 1f else 0f) - furyW) * min(1f, dt * (if (attacking) 14f else 3f))

        // ── poses de descanso: machados em guarda, balançam com o passo ──
        val armSw = sin(phase) * mvs
        kI[0] = -0.55f + armSw * 0.35f + sin(clock * 1.3f) * 0.03f * (1f - mvs)
        kI[1] = -0.12f
        kI[2] = -0.8f
        kI[3] = 0.9f + armSw * 0.2f
        kI[4] = -0.7f
        kI[5] = -0.55f - armSw * 0.35f + sin(clock * 1.1f + 1f) * 0.03f * (1f - mvs)
        kI[6] = 0.12f
        kI[7] = -0.8f
        kI[8] = 0.9f - armSw * 0.2f
        kI[9] = 0.7f
        kI[10] = 0f
        kI[11] = 0f
        kI[12] = 0.05f
        kI[13] = 0f
        var lunge = 0f
        if (attacking) {
            if (t < tW) {
                mixK(kS, KW[sw], ease(t / tW))
            } else if (t < tM) {
                mixK(KW[sw], KM[sw], ease((t - tW) / (tM - tW)))
            } else if (t < tE) {
                mixK(KM[sw], KE[sw], ease((t - tM) / (tE - tM)))
            } else {
                mixK(KE[sw], kI, ease((t - tE) / (1f - tE)))
            }
            val la = tW - 0.04f
            if (t > la && t < tE) {
                lunge = -sin(((t - la) / (tE - la)) * PI.toFloat()) * 1.5f
            }
        } else {
            for (i in 0 until NK) kO[i] = kI[i]
        }
        var rsh = kO[0]
        var rshZ = kO[1]
        var rel = kO[2]
        var rth = kO[3]
        val rtw = kO[4]
        var lsh = kO[5]
        var lshZ = kO[6]
        var lel = kO[7]
        var lth = kO[8]
        val ltw = kO[9]
        val tw = kO[10]
        var crouch = kO[11]
        var leanAtk = kO[12]
        val hipTw = kO[13]
        for (i in 0 until NK) lastPose[i] = kO[i]
        if (rollW > 0.001f) {
            rsh = lerp(rsh, -1.1f, rollW)
            rshZ = lerp(rshZ, 0.25f, rollW)
            rel = lerp(rel, -1.7f, rollW)
            rth = lerp(rth, 1.9f, rollW)
            lsh = lerp(lsh, -1.1f, rollW)
            lshZ = lerp(lshZ, 0.25f, rollW)
            lel = lerp(lel, -1.7f, rollW)
            lth = lerp(lth, 1.9f, rollW)
        }
        if (airW > 0.001f && !attacking) {
            rsh = lerp(rsh, -2.1f - 0.25f * vzN, airW)
            rshZ = lerp(rshZ, 0.45f, airW)
            rel = lerp(rel, -0.7f, airW)
            rth = lerp(rth, 0.2f, airW)
            lsh = lerp(lsh, -2.1f - 0.25f * vzN, airW)
            lshZ = lerp(lshZ, 0.45f, airW)
            lel = lerp(lel, -0.7f, airW)
            lth = lerp(lth, 0.2f, airW)
        }

        // ── salto carregado, pouso destruidor e impulso ──
        val chOn = leapCharge >= 0f && !rolling
        chW += ((if (chOn) 1f else 0f) - chW) * min(1f, dt * (if (chOn) 10f else 16f))
        val lpOn = leaping && !rolling
        leapW += ((if (lpOn) 1f else 0f) - leapW) * min(1f, dt * (if (lpOn) 18f else 12f))
        if (slamReq) {
            slamReq = false
            slamFire = true
        }
        val cp = max(0f, leapCharge)
        if (chW > 0.001f) {
            // agachado, tremendo de força (machados ficam na pose normal)
            val tr = sin(clock * 55f) * 0.05f * cp
            crouch = lerp(crouch, 0.4f + 0.65f * cp, chW)
            leanAtk = lerp(leanAtk, 0.3f + tr, chW)
        }
        if (leapW > 0.001f) {
            // subindo: machados erguidos; descendo: machados apontam para o chão
            val fall = (-vzN * 2.2f).coerceIn(0f, 1f)
            val ash = lerp(-2.9f, -1.7f, fall)
            val ael = lerp(-1.1f, -0.5f, fall)
            val ath = lerp(-0.1f, 1.5f, fall)
            rsh = lerp(rsh, ash, leapW); rshZ = lerp(rshZ, 0.25f, leapW); rel = lerp(rel, ael, leapW); rth = lerp(rth, ath, leapW)
            lsh = lerp(lsh, ash, leapW); lshZ = lerp(lshZ, -0.25f, leapW); lel = lerp(lel, ael, leapW); lth = lerp(lth, ath, leapW)
            leanAtk = lerp(leanAtk, -0.1f + 0.45f * fall, leapW)
        }

        // ── molas (capa, barba, cabelo, saias, cabeça, inclinação) ──
        val bankT = (-yawVel * 0.03f).coerceIn(-0.22f, 0.22f) * spN
        spring(0, 0.10f * spN, 90f, 12f, sdt)
        spring(1, bankT, 80f, 9f, sdt)
        val capeBase = 0.12f + 0.30f * spN + airW * (0.35f - 0.3f * vzN) + 0.09f * sin(clock * 3f + phase * 2f) * (0.4f + spN)
        spring(2, capeBase, 36f, 5.5f, sdt)
        spring(3, capeBase * 0.8f + 0.08f, 27f, 4.5f, sdt)
        spring(4, capeBase * 0.7f + 0.10f, 19f, 3.8f, sdt)
        spring(5, 0.10f * spN + 0.2f * airW, 55f, 6f, sdt)
        spring(6, 0.18f * spN + 0.25f * airW, 40f, 4.5f, sdt)
        spring(7, 0.10f + 0.3f * spN + 0.25f * airW + 0.2f * furyW, 30f, 4f, sdt)
        spring(8, 0.12f + 0.4f * spN + 0.3f * airW + 0.25f * furyW, 26f, 3.8f, sdt)
        spring(9, 0.03f - 0.2f * mvs * abs(sin(phase)) - 0.1f * airW, 60f, 7f, sdt)
        spring(10, 0.06f + 0.5f * spN + 0.5f * airW, 45f, 5.5f, sdt)
        spring(11, (yawVel * 0.03f).coerceIn(-0.35f, 0.35f), 70f, 9f, sdt)

        val bob = (0.5f + 0.5f * cos(phase * 2f)) * 1.1f * mvs + sin(clock * 2.2f) * 0.3f * (1f - mvs)
        val breath = sin(clock * 2.2f) * 0.3f * (1f - mvs * 0.6f) + furyW * sin(clock * 16f) * 0.12f
        val sway = sin(phase) * 0.8f * mvs + sin(clock * 0.9f) * 0.3f * (1f - mvs)
        crouch = max(crouch, max(rollW * 0.9f, landDip * 0.9f))
        val lean = sp[0] + leanAtk * 0.5f - hitKick * 0.3f + rollW * 0.25f + landDip * 0.2f
        val hipY = 12.6f + bob - crouch * 3.2f - hitKick * 0.9f
        val wob = sin(phase) * 0.12f * mvs
        val chestTw = tw - hipTw * 0.5f - wob - sin(phase) * 0.18f * mvs

        // ── cores que pulsam com a fúria ──
        val fq = (0.45f + 0.25f * sin(clock * 3.4f) + furyW * 0.6f).coerceIn(0f, 1f)
        furyC = rgb(255, (95 + 80 * fq).toInt(), (24 + 30 * fq).toInt())
        furyL = rgb(255, (190 + 55 * fq).toInt(), (120 + 90 * fq).toInt())
        eyeC = rgb(255, (236 - 70 * furyW).toInt(), (200 - 110 * furyW).toInt())

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

        // pernas: passada larga; postura firme (pé de trás firme) ao golpear
        val lk = if (attacking) (crouch / 0.5f).coerceIn(0f, 1f) else 0f
        var aR = 0f
        var kR = 0f
        var fR = 0f
        var aL = 0f
        var kL = 0f
        var fL = 0f
        for (k in 0..1) {
            val sd = if (k == 0) 1f else -1f
            val pp = if (sd > 0f) phase else phase + PI.toFloat()
            var a = sin(pp) * 0.8f * mvs
            var kn = max(0f, -cos(pp)) * 1.1f * mvs + 0.08f + crouch * 0.9f
            val ft = max(0f, cos(pp)) * 0.38f * mvs + airW * (0.55f * max(0f, vzN) - 0.25f * max(0f, -vzN))
            if (sd > 0f) {
                a -= 0.35f * lk
            } else {
                a += 0.3f * lk
                kn += 0.3f * lk
            }
            a = a * (1f - airW) - tuck + sd * 0.3f * airW
            kn = (kn + tuck * 1.5f + (if (sd > 0f) 0.5f else -0.1f) * airW).coerceAtLeast(0f)
            if (sd > 0f) {
                aL = a; kL = kn; fL = ft
            } else {
                aR = a; kR = kn; fR = ft
            }
        }

        if (chW > 0.001f) {
            aL = lerp(aL, -0.3f, chW); aR = lerp(aR, 0.25f, chW)
        }
        val flare = 0.04f + 0.10f * spN + 0.3f * airW
        skeleton(
            hipY, hipTw, wob, lean, chestTw, breath,
            aR, kR, fR, aL, kL, fL,
            sp[2], sp[3], sp[4], sp[9].coerceIn(-0.25f, 0.25f), sp[10] + flare * 0.2f,
            -chestTw * 0.7f + sp[11], -lean * 0.6f, min(sp[5], 0.25f), min(sp[6], 0.3f), sp[7], sp[8],
            rsh, rshZ, rel, rth - rsh - rel, rtw, lsh, lshZ, lel, lth - lsh - lel, ltw
        )

        // ── choque no chão da batida dupla ──
        if (attacking && sw == 2 && !released && t >= tM) {
            shockT = 0f
            shockSw = 2
            shockMax = 26f
            shockOff = 15f
        }
        if (slamFire) {
            // pouso do salto: só poeira
            slamFire = false
            for (i in 0..9) {
                val a = i * 0.628f
                emitDust(fx + cos(a) * 4f * rs, fy + sin(a) * 2f * rs, cos(a) * rs * 34f, sin(a) * rs * 12f - rs * 3f, rs * 2.6f)
            }
        }
        if (shockT >= 0f) {
            shockT += dt
            if (shockT > 0.38f) {
                shockT = -1f
            } else {
                val u = shockT / 0.38f
                val rr = (4f + shockMax * (1f - (1f - u) * (1f - u))) * rs
                val cx0 = fx + sin(yaw) * shockOff * rs
                val cy0 = fy + cos(yaw) * shockOff * rs * SINP
                val ra = 1f - u
                p.style = Paint.Style.STROKE
                p.strokeWidth = max(2f, rs * 2.4f * ra)
                p.color = (((ra * 0.8f * 255f).toInt()) shl 24) or 0x00FFB066
                c.drawOval(cx0 - rr, cy0 - rr * SINP, cx0 + rr, cy0 + rr * SINP, p)
                p.strokeWidth = max(1.2f, rs * 1.1f)
                p.color = (((ra * 0.6f * 255f).toInt()) shl 24) or 0x00FFFFFF
                c.drawOval(cx0 - rr * 0.8f, cy0 - rr * 0.8f * SINP, cx0 + rr * 0.8f, cy0 + rr * 0.8f * SINP, p)
                p.style = Paint.Style.FILL
            }
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

        // ── brasas da fúria subindo dos machados ──
        if (!rolling) {
            if (furyW > 0.2f || rnd.nextFloat() < dt * 2.5f) {
                val ai = if (sw == 1) 2 else 0
                if (rnd.nextFloat() < dt * (4f + 30f * furyW)) {
                    emitSpark(
                        ptX[ai], ptY[ai],
                        (rnd.nextFloat() - 0.5f) * rs * 8f, -rs * (3f + rnd.nextFloat() * 5f),
                        0.35f + rnd.nextFloat() * 0.3f, -rs * 4f,
                        if (rnd.nextFloat() > 0.5f) 0xFFFF8A33.toInt() else 0xFFFFD08A.toInt(), 0.6f
                    )
                }
            }
        }

        // ── impacto: faíscas saem dos fios no instante do golpe ──
        if (attacking && !released && t >= tM) {
            released = true
            val useR = sw != 1
            val useL = sw != 0
            for (i in 0..13) {
                val ai = if ((i % 2 == 0 && useR) || !useL) 0 else 2
                val ang = rnd.nextFloat() * TWO_PI
                val sp0 = (14f + rnd.nextFloat() * 34f) * rs
                emitSpark(
                    (ptX[ai] + ptX[ai + 1]) * 0.5f, (ptY[ai] + ptY[ai + 1]) * 0.5f,
                    cos(ang) * sp0, sin(ang) * sp0 - rs * 6f,
                    0.26f + rnd.nextFloat() * 0.2f, rs * 70f,
                    if (i % 3 == 0) 0xFFFFFFFF.toInt() else if (i % 3 == 1) 0xFFFFB066.toInt() else 0xFFFF6A1F.toInt(), 1.1f
                )
            }
            if (sw == 2) {
                for (i in 0..6) {
                    val a = i * 0.9f
                    emitDust(
                        fx + sin(yaw) * 15f * rs + cos(a) * 3f * rs, fy + cos(yaw) * 15f * rs * SINP + sin(a) * 1.5f * rs,
                        cos(a) * rs * 30f, sin(a) * rs * 10f - rs * 3f, rs * 2.2f
                    )
                }
            }
            sv[0] -= 2f; sv[2] += 3f; sv[3] += 4f; sv[4] += 5f; sv[7] += 4f; sv[8] += 5f
        }

        // ── rastro do fio dos machados (faixa entre o topo e a base da lâmina) ──
        if (!attacking) {
            trN = 0
        } else {
            if (t >= tW - 0.08f && t <= tE) {
                if (trN == TRAIL_MAX) {
                    for (a in 0..3) {
                        System.arraycopy(trX[a], 1, trX[a], 0, TRAIL_MAX - 1)
                        System.arraycopy(trY[a], 1, trY[a], 0, TRAIL_MAX - 1)
                    }
                    trN = TRAIL_MAX - 1
                }
                for (a in 0..3) {
                    trX[a][trN] = ptX[a]
                    trY[a][trN] = ptY[a]
                }
                trN++
            }
            val fade = min(1f, attack * 4f)
            for (ax in 0..1) {
                if (ax == 0 && sw == 1) continue
                if (ax == 1 && sw == 0) continue
                val b = ax * 2
                for (i in 1 until trN) {
                    val a = i.toFloat() / trN.toFloat()
                    path.reset()
                    path.moveTo(trX[b][i - 1], trY[b][i - 1])
                    path.lineTo(trX[b][i], trY[b][i])
                    path.lineTo(trX[b + 1][i], trY[b + 1][i])
                    path.lineTo(trX[b + 1][i - 1], trY[b + 1][i - 1])
                    path.close()
                    p.style = Paint.Style.FILL
                    p.color = (((a * a * 0.40f * fade * 255f).toInt()) shl 24) or 0x00FFA24D
                    c.drawPath(path, p)
                    p.style = Paint.Style.STROKE
                    p.strokeWidth = max(1f, rs * 0.9f)
                    p.color = (((a * a * 0.85f * fade * 255f).toInt()) shl 24) or 0x00FFF1D6
                    c.drawLine(trX[b][i - 1], trY[b][i - 1], trX[b][i], trY[b][i], p)
                }
            }
            p.style = Paint.Style.FILL
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
