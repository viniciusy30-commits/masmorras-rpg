package com.viniciusy.masmorras

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Mini motor 3D de cubos (estilo Minecraft Dungeons) desenhado em Canvas.
 * O Guerreiro é um esqueleto de partes articuladas (pernas com joelho, quadril/peito com
 * contra-rotação, cabeça, braços, escudo, espada, capa, penacho e tabardo em molas).
 * Cada cubo tem 3 faces visíveis com iluminação própria.
 */
object Warrior3D {
    private val V = VoxelArt

    private const val MAX = 300
    private const val PITCH = 0.8f
    private const val PX = 1.6f
    private val COSP = cos(PITCH)
    private val SINP = sin(PITCH)
    private val TWO_PI = (2.0 * PI).toFloat()

    // luz vindo de cima, esquerda e frente
    private const val LX = -0.45f
    private const val LY = 0.78f
    private const val LZ = 0.43f

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

    // pilha de matrizes: 9 floats de rotação (linha a linha) + 3 de translação
    private val stack = Array(12) { FloatArray(12) }
    private var top = 0

    // buffers de render
    private val scrX = FloatArray(MAX * 8)
    private val scrY = FloatArray(MAX * 8)
    private val faceCol = IntArray(MAX * 6)
    private val keys = FloatArray(MAX)
    private val order = IntArray(MAX)
    private var n = 0
    private val path = Path()

    private var rs = 1f
    private var ox = 0f
    private var oy = 0f

    // estado externo (preenchido pelo Player antes de desenhar)
    var rollP = -1f
    var inAir = false
    var airVz = 0f
    var swing = 0       // 0 corte diagonal, 1 corte vertical (volta), 2 estocada, 3 golpe forte
    var charge = -1f    // 0..1 enquanto segura o ataque; <0 = sem carga

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
            if (ny * SINP + nz * COSP > 0.02f) {
                if (emissive) {
                    faceCol[idx] = base
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

    private fun flush(c: Canvas, p: Paint) {
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
        cube(0f, -2.2f, 2.8f, 1.2f, 2.2f, 0.25f, STEEL)
        cube(0f, -0.2f, 0f, 2.85f, 0.45f, 2.85f, GOLD)
        translate(0f, -5.6f, 0f)
        rotX(knee)
        cube(0f, 0.2f, 2.0f, 2.3f, 1.5f, 1.0f, GOLD)
        cube(0f, 0.2f, 2.9f, 1.0f, 0.9f, 0.4f, STEEL_L)
        cube(0f, -2.7f, 0f, 2.35f, 2.8f, 2.4f, STEEL)
        cube(0f, -2.9f, 2.4f, 1.1f, 2.4f, 0.3f, STEEL_L)
        cube(0f, -5.2f, 0f, 2.55f, 0.4f, 2.6f, GOLD_D)
        translate(0f, -5.4f, 0f)
        rotX(foot)
        cube(0f, 0.2f, 1.2f, 2.7f, 1.5f, 3.7f, LEATHER)
        cube(0f, -1.2f, 1.3f, 2.85f, 0.3f, 3.9f, LEATHER_D)
        cube(0f, 0.8f, 4.1f, 2.2f, 0.8f, 0.8f, STEEL_D)
        pop()
    }

    private fun pauldron(side: Float) {
        cube(side * 0.8f, 1.5f, 0f, 3.1f, 1.0f, 3.2f, STEEL_L)
        cube(side * 1.5f, 0.1f, 0f, 2.8f, 0.9f, 3.0f, STEEL)
        cube(side * 2.0f, -1.0f, 0f, 2.4f, 0.8f, 2.7f, STEEL_D)
        cube(side * 0.8f, 2.6f, 0f, 3.15f, 0.22f, 3.25f, GOLD)
        cube(side * 1.9f, 3.3f, 0f, 0.55f, 1.0f, 0.55f, STEEL_L)
        cube(side * 1.9f, 4.6f, 0f, 0.3f, 0.7f, 0.3f, STEEL_L)
    }

    private fun armBase(el: Float) {
        cube(0f, -2.7f, 0f, 2.0f, 2.9f, 2.0f, LEATHER_D)
        cube(0f, -1.4f, 0f, 2.2f, 0.4f, 2.2f, GOLD)
        translate(0f, -5.7f, 0f)
        rotX(el)
        cube(0f, -0.1f, 0f, 2.15f, 0.9f, 2.15f, STEEL_D)
        cube(0f, -2.7f, 0f, 2.25f, 2.7f, 2.25f, STEEL)
        cube(0f, -2.7f, 2.3f, 1.0f, 2.2f, 0.25f, STEEL_L)
        cube(0f, -5.1f, 0f, 2.4f, 0.4f, 2.4f, GOLD)
        cube(0f, -6.1f, 0f, 1.8f, 1.3f, 1.8f, LEATHER_L)
        cube(0f, -6.2f, 1.7f, 1.5f, 0.5f, 0.4f, LEATHER)
    }

    private fun sword(glow: Float, wrist: Float) {
        translate(0f, -6.1f, 0f)
        rotX(1.9f + wrist)
        cube(0f, 0.4f, 0f, 0.85f, 2.2f, 0.85f, LEATHER)
        for (i in 0..2) cube(0f, -0.6f + i * 1.0f, 0f, 0.95f, 0.18f, 0.95f, LEATHER_D)
        cube(0f, -2.5f, 0f, 1.3f, 0.8f, 1.3f, GOLD)
        cube(0f, -2.6f, 1.35f, 0.5f, 0.4f, 0.3f, RED)
        cube(0f, 3.1f, 0f, 4.6f, 0.6f, 1.5f, GOLD)
        cube(-4.7f, 3.9f, 0f, 0.6f, 0.9f, 1.2f, GOLD)
        cube(4.7f, 3.9f, 0f, 0.6f, 0.9f, 1.2f, GOLD)
        cube(0f, 3.2f, 1.6f, 1.0f, 0.8f, 0.3f, RED)
        cube(0f, 14.4f, 0f, 1.0f, 10.9f, 1.7f, BLADE)
        cube(0f, 14.4f, 0f, 1.12f, 9.8f, 0.55f, STEEL)
        cube(0f, 26.2f, 0.2f, 0.8f, 0.9f, 1.2f, BLADE)
        cube(0f, 27.5f, 0.1f, 0.5f, 0.5f, 0.8f, STEEL_L)
        val g = glow.coerceIn(0f, 1f)
        val gc = rgb(255, (150 + 80 * g).toInt(), (60 + 90 * g).toInt())
        for (i in 0..4) cube(0f, 8.5f + i * 3.6f, 1.75f, 0.45f, 0.9f, 0.18f, gc, true)
        cube(0f, 14.4f, 1.9f, 0.25f, 10.2f, 0.12f, 0xFFFFFFFF.toInt(), true)
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
        cube(0.3f, 0.4f, 0.6f, 4.8f, 6.2f, 0.9f, STEEL_DD)
        cube(0.3f, 0.4f, 1.25f, 4.1f, 5.5f, 0.6f, RED)
        cube(0.3f, 6.9f, 1.2f, 4.2f, 0.35f, 0.8f, GOLD)
        cube(-4.0f, 0.5f, 1.2f, 0.35f, 5.8f, 0.8f, GOLD)
        cube(4.6f, 0.5f, 1.2f, 0.35f, 5.8f, 0.8f, GOLD)
        cube(0.3f, -5.6f, 0.6f, 3.1f, 0.9f, 0.9f, STEEL_DD)
        cube(0.3f, -5.6f, 1.25f, 2.4f, 0.7f, 0.6f, RED)
        cube(0.3f, -7.2f, 0.6f, 1.5f, 0.7f, 0.9f, STEEL_DD)
        cube(0.3f, -8.2f, 0.6f, 0.6f, 0.5f, 0.8f, STEEL_DD)
        cube(0.3f, 0.6f, 1.95f, 0.6f, 4.4f, 0.1f, CREAM)
        cube(0.3f, 1.7f, 1.95f, 3.4f, 0.6f, 0.1f, CREAM)
        cube(0.3f, 1.0f, 2.15f, 1.5f, 1.5f, 0.4f, GOLD)
        cube(0.3f, 1.0f, 2.5f, 0.7f, 0.7f, 0.25f, RED)
        pop()
    }

    private fun head(turn: Float, tilt: Float, p0: Float, p1: Float) {
        push()
        translate(0f, 14.2f, 0f)
        rotY(turn)
        rotX(tilt)
        cube(0f, 0.3f, 0f, 2.3f, 1.0f, 2.3f, SKIN_D)
        cube(0f, 5.2f, 0f, 5.0f, 5.0f, 4.8f, SKIN)
        cube(-2.0f, 4.7f, 4.85f, 1.05f, 1.0f, 0.12f, EYE)
        cube(2.0f, 4.7f, 4.85f, 1.05f, 1.0f, 0.12f, EYE)
        cube(-2.1f, 4.9f, 4.95f, 0.55f, 0.4f, 0.1f, 0xFFFFFFFF.toInt(), true)
        cube(1.9f, 4.9f, 4.95f, 0.55f, 0.4f, 0.1f, 0xFFFFFFFF.toInt(), true)
        cube(0f, 3.3f, 5.0f, 0.6f, 0.7f, 0.3f, SKIN_D)
        cube(0f, 1.7f, 4.85f, 1.5f, 0.3f, 0.12f, SKIN_D)
        cube(-2.0f, 6.4f, 4.9f, 1.4f, 0.25f, 0.1f, SKIN_D)
        cube(2.0f, 6.4f, 4.9f, 1.4f, 0.25f, 0.1f, SKIN_D)
        cube(0f, 8.6f, 0f, 5.5f, 2.9f, 5.4f, STEEL)
        cube(0f, 6.3f, 0f, 5.7f, 0.4f, 5.5f, GOLD)
        cube(0f, 7.0f, 5.4f, 5.6f, 0.55f, 0.45f, STEEL_D)
        cube(0f, 4.8f, 5.3f, 0.55f, 2.4f, 0.4f, STEEL)
        for (k in 0..1) {
            val sd = if (k == 0) -1f else 1f
            cube(sd * 5.4f, 4.6f, 0.3f, 0.55f, 2.5f, 3.9f, STEEL)
            cube(sd * 5.7f, 3.0f, 0.3f, 0.35f, 0.9f, 2.6f, STEEL_D)
            cube(sd * 5.9f, 7.8f, 0f, 0.3f, 1.0f, 3.0f, GOLD)
            cube(sd * 6.6f, 9.2f, -0.5f, 0.5f, 1.0f, 2.6f, GOLD)
            cube(sd * 7.6f, 10.6f, -0.9f, 0.4f, 0.9f, 2.0f, GOLD)
            cube(sd * 8.4f, 12.0f, -1.2f, 0.35f, 0.8f, 1.4f, GOLD_D)
        }
        cube(0f, 11.7f, 0f, 1.0f, 0.9f, 5.3f, STEEL_D)
        cube(0f, 11.9f, 3.0f, 0.5f, 0.4f, 1.0f, GOLD)
        // penacho com mola (segue a cabeça com atraso)
        push()
        translate(0f, 12.4f, -4.5f)
        rotX(0.45f + p0)
        cube(0f, 0.8f, -0.6f, 0.85f, 0.8f, 2.8f, RED)
        translate(0f, 0.4f, -3.4f)
        rotX(0.5f + p1)
        cube(0f, 0f, -1.6f, 0.8f, 0.8f, 2.8f, RED)
        translate(0f, 0f, -4.4f)
        rotX(0.5f + p1 * 1.3f)
        cube(0f, -0.3f, -1.2f, 0.75f, 0.7f, 2.0f, RED_D)
        pop()
        pop()
    }

    private fun torso(chestTw: Float, c0: Float, c1: Float, c2: Float, tabA: Float, breath: Float) {
        cube(0f, 0.6f, 0f, 5.6f, 1.4f, 3.5f, LEATHER)
        cube(0f, 0.8f, 3.55f, 1.6f, 1.2f, 0.5f, GOLD)
        cube(0f, 0.8f, 4.0f, 0.8f, 0.7f, 0.25f, RED)
        for (k in 0..1) {
            val sd = if (k == 0) -1f else 1f
            cube(sd * 5.7f, 0.4f, 0f, 0.6f, 1.3f, 3.2f, LEATHER_D)
            cube(sd * 3.0f, -1.4f, 3.6f, 2.2f, 2.6f, 0.45f, STEEL_D)
            cube(sd * 3.0f, -1.3f, 4.0f, 1.7f, 2.0f, 0.25f, STEEL)
        }
        // tabardo balançando
        push()
        translate(0f, -0.6f, 3.9f)
        rotX(tabA)
        cube(0f, -2.6f, 0.2f, 2.4f, 2.8f, 0.3f, RED)
        cube(0f, -5.4f, 0.2f, 2.4f, 0.4f, 0.4f, GOLD)
        cube(0f, -5.9f, 0.2f, 1.8f, 0.3f, 0.3f, RED_D)
        pop()
        cube(0f, 3.8f, 0f, 5.2f, 2.2f, 3.5f, STEEL_D)
        for (i in 0..2) cube(0f, 2.6f + i * 1.6f, 3.4f, 4.8f - i * 0.2f, 0.45f, 0.4f, STEEL)
        // peitoral (respira + contra-rotaciona)
        push()
        rotY(chestTw)
        translate(0f, breath, 0f)
        cube(0f, 9.8f, 0f, 6.9f, 5.0f, 3.9f, STEEL)
        cube(0f, 10.5f, 3.9f, 5.5f, 3.8f, 0.6f, STEEL_L)
        cube(0f, 13.9f, 3.7f, 6.3f, 0.5f, 0.8f, GOLD)
        cube(0f, 10.0f, 4.5f, 0.9f, 3.0f, 0.15f, RED)
        cube(0f, 10.7f, 4.5f, 2.4f, 0.8f, 0.15f, RED)
        cube(0f, 8.8f, 4.5f, 3.2f, 0.4f, 0.2f, GOLD_D)
        cube(0f, 10.0f, -3.95f, 5.6f, 4.4f, 0.5f, STEEL_D)
        cube(0f, 10.5f, -4.35f, 2.6f, 3.2f, 0.2f, STEEL)
        cube(0f, 14.4f, 0f, 3.4f, 0.9f, 3.0f, STEEL_D)
        // capa em 3 segmentos (molas)
        push()
        translate(0f, 13.6f, -4.1f)
        rotX(c0)
        cube(0f, -2.9f, -0.4f, 6.0f, 3.1f, 0.4f, RED_D)
        cube(0f, 0.9f, 0.1f, 6.7f, 0.95f, 1.2f, STEEL_D)
        cube(-5.2f, 0.6f, 0.1f, 1.0f, 1.0f, 1.4f, GOLD)
        cube(5.2f, 0.6f, 0.1f, 1.0f, 1.0f, 1.4f, GOLD)
        translate(0f, -5.8f, 0f)
        rotX(c1)
        cube(0f, -2.9f, -0.4f, 6.3f, 3.0f, 0.4f, RED)
        translate(0f, -5.8f, 0f)
        rotX(c2)
        cube(0f, -2.6f, -0.4f, 6.0f, 2.6f, 0.4f, RED_D)
        cube(0f, -5.3f, -0.4f, 6.0f, 0.4f, 0.5f, GOLD)
        cube(-4.2f, -6.7f, -0.4f, 1.3f, 1.2f, 0.4f, RED)
        cube(-1.4f, -6.7f, -0.4f, 1.3f, 1.2f, 0.4f, RED_D)
        cube(1.4f, -6.7f, -0.4f, 1.3f, 1.2f, 0.4f, RED)
        cube(4.2f, -6.7f, -0.4f, 1.3f, 1.2f, 0.4f, RED_D)
        pop()
        pop()
    }

    // ───────────── animação (molas + ataque) ─────────────

    // molas: 0 inclinação, 1 banking, 2-4 capa, 5-6 penacho, 7 tabardo, 8 cabeça
    private val sp = FloatArray(10)
    private val sv = FloatArray(10)
    private var combo = 0
    private var chargeW = 0f
    private var sparkT = 0f
    private val lastPose = FloatArray(8)
    private val kS = FloatArray(8)

    // keyframes por golpe: sh, z, el, tw, wrist, crouch, lean, hip
    private val K_W = arrayOf(
        floatArrayOf(-2.9f, 0.75f, -1.0f, -0.75f, -0.45f, 0.15f, -0.18f, 0.35f),
        floatArrayOf(-0.15f, -0.45f, -0.25f, 0.95f, 0.65f, 0.55f, 0.32f, -0.45f),
        floatArrayOf(-1.0f, 0.20f, -1.7f, 0.75f, 0.9f, 0.40f, -0.12f, -0.55f),
        floatArrayOf(-2.6f, 0.35f, -0.5f, -0.4f, 1.2f, 0.3f, -0.2f, 0.3f)
    )
    private val K_E = arrayOf(
        floatArrayOf(-0.35f, -0.30f, -0.30f, 0.80f, 0.55f, 0.50f, 0.28f, -0.40f),
        floatArrayOf(-2.7f, 0.10f, -0.45f, -0.20f, -0.10f, 0.15f, 0.0f, 0.15f),
        floatArrayOf(-1.45f, 0.0f, -0.15f, -0.50f, 1.15f, 0.60f, 0.30f, 0.45f),
        floatArrayOf(-0.1f, -0.1f, -0.2f, 0.45f, 0.6f, 0.85f, 0.5f, -0.3f)
    )
    // fim da preparação, fim do impacto, fim do segurar
    private val K_T = arrayOf(
        floatArrayOf(0.26f, 0.42f, 0.56f),
        floatArrayOf(0.14f, 0.34f, 0.52f),
        floatArrayOf(0.30f, 0.46f, 0.62f),
        floatArrayOf(0.08f, 0.24f, 0.60f)
    )
    private val K_L = floatArrayOf(3.0f, 2.2f, 5.5f, 5.0f)
    private var prevAtk = 0f
    private var prevPhase = 0f
    private var spd = 0f
    private var wasAir = false

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
            sv[2] += 2.5f; sv[3] += 3.5f; sv[4] += 4.5f
            sv[5] += 2.0f; sv[6] += 3.0f; sv[7] += 2.0f
            if (sw == 3) { sv[2] += 3f; sv[3] += 4f; sv[4] += 5f }
        }
        prevAtk = attack

        val rolling = rollP >= 0f
        val airT = if (inAir && !rolling) 1f else 0f
        val rollTgt = if (rolling) min(rollP / 0.15f, (1f - rollP) / 0.2f).coerceIn(0f, 1f) else 0f
        if (first) {
            airW = airT
            rollW = rollTgt
        } else {
            airW += (airT - airW) * min(1f, dt * (if (airT > airW) 16f else 11f))
            rollW += (rollTgt - rollW) * min(1f, dt * 26f)
        }
        if (wasAir && airW < 0.4f) {
            // aterrissagem: capa sobe, corpo amortece
            sv[2] -= 3f; sv[3] -= 4f; sv[4] -= 5f; sv[0] += 2.2f; sv[7] -= 3f
            wasAir = false
        } else if (airW > 0.7f) {
            wasAir = true
        }
        val vzN = (airVz / 340f).coerceIn(-1.2f, 1f)
        val tuck = max(rollW, airW * (0.28f + 0.32f * max(0f, vzN)))
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
        val tW = K_T[sw][0]
        val tS = K_T[sw][1]
        val tH = K_T[sw][2]
        if (attacking) {
            setK(kI, idleSh, idleZ, idleEl, 0f, 0f, 0f, 0f, 0f)
            if (t < tW) {
                mixK(kS, K_W[sw], ease(t / tW))
            } else if (t < tS) {
                var u = (t - tW) / (tS - tW)
                u = 1f - (1f - u) * (1f - u) * (1f - u)
                mixK(K_W[sw], K_E[sw], u)
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
                lunge = sin(((t - la) / (lb - la)) * PI.toFloat()) * K_L[sw]
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
        val lean = sp[0] + leanAtk * 0.5f
        val hipY = 12.6f + bob - crouch * 3.2f
        val wob = sin(phase) * 0.14f * mvs
        val chestTw = tw - hipTw * 0.5f - wob - sin(phase) * 0.22f * mvs
        val glow = 0.35f + 0.15f * sin(clock * 4f) + (if (attacking) 0.5f else 0f) +
            chargeW * (0.5f + 0.4f * sin(clock * 18f))

        rs = s * PX
        ox = fx
        oy = fy
        n = 0
        top = 0
        identity()
        rotY(yaw)
        rotZ(sp[1])
        translate(sway * 0.5f, 0f, lunge)
        if (rolling) {
            val ang = (rollP * 0.55f + ease(rollP) * 0.45f) * TWO_PI
            val lift = 7f * sin(rollP * PI.toFloat())
            translate(0f, 11f + lift, 0f)
            rotX(ang)
            translate(0f, -11f, 0f)
        }

        // pernas: swing + joelho dobrando na passada + passo no ataque
        for (k in 0..1) {
            val sd = if (k == 0) 1f else -1f
            val pp = if (sd > 0f) phase else phase + PI.toFloat()
            var a = sin(pp) * 0.85f * mvs
            var kn = max(0f, -cos(pp)) * 1.1f * mvs + 0.10f + crouch * 0.9f
            val ft = max(0f, cos(pp)) * 0.35f * mvs
            if (attacking) {
                val front = (combo % 2 == 0) == (sd > 0f)
                val lk = lunge / K_L[sw]
                if (front) {
                    a -= 0.55f * lk
                } else {
                    a += 0.45f * lk
                    kn += 0.35f * lk
                }
            }
            a = a * (1f - airW) - tuck
            leg(sd, a, kn + tuck * 1.5f, hipY, ft)
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
        rightArm(sh, shZ, el, wrist, glow)
        leftArm(shieldSh, 0.12f, -1.4f)
        pop()
        pop()
        pop()

        flush(c, p)

        // ── rastro da espada ──
        if (!attacking) {
            trN = 0
        } else {
            if (t >= tW && t <= tH + 0.05f) {
                if (trN == 24) {
                    System.arraycopy(trX, 1, trX, 0, 23)
                    System.arraycopy(trY, 1, trY, 0, 23)
                    System.arraycopy(trMx, 1, trMx, 0, 23)
                    System.arraycopy(trMy, 1, trMy, 0, 23)
                    trN = 23
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
            val fade = min(1f, attack * 3f)
            for (i in 1 until trN) {
                val a = i.toFloat() / trN.toFloat()
                path.reset()
                path.moveTo(trX[i - 1], trY[i - 1])
                path.lineTo(trX[i], trY[i])
                path.lineTo(trMx[i], trMy[i])
                path.lineTo(trMx[i - 1], trMy[i - 1])
                path.close()
                p.style = Paint.Style.FILL
                p.color = (((a * 0.55f * fade * 255f).toInt()) shl 24) or 0x00FFB74D
                c.drawPath(path, p)
                p.style = Paint.Style.STROKE
                p.strokeWidth = max(2.4f, rs * 1.8f)
                p.strokeCap = Paint.Cap.ROUND
                p.color = (((a * 0.35f * fade * 255f).toInt()) shl 24) or 0x00FFE9A8
                c.drawLine(trX[i - 1], trY[i - 1], trX[i], trY[i], p)
                p.strokeWidth = max(1.2f, rs * 0.8f)
                p.color = (((a * 0.95f * fade * 255f).toInt()) shl 24) or 0x00FFFFFF
                c.drawLine(trX[i - 1], trY[i - 1], trX[i], trY[i], p)
            }
            p.style = Paint.Style.FILL
            p.strokeCap = Paint.Cap.BUTT
        }

        // ── anel de carga no chão ──
        if (chargeW > 0.05f && !attacking) {
            val ch = max(0f, charge)
            val rr = (10f + 16f * ch) * rs
            p.style = Paint.Style.STROKE
            p.strokeWidth = max(2f, rs * (1.2f + 1.5f * ch))
            p.color = (((chargeW * (0.35f + 0.5f * ch) * 255f).toInt()) shl 24) or 0x00FFB74D
            c.drawOval(fx - rr, fy - rr * SINP, fx + rr, fy + rr * SINP, p)
            p.style = Paint.Style.FILL
            if (ch > 0.15f) {
                sparkT -= dt
                if (sparkT <= 0f) {
                    sparkT = 0.07f
                    emitSpark(fx + (kotlin.random.Random.nextFloat() - 0.5f) * rr * 1.6f, fy)
                }
            }
        }
        // ── onda de choque do golpe forte ──
        if (attacking && sw == 3 && t >= tS) {
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

        flush(c, p)
    }
}
