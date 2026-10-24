package com.viniciusy.masmorras

import android.graphics.Bitmap
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/** Texturas 16x16 estilo Minecraft (geradas por código, sem arquivos). Cores em RGB 0xRRGGBB. */
object Tex {
    const val GRASS = 0; const val GRASS2 = 1; const val DGRASS = 2; const val DIRT = 3; const val DIRT2 = 4
    const val COBBLE = 5; const val STONEBRICK = 6; const val REDBRICK = 7; const val PLANKS = 8
    const val PLANKS_D = 9; const val PLANKS_L = 10; const val BARN_V = 11; const val VPLANKS = 12
    const val LOG = 13; const val PLASTER = 14; const val PLASTER_W = 15; const val PLASTER_Y = 16
    const val PLASTER_B = 17; const val ROOF_RED = 18; const val ROOF_BLUE = 19; const val ROOF_GREEN = 20
    const val ROOF_BROWN = 21; const val ROOF_SLATE = 22; const val THATCH = 23; const val GLASS = 24
    const val GLASS_B = 25; const val WATER = 26; const val DEEP = 27; const val SAND = 28
    const val LEAF = 29; const val LEAF_D = 30; const val LEAF_P = 31; const val LEAF_O = 32
    const val LEAF_Y = 33; const val BARK = 34; const val BIRCH = 35; const val FLAG = 36
    const val FLAG2 = 37; const val FLAG3 = 38; const val METAL = 39; const val GOLD = 40
    const val EMBER = 41; const val DARK = 42; const val FLOOR = 43; const val CARPET = 44

    private val cache = arrayOfNulls<IntArray>(48)

    fun get(id: Int): IntArray = cache[id] ?: gen(id).also { cache[id] = it }

    fun h(x: Int, y: Int, k: Int): Int {
        var v = x * 374761393 + y * 668265263 + k * 1274126177
        v = (v xor (v ushr 13)) * 1274126177
        return (v xor (v ushr 16)) and 0x7FFFFFFF
    }

    fun sh(c: Int, f: Float): Int {
        val r = (((c shr 16) and 255) * f).toInt().coerceIn(0, 255)
        val g = (((c shr 8) and 255) * f).toInt().coerceIn(0, 255)
        val b = ((c and 255) * f).toInt().coerceIn(0, 255)
        return (r shl 16) or (g shl 8) or b
    }

    private fun n2(i: Int, j: Int, k: Int, cl: Int): Float = (h(i / cl, j / cl, k) % 1000) / 1000f

    private fun mk(fn: (Int, Int) -> Int): IntArray {
        val t = IntArray(256)
        for (j in 0..15) for (i in 0..15) t[j * 16 + i] = fn(i, j)
        return t
    }

    private var vS = 0
    private var vD1 = 0f
    private var vD2 = 0f

    private fun vor(i: Int, j: Int, k: Int, n: Int) {
        var best = 1e9f
        var second = 1e9f
        var bs = -1
        for (s in 0 until n) {
            val px = h(s, 1, k) % 16
            val py = h(s, 2, k) % 16
            for (ox in -16..16 step 16) for (oy in -16..16 step 16) {
                val dx = (i - px - ox).toFloat()
                val dy = (j - py - oy).toFloat()
                val d = dx * dx + dy * dy
                if (d < best) { second = best; best = d; bs = s } else if (d < second) second = d
            }
        }
        vS = bs; vD1 = sqrt(best); vD2 = sqrt(second)
    }

    private fun grass(b: Int, k: Int) = mk { i, j ->
        val n = n2(i, j, k, 2)
        var c = sh(b, 0.86f + 0.28f * n)
        if (h(i, j, k + 9) % 11 == 0) c = sh(b, 1.22f)
        if (h(i, j, k + 5) % 9 == 0) c = sh(b, 0.72f)
        c
    }

    private fun dirt(b: Int, k: Int) = mk { i, j ->
        val n = n2(i, j, k, 2)
        var c = sh(b, 0.84f + 0.3f * n)
        if (h(i, j, k + 3) % 13 == 0) c = sh(b, 1.28f)
        if (h(i, j, k + 7) % 10 == 0) c = sh(b, 0.7f)
        c
    }

    private fun cobble(b: Int, k: Int, n: Int) = mk { i, j ->
        vor(i, j, k, n)
        val edge = vD2 - vD1
        val v = 0.86f + 0.26f * ((h(vS, 3, k) % 100) / 100f)
        var c = sh(b, v)
        if (edge < 1.6f) c = sh(b, 0.52f)
        else if (h(i, j, k + 1) % 7 == 0) c = sh(c, 1.08f)
        c
    }

    private fun brick(b: Int, k: Int, bw: Int, bh: Int, mortar: Int) = mk { i, j ->
        val row = j / bh
        val off = if (row % 2 == 1) bw / 2 else 0
        val bi = ((i + off) % 16) / bw
        if (j % bh == bh - 1 || (i + off) % bw == bw - 1) mortar
        else {
            var c = sh(b, 0.88f + 0.24f * ((h(bi + row * 3, row, k) % 100) / 100f))
            if (j % bh == 0) c = sh(c, 1.12f)
            if (h(i, j, k + 2) % 8 == 0) c = sh(c, 0.93f)
            c
        }
    }

    private fun planks(b: Int, k: Int, bh: Int) = mk { i, j ->
        val row = j / bh
        val seam = (h(row, 0, k) % 12) + 2
        var c = sh(b, 0.9f + 0.16f * ((h(row, 5, k) % 100) / 100f))
        if (j % bh == bh - 1) sh(b, 0.62f)
        else if (i == seam) sh(b, 0.62f)
        else {
            if (j % bh == 0) c = sh(c, 1.1f)
            if (h(i / 3, j, k + 1) % 5 == 0) c = sh(c, 0.9f)
            if (h(i, j, k + 2) % 17 == 0) c = sh(c, 0.8f)
            c
        }
    }

    private fun vplanks(b: Int, k: Int) = mk { i, j ->
        var c = sh(b, 0.9f + 0.16f * ((h(i / 4, 5, k) % 100) / 100f))
        if (i % 4 == 3) sh(b, 0.6f)
        else {
            if (i % 4 == 0) c = sh(c, 1.1f)
            if (h(i, j / 3, k + 1) % 6 == 0) c = sh(c, 0.88f)
            c
        }
    }

    private fun logT(b: Int, k: Int) = mk { i, j ->
        var c = sh(b, 0.8f + 0.3f * ((h(i, j / 5, k) % 100) / 100f))
        if (j % 8 == 7) c = sh(b, 0.55f) else if (j % 8 == 0) c = sh(c, 1.12f)
        c
    }

    private fun plaster(b: Int, k: Int) = mk { i, j ->
        var c = sh(b, 0.94f + 0.1f * n2(i, j, k, 3))
        if (h(i, j, k + 1) % 19 == 0) c = sh(c, 0.9f)
        c
    }

    private fun shingle(b: Int, k: Int) = mk { i, j ->
        val row = j / 4
        val off = if (row % 2 == 1) 4 else 0
        val si = ((i + off) % 16) / 4
        var c = sh(b, 0.9f + 0.18f * ((h(si + row * 4, row, k) % 100) / 100f))
        val yy = j % 4
        if (yy == 3) c = sh(b, 0.62f) else if (yy == 2) c = sh(c, 0.88f) else if (yy == 0) c = sh(c, 1.12f)
        if ((i + off) % 4 == 3) c = sh(c, 0.8f)
        c
    }

    private fun thatch(b: Int, k: Int) = mk { i, j ->
        val n = (h((i + j / 2) % 16, j / 3, k) % 100) / 100f
        var c = sh(b, 0.8f + 0.4f * n)
        if (j % 4 == 3) c = sh(c, 0.78f)
        if (h(i, j, k + 1) % 6 == 0) c = sh(c, 0.85f)
        c
    }

    private fun water(b: Int, k: Int) = mk { i, j ->
        var c = sh(b, 0.88f + 0.2f * n2(i, j, k, 2))
        if (j % 6 == 0 && h(i / 4, j, k) % 3 == 0) c = sh(b, 1.35f)
        c
    }

    private fun leaf(b: Int, k: Int) = mk { i, j ->
        var c = sh(b, 0.72f + 0.5f * n2(i, j, k, 3))
        if (h(i, j, k + 1) % 7 == 0) c = sh(b, 1.3f)
        if (h(i, j, k + 2) % 6 == 0) c = sh(b, 0.62f)
        c
    }

    private fun flag(a: Int, b: Int, k: Int) = mk { i, j ->
        var c = if (((i / 8) + (j / 8)) % 2 == 0) a else b
        c = sh(c, 0.94f + 0.12f * n2(i, j, k, 2))
        if (i % 8 == 0 || j % 8 == 0) c = sh(c, 0.62f) else if (i % 8 == 1 || j % 8 == 1) c = sh(c, 1.1f)
        c
    }

    private fun flat(b: Int, k: Int) = mk { i, j -> sh(b, 0.9f + 0.2f * n2(i, j, k, 2)) }

    private fun gen(id: Int): IntArray = when (id) {
        GRASS -> grass(0x5BA84A, 1)
        GRASS2 -> grass(0x4E9A40, 2)
        DGRASS -> grass(0x3E8344, 3)
        DIRT -> dirt(0x9B7B50, 4)
        DIRT2 -> dirt(0x8C6D47, 14)
        COBBLE -> cobble(0x8F8C88, 5, 8)
        STONEBRICK -> brick(0x8A8884, 8, 8, 4, 0x5B5855)
        REDBRICK -> brick(0xA2493A, 9, 8, 4, 0xC9BCA8)
        PLANKS -> planks(0xA87B45, 10, 4)
        PLANKS_D -> planks(0x7A5430, 11, 4)
        PLANKS_L -> planks(0xC49A5E, 12, 4)
        BARN_V -> vplanks(0xA23C2F, 16)
        VPLANKS -> vplanks(0x7A5430, 15)
        LOG -> logT(0x6B4A2B, 17)
        PLASTER -> plaster(0xE8DCBC, 18)
        PLASTER_W -> plaster(0xF1ECE2, 19)
        PLASTER_Y -> plaster(0xE9C98A, 20)
        PLASTER_B -> plaster(0xB9CBD8, 21)
        ROOF_RED -> shingle(0xB5472F, 22)
        ROOF_BLUE -> shingle(0x4F6F96, 23)
        ROOF_GREEN -> shingle(0x3F8059, 24)
        ROOF_BROWN -> shingle(0x7A4A2C, 25)
        ROOF_SLATE -> shingle(0x59616E, 26)
        THATCH -> thatch(0xCDAA4E, 27)
        GLASS -> mk { i, j ->
            var c = sh(0xFFD98A, 1f - 0.18f * (j / 16f))
            if ((i + j) % 8 < 2 && i > 2 && i < 13) c = sh(c, 1.2f)
            c
        }
        GLASS_B -> mk { i, j ->
            var c = sh(0x6FA8DC, 1f - 0.2f * (j / 16f))
            if ((i + j) % 8 < 2) c = sh(c, 1.2f)
            c
        }
        WATER -> water(0x3C95B5, 30)
        DEEP -> water(0x1F6C97, 31)
        SAND -> mk { i, j ->
            var c = sh(0xE0CC8E, 0.9f + 0.16f * n2(i, j, 33, 2))
            if (h(i, j, 34) % 12 == 0) c = sh(c, 0.85f)
            c
        }
        LEAF -> leaf(0x3F8A3A, 50)
        LEAF_D -> leaf(0x2B6B3C, 51)
        LEAF_P -> leaf(0xF2A5C4, 52)
        LEAF_O -> leaf(0xD98A2B, 53)
        LEAF_Y -> leaf(0x8DBE4F, 54)
        BARK -> mk { i, j ->
            var c = sh(0x6B4423, 0.72f + 0.4f * ((h(i, j / 6, 55) % 100) / 100f))
            if (i % 4 == 0) c = sh(c, 0.82f)
            c
        }
        BIRCH -> mk { i, j ->
            if (h(i / 2, j / 3, 57) % 6 == 0) 0x2A2A2A else sh(0xE8E4DA, 0.9f + 0.1f * n2(i, j, 56, 2))
        }
        FLAG -> flag(0xB9B2A4, 0xA39D90, 60)
        FLAG2 -> flag(0x7E8CA0, 0x6F7C90, 61)
        FLAG3 -> flag(0xCDBFA2, 0xBDAF92, 62)
        METAL -> flat(0x4A505A, 63)
        GOLD -> flat(0xE8C14E, 64)
        EMBER -> mk { i, j -> sh(0xFF7A1F, 0.7f + 0.6f * n2(i, j, 65, 2)) }
        DARK -> flat(0x241A14, 66)
        FLOOR -> mk { i, j ->
            val col = j / 4
            var c = sh(0xB98A52, 0.9f + 0.16f * ((h(col, 2, 40) % 100) / 100f))
            if (j % 4 == 3 || h(i + col * 5, 1, 40) % 16 == 0) c = sh(0xB98A52, 0.65f)
            c
        }
        else -> mk { i, j ->
            if (i == 0 || i == 15 || j == 0 || j == 15) 0xE8C14E else sh(0xA8353A, 0.92f + 0.1f * n2(i, j, 41, 2))
        }
    }
}

/** Sprites em cubos (estilo Minecraft/Hytale), pixel a pixel, desenhados UMA vez em bitmap. */
object VoxelSprites {

    /** padL = x da âncora (centro do footprint) dentro do bitmap; padB = folga abaixo da linha do chão. */
    class Spr(val bmp: Bitmap, val padL: Float, val padB: Float, val fx: Float = 0f, val fy: Float = 0f)

    private const val TS = 4

    class Pix(val w: Int, val h: Int) {
        val a = IntArray(w * h)
        fun put(x: Int, y: Int, r: Float, g: Float, b: Float, al: Float) {
            if (x < 0 || y < 0 || x >= w || y >= h || al <= 0f) return
            val i = y * w + x
            val d = a[i]
            val da = (d ushr 24) / 255f
            val oa = al + da * (1f - al)
            if (oa <= 0f) return
            val k = da * (1f - al)
            val nr = (r * al + ((d shr 16) and 255) * k) / oa
            val ng = (g * al + ((d shr 8) and 255) * k) / oa
            val nb = (b * al + (d and 255) * k) / oa
            a[i] = ((oa * 255f).toInt().coerceIn(0, 255) shl 24) or
                (nr.toInt().coerceIn(0, 255) shl 16) or (ng.toInt().coerceIn(0, 255) shl 8) or nb.toInt().coerceIn(0, 255)
        }
        fun toBitmap(): Bitmap {
            val b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            b.setPixels(a, 0, w, 0, 0, w, h)
            return b
        }
    }

    private fun rgbF(c: Int, i: Int): Float = ((c shr (16 - 8 * i)) and 255).toFloat()

    private fun texPut(p: Pix, x: Int, y: Int, tex: IntArray, tu: Int, tv: Int, light: Float, tint: FloatArray?, al: Float = 1f) {
        val c = tex[((tv and 15) shl 4) or (tu and 15)]
        val jit = light * (1f + (Math.floorMod((tu shr 4) * 73 + (tv shr 4) * 151, 7) - 3) * 0.012f)
        var r = ((c shr 16) and 255) * jit
        var g = ((c shr 8) and 255) * jit
        var b = (c and 255) * jit
        if (tint != null) { r *= tint[0]; g *= tint[1]; b *= tint[2] }
        p.put(x, y, r, g, b, al)
    }

    private val ONE: FloatArray? = null

    fun front(p: Pix, x: Int, y: Int, w: Int, h: Int, tex: Int, light: Float = 1f, u0: Int = 0, v0: Int = 0, tint: FloatArray? = null) {
        val t = Tex.get(tex)
        for (yy in y until y + h) for (xx in x until x + w)
            texPut(p, xx, yy, t, ((xx - x) shr 2) + u0, ((yy - y) shr 2) + v0, light, tint)
    }

    fun solid(p: Pix, x: Int, y: Int, w: Int, h: Int, r: Int, g: Int, b: Int, al: Float = 1f) {
        for (yy in y until y + h) for (xx in x until x + w) p.put(xx, yy, r.toFloat(), g.toFloat(), b.toFloat(), al)
    }

    private fun solidC(p: Pix, x: Int, y: Int, w: Int, h: Int, c: Int, al: Float = 1f) =
        solid(p, x, y, w, h, (c shr 16) and 255, (c shr 8) and 255, c and 255, al)

    fun side(p: Pix, xr: Int, yt: Int, yb: Int, d: Int, tex: Int, light: Float, tint: FloatArray? = null) {
        val t = Tex.get(tex)
        for (k in 0 until d) for (y in yt - k until yb - k)
            texPut(p, xr + k, y, t, k shr 2, (y - (yt - k)) shr 2, light, tint)
    }

    fun top(p: Pix, xl: Int, xr: Int, yt: Int, d0: Int, d1: Int, tex: Int, light: Float, tint: FloatArray? = null, inset: Int = 0) {
        val t = Tex.get(tex)
        for (k in d0 until d1) for (x in xl + k + inset until xr + k - inset)
            texPut(p, x, yt - k - 1, t, (x - (xl + k)) shr 2, k shr 2, light, tint)
    }

    fun box(p: Pix, x: Int, y: Int, w: Int, h: Int, d: Int, tf: Int, ts: Int = tf, tt: Int = tf, tint: FloatArray? = null,
            l0: Float = 1f, l1: Float = 0.7f, l2: Float = 1.15f, bevel: Boolean = true) {
        if (d > 0) {
            side(p, x + w, y, y + h, d, ts, l1, tint)
            top(p, x, x + w, y, 0, d, tt, l2, tint)
        }
        front(p, x, y, w, h, tf, l0, 0, 0, tint)
        if (bevel) {
            solid(p, x, y + h - 2, w, 2, 0, 0, 0, 0.18f)
            solid(p, x, y, w, 1, 255, 255, 255, 0.10f)
            solid(p, x, y, 1, h, 255, 255, 255, 0.06f)
        }
    }

    fun slope(p: Pix, ox: Int, oy: Int, w: Int, sx: Int, sy: Int, tex: Int, light: Float = 1f, tint: FloatArray? = null) {
        val t = Tex.get(tex)
        val len = sqrt((sx * sx + sy * sy).toFloat())
        val lt = (len / TS).toInt()
        val x1 = max(ox + w, ox + w + sx)
        val x0 = min(ox, ox + sx)
        for (y in oy - sy..oy) {
            val v = (oy - y) / sy.toFloat()
            if (v < 0f || v >= 1f) continue
            for (x in x0..x1) {
                val u = (x - ox - v * sx) / w
                if (u < 0f || u >= 1f) continue
                val tu = ((u * w).toInt()) shr 2
                val tv = ((v * len).toInt()) shr 2
                texPut(p, x, y, t, tu, lt - tv, light, tint)
            }
        }
    }

    fun tri(p: Pix, x0: Int, y0: Int, x1: Int, y1: Int, x2: Int, y2: Int, tex: Int, light: Float, tint: FloatArray?, rx: Int, ry: Int) {
        val t = Tex.get(tex)
        val minX = min(x0, min(x1, x2)); val maxX = max(x0, max(x1, x2))
        val minY = min(y0, min(y1, y2)); val maxY = max(y0, max(y1, y2))
        for (y in minY..maxY) for (x in minX..maxX) {
            val px = x + 0.5f; val py = y + 0.5f
            val d0 = (x1 - x0) * (py - y0) - (y1 - y0) * (px - x0)
            val d1 = (x2 - x1) * (py - y1) - (y2 - y1) * (px - x1)
            val d2 = (x0 - x2) * (py - y2) - (y0 - y2) * (px - x2)
            if ((d0 >= 0 && d1 >= 0 && d2 >= 0) || (d0 <= 0 && d1 <= 0 && d2 <= 0))
                texPut(p, x, y, t, (x - rx) shr 2, (y - ry) shr 2, light, tint)
        }
    }

    fun ellipseShadow(p: Pix, cx: Int, cy: Int, rx: Int, ry: Int, a: Float) {
        for (y in cy - ry..cy + ry) for (x in cx - rx..cx + rx) {
            val dx = (x - cx) / rx.toFloat(); val dy = (y - cy) / ry.toFloat()
            if (dx * dx + dy * dy <= 1f) p.put(x, y, 0f, 0f, 0f, a)
        }
    }

    fun line(p: Pix, x0: Int, y0: Int, x1: Int, y1: Int, r: Int, g: Int, b: Int, th: Int = 2) {
        val n = max(abs(x1 - x0), abs(y1 - y0)) + 1
        for (i in 0 until n) {
            val x = x0 + (x1 - x0) * i / max(1, n - 1)
            val y = y0 + (y1 - y0) * i / max(1, n - 1)
            for (t in 0 until th) p.put(x, y + t, r.toFloat(), g.toFloat(), b.toFloat(), 1f)
        }
    }

    private fun tn(r: Float, g: Float, b: Float) = floatArrayOf(r, g, b)

    private fun toSpr(p: Pix, padL: Int, padB: Int, fx: Int = 0, fy: Int = 0) =
        Spr(p.toBitmap(), padL.toFloat(), padB.toFloat(), fx.toFloat(), fy.toFloat())

    // ───────────────────────── PEÇAS DE CASA ─────────────────────────

    private fun window(p: Pix, cx: Int, cy: Int, w: Int, h: Int, shutter: IntArray, flowers: Boolean, glass: Int) {
        val x = cx - w / 2
        val y = cy - h / 2
        front(p, x - 4, y - 4, w + 8, h + 8, Tex.PLANKS_D, 1f)
        front(p, x, y, w, h, glass, 1f)
        solid(p, x, y, w, 8, 255, 255, 255, 0.18f)
        solid(p, x + w / 2 - 1, y, 2, h, 58, 38, 22); solid(p, x, y + h / 2 - 1, w, 2, 58, 38, 22)
        val st = tn(shutter[0] / 255f * 1.25f, shutter[1] / 255f * 1.25f, shutter[2] / 255f * 1.25f)
        for (sx in intArrayOf(x - 11, x + w + 3)) {
            front(p, sx, y - 3, 8, h + 6, Tex.PLANKS_L, 1f, 0, 0, st)
            var yy = y - 1
            while (yy < y + h + 3) { solid(p, sx, yy, 8, 1, 0, 0, 0, 0.3f); yy += 4 }
        }
        box(p, x - 6, y + h + 4, w + 12, 5, 5, Tex.STONEBRICK, bevel = false)
        if (flowers) {
            box(p, x - 3, y + h + 9, w + 6, 8, 4, Tex.PLANKS_D, bevel = false)
            val fc = arrayOf(intArrayOf(229, 57, 53), intArrayOf(255, 213, 79), intArrayOf(255, 143, 177), intArrayOf(245, 245, 245))
            for (k in 0..6) {
                val fx = x - 1 + k * (w / 6)
                val fyy = y + h + 5 - (k % 2) * 2
                solid(p, fx, fyy + 2, 2, 5, 47, 122, 47)
                solid(p, fx - 1, fyy - 1, 4, 4, fc[k % 4][0], fc[k % 4][1], fc[k % 4][2])
                solid(p, fx, fyy, 2, 2, 255, 213, 79)
                if (k % 2 == 0) solid(p, fx - 3, fyy + 4, 3, 3, 60, 150, 60)
            }
        }
    }

    private fun door(p: Pix, cx: Int, fy: Int, w: Int, h: Int, stoneArch: Boolean, dbl: Boolean) {
        val x = cx - w / 2
        front(p, x - 5, fy - h - 5, w + 10, h + 5, if (stoneArch) Tex.STONEBRICK else Tex.LOG, 1f)
        front(p, x, fy - h, w, h, Tex.VPLANKS, 1f)
        for (yy in intArrayOf(fy - h + 10, fy - 18)) {
            solid(p, x, yy, w, 3, 40, 40, 48); solid(p, x + 1, yy - 1, 5, 5, 60, 60, 70); solid(p, x + w - 6, yy - 1, 5, 5, 60, 60, 70)
        }
        if (dbl) solid(p, cx - 1, fy - h, 2, h, 58, 38, 22)
        solid(p, x + w - 9, fy - h / 2, 5, 5, 232, 190, 80)
        box(p, x - 10, fy - 6, w + 20, 6, 8, Tex.STONEBRICK, bevel = false)
        box(p, x - 4, fy - 12, w + 8, 6, 5, Tex.STONEBRICK, bevel = false)
        val lx = x + w + 12
        solid(p, lx + 5, fy - h - 8, 2, 8, 50, 50, 55)
        box(p, lx, fy - h, 12, 14, 5, Tex.GLASS, bevel = false)
        for (yy in fy - h - 14 until fy - h + 36) for (xx in lx - 20 until lx + 32) {
            val dx = (xx - (lx + 6)).toFloat(); val dy = (yy - (fy - h + 6)).toFloat()
            val d = sqrt(dx * dx + dy * dy)
            if (d < 26f) p.put(xx, yy, 255f, 214f, 120f, 0.28f * (1f - d / 26f))
        }
    }

    private fun chimney(p: Pix, x: Int, ybot: Int, ytop: Int, w: Int = 22, d: Int = 14) {
        box(p, x, ytop, w, ybot - ytop, d, Tex.STONEBRICK, bevel = false)
        box(p, x - 3, ytop - 6, w + 6, 6, d + 4, Tex.COBBLE, bevel = false, tint = tn(0.8f, 0.78f, 0.76f))
    }

    // ───────────────────────── DECORAÇÃO ─────────────────────────

    private fun barrel(p: Pix, x: Int, fy: Int, s: Float = 1f) {
        val w = (24 * s).toInt(); val h = (30 * s).toInt()
        box(p, x, fy - h, w, h, 8, Tex.PLANKS, l1 = 0.66f, l2 = 1.2f, bevel = false)
        for (yy in intArrayOf(fy - h + 5, fy - 9)) { solid(p, x, yy, w, 3, 45, 48, 56); solid(p, x + w, yy - 5, 5, 3, 45, 48, 56, 0.7f) }
        ellipseShadow(p, x + w / 2 + 3, fy + 1, w / 2 + 6, 4, 0.25f)
    }

    private fun crate(p: Pix, x: Int, fy: Int, s: Int = 26) {
        box(p, x, fy - s, s, s, 10, Tex.PLANKS_L, bevel = false)
        solid(p, x, fy - s, s, 3, 90, 60, 30); solid(p, x, fy - 3, s, 3, 90, 60, 30)
        solid(p, x, fy - s, 3, s, 90, 60, 30); solid(p, x + s - 3, fy - s, 3, s, 90, 60, 30)
    }

    private fun hay(p: Pix, x: Int, fy: Int, w: Int = 40, h: Int = 26) {
        box(p, x, fy - h, w, h, 14, Tex.THATCH, bevel = false)
        solid(p, x + 8, fy - h, 2, h, 150, 110, 40); solid(p, x + w - 10, fy - h, 2, h, 150, 110, 40)
    }

    private fun woodpile(p: Pix, x: Int, fy: Int) {
        for (r in 0..2) for (c in 0 until 5 - r % 2)
            box(p, x + c * 9 + (r % 2) * 4, fy - 9 * (r + 1), 9, 9, 3, Tex.LOG, bevel = false)
    }

    private fun benchS(p: Pix, x: Int, fy: Int, w: Int = 70) {
        box(p, x, fy - 14, w, 6, 12, Tex.PLANKS_L, bevel = false)
        solid(p, x + 4, fy - 8, 6, 8, 58, 38, 22); solid(p, x + w - 10, fy - 8, 6, 8, 58, 38, 22)
        box(p, x, fy - 30, w, 5, 3, Tex.PLANKS_L, bevel = false)
    }

    private fun potted(p: Pix, x: Int, fy: Int) {
        box(p, x, fy - 12, 14, 12, 5, Tex.REDBRICK, bevel = false)
        val fc = arrayOf(intArrayOf(229, 57, 53), intArrayOf(255, 143, 177), intArrayOf(255, 213, 79))
        for (k in 0..2) {
            solid(p, x + 1 + k * 4, fy - 22 - (k % 2) * 3, 4, 4, fc[k][0], fc[k][1], fc[k][2])
            solid(p, x + 2 + k * 4, fy - 18, 2, 6, 47, 122, 47)
        }
    }

    private fun vines(p: Pix, x0: Int, fy: Int, wy: Int) {
        for (k in 0 until 60) {
            val x = x0 + Tex.h(k, 1, 4) % 40
            val y = wy + 10 + Tex.h(k, 2, 4) % max(1, fy - wy - 24)
            solid(p, x, y, 4, 4, 50 + Tex.h(k, 3, 4) % 30, 120 + Tex.h(k, 4, 4) % 40, 50)
            if (Tex.h(k, 5, 4) % 5 < 2) solid(p, x + 2, y + 4, 2, 2, 230, 120, 160)
        }
    }

    private fun signBoard(p: Pix, x0: Int, w: Int, wy: Int) {
        val ax = x0 + w - 6
        solid(p, ax, wy + 28, 40, 5, 58, 38, 22)
        solid(p, ax + 32, wy + 33, 2, 8, 60, 60, 60); solid(p, ax + 8, wy + 33, 2, 8, 60, 60, 60)
        box(p, ax + 4, wy + 40, 36, 28, 3, Tex.PLANKS_L, bevel = false)
        solid(p, ax + 14, wy + 48, 14, 14, 246, 241, 230); solid(p, ax + 14, wy + 48, 14, 5, 242, 193, 78); solid(p, ax + 28, wy + 52, 5, 7, 246, 241, 230)
    }

    private fun extras(code: Int, p: Pix, x0: Int, fy: Int, w: Int, wy: Int, doorX: Int) {
        when (code) {
            1 -> { barrel(p, x0 + w - 36, fy); potted(p, doorX - 60, fy); potted(p, doorX + 58, fy) }
            2 -> { vines(p, x0, fy, wy); barrel(p, x0 + w - 36, fy); crate(p, x0 + w - 70, fy, 22) }
            3 -> { woodpile(p, x0 + w - 52, fy); benchS(p, x0 + 14, fy, 56); potted(p, x0 + 80, fy) }
            4 -> {
                box(p, doorX - 50, fy - 92, 100, 8, 16, Tex.PLANKS_L, bevel = false)
                for (k in 0..9) solid(p, doorX - 48 + k * 10, fy - 118, 3, 22, 58, 38, 22)
                solid(p, doorX - 50, fy - 122, 100, 5, 58, 38, 22)
                for (px in intArrayOf(doorX - 54, doorX + 46)) box(p, px, fy - 84, 8, 84, 4, Tex.PLANKS_D, bevel = false)
                potted(p, doorX - 90, fy); potted(p, doorX + 80, fy); barrel(p, x0 + 14, fy)
            }
            5 -> { barrel(p, x0 + w - 70, fy); barrel(p, x0 + w - 42, fy, 0.9f); crate(p, x0 + 14, fy); benchS(p, x0 + 50, fy); signBoard(p, x0, w, wy) }
            6 -> { hay(p, x0 - 26, fy); hay(p, x0 - 18, fy - 26, 36, 24); crate(p, x0 + w - 40, fy); barrel(p, x0 + w - 70, fy) }
            7 -> {
                solid(p, x0 + 12, fy - 74, 84, 62, 30, 24, 22)
                front(p, x0 + 22, fy - 44, 64, 32, Tex.EMBER, 1f)
                front(p, x0 + 38, fy - 58, 32, 14, Tex.EMBER, 1.15f)
                box(p, x0 + 98, fy - 28, 30, 22, 10, Tex.METAL, bevel = false)
                box(p, x0 + 106, fy - 6, 14, 6, 8, Tex.LOG, bevel = false)
                barrel(p, x0 + w - 40, fy)
            }
        }
    }

    // ───────────────────────── CASA GENÉRICA ─────────────────────────

    private fun buildHouse(
        tw: Int, td: Int, stories: Int, wall: Int, roof: Int, timber: Boolean, shutter: IntArray,
        stoneDoor: Boolean = false, dormers: Int = 0, chimneys: Int = 1, porch: Boolean = false, extra: Int = 0,
        upper: Int = -1, seed: Int = 0, noWin: Boolean = false, barn: Boolean = false, gableTex: Int = wall
    ): Spr {
        val w = tw * 64
        val d = (td * 64 * 0.42f).toInt()
        val storyH = 92
        val wallH = storyH * stories
        val rise = (d * 0.62f).toInt()
        val ov = 14
        val padL = 30; val padR = d + 60; val padT = 30; val padB = 24
        val roofH = d / 2 + rise + 60
        val wb = w + padL + padR
        val hb = wallH + roofH + padT + padB + 40
        val p = Pix(wb, hb)
        val x0 = padL; val fy = hb - padB; val wy = fy - wallH
        ellipseShadow(p, x0 + w / 2 + d / 3, fy + 2, w / 2 + d / 2 + 14, 12, 0.3f)
        box(p, x0 - 3, fy - 12, w + 6, 12, d + 2, Tex.STONEBRICK, bevel = false)
        box(p, x0, wy, w, wallH - 10, d, wall, bevel = false)
        if (upper >= 0 && stories > 1) {
            front(p, x0, wy, w, storyH, upper, 1f)
            front(p, x0, wy + storyH, w, storyH - 10, wall, 1f)
        }
        solid(p, x0, wy + 8, w, 10, 0, 0, 0, 0.30f); solid(p, x0, wy + 18, w, 8, 0, 0, 0, 0.14f)
        solid(p, x0, fy - 22, w, 10, 0, 0, 0, 0.12f)
        if (timber) {
            val n = max(2, w / 110)
            val xs = ArrayList<Int>()
            xs.add(x0)
            for (k in 1 until n) xs.add(x0 + w * k / n - 4)
            xs.add(x0 + w - 8)
            for (px in xs) front(p, px, wy, 8, wallH - 10, Tex.PLANKS_D, 0.95f)
            for (s in 0..stories) {
                val yy = if (s == stories) fy - 22 else wy + s * storyH
                front(p, x0, yy, w, 8, Tex.PLANKS_D, 1f)
            }
            for (k in 0..13) { solid(p, x0 + 8 + k, wy + 16 + k, 3, 3, 95, 62, 36); solid(p, x0 + w - 11 - k, wy + 16 + k, 3, 3, 95, 62, 36) }
        }
        val slots = max(2, w / 80)
        val doorX = x0 + w / 2
        if (!noWin) {
            for (s in 0 until stories) {
                val cy = fy - storyH * s - storyH / 2 - 8
                for (k in 0 until slots) {
                    val cx = x0 + (w * (k + 0.5f) / slots).toInt()
                    if (s == 0 && abs(cx - doorX) < 56) continue
                    val sc = if ((k + s + seed) % 2 == 0) shutter else intArrayOf(shutter[0] * 8 / 10, shutter[1] * 8 / 10, shutter[2] * 8 / 10)
                    window(p, cx, cy, 28, 32, sc, s == 0, Tex.GLASS)
                }
            }
        }
        if (barn) {
            val bw = 84; val bh = 84
            front(p, doorX - bw / 2 - 5, fy - bh - 5, bw + 10, bh + 5, Tex.PLASTER_W, 1f)
            front(p, doorX - bw / 2, fy - bh, bw, bh, Tex.BARN_V, 1f)
            for (t in 0..9) {
                val xx = doorX - bw / 2 + t * bw / 9
                solid(p, xx - 2, fy - bh + t * bh / 9 - 2, 5, 5, 241, 235, 221)
                solid(p, xx - 2, fy - t * bh / 9 - 3, 5, 5, 241, 235, 221)
            }
            solid(p, doorX - 1, fy - bh, 3, bh, 241, 235, 221)
            front(p, doorX - 18, wy + 20, 36, 34, Tex.PLASTER_W, 1f)
            front(p, doorX - 14, wy + 24, 28, 26, Tex.DARK, 1f)
            box(p, doorX - 60, fy - 6, 120, 6, 10, Tex.STONEBRICK, bevel = false)
        } else {
            door(p, doorX, fy, if (tw >= 6) 44 else 36, 64, stoneDoor, tw >= 6)
        }
        if (porch) {
            for (px in intArrayOf(doorX - 50, doorX + 42)) box(p, px, fy - 70, 8, 70, 4, Tex.PLANKS_D, bevel = false)
            box(p, doorX - 58, fy - 82, 116, 12, 14, Tex.PLANKS_D, bevel = false)
        }
        val yE = wy + 6
        val sx = d / 2 - ov
        val sy = d / 2 + rise + 6
        tri(p, x0 + w, wy, x0 + w + d, wy - d, x0 + w + d / 2, wy - d / 2 - rise, gableTex, 0.72f, null, x0 + w, wy)
        slope(p, x0 - ov, yE, w + 2 * ov, sx, sy, roof)
        solid(p, x0 - ov, yE, w + 2 * ov, 3, 40, 28, 20)
        solid(p, x0 - ov, yE + 3, w + 2 * ov, 4, 0, 0, 0, 0.3f)
        solid(p, x0 - ov + sx, yE - sy - 2, w + 2 * ov, 5, 50, 36, 28)
        line(p, x0 + w + ov, yE, x0 + w + ov + sx, yE - sy, 50, 36, 28, 3)
        line(p, x0 - ov, yE, x0 - ov + sx, yE - sy, 50, 36, 28, 3)
        for (k in 0 until dormers) {
            val t = 0.42f
            val dx = x0 + w * (k + 1) / (dormers + 1) + (sx * t).toInt() - 18
            val dy = (yE - sy * t).toInt()
            front(p, dx - 3, dy - 40, 42, 34, Tex.PLANKS_D, 1f)
            front(p, dx + 3, dy - 34, 30, 28, Tex.GLASS, 1f)
            solid(p, dx + 17, dy - 34, 2, 28, 58, 38, 22); solid(p, dx + 3, dy - 22, 30, 2, 58, 38, 22)
            tri(p, dx - 8, dy - 40, dx + 44, dy - 40, dx + 18, dy - 66, roof, 1f, null, dx, dy)
            line(p, dx - 8, dy - 40, dx + 18, dy - 66, 50, 36, 28, 3); line(p, dx + 44, dy - 40, dx + 18, dy - 66, 50, 36, 28, 3)
        }
        var fxx = 0; var fyy = 0
        for (k in 0 until chimneys) {
            val t = 0.62f
            val cxx = x0 + (w * (0.80f - 0.45f * k)).toInt() + (sx * t).toInt()
            val ybot = (yE - sy * t).toInt() + 8
            val ytop = yE - sy - 34
            chimney(p, cxx, ybot, ytop)
            if (k == 0) { fxx = cxx + 11; fyy = ytop - 8 }
        }
        extras(extra, p, x0, fy, w, wy, doorX)
        // detalhes da face lateral (janelas e vigas), em perspectiva
        if (!barn) sideDetails(p, x0, fy, w, d, wy, stories, timber, shutter, storyH)
        return toSpr(p, x0 + w / 2, padB, fxx, fyy)
    }

    private fun sideDetails(p: Pix, x0: Int, fy: Int, w: Int, d: Int, wy: Int, stories: Int, timber: Boolean, shutter: IntArray, storyH: Int) {
        val npos = if (d > 110) 2 else 1
        for (s in 0 until stories) {
            val cy = fy - storyH * s - storyH / 2 - 8
            for (k in 0 until npos) {
                val dp = d * (k + 1) / (npos + 1)
                val sb = Pix(60, 70)
                window(sb, 30, 35, 22, 28, shutter, false, Tex.GLASS)
                for (c in 0 until sb.w) {
                    val kk = dp - 30 + c
                    if (kk < 0 || kk > d) continue
                    for (r in 0 until sb.h) {
                        val v = sb.a[r * sb.w + c]
                        val al = (v ushr 24) / 255f
                        if (al <= 0f) continue
                        p.put(x0 + w + kk, cy - 35 + r - kk, ((v shr 16) and 255) * 0.72f, ((v shr 8) and 255) * 0.72f, (v and 255) * 0.72f, al)
                    }
                }
            }
        }
        if (timber) {
            val t = Tex.get(Tex.PLANKS_D)
            for (kk in intArrayOf(0, d / 2, d - 8)) for (c in 0..7) {
                val y0 = wy - kk - c
                for (y in y0 until fy - 22 - kk - c) texPut(p, x0 + w + kk + c, y, t, 0, (y - y0) shr 2, 0.7f, null)
            }
        }
    }

    // ───────────────────────── CHAMADAS DO TILEMAP ─────────────────────────

    fun house(kind: Int, style: Int, w: Int, h: Int, v: Int): Spr = when (kind) {
        8 -> buildHouse(w, h, 2, Tex.PLASTER_Y, Tex.ROOF_BROWN, true, intArrayOf(180, 70, 50), chimneys = 2, extra = 5, upper = Tex.PLANKS_D, seed = v)
        9 -> buildHouse(w, h, 1, Tex.BARN_V, Tex.ROOF_BROWN, false, intArrayOf(120, 60, 40), extra = 6, noWin = true, barn = true, chimneys = 0, gableTex = Tex.PLANKS_D)
        10 -> buildHouse(w, h, 1, Tex.STONEBRICK, Tex.ROOF_SLATE, false, intArrayOf(70, 70, 80), extra = 7, seed = v)
        11 -> church(w, h)
        else -> when (style) {
            0 -> buildHouse(w, h, 1, Tex.PLASTER, Tex.ROOF_RED, true, intArrayOf(60, 140, 90), extra = 1, seed = v)
            1 -> buildHouse(w, h, 1, Tex.STONEBRICK, Tex.ROOF_BLUE, false, intArrayOf(70, 110, 170), stoneDoor = true, dormers = if (w >= 5) 2 else 1, extra = 2, seed = v)
            2 -> buildHouse(w, h, 1, Tex.LOG, Tex.ROOF_BROWN, false, intArrayOf(160, 70, 50), porch = true, extra = 3, seed = v, gableTex = Tex.PLANKS_D)
            else -> buildHouse(w, h, 2, Tex.PLASTER_W, Tex.ROOF_GREEN, true, intArrayOf(110, 80, 150), dormers = 2, chimneys = 2, extra = 4, seed = v)
        }
    }

    private fun church(tw: Int, td: Int): Spr {
        val w = tw * 64
        val d = (td * 64 * 0.42f).toInt()
        val towerW = 112; val towerH = 250; val wallH = 140
        val p = Pix(w + 170, 540)
        val x0 = 40; val fy = 516
        ellipseShadow(p, x0 + w / 2 + 40, fy + 2, w / 2 + 70, 16, 0.3f)
        val nx = x0 + towerW - 8
        val nw = w - towerW + 8
        box(p, x0 - 3, fy - 12, w + 6, 12, d + 2, Tex.STONEBRICK, bevel = false)
        box(p, nx, fy - wallH, nw, wallH - 10, d, Tex.PLASTER_W, bevel = false)
        solid(p, nx, fy - wallH + 8, nw, 10, 0, 0, 0, 0.28f)
        for (k in 0..1) {
            val cx = nx + nw * (k + 1) / 3 + (if (k == 1) 14 else -4)
            front(p, cx - 20, fy - wallH + 36, 40, 74, Tex.STONEBRICK, 1f)
            front(p, cx - 14, fy - wallH + 42, 28, 62, Tex.GLASS_B, 1f)
            solid(p, cx - 1, fy - wallH + 42, 2, 62, 58, 38, 22); solid(p, cx - 14, fy - wallH + 66, 28, 2, 58, 38, 22)
            solid(p, cx - 14, fy - wallH + 42, 28, 12, 200, 70, 60, 0.5f)
        }
        val rise = 70; val ov = 14; val yE = fy - wallH + 6
        val sx = d / 2 - ov; val sy = d / 2 + rise + 6
        tri(p, nx + nw, fy - wallH, nx + nw + d, fy - wallH - d, nx + nw + d / 2, fy - wallH - d / 2 - rise, Tex.PLASTER_W, 0.72f, null, nx, fy - wallH)
        slope(p, nx - ov, yE, nw + 2 * ov, sx, sy, Tex.ROOF_BLUE)
        solid(p, nx - ov, yE, nw + 2 * ov, 3, 40, 28, 20); solid(p, nx - ov + sx, yE - sy - 2, nw + 2 * ov, 5, 40, 40, 50)
        line(p, nx + nw + ov, yE, nx + nw + ov + sx, yE - sy, 40, 40, 50, 3)
        val doorX = nx + nw / 2
        front(p, doorX - 30, fy - 98, 60, 98, Tex.STONEBRICK, 1f)
        front(p, doorX - 22, fy - 86, 44, 86, Tex.VPLANKS, 1f)
        solid(p, doorX - 1, fy - 86, 2, 86, 58, 38, 22)
        for (yy in intArrayOf(fy - 76, fy - 28)) solid(p, doorX - 22, yy, 44, 3, 40, 40, 48)
        box(p, doorX - 40, fy - 6, 80, 6, 10, Tex.STONEBRICK, bevel = false)
        box(p, doorX - 34, fy - 12, 68, 6, 6, Tex.STONEBRICK, bevel = false)
        box(p, x0, fy - towerH, towerW, towerH - 10, 90, Tex.STONEBRICK, bevel = false)
        for (yy in intArrayOf(fy - towerH + 70, fy - towerH + 150)) solid(p, x0, yy, towerW, 4, 0, 0, 0, 0.18f)
        val ck = x0 + towerW / 2
        front(p, ck - 22, fy - towerH + 30, 44, 44, Tex.STONEBRICK, 1f)
        solid(p, ck - 17, fy - towerH + 35, 34, 34, 246, 241, 230)
        solid(p, ck - 1, fy - towerH + 40, 3, 16, 58, 38, 22); solid(p, ck - 1, fy - towerH + 53, 14, 3, 58, 38, 22)
        front(p, ck - 18, fy - towerH + 96, 36, 50, Tex.DARK, 1f)
        solid(p, ck - 6, fy - towerH + 116, 12, 14, 232, 190, 80)
        front(p, ck - 12, fy - 110, 24, 60, Tex.GLASS_B, 1f)
        val by = fy - towerH
        val ax = x0 + towerW / 2 + 45; val ay = by - 45 - 112
        tri(p, x0 - 8, by, x0 + towerW + 8, by, ax, ay, Tex.ROOF_BLUE, 1f, null, x0, by)
        tri(p, x0 + towerW + 8, by, x0 + towerW + 98, by - 90, ax, ay, Tex.ROOF_BLUE, 0.7f, null, x0, by)
        line(p, x0 - 8, by, ax, ay, 40, 40, 50, 3); line(p, x0 + towerW + 8, by, ax, ay, 40, 40, 50, 3)
        solid(p, ax - 2, ay - 34, 5, 36, 232, 193, 78); solid(p, ax - 10, ay - 26, 21, 5, 232, 193, 78)
        potted(p, doorX - 62, fy); potted(p, doorX + 50, fy)
        return toSpr(p, x0 + w / 2, 24)
    }

    // ───────────────────────── ÁRVORES ─────────────────────────

    fun tree(variant: Int): Spr {
        val p = Pix(240, 330)
        val cx = 120; val fy = 316
        ellipseShadow(p, cx + 10, fy + 2, 62, 14, 0.30f)
        if (variant == 2 || variant == 3) {
            box(p, cx - 9, fy - 50, 18, 50, 12, Tex.BARK, bevel = false)
            val n = if (variant == 2) 6 else 8
            for (i in 0 until n) {
                val half = 56 - i * (if (n == 6) 7 else 5)
                val bot = fy - 36 - i * 28
                box(p, cx - half, bot - 30, half * 2, 30, (half * 0.8f).toInt(), Tex.LEAF_D, tint = tn(1f + 0.04f * (i % 2), 1.05f, 1f), l1 = 0.68f, l2 = 1.25f, bevel = false)
            }
            return toSpr(p, cx, 14)
        }
        val lk = when (variant) { 4 -> Tex.LEAF_Y; 5 -> Tex.LEAF_P; 6 -> Tex.LEAF_O; else -> Tex.LEAF }
        val trunk = if (variant == 4) Tex.BIRCH else Tex.BARK
        val k = if (variant == 1) 1.2f else if (variant == 4) 0.92f else 1f
        fun i(v: Int) = (v * k).toInt()
        box(p, cx - i(10), fy - i(74), i(20), i(74), 16, trunk, tint = if (variant == 5) tn(0.55f, 0.42f, 0.42f) else null, bevel = false)
        solid(p, cx - i(14), fy - 6, i(8), 6, 80, 54, 30); solid(p, cx + i(8), fy - 5, i(10), 5, 80, 54, 30)
        fun blob(x: Int, y: Int, w: Int, h: Int, d: Int) = box(p, cx + i(x), fy - i(y), i(w), i(h), i(d), lk, l1 = 0.68f, l2 = 1.22f, bevel = false)
        blob(-56, 124, 112, 66, 46)
        blob(-72, 108, 36, 44, 30)
        blob(36, 118, 38, 40, 30)
        blob(-40, 164, 80, 50, 38)
        blob(-24, 194, 48, 36, 28)
        if (variant == 5) for (kk in 0..13) solid(p, cx - 66 + kk * 9, fy - 3 - (kk % 3) * 4, 5, 4, 248, 198, 218)
        return toSpr(p, cx, 14)
    }

    // ───────────────────────── PROPS DA PRAÇA ─────────────────────────

    fun fountain(): Spr {
        val p = Pix(330, 300)
        val cx = 150; val fy = 280
        ellipseShadow(p, cx + 40, fy, 130, 20, 0.28f)
        val bw = 200; val d = 100
        box(p, cx - bw / 2, fy - 36, bw, 36, d, Tex.STONEBRICK, Tex.STONEBRICK, Tex.FLAG, bevel = false)
        top(p, cx - bw / 2 + 10, cx + bw / 2 - 10, fy - 36, 12, d - 12, Tex.WATER, 1f)
        box(p, cx - bw / 2 - 4, fy - 46, bw + 8, 10, 0, Tex.FLAG, bevel = false)
        box(p, cx - 14, fy - 90, 28, 54, 20, Tex.STONEBRICK, bevel = false)
        val y2 = fy - 90
        box(p, cx - 54, y2 - 20, 108, 20, 44, Tex.STONEBRICK, Tex.STONEBRICK, Tex.FLAG, bevel = false)
        top(p, cx - 46, cx + 46, y2 - 20, 8, 36, Tex.WATER, 1.05f)
        box(p, cx - 8, y2 - 54, 16, 34, 10, Tex.STONEBRICK, bevel = false)
        box(p, cx - 10, y2 - 72, 20, 18, 12, Tex.GOLD, bevel = false)
        return toSpr(p, cx, 20, cx + 6, y2 - 72)
    }

    fun well(): Spr {
        val p = Pix(190, 220)
        val cx = 80; val fy = 204
        ellipseShadow(p, cx + 20, fy, 60, 10, 0.28f)
        box(p, cx - 34, fy - 36, 68, 36, 40, Tex.STONEBRICK, Tex.STONEBRICK, Tex.FLAG, bevel = false)
        top(p, cx - 24, cx + 24, fy - 36, 8, 32, Tex.WATER, 0.95f)
        box(p, cx - 34, fy - 106, 8, 70, 6, Tex.PLANKS_D, bevel = false)
        box(p, cx + 26, fy - 106, 8, 70, 6, Tex.PLANKS_D, bevel = false)
        box(p, cx - 46, fy - 124, 92, 18, 44, Tex.ROOF_RED, bevel = false)
        box(p, cx - 30, fy - 142, 60, 18, 28, Tex.ROOF_RED, bevel = false)
        solid(p, cx - 1, fy - 104, 2, 40, 215, 201, 160)
        box(p, cx - 6, fy - 66, 12, 12, 6, Tex.PLANKS_L, bevel = false)
        return toSpr(p, cx, 16)
    }

    fun stall(v: Int): Spr {
        val p = Pix(220, 200)
        val l = 30; val fy = 186
        ellipseShadow(p, l + 80, fy, 90, 10, 0.28f)
        box(p, l + 4, fy - 100, 8, 100, 6, Tex.PLANKS_D, bevel = false)
        box(p, l + 116, fy - 100, 8, 100, 6, Tex.PLANKS_D, bevel = false)
        box(p, l, fy - 36, 128, 36, 26, Tex.PLANKS_L, bevel = false)
        val gc = arrayOf(intArrayOf(214, 69, 58), intArrayOf(111, 191, 74), intArrayOf(242, 193, 78), intArrayOf(229, 155, 60), intArrayOf(155, 89, 182))
        for (k in 0..5) {
            val g = gc[(v + k) % 5]
            box(p, l + 8 + k * 20, fy - 50, 16, 14, 8, Tex.PLASTER_W, tint = tn(g[0] / 255f * 1.2f, g[1] / 255f * 1.2f, g[2] / 255f * 1.2f), bevel = false)
        }
        for (k in 0..7) {
            val red = k % 2 == 0
            box(p, l - 6 + k * 17, fy - 128, 17, 26, 34, Tex.PLASTER_W, tint = if (red) tn(0.85f, 0.25f, 0.2f) else null, bevel = false)
        }
        return toSpr(p, l + 64, 14)
    }

    fun lamp(): Spr {
        val p = Pix(90, 170)
        val cx = 38; val fy = 160
        ellipseShadow(p, cx + 6, fy, 20, 5, 0.3f)
        box(p, cx - 10, fy - 10, 20, 10, 8, Tex.COBBLE, bevel = false)
        box(p, cx - 3, fy - 80, 6, 70, 4, Tex.METAL, bevel = false)
        box(p, cx - 11, fy - 108, 22, 28, 14, Tex.GLASS, bevel = false)
        solid(p, cx - 11, fy - 108, 3, 28, 40, 40, 46); solid(p, cx + 8, fy - 108, 3, 28, 40, 40, 46)
        box(p, cx - 14, fy - 118, 28, 10, 18, Tex.METAL, bevel = false)
        return toSpr(p, cx, 10, cx + 4, fy - 94)
    }

    fun bench(): Spr {
        val p = Pix(190, 110)
        val cx = 80; val fy = 100
        ellipseShadow(p, cx + 20, fy, 70, 8, 0.28f)
        box(p, cx - 50, fy - 62, 104, 22, 6, Tex.PLANKS_L, l0 = 0.92f, bevel = false)
        box(p, cx - 52, fy - 24, 8, 24, 8, Tex.PLANKS_D, bevel = false)
        box(p, cx + 44, fy - 24, 8, 24, 8, Tex.PLANKS_D, bevel = false)
        box(p, cx - 56, fy - 30, 112, 8, 22, Tex.PLANKS_L, bevel = false)
        return toSpr(p, cx, 10)
    }

    fun flowerbed(v: Int): Spr {
        val p = Pix(190, 120)
        val cx = 80; val fy = 110
        ellipseShadow(p, cx + 24, fy, 80, 8, 0.28f)
        box(p, cx - 60, fy - 16, 120, 16, 30, Tex.STONEBRICK, Tex.STONEBRICK, Tex.DIRT2, bevel = false)
        val fc = arrayOf(intArrayOf(229, 57, 53), intArrayOf(255, 213, 79), intArrayOf(255, 143, 177), intArrayOf(245, 245, 245), intArrayOf(100, 181, 246))
        for (k in 0..9) {
            val x = cx - 54 + k * 12 + (k % 3) * 2
            val yb = fy - 18 - (if (k % 2 == 0) 3 else 12)
            solid(p, x + 3, yb - 10, 2, 10, 47, 122, 47)
            val c = fc[(v + k) % 5]
            solid(p, x, yb - 18, 8, 8, c[0], c[1], c[2]); solid(p, x + 2, yb - 16, 4, 4, 255, 213, 79)
            solid(p, x - 3, yb - 6, 4, 4, 60, 150, 60)
        }
        return toSpr(p, cx, 10)
    }

    fun rock(v: Int): Spr {
        val p = Pix(140, 120)
        val cx = 60; val fy = 108
        ellipseShadow(p, cx + 14, fy, 48, 9, 0.3f)
        val g = tn(0.85f, 0.88f, 0.95f)
        box(p, cx - 32, fy - 30, 66, 30, 28, Tex.COBBLE, tint = g, l1 = 0.66f, bevel = false)
        box(p, cx - 18, fy - 54, 36, 28, 20, Tex.COBBLE, tint = tn(1f, 1.02f, 1.1f), bevel = false)
        if (v % 3 == 0) { solid(p, cx - 30, fy - 34, 26, 8, 76, 138, 60); solid(p, cx - 14, fy - 58, 16, 6, 76, 138, 60) }
        return toSpr(p, cx, 12)
    }

    fun pillar(v: Int): Spr {
        val p = Pix(150, 240)
        val cx = 62; val fy = 228
        val ht = if (v % 2 == 0) 130 else 76
        ellipseShadow(p, cx + 14, fy, 44, 8, 0.3f)
        box(p, cx - 26, fy - 16, 52, 16, 22, Tex.STONEBRICK, bevel = false)
        box(p, cx - 18, fy - 16 - ht, 36, ht, 18, Tex.STONEBRICK, tint = tn(1.05f, 1.05f, 1.02f), bevel = false)
        if (ht > 100) box(p, cx - 24, fy - 28 - ht, 48, 12, 24, Tex.STONEBRICK, bevel = false)
        else box(p, cx - 18, fy - 30 - ht, 16, 14, 14, Tex.STONEBRICK, bevel = false)
        solid(p, cx - 18, fy - 16 - ht * 6 / 10, 12, ht * 6 / 10 - 2, 76, 138, 60, 0.7f)
        box(p, cx + 30, fy - 14, 24, 14, 14, Tex.STONEBRICK, bevel = false)
        return toSpr(p, cx, 12)
    }
}
