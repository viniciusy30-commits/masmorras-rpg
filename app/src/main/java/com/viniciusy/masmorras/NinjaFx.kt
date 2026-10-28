package com.viniciusy.masmorras

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
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
        const val LIFE = 5.2f
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
    val canSwap: Boolean get() = alive && age >= DASH_T && age < SWAP_WINDOW

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
