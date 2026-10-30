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
 * Troca de lugar com a Sombra Viva, só de fumaça negra: o ninja se desmancha numa nuvem escura no ponto de
 * partida, um rastro de fumaça corre até a sombra e, no destino, uma nuvem se abre e revela o ninja.
 */
class SwapFx(
    val x1: Float, val y1: Float, val gz1: Float,   // de onde o ninja saiu
    val x2: Float, val y2: Float, val gz2: Float,   // para onde ele foi
    val yaw: Float
) {
    companion object {
        const val LIFE = 0.95f
        private val INK = 0xFF07030D.toInt()
        private val DUSK = 0xFF1B0E2C.toInt()
        private val VIOLET = 0xFF4A2380.toInt()
        private val EMBER = 0xFFD8203A.toInt()
    }

    var age = 0f
    val done: Boolean get() = age >= LIFE

    fun update(dt: Float) {
        age += dt
    }

    /** Fumaça macia: três círculos concêntricos com opacidade crescente para o centro. */
    private fun puff(c: Canvas, p: Paint, x: Float, y: Float, r: Float, col: Int, a: Float) {
        if (a <= 0.01f || r < 1f) return
        p.style = Paint.Style.FILL
        p.color = nAlpha(col, a * 0.30f)
        c.drawCircle(x, y, r, p)
        p.color = nAlpha(col, a * 0.40f)
        c.drawCircle(x - r * 0.05f, y - r * 0.08f, r * 0.72f, p)
        p.color = nAlpha(col, a * 0.50f)
        c.drawCircle(x - r * 0.1f, y - r * 0.15f, r * 0.45f, p)
    }

    private fun cloud(c: Canvas, p: Paint, sx: Float, feet: Float, u: Float, seed: Int, count: Int, burst: Boolean) {
        for (i in 0 until count) {
            val delay = swRnd(seed, i) * 0.28f
            val t = ((u - delay) / (1f - 0.28f)).coerceIn(0f, 1f)
            if (t <= 0f || t >= 1f) continue
            val ang = swRnd(seed + 4, i) * 2f * PI.toFloat()
            val dist = (if (burst) 16f + 62f else 10f + 34f) * swRnd(seed + 7, i)
            val e = 1f - (1f - t) * (1f - t)
            val px = sx + cos(ang) * dist * (0.3f + 0.9f * e) + sin(u * 5f + i) * 5f * t
            val py = feet - 8f + sin(ang) * dist * 0.32f * e - (26f + 78f * swRnd(seed + 2, i)) * e
            val r = (9f + 22f * swRnd(seed + 9, i)) * (0.55f + 0.9f * e)
            val a = min(1f, t * 7f) * (1f - t) * (1f - t * 0.3f)
            val col = when (i % 5) { 0 -> DUSK; 1 -> VIOLET; else -> INK }
            puff(c, p, px, py, r, col, a * 0.95f)
        }
    }

    fun draw(c: Canvas, p: Paint, camX: Float, camY: Float) {
        val u = (age / LIFE).coerceIn(0f, 1f)
        val s1x = x1 - camX
        val s1f = y1 - camY + 13.2f - gz1
        val s2x = x2 - camX
        val s2f = y2 - camY + 13.2f - gz2

        // partida: a silhueta se desmancha dentro da fumaça
        val ua = (u / 0.75f).coerceIn(0f, 1f)
        if (ua < 1f) {
            Ninja3D.drawGhost(c, p, s1x, s1f, HeroLook.escala, yaw, 0.6f * (1f - ua) * (1f - ua), 0, 0f)
            // névoa baixa no chão
            p.style = Paint.Style.FILL
            p.color = nAlpha(INK, 0.35f * (1f - ua))
            c.drawOval(s1x - 44f, s1f - 14f, s1x + 44f, s1f + 16f, p)
            cloud(c, p, s1x, s1f, ua, 11, 20, false)
        }

        // rastro: fumaça corre do ponto de partida até o destino
        val head = ((u / 0.40f).coerceIn(0f, 1f)).let { 1f - (1f - it) * (1f - it) }
        val ax = s1x
        val ay = s1f - 24f
        val bx = s2x
        val by = s2f - 24f
        val n = 16
        for (i in 0..n) {
            val f = i.toFloat() / n
            if (f > head) break
            val born = f * 0.40f
            val t = ((u - born) / 0.45f).coerceIn(0f, 1f)
            if (t >= 1f) continue
            val bell = sin(f * PI.toFloat())
            val wob = sin(f * 9f + age * 22f) * 7f * bell
            val px = ax + (bx - ax) * f + wob * 0.5f
            val py = ay + (by - ay) * f - 10f * bell - 22f * t + wob
            val a = min(1f, t * 8f) * (1f - t)
            puff(c, p, px, py, 11f + 12f * bell + 8f * t, if (i % 3 == 0) DUSK else INK, a)
            if (i % 4 == 2) {
                p.color = nAlpha(EMBER, a * 0.9f)
                c.drawRect(px - 2.2f, py - 2.2f - 8f * t, px + 2.2f, py + 2.2f - 8f * t, p)
            }
        }

        // chegada: a nuvem se abre e revela o ninja
        val uc = ((u - 0.2f) / 0.8f).coerceIn(0f, 1f)
        if (uc > 0f && uc < 1f) {
            p.style = Paint.Style.FILL
            p.color = nAlpha(INK, 0.3f * (1f - uc))
            c.drawOval(s2x - 52f, s2f - 16f, s2x + 52f, s2f + 18f, p)
            cloud(c, p, s2x, s2f, uc, 23, 24, true)
        }
        p.style = Paint.Style.FILL
    }
}
