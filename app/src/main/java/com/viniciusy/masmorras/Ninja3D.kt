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
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Ninja das sombras (estilo Zed): mesmo motor de cubos dos outros heróis, com máscara de olhos vermelhos,
 * armadura escura, cachecol e rabo de cavalo em molas e duas lâminas curvas que deixam rastro.
 */
object Ninja3D {
    private val V = VoxelArt

    private const val MAX = 1100
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
        val base = if (ghost) ghostCol(color, emissive) else V.tint(color)
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

        // o canvas de hardware só desenha no fim do quadro: cada silhueta precisa do seu próprio bitmap e paint,
        // senão a sombra e o herói acabam mostrando a mesma imagem (a "foto" da sombra em cima do boneco)
        val b: Bitmap
        val pt: Paint
        if (ghost) {
            val gi = gNext
            gNext = (gNext + 1) % G_POOL
            val cur = gBmp[gi]
            if (cur != null && cur.width >= rw && cur.height >= rh) {
                b = cur
            } else {
                b = Bitmap.createBitmap(((max(rw, cur?.width ?: 0) + 63) / 64) * 64, ((max(rh, cur?.height ?: 0) + 63) / 64) * 64, Bitmap.Config.ARGB_8888)
                gBmp[gi] = b
            }
            pt = gPaint[gi]
        } else {
            b = ensureBmp(rw, rh)
            pt = bmpPaint
        }
        b.setPixels(cb, 0, rw, 0, 0, rw, rh)
        srcR.set(0, 0, rw, rh)
        dstR.set(bx0, by0, bx0 + rw / ss, by0 + rh / ss)
        pt.alpha = (alphaV.coerceIn(0f, 1f) * 255f).toInt()
        c.drawBitmap(b, srcR, dstR, pt)
    }

    private const val G_POOL = 6
    private val gBmp = arrayOfNulls<Bitmap>(G_POOL)
    private val gPaint = Array(G_POOL) { Paint(bmpPaint) }
    private var gNext = 0

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

    private fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t

    private fun ease(t: Float): Float {
        val x = t.coerceIn(0f, 1f)
        return x * x * (3f - 2f * x)
    }

    // ───────────── partes do corpo ─────────────

    private fun rgb(r: Int, g: Int, b: Int): Int = (0xFF shl 24) or (r shl 16) or (g shl 8) or b

    // ───────────── paleta do ninja ─────────────
    private val SUIT = 0xFF1B1A24.toInt()
    private val SUIT_L = 0xFF2C2A3A.toInt()
    private val SUIT_D = 0xFF0F0E15.toInt()
    private val PLATE = 0xFF3C3B4C.toInt()
    private val PLATE_L = 0xFF5C5A76.toInt()
    private val PLATE_D = 0xFF262538.toInt()
    private val CRIM = 0xFFA81E2C.toInt()
    private val CRIM_L = 0xFFD8354A.toInt()
    private val CRIM_D = 0xFF6A1020.toInt()
    private val GLOW = 0xFFFF3B4A.toInt()
    private val GLOW_L = 0xFFFFB4B4.toInt()
    private val STEEL = 0xFFB8C0CC.toInt()
    private val STEEL_D = 0xFF6E7488.toInt()
    private val SKIN: Int get() = HeroLook.skin(0xFFE9B98A.toInt())
    private val SKIN_D: Int get() = HeroLook.skinD(0xFFBE8A62.toInt())
    private val HAIR: Int get() = HeroLook.hair(0xFF1C1626.toInt())
    private val HAIR_D: Int get() = HeroLook.hairD(0xFF0F0A18.toInt())
    private val HAIR_L: Int get() = HeroLook.hairL(0xFF3A2C52.toInt())

    private fun ghostCol(col: Int, em: Boolean): Int {
        if (em) return col
        val r = (col shr 16) and 255
        val g = (col shr 8) and 255
        val b = col and 255
        val lum = (r * 3 + g * 5 + b * 2) / 10
        val nr = (30 + lum * 0.30f).toInt()
        val ng = (14 + lum * 0.14f).toInt()
        val nb = (56 + lum * 0.55f).toInt()
        return (0xFF shl 24) or (min(255, nr) shl 16) or (min(255, ng) shl 8) or min(255, nb)
    }

    // ───────────── estado externo (preenchido pelo Player antes de desenhar) ─────────────
    var rollP = -1f
    var inAir = false
    var airVz = 0f
    var airH = 0f
    var swing = 0
    var dashP = -1f             // 0..1 durante o pulo-dash da sombra; <0 = sem dash
    var dashUp = false          // dash parado = sobe reto
    var skillK = 0              // 1 Q, 2 W, 3 E, 4 R
    var skillP = -1f            // 0..1 durante a skill
    var alphaV = 1f

    // ───────────── estado de animação ─────────────
    private var ghost = false
    private var yaw = 0f
    private var airW = 0f
    private var rollW = 0f
    private var dashW = 0f
    private var blend = 0f
    private var clock = 0f
    private var lastNs = 0L
    private var tipRX = 0f
    private var tipRY = 0f
    private var tipLX = 0f
    private var tipLY = 0f
    private var eyeC = GLOW
    private var eyeOpen = 1f
    private var bladeG = 0f
    private var prevAtk = 0f
    private var prevSkillOn = false
    private var prevSkillK = 0
    private var released = false
    private var prevPhase = 0f
    private var spd = 0f
    private var wasAir = false
    private var landDip = 0f
    private var airTimer = 0f
    private var prevFlash = 0f
    private var hitKick = 0f
    private var prevSin = 0f
    private var dustT = 0f
    private var moteT = 0f
    private var wasDash = false

    // molas: 0 inclinação, 1 banking, 2-4 cachecol, 5-6 rabo de cavalo, 7 saia da frente, 8 cabeça, 9 faixa da testa, 10 saia de trás
    private val sp = FloatArray(12)
    private val sv = FloatArray(12)

    // rastro das duas lâminas
    private const val TRAIL_MAX = 12
    private val trRX = FloatArray(24)
    private val trRY = FloatArray(24)
    private val trLX = FloatArray(24)
    private val trLY = FloatArray(24)
    private var trN = 0

    // poeira
    private const val ND = 16
    private val dX = FloatArray(ND)
    private val dY = FloatArray(ND)
    private val dVx = FloatArray(ND)
    private val dVy = FloatArray(ND)
    private val dL = FloatArray(ND)
    private val dM = FloatArray(ND)
    private val dS = FloatArray(ND)
    private var dNext = 0

    // faíscas e brasas sombrias
    private const val NSP = 56
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

    // ───────────── poses (11 valores) ─────────────
    // 0 ombro D, 1 ombro D lateral, 2 cotovelo D, 3 punho D, 4-7 o mesmo no braço E, 8 giro do peito, 9 inclinação, 10 agachar
    private const val NK = 11
    private val kS = FloatArray(NK)
    private val kI = FloatArray(NK)
    private val kO = FloatArray(NK)
    private val kT = FloatArray(NK)
    private val lastPose = FloatArray(NK)

    private fun mirror(a: FloatArray): FloatArray = floatArrayOf(a[4], -a[5], a[6], a[7], a[0], -a[1], a[2], a[3], -a[8], a[9], a[10])

    private val S0 = arrayOf(
        floatArrayOf(-2.5f, -0.6f, -0.5f, -0.4f, -1.1f, 0.35f, -1.4f, -0.9f, 0.55f, -0.12f, 0.1f),
        floatArrayOf(-1.15f, 0.35f, -0.15f, -1.25f, -0.9f, 0.45f, -1.5f, -0.9f, -0.6f, 0.32f, 0.35f),
        floatArrayOf(-0.5f, 0.55f, -0.5f, -0.9f, -0.8f, 0.3f, -1.3f, -0.9f, -0.3f, 0.15f, 0.2f)
    )
    private val S2 = arrayOf(
        floatArrayOf(-1.3f, -1.4f, -0.4f, -0.5f, -1.3f, 1.4f, -0.4f, -0.5f, 0f, -0.05f, 0.45f),
        floatArrayOf(-1.45f, 0.5f, -0.2f, -1.2f, -1.45f, -0.5f, -0.2f, -1.2f, 0f, 0.3f, 0.3f),
        floatArrayOf(-0.7f, 0.4f, -0.6f, -0.9f, -0.7f, -0.4f, -0.6f, -0.9f, 0f, 0.1f, 0.1f)
    )
    private val SQ = arrayOf(
        floatArrayOf(-2.4f, -0.5f, -0.7f, -0.3f, -1.0f, 0.4f, -1.2f, -0.8f, 0.35f, -0.05f, 0.08f),
        floatArrayOf(-1.5f, -0.1f, -0.1f, -1.4f, -0.9f, 0.3f, -1.3f, -0.8f, -0.3f, 0.08f, 0.08f),
        floatArrayOf(-0.9f, 0.1f, -0.6f, -1.0f, -0.8f, 0.25f, -1.2f, -0.9f, -0.1f, 0.05f, 0.06f)
    )
    private val SW = arrayOf(
        floatArrayOf(-1.0f, -0.2f, -1.2f, -0.9f, -1.0f, 0.2f, -1.2f, -0.9f, 0.15f, 0f, 0.06f),
        floatArrayOf(-1.5f, -0.1f, -0.1f, -0.2f, -0.5f, 0.4f, -0.6f, -0.5f, 0.25f, 0.06f, 0.06f),
        floatArrayOf(-0.9f, 0.1f, -0.6f, -1.0f, -0.7f, 0.2f, -0.7f, -0.9f, 0.1f, 0.04f, 0.05f)
    )
    private val SE = arrayOf(
        floatArrayOf(-1.5f, -1.5f, -0.2f, -0.5f, -1.5f, 1.5f, -0.2f, -0.5f, 0f, -0.05f, 0.5f),
        floatArrayOf(-1.5f, -1.5f, -0.2f, -0.5f, -1.5f, 1.5f, -0.2f, -0.5f, 0f, 0.15f, 0.4f),
        floatArrayOf(-0.8f, -0.4f, -0.5f, -0.9f, -0.8f, 0.4f, -0.5f, -0.9f, 0f, 0.1f, 0.15f)
    )
    private val SR = arrayOf(S0[1], S0[1], S0[2])

    // 0..2 golpes do combo, 3 Q, 4 W, 5 E, 6 R
    private val KEYS: Array<Array<FloatArray>> = arrayOf(
        S0,
        arrayOf(mirror(S0[0]), mirror(S0[1]), mirror(S0[2])),
        S2, SQ, SW, SE, SR
    )
    private val TW = floatArrayOf(0.22f, 0.22f, 0.26f, 0.18f, 0.15f, 0.20f, 0.12f)
    private val TI = floatArrayOf(0.34f, 0.34f, 0.42f, 0.42f, 0.38f, 0.40f, 0.30f)
    private val TE = floatArrayOf(0.62f, 0.62f, 0.68f, 0.70f, 0.70f, 0.75f, 0.70f)

    /** Fração da animação do golpe em que a lâmina acerta (o Player usa para sincronizar o dano). */
    fun impactAt(sw: Int): Float = TI[sw.coerceIn(0, 2)]

    // duração (s) e ponto de saída (fração) de cada skill — o Player e o GameView leem daqui
    const val Q_DUR = 0.34f
    const val Q_REL = 0.42f
    const val W_DUR = 0.30f
    const val W_REL = 0.38f
    const val E_DUR = 0.44f
    const val E_REL = 0.40f
    const val R_DUR = 0.42f
    const val R_REL = 0.10f

    // parâmetros do corpo lidos por assemble()
    private val legA = FloatArray(2)
    private val legK = FloatArray(2)
    private val legF = FloatArray(2)
    private var bHipY = 12.6f
    private var bLean = 0f
    private var bChestTw = 0f
    private var bHipTw = 0f
    private var bBreath = 0f

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

    private fun projectTo(x: Float, y: Float, z: Float, out: Int) {
        val m = stack[top]
        val wx = m[0] * x + m[1] * y + m[2] * z + m[9]
        val wy = m[3] * x + m[4] * y + m[5] * z + m[10]
        val wz = m[6] * x + m[7] * y + m[8] * z + m[11]
        val px = ox + wx * rs
        val py = oy + (wz * SINP - wy * COSP) * rs
        if (out == 0) {
            tipRX = px
            tipRY = py
        } else {
            tipLX = px
            tipLY = py
        }
    }

    private val hg = object : HeroGfx {
        override fun cube(cx: Float, cy: Float, cz: Float, hx: Float, hy: Float, hz: Float, color: Int, emissive: Boolean) = this@Ninja3D.cube(cx, cy, cz, hx, hy, hz, color, emissive)
        override fun push() = this@Ninja3D.push()
        override fun pop() = this@Ninja3D.pop()
        override fun translate(x: Float, y: Float, z: Float) = this@Ninja3D.translate(x, y, z)
        override fun rotX(a: Float) = this@Ninja3D.rotX(a)
        override fun rotY(a: Float) = this@Ninja3D.rotY(a)
        override fun rotZ(a: Float) = this@Ninja3D.rotZ(a)
    }

    // ───────────── partes do corpo ─────────────

    private fun leg(side: Float, a: Float, knee: Float, hipY: Float, foot: Float) {
        push()
            translate(side * 3f, hipY, 0f)
            rotX(a)
            cube(0f, -2.7f, 0f, 2.2f, 2.9f, 2.3f, SUIT)
            cube(0f, -4.9f, 0f, 2.35f, 0.5f, 2.45f, CRIM_D)
            translate(0f, -5.6f, 0f)
            rotX(knee)
            cube(0f, -2.7f, 0f, 2.0f, 2.8f, 2.05f, SUIT_L)
            cube(0f, -0.2f, 2.1f, 1.3f, 0.9f, 0.25f, PLATE)
            cube(0f, -4.6f, 0f, 2.15f, 0.35f, 2.2f, CRIM)
            translate(0f, -5.4f, 0f)
            rotX(foot)
            cube(0f, 0.2f, 1.2f, 2.2f, 1.3f, 3.1f, SUIT_D)
            cube(0f, -1.1f, 1.3f, 2.35f, 0.3f, 3.3f, PLATE_D)
            cube(0f, 0.8f, 4.3f, 1.5f, 0.7f, 0.9f, PLATE)
            cube(0f, 0.9f, -2.1f, 1.7f, 0.9f, 0.5f, SUIT_D)
        pop()
    }

    /** Lâmina curva presa na mão (ponta para -Y). */
    private fun blade(out: Int) {
        val g = bladeG
        val edge = if (g > 0.5f) GLOW_L else GLOW
        cube(0f, 0f, 0f, 0.55f, 1.5f, 0.55f, SUIT_D)
        cube(0f, -1.6f, 0f, 1.7f, 0.3f, 0.85f, PLATE)
        cube(0f, -4.6f, 0f, 0.32f, 2.9f, 0.95f, STEEL_D)
        cube(0f, -9.2f, 0.3f, 0.3f, 1.9f, 0.8f, STEEL)
        cube(0f, -12.0f, 0.75f, 0.26f, 0.95f, 0.5f, STEEL)
        cube(0f, -4.6f, 0.98f, 0.12f, 2.7f, 0.07f, edge, true)
        cube(0f, -9.2f, 1.12f, 0.12f, 1.8f, 0.07f, edge, true)
        cube(0f, -12.0f, 1.28f, 0.12f, 0.8f, 0.07f, GLOW_L, true)
        cube(0f, -4.6f, -0.9f, 0.14f, 2.7f, 0.1f, CRIM_D)
        projectTo(0f, -12.6f, 0.9f, out)
    }

    private fun arm(side: Float, sh: Float, shZ: Float, el: Float, wr: Float) {
        push()
        translate(side * 7.0f, 12.8f, 0f)
        cube(side * 0.5f, 1.0f, 0f, 3.1f, 1.0f, 3.2f, PLATE)
        cube(side * 0.5f, 2.0f, 0f, 3.2f, 0.25f, 3.3f, CRIM)
        cube(side * 2.6f, -0.2f, 0f, 0.5f, 1.4f, 3.2f, PLATE_D)
        rotZ(shZ)
        rotX(sh)
        cube(0f, -2.5f, 0f, 1.75f, 2.7f, 1.75f, SKIN)
        cube(0f, -1.2f, 0f, 1.95f, 0.5f, 1.95f, SUIT_L)
        cube(0f, -5.2f, 0f, 2.0f, 0.35f, 2.0f, CRIM)
        translate(0f, -5.3f, 0f)
        rotX(el)
        cube(0f, -3.0f, 0f, 2.2f, 3.0f, 2.2f, SUIT)
        cube(0f, -5.9f, 0f, 2.4f, 0.4f, 2.4f, PLATE)
        cube(0f, -3.2f, 2.25f, 1.0f, 2.2f, 0.15f, PLATE_L)
        cube(0f, -1.2f, 0f, 2.3f, 0.35f, 2.3f, CRIM_D)
        translate(0f, -6.8f, 0f)
        cube(0f, 0f, 0f, 1.35f, 1.2f, 1.35f, SKIN)
        rotX(wr)
        blade(if (side < 0f) 0 else 1)
        pop()
    }

    private fun head(turn: Float, tilt: Float, h0: Float, h1: Float, band: Float) {
        push()
            translate(0f, 14.2f, 0f)
            rotY(turn)
            rotX(tilt)
            cube(0f, 0.3f, 0f, 2.3f, 1f, 2.3f, SKIN_D)
            cube(0f, 1.3f, 0f, 3.1f, 0.9f, 3.1f, SUIT_L)
            // cabeça coberta
            cube(0f, 5f, 0f, 4.5f, 4.5f, 4.3f, SUIT)
            cube(0f, 8.6f, -0.3f, 4.2f, 0.9f, 3.9f, SUIT_L)
            // máscara metálica
            cube(0f, 4.0f, 4.35f, 4.2f, 3.3f, 0.22f, PLATE)
            cube(0f, 6.8f, 4.35f, 4.2f, 0.45f, 0.22f, PLATE_D)
            cube(0f, 5.5f, 4.5f, 3.9f, 0.95f, 0.12f, SUIT_D)
            cube(0f, 4.2f, 4.62f, 0.45f, 1.3f, 0.2f, PLATE_L)
            cube(0f, 2.2f, 4.58f, 1.9f, 0.2f, 0.1f, PLATE_D)
            cube(-0.9f, 1.9f, 4.6f, 0.12f, 0.7f, 0.08f, PLATE_D)
            cube(0f, 1.9f, 4.6f, 0.12f, 0.7f, 0.08f, PLATE_D)
            cube(0.9f, 1.9f, 4.6f, 0.12f, 0.7f, 0.08f, PLATE_D)
            cube(0f, 3.1f, 4.62f, 2.7f, 0.12f, 0.08f, PLATE_L)
            // olhos vermelhos brilhantes, em V
            val ey = 0.38f * eyeOpen
            for (k in 0..1) {
                val sd = if (k == 0) 1f else -1f
                push()
                    translate(sd * 2.0f, 5.5f, 4.68f)
                    rotZ(sd * 0.32f)
                    cube(0f, 0f, 0f, 1.45f, ey, 0.08f, HeroLook.eye(eyeC), true)
                    cube(sd * -0.3f, 0f, 0.05f, 0.6f, ey * 0.55f, 0.06f, GLOW_L, true)
                pop()
            }
            // faixa vermelha na testa
            cube(0f, 7.6f, 0f, 4.65f, 0.55f, 4.45f, CRIM)
            cube(0f, 7.6f, 4.5f, 1.3f, 0.5f, 0.12f, CRIM_L)
            cube(0f, 7.6f, 4.62f, 0.5f, 0.3f, 0.1f, GLOW, true)
            // cabelo espetado
            cube(0f, 9.4f, -0.8f, 3.9f, 1.1f, 3.7f, HAIR)
            for (i in -2..2) {
                val h = 1.0f + (2 - abs(i)) * 0.6f
                cube(i * 1.5f, 10.4f + h * 0.5f, -1.6f, 0.75f, h * 0.9f, 0.75f, if (i % 2 == 0) HAIR else HAIR_L)
            }
            cube(0f, 6.0f, -4.4f, 4.0f, 3.2f, 0.5f, HAIR)
            cube(0f, 4.2f, -4.45f, 3.0f, 1.2f, 0.45f, HAIR_D)
            // rabo de cavalo em molas
            push()
                translate(0f, 8.4f, -3.9f)
                rotX(-(1.15f + h0))
                cube(0f, 1.6f, 0f, 1.2f, 1.7f, 1.2f, HAIR)
                cube(0f, 0.2f, 0f, 1.45f, 0.35f, 1.45f, CRIM)
                translate(0f, 3.2f, 0f)
                rotX(0.7f + h1)
                cube(0f, 1.8f, 0f, 1.05f, 2f, 1.05f, HAIR_D)
                translate(0f, 3.8f, 0f)
                rotX(0.8f + h1 * 1.2f)
                cube(0f, 1.6f, 0f, 0.75f, 1.8f, 0.75f, HAIR)
            pop()
            // pontas da faixa
            push()
                translate(0f, 7.6f, -4.5f)
                rotX(0.25f + band)
                cube(-0.9f, -2.5f, -0.2f, 0.65f, 2.6f, 0.2f, CRIM)
                cube(0.9f, -3.2f, -0.2f, 0.65f, 3.3f, 0.2f, CRIM_D)
            pop()
            if (!ghost) HeroKit.cabeca(hg, Classe.NINJA)
        pop()
    }

    private fun torso(chestTw: Float, c0: Float, c1: Float, c2: Float, skF: Float, skB: Float, breath: Float) {
        if (!ghost) HeroKit.cauda(hg, Classe.NINJA)
        // cinto, fivela e bolsas
        cube(0f, 0.6f, 0f, 5.0f, 1.2f, 3.3f, SUIT_D)
        cube(0f, 0.7f, 3.35f, 1.5f, 1.0f, 0.4f, PLATE)
        cube(0f, 0.7f, 3.7f, 0.8f, 0.6f, 0.15f, GLOW, true)
        cube(-5.3f, 0.3f, 0.5f, 0.8f, 1.6f, 1.3f, PLATE_D)
        cube(5.3f, 0.3f, -0.5f, 0.8f, 1.6f, 1.3f, PLATE_D)
        // saia: painéis da frente e de trás
        cube(0f, -3.6f, 0f, 5.2f, 3.6f, 3.0f, SUIT_D)
        push()
            translate(0f, 0.8f, 3.4f)
            rotX(skF)
            cube(-1.9f, -3.6f, 0f, 1.9f, 3.6f, 0.4f, SUIT_L)
            cube(1.9f, -3.6f, 0f, 1.9f, 3.6f, 0.4f, SUIT)
            cube(0f, -7.4f, 0.05f, 3.9f, 0.3f, 0.45f, CRIM)
            cube(0f, -3.6f, 0.45f, 0.4f, 3.4f, 0.1f, CRIM_D)
        pop()
        push()
            translate(0f, 0.8f, -3.4f)
            rotX(skB)
            cube(0f, -4.2f, 0f, 4.8f, 4.2f, 0.4f, SUIT)
            cube(0f, -8.3f, -0.05f, 4.9f, 0.3f, 0.45f, CRIM)
            cube(0f, -4.2f, -0.45f, 0.4f, 4.0f, 0.1f, CRIM_D)
        pop()
        for (k in 0..1) {
            val sd = if (k == 0) 1f else -1f
            cube(sd * 5.6f, -1.8f, 0f, 0.5f, 2.2f, 2.6f, PLATE_D)
            cube(sd * 5.65f, -3.9f, 0f, 0.4f, 0.3f, 2.7f, CRIM)
        }
        // abdômen
        cube(0f, 3.8f, 0f, 4.8f, 2.2f, 3.2f, SUIT)
        cube(0f, 3.8f, 3.25f, 4.4f, 0.25f, 0.15f, PLATE_D)
        push()
            rotY(chestTw)
            translate(0f, breath, 0f)
            cube(0f, 9.6f, 0f, 5.4f, 4.8f, 3.4f, SUIT)
            cube(0f, 10.0f, 3.45f, 4.6f, 4.2f, 0.2f, PLATE)
            cube(0f, 10.0f, 3.62f, 3.8f, 3.4f, 0.1f, PLATE_D)
            cube(0f, 7.0f, 3.62f, 4.0f, 0.3f, 0.12f, PLATE_L)
            // faixa vermelha em diagonal e emblema
            push()
                translate(0f, 9.4f, 3.75f)
                rotZ(0.7f)
                cube(0f, 0f, 0f, 0.9f, 6.0f, 0.18f, CRIM)
                cube(0f, 0f, 0.15f, 0.3f, 6.0f, 0.06f, CRIM_L)
            pop()
            cube(0f, 11.2f, 3.8f, 1.2f, 1.2f, 0.12f, CRIM_D)
            cube(0f, 11.2f, 3.92f, 0.7f, 0.7f, 0.1f, GLOW, true)
            // gola alta
            cube(0f, 13.7f, 0f, 3.6f, 1.2f, 3.2f, SUIT_L)
            cube(0f, 14.4f, 0f, 3.9f, 0.4f, 3.4f, PLATE)
            // cachecol vermelho comprido em três segmentos
            push()
                translate(0f, 13.0f, -3.4f)
                rotX(c0)
                cube(0f, -2.8f, -0.3f, 2.4f, 3.0f, 0.35f, CRIM)
                cube(0f, 0.2f, 0.1f, 3.2f, 0.7f, 1.0f, CRIM_D)
                translate(0f, -5.8f, 0f)
                rotX(c1)
                cube(0f, -2.8f, -0.3f, 2.3f, 3.0f, 0.35f, CRIM_L)
                cube(0f, -2.8f, -0.55f, 0.35f, 2.6f, 0.08f, CRIM_D)
                translate(0f, -5.8f, 0f)
                rotX(c2)
                cube(0f, -2.2f, -0.3f, 2.1f, 2.4f, 0.35f, CRIM)
                cube(-1.1f, -5.0f, -0.3f, 0.9f, 0.8f, 0.3f, CRIM_D)
                cube(1.1f, -5.0f, -0.3f, 0.9f, 0.8f, 0.3f, CRIM_D)
            pop()
        pop()
    }

    /** Monta o corpo inteiro com os parâmetros de pose já preenchidos (draw e drawGhost usam o mesmo). */
    private fun assemble() {
        for (k in 0..1) {
            val sd = if (k == 0) 1f else -1f
            leg(sd, legA[k], legK[k], bHipY, legF[k])
        }
        push()
        translate(0f, bHipY, 0f)
        rotY(bHipTw * 0.5f)
        rotX(bLean)
        push()
        torso(bChestTw, sp[2], sp[3], sp[4], sp[7].coerceIn(-0.2f, 0.2f), sp[10], bBreath)
        push()
        rotY(bChestTw)
        translate(0f, bBreath, 0f)
        head(-bChestTw * 0.7f + sp[8], -bLean * 0.6f, sp[5], sp[6], sp[9])
        arm(-1f, kO[0], kO[1], kO[2], kO[3])
        arm(1f, kO[4], kO[5], kO[6], kO[7])
        pop()
        pop()
        pop()
    }

    private fun poseFrom(ki: Int, t: Float) {
        val keys = KEYS[ki]
        val tw = TW[ki]
        val ti = TI[ki]
        val te = TE[ki]
        if (t < tw) {
            mixK(kS, keys[0], ease(t / tw))
        } else if (t < ti) {
            mixK(keys[0], keys[1], ease((t - tw) / (ti - tw)))
        } else if (t < te) {
            mixK(keys[1], keys[2], ease((t - ti) / (te - ti)))
        } else {
            mixK(keys[2], kI, ease((t - te) / (1f - te)))
        }
    }

    // ───────────── sombra (clone fantasma da Sombra Viva) ─────────────

    /**
     * Desenha a silhueta da sombra: sempre parada (não imita os passos do herói), só copia as skills.
     * mode 1 = lançando shuriken (pose do Q), mode 2 = giro de lâminas (pose do E); prog = 0..1 da skill.
     */
    fun drawGhost(c: Canvas, p: Paint, fx: Float, fy: Float, s: Float, yawIn: Float, alpha: Float, mode: Int, prog: Float) {
        if (alpha <= 0.01f) return
        ghost = true
        val oldAlpha = alphaV
        alphaV = alpha
        val spSave = sp.copyOf()
        val kSSave = kS.copyOf()
        val kISave = kI.copyOf()
        // cachecol e rabo de cavalo calmos: a sombra não herda o balanço dos passos do herói
        val sw = sin(clock * 1.6f)
        sp[0] = 0f
        sp[1] = 0f
        sp[2] = 0.14f + 0.03f * sw
        sp[3] = 0.16f + 0.03f * sw
        sp[4] = 0.17f + 0.04f * sw
        sp[5] = 0.05f
        sp[6] = 0.08f
        sp[7] = 0.03f
        sp[8] = 0f
        sp[9] = 0.12f
        sp[10] = 0.06f
        rs = s * PX
        ox = fx
        oy = fy
        n = 0
        top = 0
        zMode = true
        lYc = cos(yawIn)
        lYs = sin(yawIn)
        identity()
        rotY(yawIn)
        for (i in 0 until NK) {
            kI[i] = KG_IDLE[i]
            kS[i] = KG_IDLE[i]
        }
        val pr = prog.coerceIn(0f, 1f)
        var lean = 0.06f
        var dip = 0.3f
        var bend = 0.16f
        if (mode == 1 || mode == 2) {
            // mesma pose e mesma linha do tempo da skill do herói
            poseFrom(if (mode == 1) 3 else 5, pr)
            lean = 0.08f
            dip = 0.2f
            bend = 0.16f
            if (mode == 2) {
                rotY(TWO_PI * 2f * ease(((pr - 0.12f) / 0.66f).coerceIn(0f, 1f)))
                dip = 1.2f
                bend = 0.66f
            }
        } else {
            for (i in 0 until NK) kO[i] = KG_IDLE[i]
            kO[0] += sin(clock * 1.3f) * 0.03f
            kO[4] += sin(clock * 1.1f + 1f) * 0.03f
        }
        bladeG = 1f
        bHipY = 12.6f - dip
        bLean = lean
        bChestTw = kO[8]
        bHipTw = 0f
        bBreath = sin(clock * 2.2f) * 0.26f
        for (k in 0..1) {
            legA[k] = if (k == 0) 0.12f else -0.12f
            legK[k] = bend
            legF[k] = 0f
        }
        assemble()
        flushZ(c)
        System.arraycopy(spSave, 0, sp, 0, sp.size)
        System.arraycopy(kSSave, 0, kS, 0, kS.size)
        System.arraycopy(kISave, 0, kI, 0, kI.size)
        alphaV = oldAlpha
        ghost = false
    }

    private val KG_IDLE = floatArrayOf(-0.55f, -0.15f, -0.9f, -0.9f, -0.55f, 0.15f, -0.9f, -0.9f, 0f, 0.05f, 0.05f)

    // ───────────── desenho principal ─────────────

    fun draw(
        c: Canvas, p: Paint, fx: Float, fy: Float, s: Float, phase: Float,
        moving: Boolean, faceX: Float, faceY: Float, attack: Float
    ) {
        ghost = false
        val now = System.nanoTime()
        val first = lastNs == 0L
        val dt = if (first) 0.016f else ((now - lastNs) / 1_000_000_000f).coerceIn(0.001f, 0.1f)
        lastNs = now
        clock += dt
        val sdt = min(dt, 0.04f)

        // direção suave
        var yawStep = 0f
        if (abs(faceX) + abs(faceY) > 0.01f) {
            val target = atan2(faceX, faceY)
            if (first) {
                yaw = target
            } else {
                var d = target - yaw
                while (d > PI.toFloat()) d -= TWO_PI
                while (d < -PI.toFloat()) d += TWO_PI
                yawStep = d * min(1f, dt * 18f)
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

        // gesto atual: golpe do combo ou skill
        val skillOn = skillK in 1..4 && skillP >= 0f
        val atkOn = !skillOn && attack > 0.001f
        val t = if (skillOn) skillP else if (atkOn) 1f - attack else -1f
        val acting = t >= 0f
        val ki = if (skillOn) 2 + skillK else swing.coerceIn(0, 2)
        val newGesture = (atkOn && attack > prevAtk + 0.3f) || (skillOn && (!prevSkillOn || skillK != prevSkillK))
        if (newGesture) {
            System.arraycopy(lastPose, 0, kS, 0, NK)
            released = false
            sv[2] += 2f
            sv[3] += 3f
            sv[4] += 4f
            sv[5] += 1.5f
            sv[9] += 3f
        }
        prevAtk = attack
        prevSkillOn = skillOn
        prevSkillK = skillK
        if (!acting) released = false

        val rolling = rollP >= 0f
        val dashing = dashP >= 0f
        val airCand = inAir && !rolling && (airH > 8f || airVz > 60f)
        airTimer = if (airCand) airTimer + dt else 0f
        val airT = if (airTimer > 0.04f) 1f else 0f
        val rollTgt = if (rolling) min(rollP / 0.15f, (1f - rollP) / 0.2f).coerceIn(0f, 1f) else 0f
        val dashTgt = if (dashing) min(dashP / 0.12f, (1f - dashP) / 0.25f).coerceIn(0f, 1f) else 0f
        if (first) {
            airW = airT
            rollW = rollTgt
            dashW = dashTgt
        } else {
            airW += (airT - airW) * min(1f, dt * (if (airT > airW) 16f else 11f))
            rollW += (rollTgt - rollW) * min(1f, dt * 26f)
            dashW += (dashTgt - dashW) * min(1f, dt * 30f)
        }
        var landPuff = false
        var takeoffPuff = false
        if (wasAir && airW < 0.4f) {
            sv[2] -= 3f
            sv[3] -= 4f
            sv[4] -= 5f
            sv[0] += 2.2f
            sv[7] -= 3f
            wasAir = false
            landPuff = true
            landDip = 1f
        } else if (airW > 0.7f) {
            if (!wasAir) takeoffPuff = true
            wasAir = true
        }
        if (rolling && rollW < 0.3f) takeoffPuff = true
        val dashStart = dashing && !wasDash
        wasDash = dashing
        val fl = V.flash
        if (!first && fl > prevFlash + 0.25f) {
            hitKick = 1f
            sv[0] -= 6f
            sv[2] += 4f
            sv[3] += 5f
            sv[4] += 6f
            sv[7] += 3f
            sv[8] += 6f
            sv[9] += 6f
        }
        prevFlash = fl
        hitKick = max(0f, hitKick - dt * 4.5f)
        landDip = max(0f, landDip - dt * 5.5f)
        val bp = clock % 3.6f
        eyeOpen = if (bp < 0.14f) max(0.12f, abs(bp - 0.07f) / 0.07f) else 1f
        val vzN = (airVz / 340f).coerceIn(-1.2f, 1f)
        val tuck = max(rollW, airW * (0.30f + 0.35f * max(0f, vzN) - 0.2f * max(0f, -vzN)))
        val still = 1f - max(rollW, max(airW, dashW))
        val cast = skillOn && (ki == 3 || ki == 4)
        val mvs = if (cast) 0f else still * (if (acting) blend * 0.35f else blend)
        val lmv = if (cast) 0f else still * blend

        // ── poses ──
        kI[0] = -0.55f + sin(phase) * 0.55f * mvs + sin(clock * 1.3f) * 0.04f * (1f - mvs)
        kI[1] = -0.15f
        kI[2] = -0.9f - 0.25f * mvs
        kI[3] = -0.9f - 0.2f * mvs
        kI[4] = -0.55f - sin(phase) * 0.55f * mvs + sin(clock * 1.1f + 1f) * 0.04f * (1f - mvs)
        kI[5] = 0.15f
        kI[6] = -0.9f - 0.25f * mvs
        kI[7] = -0.9f - 0.2f * mvs
        kI[8] = 0f
        kI[9] = 0.06f + 0.12f * mvs
        kI[10] = 0.06f
        var lunge = 0f
        var spin = 0f
        if (acting) {
            if (ki == 6) {
                // Marca da Morte: rajada de cortes alternados
                val w = t * 24f
                kT[0] = -1.3f + sin(w) * 0.9f
                kT[1] = -0.25f
                kT[2] = -0.3f
                kT[3] = -1.2f
                kT[4] = -1.3f - sin(w) * 0.9f
                kT[5] = 0.25f
                kT[6] = -0.3f
                kT[7] = -1.2f
                kT[8] = sin(w) * 0.7f
                kT[9] = 0.45f
                kT[10] = 0.35f
                val u = ease(min(1f, t / 0.12f))
                val tail = ease(((t - 0.75f) / 0.25f).coerceIn(0f, 1f))
                for (i in 0 until NK) kO[i] = lerp(lerp(kS[i], kT[i], u), kI[i], tail)
                lunge = 1.6f * sin(min(1f, t / 0.3f) * PI.toFloat())
            } else {
                poseFrom(ki, t)
                val la = TW[ki] - 0.04f
                if (ki != 3 && ki != 4 && t > la && t < TE[ki]) {
                    lunge = -sin(((t - la) / (TE[ki] - la)) * PI.toFloat()) * 1.3f
                }
            }
            if (ki == 2) {
                spin = TWO_PI * ease(((t - 0.10f) / (TE[2] - 0.10f)).coerceIn(0f, 1f))
            } else if (ki == 5) {
                spin = TWO_PI * 2f * ease(((t - 0.12f) / 0.66f).coerceIn(0f, 1f))
            }
        } else {
            for (i in 0 until NK) kO[i] = kI[i]
        }
        for (i in 0 until NK) lastPose[i] = kO[i]
        if (rollW > 0.001f) {
            kO[0] = lerp(kO[0], -1.2f, rollW)
            kO[1] = lerp(kO[1], -0.2f, rollW)
            kO[2] = lerp(kO[2], -1.9f, rollW)
            kO[3] = lerp(kO[3], -1.0f, rollW)
            kO[4] = lerp(kO[4], -1.2f, rollW)
            kO[5] = lerp(kO[5], 0.2f, rollW)
            kO[6] = lerp(kO[6], -1.9f, rollW)
            kO[7] = lerp(kO[7], -1.0f, rollW)
        }
        if (airW > 0.001f && !acting) {
            kO[0] = lerp(kO[0], -2.0f - 0.25f * vzN, airW)
            kO[1] = lerp(kO[1], -0.6f, airW)
            kO[2] = lerp(kO[2], -0.5f, airW)
            kO[3] = lerp(kO[3], -0.6f, airW)
            kO[4] = lerp(kO[4], -2.0f - 0.25f * vzN, airW)
            kO[5] = lerp(kO[5], 0.6f, airW)
            kO[6] = lerp(kO[6], -0.5f, airW)
            kO[7] = lerp(kO[7], -0.6f, airW)
        }
        var dashSpin = 0f
        if (dashW > 0.001f) {
            if (dashUp) {
                // sobe reto: lâminas cruzadas acima da cabeça e giro no ar
                kO[0] = lerp(kO[0], -2.8f, dashW)
                kO[1] = lerp(kO[1], 0.35f, dashW)
                kO[2] = lerp(kO[2], -0.3f, dashW)
                kO[3] = lerp(kO[3], -0.2f, dashW)
                kO[4] = lerp(kO[4], -2.8f, dashW)
                kO[5] = lerp(kO[5], -0.35f, dashW)
                kO[6] = lerp(kO[6], -0.3f, dashW)
                kO[7] = lerp(kO[7], -0.2f, dashW)
                dashSpin = TWO_PI * ease(dashP.coerceIn(0f, 1f))
            } else {
                // dash para a frente: braços e lâminas para trás, corpo deitado
                kO[0] = lerp(kO[0], 1.1f, dashW)
                kO[1] = lerp(kO[1], -0.25f, dashW)
                kO[2] = lerp(kO[2], -0.2f, dashW)
                kO[3] = lerp(kO[3], 0.25f, dashW)
                kO[4] = lerp(kO[4], 1.1f, dashW)
                kO[5] = lerp(kO[5], 0.25f, dashW)
                kO[6] = lerp(kO[6], -0.2f, dashW)
                kO[7] = lerp(kO[7], 0.25f, dashW)
            }
        }

        // ── molas ──
        val bankT = (-yawVel * 0.03f).coerceIn(-0.22f, 0.22f) * spN
        val rush = max(dashW * (if (dashUp) 0.4f else 1f), rollW * 0.5f)
        spring(0, 0.12f * spN, 90f, 12f, sdt)
        spring(1, bankT, 80f, 9f, sdt)
        val scarfBase = 0.12f + 0.40f * spN + rush * 0.9f + airW * (0.35f - 0.3f * vzN) + 0.09f * sin(clock * 3f + phase * 2f) * (0.4f + spN)
        spring(2, scarfBase, 36f, 5.5f, sdt)
        spring(3, scarfBase * 0.8f + 0.08f, 27f, 4.5f, sdt)
        spring(4, scarfBase * 0.7f + 0.10f, 19f, 3.8f, sdt)
        spring(5, 0.12f * spN + 0.25f * airW + 0.5f * rush, 55f, 6f, sdt)
        spring(6, 0.2f * spN + 0.3f * airW + 0.6f * rush, 40f, 4.5f, sdt)
        spring(7, 0.03f - 0.2f * mvs * abs(sin(phase)) - 0.1f * airW, 60f, 7f, sdt)
        spring(8, (yawVel * 0.03f).coerceIn(-0.35f, 0.35f), 70f, 9f, sdt)
        spring(9, 0.12f + 0.35f * spN + 0.25f * airW + 0.6f * rush, 30f, 4f, sdt)
        spring(10, 0.06f + 0.5f * spN + 0.5f * airW + 0.8f * rush, 45f, 5.5f, sdt)

        val bob = (0.5f + 0.5f * cos(phase * 2f)) * 1.2f * mvs + sin(clock * 2.2f) * 0.3f * (1f - mvs)
        bBreath = sin(clock * 2.2f) * 0.26f * (1f - mvs * 0.6f)
        val sway = sin(phase) * 0.8f * mvs + sin(clock * 0.9f) * 0.3f * (1f - mvs)
        val crouch = max(kO[10], max(rollW * 0.9f, landDip * 0.9f))
        bLean = sp[0] + kO[9] * 0.5f - hitKick * 0.3f + rollW * 0.25f + landDip * 0.2f + dashW * (if (dashUp) 0.1f else 0.85f)
        bHipY = 12.6f + bob - crouch * 3f - hitKick * 0.9f
        val wob = sin(phase) * 0.12f * mvs
        bChestTw = kO[8] - sin(phase) * 0.18f * mvs - wob
        bHipTw = kO[8] * 0.6f + wob * 0.5f

        // brilho das lâminas e dos olhos
        val act = if (acting) 1f else 0f
        bladeG = max(act, dashW)
        val eq = (0.5f + 0.3f * sin(clock * 2.4f) + 0.5f * act).coerceIn(0f, 1f)
        eyeC = rgb((200 + 55 * eq).toInt(), (30 + 90 * eq).toInt(), (40 + 60 * eq).toInt())

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
        if (spin != 0f) rotY(spin)
        if (dashSpin != 0f) {
            translate(0f, 7f, 0f)
            rotY(dashSpin)
            translate(0f, -7f, 0f)
        }
        if (rolling) {
            val r = ((rollP - 0.10f) / 0.80f).coerceIn(0f, 1f)
            val ang = (r * 0.6f + r * r * (3f - 2f * r) * 0.4f) * TWO_PI
            val lift = 4.5f * sin(r * PI.toFloat())
            translate(0f, 10f + lift, 0f)
            rotX(ang)
            translate(0f, -11f, 0f)
        }

        // pernas
        for (k in 0..1) {
            val sd = if (k == 0) 1f else -1f
            val pp = if (sd > 0f) phase else phase + PI.toFloat()
            var a = sin(pp) * 0.72f * lmv
            var kn = max(0f, -cos(pp)) * 1.05f * lmv + 0.08f + crouch * 0.9f
            var ft = max(0f, cos(pp)) * 0.35f * lmv + airW * (0.55f * max(0f, vzN) - 0.25f * max(0f, -vzN))
            a = a * (1f - airW) - tuck + sd * 0.3f * airW
            kn = (kn + tuck * 1.5f + (if (sd > 0f) 0.5f else -0.1f) * airW).coerceAtLeast(0f)
            if (dashW > 0.001f) {
                if (dashUp) {
                    a = lerp(a, -0.5f + sd * 0.1f, dashW)
                    kn = lerp(kn, 1.2f, dashW)
                    ft = lerp(ft, 0.5f, dashW)
                } else {
                    a = lerp(a, if (sd > 0f) 1.0f else -0.7f, dashW)
                    kn = lerp(kn, if (sd > 0f) 0.9f else 0.5f, dashW)
                    ft = lerp(ft, 0.4f, dashW)
                }
            }
            legA[k] = a
            legK[k] = kn
            legF[k] = ft
        }
        assemble()

        // ── poeira dos passos, pouso, decolagem ──
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
        if (takeoffPuff || dashStart) {
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

        // ── brasas sombrias subindo do corpo ──
        moteT -= dt
        if (moteT <= 0f) {
            moteT = if (mvs > 0.5f) 0.07f else 0.2f
            emitSpark(
                fx + (rnd.nextFloat() - 0.5f) * 10f * rs, fy - (6f + rnd.nextFloat() * 14f) * rs,
                (rnd.nextFloat() - 0.5f) * rs * 6f, -rs * (4f + rnd.nextFloat() * 6f),
                0.5f + rnd.nextFloat() * 0.4f, -rs * 3f,
                if (rnd.nextFloat() > 0.5f) 0xFFB3202D.toInt() else 0xFF5A3A8A.toInt(), 0.75f
            )
        }

        // faíscas no instante do impacto
        if (acting && !released && t >= TI[ki]) {
            released = true
            for (i in 0..9) {
                val ang = rnd.nextFloat() * TWO_PI
                val sp0 = (14f + rnd.nextFloat() * 30f) * rs
                val tx = if (i % 2 == 0) tipRX else tipLX
                val ty = if (i % 2 == 0) tipRY else tipLY
                emitSpark(
                    tx, ty, cos(ang) * sp0, sin(ang) * sp0 - rs * 6f,
                    0.25f + rnd.nextFloat() * 0.2f, rs * 70f,
                    if (i % 3 == 0) 0xFFFFFFFF.toInt() else if (i % 3 == 1) 0xFFFF5060.toInt() else 0xFFB3202D.toInt(), 1.0f
                )
            }
            sv[0] -= 2f
            sv[2] += 3f
            sv[3] += 4f
            sv[4] += 5f
        }

        // ── rastro das duas lâminas ──
        if (!acting && !(dashW > 0.3f && !dashUp)) {
            trN = 0
        } else {
            if (trN == TRAIL_MAX) {
                System.arraycopy(trRX, 1, trRX, 0, TRAIL_MAX - 1)
                System.arraycopy(trRY, 1, trRY, 0, TRAIL_MAX - 1)
                System.arraycopy(trLX, 1, trLX, 0, TRAIL_MAX - 1)
                System.arraycopy(trLY, 1, trLY, 0, TRAIL_MAX - 1)
                trN = TRAIL_MAX - 1
            }
            trRX[trN] = tipRX
            trRY[trN] = tipRY
            trLX[trN] = tipLX
            trLY[trN] = tipLY
            trN++
            val fade = if (acting) 1f else dashW
            p.strokeCap = Paint.Cap.ROUND
            for (i in 1 until trN) {
                val a = i.toFloat() / trN.toFloat()
                p.style = Paint.Style.STROKE
                p.strokeWidth = max(2.4f, rs * 3.4f * a)
                p.color = (((a * a * 0.30f * fade * 255f).toInt()) shl 24) or 0x00D8354A
                c.drawLine(trRX[i - 1], trRY[i - 1], trRX[i], trRY[i], p)
                c.drawLine(trLX[i - 1], trLY[i - 1], trLX[i], trLY[i], p)
                p.strokeWidth = max(1f, rs * 1.1f)
                p.color = (((a * a * 0.75f * fade * 255f).toInt()) shl 24) or 0x00FFE4E4
                c.drawLine(trRX[i - 1], trRY[i - 1], trRX[i], trRY[i], p)
                c.drawLine(trLX[i - 1], trLY[i - 1], trLX[i], trLY[i], p)
            }
            p.strokeCap = Paint.Cap.BUTT
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
        p.style = Paint.Style.FILL
    }
}
