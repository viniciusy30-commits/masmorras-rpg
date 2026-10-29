package com.viniciusy.masmorras

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/** Cores e formas compartilhadas pelos efeitos do Ichigo. */
private object IchiPal {
    const val DARK = 0xFF1A0610.toInt()
    const val RED = 0xFFC8102E.toInt()
    const val RED_L = 0xFFFF4B5C.toInt()
    const val WHITE = 0xFFFFFFFF.toInt()
    const val BONE = 0xFFF2EFE8.toInt()
    val path = Path()
    val rect = RectF()

    fun alpha(c: Int, a: Float): Int = (((a.coerceIn(0f, 1f) * 255f).toInt()) shl 24) or (c and 0x00FFFFFF)

    /** Meia-lua (lâmina de energia) com a convexidade voltada para [ang] (radianos, na tela). */
    fun crescent(c: Canvas, p: Paint, cx: Float, cy: Float, r: Float, ang: Float, sweep: Float, thick: Float, color: Int, a: Float) {
        val sq = 0.78f
        val start = Math.toDegrees(ang.toDouble()).toFloat() - sweep / 2f
        path.reset()
        rect.set(cx - r, cy - r * sq, cx + r, cy + r * sq)
        path.arcTo(rect, start, sweep, true)
        val sx = -cos(ang) * r * thick
        val sy = -sin(ang) * r * thick * sq
        rect.set(cx - r * 0.94f + sx, cy - r * sq * 0.94f + sy, cx + r * 0.94f + sx, cy + r * sq * 0.94f + sy)
        path.arcTo(rect, start + sweep, -sweep, false)
        path.close()
        p.style = Paint.Style.FILL
        p.color = alpha(color, a)
        c.drawPath(path, p)
    }
}

/** Getsuga Tenshou: meia-lua negra de contorno vermelho que atravessa os inimigos. */
class GetsugaWave(
    var x: Float, var y: Float, val dx: Float, val dy: Float,
    val dmg: Float, val lift: Float
) {
    var alive = true
    var age = 0f
    var life = 1.0f
    val speed = 470f
    val hit = HashSet<Enemy>()

    fun update(dt: Float) {
        x += dx * speed * dt
        y += dy * speed * dt
        age += dt
        life -= dt
        if (life <= 0f) alive = false
    }

    fun draw(c: Canvas, p: Paint, camX: Float, camY: Float) {
        if (!alive) return
        val sx = x - camX
        val sy = y - camY - lift
        val ang = atan2(dy * 0.75f, dx)
        val grow = min(1f, age / 0.12f)
        val fade = min(1f, life / 0.25f)
        val r = (22f + 6f * grow) * (0.6f + 0.4f * grow)
        // rastro
        for (i in 3 downTo 1) {
            val bx = sx - dx * 16f * i
            val by = sy - dy * 12f * i
            IchiPal.crescent(c, p, bx, by, r * (1f - 0.1f * i), ang, 110f, 0.55f, IchiPal.DARK, 0.20f * fade)
        }
        IchiPal.crescent(c, p, sx, sy, r * 1.25f, ang, 125f, 0.5f, IchiPal.RED, 0.55f * fade)
        IchiPal.crescent(c, p, sx, sy, r * 1.08f, ang, 120f, 0.5f, IchiPal.DARK, 0.95f * fade)
        IchiPal.crescent(c, p, sx, sy, r * 0.86f, ang, 105f, 0.45f, IchiPal.RED_L, 0.9f * fade)
        IchiPal.crescent(c, p, sx, sy, r * 0.62f, ang, 85f, 0.4f, IchiPal.WHITE, 0.85f * fade)
    }
}

/**
 * Efeitos curtos do Ichigo. kind: 0 = feixe (Cero), 1 = anel no chão, 2 = rastro de Shunpo,
 * 3 = onda escura que preenche (Mugetsu / transformação), 4 = clarão de corte.
 */
class IchiFx(
    val kind: Int, val x0: Float, val y0: Float, val x1: Float, val y1: Float,
    val maxLife: Float, val r: Float, val col: Int
) {
    var life = maxLife
    val done: Boolean get() = life <= 0f

    fun update(dt: Float) {
        life -= dt
    }

    private fun ease(t: Float): Float {
        val v = t.coerceIn(0f, 1f)
        return v * v * (3f - 2f * v)
    }

    fun draw(c: Canvas, p: Paint, camX: Float, camY: Float) {
        if (life <= 0f) return
        val a = (life / maxLife).coerceIn(0f, 1f)
        val u = 1f - a
        val ax = x0 - camX
        val ay = y0 - camY
        val bx = x1 - camX
        val by = y1 - camY
        when (kind) {
            0 -> {
                val w = r * (0.30f + 0.70f * a) * (1f + 0.07f * sin(life * 70f))
                p.style = Paint.Style.STROKE
                p.strokeCap = Paint.Cap.ROUND
                p.strokeWidth = w * 2.1f
                p.color = IchiPal.alpha(IchiPal.DARK, 0.55f * a)
                c.drawLine(ax, ay, bx, by, p)
                p.strokeWidth = w * 1.45f
                p.color = IchiPal.alpha(col, 0.85f * a)
                c.drawLine(ax, ay, bx, by, p)
                p.strokeWidth = w * 0.9f
                p.color = IchiPal.alpha(IchiPal.RED_L, 0.9f * a)
                c.drawLine(ax, ay, bx, by, p)
                p.strokeWidth = w * 0.45f
                p.color = IchiPal.alpha(IchiPal.WHITE, 0.95f * a)
                c.drawLine(ax, ay, bx, by, p)
                p.strokeCap = Paint.Cap.BUTT
                p.style = Paint.Style.FILL
                p.color = IchiPal.alpha(IchiPal.WHITE, 0.8f * a)
                c.drawCircle(ax, ay, w * 1.1f, p)
            }
            1 -> {
                val rad = r * (0.2f + 0.8f * ease(u))
                p.style = Paint.Style.STROKE
                p.strokeWidth = 2f + 7f * a
                p.color = IchiPal.alpha(col, 0.85f * a)
                c.drawOval(ax - rad, ay - rad * 0.62f, ax + rad, ay + rad * 0.62f, p)
                p.strokeWidth = 2f + 3f * a
                p.color = IchiPal.alpha(IchiPal.DARK, 0.7f * a)
                c.drawOval(ax - rad * 0.8f, ay - rad * 0.5f, ax + rad * 0.8f, ay + rad * 0.5f, p)
                p.style = Paint.Style.FILL
            }
            2 -> {
                p.style = Paint.Style.STROKE
                p.strokeCap = Paint.Cap.ROUND
                p.strokeWidth = r * (0.4f + 0.6f * a)
                p.color = IchiPal.alpha(IchiPal.DARK, 0.5f * a)
                c.drawLine(ax, ay, bx, by, p)
                p.strokeWidth = r * 0.5f * a
                p.color = IchiPal.alpha(col, 0.85f * a)
                c.drawLine(ax, ay, bx, by, p)
                p.strokeWidth = max(1.5f, r * 0.16f * a)
                p.color = IchiPal.alpha(IchiPal.WHITE, 0.9f * a)
                c.drawLine(ax, ay, bx, by, p)
                p.strokeCap = Paint.Cap.BUTT
                p.style = Paint.Style.FILL
            }
            3 -> {
                val rad = r * (0.1f + 0.9f * ease(u * 1.4f))
                p.style = Paint.Style.FILL
                p.color = IchiPal.alpha(IchiPal.DARK, 0.45f * a)
                c.drawOval(ax - rad, ay - rad * 0.62f, ax + rad, ay + rad * 0.62f, p)
                p.style = Paint.Style.STROKE
                p.strokeWidth = 4f + 10f * a
                p.color = IchiPal.alpha(col, 0.9f * a)
                c.drawOval(ax - rad, ay - rad * 0.62f, ax + rad, ay + rad * 0.62f, p)
                p.strokeWidth = 2f + 4f * a
                p.color = IchiPal.alpha(IchiPal.WHITE, 0.8f * a)
                c.drawOval(ax - rad * 0.93f, ay - rad * 0.58f, ax + rad * 0.93f, ay + rad * 0.58f, p)
                p.style = Paint.Style.FILL
                if (u < 0.3f) {
                    p.color = IchiPal.alpha(IchiPal.WHITE, 0.5f * (1f - u / 0.3f))
                    c.drawCircle(ax, ay - 20f, r * 0.35f * (1f - u), p)
                }
            }
            else -> {
                // clarão de corte: duas luas cruzadas na direção (x1, y1)
                val ang = atan2(y1 * 0.75f, x1)
                val grow = 0.55f + 0.45f * ease(u * 3f)
                IchiPal.crescent(c, p, ax, ay, r * grow, ang, 140f, 0.45f, col, 0.75f * a)
                IchiPal.crescent(c, p, ax, ay, r * grow * 0.8f, ang, 120f, 0.4f, IchiPal.WHITE, 0.9f * a)
            }
        }
    }
}
