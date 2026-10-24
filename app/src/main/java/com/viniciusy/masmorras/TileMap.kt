package com.viniciusy.masmorras

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.random.Random

class TileMap(val width: Int = 40, val height: Int = 40, val tileSize: Float = 64f) {

    // 0 = chão, 1 = parede, 2 = água rasa (andável), 6 = caixote/barril (sólido)
    val tiles = Array(height) { IntArray(width) }
    private val variant = Array(height) { IntArray(width) { Random.nextInt(1000) } }
    private val edge = Array(height) { BooleanArray(width) }
    private val torch = Array(height) { BooleanArray(width) }

    // relevo: altura de cada tile em "níveis" (0 = chão, 0.5 = degrau, 1 = plataforma, 2 = topo)
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

    init {
        generate()
    }

    // ───────────────────────── GERAÇÃO ─────────────────────────

    private fun generate() {
        // Mundo aberto: começa tudo como chão (grama / campo)
        for (y in 0 until height) for (x in 0 until width) tiles[y][x] = 0

        // Bordas do mapa como "floresta densa" (paredes)
        for (y in 0 until height) {
            for (x in 0 until width) {
                if (x == 0 || y == 0 || x == width - 1 || y == height - 1) tiles[y][x] = 1
            }
        }

        // ── VILAREJO (sala 0 = zona segura / spawn) ──
        val vx = 5
        val vy = 5
        val vw = 12
        val vh = 10
        rooms.add(Room(vx, vy, vw, vh))

        // Casas (paredes internas no vilarejo)
        placeHouse(vx + 1, vy + 1, 4, 3)
        placeHouse(vx + 7, vy + 1, 4, 3)
        placeHouse(vx + 1, vy + 6, 4, 3)
        placeHouse(vx + 7, vy + 6, 4, 3)
        // praça central andável
        for (y in vy + 4 until vy + 6) for (x in vx + 4 until vx + 8) tiles[y][x] = 0

        // ── Pontos de interesse (ruínas / acampamentos) espalhados ──
        var tries = 0
        while (rooms.size < 8 && tries < 400) {
            tries++
            val w = Random.nextInt(6, 10)
            val h = Random.nextInt(5, 9)
            val x = Random.nextInt(3, width - w - 3)
            val y = Random.nextInt(3, height - h - 3)
            // longe do vilarejo
            if (kotlin.math.abs(x - vx) < 14 && kotlin.math.abs(y - vy) < 12) continue
            val r = Room(x, y, w, h)
            if (rooms.any { it.overlaps(r, 2) }) continue
            rooms.add(r)
            // limpa a área da ruína
            for (ry in r.y until r.y + r.h) for (rx in r.x until r.x + r.w) {
                if (tiles[ry][rx] != 1) tiles[ry][rx] = 0
            }
            // algumas paredes internas (ruína parcial)
            for (i in 0 until 3) {
                val wx = Random.nextInt(r.x, r.x + r.w)
                val wy = Random.nextInt(r.y, r.y + r.h)
                if (tiles[wy][wx] == 0) tiles[wy][wx] = 1
            }
        }
        if (rooms.size < 2) rooms.add(Room(width - 12, height - 12, 8, 7))

        // Caminhos largos entre vilarejo e pontos de interesse
        for (i in 1 until rooms.size) connect(rooms[0], rooms[i])

        // Lagos / poças no campo
        for (i in 0 until 6) {
            val px = Random.nextInt(4, width - 6)
            val py = Random.nextInt(4, height - 6)
            // não no vilarejo
            if (kotlin.math.abs(px - (vx + vw / 2)) < 10 && kotlin.math.abs(py - (vy + vh / 2)) < 8) continue
            for (dy in 0..2) for (dx in 0..3) {
                val tx = (px + dx).coerceIn(1, width - 2)
                val ty = (py + dy).coerceIn(1, height - 2)
                if (tiles[ty][tx] == 0) tiles[ty][tx] = 2
            }
        }

        // Caixotes nas ruínas
        for (i in 1 until rooms.size) {
            val r = rooms[i]
            val corners = arrayOf(
                r.x to r.y, r.x + r.w - 1 to r.y,
                r.x to r.y + r.h - 1, r.x + r.w - 1 to r.y + r.h - 1
            )
            corners.forEach { (cx, cy) ->
                if (Random.nextInt(100) < 50 && tiles[cy][cx] == 0) tiles[cy][cx] = 6
            }
        }

        addRelief()

        // Bordas visíveis das paredes e tochas
        for (y in 0 until height) for (x in 0 until width) {
            if (tiles[y][x] != 1) continue
            var near = false
            for (dy in -1..1) for (dx in -1..1) {
                val nx = x + dx
                val ny = y + dy
                if (nx in 0 until width && ny in 0 until height && tiles[ny][nx] != 1) near = true
            }
            edge[y][x] = near
            if (y + 1 < height && tiles[y + 1][x] == 0 && variant[y][x] % 7 == 0) torch[y][x] = true
        }
    }

    private fun placeHouse(ox: Int, oy: Int, w: Int, h: Int) {
        for (y in oy until oy + h) {
            for (x in ox until ox + w) {
                if (x !in 0 until width || y !in 0 until height) continue
                if (x == ox || x == ox + w - 1 || y == oy || y == oy + h - 1) {
                    tiles[y][x] = 1 // parede
                } else {
                    tiles[y][x] = 0 // interior
                }
            }
        }
        // porta
        val doorX = (ox + w / 2).coerceIn(1, width - 2)
        val doorY = (oy + h - 1).coerceIn(1, height - 2)
        tiles[doorY][doorX] = 0
    }

    /** Plataformas com degraus em algumas salas (nunca na sala inicial). */
    private fun addRelief() {
        for (i in 1 until rooms.size) {
            val r = rooms[i]
            if (r.w < 7 || r.h < 7) continue
            if (Random.nextInt(100) < 20) continue
            val px = r.x + 2
            val py = r.y + 2
            val pw = r.w - 4
            val ph = r.h - 5
            if (pw < 3 || ph < 2) continue
            for (y in py..py + ph) for (x in px until px + pw) {
                if (tiles[y][x] == 2 || tiles[y][x] == 6) tiles[y][x] = 0
                lvl[y][x] = if (y == py + ph) 0.5f else 1f
            }
            if (pw >= 5 && ph >= 3) {
                for (y in py until py + ph) for (x in px + 1 until px + pw - 1) {
                    lvl[y][x] = if (y == py + ph - 1) 1.5f else 2f
                }
            }
        }
    }

    fun heightAt(wx: Float, wy: Float): Float {
        val tx = (wx / tileSize).toInt()
        val ty = (wy / tileSize).toInt()
        if (wx < 0f || wy < 0f || tx >= width || ty >= height) return 0f
        return lvl[ty][tx] * LEVEL_PX
    }

    private fun wallNeighbors(x: Int, y: Int): Int {
        var n = 0
        if (tiles[y - 1][x] == 1) n++
        if (tiles[y + 1][x] == 1) n++
        if (tiles[y][x - 1] == 1) n++
        if (tiles[y][x + 1] == 1) n++
        return n
    }

    private fun connect(a: Room, b: Room) {
        var x = a.cx
        var y = a.cy
        while (x != b.cx) {
            carve(x, y)
            x += if (b.cx > x) 1 else -1
        }
        while (y != b.cy) {
            carve(x, y)
            y += if (b.cy > y) 1 else -1
        }
        carve(x, y)
    }

    private fun carve(x: Int, y: Int) {
        for (dy in 0..1) for (dx in 0..1) {
            val tx = (x + dx).coerceIn(1, width - 2)
            val ty = (y + dy).coerceIn(1, height - 2)
            if (tiles[ty][tx] == 1) tiles[ty][tx] = 0
        }
    }

    // ───────────────────────── COLISÃO / SPAWN ─────────────────────────

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
        val r = rooms[0]
        return Pair((r.cx + 0.5f) * tileSize, (r.cy + 0.5f) * tileSize)
    }

    fun getBossSpawn(): Pair<Float, Float> {
        val r = rooms.last()
        return Pair((r.cx + 0.5f) * tileSize, (r.cy + 0.5f) * tileSize)
    }

    fun getEnemySpawns(count: Int): List<Pair<Float, Float>> {
        val list = mutableListOf<Pair<Float, Float>>()
        val (sx, sy) = getPlayerSpawn()
        var tries = 0
        while (list.size < count && tries < 800) {
            tries++
            // 60% nas ruínas, 40% no campo aberto
            val useRoom = rooms.size > 1 && Random.nextInt(100) < 60
            val tx: Int
            val ty: Int
            if (useRoom) {
                val r = rooms[1 + tries % (rooms.size - 1)]
                tx = Random.nextInt(r.x, r.x + r.w)
                ty = Random.nextInt(r.y, r.y + r.h)
            } else {
                tx = Random.nextInt(2, width - 2)
                ty = Random.nextInt(2, height - 2)
            }
            if (tiles[ty][tx] == 0 || tiles[ty][tx] == 2) {
                val px = tx * tileSize + tileSize / 2
                val py = ty * tileSize + tileSize / 2
                // zona segura do vilarejo
                if (hypot(px - sx, py - sy) > 280f) list.add(px to py)
            }
        }
        return list
    }

    // ───────────────────────── DESENHO ─────────────────────────

    private fun startX(camX: Float) = (camX / tileSize).toInt().coerceAtLeast(0)
    private fun endX(camX: Float, viewW: Float) = ((camX + viewW) / tileSize).toInt().coerceAtMost(width - 1)

    /** Chão, poças e luz das tochas. */
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
                if (t == 2) drawWater(canvas, paint, tx, ty, left, top) else drawFloorTile(canvas, paint, tx, ty, left, top)
            }
        }
        // luz das tochas no chão
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
                paint.color = 0x1CFFB74D
                canvas.drawCircle(cx, cy, ts * 1.0f * flick, paint)
            }
        }
    }

    private fun drawFloorTile(canvas: Canvas, paint: Paint, tx: Int, ty: Int, left: Float, top: Float) {
        val ts = tileSize
        val right = left + ts
        val bottom = top + ts
        val v = variant[ty][tx]
        paint.style = Paint.Style.FILL
        paint.color = 0xFF2F2C38.toInt()
        canvas.drawRect(left, top, right, bottom, paint)
        paint.color = when (v % 3) {
            0 -> 0xFF5C5866.toInt()
            1 -> 0xFF635E6E.toInt()
            else -> 0xFF565260.toInt()
        }
        canvas.drawRect(left + 1.5f, top + 1.5f, right - 1.5f, bottom - 1.5f, paint)
        paint.color = 0x33FFFFFF
        canvas.drawRect(left + 1.5f, top + 1.5f, right - 1.5f, top + 4f, paint)
        paint.color = 0x22000000
        canvas.drawRect(left + 1.5f, bottom - 5f, right - 1.5f, bottom - 1.5f, paint)

        when {
            v % 11 == 0 -> {
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 1.6f
                paint.color = 0x66000000
                path.reset()
                path.moveTo(left + ts * 0.2f, top + ts * 0.3f)
                path.lineTo(left + ts * 0.45f, top + ts * 0.5f)
                path.lineTo(left + ts * 0.4f, top + ts * 0.75f)
                path.lineTo(left + ts * 0.7f, top + ts * 0.85f)
                canvas.drawPath(path, paint)
                paint.style = Paint.Style.FILL
            }
            v % 9 == 1 -> {
                paint.color = 0x553F7D3A
                canvas.drawCircle(left + ts * 0.3f, top + ts * 0.35f, ts * 0.18f, paint)
                canvas.drawCircle(left + ts * 0.42f, top + ts * 0.5f, ts * 0.12f, paint)
                canvas.drawCircle(left + ts * 0.75f, top + ts * 0.7f, ts * 0.1f, paint)
            }
            v % 13 == 2 -> {
                paint.color = 0x66A09CAB
                canvas.drawCircle(left + ts * 0.3f, top + ts * 0.6f, 3.5f, paint)
                canvas.drawCircle(left + ts * 0.5f, top + ts * 0.7f, 2.5f, paint)
                canvas.drawCircle(left + ts * 0.65f, top + ts * 0.55f, 3f, paint)
            }
            v % 53 == 3 -> {
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 3f
                paint.color = 0xCCE0DCCB.toInt()
                canvas.drawLine(left + ts * 0.25f, top + ts * 0.35f, left + ts * 0.7f, top + ts * 0.65f, paint)
                canvas.drawLine(left + ts * 0.25f, top + ts * 0.65f, left + ts * 0.7f, top + ts * 0.35f, paint)
                paint.style = Paint.Style.FILL
                canvas.drawCircle(left + ts * 0.48f, top + ts * 0.5f, 6f, paint)
                paint.color = 0xFF2F2C38.toInt()
                canvas.drawCircle(left + ts * 0.45f, top + ts * 0.49f, 1.6f, paint)
                canvas.drawCircle(left + ts * 0.52f, top + ts * 0.49f, 1.6f, paint)
            }
        }
        // sombra da parede acima
        if (ty > 0 && tiles[ty - 1][tx] == 1) {
            paint.style = Paint.Style.FILL
            paint.color = 0x55000000
            canvas.drawRect(left, top, right, top + ts * 0.28f, paint)
        }
    }

    private fun drawWater(canvas: Canvas, paint: Paint, tx: Int, ty: Int, left: Float, top: Float) {
        val ts = tileSize
        val right = left + ts
        val bottom = top + ts
        paint.style = Paint.Style.FILL
        paint.color = 0xFF2A7092.toInt()
        canvas.drawRect(left, top, right, bottom, paint)
        for (k in 0..2) {
            val wy = top + ts * (0.2f + 0.3f * k) + sin(VoxelArt.time * 2f + tx + k * 1.7f) * 3f
            val wx = left + ts * 0.1f + sin(VoxelArt.time * 1.3f + ty + k) * 6f
            paint.color = 0x55FFFFFF
            canvas.drawRect(wx, wy, wx + ts * 0.4f, wy + 2.5f, paint)
        }
        paint.color = 0x77FFFFFF
        if (ty > 0 && tiles[ty - 1][tx] != 2) canvas.drawRect(left, top, right, top + 3f, paint)
        if (ty + 1 < height && tiles[ty + 1][tx] != 2) canvas.drawRect(left, bottom - 3f, right, bottom, paint)
        if (tx > 0 && tiles[ty][tx - 1] != 2) canvas.drawRect(left, top, left + 3f, bottom, paint)
        if (tx + 1 < width && tiles[ty][tx + 1] != 2) canvas.drawRect(right - 3f, top, right, bottom, paint)
        if (ty > 0 && tiles[ty - 1][tx] == 1) {
            paint.color = 0x55000000
            canvas.drawRect(left, top, right, top + ts * 0.28f, paint)
        }
    }

    /** Paredes 3D e props de uma linha (para ordenar com personagens). */
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
            } else if (t == 0 && lvl[ty][tx] > 0f) {
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

        if (!edge[ty][tx]) {
            paint.color = 0xFF0C0B10.toInt()
            canvas.drawRect(left, tt, right, bottom, paint)
            return
        }
        val v = variant[ty][tx]
        // topo da parede
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
            paint.color = 0x55000000
            canvas.drawRect(left, tb, right, tb + 3f, paint)
            if (torch[ty][tx]) drawTorch(canvas, paint, tx, ty, left + ts / 2f, tb + h * 0.5f)
        }
    }

    /** Bloco de relevo: topo + face frontal de pedra. */
    private fun drawRaised(canvas: Canvas, paint: Paint, tx: Int, ty: Int, left: Float, top: Float) {
        val ts = tileSize
        val l = lvl[ty][tx]
        val h = l * LEVEL_PX
        val right = left + ts
        val tt = top - h
        val tb = tt + ts
        val v = variant[ty][tx]
        paint.style = Paint.Style.FILL
        paint.color = 0xFF26232F.toInt()
        canvas.drawRect(left, tt, right, tb, paint)
        paint.color = if (l >= 2f) 0xFFA39DB8.toInt()
        else if (l >= 1.5f) 0xFF958FAA.toInt()
        else if (l >= 1f) 0xFF8A849D.toInt()
        else 0xFF7A7489.toInt()
        canvas.drawRect(left + 1.5f, tt + 1.5f, right - 1.5f, tb - 1.5f, paint)
        paint.color = 0x22000000
        canvas.drawRect(left + ts / 2f - 0.75f, tt + 1.5f, left + ts / 2f + 0.75f, tb - 1.5f, paint)
        canvas.drawRect(left + 1.5f, tt + ts / 2f - 0.75f, right - 1.5f, tt + ts / 2f + 0.75f, paint)
        paint.color = 0x40FFFFFF
        canvas.drawRect(left + 1.5f, tt + 1.5f, right - 1.5f, tt + 4.5f, paint)
        canvas.drawRect(left + 1.5f, tt + 1.5f, left + 4.5f, tb - 1.5f, paint)
        if (v % 5 == 0) {
            paint.color = 0x663F7D3A
            canvas.drawRect(left + ts * 0.15f, tt + ts * 0.2f, left + ts * 0.45f, tt + ts * 0.4f, paint)
        }
        val sh = if (ty + 1 < height && tiles[ty + 1][tx] != 1) lvl[ty + 1][tx] * LEVEL_PX else h
        if (h > sh + 0.5f) {
            val fh = h - sh
            paint.color = 0xFF3A3646.toInt()
            canvas.drawRect(left, tb, right, tb + fh, paint)
            val rows = (fh / 13f + 0.5f).toInt().coerceAtLeast(1)
            val rh = fh / rows
            val bw = ts / 2f
            for (r in 0 until rows) {
                val ry = tb + r * rh
                var bx = left - (if ((r + v) % 2 == 0) 0f else bw / 2f)
                while (bx < right) {
                    val l2 = maxOf(bx, left)
                    val r2 = minOf(bx + bw, right)
                    paint.color = if (((bx / bw).toInt() + r + v) % 3 == 0) 0xFF585369.toInt() else 0xFF4C4859.toInt()
                    canvas.drawRect(l2 + 1f, ry + 1f, r2 - 1f, ry + rh - 1f, paint)
                    bx += bw
                }
            }
            paint.color = 0x55000000
            canvas.drawRect(left, tb, right, tb + 3f, paint)
            paint.color = 0x33000000
            canvas.drawRect(left, tb + fh - 4f, right, tb + fh, paint)
        }
    }

    private fun drawTorch(canvas: Canvas, paint: Paint, tx: Int, ty: Int, cx: Float, cy: Float) {
        val flick = sin(VoxelArt.time * 13f + tx * 3f + ty) * 1.6f
        paint.style = Paint.Style.FILL
        paint.color = 0xFF3E2723.toInt()
        canvas.drawRect(cx - 2f, cy, cx + 2f, cy + wallH * 0.45f, paint)
        paint.color = 0xFF5D4037.toInt()
        canvas.drawRect(cx - 3.5f, cy - 1f, cx + 3.5f, cy + 2f, paint)
        paint.color = 0xFFFF6F00.toInt()
        path.reset()
        path.moveTo(cx - 4.5f, cy - 1f)
        path.lineTo(cx + flick * 0.5f, cy - 12f + flick)
        path.lineTo(cx + 4.5f, cy - 1f)
        path.close()
        canvas.drawPath(path, paint)
        paint.color = 0xFFFFD54F.toInt()
        path.reset()
        path.moveTo(cx - 2.5f, cy - 1f)
        path.lineTo(cx + flick * 0.3f, cy - 7f + flick * 0.5f)
        path.lineTo(cx + 2.5f, cy - 1f)
        path.close()
        canvas.drawPath(path, paint)
    }

    private fun drawProp(canvas: Canvas, paint: Paint, tx: Int, ty: Int, left: Float, top: Float) {
        val ts = tileSize
        val v = variant[ty][tx]
        val cx = left + ts / 2f
        val base = top + ts - 6f
        paint.style = Paint.Style.FILL
        paint.color = 0x44000000
        canvas.drawOval(cx - ts * 0.42f, base - ts * 0.08f, cx + ts * 0.42f, base + ts * 0.12f, paint)
        if (v % 2 == 0) {
            // caixote
            VoxelArt.box(canvas, paint, cx, base, ts * 0.74f, ts * 0.58f, ts * 0.22f, 0xFF9C6B3A.toInt(), 0xFFB98550.toInt())
            paint.color = 0xFF6D4524.toInt()
            canvas.drawRect(cx - ts * 0.37f, base - ts * 0.31f, cx + ts * 0.37f, base - ts * 0.27f, paint)
            canvas.drawRect(cx - ts * 0.37f, base - ts * 0.58f, cx - ts * 0.31f, base, paint)
            canvas.drawRect(cx + ts * 0.31f, base - ts * 0.58f, cx + ts * 0.37f, base, paint)
        } else {
            // barril
            VoxelArt.box(canvas, paint, cx, base, ts * 0.6f, ts * 0.66f, ts * 0.14f, 0xFF8D5A2B.toInt(), 0xFFA9743F.toInt())
            paint.color = 0xFF37474F.toInt()
            canvas.drawRect(cx - ts * 0.3f, base - ts * 0.5f, cx + ts * 0.3f, base - ts * 0.44f, paint)
            canvas.drawRect(cx - ts * 0.3f, base - ts * 0.2f, cx + ts * 0.3f, base - ts * 0.14f, paint)
        }
    }
}
