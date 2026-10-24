package com.viniciusy.masmorras

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.random.Random

/**
 * Mapa mundo aberto com VILA completa (casas, ruas, praça, poço).
 * Tiles:
 *  0 = grama
 *  1 = parede (madeira na vila / pedra fora)
 *  2 = água
 *  3 = caminho de terra
 *  5 = pedra da praça
 *  6 = caixote / barril
 *  7 = chão de madeira (interior de casa)
 */
class TileMap(val width: Int = 56, val height: Int = 56, val tileSize: Float = 56f) {

    val tiles = Array(height) { IntArray(width) }
    private val variant = Array(height) { IntArray(width) { Random.nextInt(1000) } }
    private val edge = Array(height) { BooleanArray(width) }
    private val torch = Array(height) { BooleanArray(width) }
    private val isWood = Array(height) { BooleanArray(width) } // parede de madeira (vila)

    val lvl = Array(height) { FloatArray(width) }

    companion object {
        const val LEVEL_PX = 26f
    }

    private class Room(val x: Int, val y: Int, val w: Int, val h: Int) {
        val cx get() = x + w / 2
        val cy get() = y + h / 2
        fun overlaps(o: Room, m: Int) =
            x - m < o.x + o.w && x + w + m > o.x && y - m < o.y + o.h && y + h + m > o.y
    }

    private val rooms = mutableListOf<Room>()
    private val wallH = tileSize * 0.36f
    private val path = Path()

    // limites da vila (zona segura, sem mobs)
    private var villageX = 4
    private var villageY = 4
    private var villageW = 28
    private var villageH = 24

    init {
        generate()
    }

    // ───────────────────────── GERAÇÃO ─────────────────────────

    private fun generate() {
        // 1. Tudo grama
        for (y in 0 until height) for (x in 0 until width) tiles[y][x] = 0

        // Bordas do mapa (floresta / limite)
        for (y in 0 until height) for (x in 0 until width) {
            if (x <= 1 || y <= 1 || x >= width - 2 || y >= height - 2) tiles[y][x] = 1
        }

        // 2. VILA GRANDE
        villageX = 6
        villageY = 6
        villageW = 30
        villageH = 26

        // Ruas principais (grade)
        // Horizontal
        for (row in listOf(villageY + 6, villageY + 13, villageY + 20)) {
            for (x in villageX until villageX + villageW) {
                if (row in 0 until height && x in 0 until width) {
                    tiles[row][x] = 3
                    if (row + 1 < height) tiles[row + 1][x] = 3
                }
            }
        }
        // Vertical
        for (col in listOf(villageX + 5, villageX + 14, villageX + 23)) {
            for (y in villageY until villageY + villageH) {
                if (y in 0 until height && col in 0 until width) {
                    tiles[y][col] = 3
                    if (col + 1 < width) tiles[y][col + 1] = 3
                }
            }
        }

        // Praça central (pedra)
        val px = villageX + 12
        val py = villageY + 10
        for (y in py until py + 6) for (x in px until px + 8) {
            if (y in 0 until height && x in 0 until width) tiles[y][x] = 5
        }
        // Poço no meio da praça
        tiles[py + 2][px + 3] = 2
        tiles[py + 2][px + 4] = 2
        tiles[py + 3][px + 3] = 2
        tiles[py + 3][px + 4] = 2

        // Casas (várias, em grid ao redor das ruas)
        val houseSpecs = listOf(
            // linha de cima
            Triple(villageX + 2, villageY + 2, 4 to 3),
            Triple(villageX + 8, villageY + 2, 5 to 3),
            Triple(villageX + 17, villageY + 2, 4 to 3),
            Triple(villageX + 24, villageY + 2, 5 to 4),
            // meio
            Triple(villageX + 2, villageY + 9, 4 to 3),
            Triple(villageX + 8, villageY + 8, 3 to 3),
            Triple(villageX + 22, villageY + 9, 4 to 3),
            Triple(villageX + 27, villageY + 9, 3 to 3),
            // baixo
            Triple(villageX + 2, villageY + 16, 5 to 3),
            Triple(villageX + 9, villageY + 16, 4 to 4),
            Triple(villageX + 17, villageY + 17, 4 to 3),
            Triple(villageX + 24, villageY + 16, 5 to 3),
            // extras
            Triple(villageX + 2, villageY + 22, 4 to 3),
            Triple(villageX + 12, villageY + 22, 5 to 3),
            Triple(villageX + 22, villageY + 22, 4 to 3)
        )
        houseSpecs.forEach { (hx, hy, size) ->
            placeHouse(hx, hy, size.first, size.second)
        }

        // Barracas / caixotes na praça e ruas
        listOf(
            px - 1 to py + 1, px + 8 to py + 1, px + 1 to py + 5,
            villageX + 7 to villageY + 7, villageX + 16 to villageY + 14,
            villageX + 25 to villageY + 7, villageX + 5 to villageY + 15
        ).forEach { (cx, cy) ->
            if (cx in 1 until width - 1 && cy in 1 until height - 1) {
                if (tiles[cy][cx] == 0 || tiles[cy][cx] == 3 || tiles[cy][cx] == 5) {
                    tiles[cy][cx] = 6
                }
            }
        }

        // Room 0 = vila (spawn)
        rooms.add(Room(villageX, villageY, villageW, villageH))

        // 3. Fora da vila: campo + algumas ruínas (para combate)
        var tries = 0
        while (rooms.size < 6 && tries < 500) {
            tries++
            val w = Random.nextInt(5, 9)
            val h = Random.nextInt(5, 8)
            val x = Random.nextInt(3, width - w - 3)
            val y = Random.nextInt(3, height - h - 3)
            // longe da vila
            if (x < villageX + villageW + 3 && y < villageY + villageH + 3 &&
                x + w > villageX - 2 && y + h > villageY - 2
            ) continue
            val r = Room(x, y, w, h)
            if (rooms.any { it.overlaps(r, 2) }) continue
            rooms.add(r)
            // limpa interior
            for (ry in r.y until r.y + r.h) for (rx in r.x until r.x + r.w) {
                if (tiles[ry][rx] == 0) tiles[ry][rx] = 0
            }
            // paredes parciais de pedra
            for (i in 0 until 4) {
                val wx = Random.nextInt(r.x, r.x + r.w)
                val wy = Random.nextInt(r.y, r.y + r.h)
                tiles[wy][wx] = 1
                isWood[wy][wx] = false
            }
            // caixotes
            if (Random.nextBoolean()) {
                val cx = r.cx.coerceIn(1, width - 2)
                val cy = r.cy.coerceIn(1, height - 2)
                tiles[cy][cx] = 6
            }
        }
        if (rooms.size < 2) {
            rooms.add(Room(width - 14, height - 14, 8, 7))
        }

        // Caminhos de terra saindo da vila para o campo
        for (x in villageX + villageW until (villageX + villageW + 12).coerceAtMost(width - 2)) {
            tiles[villageY + 13][x] = 3
            tiles[villageY + 14][x] = 3
        }
        for (y in villageY + villageH until (villageY + villageH + 10).coerceAtMost(height - 2)) {
            tiles[y][villageX + 14] = 3
            tiles[y][villageX + 15] = 3
        }

        // Lagos no campo
        for (i in 0 until 4) {
            val lx = Random.nextInt(villageX + villageW + 2, width - 6)
            val ly = Random.nextInt(4, height - 6)
            for (dy in 0..2) for (dx in 0..3) {
                val tx = (lx + dx).coerceIn(2, width - 3)
                val ty = (ly + dy).coerceIn(2, height - 3)
                if (tiles[ty][tx] == 0) tiles[ty][tx] = 2
            }
        }

        // Edges e tochas
        for (y in 0 until height) for (x in 0 until width) {
            if (tiles[y][x] != 1) continue
            var near = false
            for (dy in -1..1) for (dx in -1..1) {
                val nx = x + dx
                val ny = y + dy
                if (nx in 0 until width && ny in 0 until height && tiles[ny][nx] != 1) near = true
            }
            edge[y][x] = near
            if (y + 1 < height && tiles[y + 1][x] != 1 && variant[y][x] % 9 == 0) torch[y][x] = true
        }
    }

    private fun placeHouse(ox: Int, oy: Int, w: Int, h: Int) {
        for (y in oy until oy + h) {
            for (x in ox until ox + w) {
                if (x !in 0 until width || y !in 0 until height) continue
                if (x == ox || x == ox + w - 1 || y == oy || y == oy + h - 1) {
                    tiles[y][x] = 1
                    isWood[y][x] = true
                } else {
                    tiles[y][x] = 7 // chão madeira
                }
            }
        }
        // porta na parede de baixo
        val doorX = (ox + w / 2).coerceIn(1, width - 2)
        val doorY = (oy + h - 1).coerceIn(1, height - 2)
        tiles[doorY][doorX] = 3 // caminho na porta
        isWood[doorY][doorX] = false
        // se a porta fica na rua, ok
    }

    // ───────────────────────── COLISÃO / SPAWN ─────────────────────────

    fun heightAt(wx: Float, wy: Float): Float {
        val tx = (wx / tileSize).toInt()
        val ty = (wy / tileSize).toInt()
        if (wx < 0f || wy < 0f || tx >= width || ty >= height) return 0f
        return lvl[ty][tx] * LEVEL_PX
    }

    private fun wallNeighbors(x: Int, y: Int): Int {
        var n = 0
        if (y > 0 && tiles[y - 1][x] == 1) n++
        if (y + 1 < height && tiles[y + 1][x] == 1) n++
        if (x > 0 && tiles[y][x - 1] == 1) n++
        if (x + 1 < width && tiles[y][x + 1] == 1) n++
        return n
    }

    fun isWall(wx: Float, wy: Float): Boolean {
        val tx = (wx / tileSize).toInt()
        val ty = (wy / tileSize).toInt()
        if (wx < 0f || wy < 0f || tx >= width || ty >= height) return true
        val t = tiles[ty][tx]
        return t == 1 || t == 6
    }

    fun worldWidth() = width * tileSize
    fun worldHeight() = height * tileSize

    fun getPlayerSpawn(): Pair<Float, Float> {
        // centro da praça
        val sx = (villageX + 15.5f) * tileSize
        val sy = (villageY + 12.5f) * tileSize
        return Pair(sx, sy)
    }

    fun getBossSpawn(): Pair<Float, Float> {
        val r = rooms.last()
        return Pair((r.cx + 0.5f) * tileSize, (r.cy + 0.5f) * tileSize)
    }

    fun getEnemySpawns(count: Int): List<Pair<Float, Float>> {
        val list = mutableListOf<Pair<Float, Float>>()
        val (sx, sy) = getPlayerSpawn()
        var tries = 0
        while (list.size < count && tries < 900) {
            tries++
            val tx = Random.nextInt(3, width - 3)
            val ty = Random.nextInt(3, height - 3)
            // FORA da vila
            if (tx in villageX until (villageX + villageW) &&
                ty in villageY until (villageY + villageH)
            ) continue
            if (tiles[ty][tx] == 0 || tiles[ty][tx] == 3) {
                val px = tx * tileSize + tileSize / 2
                val py = ty * tileSize + tileSize / 2
                if (hypot(px - sx, py - sy) > 320f) list.add(px to py)
            }
        }
        return list
    }

    // ───────────────────────── DESENHO ─────────────────────────

    private fun startX(camX: Float) = (camX / tileSize).toInt().coerceAtLeast(0)
    private fun endX(camX: Float, viewW: Float) = ((camX + viewW) / tileSize).toInt().coerceAtMost(width - 1)

    fun drawFloor(canvas: Canvas, paint: Paint, camX: Float, camY: Float, viewW: Float, viewH: Float) {
        val sx = startX(camX)
        val ex = endX(camX, viewW)
        val sy = (camY / tileSize).toInt().coerceAtLeast(0)
        val ey = (((camY + viewH) / tileSize).toInt() + 1).coerceAtMost(height - 1)
        val ts = tileSize

        for (ty in sy..ey) {
            for (tx in sx..ex) {
                val t = tiles[ty][tx]
                if (t == 1) continue
                val left = tx * ts - camX
                val top = ty * ts - camY
                when (t) {
                    2 -> drawWater(canvas, paint, tx, ty, left, top)
                    3 -> drawDirt(canvas, paint, tx, ty, left, top)
                    5 -> drawPlaza(canvas, paint, tx, ty, left, top)
                    7 -> drawWoodFloor(canvas, paint, tx, ty, left, top)
                    else -> drawGrass(canvas, paint, tx, ty, left, top)
                }
            }
        }
        // luz tochas
        for (ty in 0..(ey + 2).coerceAtMost(height - 1)) {
            for (tx in sx..ex) {
                if (!torch[ty][tx]) continue
                val cx = tx * ts - camX + ts / 2
                val cy = (ty + 1) * ts - camY + ts * 0.5f
                val flick = 1f + 0.06f * sin(VoxelArt.time * 9f + tx * 2.3f)
                paint.style = Paint.Style.FILL
                paint.color = 0x0EFFA726
                canvas.drawCircle(cx, cy, ts * 2.6f * flick, paint)
                paint.color = 0x14FFA726
                canvas.drawCircle(cx, cy, ts * 1.8f * flick, paint)
            }
        }
    }

    private fun drawGrass(canvas: Canvas, paint: Paint, tx: Int, ty: Int, left: Float, top: Float) {
        val ts = tileSize
        val right = left + ts
        val bottom = top + ts
        val v = variant[ty][tx]
        paint.style = Paint.Style.FILL
        paint.color = when (v % 4) {
            0 -> 0xFF3D7A3A.toInt()
            1 -> 0xFF458545.toInt()
            2 -> 0xFF367036.toInt()
            else -> 0xFF4A8F4A.toInt()
        }
        canvas.drawRect(left, top, right, bottom, paint)
        // detalhes grama
        paint.color = 0x33000000
        canvas.drawRect(left + ts * 0.2f, top + ts * 0.3f, left + ts * 0.23f, top + ts * 0.7f, paint)
        canvas.drawRect(left + ts * 0.6f, top + ts * 0.4f, left + ts * 0.63f, top + ts * 0.75f, paint)
        if (v % 7 == 0) {
            paint.color = 0xFFE91E63.toInt()
            canvas.drawCircle(left + ts * 0.4f, top + ts * 0.5f, 3f, paint)
            paint.color = 0xFFFFEB3B.toInt()
            canvas.drawCircle(left + ts * 0.4f, top + ts * 0.5f, 1.5f, paint)
        }
        if (ty > 0 && tiles[ty - 1][tx] == 1) {
            paint.color = 0x44000000
            canvas.drawRect(left, top, right, top + ts * 0.22f, paint)
        }
    }

    private fun drawDirt(canvas: Canvas, paint: Paint, tx: Int, ty: Int, left: Float, top: Float) {
        val ts = tileSize
        val right = left + ts
        val bottom = top + ts
        val v = variant[ty][tx]
        paint.style = Paint.Style.FILL
        paint.color = if (v % 2 == 0) 0xFF8B7355.toInt() else 0xFF9A8266.toInt()
        canvas.drawRect(left, top, right, bottom, paint)
        paint.color = 0x33FFFFFF
        canvas.drawCircle(left + ts * 0.3f, top + ts * 0.4f, 2.5f, paint)
        canvas.drawCircle(left + ts * 0.7f, top + ts * 0.65f, 2f, paint)
        paint.color = 0x22000000
        canvas.drawRect(left, bottom - 4f, right, bottom, paint)
        if (ty > 0 && tiles[ty - 1][tx] == 1) {
            paint.color = 0x44000000
            canvas.drawRect(left, top, right, top + ts * 0.2f, paint)
        }
    }

    private fun drawPlaza(canvas: Canvas, paint: Paint, tx: Int, ty: Int, left: Float, top: Float) {
        val ts = tileSize
        val right = left + ts
        val bottom = top + ts
        val v = variant[ty][tx]
        paint.style = Paint.Style.FILL
        paint.color = if ((tx + ty) % 2 == 0) 0xFF7A7A7A.toInt() else 0xFF6E6E6E.toInt()
        canvas.drawRect(left, top, right, bottom, paint)
        paint.color = 0x22FFFFFF
        canvas.drawRect(left + 2, top + 2, right - 2, top + 6, paint)
        paint.color = 0x33000000
        canvas.drawRect(left + 1, top + 1, right - 1, bottom - 1, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1.2f
        paint.color = 0x44000000
        canvas.drawRect(left + 1, top + 1, right - 1, bottom - 1, paint)
        paint.style = Paint.Style.FILL
    }

    private fun drawWoodFloor(canvas: Canvas, paint: Paint, tx: Int, ty: Int, left: Float, top: Float) {
        val ts = tileSize
        val right = left + ts
        val bottom = top + ts
        paint.style = Paint.Style.FILL
        paint.color = 0xFF6D4C41.toInt()
        canvas.drawRect(left, top, right, bottom, paint)
        paint.color = 0xFF5D4037.toInt()
        val step = ts / 4f
        for (i in 1..3) {
            canvas.drawRect(left, top + i * step - 0.8f, right, top + i * step + 0.8f, paint)
        }
        paint.color = 0x22FFFFFF
        canvas.drawRect(left + 2, top + 2, right - 2, top + 5, paint)
    }

    private fun drawWater(canvas: Canvas, paint: Paint, tx: Int, ty: Int, left: Float, top: Float) {
        val ts = tileSize
        val right = left + ts
        val bottom = top + ts
        paint.style = Paint.Style.FILL
        paint.color = 0xFF1E88E5.toInt()
        canvas.drawRect(left, top, right, bottom, paint)
        paint.color = 0xFF1565C0.toInt()
        canvas.drawRect(left + 3, top + 3, right - 3, bottom - 3, paint)
        for (k in 0..2) {
            val wy = top + ts * (0.2f + 0.25f * k) + sin(VoxelArt.time * 2f + tx + k * 1.7f) * 2.5f
            val wx = left + ts * 0.15f + sin(VoxelArt.time * 1.3f + ty + k) * 5f
            paint.color = 0x55FFFFFF
            canvas.drawRect(wx, wy, wx + ts * 0.35f, wy + 2.2f, paint)
        }
        if (ty > 0 && tiles[ty - 1][tx] == 1) {
            paint.color = 0x44000000
            canvas.drawRect(left, top, right, top + ts * 0.2f, paint)
        }
    }

    fun drawRow(canvas: Canvas, paint: Paint, ty: Int, camX: Float, camY: Float, viewW: Float) {
        if (ty < 0 || ty >= height) return
        val sx = startX(camX)
        val ex = endX(camX, viewW)
        val ts = tileSize
        for (tx in sx..ex) {
            val t = tiles[ty][tx]
            val left = tx * ts - camX
            val top = ty * ts - camY
            if (t == 1) {
                drawWall(canvas, paint, tx, ty, left, top)
            } else if (t == 6) {
                drawProp(canvas, paint, tx, ty, left, top)
            } else if ((t == 0 || t == 3 || t == 5) && lvl[ty][tx] > 0f) {
                drawRaised(canvas, paint, tx, ty, left, top)
            }
        }
    }

    private fun drawWall(canvas: Canvas, paint: Paint, tx: Int, ty: Int, left: Float, top: Float) {
        val ts = tileSize
        val h = wallH
        val right = left + ts
        val bottom = top + ts
        val tt = top - h
        val tb = bottom - h
        paint.style = Paint.Style.FILL
        val wood = isWood[ty][tx]

        if (!edge[ty][tx]) {
            paint.color = if (wood) 0xFF2E1A12.toInt() else 0xFF0C0B10.toInt()
            canvas.drawRect(left, tt, right, bottom, paint)
            return
        }
        val v = variant[ty][tx]

        if (wood) {
            // ── PAREDE DE MADEIRA (casa) ──
            paint.color = 0xFF4E342E.toInt()
            canvas.drawRect(left, tt, right, tb, paint)
            paint.color = 0xFF6D4C41.toInt()
            canvas.drawRect(left + 1.5f, tt + 1.5f, right - 1.5f, tb - 1.5f, paint)
            // tábuas
            paint.color = 0xFF5D4037.toInt()
            val step = ts / 4f
            for (i in 1..3) {
                canvas.drawRect(left + i * step - 1f, tt, left + i * step + 1f, tb, paint)
            }
            paint.color = 0x33FFFFFF
            canvas.drawRect(left + 1.5f, tt + 1.5f, right - 1.5f, tt + 4f, paint)

            val frontVisible = ty + 1 < height && tiles[ty + 1][tx] != 1
            if (frontVisible) {
                paint.color = 0xFF5D4037.toInt()
                canvas.drawRect(left, tb, right, bottom, paint)
                paint.color = 0xFF4E342E.toInt()
                for (i in 1..3) {
                    canvas.drawRect(left + i * step - 1f, tb, left + i * step + 1f, bottom, paint)
                }
                paint.color = 0x33000000
                canvas.drawRect(left, bottom - 4f, right, bottom, paint)
                // telhado / topo vermelho-tijolo
                paint.color = 0xFF8B3A3A.toInt()
                canvas.drawRect(left - 2f, tt - 6f, right + 2f, tt + 2f, paint)
                paint.color = 0xFF6B2A2A.toInt()
                canvas.drawRect(left, tt - 4f, right, tt, paint)
            }
        } else {
            // ── PAREDE DE PEDRA (fora / limite) ──
            paint.color = 0xFF2A2733.toInt()
            canvas.drawRect(left, tt, right, tb, paint)
            paint.color = if (v % 2 == 0) 0xFF8A8497.toInt() else 0xFF827C90.toInt()
            canvas.drawRect(left + 1.5f, tt + 1.5f, right - 1.5f, tb - 1.5f, paint)
            paint.color = 0x33FFFFFF
            canvas.drawRect(left + 1.5f, tt + 1.5f, right - 1.5f, tt + 4f, paint)

            val frontVisible = ty + 1 < height && tiles[ty + 1][tx] != 1
            if (frontVisible) {
                paint.color = 0xFF3D3949.toInt()
                canvas.drawRect(left, tb, right, bottom, paint)
                val rowH = h / 2f
                val bw = ts / 2f
                for (r in 0..1) {
                    val ry = tb + r * rowH
                    var bx = left - (if (r % 2 == 0) 0f else bw / 2f)
                    while (bx < right) {
                        val l2 = maxOf(bx, left)
                        val r2 = minOf(bx + bw, right)
                        paint.color = if (((bx / bw).toInt() + r + v) % 3 == 0) 0xFF5A5568.toInt() else 0xFF4F4A5C.toInt()
                        canvas.drawRect(l2 + 1f, ry + 1f, r2 - 1f, ry + rowH - 1f, paint)
                        bx += bw
                    }
                }
                paint.color = 0x33000000
                canvas.drawRect(left, bottom - 5f, right, bottom, paint)
                if (torch[ty][tx]) drawTorch(canvas, paint, tx, ty, left + ts / 2f, tb + h * 0.5f)
            }
        }
    }

    private fun drawRaised(canvas: Canvas, paint: Paint, tx: Int, ty: Int, left: Float, top: Float) {
        val ts = tileSize
        val l = lvl[ty][tx]
        val h = l * LEVEL_PX
        val right = left + ts
        val tt = top - h
        val tb = tt + ts
        paint.style = Paint.Style.FILL
        paint.color = 0xFF5D4037.toInt()
        canvas.drawRect(left, tt, right, tb, paint)
        paint.color = 0xFF8D6E63.toInt()
        canvas.drawRect(left + 1.5f, tt + 1.5f, right - 1.5f, tb - 1.5f, paint)
    }

    private fun drawTorch(canvas: Canvas, paint: Paint, tx: Int, ty: Int, cx: Float, cy: Float) {
        paint.style = Paint.Style.FILL
        paint.color = 0xFF5D4037.toInt()
        canvas.drawRect(cx - 2.5f, cy - 4f, cx + 2.5f, cy + 10f, paint)
        val flick = 1f + 0.15f * sin(VoxelArt.time * 12f + tx)
        paint.color = 0xFFFF9800.toInt()
        canvas.drawCircle(cx, cy - 6f * flick, 5f * flick, paint)
        paint.color = 0xFFFFEB3B.toInt()
        canvas.drawCircle(cx, cy - 7f * flick, 2.5f * flick, paint)
    }

    private fun drawProp(canvas: Canvas, paint: Paint, tx: Int, ty: Int, left: Float, top: Float) {
        val ts = tileSize
        val cx = left + ts / 2
        val cy = top + ts * 0.55f
        paint.style = Paint.Style.FILL
        // barril / caixote
        paint.color = 0xFF6D4C41.toInt()
        canvas.drawRoundRect(left + ts * 0.2f, top + ts * 0.25f, left + ts * 0.8f, top + ts * 0.85f, 4f, 4f, paint)
        paint.color = 0xFF5D4037.toInt()
        canvas.drawRect(left + ts * 0.2f, top + ts * 0.4f, left + ts * 0.8f, top + ts * 0.48f, paint)
        canvas.drawRect(left + ts * 0.2f, top + ts * 0.62f, left + ts * 0.8f, top + ts * 0.7f, paint)
        paint.color = 0x33FFFFFF
        canvas.drawRect(left + ts * 0.25f, top + ts * 0.28f, left + ts * 0.45f, top + ts * 0.5f, paint)
    }
}
