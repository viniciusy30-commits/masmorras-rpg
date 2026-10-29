package com.viniciusy.masmorras

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
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

    /** Número pseudo-aleatório estável (0..1) para a semente [i]. */
    fun rn(i: Int): Float {
        val v = sin(i * 12.9898f + 78.233f) * 43758.547f
        return v - floor(v)
    }

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

    /** Espinho triangular de chama que sai de (x, y) na direção [ang] (tela). */
    fun spike(c: Canvas, p: Paint, x: Float, y: Float, ang: Float, len: Float, w: Float, color: Int, a: Float) {
        val ca = cos(ang)
        val sa = sin(ang)
        path.reset()
        path.moveTo(x - sa * w, y + ca * w * 0.8f)
        path.lineTo(x + ca * len, y + sa * len * 0.8f)
        path.lineTo(x + sa * w, y - ca * w * 0.8f)
        path.close()
        p.style = Paint.Style.FILL
        p.color = alpha(color, a)
        c.drawPath(path, p)
    }

    /** Raios saindo de um ponto (explosão, faísca de corte). */
    fun rays(c: Canvas, p: Paint, x: Float, y: Float, r0: Float, r1: Float, cnt: Int, rot: Float, w: Float, color: Int, a: Float, seed: Int) {
        p.style = Paint.Style.STROKE
        p.strokeCap = Paint.Cap.ROUND
        p.strokeWidth = w
        p.color = alpha(color, a)
        for (i in 0 until cnt) {
            val an = rot + i * (2f * PI.toFloat()) / cnt
            val l1 = r0 + (r1 - r0) * (0.45f + 0.55f * rn(i + seed))
            c.drawLine(x + cos(an) * r0, y + sin(an) * r0 * 0.8f, x + cos(an) * l1, y + sin(an) * l1 * 0.8f, p)
        }
        p.strokeCap = Paint.Cap.BUTT
        p.style = Paint.Style.FILL
    }

    /** Linha em zigue-zague (raio de reiatsu). */
    fun zig(c: Canvas, p: Paint, x0: Float, y0: Float, x1: Float, y1: Float, amp: Float, seed: Int, w: Float, color: Int, a: Float) {
        val dx = x1 - x0
        val dy = y1 - y0
        val len = max(1f, hypot(dx, dy))
        val nx = -dy / len
        val ny = dx / len
        path.reset()
        path.moveTo(x0, y0)
        for (i in 1..6) {
            val u = i / 6f
            val j = if (i == 6) 0f else (rn(seed + i * 5) - 0.5f) * amp
            path.lineTo(x0 + dx * u + nx * j, y0 + dy * u + ny * j)
        }
        p.style = Paint.Style.STROKE
        p.strokeCap = Paint.Cap.ROUND
        p.strokeWidth = w
        p.color = alpha(color, a)
        c.drawPath(path, p)
        p.strokeCap = Paint.Cap.BUTT
        p.style = Paint.Style.FILL
    }
}

/** Getsuga Tenshou: meia-lua negra de contorno vermelho, com chamas, rastro longo e faíscas. */
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
        val tvx = dx
        val tvy = dy * 0.75f
        val tl = max(0.001f, hypot(tvx, tvy))
        val ux = tvx / tl
        val uy = tvy / tl
        val nx = -uy
        val ny = ux
        val ang = atan2(dy * 0.75f, dx)
        val grow = min(1f, age / 0.10f)
        val fade = min(1f, life / 0.25f)
        val r = (30f + 12f * grow) * (0.5f + 0.5f * grow)
        val fl = (age * 30f).toInt()

        // sombra no chão
        p.style = Paint.Style.FILL
        p.color = IchiPal.alpha(IchiPal.DARK, 0.30f * fade)
        c.drawOval(sx - r * 0.95f, sy + lift - r * 0.20f, sx + r * 0.95f, sy + lift - r * 0.20f + r * 0.4f, p)

        // rastro longo e afunilado
        val tr = r * 5.2f
        val bx = sx - ux * tr
        val by = sy - uy * tr
        val wd = floatArrayOf(1.00f, 0.62f, 0.26f)
        val cl = intArrayOf(IchiPal.DARK, IchiPal.RED, IchiPal.RED_L)
        val al = floatArrayOf(0.34f, 0.48f, 0.55f)
        for (i in 0..2) {
            val w = r * wd[i]
            IchiPal.path.reset()
            IchiPal.path.moveTo(sx + nx * w, sy + ny * w)
            IchiPal.path.lineTo(bx, by)
            IchiPal.path.lineTo(sx - nx * w, sy - ny * w)
            IchiPal.path.close()
            p.color = IchiPal.alpha(cl[i], al[i] * fade)
            c.drawPath(IchiPal.path, p)
        }
        // luas fantasmas
        for (i in 4 downTo 1) {
            IchiPal.crescent(
                c, p, sx - ux * 14f * i, sy - uy * 14f * i, r * (1f - 0.09f * i), ang,
                110f, 0.55f, IchiPal.DARK, 0.20f * fade
            )
        }
        // chamas na borda externa
        for (k in 0..12) {
            val t = k / 12f - 0.5f
            val a = ang + t * 2.1f
            val bxx = sx + cos(a) * r * 1.12f
            val byy = sy + sin(a) * r * 0.78f * 1.12f
            val l = r * (0.30f + 0.55f * IchiPal.rn(k + fl * 3)) * (1f - abs2(t) * 0.8f)
            IchiPal.spike(c, p, bxx, byy, a, l, r * 0.11f, if (k % 2 == 0) IchiPal.RED_L else IchiPal.RED, 0.85f * fade)
        }
        IchiPal.crescent(c, p, sx, sy, r * 1.30f, ang, 130f, 0.5f, IchiPal.RED, 0.55f * fade)
        IchiPal.crescent(c, p, sx, sy, r * 1.12f, ang, 124f, 0.5f, IchiPal.DARK, 0.96f * fade)
        IchiPal.crescent(c, p, sx, sy, r * 0.90f, ang, 112f, 0.45f, IchiPal.RED_L, 0.92f * fade)
        IchiPal.crescent(c, p, sx, sy, r * 0.66f, ang, 92f, 0.40f, IchiPal.WHITE, 0.90f * fade)
        // brilho no centro
        p.color = IchiPal.alpha(IchiPal.WHITE, 0.55f * fade)
        c.drawCircle(sx + ux * r * 0.25f, sy + uy * r * 0.25f, r * 0.20f, p)
        // faíscas soltas atrás
        for (k in 0..7) {
            val ph = (age * 2.6f + k * 0.131f) % 1f
            val off = (IchiPal.rn(k + 40) - 0.5f) * r * 1.6f
            val px = sx - ux * ph * tr * 0.8f + nx * off
            val py = sy - uy * ph * tr * 0.8f + ny * off
            val sz = 1.5f + 3f * (1f - ph)
            p.color = IchiPal.alpha(if (k % 2 == 0) IchiPal.WHITE else IchiPal.RED_L, (1f - ph) * fade)
            c.drawRect(px - sz, py - sz, px + sz, py + sz, p)
        }
    }

    private fun abs2(v: Float): Float = if (v < 0f) -v else v
}

/**
 * Efeitos curtos do Ichigo. kind: 0 = feixe (Cero), 1 = anel no chão, 2 = rastro de Shunpo,
 * 3 = onda escura que preenche (Mugetsu / transformação), 4 = clarão de corte,
 * 5 = onda lunar rasteira (Golpe Lunar), 6 = corte em linha (garras), 7 = faísca de impacto,
 * 8 = feixe negro (Cero Oscuras). [delay] adia o efeito.
 */
class IchiFx(
    val kind: Int, val x0: Float, val y0: Float, val x1: Float, val y1: Float,
    val maxLife: Float, val r: Float, val col: Int, val delay: Float = 0f
) {
    var life = maxLife + delay
    val done: Boolean get() = life <= 0f
    private val seed = (x0 * 7f + y0 * 13f).toInt() and 0xFFFF

    fun update(dt: Float) {
        life -= dt
    }

    private fun ease(t: Float): Float {
        val v = t.coerceIn(0f, 1f)
        return v * v * (3f - 2f * v)
    }

    fun draw(c: Canvas, p: Paint, camX: Float, camY: Float) {
        if (life <= 0f || life > maxLife) return
        val a = (life / maxLife).coerceIn(0f, 1f)
        val u = 1f - a
        val ax = x0 - camX
        val ay = y0 - camY
        val bx = x1 - camX
        val by = y1 - camY
        val tf = (life * 40f).toInt()
        when (kind) {
            0, 8 -> {
                val dk = kind == 8
                val w = r * (0.30f + 0.70f * a) * (1f + 0.07f * sin(life * 70f))
                val dxl = bx - ax
                val dyl = by - ay
                val len = max(1f, hypot(dxl, dyl))
                val ux = dxl / len
                val uy = dyl / len
                // halo largo
                p.style = Paint.Style.STROKE
                p.strokeCap = Paint.Cap.ROUND
                p.strokeWidth = w * 3.4f
                p.color = IchiPal.alpha(IchiPal.DARK, 0.30f * a)
                c.drawLine(ax, ay, bx, by, p)
                p.strokeWidth = w * 2.1f
                p.color = IchiPal.alpha(IchiPal.DARK, 0.55f * a)
                c.drawLine(ax, ay, bx, by, p)
                p.strokeWidth = w * 1.45f
                p.color = IchiPal.alpha(col, 0.85f * a)
                c.drawLine(ax, ay, bx, by, p)
                p.strokeWidth = w * 0.9f
                p.color = IchiPal.alpha(if (dk) IchiPal.DARK else IchiPal.RED_L, 0.95f * a)
                c.drawLine(ax, ay, bx, by, p)
                p.strokeWidth = w * 0.45f
                p.color = IchiPal.alpha(if (dk) IchiPal.RED_L else IchiPal.WHITE, 0.95f * a)
                c.drawLine(ax, ay, bx, by, p)
                p.strokeCap = Paint.Cap.BUTT
                // anéis de energia correndo pelo feixe
                for (k in 0..9) {
                    val t = (u * 2.4f + k * 0.1f) % 1f
                    val px = ax + dxl * t
                    val py = ay + dyl * t
                    val h = w * (1.5f - 0.5f * t)
                    p.style = Paint.Style.STROKE
                    p.strokeWidth = max(1.5f, w * 0.18f)
                    p.color = IchiPal.alpha(IchiPal.WHITE, 0.65f * a)
                    c.drawLine(px - uy * h, py + ux * h, px + uy * h, py - ux * h, p)
                }
                // raios elétricos nas bordas
                for (k in 0..2) {
                    val sgn = if (k % 2 == 0) 1f else -1f
                    val o = w * 1.3f * sgn
                    IchiPal.zig(
                        c, p, ax - uy * o, ay + ux * o, bx - uy * o, by + ux * o,
                        w * 1.6f, tf * 3 + k * 17, max(1.4f, w * 0.12f), IchiPal.RED_L, 0.85f * a
                    )
                }
                // origem: estrela de energia
                p.style = Paint.Style.FILL
                p.color = IchiPal.alpha(IchiPal.WHITE, 0.8f * a)
                c.drawCircle(ax, ay, w * 1.3f, p)
                IchiPal.rays(c, p, ax, ay, w * 1.0f, w * (3.4f + 2f * a), 12, life * 9f, max(1.6f, w * 0.18f), IchiPal.RED_L, 0.85f * a, 3)
                // fim: explosão
                val ex = r * (1.0f + 2.6f * u)
                p.color = IchiPal.alpha(IchiPal.DARK, 0.35f * a)
                c.drawCircle(bx, by, ex * 1.25f, p)
                p.color = IchiPal.alpha(col, 0.55f * a)
                c.drawCircle(bx, by, ex, p)
                p.color = IchiPal.alpha(IchiPal.WHITE, 0.75f * a)
                c.drawCircle(bx, by, ex * 0.5f, p)
                IchiPal.rays(c, p, bx, by, ex * 0.7f, ex * 2.1f, 14, u * 2f, max(1.6f, r * 0.12f), IchiPal.RED_L, 0.8f * a, 9)
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
                // segundo anel, mais lento e fino
                val r2 = r * (0.1f + 0.7f * ease(u * 0.9f))
                p.strokeWidth = 1.5f + 3f * a
                p.color = IchiPal.alpha(IchiPal.WHITE, 0.65f * a)
                c.drawOval(ax - r2, ay - r2 * 0.62f, ax + r2, ay + r2 * 0.62f, p)
                // rachaduras no chão
                p.strokeCap = Paint.Cap.ROUND
                for (k in 0..9) {
                    val an = k * 0.628f + IchiPal.rn(seed + k) * 0.3f
                    val l0 = rad * 0.15f
                    val l1 = rad * (0.55f + 0.4f * IchiPal.rn(seed + k + 20))
                    p.strokeWidth = 1.5f + 2.2f * a
                    p.color = IchiPal.alpha(if (k % 2 == 0) IchiPal.DARK else col, 0.75f * a)
                    c.drawLine(ax + cos(an) * l0, ay + sin(an) * l0 * 0.62f, ax + cos(an) * l1, ay + sin(an) * l1 * 0.62f, p)
                }
                p.strokeCap = Paint.Cap.BUTT
                // chamas subindo do anel
                for (k in 0..15) {
                    val an = k * 0.3927f
                    val l = r * 0.20f * (0.4f + IchiPal.rn(seed + k + 50)) * a
                    IchiPal.spike(
                        c, p, ax + cos(an) * rad * 0.97f, ay + sin(an) * rad * 0.62f * 0.97f,
                        -PI.toFloat() / 2f, l, r * 0.025f, if (k % 2 == 0) col else IchiPal.DARK, 0.7f * a
                    )
                }
                p.style = Paint.Style.FILL
            }
            2 -> {
                val dxl = bx - ax
                val dyl = by - ay
                val len = max(1f, hypot(dxl, dyl))
                val ux = dxl / len
                val uy = dyl / len
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
                // linhas de velocidade paralelas
                for (k in -3..3) {
                    val o = k * r * 0.34f
                    val s0 = 0.1f + 0.5f * IchiPal.rn(seed + k + 9)
                    val s1 = s0 + 0.3f + 0.4f * IchiPal.rn(seed + k + 19)
                    p.strokeWidth = max(1.2f, r * 0.06f)
                    p.color = IchiPal.alpha(if (k % 2 == 0) IchiPal.WHITE else col, 0.7f * a)
                    c.drawLine(
                        ax + dxl * s0 - uy * o, ay + dyl * s0 + ux * o,
                        ax + dxl * min(1f, s1) - uy * o, ay + dyl * min(1f, s1) + ux * o, p
                    )
                }
                p.strokeCap = Paint.Cap.BUTT
                // clarões de partida e de chegada
                IchiPal.rays(c, p, ax, ay, r * 0.3f, r * (1.4f + 1.2f * u), 10, u * 3f, max(1.4f, r * 0.08f), col, 0.8f * a, 5)
                IchiPal.rays(c, p, bx, by, r * 0.3f, r * (1.8f + 1.4f * u), 12, -u * 3f, max(1.4f, r * 0.08f), IchiPal.WHITE, 0.8f * a, 11)
                p.style = Paint.Style.FILL
                p.color = IchiPal.alpha(IchiPal.WHITE, 0.55f * a)
                c.drawCircle(bx, by, r * (0.5f + 0.9f * u), p)
            }
            3 -> {
                val rad = r * (0.1f + 0.9f * ease(u * 1.4f))
                p.style = Paint.Style.FILL
                p.color = IchiPal.alpha(IchiPal.DARK, 0.45f * a)
                c.drawOval(ax - rad, ay - rad * 0.62f, ax + rad, ay + rad * 0.62f, p)
                // chamas negras subindo da onda
                for (k in 0..23) {
                    val an = k * 0.2618f
                    val l = r * (0.18f + 0.30f * IchiPal.rn(seed + k + tf / 3)) * (0.4f + a)
                    IchiPal.spike(
                        c, p, ax + cos(an) * rad * 0.96f, ay + sin(an) * rad * 0.62f * 0.96f,
                        -PI.toFloat() / 2f + (IchiPal.rn(seed + k) - 0.5f) * 0.5f, l, r * 0.03f,
                        if (k % 3 == 0) col else IchiPal.DARK, 0.75f * a
                    )
                }
                p.style = Paint.Style.STROKE
                p.strokeWidth = 4f + 10f * a
                p.color = IchiPal.alpha(col, 0.9f * a)
                c.drawOval(ax - rad, ay - rad * 0.62f, ax + rad, ay + rad * 0.62f, p)
                p.strokeWidth = 2f + 4f * a
                p.color = IchiPal.alpha(IchiPal.WHITE, 0.8f * a)
                c.drawOval(ax - rad * 0.93f, ay - rad * 0.58f, ax + rad * 0.93f, ay + rad * 0.58f, p)
                // raios da explosão
                IchiPal.rays(c, p, ax, ay - 10f, rad * 0.15f, rad * (0.5f + 0.5f * u), 18, u * 1.5f, 2f + 4f * a, IchiPal.RED_L, 0.6f * a, 21)
                p.style = Paint.Style.FILL
                if (u < 0.3f) {
                    p.color = IchiPal.alpha(IchiPal.WHITE, 0.5f * (1f - u / 0.3f))
                    c.drawCircle(ax, ay - 20f, r * 0.35f * (1f - u), p)
                }
            }
            5 -> {
                // onda lunar rasteira: meia-lua que se abre no chão à frente do golpe
                val ang = atan2(y1 * 0.75f, x1)
                val rad = r * (0.35f + 0.65f * ease(u))
                val cx = ax + x1 * r * 0.25f * u
                val cy = ay + y1 * r * 0.25f * u * 0.75f
                IchiPal.crescent(c, p, cx, cy, rad * 1.1f, ang, 150f, 0.40f, IchiPal.DARK, 0.5f * a)
                IchiPal.crescent(c, p, cx, cy, rad, ang, 140f, 0.34f, col, 0.85f * a)
                IchiPal.crescent(c, p, cx, cy, rad * 0.82f, ang, 120f, 0.30f, IchiPal.WHITE, 0.9f * a)
                IchiPal.rays(c, p, ax, ay, r * 0.2f, r * (0.7f + 0.9f * u), 12, u * 2f, max(1.4f, r * 0.04f), IchiPal.RED_L, 0.7f * a, 41)
                p.style = Paint.Style.FILL
            }
            6 -> {
                // corte em linha (garra): risca o ar rapidamente e some
                val prog = min(1f, u * 5f)
                val ex = ax + (bx - ax) * prog
                val ey = ay + (by - ay) * prog
                p.style = Paint.Style.STROKE
                p.strokeCap = Paint.Cap.ROUND
                p.strokeWidth = r * 2.6f * a
                p.color = IchiPal.alpha(IchiPal.DARK, 0.45f * a)
                c.drawLine(ax, ay, ex, ey, p)
                p.strokeWidth = r * 1.5f * a
                p.color = IchiPal.alpha(col, 0.9f * a)
                c.drawLine(ax, ay, ex, ey, p)
                p.strokeWidth = max(1.4f, r * 0.55f * a)
                p.color = IchiPal.alpha(IchiPal.WHITE, 0.95f * a)
                c.drawLine(ax, ay, ex, ey, p)
                p.strokeCap = Paint.Cap.BUTT
                IchiPal.rays(c, p, ex, ey, r * 0.4f, r * (1.4f + 1.6f * u), 7, u * 4f, max(1.2f, r * 0.2f), IchiPal.RED_L, 0.8f * a, 61)
                p.style = Paint.Style.FILL
            }
            7 -> {
                // faísca de impacto sobre quem foi atingido
                p.style = Paint.Style.FILL
                p.color = IchiPal.alpha(IchiPal.WHITE, 0.7f * a)
                c.drawCircle(ax, ay, r * (0.25f + 0.5f * u), p)
                p.color = IchiPal.alpha(col, 0.55f * a)
                c.drawCircle(ax, ay, r * (0.5f + 0.9f * u), p)
                IchiPal.rays(c, p, ax, ay, r * 0.3f, r * (1.0f + 1.4f * u), 9, u * 3f, max(1.4f, r * 0.07f), IchiPal.WHITE, 0.85f * a, 71)
                p.style = Paint.Style.FILL
            }
            else -> {
                // clarão de corte: duas luas cruzadas na direção (x1, y1)
                val ang = atan2(y1 * 0.75f, x1)
                val grow = 0.55f + 0.45f * ease(u * 3f)
                IchiPal.crescent(c, p, ax, ay, r * grow * 1.15f, ang, 150f, 0.5f, IchiPal.DARK, 0.45f * a)
                IchiPal.crescent(c, p, ax, ay, r * grow, ang, 140f, 0.45f, col, 0.80f * a)
                IchiPal.crescent(c, p, ax, ay, r * grow * 0.8f, ang, 120f, 0.4f, IchiPal.WHITE, 0.92f * a)
                // risco reto brilhante cortando o ar
                val pa = ang + PI.toFloat() / 2f
                val ll = r * (0.9f + 0.7f * u)
                p.style = Paint.Style.STROKE
                p.strokeCap = Paint.Cap.ROUND
                p.strokeWidth = max(1.6f, r * 0.07f)
                p.color = IchiPal.alpha(IchiPal.WHITE, 0.9f * a)
                c.drawLine(ax - cos(pa) * ll, ay - sin(pa) * ll * 0.8f, ax + cos(pa) * ll, ay + sin(pa) * ll * 0.8f, p)
                p.strokeCap = Paint.Cap.BUTT
                IchiPal.rays(c, p, ax, ay, r * 0.2f, r * (0.9f + 0.9f * u), 10, ang, max(1.4f, r * 0.05f), IchiPal.RED_L, 0.8f * a, 31)
                p.style = Paint.Style.FILL
            }
        }
    }
}


/** Getsuga Jujisho: grande cruz de reiatsu rubro-negro que avança girando e atravessa todos. */
class CrossWave(
    var x: Float, var y: Float, val dx: Float, val dy: Float,
    val dmg: Float, val lift: Float
) {
    var alive = true
    var age = 0f
    var life = 0.95f
    val speed = 400f
    val hit = HashSet<Enemy>()

    fun update(dt: Float) {
        x += dx * speed * dt
        y += dy * speed * dt
        age += dt
        life -= dt
        if (life <= 0f) alive = false
    }

    private fun bar(c: Canvas, p: Paint, sx: Float, sy: Float, ang: Float, half: Float, w: Float, fade: Float) {
        val ca = cos(ang)
        val sa = sin(ang)
        val x0 = sx - ca * half
        val y0 = sy - sa * half * 0.8f
        val x1 = sx + ca * half
        val y1 = sy + sa * half * 0.8f
        p.style = Paint.Style.STROKE
        p.strokeCap = Paint.Cap.ROUND
        p.strokeWidth = w * 1.9f
        p.color = IchiPal.alpha(IchiPal.DARK, 0.55f * fade)
        c.drawLine(x0, y0, x1, y1, p)
        p.strokeWidth = w * 1.25f
        p.color = IchiPal.alpha(IchiPal.RED, 0.95f * fade)
        c.drawLine(x0, y0, x1, y1, p)
        p.strokeWidth = w * 0.7f
        p.color = IchiPal.alpha(IchiPal.RED_L, 0.95f * fade)
        c.drawLine(x0, y0, x1, y1, p)
        p.strokeWidth = w * 0.28f
        p.color = IchiPal.alpha(IchiPal.WHITE, 0.95f * fade)
        c.drawLine(x0, y0, x1, y1, p)
        p.strokeCap = Paint.Cap.BUTT
    }

    fun draw(c: Canvas, p: Paint, camX: Float, camY: Float) {
        if (!alive) return
        val sx = x - camX
        val sy = y - camY - lift
        val ang = atan2(dy * 0.75f, dx)
        val grow = min(1f, age / 0.10f)
        val fade = min(1f, life / 0.25f)
        val half = 54f * (0.5f + 0.5f * grow)
        val w = 9f * (0.6f + 0.4f * grow)
        val tl = max(0.001f, hypot(dx, dy * 0.75f))
        val ux = dx / tl
        val uy = dy * 0.75f / tl

        // sombra no chão
        p.style = Paint.Style.FILL
        p.color = IchiPal.alpha(IchiPal.DARK, 0.30f * fade)
        c.drawOval(sx - half * 0.9f, sy + lift - half * 0.18f, sx + half * 0.9f, sy + lift + half * 0.22f, p)
        // rastro: cruzes fantasmas
        for (i in 3 downTo 1) {
            val gx = sx - ux * 22f * i
            val gy = sy - uy * 22f * i
            bar(c, p, gx, gy, ang + PI.toFloat() / 2f, half * (1f - 0.12f * i), w * 0.8f, 0.22f * fade)
            bar(c, p, gx, gy, ang, half * 0.8f * (1f - 0.12f * i), w * 0.8f, 0.22f * fade)
        }
        // cruz principal (a barra maior corta de través, a menor segue o avanço)
        bar(c, p, sx, sy, ang + PI.toFloat() / 2f, half, w, fade)
        bar(c, p, sx, sy, ang, half * 0.8f, w, fade)
        // chamas nas pontas e brilho central
        p.style = Paint.Style.FILL
        for (k in 0..3) {
            val a0 = ang + k * PI.toFloat() / 2f
            val hl = if (k % 2 == 0) half * 0.8f else half
            val flick = 0.6f + 0.4f * IchiPal.rn(k + (age * 30f).toInt() * 3)
            IchiPal.spike(c, p, sx + cos(a0) * hl, sy + sin(a0) * hl * 0.8f, a0, 16f * flick, 4f, IchiPal.RED_L, 0.85f * fade)
        }
        p.color = IchiPal.alpha(IchiPal.WHITE, 0.75f * fade)
        c.drawCircle(sx, sy, w * 1.1f, p)
        IchiPal.rays(c, p, sx, sy, w * 0.9f, w * 3.2f, 8, age * 6f, max(1.4f, w * 0.16f), IchiPal.RED_L, 0.8f * fade, 91)
        // faíscas soltas atrás
        p.style = Paint.Style.FILL
        for (k in 0..7) {
            val ph = (age * 2.6f + k * 0.131f) % 1f
            val off = (IchiPal.rn(k + 140) - 0.5f) * half * 1.4f
            val px = sx - ux * ph * 120f - uy * off
            val py = sy - uy * ph * 120f + ux * off
            val sz = 1.5f + 3f * (1f - ph)
            p.color = IchiPal.alpha(if (k % 2 == 0) IchiPal.WHITE else IchiPal.RED_L, (1f - ph) * fade)
            c.drawRect(px - sz, py - sz, px + sz, py + sz, p)
        }
    }
}
