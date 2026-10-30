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
 * O Guerreiro é um esqueleto de partes articuladas: pernas, tronco, cabeça, braços,
 * escudo, espada e capa. Cada cubo tem 3 faces visíveis com iluminação própria.
 */
object Warrior3D {
    private val V = VoxelArt

    private const val MAX = 160
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
    private val PANTS = 0xFF3B4650.toInt()
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

    // estado de animação
    private var yaw = 0f
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

    private fun leg(side: Float, a: Float, hipY: Float, tuck: Float) {
        push()
        translate(side * 3.3f, hipY, 0f)
        rotX(a)
        cube(0f, -2.8f, 0f, 2.7f, 3.0f, 2.8f, PANTS)
        cube(0f, -3.5f, 0f, 2.95f, 2.1f, 3.05f, STEEL_D)
        cube(0f, -0.9f, 0f, 3.0f, 0.5f, 3.1f, GOLD)
        translate(0f, -5.6f, 0f)
        rotX(max(0f, a) * 1.0f + 0.08f + tuck * 1.5f)
        cube(0f, -2.6f, 0f, 2.55f, 2.8f, 2.65f, STEEL)
        cube(0f, 0.1f, 2.0f, 2.1f, 1.5f, 0.9f, GOLD)
        translate(0f, -5.4f, 0f)
        rotX(-max(0f, a) * 0.5f)
        cube(0f, 0f, 1.1f, 3.0f, 1.6f, 3.9f, LEATHER)
        cube(0f, -1.35f, 1.2f, 3.1f, 0.25f, 4.0f, LEATHER_D)
        cube(0f, 1.0f, 3.9f, 2.4f, 0.6f, 0.9f, LEATHER_L)
        pop()
    }

    private fun pauldron(side: Float) {
        cube(side * 0.9f, 1.4f, 0f, 3.9f, 1.4f, 3.7f, STEEL_L)
        cube(side * 1.6f, 0f, 0f, 3.3f, 1.2f, 3.4f, STEEL)
        cube(side * 0.9f, 2.9f, 0f, 3.95f, 0.35f, 3.75f, GOLD)
        cube(side * 2.2f, 3.7f, 0f, 0.7f, 1.0f, 0.7f, GOLD)
    }

    private fun armBase(el: Float) {
        // coxa-braço + antebraço (assume que o ombro já foi rotacionado)
        cube(0f, -2.8f, 0f, 2.2f, 3.0f, 2.2f, STEEL_D)
        cube(0f, -0.4f, 0f, 2.35f, 0.4f, 2.35f, GOLD)
        translate(0f, -5.8f, 0f)
        rotX(el)
        cube(0f, -2.6f, 0f, 2.35f, 2.8f, 2.35f, STEEL)
        cube(0f, -0.3f, 0f, 2.5f, 0.45f, 2.5f, GOLD)
        cube(0f, -4.9f, 0f, 2.5f, 0.4f, 2.5f, GOLD)
        cube(0f, -5.9f, 0f, 1.9f, 1.5f, 1.9f, LEATHER_L)
    }

    private fun rightArm(sh: Float, shZ: Float, el: Float, wrist: Float) {
        push()
        translate(-8.7f, 13.2f, 0f)
        pauldron(-1f)
        rotZ(shZ)
        rotX(sh)
        armBase(el)
        // espada na mão direita
        translate(0f, -5.9f, 0f)
        rotX(1.9f + wrist)
        cube(0f, 0.4f, 0f, 0.9f, 2.2f, 0.9f, LEATHER)
        cube(0f, -2.1f, 0f, 1.25f, 0.8f, 1.25f, GOLD)
        cube(0f, 3.2f, 0f, 4.3f, 0.7f, 1.4f, GOLD)
        cube(0f, 3.2f, 1.5f, 0.8f, 0.6f, 0.3f, RED)
        cube(0f, 14.6f, 0f, 0.8f, 10.9f, 1.9f, BLADE)
        cube(0f, 14.6f, 1.95f, 0.45f, 10.4f, 0.2f, 0xFFFFFFFF.toInt(), true)
        cube(0f, 14.6f, 0f, 0.9f, 8.6f, 0.5f, STEEL)
        cube(0f, 26.2f, 0.4f, 0.65f, 0.9f, 1.2f, BLADE)
        projectTo(0f, 26.5f, 0.5f, 0)
        projectTo(0f, 12f, 0.5f, 1)
        pop()
    }

    private fun leftArm(sh: Float, shZ: Float, el: Float) {
        push()
        translate(8.7f, 13.2f, 0f)
        pauldron(1f)
        rotZ(shZ)
        rotX(sh)
        armBase(el)
        // escudo: frame "em pé" (cancela a inclinação do braço)
        translate(0f, -5.6f, 0f)
        rotX(-(sh + el))
        cube(0.2f, 0.6f, 0.5f, 4.7f, 6.0f, 1.0f, STEEL_D)
        cube(0.2f, 0.6f, 1.1f, 3.9f, 5.2f, 0.7f, RED)
        cube(0.2f, 6.7f, 1.1f, 4.0f, 0.3f, 0.8f, GOLD)
        cube(0.2f, -5.5f, 0.5f, 2.9f, 0.9f, 1.0f, STEEL_D)
        cube(0.2f, -5.5f, 1.1f, 2.2f, 0.7f, 0.7f, RED)
        cube(0.2f, -7.0f, 0.5f, 1.4f, 0.6f, 1.0f, STEEL_D)
        cube(0.2f, 0.6f, 1.85f, 0.55f, 4.2f, 0.1f, CREAM)
        cube(0.2f, 1.6f, 1.85f, 3.2f, 0.55f, 0.1f, CREAM)
        cube(0.2f, 0.9f, 2.05f, 1.3f, 1.3f, 0.45f, GOLD)
        pop()
    }

    private fun head(turn: Float, tilt: Float) {
        push()
        translate(0f, 14.0f, 0f)
        rotY(turn)
        rotX(tilt)
        cube(0f, 5.2f, 0f, 5.2f, 5.2f, 5.0f, SKIN)
        // rosto
        cube(-2.0f, 4.7f, 5.05f, 0.95f, 1.1f, 0.12f, EYE)
        cube(2.0f, 4.7f, 5.05f, 0.95f, 1.1f, 0.12f, EYE)
        cube(-2.0f, 5.35f, 5.1f, 0.5f, 0.45f, 0.1f, 0xFFFFFFFF.toInt())
        cube(2.0f, 5.35f, 5.1f, 0.5f, 0.45f, 0.1f, 0xFFFFFFFF.toInt())
        cube(0f, 3.2f, 5.3f, 0.6f, 0.7f, 0.3f, SKIN_D)
        cube(0f, 1.6f, 5.05f, 1.3f, 0.3f, 0.12f, SKIN_D)
        // elmo
        cube(0f, 8.7f, 0f, 5.7f, 2.7f, 5.6f, STEEL)
        cube(0f, 6.4f, 0f, 5.8f, 0.35f, 5.7f, GOLD)
        cube(0f, 6.9f, 5.5f, 5.9f, 0.6f, 0.5f, STEEL_D)
        cube(0f, 4.6f, 5.5f, 0.55f, 2.3f, 0.45f, STEEL)
        cube(-5.5f, 4.4f, 0.5f, 0.5f, 2.4f, 3.6f, STEEL)
        cube(5.5f, 4.4f, 0.5f, 0.5f, 2.4f, 3.6f, STEEL)
        cube(0f, 11.7f, 0f, 0.9f, 0.9f, 5.2f, STEEL_D)
        // penacho vermelho
        cube(0f, 13.2f, -0.5f, 0.8f, 1.5f, 4.2f, RED)
        cube(0f, 12.4f, -5.4f, 0.8f, 2.0f, 1.4f, RED_D)
        cube(0f, 12.9f, 4.4f, 0.8f, 1.0f, 1.2f, RED)
        pop()
    }

    // ───────────── desenho principal ─────────────

    fun draw(
        c: Canvas, p: Paint, fx: Float, fy: Float, s: Float, phase: Float,
        moving: Boolean, faceX: Float, faceY: Float, attack: Float
    ) {
        val now = System.nanoTime()
        val first = lastNs == 0L
        val dt = if (first) 0.016f else ((now - lastNs) / 1_000_000_000f).coerceIn(0f, 0.1f)
        lastNs = now
        clock += dt

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

        // fase do golpe: 0..1 (negativo = sem ataque)
        val t = if (attack > 0.001f) 1f - attack else -1f
        val attacking = t >= 0f
        val rolling = rollP >= 0f
        val air = inAir && !rolling
        val tuck = if (rolling) 1f else if (air) (if (airVz > 0f) 0.55f else 0.25f) else 0f
        val mv = if (rolling || air) 0f else if (attacking) blend * 0.4f else blend
        val walk = sin(phase)

        // poses da espada (braço direito)
        val idleSh = -0.5f + walk * 0.3f * mv
        val idleZ = -0.12f
        val idleEl = -0.8f
        var sh = idleSh
        var shZ = idleZ
        var el = idleEl
        var tw = 0f
        var lunge = 0f
        var wrist = 0f
        if (attacking) {
            val wSh = -2.7f
            val wZ = 0.55f
            val wEl = -0.9f
            val wTw = -0.55f
            val eSh = -0.55f
            val eZ = 0.35f
            val eEl = -0.35f
            val eTw = 0.55f
            if (t < 0.26f) {
                val u = ease(t / 0.26f)
                sh = lerp(idleSh, wSh, u)
                shZ = lerp(idleZ, wZ, u)
                el = lerp(idleEl, wEl, u)
                tw = lerp(0f, wTw, u)
                wrist = lerp(0f, -0.3f, u)
            } else if (t < 0.46f) {
                var u = (t - 0.26f) / 0.2f
                u = 1f - (1f - u) * (1f - u)
                sh = lerp(wSh, eSh, u)
                shZ = lerp(wZ, eZ, u)
                el = lerp(wEl, eEl, u)
                tw = lerp(wTw, eTw, u)
                wrist = lerp(-0.3f, 0.35f, u)
            } else {
                val u = ease((t - 0.46f) / 0.54f)
                sh = lerp(eSh, idleSh, u)
                shZ = lerp(eZ, idleZ, u)
                el = lerp(eEl, idleEl, u)
                tw = lerp(eTw, 0f, u)
                wrist = lerp(0.35f, 0f, u)
            }
            if (t in 0.2f..0.7f) {
                lunge = sin(((t - 0.2f) / 0.5f) * PI.toFloat()) * 3f
            }
        }

        val bob = abs(walk) * 1.4f * mv + sin(clock * 2.4f) * 0.35f * (1f - mv)
        val lean = 0.10f * mv + lunge * 0.04f
        if (rolling) {
            sh = -1.2f
            shZ = 0.2f
            el = -1.9f
            wrist = 0.3f
        } else if (air && !attacking) {
            sh = -2.0f
            shZ = 0.5f
            el = -0.6f
        }
        var shieldSh = (if (attacking) -0.45f else -0.25f) - walk * 0.15f * mv
        if (rolling) shieldSh = -1.0f else if (air) shieldSh = -1.1f

        rs = s * PX
        ox = fx
        oy = fy
        n = 0
        top = 0
        identity()
        rotY(yaw)
        translate(0f, 0f, lunge)
        if (rolling) {
            val ang = ease(rollP) * TWO_PI
            val lift = 7f * sin(rollP * PI.toFloat())
            translate(0f, 11f + lift, 0f)
            rotX(ang)
            translate(0f, -11f, 0f)
        }

        // pernas
        leg(1f, walk * 0.8f * mv - 1.0f * tuck, 12.5f + bob, tuck)
        leg(-1f, -walk * 0.8f * mv - 1.0f * tuck, 12.5f + bob, tuck)

        // tronco
        push()
        translate(0f, 12.5f + bob, 0f)
        rotX(lean)
        rotY(tw)
        cube(0f, 1.2f, 0f, 6.6f, 1.6f, 3.8f, LEATHER)
        cube(0f, 1.4f, 3.9f, 1.7f, 1.3f, 0.5f, GOLD)
        cube(0f, 8.4f, 0f, 6.8f, 5.6f, 3.9f, STEEL)
        cube(0f, 9.0f, 3.9f, 5.2f, 4.2f, 0.6f, STEEL_L)
        cube(0f, 13.7f, 3.7f, 6.2f, 0.5f, 0.7f, GOLD)
        cube(0f, 9.2f, 4.55f, 0.9f, 3.0f, 0.15f, RED)
        cube(0f, 9.8f, 4.55f, 2.4f, 0.8f, 0.15f, RED)
        cube(0f, 9.0f, -3.9f, 5.4f, 4.4f, 0.5f, STEEL_D)
        cube(0f, -1.6f, 3.7f, 3.2f, 3.4f, 0.5f, RED)
        cube(0f, -4.9f, 3.7f, 3.2f, 0.4f, 0.55f, GOLD)
        cube(-5.4f, -0.8f, 0f, 1.3f, 2.4f, 3.6f, STEEL_D)
        cube(5.4f, -0.8f, 0f, 1.3f, 2.4f, 3.6f, STEEL_D)

        // capa
        push()
        translate(0f, 13.4f, -4.2f)
        val cape1 = 0.14f + 0.28f * mv + 0.12f * sin(clock * 3f + phase * 2f) * (0.4f + mv) + lunge * 0.05f
        rotX(cape1)
        cube(0f, -5.8f, -0.4f, 5.9f, 6.0f, 0.45f, RED_D)
        cube(0f, 0.9f, 0.1f, 6.6f, 1.0f, 1.2f, STEEL_D)
        translate(0f, -11.8f, 0f)
        rotX(0.1f + 0.3f * mv * (0.5f + 0.5f * sin(clock * 4f + phase * 2f)))
        cube(0f, -3.0f, -0.4f, 6.1f, 3.2f, 0.45f, RED)
        cube(0f, -6.0f, -0.4f, 6.1f, 0.4f, 0.55f, GOLD)
        pop()

        head(-tw * 0.6f, -lean * 0.8f)
        rightArm(sh, shZ, el, wrist)
        leftArm(shieldSh, 0.12f, -1.5f)
        pop()

        flush(c, p)

        // rastro dourado da espada
        if (!attacking) {
            trN = 0
        } else {
            if (t in 0.18f..0.9f) {
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
                p.color = (((a * 0.6f * fade * 255f).toInt()) shl 24) or 0x00FFE9A8
                c.drawPath(path, p)
                p.style = Paint.Style.STROKE
                p.strokeWidth = max(1.2f, rs * 0.9f)
                p.strokeCap = Paint.Cap.ROUND
                p.color = (((a * 0.9f * fade * 255f).toInt()) shl 24) or 0x00FFFFFF
                c.drawLine(trX[i - 1], trY[i - 1], trX[i], trY[i], p)
            }
            p.style = Paint.Style.FILL
            p.strokeCap = Paint.Cap.BUTT
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
