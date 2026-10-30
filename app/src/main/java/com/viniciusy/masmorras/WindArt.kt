package com.viniciusy.masmorras

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin

/**
 * Pincéis do vento: tudo desenhado em código, usado tanto no jogo quanto na árvore de talentos.
 * Estilos de skill: 0 flecha espiral, 1 lâmina crescente, 2 tornado, 3 dash, 4 aura/anel,
 * 5 chuva de lâminas, 6 implosão, 7 passiva, 8 raio gigante.
 */
object WindArt {
    private val rf = RectF()
    private val path = Path()
    private const val DEG = 57.29578f
    private const val TAU = 6.2831855f
    private const val PI = 3.1415927f
    const val BRANCO = -1 // 0xFFFFFFFF

    fun a(cor: Int, f: Float): Int {
        val al = ((cor ushr 24) * f.coerceIn(0f, 1f)).toInt()
        return (al shl 24) or (cor and 0x00FFFFFF)
    }

    /** Lâmina crescente apontando para +x (gira por [angDeg]) com rastro de cópias mais finas atrás. */
    fun crescente(c: Canvas, p: Paint, cx: Float, cy: Float, r: Float, angDeg: Float, cor: Int, alpha: Float, larg: Float) {
        c.save()
        c.translate(cx, cy)
        c.rotate(angDeg)
        p.style = Paint.Style.STROKE
        p.strokeCap = Paint.Cap.ROUND
        for (i in 0..2) {
            val k = 1f - i * 0.28f
            val rr = r * (1f - i * 0.10f)
            val sh = i * r * 0.22f
            rf.set(-rr - sh, -rr, rr - sh, rr)
            p.strokeWidth = larg * k
            p.color = a(cor, alpha * k)
            c.drawArc(rf, -58f, 116f, false, p)
        }
        rf.set(-r, -r, r, r)
        p.strokeWidth = larg * 0.35f
        p.color = a(BRANCO, alpha * 0.9f)
        c.drawArc(rf, -52f, 104f, false, p)
        p.strokeCap = Paint.Cap.BUTT
        c.restore()
    }

    /** Duas fitas de vento em hélice ao longo de uma linha. */
    fun espiral(c: Canvas, p: Paint, x1: Float, y1: Float, x2: Float, y2: Float, t: Float, cor: Int, alpha: Float, amp: Float, voltas: Float) {
        val dx = x2 - x1
        val dy = y2 - y1
        val len = hypot(dx, dy)
        if (len < 4f) return
        val nx = -dy / len
        val ny = dx / len
        val steps = (len / 8f).toInt().coerceIn(6, 60)
        p.style = Paint.Style.STROKE
        p.strokeCap = Paint.Cap.ROUND
        for (s in 0..1) {
            path.reset()
            for (i in 0..steps) {
                val q = i / steps.toFloat()
                val w = sin(q * voltas * TAU - t * 12f + s * PI) * amp * sin(q * PI)
                val x = x1 + dx * q + nx * w
                val y = y1 + dy * q + ny * w
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            p.strokeWidth = 2.6f
            p.color = a(cor, alpha * 0.9f)
            c.drawPath(path, p)
        }
        p.strokeWidth = 1.6f
        p.color = a(BRANCO, alpha * 0.8f)
        c.drawLine(x1, y1, x2, y2, p)
        p.strokeCap = Paint.Cap.BUTT
    }

    /** Funil de tornado girando: anéis de arco empilhados que alargam para cima. */
    fun funil(c: Canvas, p: Paint, cx: Float, baseY: Float, r: Float, h: Float, t: Float, cor: Int, alpha: Float) {
        p.style = Paint.Style.STROKE
        p.strokeCap = Paint.Cap.ROUND
        for (i in 0..8) {
            val q = i / 8f
            val rr = r * (0.22f + 0.78f * q)
            val y = baseY - h * q
            val ang = t * 380f + i * 47f
            rf.set(cx - rr, y - rr * 0.28f, cx + rr, y + rr * 0.28f)
            p.strokeWidth = 2f + 2.5f * q
            p.color = a(cor, alpha * (0.35f + 0.5f * q))
            c.drawArc(rf, ang, 150f, false, p)
            p.strokeWidth = 1.5f
            p.color = a(BRANCO, alpha * 0.4f)
            c.drawArc(rf, ang + 180f, 70f, false, p)
        }
        p.strokeCap = Paint.Cap.BUTT
    }

    /** Anel de vento girando (elipse achatada, como no chão). */
    fun anel(c: Canvas, p: Paint, cx: Float, cy: Float, r: Float, t: Float, cor: Int, alpha: Float, larg: Float) {
        p.style = Paint.Style.STROKE
        p.strokeCap = Paint.Cap.ROUND
        p.strokeWidth = larg
        for (i in 0..2) {
            val ang = t * 300f + i * 120f
            rf.set(cx - r, cy - r * 0.45f, cx + r, cy + r * 0.45f)
            p.color = a(cor, alpha * (1f - i * 0.2f))
            c.drawArc(rf, ang, 70f, false, p)
        }
        p.strokeCap = Paint.Cap.BUTT
    }

    /** Ícone da skill (traços simples) centrado em (cx, cy) com raio r. */
    fun icone(c: Canvas, p: Paint, cx: Float, cy: Float, r: Float, estilo: Int, cor: Int, alpha: Float) {
        p.style = Paint.Style.STROKE
        p.strokeCap = Paint.Cap.ROUND
        p.strokeWidth = r * 0.17f
        p.color = a(cor, alpha)
        when (estilo) {
            0 -> {
                c.drawLine(cx - r * 0.8f, cy + r * 0.6f, cx + r * 0.8f, cy - r * 0.6f, p)
                c.drawLine(cx + r * 0.8f, cy - r * 0.6f, cx + r * 0.35f, cy - r * 0.62f, p)
                c.drawLine(cx + r * 0.8f, cy - r * 0.6f, cx + r * 0.62f, cy - r * 0.12f, p)
                rf.set(cx - r * 0.7f, cy - r * 0.5f, cx + r * 0.1f, cy + r * 0.3f)
                c.drawArc(rf, 100f, 190f, false, p)
            }
            1 -> {
                rf.set(cx - r * 0.5f, cy - r * 0.8f, cx + r * 0.8f, cy + r * 0.8f)
                c.drawArc(rf, -65f, 130f, false, p)
                rf.set(cx - r * 0.8f, cy - r * 0.55f, cx + r * 0.3f, cy + r * 0.55f)
                c.drawArc(rf, -60f, 120f, false, p)
            }
            2 -> {
                for (i in 0..2) {
                    val w = r * (0.9f - i * 0.28f)
                    val y = cy - r * 0.55f + i * r * 0.55f
                    rf.set(cx - w, y - r * 0.16f, cx + w, y + r * 0.16f)
                    c.drawArc(rf, 10f, 160f, false, p)
                }
            }
            3 -> {
                c.drawLine(cx - r * 0.9f, cy - r * 0.45f, cx + r * 0.5f, cy - r * 0.45f, p)
                c.drawLine(cx - r * 0.5f, cy, cx + r * 0.9f, cy, p)
                c.drawLine(cx - r * 0.9f, cy + r * 0.45f, cx + r * 0.3f, cy + r * 0.45f, p)
            }
            4 -> {
                c.drawCircle(cx, cy, r * 0.55f, p)
                rf.set(cx - r * 0.9f, cy - r * 0.9f, cx + r * 0.9f, cy + r * 0.9f)
                c.drawArc(rf, 200f, 100f, false, p)
                c.drawArc(rf, 20f, 100f, false, p)
            }
            5 -> {
                rf.set(cx - r * 0.9f, cy - r * 0.8f, cx - r * 0.1f, cy + r * 0.0f)
                c.drawArc(rf, -65f, 130f, false, p)
                rf.set(cx + r * 0.0f, cy - r * 0.1f, cx + r * 0.9f, cy + r * 0.8f)
                c.drawArc(rf, -65f, 130f, false, p)
            }
            6 -> {
                c.drawLine(cx - r * 0.85f, cy - r * 0.85f, cx - r * 0.25f, cy - r * 0.25f, p)
                c.drawLine(cx + r * 0.85f, cy - r * 0.85f, cx + r * 0.25f, cy - r * 0.25f, p)
                c.drawLine(cx - r * 0.85f, cy + r * 0.85f, cx - r * 0.25f, cy + r * 0.25f, p)
                c.drawLine(cx + r * 0.85f, cy + r * 0.85f, cx + r * 0.25f, cy + r * 0.25f, p)
                p.style = Paint.Style.FILL
                c.drawCircle(cx, cy, r * 0.16f, p)
            }
            7 -> {
                path.reset()
                path.moveTo(cx, cy - r * 0.8f)
                path.lineTo(cx + r * 0.8f, cy)
                path.lineTo(cx, cy + r * 0.8f)
                path.lineTo(cx - r * 0.8f, cy)
                path.close()
                c.drawPath(path, p)
                p.style = Paint.Style.FILL
                c.drawCircle(cx, cy, r * 0.2f, p)
            }
            else -> {
                c.drawLine(cx - r * 0.9f, cy + r * 0.6f, cx + r * 0.5f, cy - r * 0.3f, p)
                c.drawLine(cx + r * 0.5f, cy - r * 0.3f, cx + r * 0.95f, cy - r * 0.75f, p)
                c.drawLine(cx + r * 0.5f, cy - r * 0.3f, cx + r * 0.95f, cy - r * 0.1f, p)
                c.drawLine(cx + r * 0.5f, cy - r * 0.3f, cx + r * 0.2f, cy - r * 0.8f, p)
            }
        }
        p.style = Paint.Style.FILL
        p.strokeCap = Paint.Cap.BUTT
    }

    private fun boneco(c: Canvas, p: Paint, x: Float, y: Float, s: Float, cor: Int, alpha: Float) {
        p.style = Paint.Style.FILL
        p.color = a(cor, alpha)
        c.drawCircle(x, y - s * 1.3f, s * 0.42f, p)
        c.drawRect(x - s * 0.3f, y - s * 0.85f, x + s * 0.3f, y + s * 0.2f, p)
    }

    /** Demonstração animada da skill dentro de [r] (usada no painel de detalhes da árvore). */
    fun preview(c: Canvas, p: Paint, r: RectF, estilo: Int, cor: Int, t: Float) {
        c.save()
        c.clipRect(r)
        p.style = Paint.Style.FILL
        p.color = 0xFF0A1014.toInt()
        c.drawRect(r, p)
        val w = r.width()
        val h = r.height()
        val gy = r.top + h * 0.8f
        p.color = 0xFF16222A.toInt()
        c.drawRect(r.left, gy, r.right, r.bottom, p)
        val s = h * 0.17f
        val ax = r.left + w * 0.16f
        val tx = r.right - w * 0.16f
        val my = gy - s * 0.1f
        val ph = (t % 2.4f) / 2.4f
        boneco(c, p, tx, gy, s, 0xFF8D6E63.toInt(), 1f)
        when (estilo) {
            0 -> {
                boneco(c, p, ax, gy, s, 0xFFB2EBF2.toInt(), 1f)
                val q = (ph * 1.25f).coerceIn(0f, 1f)
                val x = ax + (tx - ax) * q
                val y = my - s * 1.2f
                espiral(c, p, (x - w * 0.22f).coerceAtLeast(ax), y, x, y, t, cor, 1f, s * 0.35f, 3f)
                p.style = Paint.Style.FILL
                p.color = a(BRANCO, 1f)
                c.drawCircle(x, y, s * 0.15f, p)
                if (q >= 1f) {
                    p.style = Paint.Style.STROKE
                    p.strokeWidth = 3f
                    p.color = a(cor, 1f - (ph - 0.8f) * 5f)
                    c.drawCircle(tx, y, s * (0.5f + (ph - 0.8f) * 4f), p)
                }
            }
            1 -> {
                boneco(c, p, ax, gy, s, 0xFFB2EBF2.toInt(), 1f)
                val x = ax + (tx + s * 3f - ax) * ph
                crescente(c, p, x, my - s * 1.2f, s * 1.5f, 0f, cor, 1f, 5f)
            }
            2 -> {
                funil(c, p, r.left + w * 0.5f, gy, s * 1.7f, h * 0.72f, t, cor, 1f)
                p.style = Paint.Style.FILL
                for (i in 0..5) {
                    val an = t * 4f + i * 1.05f
                    p.color = a(BRANCO, 0.7f)
                    c.drawCircle(r.left + w * 0.5f + cos(an) * s * 2.2f, gy - s * 0.4f + sin(an) * s * 0.5f, 2.5f, p)
                }
            }
            3 -> {
                val x = ax + (tx - ax) * 0.8f * ph
                for (i in 4 downTo 1) boneco(c, p, x - i * s * 0.9f, gy, s, cor, 0.16f * (5 - i))
                boneco(c, p, x, gy, s, 0xFFB2EBF2.toInt(), 1f)
                p.style = Paint.Style.STROKE
                p.strokeWidth = 2.5f
                p.color = a(cor, 0.8f)
                for (i in 0..3) c.drawLine(x - s * 4f, gy - s * (0.2f + i * 0.4f), x - s * (1.2f + i * 0.3f), gy - s * (0.2f + i * 0.4f), p)
            }
            4 -> {
                val cx = r.left + w * 0.5f
                boneco(c, p, cx, gy, s, 0xFFB2EBF2.toInt(), 1f)
                anel(c, p, cx, gy - s * 0.6f, s * 1.5f, t, cor, 1f, 4f)
                p.style = Paint.Style.STROKE
                p.strokeWidth = 3f
                p.color = a(cor, 1f - ph)
                rf.set(cx - s * (1f + ph * 3f), gy - s * 0.5f - s * 0.5f * (1f + ph * 3f), cx + s * (1f + ph * 3f), gy - s * 0.5f + s * 0.5f * (1f + ph * 3f))
                c.drawOval(rf, p)
            }
            5 -> {
                boneco(c, p, ax, gy, s, 0xFFB2EBF2.toInt(), 1f)
                for (i in 0..3) {
                    val lp = ((ph * 2.5f + i * 0.27f) % 1f)
                    val x = tx - s * 2.5f + i * s * 1.7f
                    val y = r.top + (gy - r.top) * lp
                    crescente(c, p, x, y, s * 0.9f, 90f, cor, 1f - lp * 0.3f, 4f)
                    if (lp > 0.85f) {
                        p.style = Paint.Style.STROKE
                        p.strokeWidth = 2.5f
                        p.color = a(cor, (1f - lp) * 6f)
                        c.drawCircle(x, gy, s * (0.4f + (lp - 0.85f) * 6f), p)
                    }
                }
            }
            6 -> {
                val cx = r.left + w * 0.5f
                val cy = gy - s * 0.8f
                p.style = Paint.Style.FILL
                for (i in 0..7) {
                    val an = i * TAU / 8f
                    val rr = if (ph < 0.65f) s * 3.2f * (1f - ph / 0.65f) + s * 0.3f else s * 0.3f + (ph - 0.65f) * s * 9f
                    p.color = a(cor, if (ph < 0.65f) 1f else (1f - (ph - 0.65f) * 2.8f))
                    c.drawCircle(cx + cos(an) * rr, cy + sin(an) * rr * 0.6f, s * 0.14f, p)
                }
                boneco(c, p, cx, gy, s, 0xFF8D6E63.toInt(), 0f)
            }
            7 -> {
                val cx = r.left + w * 0.5f
                boneco(c, p, cx, gy, s, 0xFFB2EBF2.toInt(), 1f)
                p.style = Paint.Style.STROKE
                p.strokeWidth = 3f
                p.color = a(cor, 0.5f + 0.4f * sin(t * 4f))
                c.drawCircle(cx, gy - s * 0.6f, s * (1.5f + 0.2f * sin(t * 4f)), p)
                for (i in 0..2) {
                    val lp = ((t * 0.8f + i * 0.33f) % 1f)
                    icone(c, p, cx - s * 1.8f + i * s * 1.8f, gy - s * (1.8f + lp * 1.6f), s * 0.35f, 7, cor, 1f - lp)
                }
            }
            else -> {
                boneco(c, p, ax, gy, s, 0xFFB2EBF2.toInt(), 1f)
                val y = my - s * 1.2f
                if (ph < 0.45f) {
                    p.style = Paint.Style.STROKE
                    p.strokeWidth = 3f
                    p.color = a(cor, 0.5f + ph)
                    c.drawCircle(ax + s, y, s * (1.6f - ph * 2.5f), p)
                } else if (ph < 0.8f) {
                    val k = 1f - (ph - 0.45f) / 0.35f
                    p.style = Paint.Style.STROKE
                    p.strokeWidth = 14f * k + 2f
                    p.color = a(cor, 0.35f * k)
                    c.drawLine(ax + s, y, tx, y, p)
                    espiral(c, p, ax + s, y, tx, y, t, cor, k, s * 0.7f, 5f)
                    p.style = Paint.Style.FILL
                    p.color = a(BRANCO, 0.6f * k)
                    c.drawCircle(tx, y, s * (1f + (1f - k) * 1.5f), p)
                }
            }
        }
        p.style = Paint.Style.STROKE
        p.strokeWidth = 2f
        p.color = a(cor, 0.5f)
        c.drawRect(r.left + 1f, r.top + 1f, r.right - 1f, r.bottom - 1f, p)
        p.style = Paint.Style.FILL
        c.restore()
    }

    /** Menor dos dois valores, só para evitar import duplicado em quem usa a classe. */
    fun menor(a: Float, b: Float): Float = min(a, b)
}
