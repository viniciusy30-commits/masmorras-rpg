package com.viniciusy.masmorras

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import kotlin.math.abs
import kotlin.math.sin

/** Primitivas de arte em blocos (voxel 3D falso) no estilo Minecraft Dungeons. */
object VoxelArt {
    var time = 0f
    var flash = 0f
    private val path = Path()

    class Pose {
        var handRX = 0f
        var handRY = 0f
        var handLX = 0f
        var handLY = 0f
        var headCX = 0f
        var headBase = 0f
        var headW = 0f
        var headH = 0f
        var torsoTop = 0f
        var side = 1f
    }

    val pose = Pose()

    fun shade(c: Int, f: Float): Int {
        val a = c ushr 24 and 0xFF
        val r = ((c shr 16 and 0xFF) * f).toInt().coerceIn(0, 255)
        val g = ((c shr 8 and 0xFF) * f).toInt().coerceIn(0, 255)
        val b = ((c and 0xFF) * f).toInt().coerceIn(0, 255)
        return (a shl 24) or (r shl 16) or (g shl 8) or b
    }

    fun mix(a: Int, b: Int, t: Float): Int {
        val al = a ushr 24 and 0xFF
        val r = ((a shr 16 and 0xFF) * (1f - t) + (b shr 16 and 0xFF) * t).toInt()
        val g = ((a shr 8 and 0xFF) * (1f - t) + (b shr 8 and 0xFF) * t).toInt()
        val bl = ((a and 0xFF) * (1f - t) + (b and 0xFF) * t).toInt()
        return (al shl 24) or (r shl 16) or (g shl 8) or bl
    }

    fun alpha(c: Int, a: Float): Int =
        (c and 0x00FFFFFF) or ((a.coerceIn(0f, 1f) * 255f).toInt() shl 24)

    fun tint(c: Int): Int =
        if (flash > 0.01f) mix(c, 0xFFFFFFFF.toInt(), (flash * 0.8f).coerceIn(0f, 1f)) else c

    fun box(
        canvas: Canvas, paint: Paint, x: Float, y: Float, w: Float, h: Float, d: Float,
        color: Int, topColor: Int = 0, sideColor: Int = 0
    ) {
        val c = tint(color)
        val l = x - w / 2f
        val r = x + w / 2f
        val t = y - h
        val dx = d * 0.6f

        paint.style = Paint.Style.FILL

        paint.color = if (sideColor != 0) tint(sideColor) else shade(c, 0.72f)
        path.reset()
        path.moveTo(r, y)
        path.lineTo(r + dx, y - dx)
        path.lineTo(r + dx, t - dx)
        path.lineTo(r, t)
        path.close()
        canvas.drawPath(path, paint)

        paint.color = if (topColor != 0) tint(topColor) else shade(c, 1.12f)
        path.reset()
        path.moveTo(l, t)
        path.lineTo(l + dx, t - dx)
        path.lineTo(r + dx, t - dx)
        path.lineTo(r, t)
        path.close()
        canvas.drawPath(path, paint)

        paint.color = shade(c, 0.92f)
        canvas.drawRect(l, t, r, y, paint)

        if (h > 6f) {
            paint.color = 0x22000000
            canvas.drawRect(l, y - h * 0.22f, r, y, paint)
        }
    }

    fun humanoid(
        c: Canvas, p: Paint, fx: Float, fy: Float, s: Float, phase: Float, moving: Boolean,
        faceX: Float, back: Boolean,
        skin: Int, shirt: Int, pants: Int, boots: Int, hair: Int,
        sleeve: Int = shirt, torsoW: Float = 16f, armW: Float = 5f, legW: Float = 7f,
        headW: Float = 14f, armsUp: Boolean = false, robe: Boolean = false,
        eyeColor: Int = 0xFF1A1A1A.toInt(), eyeSize: Float = 1f
    ) {
        val side = if (faceX > 0.3f) 1f else if (faceX < -0.3f) -1f else 0f
        val swing = if (moving) sin(phase) else 0f
        val bob = if (moving) abs(sin(phase)) * 1.6f * s else sin(time * 2.2f) * 0.6f * s
        val legH = 11f * s
        val torsoH = 15f * s
        val headH = 13f * s
        val headWs = headW * s

        if (robe) {
            box(c, p, fx - 3.5f * s, fy, 5f * s, 3f * s, 3f * s, boots)
            box(c, p, fx + 3.5f * s, fy, 5f * s, 3f * s, 3f * s, boots)
            box(c, p, fx, fy - 2f * s, (torsoW + 3f) * s, legH, 4f * s, pants)
        } else {
            val liftL = (swing * 3.5f * s).coerceAtLeast(0f)
            val liftR = (-swing * 3.5f * s).coerceAtLeast(0f)
            val lx = fx - legW * 0.55f * s
            val rx = fx + legW * 0.55f * s
            box(c, p, lx, fy - liftL - 4f * s, legW * s, 7f * s, 3f * s, pants)
            box(c, p, lx, fy - liftL, (legW + 1f) * s, 4f * s, 3.5f * s, boots)
            box(c, p, rx, fy - liftR - 4f * s, legW * s, 7f * s, 3f * s, pants)
            box(c, p, rx, fy - liftR, (legW + 1f) * s, 4f * s, 3.5f * s, boots)
        }

        val torsoBase = fy - legH - bob
        box(c, p, fx, torsoBase, torsoW * s, torsoH, 4f * s, shirt)
        val torsoTop = torsoBase - torsoH

        val armSw = if (moving) sin(phase) * 3f * s else 0f
        val ax = (torsoW / 2f + armW / 2f) * s
        val armH = if (armsUp) 10f * s else 14f * s
        val lBase = if (armsUp) torsoTop + 11f * s else torsoTop + 14f * s - armSw
        val rBase = if (armsUp) torsoTop + 11f * s else torsoTop + 14f * s + armSw
        box(c, p, fx - ax, lBase, armW * s, armH, 3f * s, sleeve)
        box(c, p, fx - ax, lBase, armW * s, 4f * s, 3f * s, skin)
        box(c, p, fx + ax, rBase, armW * s, armH, 3f * s, sleeve)
        box(c, p, fx + ax, rBase, armW * s, 4f * s, 3f * s, skin)

        val headBase = torsoTop + 1f * s
        box(c, p, fx, headBase, headWs, headH, 4.5f * s, if (back) hair else skin, hair)
        if (!back) {
            p.style = Paint.Style.FILL
            p.color = tint(hair)
            c.drawRect(fx - headWs / 2f, headBase - headH, fx + headWs / 2f, headBase - headH + 3.5f * s, p)
            val shx = side * 1.4f * s
            val ey = headBase - headH * 0.45f
            val ew = 1.15f * s * eyeSize
            val eh = 1.4f * s * eyeSize
            p.color = tint(eyeColor)
            c.drawRect(fx - 3.4f * s + shx - ew, ey - eh, fx - 3.4f * s + shx + ew, ey + eh, p)
            c.drawRect(fx + 3.4f * s + shx - ew, ey - eh, fx + 3.4f * s + shx + ew, ey + eh, p)
            p.color = tint(shade(skin, 0.7f))
            c.drawRect(fx - 1.8f * s + shx, ey + 4.2f * s, fx + 1.8f * s + shx, ey + 5.4f * s, p)
        }

        pose.handLX = fx - ax
        pose.handLY = lBase - 2f * s
        pose.handRX = fx + ax
        pose.handRY = rBase - 2f * s
        pose.headCX = fx
        pose.headBase = headBase
        pose.headW = headWs
        pose.headH = headH
        pose.torsoTop = torsoTop
        pose.side = if (side == 0f) 1f else side
    }
}
