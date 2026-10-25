package com.viniciusy.masmorras

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import kotlin.math.abs
import kotlin.math.sin

/** Mapa montado no editor, pronto para jogar. Chão desenhado por tile, paredes/props por fileira (ordem de profundidade). */
class CustomMap(private val data: MapData) : GameMap {

    override val width: Int = data.w
    override val height: Int = data.h
    override val tileSize: Float = 64f

    private val ts = 64f
    private val solid = BooleanArray(width * height)
    private val spawnCell: Pair<Int, Int>
    private val bmpPaint = Paint().apply { isFilterBitmap = false; isAntiAlias = false }
    private val src = Rect(0, 0, MapTex.TS, MapTex.TS)
    private val dst = RectF()

    init {
        for (i in solid.indices) {
            solid[i] = MT.solidFloor(data.floor[i]) || MT.solidObj(data.obj[i])
        }
        spawnCell = findFree(data.spawnX, data.spawnY)
    }

    /** Célula livre mais próxima de (cx, cy). */
    private fun findFree(cx: Int, cy: Int): Pair<Int, Int> {
        val sx = cx.coerceIn(0, width - 1)
        val sy = cy.coerceIn(0, height - 1)
        if (!solid[sy * width + sx]) return sx to sy
        val maxR = maxOf(width, height)
        for (r in 1..maxR) {
            for (dy in -r..r) for (dx in -r..r) {
                if (abs(dx) != r && abs(dy) != r) continue
                val x = sx + dx
                val y = sy + dy
                if (x in 0 until width && y in 0 until height && !solid[y * width + x]) return x to y
            }
        }
        return sx to sy
    }

    private fun hash(x: Int, y: Int): Int {
        var h = x * 374761393 + y * 668265263
        h = (h xor (h ushr 13)) * 1274126177
        return (h xor (h ushr 16)) and 0x7FFFFFFF
    }

    override fun heightAt(wx: Float, wy: Float): Float = 0f

    override fun isWall(wx: Float, wy: Float): Boolean {
        if (wx < 0f || wy < 0f) return true
        val tx = (wx / ts).toInt()
        val ty = (wy / ts).toInt()
        if (tx >= width || ty >= height) return true
        return solid[ty * width + tx]
    }

    override fun inSafeZone(wx: Float, wy: Float): Boolean = false

    override fun worldWidth(): Float = width * ts
    override fun worldHeight(): Float = height * ts

    override fun getPlayerSpawn(): Pair<Float, Float> =
        Pair((spawnCell.first + 0.5f) * ts, (spawnCell.second + 0.5f) * ts)

    override fun getBossSpawn(): Pair<Float, Float> {
        val b = data.ents.firstOrNull { it.type == 2 }
        return if (b != null) Pair((b.x + 0.5f) * ts, (b.y + 0.5f) * ts) else getPlayerSpawn()
    }

    override fun getEnemySpawns(count: Int): List<Pair<Float, Float>> = emptyList()

    override fun createEnemies(): List<Enemy> {
        val list = ArrayList<Enemy>()
        for (e in data.ents) {
            if (!data.inB(e.x, e.y)) continue
            if (solid[e.y * width + e.x]) continue
            list.add(Enemy((e.x + 0.5f) * ts, (e.y + 0.5f) * ts, e.type.coerceIn(0, 2)))
        }
        return list
    }

    override fun prewarm(wx: Float, wy: Float) {
        for (d in MT.floors) MapTex.tile(d, 0)
        for (d in MT.walls) for (m in 0..15) MapTex.wall(d, m)
        for (d in MT.props) PropArt.get(d.id)
    }

    private fun isWallCell(x: Int, y: Int): Boolean =
        x in 0 until width && y in 0 until height && MT.isWall(data.obj[y * width + x])

    /** Parede (ou borda do mapa) nesse lado => a parede se emenda, sem contorno. Ao sul, fora do mapa conta como aberto. */
    private fun wallMask(x: Int, y: Int): Int {
        var m = 0
        if (y - 1 < 0 || isWallCell(x, y - 1)) m = m or MapTex.M_N
        if (x + 1 >= width || isWallCell(x + 1, y)) m = m or MapTex.M_E
        if (y + 1 < height && isWallCell(x, y + 1)) m = m or MapTex.M_S
        if (x - 1 < 0 || isWallCell(x - 1, y)) m = m or MapTex.M_W
        return m
    }

    private fun floorAt(x: Int, y: Int): Int =
        if (x in 0 until width && y in 0 until height) data.floor[y * width + x] else -1

    private fun isLiquid(f: Int) = f == MT.F_WATER || f == MT.F_LAVA || f == MT.F_SLIME

    /** Espuma animada nas margens da água. */
    private fun foam(canvas: Canvas, paint: Paint, x: Float, y: Float, side: Int, t: Float, seed: Int) {
        for (k in 0 until 8) {
            val o = sin(t * 2.2f + k * 0.9f + seed * 0.7f) * 2.2f
            val seg = ts / 8f
            val a = 0x30 + (0x30 * (0.5f + 0.5f * sin(t * 1.7f + k + seed))).toInt()
            for (layer in 0..1) {
                val th = if (layer == 0) 6f + o else 2.5f + o * 0.4f
                paint.color = ((if (layer == 0) a / 2 else a) shl 24) or 0xFFFFFF
                when (side) {
                    0 -> canvas.drawRect(x + k * seg, y, x + (k + 1) * seg + 0.5f, y + th, paint)
                    1 -> canvas.drawRect(x + k * seg, y + ts - th, x + (k + 1) * seg + 0.5f, y + ts, paint)
                    2 -> canvas.drawRect(x, y + k * seg, x + th, y + (k + 1) * seg + 0.5f, paint)
                    else -> canvas.drawRect(x + ts - th, y + k * seg, x + ts, y + (k + 1) * seg + 0.5f, paint)
                }
            }
        }
    }

    /** Brilho que vaza de lava/lodo para o chão vizinho. */
    private fun spill(canvas: Canvas, paint: Paint, x: Float, y: Float, side: Int, color: Int, pulse: Float) {
        val steps = intArrayOf(0x46, 0x2a, 0x14)
        for (i in 0..2) {
            val a = (steps[i] * pulse).toInt().coerceIn(0, 255)
            paint.color = (a shl 24) or color
            val o0 = i * 6f
            val o1 = o0 + 6f
            when (side) {
                0 -> canvas.drawRect(x, y + o0, x + ts, y + o1, paint)
                1 -> canvas.drawRect(x, y + ts - o1, x + ts, y + ts - o0, paint)
                2 -> canvas.drawRect(x + o0, y, x + o1, y + ts, paint)
                else -> canvas.drawRect(x + ts - o1, y, x + ts - o0, y + ts, paint)
            }
        }
    }

    override fun drawFloor(canvas: Canvas, paint: Paint, camX: Float, camY: Float, viewW: Float, viewH: Float) {
        val sx = (camX / ts).toInt().coerceAtLeast(0)
        val ex = ((camX + viewW) / ts).toInt().coerceAtMost(width - 1)
        val sy = (camY / ts).toInt().coerceAtLeast(0)
        val ey = ((camY + viewH) / ts).toInt().coerceAtMost(height - 1)
        val t = VoxelArt.time
        val pulse = 0.75f + 0.25f * sin(t * 2.4f)
        for (ty in sy..ey) for (tx in sx..ex) {
            val f = data.floor[ty * width + tx]
            val def = MT.floorDef(f)
            val bmp = MapTex.tile(def, hash(tx, ty) and 3, t)
            val x = Math.round(tx * ts - camX).toFloat()
            val y = Math.round(ty * ts - camY).toFloat()
            dst.set(x, y, x + ts + 1f, y + ts + 1f) // +1px evita frestas com zoom
            canvas.drawBitmap(bmp, src, dst, bmpPaint)

            paint.style = Paint.Style.FILL
            if (f == MT.F_WATER) {
                // espuma nas margens com terra
                val nb = intArrayOf(floorAt(tx, ty - 1), floorAt(tx, ty + 1), floorAt(tx - 1, ty), floorAt(tx + 1, ty))
                for (s in 0..3) if (nb[s] >= 0 && nb[s] != MT.F_WATER && !isLiquid(nb[s]) && nb[s] != MT.F_VOID) foam(canvas, paint, x, y, s, t, tx * 3 + ty)
            } else if (f == MT.F_LAVA) {
                val g = 0.5f + 0.5f * sin(t * 2.2f + tx * 0.7f + ty * 0.9f)
                paint.color = ((0x28 * g).toInt() shl 24) or 0xFFC040
                canvas.drawRect(x, y, x + ts, y + ts, paint)
                val nb = intArrayOf(floorAt(tx, ty - 1), floorAt(tx, ty + 1), floorAt(tx - 1, ty), floorAt(tx + 1, ty))
                for (s in 0..3) if (nb[s] >= 0 && nb[s] != MT.F_LAVA && nb[s] != MT.F_VOID) {
                    paint.color = 0x70120604
                    when (s) {
                        0 -> canvas.drawRect(x, y, x + ts, y + 4f, paint)
                        1 -> canvas.drawRect(x, y + ts - 4f, x + ts, y + ts, paint)
                        2 -> canvas.drawRect(x, y, x + 4f, y + ts, paint)
                        else -> canvas.drawRect(x + ts - 4f, y, x + ts, y + ts, paint)
                    }
                }
            } else {
                // chão comum: brilho vindo de lava/lodo vizinhos
                if (!isLiquid(f) && f != MT.F_VOID) {
                    val nb = intArrayOf(floorAt(tx, ty - 1), floorAt(tx, ty + 1), floorAt(tx - 1, ty), floorAt(tx + 1, ty))
                    for (s in 0..3) {
                        if (nb[s] == MT.F_LAVA) spill(canvas, paint, x, y, s, 0xFF7A20, pulse)
                        else if (nb[s] == MT.F_SLIME) spill(canvas, paint, x, y, s, 0x7CFF3C, pulse * 0.7f)
                    }
                }
            }
            // sombra embaixo das paredes
            if (isWallCell(tx, ty - 1) && !isWallCell(tx, ty)) {
                paint.color = 0x40000000
                canvas.drawRect(x, y, x + ts, y + 8f, paint)
                paint.color = 0x22000000
                canvas.drawRect(x, y + 8f, x + ts, y + 18f, paint)
            }
        }
    }

    override fun drawRow(canvas: Canvas, paint: Paint, ty: Int, camX: Float, camY: Float, viewW: Float) {
        if (ty < 0 || ty >= height) return
        val sx = (camX / ts).toInt().coerceAtLeast(0)
        val ex = ((camX + viewW) / ts).toInt().coerceAtMost(width - 1)
        val t = VoxelArt.time
        for (tx in sx..ex) {
            val o = data.obj[ty * width + tx]
            if (o == 0) continue
            val x = Math.round(tx * ts - camX).toFloat()
            if (MT.isWall(o)) {
                val def = MT.wallDef(o) ?: continue
                val bmp = MapTex.wall(def, wallMask(tx, ty))
                canvas.drawBitmap(bmp, x, Math.round(ty * ts - MapTex.WALL_H - camY).toFloat(), bmpPaint)
            } else {
                val bmp = PropArt.get(o, t + hash(tx, ty) % 7 * 0.13f)
                val y = Math.round((ty + 1) * ts - PropArt.H - camY).toFloat()
                canvas.drawBitmap(bmp, x, y, bmpPaint)
                val g = MT.glows[o]
                if (g != null) {
                    val fl = 1f + g.flicker * (0.08f * sin(t * 9f + tx * 1.3f) + 0.05f * sin(t * 15f + ty))
                    val cx = x + 32f
                    val cy = y + g.cy
                    val r = g.radius * fl
                    paint.style = Paint.Style.FILL
                    paint.color = (0x12 shl 24) or g.color
                    canvas.drawCircle(cx, cy, r, paint)
                    paint.color = (0x1C shl 24) or g.color
                    canvas.drawCircle(cx, cy, r * 0.6f, paint)
                    paint.color = (0x2A shl 24) or g.color
                    canvas.drawCircle(cx, cy, r * 0.3f, paint)
                }
            }
        }
    }
}
