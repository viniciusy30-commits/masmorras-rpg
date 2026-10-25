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
        for (d in MT.walls) MapTex.wall(d)
        for (d in MT.props) PropArt.get(d.id)
    }

    private fun isWallCell(x: Int, y: Int): Boolean =
        x in 0 until width && y in 0 until height && MT.isWall(data.obj[y * width + x])

    override fun drawFloor(canvas: Canvas, paint: Paint, camX: Float, camY: Float, viewW: Float, viewH: Float) {
        val sx = (camX / ts).toInt().coerceAtLeast(0)
        val ex = ((camX + viewW) / ts).toInt().coerceAtMost(width - 1)
        val sy = (camY / ts).toInt().coerceAtLeast(0)
        val ey = ((camY + viewH) / ts).toInt().coerceAtMost(height - 1)
        val t = VoxelArt.time
        for (ty in sy..ey) for (tx in sx..ex) {
            val f = data.floor[ty * width + tx]
            val def = MT.floorDef(f)
            val bmp = MapTex.tile(def, hash(tx, ty) and 3)
            val x = Math.round(tx * ts - camX).toFloat()
            val y = Math.round(ty * ts - camY).toFloat()
            dst.set(x, y, x + ts + 1f, y + ts + 1f) // +1px evita frestas com zoom
            canvas.drawBitmap(bmp, src, dst, bmpPaint)

            paint.style = Paint.Style.FILL
            // sombra embaixo das paredes
            if (isWallCell(tx, ty - 1) && !isWallCell(tx, ty)) {
                paint.color = 0x40000000
                canvas.drawRect(x, y, x + ts, y + 8f, paint)
                paint.color = 0x22000000
                canvas.drawRect(x, y + 8f, x + ts, y + 18f, paint)
            }
            // água: ondinhas; lava: brilho pulsante
            if (f == MT.F_WATER) {
                paint.color = 0x40FFFFFF
                for (k in 0..1) {
                    val wy = y + ts * (0.25f + 0.4f * k) + sin(t * 2f + tx + k * 1.7f) * 3f
                    val wx = x + ts * 0.1f + sin(t * 1.3f + ty + k) * 6f + k * 18f
                    canvas.drawRect(wx, wy, wx + ts * 0.38f, wy + 2.5f, paint)
                }
            } else if (f == MT.F_LAVA) {
                val g = 0.5f + 0.5f * sin(t * 2.2f + tx * 0.7f + ty * 0.9f)
                paint.color = ((0x30 * g).toInt() shl 24) or 0xFFC040
                canvas.drawRect(x, y, x + ts, y + ts, paint)
            }
        }
    }

    override fun drawRow(canvas: Canvas, paint: Paint, ty: Int, camX: Float, camY: Float, viewW: Float) {
        if (ty < 0 || ty >= height) return
        val sx = (camX / ts).toInt().coerceAtLeast(0)
        val ex = ((camX + viewW) / ts).toInt().coerceAtMost(width - 1)
        for (tx in sx..ex) {
            val o = data.obj[ty * width + tx]
            if (o == 0) continue
            val x = Math.round(tx * ts - camX).toFloat()
            if (MT.isWall(o)) {
                val def = MT.wallDef(o) ?: continue
                val bmp = MapTex.wall(def)
                canvas.drawBitmap(bmp, x, Math.round(ty * ts - MapTex.WALL_H - camY).toFloat(), bmpPaint)
            } else {
                val bmp = PropArt.get(o)
                val y = Math.round((ty + 1) * ts - PropArt.H - camY).toFloat()
                canvas.drawBitmap(bmp, x, y, bmpPaint)
                if (o == MT.P_BRAZIER) {
                    val t = VoxelArt.time
                    val fl = 1f + 0.08f * sin(t * 9f + tx * 1.3f) + 0.05f * sin(t * 15f + ty)
                    paint.style = Paint.Style.FILL
                    paint.color = 0x14FFA040
                    canvas.drawCircle(x + 32f, y + 44f, 92f * fl, paint)
                    paint.color = 0x22FFB050
                    canvas.drawCircle(x + 32f, y + 44f, 56f * fl, paint)
                    paint.color = 0x30FFD070
                    canvas.drawCircle(x + 32f, y + 44f, 28f * fl, paint)
                }
            }
        }
    }
}
