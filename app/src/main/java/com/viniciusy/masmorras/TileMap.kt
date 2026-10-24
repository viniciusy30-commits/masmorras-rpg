package com.viniciusy.masmorras

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import kotlin.math.abs
import kotlin.random.Random

/**
 * Mapa aberto estilo Diablo / mundo aberto.
 * 0 = grama, 1 = caminho de terra, 2 = pedra / chão de casa,
 * 3 = parede de madeira (casa), 4 = telhado, 5 = água, 6 = árvore,
 * 7 = cerca, 8 = flor / detalhe, 9 = spawn bloqueado (interior casa)
 */
class TileMap(
    val width: Int = 48,
    val height: Int = 48,
    val tileSize: Float = 40f
) {
    val tiles = Array(height) { IntArray(width) }
    private val treeOffsets = Array(height) { FloatArray(width) } // variação visual

    // Cores pré-calculadas para performance
    private val cGrass1 = 0xFF3D7A3D.toInt()
    private val cGrass2 = 0xFF4A8F4A.toInt()
    private val cGrass3 = 0xFF356B35.toInt()
    private val cPath1 = 0xFF8B7355.toInt()
    private val cPath2 = 0xFFA08B6E.toInt()
    private val cStone1 = 0xFF6B6B6B.toInt()
    private val cStone2 = 0xFF7A7A7A.toInt()
    private val cWood = 0xFF5D4037.toInt()
    private val cWoodDark = 0xFF3E2723.toInt()
    private val cRoof = 0xFF8B3A3A.toInt()
    private val cRoofDark = 0xFF6B2A2A.toInt()
    private val cWater1 = 0xFF1E88E5.toInt()
    private val cWater2 = 0xFF1565C0.toInt()
    private val cTreeTrunk = 0xFF4E342E.toInt()
    private val cTreeLeaf1 = 0xFF2E7D32.toInt()
    private val cTreeLeaf2 = 0xFF388E3C.toInt()
    private val cFence = 0xFF6D4C41.toInt()
    private val cFlower = 0xFFE91E63.toInt()

    init {
        generateVillage()
    }

    private fun generateVillage() {
        // 1. Tudo começa como grama
        for (y in 0 until height) {
            for (x in 0 until width) {
                tiles[y][x] = 0
                treeOffsets[y][x] = Random.nextFloat() * 6f - 3f
            }
        }

        // 2. Vilarejo no centro-esquerda (área ~12x12)
        val vx = 8
        val vy = 16
        // Caminhos principais (cruz)
        for (x in 2 until width - 2) {
            tiles[vy + 5][x] = 1
            tiles[vy + 6][x] = 1
        }
        for (y in 4 until height - 4) {
            tiles[y][vx + 6] = 1
            tiles[y][vx + 7] = 1
        }

        // Casas (retângulos de madeira + telhado)
        placeHouse(vx + 1, vy + 1, 4, 3)
        placeHouse(vx + 10, vy + 1, 5, 4)
        placeHouse(vx + 1, vy + 9, 4, 3)
        placeHouse(vx + 10, vy + 10, 4, 3)
        // Casa maior (taverna / prefeitura)
        placeHouse(vx + 4, vy + 4, 6, 4)

        // Poço / praça
        tiles[vy + 5][vx + 5] = 2
        tiles[vy + 5][vx + 6] = 2
        tiles[vy + 6][vx + 5] = 2
        tiles[vy + 6][vx + 6] = 2

        // Cercas ao redor de algumas casas
        for (x in vx to vx + 5) {
            if (tiles[vy][x] == 0) tiles[vy][x] = 7
        }

        // 3. Lago pequeno no lado direito
        for (y in 10 until 18) {
            for (x in 34 until 42) {
                val dx = x - 38
                val dy = y - 14
                if (dx * dx + dy * dy < 18) tiles[y][x] = 5
            }
        }

        // 4. Árvores espalhadas (fora do vilarejo)
        for (i in 0 until 70) {
            val tx = Random.nextInt(1, width - 1)
            val ty = Random.nextInt(1, height - 1)
            // longe do centro do vilarejo
            if (abs(tx - (vx + 6)) > 9 || abs(ty - (vy + 6)) > 8) {
                if (tiles[ty][tx] == 0) tiles[ty][tx] = 6
            }
        }

        // 5. Flores / detalhes
        for (i in 0 until 40) {
            val tx = Random.nextInt(1, width - 1)
            val ty = Random.nextInt(1, height - 1)
            if (tiles[ty][tx] == 0) tiles[ty][tx] = 8
        }

        // 6. Caminhos secundários de terra saindo do vilarejo
        for (x in vx + 12 until 30) {
            tiles[vy + 5][x] = 1
            tiles[vy + 6][x] = 1
        }
        for (y in vy + 10 until 30) {
            tiles[y][vx + 6] = 1
            tiles[y][vx + 7] = 1
        }
    }

    private fun placeHouse(ox: Int, oy: Int, w: Int, h: Int) {
        for (y in oy until oy + h) {
            for (x in ox until ox + w) {
                if (x in 0 until width && y in 0 until height) {
                    // paredes nas bordas
                    if (x == ox || x == ox + w - 1 || y == oy || y == oy + h - 1) {
                        tiles[y][x] = 3
                    } else {
                        tiles[y][x] = 2 // chão interno
                    }
                }
            }
        }
        // telhado (linha de cima extra)
        for (x in ox until ox + w) {
            if (oy - 1 in 0 until height && x in 0 until width) {
                tiles[oy - 1][x] = 4
            }
        }
        // porta (abertura)
        val doorX = ox + w / 2
        val doorY = oy + h - 1
        if (doorX in 0 until width && doorY in 0 until height) {
            tiles[doorY][doorX] = 1 // caminho na porta
        }
    }

    fun isWall(wx: Float, wy: Float): Boolean {
        val tx = (wx / tileSize).toInt()
        val ty = (wy / tileSize).toInt()
        if (tx < 0 || ty < 0 || tx >= width || ty >= height) return true
        return when (tiles[ty][tx]) {
            3, 4, 6, 7 -> true // parede, telhado, árvore, cerca
            else -> false
        }
    }

    fun worldWidth() = width * tileSize
    fun worldHeight() = height * tileSize

    fun draw(canvas: Canvas, paint: Paint, camX: Float, camY: Float, viewW: Float, viewH: Float) {
        val startX = (camX / tileSize).toInt().coerceAtLeast(0)
        val startY = (camY / tileSize).toInt().coerceAtLeast(0)
        val endX = ((camX + viewW) / tileSize + 1).toInt().coerceAtMost(width - 1)
        val endY = ((camY + viewH) / tileSize + 1).toInt().coerceAtMost(height - 1)

        paint.style = Paint.Style.FILL

        for (ty in startY..endY) {
            for (tx in startX..endX) {
                val left = tx * tileSize - camX
                val top = ty * tileSize - camY
                val right = left + tileSize
                val bottom = top + tileSize
                val t = tiles[ty][tx]

                when (t) {
                    0 -> drawGrass(canvas, paint, left, top, right, bottom, tx, ty)
                    1 -> drawPath(canvas, paint, left, top, right, bottom, tx, ty)
                    2 -> drawStone(canvas, paint, left, top, right, bottom, tx, ty)
                    3 -> drawWoodWall(canvas, paint, left, top, right, bottom)
                    4 -> drawRoof(canvas, paint, left, top, right, bottom, tx)
                    5 -> drawWater(canvas, paint, left, top, right, bottom, tx, ty)
                    6 -> {
                        drawGrass(canvas, paint, left, top, right, bottom, tx, ty)
                        drawTree(canvas, paint, left, top, right, bottom, treeOffsets[ty][tx])
                    }
                    7 -> {
                        drawGrass(canvas, paint, left, top, right, bottom, tx, ty)
                        drawFence(canvas, paint, left, top, right, bottom)
                    }
                    8 -> {
                        drawGrass(canvas, paint, left, top, right, bottom, tx, ty)
                        drawFlower(canvas, paint, left, top, right, bottom)
                    }
                    else -> drawGrass(canvas, paint, left, top, right, bottom, tx, ty)
                }
            }
        }
    }

    private fun drawGrass(c: Canvas, p: Paint, l: Float, t: Float, r: Float, b: Float, tx: Int, ty: Int) {
        val base = when ((tx * 7 + ty * 13) % 3) {
            0 -> cGrass1
            1 -> cGrass2
            else -> cGrass3
        }
        p.color = base
        c.drawRect(l, t, r, b, p)
        // detalhe de grama (linhas finas)
        p.color = 0x22000000
        val mid = (l + r) * 0.5f
        c.drawRect(mid - 1, t + 8, mid + 1, b - 6, p)
        c.drawRect(l + 10, t + 14, l + 12, b - 10, p)
    }

    private fun drawPath(c: Canvas, p: Paint, l: Float, t: Float, r: Float, b: Float, tx: Int, ty: Int) {
        p.color = if ((tx + ty) % 2 == 0) cPath1 else cPath2
        c.drawRect(l, t, r, b, p)
        // pedrinhas
        p.color = 0x33FFFFFF
        c.drawCircle(l + 12, t + 15, 2.5f, p)
        c.drawCircle(l + 28, t + 28, 2f, p)
    }

    private fun drawStone(c: Canvas, p: Paint, l: Float, t: Float, r: Float, b: Float, tx: Int, ty: Int) {
        p.color = if ((tx + ty) % 2 == 0) cStone1 else cStone2
        c.drawRect(l, t, r, b, p)
        p.color = 0x22FFFFFF
        c.drawRect(l + 3, t + 3, r - 3, t + 7, p)
    }

    private fun drawWoodWall(c: Canvas, p: Paint, l: Float, t: Float, r: Float, b: Float) {
        p.color = cWood
        c.drawRect(l, t, r, b, p)
        // tábuas verticais
        p.color = cWoodDark
        val step = tileSize / 4f
        for (i in 1..3) {
            val x = l + i * step
            c.drawRect(x - 1, t, x + 1, b, p)
        }
        // borda
        p.color = 0x44000000
        c.drawRect(l, t, r, t + 3, p)
    }

    private fun drawRoof(c: Canvas, p: Paint, l: Float, t: Float, r: Float, b: Float, tx: Int) {
        p.color = if (tx % 2 == 0) cRoof else cRoofDark
        c.drawRect(l, t, r, b, p)
        // telhas
        p.color = 0x33FFFFFF
        c.drawRect(l + 2, t + 4, r - 2, t + 8, p)
        c.drawRect(l + 2, t + 16, r - 2, t + 20, p)
    }

    private fun drawWater(c: Canvas, p: Paint, l: Float, t: Float, r: Float, b: Float, tx: Int, ty: Int) {
        p.color = if ((tx + ty) % 2 == 0) cWater1 else cWater2
        c.drawRect(l, t, r, b, p)
        // reflexo
        p.color = 0x44FFFFFF
        c.drawCircle(l + tileSize * 0.35f, t + tileSize * 0.4f, 5f, p)
    }

    private fun drawTree(c: Canvas, p: Paint, l: Float, t: Float, r: Float, b: Float, off: Float) {
        val cx = (l + r) * 0.5f + off
        val cy = (t + b) * 0.5f
        // tronco
        p.color = cTreeTrunk
        c.drawRect(cx - 5, cy, cx + 5, b - 2, p)
        // copa (3 círculos sobrepostos)
        p.color = cTreeLeaf1
        c.drawCircle(cx, cy - 6, 16f, p)
        p.color = cTreeLeaf2
        c.drawCircle(cx - 9, cy + 2, 12f, p)
        c.drawCircle(cx + 9, cy + 2, 12f, p)
    }

    private fun drawFence(c: Canvas, p: Paint, l: Float, t: Float, r: Float, b: Float) {
        p.color = cFence
        // poste
        c.drawRect(l + 4, t + 6, l + 10, b - 4, p)
        c.drawRect(r - 10, t + 6, r - 4, b - 4, p)
        // travessas
        c.drawRect(l + 4, t + 12, r - 4, t + 17, p)
        c.drawRect(l + 4, t + 24, r - 4, t + 29, p)
    }

    private fun drawFlower(c: Canvas, p: Paint, l: Float, t: Float, r: Float, b: Float) {
        val cx = (l + r) * 0.5f
        val cy = (t + b) * 0.55f
        p.color = cFlower
        c.drawCircle(cx, cy, 4f, p)
        p.color = 0xFFFFEB3B.toInt()
        c.drawCircle(cx, cy, 2f, p)
    }

    fun getPlayerSpawn(): Pair<Float, Float> {
        // Centro da praça do vilarejo
        return Pair(11.5f * tileSize, 21.5f * tileSize)
    }

    fun getEnemySpawns(count: Int): List<Pair<Float, Float>> {
        val list = mutableListOf<Pair<Float, Float>>()
        val (sx, sy) = getPlayerSpawn()
        var tries = 0
        while (list.size < count && tries < 400) {
            tries++
            val tx = Random.nextInt(2, width - 2)
            val ty = Random.nextInt(2, height - 2)
            val t = tiles[ty][tx]
            if (t == 0 || t == 1 || t == 8) { // grama, caminho ou flor
                val px = tx * tileSize + tileSize / 2
                val py = ty * tileSize + tileSize / 2
                // longe do vilarejo (zona segura)
                if (hypot(px - sx, py - sy) > 220f) {
                    list.add(px to py)
                }
            }
        }
        return list
    }

    private fun hypot(a: Float, b: Float) = kotlin.math.sqrt(a * a + b * b)
}
