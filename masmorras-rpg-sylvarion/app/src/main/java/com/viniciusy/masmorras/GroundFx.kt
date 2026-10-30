package com.viniciusy.masmorras

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Chão destruído (rachaduras que se abrem + cratera escura) deixado pelo Bárbaro no salto, no pouso e no impulso.
 * Posição em coordenadas do mundo (já com a altura do chão descontada, igual às partículas).
 */
class GroundFx(val x: Float, val y: Float, val radius: Float, val life: Float, seed: Int) {
    var age = 0f
    private val n = 11
    private val ang = FloatArray(n)
    private val jit = FloatArray(n)
    private val len = FloatArray(n)

    init {
        val r = java.util.Random(seed.toLong())
        for (i in 0 until n) {
            ang[i] = (i + r.nextFloat() * 0.7f) * (6.2832f / n)
            jit[i] = (r.nextFloat() - 0.5f) * 0.7f
            len[i] = 0.65f + r.nextFloat() * 0.35f
        }
    }

    fun draw(canvas: Canvas, paint: Paint, camX: Float, camY: Float, path: Path) {
        val g = min(1f, age / 0.14f)
        val fadeT = life * 0.35f
        val fade = if (age > life - fadeT) max(0f, (life - age) / fadeT) else 1f
        val sx = x - camX
        val sy = y - camY
        val rr = radius * (0.35f + 0.65f * g)
        val ry = rr * 0.55f
        paint.style = Paint.Style.FILL
        paint.color = (((0.38f * fade * 255f).toInt()) shl 24) or 0x00140E0A
        canvas.drawOval(sx - rr * 0.8f, sy - ry * 0.8f, sx + rr * 0.8f, sy + ry * 0.8f, paint)
        val glow = max(0f, 1f - age / 0.5f)
        paint.style = Paint.Style.STROKE
        paint.strokeCap = Paint.Cap.ROUND
        paint.strokeJoin = Paint.Join.ROUND
        for (pass in 0..2) {
            if (pass == 2 && glow <= 0.02f) continue
            paint.strokeWidth = when (pass) { 0 -> 5.5f; 1 -> 2.6f; else -> 1.6f }
            paint.color = when (pass) {
                0 -> (((0.85f * fade * 255f).toInt()) shl 24) or 0x001A120C
                1 -> (((0.9f * fade * 255f).toInt()) shl 24) or 0x00544534
                else -> (((0.9f * glow * fade * 255f).toInt()) shl 24) or 0x00FF7A2A
            }
            for (i in 0 until n) {
                val a0 = ang[i]
                val a1 = a0 + jit[i]
                val a2 = a0 + jit[i] * 0.3f
                val l = len[i] * g
                path.reset()
                path.moveTo(sx, sy)
                path.lineTo(sx + cos(a0) * rr * 0.4f * l, sy + sin(a0) * ry * 0.4f * l)
                path.lineTo(sx + cos(a1) * rr * 0.7f * l, sy + sin(a1) * ry * 0.7f * l)
                path.lineTo(sx + cos(a2) * rr * l, sy + sin(a2) * ry * l)
                canvas.drawPath(path, paint)
            }
        }
        paint.style = Paint.Style.FILL
        paint.strokeCap = Paint.Cap.BUTT
    }
}
