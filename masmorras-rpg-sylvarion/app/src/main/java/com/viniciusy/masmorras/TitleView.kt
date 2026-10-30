package com.viniciusy.masmorras

import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.View
import kotlin.math.sin

/** Logo "MASMORRAS" em blocos de pedra com extrusão 3D, contorno preto e brilho leve. */
class TitleView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val d = resources.displayMetrics.density
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val text = "MASMORRAS"
    private var t = 0f
    private var running = false

    init {
        paint.typeface = androidx.core.content.res.ResourcesCompat.getFont(context, R.font.pixel)
            ?: Typeface.create("sans-serif-black", Typeface.NORMAL)
        paint.letterSpacing = 0.02f
        paint.strokeJoin = Paint.Join.BEVEL
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        running = true
        postOnAnimation(tick)
    }

    override fun onDetachedFromWindow() {
        running = false
        removeCallbacks(tick)
        super.onDetachedFromWindow()
    }

    private val tick = object : Runnable {
        override fun run() {
            if (!running) return
            t += 0.016f
            if (isShown) invalidate()
            postOnAnimation(this)
        }
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val padX = 10f * d
        var size = 50f * d
        paint.textSize = size
        var tw = paint.measureText(text)
        val maxW = w - padX * 2
        if (tw > maxW) {
            size *= maxW / tw
            paint.textSize = size
            tw = paint.measureText(text)
        }
        val x = (w - tw) / 2f
        val depth = (size * 0.13f).coerceAtLeast(3f)
        val base = h * 0.62f + (sin(t * 1.6f) * 1.5f * d)

        paint.shader = null
        paint.style = Paint.Style.FILL
        paint.color = 0x88000000.toInt()
        canvas.drawText(text, x + depth * 0.4f, base + depth + 7f * d, paint)

        // contorno preto grosso
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = size * 0.17f
        paint.color = 0xFF050505.toInt()
        canvas.drawText(text, x, base + depth, paint)
        canvas.drawText(text, x, base, paint)

        // lateral (extrusão)
        paint.style = Paint.Style.FILL
        var i = depth.toInt()
        while (i >= 1) {
            paint.color = if (i > depth * 0.5f) 0xFF2B2927.toInt() else 0xFF3C3936.toInt()
            canvas.drawText(text, x, base + i, paint)
            i--
        }

        // face de pedra
        paint.shader = LinearGradient(
            0f, base - size * 0.78f, 0f, base,
            intArrayOf(0xFFE2DED6.toInt(), 0xFFB1ACA4.toInt(), 0xFF8A857E.toInt()),
            floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP
        )
        canvas.drawText(text, x, base, paint)
        paint.shader = null

        // realce dourado fino no topo
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1.2f * d
        paint.color = 0x55FFE69A
        canvas.save()
        canvas.clipRect(0f, base - size * 0.8f, w, base - size * 0.52f)
        canvas.drawText(text, x, base, paint)
        canvas.restore()
        paint.style = Paint.Style.FILL
    }
}
