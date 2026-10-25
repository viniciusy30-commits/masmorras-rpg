package com.viniciusy.masmorras

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.graphics.drawable.StateListDrawable

/** Helpers para montar o visual "pedra biselada" em código (chips do editor, emblemas das classes...). */
object Ui {
    val GOLD: Int = 0xFFF2B63D.toInt()
    val STONE: Int = 0xFF55514C.toInt()
    val FRAME: Int = 0xFF0C0B0A.toInt()
    val INK: Int = 0xFF2A1600.toInt()

    fun dp(ctx: Context, v: Float): Float = v * ctx.resources.displayMetrics.density

    fun lighten(c: Int, f: Float): Int = Color.argb(
        Color.alpha(c),
        (Color.red(c) + (255 - Color.red(c)) * f).toInt().coerceIn(0, 255),
        (Color.green(c) + (255 - Color.green(c)) * f).toInt().coerceIn(0, 255),
        (Color.blue(c) + (255 - Color.blue(c)) * f).toInt().coerceIn(0, 255)
    )

    fun darken(c: Int, f: Float): Int = Color.argb(
        Color.alpha(c),
        (Color.red(c) * (1f - f)).toInt().coerceIn(0, 255),
        (Color.green(c) * (1f - f)).toInt().coerceIn(0, 255),
        (Color.blue(c) * (1f - f)).toInt().coerceIn(0, 255)
    )

    private fun rect(c: Int): GradientDrawable {
        val g = GradientDrawable()
        g.setColor(c)
        return g
    }

    /** Quadrado com moldura preta + luz em cima/esquerda + sombra embaixo/direita. */
    fun bevel(ctx: Context, face: Int, unitDp: Float = 2f, pressed: Boolean = false): Drawable {
        val u = dp(ctx, unitDp).toInt().coerceAtLeast(1)
        val hi = if (pressed) darken(face, 0.5f) else lighten(face, 0.38f)
        val lo = if (pressed) lighten(face, 0.18f) else darken(face, 0.5f)
        val f = if (pressed) darken(face, 0.2f) else face
        val ld = LayerDrawable(arrayOf<Drawable>(rect(FRAME), rect(hi), rect(lo), rect(f)))
        ld.setLayerInset(1, u, u, u, u)
        ld.setLayerInset(2, 2 * u, 2 * u, u, u)
        ld.setLayerInset(3, 2 * u, 2 * u, 2 * u, 2 * u)
        return ld
    }

    /** Versão clicável (muda ao apertar). */
    fun button(ctx: Context, face: Int, unitDp: Float = 2f): StateListDrawable {
        val s = StateListDrawable()
        s.addState(intArrayOf(android.R.attr.state_pressed), bevel(ctx, face, unitDp, true))
        s.addState(intArrayOf(), bevel(ctx, face, unitDp, false))
        return s
    }
}
