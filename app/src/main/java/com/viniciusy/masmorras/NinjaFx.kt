package com.viniciusy.masmorras

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

private fun nAlpha(col: Int, a: Float): Int =
    (((a.coerceIn(0f, 1f) * 255f).toInt()) shl 24) or (col and 0x00FFFFFF)

/** Shuriken da skill Q: voa em linha reta, atravessa todos os inimigos e some no fim do alcance ou numa parede. */
class Shuriken(
    var x: Float, var y: Float,
    val dx: Float, val dy: Float,
    val damage: Float,
    val gz: Float
) {
    companion object {
        const val SPEED = 640f
        const val RANGE = 400f
    }

    var alive = true
    var traveled = 0f
    var spin = 0f
    val hit = HashSet<Enemy>()

    fun update(dt: Float) {
        val st = SPEED * dt
        x += dx * st
        y += dy * st
        traveled += st
        spin += dt * 28f
        if (traveled >= RANGE) alive = false
    }

    fun draw(c: Canvas, p: Paint, camX: Float, camY: Float) {
        val sx = x - camX
        val sy = y - camY - 18f - gz * 0f
        val fade = if (traveled > RANGE - 70f) ((RANGE - traveled) / 70f).coerceIn(0f, 1f) else 1f
        p.style = Paint.Style.FILL
        // rastro
        for (i in 1..4) {
            val t = i * 14f
            p.color = nAlpha(0xFFD8354A.toInt(), 0.28f * (1f - i / 5f) * fade)
            c.drawCircle(sx - dx * t, sy - dy * t * 0.8f, 6.5f - i * 1.1f, p)
        }
        // sombra no chão
        p.color = nAlpha(0xFF000000.toInt(), 0.22f * fade)
        c.drawOval(sx - 8f, sy + 18f - 3f, sx + 8f, sy + 18f + 3f, p)
        c.save()
        c.translate(sx, sy)
        c.scale(1f, 0.85f)
        c.rotate(spin * 57.2958f)
        for (k in 0..3) {
            val a = k * (PI.toFloat() / 2f)
            val ca = cos(a)
            val sa = sin(a)
            val path = android.graphics.Path()
            path.moveTo(ca * 15f, sa * 15f)
            path.lineTo(cos(a - 0.55f) * 5f, sin(a - 0.55f) * 5f)
            path.lineTo(0f, 0f)
            path.lineTo(cos(a + 1.0f) * 6.5f, sin(a + 1.0f) * 6.5f)
            path.close()
            p.color = nAlpha(0xFFE8EEF6.toInt(), fade)
            c.drawPath(path, p)
            val path2 = android.graphics.Path()
            path2.moveTo(ca * 15f, sa * 15f)
            path2.lineTo(cos(a + 1.0f) * 6.5f, sin(a + 1.0f) * 6.5f)
            path2.lineTo(0f, 0f)
            path2.close()
            p.color = nAlpha(0xFF7A8294.toInt(), fade)
            c.drawPath(path2, p)
        }
        p.color = nAlpha(0xFFB3202D.toInt(), fade)
        c.drawCircle(0f, 0f, 3.4f, p)
        p.color = nAlpha(0xFFFF6070.toInt(), fade)
        c.drawCircle(-0.8f, -0.8f, 1.4f, p)
        c.restore()
    }
}

/** Anel de corte da skill E: duas luas crescentes giram e se abrem ao redor de quem cortou. */
class SlashFx(val x: Float, val y: Float, val gz: Float, val radius: Float) {
    var age = 0f
    val life = 0.34f
    val done: Boolean get() = age >= life
    private val oval = RectF()

    fun update(dt: Float) {
        age += dt
    }

    fun draw(c: Canvas, p: Paint, camX: Float, camY: Float) {
        val u = (age / life).coerceIn(0f, 1f)
        val e = 1f - (1f - u) * (1f - u)
        val r = radius * (0.35f + 0.65f * e)
        val a = (1f - u) * (1f - u)
        val sx = x - camX
        val sy = y - camY + 13.2f - gz - 16f
        oval.set(sx - r, sy - r * 0.62f, sx + r, sy + r * 0.62f)
        p.style = Paint.Style.STROKE
        p.strokeCap = Paint.Cap.ROUND
        val rot = u * 300f
        p.strokeWidth = 11f * (1f - u * 0.6f)
        p.color = nAlpha(0xFFB3202D.toInt(), a * 0.9f)
        c.drawArc(oval, rot + 15f, 130f, false, p)
        c.drawArc(oval, rot + 195f, 130f, false, p)
        p.strokeWidth = 4f * (1f - u * 0.5f)
        p.color = nAlpha(0xFFFFE9E9.toInt(), a)
        c.drawArc(oval, rot + 25f, 110f, false, p)
        c.drawArc(oval, rot + 205f, 110f, false, p)
        p.strokeCap = Paint.Cap.BUTT
        p.style = Paint.Style.FILL
    }
}

/** A Sombra Viva: clone que corre até o ponto, fica parada copiando as skills e pode trocar de lugar com o herói. */
class NinjaShadow(
    x: Float, y: Float,
    private val toX: Float, private val toY: Float,
    var gz: Float,
    var yaw: Float
) : Entity(x, y, 24f, 1f, 1f) {
    companion object {
        const val DASH_T = 0.24f
        const val LIFE = 5.0f
        const val SWAP_WINDOW = 4.6f
    }

    private val fromX = x
    private val fromY = y
    var age = 0f
    var mode = 0        // 0 parada, 1 lançando, 2 girando
    var modeT = 0f
    var modeDur = 0.4f
    var swapFlash = 0f

    private var arrived = false
    val canSwap: Boolean get() = alive && age >= DASH_T

    fun setMode(m: Int, dur: Float) {
        mode = m
        modeT = 0f
        modeDur = dur
    }

    override fun update(dt: Float) {
        age += dt
        swapFlash = max(0f, swapFlash - dt * 3f)
        if (age < DASH_T) {
            val u = (age / DASH_T).coerceIn(0f, 1f)
            val e = u * u * (3f - 2f * u)
            x = fromX + (toX - fromX) * e
            y = fromY + (toY - fromY) * e
        } else if (!arrived) {
            arrived = true
            x = toX
            y = toY
        }
        if (mode != 0) {
            modeT += dt
            if (modeT >= modeDur) mode = 0
        }
        if (age >= LIFE) alive = false
    }

    private fun alphaNow(): Float {
        val fadeIn = min(1f, age / 0.12f)
        val fadeOut = ((LIFE - age) / 0.6f).coerceIn(0f, 1f)
        return 0.62f * fadeIn * fadeOut + 0.25f * swapFlash
    }

    override fun draw(canvas: Canvas, paint: Paint, camX: Float, camY: Float) {
        if (!alive) return
        val sx = x - camX
        val sy = y - camY
        val feet = sy + radius * 0.55f - gz
        val al = alphaNow()
        paint.style = Paint.Style.FILL
        paint.color = nAlpha(0xFF2A1040.toInt(), 0.35f * al)
        canvas.drawOval(sx - radius * 1.0f, feet - radius * 0.3f, sx + radius * 1.0f, feet + radius * 0.36f, paint)
        // anel roxo no chão
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 3f
        paint.color = nAlpha(0xFF9B5DE5.toInt(), 0.7f * al)
        canvas.drawOval(sx - radius * 1.1f, feet - radius * 0.36f, sx + radius * 1.1f, feet + radius * 0.42f, paint)
        paint.style = Paint.Style.FILL
        val prog = if (mode == 0) 0f else (modeT / modeDur).coerceIn(0f, 1f)
        Ninja3D.drawGhost(canvas, paint, sx, feet, HeroLook.escala, yaw, al, mode, prog)
    }

    fun faceDir(dx: Float, dy: Float) {
        if (kotlin.math.abs(dx) + kotlin.math.abs(dy) > 0.01f) yaw = atan2(dx, dy)
    }
}

/** Marca da Morte: glifo vermelho girando sobre o alvo; o anel interno fecha até o momento em que a marca explode. */
object DeathMarkFx {
    fun draw(c: Canvas, p: Paint, camX: Float, camY: Float, e: Enemy, left: Float, total: Float, time: Float) {
        val sx = e.x - camX
        val base = e.y - camY + e.radius * 0.55f - e.alt
        val u = (1f - left / total).coerceIn(0f, 1f)
        val pulse = 0.5f + 0.5f * sin(time * (6f + 14f * u))
        // anel no chão
        val rx = e.radius * 1.5f
        p.style = Paint.Style.STROKE
        p.strokeWidth = 3.5f
        p.color = nAlpha(0xFFFF3B4A.toInt(), 0.55f + 0.35f * pulse)
        c.drawOval(sx - rx, base - rx * 0.5f, sx + rx, base + rx * 0.5f, p)
        // anel que fecha
        val rr = rx * (1.25f - 0.95f * u)
        p.strokeWidth = 2.5f
        p.color = nAlpha(0xFFFFB4B4.toInt(), 0.8f)
        c.drawOval(sx - rr, base - rr * 0.5f, sx + rr, base + rr * 0.5f, p)
        // losango flutuante sobre a cabeça
        val hy = base - e.radius * 2.9f - 10f
        c.save()
        c.translate(sx, hy)
        c.scale(1f + 0.12f * pulse, 1f + 0.12f * pulse)
        p.style = Paint.Style.FILL
        val path = android.graphics.Path()
        path.moveTo(0f, -13f)
        path.lineTo(10f, 0f)
        path.lineTo(0f, 13f)
        path.lineTo(-10f, 0f)
        path.close()
        p.color = nAlpha(0xFF14080C.toInt(), 0.9f)
        c.drawPath(path, p)
        c.scale(0.72f, 0.72f)
        p.color = nAlpha(0xFFFF3B4A.toInt(), 1f)
        c.drawPath(path, p)
        p.color = nAlpha(0xFF14080C.toInt(), 1f)
        c.drawCircle(0f, 2f, 2.6f, p)
        c.restore()
        // contagem em arcos ao redor do losango
        p.style = Paint.Style.STROKE
        p.strokeWidth = 3f
        p.strokeCap = Paint.Cap.ROUND
        p.color = nAlpha(0xFFFF6070.toInt(), 0.9f)
        val ov = RectF(sx - 17f, hy - 17f, sx + 17f, hy + 17f)
        c.drawArc(ov, -90f, 360f * (1f - u), false, p)
        p.strokeCap = Paint.Cap.BUTT
        p.style = Paint.Style.FILL
    }
}

private fun swRnd(i: Int, k: Int): Float = ((((i + 1) * 73856093) xor ((k + 3) * 19349663)) and 0xFFFF) / 65535f

/**
 * Troca de lugar com a Sombra Viva: o ninja se desfaz em fumaça negra no ponto de partida (anel que implode,
 * pilares de sombra, silhueta que se dissolve), uma fita escura com miolo vermelho rasga o caminho e, no destino,
 * um anel de runas explode e dois cortes cruzados em vermelho marcam a chegada.
 */
class SwapFx(
    val x1: Float, val y1: Float, val gz1: Float,   // de onde o ninja saiu
    val x2: Float, val y2: Float, val gz2: Float,   // para onde ele foi
    val yaw: Float
) {
    companion object {
        const val LIFE = 0.8f
        private val VIOLET = 0xFF8B3DFF.toInt()
        private val DEEP = 0xFF2A0F4A.toInt()
        private val INK = 0xFF0A0314.toInt()
        private val BLOOD = 0xFFD8203A.toInt()
        private val PALE = 0xFFFFE9EE.toInt()
    }

    var age = 0f
    val done: Boolean get() = age >= LIFE
    private val path = android.graphics.Path()
    private val oval = RectF()

    fun update(dt: Float) {
        age += dt
    }

    private fun ring(c: Canvas, p: Paint, cx: Float, cy: Float, r: Float, col: Int, a: Float, w: Float, spokes: Int, rot: Float) {
        if (a <= 0.01f || r <= 1f) return
        p.style = Paint.Style.STROKE
        p.strokeWidth = w
        p.color = nAlpha(col, a)
        oval.set(cx - r, cy - r * 0.5f, cx + r, cy + r * 0.5f)
        c.drawOval(oval, p)
        // runas: marcas que giram na borda do anel
        p.strokeWidth = max(1.5f, w * 0.7f)
        for (k in 0 until spokes) {
            val an = rot + k * (2f * PI.toFloat() / spokes)
            val ca = cos(an)
            val sa = sin(an)
            c.drawLine(cx + ca * r * 0.82f, cy + sa * r * 0.41f, cx + ca * r * 1.12f, cy + sa * r * 0.56f, p)
        }
    }

    private fun pillar(c: Canvas, p: Paint, sx: Float, feet: Float, u: Float, seed: Int, h0: Float, spread: Float) {
        // colunas de sombra que sobem em línguas irregulares e afinam até sumir
        p.style = Paint.Style.FILL
        val rise = 1f - (1f - u) * (1f - u)
        val fade = (1f - u)
        for (i in 0 until 9) {
            val ox = (swRnd(seed, i) - 0.5f) * spread
            val hh = h0 * (0.45f + 0.75f * swRnd(seed + 5, i)) * rise
            val bw = (7f + 9f * swRnd(seed + 9, i)) * (1f - 0.55f * u)
            val sway = sin(age * 9f + i * 1.7f) * 4f * rise
            path.reset()
            path.moveTo(sx + ox - bw, feet)
            path.lineTo(sx + ox - bw * 0.35f + sway * 0.5f, feet - hh * 0.55f)
            path.lineTo(sx + ox + sway, feet - hh)
            path.lineTo(sx + ox + bw * 0.35f + sway * 0.5f, feet - hh * 0.55f)
            path.lineTo(sx + ox + bw, feet)
            path.close()
            p.color = nAlpha(if (i % 3 == 0) DEEP else INK, 0.8f * fade)
            c.drawPath(path, p)
            if (i % 4 == 0) {
                p.color = nAlpha(VIOLET, 0.55f * fade)
                c.drawCircle(sx + ox + sway, feet - hh, 3.5f * (1f - u * 0.5f), p)
            }
        }
    }

    private fun motes(c: Canvas, p: Paint, sx: Float, feet: Float, u: Float, seed: Int, up: Boolean) {
        // fragmentos de sombra: sobem e se espalham (partida) ou se afastam em estouro (chegada)
        p.style = Paint.Style.FILL
        for (i in 0 until 16) {
            val ang = swRnd(seed, i) * 2f * PI.toFloat()
            val spd = 40f + 90f * swRnd(seed + 3, i)
            val t = u * 0.8f
            val px = sx + cos(ang) * spd * t
            val py = if (up) feet - 20f - spd * 0.9f * t - 30f * swRnd(seed + 6, i) * t
                     else feet - 14f + sin(ang) * spd * 0.5f * t - 70f * t * t
            val a = (1f - u) * (0.5f + 0.5f * swRnd(seed + 8, i))
            val sz = 2.6f + 4f * swRnd(seed + 2, i)
            p.color = nAlpha(if (i % 3 == 0) BLOOD else if (i % 3 == 1) VIOLET else INK, a)
            c.drawRect(px - sz, py - sz, px + sz, py + sz, p)
        }
    }

    fun draw(c: Canvas, p: Paint, camX: Float, camY: Float) {
        val u = (age / LIFE).coerceIn(0f, 1f)
        val s1x = x1 - camX
        val s1f = y1 - camY + 13.2f - gz1
        val s2x = x2 - camX
        val s2f = y2 - camY + 13.2f - gz2

        // ── partida: silhueta que se dissolve, anel que implode, pilares ──
        val ua = (u / 0.7f).coerceIn(0f, 1f)
        if (ua < 1f) {
            val al = (1f - ua) * (1f - ua)
            Ninja3D.drawGhost(c, p, s1x, s1f, HeroLook.escala, yaw, 0.7f * al, 0, 0f)
            ring(c, p, s1x, s1f, 78f * (1f - ua) + 10f, VIOLET, 0.9f * (1f - ua), 7f * (1f - ua) + 1.5f, 8, -ua * 6f)
            ring(c, p, s1x, s1f, 46f * (1f - ua) + 6f, BLOOD, 0.8f * (1f - ua), 4f, 6, ua * 8f)
            pillar(c, p, s1x, s1f, ua, 11, 120f, 56f)
            motes(c, p, s1x, s1f, ua, 31, true)
        }

        // ── caminho: fita escura com miolo vermelho que corre de um ponto ao outro ──
        val ub = (u / 0.42f).coerceIn(0f, 1f)
        val tail = ((u - 0.18f) / 0.5f).coerceIn(0f, 1f)
        if (tail < 1f) {
            val ax = s1x
            val ay = s1f - 22f
            val bx = s2x
            val by = s2f - 22f
            val dx = bx - ax
            val dy = by - ay
            val len = hypot(dx, dy).coerceAtLeast(1f)
            val nx = -dy / len
            val ny = dx / len
            val head = 1f - (1f - ub) * (1f - ub)
            val n = 16
            val i0 = (tail * n).toInt()
            val i1 = (head * n).toInt().coerceAtLeast(i0 + 1).coerceAtMost(n)
            val fade = 1f - tail
            p.strokeCap = Paint.Cap.ROUND
            p.style = Paint.Style.STROKE
            for (pass in 0..2) {
                var px = 0f
                var py = 0f
                for (i in i0..i1) {
                    val f = i.toFloat() / n
                    val bell = sin(f * PI.toFloat())
                    val wob = sin(f * 14f + age * 40f) * 9f * bell + (swRnd(i, 77) - 0.5f) * 6f * bell
                    val cx = ax + dx * f + nx * wob
                    val cy = ay + dy * f + ny * wob - 26f * bell
                    if (i > i0) {
                        when (pass) {
                            0 -> { p.strokeWidth = 5f + 24f * bell; p.color = nAlpha(INK, 0.85f * fade) }
                            1 -> { p.strokeWidth = 3f + 10f * bell; p.color = nAlpha(BLOOD, 0.95f * fade) }
                            else -> { p.strokeWidth = 1.2f + 2.5f * bell; p.color = nAlpha(PALE, fade) }
                        }
                        c.drawLine(px, py, cx, cy, p)
                    }
                    px = cx
                    py = cy
                }
            }
            p.strokeCap = Paint.Cap.BUTT
            p.style = Paint.Style.FILL
        }

        // ── chegada: anel de runas que explode, pilares, cortes cruzados ──
        val uc = ((u - 0.22f) / 0.78f).coerceIn(0f, 1f)
        if (uc > 0f && uc < 1f) {
            val e = 1f - (1f - uc) * (1f - uc)
            ring(c, p, s2x, s2f, 20f + 92f * e, VIOLET, 0.95f * (1f - uc), 8f * (1f - uc) + 1.5f, 10, uc * 5f)
            ring(c, p, s2x, s2f, 12f + 60f * e, BLOOD, 0.85f * (1f - uc), 4.5f, 6, -uc * 7f)
            pillar(c, p, s2x, s2f, uc, 23, 150f, 64f)
            motes(c, p, s2x, s2f, uc, 47, false)
            // dois cortes cruzados no instante da chegada
            val ux = (uc / 0.45f).coerceIn(0f, 1f)
            if (ux < 1f) {
                val len = 62f * (1f - (1f - ux) * (1f - ux))
                val a = 1f - ux
                p.style = Paint.Style.STROKE
                p.strokeCap = Paint.Cap.ROUND
                val cy = s2f - 26f
                for (k in 0..1) {
                    val sg = if (k == 0) 1f else -1f
                    p.strokeWidth = 9f * a + 2f
                    p.color = nAlpha(BLOOD, 0.9f * a)
                    c.drawLine(s2x - len * sg, cy - len * 0.55f, s2x + len * sg, cy + len * 0.55f, p)
                    p.strokeWidth = 3f * a + 1f
                    p.color = nAlpha(PALE, a)
                    c.drawLine(s2x - len * sg, cy - len * 0.55f, s2x + len * sg, cy + len * 0.55f, p)
                }
                p.strokeCap = Paint.Cap.BUTT
                p.style = Paint.Style.FILL
            }
        }
        p.style = Paint.Style.FILL
    }
}
