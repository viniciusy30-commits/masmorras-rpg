package com.viniciusy.masmorras

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PathMeasure
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/** Anel de carga do golpe forte: percorre o hexágono gravado no botão de ataque. Não captura toques. */
class ChargeRingView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.BUTT
        strokeJoin = Paint.Join.MITER
    }
    private val hex = Path()
    private val seg = Path()
    private val measure = PathMeasure()
    private var level = 0f
    private var shown = 0f // suavizado pra subir/sumir sem piscar
    private var builtFor = 0f

    fun setLevel(v: Float) {
        val nv = v.coerceIn(0f, 1f)
        if (nv == level) return
        level = nv
        invalidate()
    }

    private fun buildHex(cx: Float, cy: Float, r: Float) {
        hex.reset()
        hex.arcTo(RectF(cx - r, cy - r, cx + r, cy + r), -90f, 359.9f, true)
        measure.setPath(hex, false)
        builtFor = r
    }

    override fun onDraw(canvas: Canvas) {
        shown += (level - shown) * (if (level > shown) 0.6f else 0.25f)
        if (level <= 0f && shown < 0.01f) { shown = 0f; return }

        val d = resources.displayMetrics.density
        // raio do anel de ferro do botão redondo (90% do raio da moldura)
        val outer = min(width, height) / 2f - (14f / 3f) * d
        val r = outer * 0.90f
        if (builtFor != r) buildHex(width / 2f, height / 2f, r)

        val full = level >= 0.999f
        val pulse = if (full) 0.5f + 0.5f * sin(System.currentTimeMillis() / 70.0).toFloat() else 0f
        val w = 6f * d

        // trilho escuro
        paint.strokeWidth = w + 2f * d
        paint.color = 0xCC0C0B0A.toInt()
        canvas.drawPath(hex, paint)

        // progresso, a partir do vértice do topo, sentido horário
        seg.reset()
        measure.getSegment(0f, measure.length * shown, seg, true)
        paint.strokeWidth = w
        paint.color = if (full) mix(0xFFFFB74D.toInt(), 0xFFFFFFFF.toInt(), pulse) else 0xFFFFB74D.toInt()
        canvas.drawPath(seg, paint)

        // brilho interno fino
        paint.strokeWidth = w * 0.35f
        paint.color = 0xDDFFF3C4.toInt()
        canvas.drawPath(seg, paint)

        if (full) postInvalidateOnAnimation()
        else if (kotlin.math.abs(level - shown) > 0.005f) postInvalidateOnAnimation()
    }

    private fun mix(a: Int, b: Int, t: Float): Int {
        fun ch(s: Int) = ((a shr s and 0xFF) + ((b shr s and 0xFF) - (a shr s and 0xFF)) * t).toInt()
        return (0xFF shl 24) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
    }
}
