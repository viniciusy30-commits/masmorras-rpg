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
        for (y in 0 until height) for (x in 0 until width) tiles[y][x] = 1

        var tries = 0
        while (rooms.size < 9 && tries < 300) {
            tries++
            val w = Random.nextInt(7, 12)
            val h = Random.nextInt(6, 10)
            val x = Random.nextInt(2, width - w - 2)
            val y = Random.nextInt(2, height - h - 2)
            val r = Room(x, y, w, h)
            if (rooms.any { it.overlaps(r, 3) }) continue
            rooms.add(r)
        }
        if (rooms.isEmpty()) rooms.add(Room(width / 2 - 4, height / 2 - 3, 8, 6))

        rooms.forEach { r ->
            for (y in r.y until r.y + r.h) for (x in r.x until r.x + r.w) tiles[y][x] = 0
        }
        for (i in 1 until rooms.size) connect(rooms[i - 1], rooms[i])
        if (rooms.size > 2) connect(rooms.last(), rooms.first())

        // poças d'água (fora da sala inicial)
        for (i in 1 until rooms.size) {
            val r = rooms[i]
            if (Random.nextInt(100) < 45) {
                val px = Random.nextInt(r.x + 2, r.x + r.w - 3)
                val py = Random.nextInt(r.y + 2, r.y + r.h - 3)
                for (dy in 0..1) for (dx in 0..2) {
                    if (tiles[py + dy][px + dx] == 0) tiles[py + dy][px + dx] = 2
                }
            }
        }

        // caixotes e barris nos cantos das salas
        rooms.forEach { r ->
            val corners = arrayOf(
                r.x to r.y, r.x + r.w - 1 to r.y,
                r.x to r.y + r.h - 1, r.x + r.w - 1 to r.y + r.h - 1
            )
            corners.forEach { (cx, cy) ->
                if (Random.nextInt(100) < 55 && tiles[cy][cx] == 0 && wallNeighbors(cx, cy) >= 2) {
                    tiles[cy][cx] = 6
                }
            }
        }

        // garante que tudo que é andável está conectado ao spawn
        val sx = rooms[0].cx
        val sy = rooms[0].cy
        tiles[sy][sx] = 0
        val seen = Array(height) { BooleanArray(width) }
        val queue = ArrayDeque<Int>()
        queue.add(sy * width + sx)
        seen[sy][sx] = true
        while (queue.isNotEmpty()) {
            val cur = queue.removeFirst()
            val cx = cur % width
            val cy = cur / width
            val dirs = arrayOf(0 to 1, 0 to -1, 1 to 0, -1 to 0)
            for ((dx, dy) in dirs) {
                val nx = cx + dx
                val ny = cy + dy
                if (nx < 0 || ny < 0 || nx >= width || ny >= height) continue
                if (seen[ny][nx]) continue
                val t = tiles[ny][nx]
                if (t == 1 || t == 6) continue
                seen[ny][nx] = true
                queue.add(ny * width + nx)
            }
        }
        for (y in 0 until height) for (x in 0 until width) {
            if ((tiles[y][x] == 0 || tiles[y][x] == 2) && !seen[y][x]) tiles[y][x] = 1
        }

        // bordas visíveis das paredes e tochas
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
        val pool = if (rooms.size > 1) rooms.drop(1) else rooms
        val (sx, sy) = getPlayerSpawn()
        var tries = 0
        while (list.size < count && tries < 600) {
            tries++
            val r = pool[tries % pool.size]
            val tx = Random.nextInt(r.x, r.x + r.w)
            val ty = Random.nextInt(r.y, r.y + r.h)
            if (tiles[ty][tx] == 0) {
                val px = tx * tileSize + tileSize / 2
                val py = ty * tileSize + tileSize / 2
                if (hypot(px - sx, py - sy) > 350f) list.add(px to py)
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
            if (t == 1) drawWall(canvas, paint, tx, ty, left, top) else if (t == 6) drawProp(canvas, paint, tx, ty, left, top)
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
