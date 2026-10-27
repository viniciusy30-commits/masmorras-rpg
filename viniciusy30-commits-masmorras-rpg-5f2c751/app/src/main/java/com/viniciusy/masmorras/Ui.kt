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

    /** Retângulo arredondado com gradiente vertical (top -> bottom) e contorno. */
    fun round(ctx: Context, top: Int, bottom: Int, stroke: Int, radiusDp: Float = 8f, strokeDp: Float = 1.5f): GradientDrawable {
        val g = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(top, bottom))
        g.cornerRadius = dp(ctx, radiusDp)
        g.setStroke(dp(ctx, strokeDp).toInt().coerceAtLeast(1), stroke)
        return g
    }

    /** Botão do editor: pedra escura com friso dourado quando selecionado. */
    fun chipBg(ctx: Context, selected: Boolean, base: Int = 0xFF3A3530.toInt()): Drawable {
        val s = StateListDrawable()
        val press = if (selected) round(ctx, darken(GOLD, 0.25f), darken(GOLD, 0.45f), 0xFFFFE9A8.toInt())
                    else round(ctx, darken(base, 0.35f), darken(base, 0.15f), GOLD)
        val norm = if (selected) round(ctx, lighten(GOLD, 0.25f), darken(GOLD, 0.12f), 0xFFFFF3C4.toInt())
                   else round(ctx, lighten(base, 0.10f), darken(base, 0.30f), 0xFF8C7B5A.toInt())
        s.addState(intArrayOf(android.R.attr.state_pressed), press)
        s.addState(intArrayOf(), norm)
        return s
    }

    /** Painel escuro das barras do editor, com borda dourada fina. */
    fun dock(ctx: Context, radiusDp: Float = 10f): Drawable =
        round(ctx, 0xF0221C17.toInt(), 0xF0120F0C.toInt(), 0xFF8A6A2A.toInt(), radiusDp, 1.5f)

    /** Cartão da paleta (selecionado = moldura dourada). */
    fun card(ctx: Context, selected: Boolean): Drawable =
        if (selected) round(ctx, 0xFF5A4720.toInt(), 0xFF2E2410.toInt(), GOLD, 8f, 2.5f)
        else round(ctx, 0xFF3A332B.toInt(), 0xFF1E1914.toInt(), 0xFF5C503C.toInt(), 8f, 1.5f)
}
