package com.viniciusy.masmorras

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import kotlin.math.sin

/** Anel de carga do golpe forte, desenhado por cima do botão de ataque. Não captura toques. */
class ChargeRingView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val rect = RectF()
    private var level = 0f
    private var shown = 0f // suavizado pra subir/sumir sem piscar

    fun setLevel(v: Float) {
        val nv = v.coerceIn(0f, 1f)
        if (nv == level) return
        level = nv
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        shown += (level - shown) * (if (level > shown) 0.6f else 0.25f)
        if (level <= 0f && shown < 0.01f) { shown = 0f; return }

        val d = resources.displayMetrics.density
        val w = 6f * d
        val full = level >= 0.999f
        val pulse = if (full) 0.5f + 0.5f * sin(System.currentTimeMillis() / 70.0).toFloat() else 0f
        val inset = w / 2f + 1f * d
        rect.set(inset, inset, width - inset, height - inset)

        // trilho escuro
        paint.strokeWidth = w
        paint.color = 0x99000000.toInt()
        canvas.drawArc(rect, 0f, 360f, false, paint)

        // progresso (sentido horário, começando no topo)
        paint.strokeWidth = w
        paint.color = if (full) mix(0xFFFFB74D.toInt(), 0xFFFFFFFF.toInt(), pulse) else 0xFFFFB74D.toInt()
        canvas.drawArc(rect, -90f, 360f * shown, false, paint)

        // brilho interno fino
        paint.strokeWidth = w * 0.35f
        paint.color = 0xCCFFF3C4.toInt()
        canvas.drawArc(rect, -90f, 360f * shown, false, paint)

        if (full) postInvalidateOnAnimation()
        else if (kotlin.math.abs(level - shown) > 0.005f) postInvalidateOnAnimation()
    }

    private fun mix(a: Int, b: Int, t: Float): Int {
        fun ch(s: Int) = ((a shr s and 0xFF) + ((b shr s and 0xFF) - (a shr s and 0xFF)) * t).toInt()
        return (0xFF shl 24) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
    }
}
