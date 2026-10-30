package com.viniciusy.masmorras

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.View
import kotlin.math.min
import kotlin.math.sin

/**
 * Indicador do botão da Sombra Viva (no lugar do rolamento): enquanto a sombra existe, um anel roxo se esvazia
 * ao redor do botão e mostra os segundos que faltam; sem sombra, mostra a recarga. Não captura toques.
 */
class ShadowTimerView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.BUTT
    }
    private val txt = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD
    }
    private val rect = RectF()
    private var endAt = 0L      // instante (ms) em que a sombra some; 0 = sem sombra
    private var total = 1f
    private var cd = 0f         // recarga que falta (s), mostrada só sem sombra

    /** Sombra viva: left = segundos restantes, totalSec = duração total. */
    fun setShadow(left: Float, totalSec: Float) {
        val e = System.currentTimeMillis() + (left * 1000f).toLong()
        // só reajusta se houver diferença grande (a tela se anima sozinha entre as atualizações)
        if (endAt == 0L || kotlin.math.abs(e - endAt) > 250L) endAt = e
        total = totalSec
        cd = 0f
        invalidate()
    }

    /** Sem sombra: mostra a recarga (0 = pronta, sem número). */
    fun setIdle(cooldown: Float) {
        if (endAt == 0L && cd == cooldown) return
        endAt = 0L
        cd = cooldown
        invalidate()
    }

    fun clear() {
        endAt = 0L
        cd = 0f
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        val d = resources.displayMetrics.density
        val cx = width / 2f
        val cy = height / 2f
        val r = min(width, height) / 2f - 3.5f * d
        if (endAt > 0L) {
            val left = ((endAt - System.currentTimeMillis()) / 1000f).coerceAtLeast(0f)
            val frac = (left / total).coerceIn(0f, 1f)
            val urgent = left < 1.3f
            val blink = if (urgent) 0.5f + 0.5f * sin(System.currentTimeMillis() / 60.0).toFloat() else 0f
            rect.set(cx - r, cy - r, cx + r, cy + r)
            ring.strokeWidth = 5f * d
            ring.color = 0xCC0A0314.toInt()
            canvas.drawArc(rect, 0f, 360f, false, ring)
            ring.strokeWidth = 4f * d
            ring.color = if (urgent) mix(0xFFB3202D.toInt(), 0xFFFF6070.toInt(), blink) else 0xFFA56BFF.toInt()
            canvas.drawArc(rect, -90f, 360f * frac, false, ring)
            ring.strokeWidth = 1.4f * d
            ring.color = 0xFFE6D4FF.toInt()
            canvas.drawArc(rect, -90f, 360f * frac, false, ring)
            drawText(canvas, "%.1f".format(left), cx, cy, 17f * d / 1.0f, 0xFFE6D4FF.toInt())
            postInvalidateOnAnimation()
        } else if (cd > 0f) {
            drawText(canvas, Math.ceil(cd.toDouble()).toInt().toString(), cx, cy, 20f * d, 0xFFFFFFFF.toInt())
        }
    }

    private fun drawText(c: Canvas, s: String, cx: Float, cy: Float, size: Float, col: Int) {
        txt.textSize = size
        val y = cy - (txt.descent() + txt.ascent()) / 2f
        txt.style = Paint.Style.STROKE
        txt.strokeWidth = 4f
        txt.color = 0xFF0A0314.toInt()
        c.drawText(s, cx, y, txt)
        txt.style = Paint.Style.FILL
        txt.color = col
        c.drawText(s, cx, y, txt)
    }

    private fun mix(a: Int, b: Int, t: Float): Int {
        fun ch(s: Int) = ((a shr s and 0xFF) + ((b shr s and 0xFF) - (a shr s and 0xFF)) * t).toInt()
        return (0xFF shl 24) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
    }
}
