package com.viniciusy.masmorras

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import kotlin.math.max

/** Sprites em cubos (estilo Minecraft/Hytale). Cada sprite é desenhado UMA vez num bitmap. */
object VoxelSprites {

    /** padL = x da âncora (centro do footprint) dentro do bitmap; padB = folga abaixo da linha do chão. */
    class Spr(val bmp: Bitmap, val padL: Float, val padB: Float, val fx: Float = 0f, val fy: Float = 0f)

    private val p = Paint()
    private val vpath = Path()

    private fun col(v: Long): Int = v.toInt()
    private fun sh(c: Int, f: Float): Int = VoxelArt.shade(c, f)

    private fun hsh(a: Int, b: Int, k: Int): Int {
        var h = a * 374761393 + b * 668265263 + k * 1274126177
        h = (h xor (h ushr 13)) * 1274126177
        return (h xor (h ushr 16)) and 0x7FFFFFFF
    }

    private fun newBmp(w: Float, h: Float): Pair<Bitmap, Canvas> {
        val b = Bitmap.createBitmap(w.toInt().coerceAtLeast(8), h.toInt().coerceAtLeast(8), Bitmap.Config.ARGB_8888)
        return b to Canvas(b)
    }

    private fun fill(c: Canvas, l: Float, t: Float, r: Float, b: Float, color: Int) {
        p.style = Paint.Style.FILL
        p.color = color
        c.drawRect(l, t, r, b, p)
    }

    /** Preenche com "texels" de brilho variável (textura pixelada). */
    private fun tex(c: Canvas, l: Float, t: Float, r: Float, b: Float, color: Int, texel: Float, vr: Float, seed: Int) {
        p.style = Paint.Style.FILL
        var y = t
        var j = 0
        while (y < b) {
            val y2 = minOf(y + texel, b)
            var x = l
            var i = 0
            while (x < r) {
                val x2 = minOf(x + texel, r)
                val f = 1f + ((hsh(i, j, seed) % 1000) / 1000f - 0.5f) * 2f * vr
                p.color = sh(color, f)
                c.drawRect(x, y, x2, y2, p)
                x += texel
                i++
            }
            y += texel
            j++
        }
    }

    /** Cubo: frente (l,t,r,b) + lado direito + topo (oblíquo), com textura opcional. */
    private fun cube(
        c: Canvas, l: Float, t: Float, r: Float, b: Float, d: Float, color: Int,
        texel: Float = 0f, vr: Float = 0.1f, seed: Int = 0, top: Int = 0, side: Int = 0
    ) {
        p.style = Paint.Style.FILL
        if (d > 0f) {
            val dx = d * 0.6f
            val dy = d * 0.6f
            val sc = if (side != 0) side else sh(color, 0.66f)
            val tc = if (top != 0) top else sh(color, 1.2f)
            vpath.reset()
            vpath.moveTo(r, b); vpath.lineTo(r + dx, b - dy); vpath.lineTo(r + dx, t - dy); vpath.lineTo(r, t); vpath.close()
            p.color = sc
            c.drawPath(vpath, p)
            if (texel > 0f) {
                c.save(); c.clipPath(vpath)
                tex(c, r, t - dy, r + dx, b, sc, texel * 0.8f, vr, seed + 1)
                c.restore()
            }
            vpath.reset()
            vpath.moveTo(l, t); vpath.lineTo(l + dx, t - dy); vpath.lineTo(r + dx, t - dy); vpath.lineTo(r, t); vpath.close()
            p.color = tc
            c.drawPath(vpath, p)
            if (texel > 0f) {
                c.save(); c.clipPath(vpath)
                tex(c, l, t - dy, r + dx, t, tc, texel * 0.8f, vr, seed + 2)
                c.restore()
            }
        }
        if (texel > 0f) tex(c, l, t, r, b, color, texel, vr, seed) else fill(c, l, t, r, b, color)
        fill(c, l, b - 3f, r, b, 0x22000000)
    }

    /** Superfície de água sobre a face de topo de um cubo. */
    private fun waterTop(c: Canvas, l: Float, t: Float, r: Float, d: Float, color: Int, f: Float) {
        val dx = d * 0.6f
        val dy = d * 0.6f
        val cx = (l + r) / 2f + dx / 2f
        val cy = t - dy / 2f
        val xs = floatArrayOf(l, l + dx, r + dx, r)
        val ys = floatArrayOf(t, t - dy, t - dy, t)
        vpath.reset()
        for (i in 0..3) {
            val x = xs[i] + (cx - xs[i]) * f
            val y = ys[i] + (cy - ys[i]) * f
            if (i == 0) vpath.moveTo(x, y) else vpath.lineTo(x, y)
        }
        vpath.close()
        p.style = Paint.Style.FILL
        p.color = color
        c.drawPath(vpath, p)
        c.save(); c.clipPath(vpath)
        tex(c, l, t - dy, r + dx, t, color, 8f, 0.1f, 99)
        c.restore()
    }

    private fun shadow(c: Canvas, l: Float, r: Float, y: Float) {
        p.style = Paint.Style.FILL
        p.color = 0x38000000
        c.drawOval(l, y - 9f, r, y + 11f, p)
    }

    // ───────────────────────── ÁRVORES ─────────────────────────

    fun tree(variant: Int): Spr {
        val wd = 210f
        val hd = 300f
        val (bmp, c) = newBmp(wd, hd)
        val cx = wd / 2f
        val fy = hd - 12f
        shadow(c, cx - 46f, cx + 54f, fy)
        val pine = variant == 2 || variant == 3
        if (pine) {
            cube(c, cx - 8f, fy - 56f, cx + 8f, fy, 12f, col(0xFF5B3A1E), 6f, 0.12f, variant)
            val n = if (variant == 3) 8 else 6
            for (i in 0 until n) {
                val half = 52f - i * (if (variant == 3) 5.2f else 7f)
                val bot = fy - 38f - i * 26f
                val g = sh(col(0xFF22663F), if (i % 2 == 0) 1f else 0.9f)
                cube(c, cx - half, bot - 30f, cx + half, bot, half * 0.8f, g, 7f, 0.14f, variant * 10 + i, top = sh(g, 1.35f))
            }
            return Spr(bmp, cx, 12f)
        }
        val k = if (variant == 1) 1.2f else if (variant == 4) 0.9f else 1f
        val lc = when (variant) {
            4 -> col(0xFF86BE4F)
            5 -> col(0xFFF4A6C6)
            6 -> col(0xFFD9822B)
            else -> col(0xFF3E8E3A)
        }
        val trunk = when (variant) {
            4 -> col(0xFFE8E4DA)
            5 -> col(0xFF4A2F2A)
            else -> col(0xFF6B4423)
        }
        cube(c, cx - 10f * k, fy - 70f * k, cx + 10f * k, fy, 16f, trunk, 6f, 0.12f, variant)
        if (variant == 4) {
            for (i in 0..3) fill(c, cx - 10f + (i % 2) * 8f, fy - 56f + i * 14f, cx - 3f + (i % 2) * 8f, fy - 52f + i * 14f, 0xFF2A2A2A.toInt())
        }
        cube(c, cx - 52f * k, fy - 122f * k, cx + 52f * k, fy - 50f * k, 44f * k, lc, 8f, 0.16f, 1 + variant, top = sh(lc, 1.25f))
        cube(c, cx - 70f * k, fy - 104f * k, cx - 36f * k, fy - 64f * k, 28f * k, sh(lc, 0.92f), 8f, 0.16f, 2 + variant, top = sh(lc, 1.2f))
        cube(c, cx + 34f * k, fy - 118f * k, cx + 66f * k, fy - 80f * k, 28f * k, sh(lc, 1.05f), 8f, 0.16f, 3 + variant, top = sh(lc, 1.25f))
        cube(c, cx - 38f * k, fy - 160f * k, cx + 38f * k, fy - 112f * k, 36f * k, sh(lc, 1.03f), 8f, 0.16f, 4 + variant, top = sh(lc, 1.3f))
        cube(c, cx - 22f * k, fy - 190f * k, cx + 22f * k, fy - 154f * k, 26f * k, sh(lc, 1.08f), 8f, 0.16f, 5 + variant, top = sh(lc, 1.35f))
        if (variant == 5) {
            for (i in 0..6) fill(c, cx - 50f + i * 15f, fy - 4f - (i % 3) * 5f, cx - 44f + i * 15f, fy + 1f - (i % 3) * 5f, col(0xFFF8C6DA))
        }
        return Spr(bmp, cx, 12f)
    }

    // ───────────────────────── PEDRAS / RUÍNAS ─────────────────────────

    fun rock(v: Int): Spr {
        val (bmp, c) = newBmp(120f, 110f)
        val cx = 56f
        val fy = 100f
        shadow(c, cx - 38f, cx + 50f, fy)
        cube(c, cx - 30f, fy - 26f, cx + 26f, fy, 24f, col(0xFF84848C), 7f, 0.18f, v, top = col(0xFF9E9EA6))
        cube(c, cx - 18f, fy - 50f, cx + 14f, fy - 22f, 18f, col(0xFF9A9AA2), 7f, 0.18f, v + 1, top = col(0xFFB4B4BC))
        if (v % 3 == 0) tex(c, cx - 30f, fy - 30f, cx - 6f, fy - 22f, col(0xFF4C8A3C), 5f, 0.2f, v)
        return Spr(bmp, cx, 10f)
    }

    fun pillar(v: Int): Spr {
        val (bmp, c) = newBmp(130f, 220f)
        val cx = 60f
        val fy = 208f
        val ht = if (v % 2 == 0) 120f else 70f
        shadow(c, cx - 36f, cx + 50f, fy)
        cube(c, cx - 25f, fy - 14f, cx + 25f, fy, 18f, col(0xFF8D877C), 7f, 0.14f, v)
        cube(c, cx - 17f, fy - 14f - ht, cx + 17f, fy - 14f, 16f, col(0xFFB8B2A6), 7f, 0.12f, v + 3)
        if (ht > 100f) cube(c, cx - 24f, fy - 26f - ht, cx + 24f, fy - 14f - ht, 22f, col(0xFF9E988C), 7f, 0.12f, v + 4)
        else cube(c, cx - 17f, fy - 28f - ht, cx - 2f, fy - 14f - ht, 14f, col(0xFFB8B2A6), 7f, 0.14f, v + 5)
        tex(c, cx - 17f, fy - 14f - ht * 0.6f, cx - 8f, fy - 14f, col(0xFF4C8A3C), 5f, 0.2f, v)
        cube(c, cx + 28f, fy - 12f, cx + 50f, fy, 12f, col(0xFF8D877C), 6f, 0.14f, v + 6)
        return Spr(bmp, cx, 12f)
    }

    // ───────────────────────── PROPS DO VILAREJO ─────────────────────────

    fun well(): Spr {
        val (bmp, c) = newBmp(150f, 190f)
        val cx = 70f
        val fy = 178f
        shadow(c, cx - 42f, cx + 60f, fy)
        cube(c, cx - 30f, fy - 30f, cx + 30f, fy, 28f, col(0xFF8D8985), 7f, 0.16f, 11, top = col(0xFFA6A29C))
        waterTop(c, cx - 30f, fy - 30f, cx + 30f, 28f, col(0xFF2F86AD), 0.22f)
        cube(c, cx - 30f, fy - 94f, cx - 22f, fy - 28f, 8f, col(0xFF5B3A1E), 5f, 0.1f, 12)
        cube(c, cx + 22f, fy - 94f, cx + 30f, fy - 28f, 8f, col(0xFF5B3A1E), 5f, 0.1f, 13)
        val rc = col(0xFFB04632)
        cube(c, cx - 44f, fy - 112f, cx + 44f, fy - 94f, 30f, rc, 5f, 0.1f, 14, top = sh(rc, 1.3f))
        cube(c, cx - 30f, fy - 128f, cx + 30f, fy - 112f, 22f, sh(rc, 0.95f), 5f, 0.1f, 15, top = sh(rc, 1.3f))
        cube(c, cx - 16f, fy - 142f, cx + 16f, fy - 128f, 12f, rc, 5f, 0.1f, 16, top = sh(rc, 1.3f))
        fill(c, cx - 1f, fy - 92f, cx + 1f, fy - 62f, col(0xFFD7C9A0))
        cube(c, cx - 6f, fy - 64f, cx + 6f, fy - 52f, 6f, col(0xFF8A5A2B), 4f, 0.1f, 17)
        return Spr(bmp, cx, 12f)
    }

    fun fountain(): Spr {
        val (bmp, c) = newBmp(280f, 270f)
        val cx = 125f
        val fy = 256f
        val st = col(0xFF9A958F)
        shadow(c, cx - 100f, cx + 130f, fy)
        cube(c, cx - 88f, fy - 34f, cx + 88f, fy, 62f, st, 8f, 0.14f, 21, top = col(0xFFB6B1AA))
        waterTop(c, cx - 88f, fy - 34f, cx + 88f, 62f, col(0xFF3C95B5), 0.12f)
        for (sx in intArrayOf(-88, 76)) cube(c, cx + sx, fy - 48f, cx + sx + 12f, fy - 30f, 12f, sh(st, 1.05f), 6f, 0.12f, 22, top = col(0xFFC8C3BC))
        cube(c, cx - 16f, fy - 34f - 56f, cx + 16f, fy - 34f, 18f, sh(st, 1.05f), 7f, 0.12f, 23)
        val y2 = fy - 34f - 56f
        cube(c, cx - 46f, y2 - 18f, cx + 46f, y2, 34f, st, 7f, 0.14f, 24, top = col(0xFFB6B1AA))
        waterTop(c, cx - 46f, y2 - 18f, cx + 46f, 34f, col(0xFF3C95B5), 0.14f)
        cube(c, cx - 7f, y2 - 18f - 32f, cx + 7f, y2 - 18f, 8f, sh(st, 1.05f), 6f, 0.1f, 25)
        cube(c, cx - 10f, y2 - 18f - 50f, cx + 10f, y2 - 18f - 32f, 10f, col(0xFFE8C14E), 5f, 0.12f, 26, top = col(0xFFFFE08A))
        return Spr(bmp, cx + 6f, 14f, cx + 12f, y2 - 40f)
    }

    fun stall(v: Int): Spr {
        val (bmp, c) = newBmp(190f, 180f)
        val l = 30f
        val fy = 166f
        shadow(c, l, l + 150f, fy)
        cube(c, l + 2f, fy - 100f, l + 10f, fy, 6f, col(0xFF5B3A1E), 5f, 0.1f, v)
        cube(c, l + 110f, fy - 100f, l + 118f, fy, 6f, col(0xFF5B3A1E), 5f, 0.1f, v + 1)
        cube(c, l, fy - 34f, l + 120f, fy, 22f, col(0xFF8A5A2B), 6f, 0.12f, v + 2, top = col(0xFFA8743C))
        val gc = intArrayOf(col(0xFFD6453A), col(0xFF6FBF4A), col(0xFFF2C14E), col(0xFFE59B3C), col(0xFF9B59B6))
        for (k in 0..4) {
            val g = gc[(v + k) % 5]
            cube(c, l + 8f + k * 22f, fy - 48f, l + 24f + k * 22f, fy - 34f, 8f, g, 4f, 0.12f, v + k, top = sh(g, 1.3f))
        }
        for (k in 0 until 8) {
            val cc = if (k % 2 == 0) col(0xFFC63D32) else col(0xFFF1E9D8)
            cube(c, l - 6f + k * 16f, fy - 124f, l + 10f + k * 16f, fy - 100f, 30f, cc, 5f, 0.06f, k, top = sh(cc, 1.15f))
        }
        return Spr(bmp, l + 60f, 14f)
    }

    fun lamp(): Spr {
        val (bmp, c) = newBmp(80f, 160f)
        val cx = 34f
        val fy = 150f
        shadow(c, cx - 14f, cx + 24f, fy)
        val dk = col(0xFF2E2A26)
        cube(c, cx - 10f, fy - 10f, cx + 10f, fy, 8f, dk, 4f, 0.1f, 1)
        cube(c, cx - 3f, fy - 78f, cx + 3f, fy - 10f, 5f, dk, 0f, 0f, 2)
        cube(c, cx - 11f, fy - 104f, cx + 11f, fy - 78f, 14f, col(0xFFFFD27A), 5f, 0.1f, 3, top = col(0xFFFFE9B0))
        fill(c, cx - 11f, fy - 104f, cx - 8f, fy - 78f, dk)
        fill(c, cx + 8f, fy - 104f, cx + 11f, fy - 78f, dk)
        cube(c, cx - 13f, fy - 114f, cx + 13f, fy - 104f, 16f, dk, 4f, 0.1f, 4)
        return Spr(bmp, cx, 10f, cx + 4f, fy - 90f)
    }

    fun bench(): Spr {
        val (bmp, c) = newBmp(170f, 100f)
        val cx = 75f
        val fy = 90f
        val wd = col(0xFF9A6A3A)
        shadow(c, cx - 56f, cx + 70f, fy)
        cube(c, cx - 44f, fy - 56f, cx + 60f, fy - 36f, 5f, sh(wd, 0.9f), 5f, 0.12f, 1, top = sh(wd, 1.2f))
        cube(c, cx - 48f, fy - 22f, cx - 40f, fy, 8f, col(0xFF5B3A1E), 4f, 0.1f, 2)
        cube(c, cx + 40f, fy - 22f, cx + 48f, fy, 8f, col(0xFF5B3A1E), 4f, 0.1f, 3)
        cube(c, cx - 54f, fy - 28f, cx + 54f, fy - 20f, 20f, wd, 5f, 0.12f, 4, top = col(0xFFB98550))
        return Spr(bmp, cx, 10f)
    }

    fun flowerbed(v: Int): Spr {
        val (bmp, c) = newBmp(170f, 110f)
        val cx = 75f
        val fy = 100f
        shadow(c, cx - 62f, cx + 74f, fy)
        cube(c, cx - 58f, fy - 14f, cx + 58f, fy, 30f, col(0xFF9C9690), 6f, 0.14f, v, top = col(0xFF5B3A1E))
        val fc = intArrayOf(col(0xFFE53935), col(0xFFFFD54F), col(0xFFFF8FB1), col(0xFFF5F5F5), col(0xFF64B5F6))
        for (k in 0 until 8) {
            val x = cx - 50f + k * 13f
            val yb = fy - 16f - (if (k % 2 == 0) 2f else 10f)
            cube(c, x - 2f, yb - 10f, x + 5f, yb, 0f, col(0xFF2F7A2F), 0f, 0f, k)
            cube(c, x - 3f, yb - 20f, x + 8f, yb - 9f, 5f, fc[(v + k) % 5], 3f, 0.1f, k + 5, top = sh(fc[(v + k) % 5], 1.3f))
        }
        return Spr(bmp, cx, 10f)
    }

    // ───────────────────────── CASAS E PRÉDIOS ─────────────────────────

    private fun win(c: Canvas, cx: Float, cy: Float, shutter: Int, flowers: Boolean, seed: Int) {
        val dk = col(0xFF4A2F18)
        fill(c, cx - 18f, cy - 20f, cx + 18f, cy + 20f, dk)
        tex(c, cx - 14f, cy - 16f, cx + 14f, cy + 16f, col(0xFFFFD98A), 7f, 0.1f, seed)
        fill(c, cx - 14f, cy - 16f, cx + 14f, cy - 8f, 0x30FFFFFF)
        fill(c, cx - 1.5f, cy - 16f, cx + 1.5f, cy + 16f, dk)
        fill(c, cx - 14f, cy - 1.5f, cx + 14f, cy + 1.5f, dk)
        tex(c, cx - 26f, cy - 18f, cx - 18f, cy + 18f, shutter, 4f, 0.1f, seed + 1)
        tex(c, cx + 18f, cy - 18f, cx + 26f, cy + 18f, shutter, 4f, 0.1f, seed + 2)
        fill(c, cx - 20f, cy + 20f, cx + 20f, cy + 25f, col(0xFF9C9690))
        if (flowers) {
            tex(c, cx - 18f, cy + 25f, cx + 18f, cy + 33f, col(0xFF6D4524), 4f, 0.1f, seed)
            val fc = intArrayOf(col(0xFFE53935), col(0xFFFFD54F), col(0xFFFF8FB1), col(0xFFF5F5F5))
            for (k in 0..3) fill(c, cx - 16f + k * 9f, cy + 20f, cx - 11f + k * 9f, cy + 26f, fc[k])
        }
    }

    fun house(kind: Int, style: Int, w: Int, h: Int, v: Int): Spr {
        val ts = 64f
        val wTot = w * ts
        val d = h * ts * 0.5f
        val dd = d * 0.6f
        val church = kind == 11
        val stories = if (kind == 8 || church || (kind == 2 && style == 3)) 2 else 1
        val storyH = ts * 1.2f
        val wallH = storyH * stories
        val ov = 16f
        val step = 17f
        val layerH = 15f
        val towerW = if (church) ts * 1.7f else 0f
        val rw = wTot - (if (church) towerW - 8f else 0f)
        val n = (((rw + 2f * ov) / 2f - 14f) / step).toInt().coerceIn(3, 9)
        val roofH = n * layerH
        val towerBody = wallH + ts * 1.3f
        val spireH = 7 * 18f
        val topH = if (church) max(wallH + roofH, towerBody + spireH + 34f) else wallH + roofH
        val padL = 40f
        val padR = dd + ov + 44f
        val padB = 26f
        val hh = topH + dd + 56f + padB
        val (bmp, c) = newBmp(wTot + padL + padR, hh)
        val x0 = padL
        val fy = hh - padB

        val cols: IntArray = when (kind) {
            8 -> intArrayOf(col(0xFFB07C4C), col(0xFFA8572B))
            9 -> intArrayOf(col(0xFFA6392D), col(0xFF5B4A44))
            10 -> intArrayOf(col(0xFF7E7C7A), col(0xFF4B525C))
            11 -> intArrayOf(col(0xFFDAD5CC), col(0xFF4A6A9A))
            else -> when (style) {
                0 -> intArrayOf(col(0xFFE8DBBA), col(0xFFB04632))
                1 -> intArrayOf(col(0xFF8F8C88), col(0xFF52708F))
                2 -> intArrayOf(col(0xFF9A6A3A), col(0xFFC8A445))
                else -> intArrayOf(col(0xFFF0EBE0), col(0xFF3F7F58))
            }
        }
        val wallC = cols[0]
        val roofC = cols[1]
        val stone = kind == 10 || church || (kind == 2 && style == 1)
        val timber = kind == 8 || (kind == 2 && (style == 0 || style == 3))
        val logs = kind == 2 && style == 2
        val thatch = kind == 2 && style == 2
        val dark = col(0xFF4A2F18)
        val wood = col(0xFF5A3A22)
        val nl = x0 + (if (church) towerW - 8f else 0f)
        val xr = x0 + wTot
        val w2 = xr - nl
        val doorX = nl + w2 / 2f
        val yEave = fy - wallH + 8f

        shadow(c, x0 + 10f, xr + dd + 30f, fy)
        cube(c, x0 - 3f, fy - 12f, xr + 3f, fy, d, col(0xFF7B7773), 7f, 0.15f, 5)
        cube(c, nl, fy - wallH, xr, fy, d, wallC, 8f, if (stone) 0.16f else 0.06f, v, top = sh(wallC, 1.1f))
        if (logs) { var y = fy - wallH + 14f; while (y < fy - 12f) { fill(c, nl, y, xr, y + 2f, 0x38000000); y += 16f } }
        if (kind == 9) { var x = nl + 14f; while (x < xr - 8f) { fill(c, x, fy - wallH, x + 2f, fy, 0x38000000); x += 16f } }
        if (stone) {
            var y = fy - wallH + 16f
            var r = 0
            while (y < fy - 12f) {
                fill(c, nl, y, xr, y + 1.5f, 0x2A000000)
                var x = nl + (if (r % 2 == 0) 0f else 12f)
                while (x < xr) { fill(c, x, y - 16f, x + 1.5f, y, 0x22000000); x += 24f }
                y += 16f; r++
            }
        }
        tex(c, nl, fy - 12f, xr, fy, col(0xFF77706A), 6f, 0.15f, v + 7)
        if (timber) {
            tex(c, nl, fy - wallH, nl + 8f, fy - 12f, wood, 5f, 0.12f, v)
            tex(c, xr - 8f, fy - wallH, xr, fy - 12f, wood, 5f, 0.12f, v + 1)
            var xp = nl + ts * 1.4f
            while (xp < xr - 24f) { tex(c, xp - 4f, fy - wallH, xp + 4f, fy - 12f, wood, 5f, 0.12f, v + 2); xp += ts * 1.4f }
            for (s in 1..stories) tex(c, nl, fy - storyH * s + (if (s == stories) 0f else -4f), xr, fy - storyH * s + (if (s == stories) 8f else 4f), wood, 5f, 0.12f, v + 3)
        }

        // portas e janelas
        val shutters = intArrayOf(col(0xFF3E7D4F), col(0xFF3F6FA8), col(0xFFB5432F), col(0xFF6A4C93))
        val dw = if (kind == 8) 52f else if (kind == 9) 70f else 34f
        val dh = if (kind == 9) 74f else if (kind == 8) 66f else 58f
        if (church) {
            val glass = intArrayOf(col(0xFF4A78C8), col(0xFFC8453A), col(0xFFE8C14E), col(0xFF4FA36B))
            val slots = (w2 / 74f).toInt().coerceAtLeast(2)
            for (k in 0 until slots) {
                val gx = nl + w2 / slots * (k + 0.5f)
                if (kotlin.math.abs(gx - doorX) < 44f) continue
                fill(c, gx - 13f, fy - storyH * 1.45f, gx + 13f, fy - storyH * 0.25f, dark)
                tex(c, gx - 10f, fy - storyH * 1.45f + 3f, gx + 10f, fy - storyH * 0.25f - 3f, glass[(v + k) % 4], 5f, 0.18f, v + k)
                fill(c, gx - 1f, fy - storyH * 1.45f, gx + 1f, fy - storyH * 0.25f, dark)
            }
        } else if (kind != 9) {
            val slots = (w2 / (ts * 1.1f)).toInt().coerceAtLeast(2)
            for (s in 0 until stories) for (k in 0 until slots) {
                val gx = nl + w2 / slots * (k + 0.5f)
                if (s == 0 && kotlin.math.abs(gx - doorX) < 42f) continue
                win(c, gx, fy - storyH * (s + 0.55f), shutters[(v + k + s) % 4], s == 0 && (v + k) % 2 == 0, v + k * 3 + s)
            }
        }
        // porta
        if (kind == 9) {
            val wf = col(0xFFF1EBDD)
            fill(c, doorX - dw / 2 - 4f, fy - dh - 4f, doorX + dw / 2 + 4f, fy, wf)
            tex(c, doorX - dw / 2, fy - dh, doorX + dw / 2, fy, col(0xFF8E2F25), 5f, 0.1f, v)
            for (t in 0..9) {
                val xx = doorX - dw / 2 + t * (dw / 9f)
                fill(c, xx - 2f, fy - dh + t * (dh / 9f) - 2f, xx + 3f, fy - dh + t * (dh / 9f) + 3f, wf)
                fill(c, xx - 2f, fy - t * (dh / 9f) - 3f, xx + 3f, fy - t * (dh / 9f) + 2f, wf)
            }
            fill(c, doorX - 1.5f, fy - dh, doorX + 1.5f, fy, wf)
            tex(c, doorX - 14f, fy - dh - 30f, doorX + 14f, fy - dh - 8f, col(0xFF2A1D14), 5f, 0.1f, v)
        } else {
            fill(c, doorX - dw / 2 - 4f, fy - dh - 4f, doorX + dw / 2 + 4f, fy, dark)
            tex(c, doorX - dw / 2, fy - dh, doorX + dw / 2, fy, col(0xFF7A4E2A), 5f, 0.12f, v)
            var gx = doorX - dw / 2 + 11f
            while (gx < doorX + dw / 2 - 4f) { fill(c, gx, fy - dh, gx + 1.5f, fy, 0x44000000); gx += 11f }
            fill(c, doorX + dw / 2 - 11f, fy - dh * 0.45f, doorX + dw / 2 - 6f, fy - dh * 0.45f + 5f, col(0xFFE0B85C))
            if (church) {
                fill(c, doorX - dw / 2, fy - dh, doorX + dw / 2, fy - dh + 6f, col(0xFF3B2412))
            }
        }
        cube(c, doorX - dw / 2 - 10f, fy - 6f, doorX + dw / 2 + 10f, fy, 8f, col(0xFF9C9690), 6f, 0.12f, v + 8)
        // lanternas ao lado da porta
        if (kind != 9) {
            cube(c, doorX + dw / 2 + 12f, fy - dh - 6f, doorX + dw / 2 + 24f, fy - dh + 10f, 6f, col(0xFFFFD27A), 4f, 0.1f, v, top = col(0xFFFFE9B0))
        }
        // sacada da mansão
        if (kind == 2 && style == 3) {
            cube(c, doorX - 38f, fy - storyH - 2f, doorX + 38f, fy - storyH + 6f, 14f, col(0xFF8A5A2B), 5f, 0.12f, v + 9, top = col(0xFFA8743C))
            cube(c, doorX - 38f, fy - storyH + 6f, doorX - 30f, fy, 8f, col(0xFFF0EBE0), 5f, 0.06f, v + 10)
            cube(c, doorX + 30f, fy - storyH + 6f, doorX + 38f, fy, 8f, col(0xFFF0EBE0), 5f, 0.06f, v + 11)
            for (k in 0..5) fill(c, doorX - 36f + k * 14f, fy - storyH - 20f, doorX - 33f + k * 14f, fy - storyH - 2f, wood)
            fill(c, doorX - 38f, fy - storyH - 24f, doorX + 38f, fy - storyH - 19f, wood)
        }
        // detalhes de cada tipo
        if (kind == 8) {
            fill(c, nl + 6f, fy - storyH - 28f, nl + 66f, fy - storyH - 22f, dark)
            tex(c, nl + 30f, fy - storyH - 22f, nl + 66f, fy - storyH + 10f, col(0xFF8A5A2B), 5f, 0.12f, v)
            fill(c, nl + 40f, fy - storyH - 14f, nl + 54f, fy - storyH + 2f, col(0xFFF6F1E6))
            fill(c, nl + 40f, fy - storyH - 14f, nl + 54f, fy - storyH - 9f, col(0xFFF2C14E))
            for (b in 0..1) {
                val bx = xr - 46f + b * 30f
                cube(c, bx, fy - 36f, bx + 24f, fy, 12f, col(0xFF8D5A2B), 5f, 0.12f, v + b, top = col(0xFFA9743F))
                fill(c, bx, fy - 26f, bx + 24f, fy - 22f, col(0xFF37474F))
                fill(c, bx, fy - 10f, bx + 24f, fy - 6f, col(0xFF37474F))
            }
        }
        if (kind == 9) {
            for (b in 0..1) cube(c, x0 - 28f + b * 4f, fy - 28f - b * 26f, x0 + 8f + b * 4f, fy - b * 26f, 14f, col(0xFFD9B84A), 5f, 0.2f, v + b, top = col(0xFFEBCF6A))
        }
        if (kind == 10) {
            fill(c, x0 + 10f, fy - 74f, x0 + 94f, fy - 12f, col(0xFF1E1816))
            tex(c, x0 + 20f, fy - 40f, x0 + 84f, fy - 12f, col(0xFFFF7A1F), 6f, 0.25f, v)
            tex(c, x0 + 36f, fy - 54f, x0 + 70f, fy - 38f, col(0xFFFFB347), 5f, 0.2f, v + 1)
            cube(c, x0 + 110f, fy - 28f, x0 + 140f, fy - 6f, 10f, col(0xFF2B2F36), 5f, 0.12f, v, top = col(0xFF4B525C))
            cube(c, x0 + 118f, fy - 6f, x0 + 132f, fy, 8f, wood, 4f, 0.1f, v)
        }
        // beiral + telhado em degraus
        fill(c, nl, yEave, xr, yEave + 12f, 0x44000000)
        for (i in 0 until n) {
            val l = nl - ov + i * step
            val r = xr + ov - i * step
            val t = yEave - (i + 1) * layerH
            val b = yEave - i * layerH + (if (i == 0) 6f else 2f)
            val rc = sh(roofC, if (i % 2 == 0) 1f else 0.92f)
            cube(c, l, t, r, b, d, rc, 5f, if (thatch) 0.2f else 0.1f, v + i, top = sh(roofC, 1.28f))
        }
        // chaminé
        var fxx = 0f
        var fyy = 0f
        if (kind == 2 || kind == 8 || kind == 10) {
            val cw = if (kind == 10) 30f else 24f
            val chx = nl + w2 * 0.74f
            val top = yEave - roofH - (if (kind == 10) 40f else 22f)
            cube(c, chx, top, chx + cw, yEave - roofH * 0.42f, 16f, col(0xFF7A6E66), 6f, 0.15f, v)
            cube(c, chx - 3f, top - 8f, chx + cw + 3f, top, 20f, col(0xFF5E544E), 5f, 0.12f, v + 1)
            fxx = chx + cw / 2f
            fyy = top - 12f
        }
        // torre e agulha da igreja
        if (church) {
            val tl = x0
            val tr = x0 + towerW
            val tc = (tl + tr) / 2f
            cube(c, tl - 3f, fy - 12f, tr + 3f, fy, d * 0.8f, col(0xFF7B7773), 7f, 0.15f, 31)
            cube(c, tl, fy - towerBody, tr, fy, d * 0.8f, sh(wallC, 0.97f), 8f, 0.16f, v + 9, top = sh(wallC, 1.1f))
            fill(c, tc - 15f, fy - towerBody + 34f, tc + 15f, fy - towerBody + 64f, dark)
            fill(c, tc - 12f, fy - towerBody + 37f, tc + 12f, fy - towerBody + 61f, col(0xFFF6F1E6))
            fill(c, tc - 1f, fy - towerBody + 41f, tc + 1f, fy - towerBody + 50f, dark)
            fill(c, tc, fy - towerBody + 49f, tc + 8f, fy - towerBody + 51f, dark)
            fill(c, tc - 12f, fy - towerBody + 84f, tc + 12f, fy - towerBody + 124f, col(0xFF2A1D14))
            fill(c, tc - 10f, fy - towerBody + 88f, tc + 10f, fy - towerBody + 124f, col(0xFF1A120C))
            fill(c, tc - 8f, fy - wallH * 0.75f, tc + 8f, fy - wallH * 0.15f, dark)
            tex(c, tc - 6f, fy - wallH * 0.75f + 3f, tc + 6f, fy - wallH * 0.15f - 3f, col(0xFF4A78C8), 4f, 0.15f, v)
            val by0 = fy - towerBody
            for (k in 0 until 7) {
                val hw = (towerW / 2f + 10f) * (1f - k / 7f) + 3f
                cube(c, tc - hw, by0 - (k + 1) * 18f, tc + hw, by0 - k * 18f + 2f, hw * 0.7f, sh(roofC, if (k % 2 == 0) 1f else 0.9f), 5f, 0.1f, v + k, top = sh(roofC, 1.3f))
            }
            val ty = by0 - 7 * 18f
            fill(c, tc - 2.5f, ty - 32f, tc + 2.5f, ty, col(0xFFE8C14E))
            fill(c, tc - 9f, ty - 24f, tc + 9f, ty - 19f, col(0xFFE8C14E))
        }
        return Spr(bmp, x0 + wTot / 2f, padB, fxx, fyy)
    }
}
