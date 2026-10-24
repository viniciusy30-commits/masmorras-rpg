package com.viniciusy.masmorras

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import kotlin.random.Random

class TileMap(val width: Int = 24, val height: Int = 24, val tileSize: Float = 48f) {

    // 0 = chão, 1 = parede, 2 = água / decoração, 3 = spawn player, 4 = spawn inimigo
    val tiles = Array(height) { IntArray(width) }

    init {
        generate()
    }

    private fun generate() {
        // Bordas paredes
        for (y in 0 until height) {
            for (x in 0 until width) {
                if (x == 0 || y == 0 || x == width - 1 || y == height - 1) {
                    tiles[y][x] = 1
                } else {
                    tiles[y][x] = 0
                }
            }
        }

        // Algumas paredes internas (corredores simples)
        for (i in 0 until 8) {
            val wx = Random.nextInt(3, width - 3)
            val wy = Random.nextInt(3, height - 3)
            val len = Random.nextInt(3, 7)
            val horiz = Random.nextBoolean()
            for (j in 0 until len) {
                val tx = if (horiz) (wx + j).coerceIn(1, width - 2) else wx
                val ty = if (horiz) wy else (wy + j).coerceIn(1, height - 2)
                tiles[ty][tx] = 1
            }
        }

        // Água / poças decorativas
        for (i in 0 until 5) {
            val ax = Random.nextInt(2, width - 2)
            val ay = Random.nextInt(2, height - 2)
            if (tiles[ay][ax] == 0) tiles[ay][ax] = 2
        }

        // Garantir spawn do player no centro-ish livre
        tiles[height / 2][width / 2] = 0
        tiles[height / 2][width / 2 - 1] = 0
        tiles[height / 2 + 1][width / 2] = 0
    }

    fun isWall(wx: Float, wy: Float): Boolean {
        val tx = (wx / tileSize).toInt()
        val ty = (wy / tileSize).toInt()
        if (tx < 0 || ty < 0 || tx >= width || ty >= height) return true
        return tiles[ty][tx] == 1
    }

    fun worldWidth() = width * tileSize
    fun worldHeight() = height * tileSize

    fun draw(canvas: Canvas, paint: Paint, camX: Float, camY: Float, viewW: Float, viewH: Float) {
        val startX = (camX / tileSize).toInt().coerceAtLeast(0)
        val startY = (camY / tileSize).toInt().coerceAtLeast(0)
        val endX = ((camX + viewW) / tileSize).toInt().coerceAtMost(width - 1)
        val endY = ((camY + viewH) / tileSize).toInt().coerceAtMost(height - 1)

        for (ty in startY..endY) {
            for (tx in startX..endX) {
                val left = tx * tileSize - camX
                val top = ty * tileSize - camY
                val right = left + tileSize
                val bottom = top + tileSize

                when (tiles[ty][tx]) {
                    0 -> { // chão pedra
                        paint.color = if ((tx + ty) % 2 == 0) 0xFF3E3E3E.toInt() else 0xFF4A4A4A.toInt()
                        canvas.drawRect(left, top, right, bottom, paint)
                        // detalhe bloco
                        paint.color = 0x22FFFFFF
                        canvas.drawRect(left + 2, top + 2, right - 2, top + 6, paint)
                    }
                    1 -> { // parede
                        paint.color = 0xFF2A2A2A.toInt()
                        canvas.drawRect(left, top, right, bottom, paint)
                        paint.color = 0xFF1A1A1A.toInt()
                        canvas.drawRect(left + 4, top + 4, right - 4, bottom - 4, paint)
                        // "blocos" estilo Minecraft
                        paint.color = 0xFF3A3A3A.toInt()
                        canvas.drawRect(left + 6, top + 6, left + tileSize / 2 - 2, top + tileSize / 2 - 2, paint)
                        canvas.drawRect(left + tileSize / 2 + 2, top + tileSize / 2 + 2, right - 6, bottom - 6, paint)
                    }
                    2 -> { // água
                        paint.color = 0xFF1565C0.toInt()
                        canvas.drawRect(left, top, right, bottom, paint)
                        paint.color = 0x442196F3
                        canvas.drawCircle(left + tileSize / 2, top + tileSize / 2, 10f, paint)
                    }
                }
            }
        }
    }

    fun getPlayerSpawn(): Pair<Float, Float> {
        return Pair(width / 2f * tileSize, height / 2f * tileSize)
    }

    fun getEnemySpawns(count: Int): List<Pair<Float, Float>> {
        val list = mutableListOf<Pair<Float, Float>>()
        var tries = 0
        while (list.size < count && tries < 200) {
            tries++
            val tx = Random.nextInt(2, width - 2)
            val ty = Random.nextInt(2, height - 2)
            if (tiles[ty][tx] == 0) {
                val px = tx * tileSize + tileSize / 2
                val py = ty * tileSize + tileSize / 2
                // longe do player
                val (sx, sy) = getPlayerSpawn()
                if (kotlin.math.hypot(px - sx, py - sy) > 150f) {
                    list.add(px to py)
                }
            }
        }
        return list
    }
}
