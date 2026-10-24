package com.viniciusy.masmorras

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.random.Random

class TileMap(val width: Int = 56, val height: Int = 56, val tileSize: Float = 64f) {

    // 0 = chão, 1 = parede, 2 = água rasa (andável), 3 = lava (andável, queima),
    // 4 = pilar (sólido), 5 = braseiro (sólido), 6 = caixote/barril (sólido)
    val tiles = Array(height) { IntArray(width) }
    private val variant = Array(height) { IntArray(width) { Random.nextInt(1000) } }
    private val edge = Array(height) { BooleanArray(width) }
    private val torch = Array(height) { BooleanArray(width) }
    private val theme = Array(height) { IntArray(width) }       // 0 pedra, 1 musgo, 2 cripta, 3 brasa
    private val carpet = Array(height) { BooleanArray(width) }
    private val explored = Array(height) { BooleanArray(width) }
    private var mini: Bitmap? = null
    private var lastRX = -999
    private var lastRY = -999

    // relevo: altura de cada tile em "níveis" (0 = chão, 0.5 = degrau, 1 = bloco, até 3 = torre)
    val lvl = Array(height) { FloatArray(width) }

    /** Lugares de poção (coordenadas de mundo), geralmente em cima de torres e ilhas. */
    val potionSpots = mutableListOf<Pair<Float, Float>>()
    private val potionTiles = mutableListOf<Pair<Int, Int>>()

    companion object {
        const val LEVEL_PX = 26f
        private const val T_NORMAL = 0
        private const val T_PILLARS = 1
        private const val T_FLOOD = 2
        private const val T_LAVA = 3
        private const val T_CRYPT = 4
        private const val T_TOWER = 5
        private const val T_START = 6
        private const val T_BOSS = 7
    }

    private class Room(val x: Int, val y: Int, val w: Int, val h: Int) {
        var type = T_NORMAL
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

    private fun inside(x: Int, y: Int) = x in 1 until width - 1 && y in 1 until height - 1

    private fun setT(x: Int, y: Int, t: Int) {
        if (inside(x, y)) tiles[y][x] = t
    }

    private fun setL(x: Int, y: Int, l: Float) {
        if (!inside(x, y)) return
        if (tiles[y][x] == 1) return
        tiles[y][x] = 0
        lvl[y][x] = l
    }

    private fun setTheme(r: Room, th: Int) {
        for (y in r.y until r.y + r.h) for (x in r.x until r.x + r.w) theme[y][x] = th
    }

    private fun generate() {
        for (y in 0 until height) for (x in 0 until width) tiles[y][x] = 1

        var tries = 0
        while (rooms.size < 12 && tries < 700) {
            tries++
            val w = Random.nextInt(8, 15)
            val h = Random.nextInt(7, 12)
            val x = Random.nextInt(2, width - w - 2)
            val y = Random.nextInt(2, height - h - 2)
            val r = Room(x, y, w, h)
            if (rooms.any { it.overlaps(r, 3) }) continue
            rooms.add(r)
        }
        if (rooms.size < 3) {
            rooms.clear()
            rooms.add(Room(8, 8, 10, 8))
            rooms.add(Room(24, 12, 12, 9))
            rooms.add(Room(36, 30, 12, 10))
        }

        rooms.forEach { r ->
            for (y in r.y until r.y + r.h) for (x in r.x until r.x + r.w) tiles[y][x] = 0
        }
        for (i in 1 until rooms.size) connect(rooms[i - 1], rooms[i])
        if (rooms.size > 2) connect(rooms.last(), rooms.first())
        // atalhos extras: o mapa deixa de ser uma linha reta de salas
        for (i in 0 until rooms.size - 2) {
            if (Random.nextInt(100) < 35) connect(rooms[i], rooms[i + 2])
        }

        // tipos de sala
        val pool = mutableListOf(T_PILLARS, T_FLOOD, T_LAVA, T_CRYPT, T_TOWER, T_LAVA, T_TOWER, T_NORMAL)
        pool.shuffle()
        rooms.forEachIndexed { i, r ->
            r.type = when (i) {
                0 -> T_START
                rooms.size - 1 -> T_BOSS
                else -> pool[(i - 1) % pool.size]
            }
        }
        rooms.forEach { decorate(it) }

        // garante que tudo que é andável está conectado ao spawn
        val sx = rooms[0].cx
        val sy = rooms[0].cy
        tiles[sy][sx] = 0
        lvl[sy][sx] = 0f
        val bx = rooms.last().cx
        val by = rooms.last().cy
        tiles[by][bx] = 0
        lvl[by][bx] = 0f

        val seen = Array(height) { BooleanArray(width) }
        val queue = ArrayDeque<Int>()
        queue.add(sy * width + sx)
        seen[sy][sx] = true
        val dirs = arrayOf(0 to 1, 0 to -1, 1 to 0, -1 to 0)
        while (queue.isNotEmpty()) {
            val cur = queue.removeFirst()
            val cx = cur % width
            val cy = cur / width
            for ((dx, dy) in dirs) {
                val nx = cx + dx
                val ny = cy + dy
                if (nx < 0 || ny < 0 || nx >= width || ny >= height) continue
                if (seen[ny][nx]) continue
                if (isSolidTile(tiles[ny][nx])) continue
                seen[ny][nx] = true
                queue.add(ny * width + nx)
            }
        }
        for (y in 0 until height) for (x in 0 until width) {
            val t = tiles[y][x]
            if ((t == 0 || t == 2 || t == 3) && !seen[y][x]) {
                tiles[y][x] = 1
                lvl[y][x] = 0f
            }
        }

        potionTiles.forEach { (px, py) ->
            if (tiles[py][px] == 0 && lvl[py][px] > 0f) {
                potionSpots.add((px + 0.5f) * tileSize to (py + 0.5f) * tileSize)
            }
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
            if (y + 1 < height && tiles[y + 1][x] == 0 && lvl[y + 1][x] == 0f && variant[y][x] % 9 == 0) {
                torch[y][x] = true
            }
        }

        mini = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    }

    private fun isSolidTile(t: Int) = t == 1 || t == 4 || t == 5 || t == 6

    private fun decorate(r: Room) {
        when (r.type) {
            T_START -> decorStart(r)
            T_PILLARS -> decorPillars(r)
            T_FLOOD -> decorFlood(r)
            T_LAVA -> decorLava(r)
            T_CRYPT -> decorCrypt(r)
            T_TOWER -> decorTower(r)
            T_BOSS -> decorBoss(r)
            else -> decorNormal(r)
        }
        if (r.type != T_START && r.type != T_BOSS) scatterCrates(r)
    }

    private fun scatterCrates(r: Room) {
        val corners = arrayOf(
            r.x to r.y, r.x + r.w - 1 to r.y,
            r.x to r.y + r.h - 1, r.x + r.w - 1 to r.y + r.h - 1
        )
        corners.forEach { (cx, cy) ->
            if (Random.nextInt(100) < 45 && tiles[cy][cx] == 0 && lvl[cy][cx] == 0f && wallNeighbors(cx, cy) >= 2) {
                tiles[cy][cx] = 6
            }
        }
    }

    private fun decorStart(r: Room) {
        setTheme(r, 0)
        // bloco de treino perto do canto: pule nele para pegar a poção
        val x0 = r.x + r.w - 4
        val y0 = r.y + 1
        setL(x0, y0, 1f); setL(x0 + 1, y0, 1f)
        setL(x0, y0 + 1, 0.5f); setL(x0 + 1, y0 + 1, 0.5f)
        potionTiles.add(x0 to y0)
        setT(r.x, r.y + r.h - 1, 6)
        setT(r.x + 1, r.y + r.h - 1, 6)
    }

    private fun decorNormal(r: Room) {
        setTheme(r, 0)
        if (Random.nextInt(100) < 45) {
            val px = Random.nextInt(r.x + 2, r.x + r.w - 4)
            val py = Random.nextInt(r.y + 2, r.y + r.h - 3)
            for (dy in 0..1) for (dx in 0..2) if (tiles[py + dy][px + dx] == 0) tiles[py + dy][px + dx] = 2
        }
        if (Random.nextInt(100) < 55) {
            // plataforma com degrau
            val px = r.x + 2
            val py = r.y + 2
            val pw = r.w - 5
            for (x in px until px + pw) {
                setL(x, py, 1f); setL(x, py + 1, 1f)
                setL(x, py + 2, 0.5f)
            }
            potionTiles.add(px + pw / 2 to py)
        }
    }

    private fun decorPillars(r: Room) {
        setTheme(r, 1)
        var y = r.y + 2
        while (y < r.y + r.h - 2) {
            var x = r.x + 2
            while (x < r.x + r.w - 2) {
                if (x != r.cx && y != r.cy) setT(x, y, 4)
                x += 3
            }
            y += 3
        }
    }

    private fun decorFlood(r: Room) {
        setTheme(r, 1)
        for (y in r.y + 2 until r.y + r.h - 2) for (x in r.x + 2 until r.x + r.w - 2) {
            if (abs(x - r.cx) + abs(y - r.cy) <= 4 || Random.nextInt(100) < 35) setT(x, y, 2)
        }
        // ilhotas de pedra em degraus
        setL(r.cx, r.cy, 1f)
        setL(r.cx + 1, r.cy, 0.5f)
        setL(r.cx - 1, r.cy, 0.5f)
        potionTiles.add(r.cx to r.cy)
    }

    private fun decorLava(r: Room) {
        setTheme(r, 3)
        // ilha de pedra cercada de lava: é preciso PULAR para chegar nela
        val cx = r.cx
        val cy = r.cy
        for (y in cy - 2..cy + 2) for (x in cx - 2..cx + 2) setT(x, y, 3)
        for (y in cy - 1..cy + 1) for (x in cx - 1..cx + 1) setL(x, y, 1f)
        potionTiles.add(cx to cy)
        // braseiros nos cantos
        setT(r.x, r.y, 5)
        setT(r.x + r.w - 1, r.y, 5)
    }

    private fun decorCrypt(r: Room) {
        setTheme(r, 2)
        val cx = r.cx
        val cy = r.cy
        // tapete vermelho até o altar
        for (y in cy - 1 until r.y + r.h) carpet[y][cx] = true
        // altar elevado com degrau
        for (x in cx - 2..cx + 1) {
            setL(x, cy - 2, 1f)
            setL(x, cy - 1, 1f)
            setL(x, cy, 0.5f)
        }
        potionTiles.add(cx - 1 to cy - 2)
        setT(r.x, r.y, 5)
        setT(r.x + r.w - 1, r.y, 5)
        setT(r.x, r.y + r.h - 1, 5)
        setT(r.x + r.w - 1, r.y + r.h - 1, 5)
    }

    private fun decorTower(r: Room) {
        setTheme(r, 1)
        // escada de blocos 1 -> 2 -> 3: cada degrau exige um pulo
        val x0 = r.x + 2
        val y0 = r.y + 2
        for (dy in 0..1) {
            setL(x0, y0 + dy, 3f)
            setL(x0 + 1, y0 + dy, 2f)
            setL(x0 + 2, y0 + dy, 1f)
        }
        potionTiles.add(x0 to y0)
        // mirante do outro lado
        val ex = r.x + r.w - 4
        val ey = r.y + r.h - 3
        setL(ex, ey, 1f); setL(ex + 1, ey, 1f)
        setL(ex, ey - 1, 1.5f); setL(ex + 1, ey - 1, 1.5f)
    }

    private fun decorBoss(r: Room) {
        setTheme(r, 3)
        val cx = r.cx
        val cy = r.cy
        // poças de lava nos quatro cantos
        for (dy in 0..1) for (dx in 0..1) {
            setT(r.x + 1 + dx, r.y + 1 + dy, 3)
            setT(r.x + r.w - 2 - dx, r.y + 1 + dy, 3)
            setT(r.x + 1 + dx, r.y + r.h - 2 - dy, 3)
            setT(r.x + r.w - 2 - dx, r.y + r.h - 2 - dy, 3)
        }
        // pilares e braseiros em volta do trono
        setT(cx - 3, cy - 2, 4); setT(cx + 3, cy - 2, 4)
        setT(cx - 3, cy + 2, 5); setT(cx + 3, cy + 2, 5)
        // tapete até o trono
        for (y in cy until r.y + r.h) carpet[y][cx] = true
        // trono elevado ao norte
        for (x in cx - 1..cx + 1) {
            setL(x, r.y + 1, 1.5f)
            setL(x, r.y + 2, 1f)
        }
        potionTiles.add(cx to r.y + 1)
    }

    fun heightAt(wx: Float, wy: Float): Float {
        val tx = (wx / tileSize).toInt()
        val ty = (wy / tileSize).toInt()
        if (wx < 0f || wy < 0f || tx >= width || ty >= height) return 0f
        return lvl[ty][tx] * LEVEL_PX
    }

    fun tileAt(wx: Float, wy: Float): Int {
        val tx = (wx / tileSize).toInt()
        val ty = (wy / tileSize).toInt()
        if (wx < 0f || wy < 0f || tx >= width || ty >= height) return 1
        return tiles[ty][tx]
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
        return isSolidTile(tiles[ty][tx])
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
        while (list.size < count && tries < 900) {
            tries++
            val r = pool[tries % pool.size]
            val tx = Random.nextInt(r.x, r.x + r.w)
            val ty = Random.nextInt(r.y, r.y + r.h)
            if (tiles[ty][tx] == 0 && lvl[ty][tx] == 0f) {
                val px = tx * tileSize + tileSize / 2
                val py = ty * tileSize + tileSize / 2
                if (hypot(px - sx, py - sy) > 350f) list.add(px to py)
            }
        }
        return list
    }

    // ───────────────────────── MINIMAPA ─────────────────────────

    fun reveal(wx: Float, wy: Float) {
        val cx = (wx / tileSize).toInt()
        val cy = (wy / tileSize).toInt()
        if (cx == lastRX && cy == lastRY) return
        lastRX = cx
        lastRY = cy
        val bmp = mini ?: return
        for (y in cy - 8..cy + 8) for (x in cx - 8..cx + 8) {
            if (x < 0 || y < 0 || x >= width || y >= height) continue
            if (hypot((x - cx).toFloat(), (y - cy).toFloat()) > 8f) continue
            if (explored[y][x]) continue
            explored[y][x] = true
            val t = tiles[y][x]
            val col = when {
                t == 1 -> if (edge[y][x]) 0xFF3A3646.toInt() else 0
                t == 2 -> 0xFF2A7092.toInt()
                t == 3 -> 0xFFE0561A.toInt()
                t == 4 || t == 5 || t == 6 -> 0xFFA8957E.toInt()
                lvl[y][x] > 0f -> 0xFFD0CAE4.toInt()
                else -> 0xFF8A859C.toInt()
            }
            bmp.setPixel(x, y, col)
        }
    }

    fun drawMinimap(canvas: Canvas, paint: Paint, left: Float, top: Float, size: Float, px: Float, py: Float) {
        val bmp = mini ?: return
        val cell = size / maxOf(width, height)
        val w = cell * width
        val h = cell * height
        paint.style = Paint.Style.FILL
        paint.color = 0xAA000000.toInt()
        canvas.drawRoundRect(left - 6f, top - 6f, left + w + 6f, top + h + 6f, 12f, 12f, paint)
        val old = paint.isFilterBitmap
        paint.isFilterBitmap = false
        canvas.drawBitmap(bmp, null, RectF(left, top, left + w, top + h), paint)
        paint.isFilterBitmap = old
        val mx = left + px / tileSize * cell
        val my = top + py / tileSize * cell
        paint.style = Paint.Style.FILL
        paint.color = if ((VoxelArt.time * 3f).toInt() % 2 == 0) 0xFFFFFFFF.toInt() else 0xFFFFD54F.toInt()
        canvas.drawCircle(mx, my, 4f, paint)
    }

    // ───────────────────────── DESENHO ─────────────────────────

    private fun startX(camX: Float) = (camX / tileSize).toInt().coerceAtLeast(0)
    private fun endX(camX: Float, viewW: Float) = ((camX + viewW) / tileSize).toInt().coerceAtMost(width - 1)

    /** Chão, poças, lava e luz das tochas. */
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
                    3 -> drawLava(canvas, paint, tx, ty, left, top)
                    else -> drawFloorTile(canvas, paint, tx, ty, left, top)
                }
            }
        }
        // luzes: tochas, lava e braseiros
        paint.style = Paint.Style.FILL
        for (ty in sy..(ey + 2).coerceAtMost(height - 1)) {
            for (tx in sx..ex) {
                val t = tiles[ty][tx]
                if (torch[ty][tx]) {
                    val cx = tx * ts - camX + ts / 2
                    val cy = (ty + 1) * ts - camY + ts * 0.5f
                    val flick = 1f + 0.06f * sin(VoxelArt.time * 9f + tx * 2.3f)
                    paint.color = 0x0EFFA726
                    canvas.drawCircle(cx, cy, ts * 2.6f * flick, paint)
                    paint.color = 0x14FFA726
                    canvas.drawCircle(cx, cy, ts * 1.8f * flick, paint)
                    paint.color = 0x1CFFB74D
                    canvas.drawCircle(cx, cy, ts * 1.0f * flick, paint)
                } else if (t == 3) {
                    val cx = tx * ts - camX + ts / 2
                    val cy = ty * ts - camY + ts / 2
                    val flick = 1f + 0.08f * sin(VoxelArt.time * 3f + tx * 1.7f + ty)
                    paint.color = 0x12FF5A14
                    canvas.drawCircle(cx, cy, ts * 1.9f * flick, paint)
                    paint.color = 0x14FF7A1F
                    canvas.drawCircle(cx, cy, ts * 1.1f * flick, paint)
                } else if (t == 5) {
                    val cx = tx * ts - camX + ts / 2
                    val cy = ty * ts - camY + ts * 0.6f
                    val flick = 1f + 0.1f * sin(VoxelArt.time * 11f + tx * 3.1f)
                    paint.color = 0x12FFA726
                    canvas.drawCircle(cx, cy, ts * 3.0f * flick, paint)
                    paint.color = 0x1AFFA726
                    canvas.drawCircle(cx, cy, ts * 2.0f * flick, paint)
                    paint.color = 0x22FFB74D
                    canvas.drawCircle(cx, cy, ts * 1.1f * flick, paint)
                }
            }
        }
    }

    private fun floorColors(th: Int, v: Int): IntArray {
        // [borda, base]
        return when (th) {
            1 -> intArrayOf(
                0xFF242E29.toInt(),
                when (v % 3) { 0 -> 0xFF4E6155.toInt(); 1 -> 0xFF566A5C.toInt(); else -> 0xFF485A4E.toInt() }
            )
            2 -> intArrayOf(
                0xFF232840.toInt(),
                when (v % 3) { 0 -> 0xFF4A5478.toInt(); 1 -> 0xFF535E86.toInt(); else -> 0xFF434D6F.toInt() }
            )
            3 -> intArrayOf(
                0xFF2E1B19.toInt(),
                when (v % 3) { 0 -> 0xFF6B4A43.toInt(); 1 -> 0xFF745249.toInt(); else -> 0xFF5F403B.toInt() }
            )
            else -> intArrayOf(
                0xFF2F2C38.toInt(),
                when (v % 3) { 0 -> 0xFF5C5866.toInt(); 1 -> 0xFF635E6E.toInt(); else -> 0xFF565260.toInt() }
            )
        }
    }

    private fun drawFloorTile(canvas: Canvas, paint: Paint, tx: Int, ty: Int, left: Float, top: Float) {
        val ts = tileSize
        val right = left + ts
        val bottom = top + ts
        val v = variant[ty][tx]
        val th = theme[ty][tx]
        val cols = floorColors(th, v)
        paint.style = Paint.Style.FILL
        paint.color = cols[0]
        canvas.drawRect(left, top, right, bottom, paint)
        paint.color = cols[1]
        canvas.drawRect(left + 1.5f, top + 1.5f, right - 1.5f, bottom - 1.5f, paint)
        paint.color = 0x33FFFFFF
        canvas.drawRect(left + 1.5f, top + 1.5f, right - 1.5f, top + 4f, paint)
        paint.color = 0x22000000
        canvas.drawRect(left + 1.5f, bottom - 5f, right - 1.5f, bottom - 1.5f, paint)

        // tapete
        if (carpet[ty][tx]) {
            paint.color = 0xFF7E1F2B.toInt()
            canvas.drawRect(left + ts * 0.12f, top, right - ts * 0.12f, bottom, paint)
            paint.color = 0xFF9E2B39.toInt()
            canvas.drawRect(left + ts * 0.2f, top, right - ts * 0.2f, bottom, paint)
            paint.color = 0xFFE0B040.toInt()
            canvas.drawRect(left + ts * 0.12f, top, left + ts * 0.16f, bottom, paint)
            canvas.drawRect(right - ts * 0.16f, top, right - ts * 0.12f, bottom, paint)
        } else {
            when {
                v % 11 == 0 -> {
                    paint.style = Paint.Style.STROKE
                    paint.strokeWidth = 1.6f
                    paint.color = if (th == 3) 0xAAFF6F20.toInt() else 0x66000000
                    path.reset()
                    path.moveTo(left + ts * 0.2f, top + ts * 0.3f)
                    path.lineTo(left + ts * 0.45f, top + ts * 0.5f)
                    path.lineTo(left + ts * 0.4f, top + ts * 0.75f)
                    path.lineTo(left + ts * 0.7f, top + ts * 0.85f)
                    canvas.drawPath(path, paint)
                    paint.style = Paint.Style.FILL
                }
                (v % 9 == 1) || (th == 1 && v % 4 == 1) -> {
                    paint.color = 0x663F7D3A
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
                (v % 53 == 3) || (th == 2 && v % 29 == 3) -> {
                    // ossos
                    paint.style = Paint.Style.STROKE
                    paint.strokeWidth = 3f
                    paint.color = 0xCCE0DCCB.toInt()
                    canvas.drawLine(left + ts * 0.25f, top + ts * 0.35f, left + ts * 0.7f, top + ts * 0.65f, paint)
                    canvas.drawLine(left + ts * 0.25f, top + ts * 0.65f, left + ts * 0.7f, top + ts * 0.35f, paint)
                    paint.style = Paint.Style.FILL
                    canvas.drawCircle(left + ts * 0.48f, top + ts * 0.5f, 6f, paint)
                    paint.color = cols[0]
                    canvas.drawCircle(left + ts * 0.45f, top + ts * 0.49f, 1.6f, paint)
                    canvas.drawCircle(left + ts * 0.52f, top + ts * 0.49f, 1.6f, paint)
                }
            }
        }
        paint.style = Paint.Style.FILL
        // sombra da parede acima e nas laterais
        if (ty > 0 && tiles[ty - 1][tx] == 1) {
            paint.color = 0x55000000
            canvas.drawRect(left, top, right, top + ts * 0.28f, paint)
        }
        paint.color = 0x2A000000
        if (tx > 0 && tiles[ty][tx - 1] == 1) canvas.drawRect(left, top, left + 7f, bottom, paint)
        if (tx + 1 < width && tiles[ty][tx + 1] == 1) canvas.drawRect(right - 7f, top, right, bottom, paint)
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

    private fun drawLava(canvas: Canvas, paint: Paint, tx: Int, ty: Int, left: Float, top: Float) {
        val ts = tileSize
        val right = left + ts
        val bottom = top + ts
        val t = VoxelArt.time
        paint.style = Paint.Style.FILL
        paint.color = 0xFFB3380C.toInt()
        canvas.drawRect(left, top, right, bottom, paint)
        // bolhas de magma
        for (k in 0..3) {
            val bx = left + ts * (0.2f + 0.2f * k) + sin(t * 0.9f + tx * 1.3f + k * 2f) * 7f
            val by = top + ts * (0.25f + 0.17f * ((k * 3) % 4)) + sin(t * 1.3f + ty * 1.1f + k) * 6f
            paint.color = 0xFFFF8A1E.toInt()
            canvas.drawCircle(bx, by, ts * 0.13f, paint)
            paint.color = 0xFFFFD04A.toInt()
            canvas.drawCircle(bx, by, ts * 0.06f, paint)
        }
        // crosta escura
        paint.color = 0x66401006
        val cx = left + ts * (0.5f + 0.25f * sin(t * 0.5f + tx))
        canvas.drawCircle(cx, top + ts * 0.55f, ts * 0.16f, paint)
        // borda de pedra queimada
        paint.color = 0xFF3E1A12.toInt()
        if (ty > 0 && tiles[ty - 1][tx] != 3) canvas.drawRect(left, top, right, top + 5f, paint)
        if (ty + 1 < height && tiles[ty + 1][tx] != 3) canvas.drawRect(left, bottom - 5f, right, bottom, paint)
        if (tx > 0 && tiles[ty][tx - 1] != 3) canvas.drawRect(left, top, left + 5f, bottom, paint)
        if (tx + 1 < width && tiles[ty][tx + 1] != 3) canvas.drawRect(right - 5f, top, right, bottom, paint)
    }

    /** Paredes 3D, pilares, braseiros, props e blocos de relevo de uma linha. */
    fun drawRow(canvas: Canvas, paint: Paint, ty: Int, camX: Float, camY: Float, viewW: Float) {
        if (ty < 0 || ty >= height) return
        val sx = startX(camX)
        val ex = endX(camX, viewW)
        val ts = tileSize
        for (tx in sx..ex) {
            val t = tiles[ty][tx]
            val left = tx * ts - camX
            val top = ty * ts - camY
            when {
                t == 1 -> drawWall(canvas, paint, tx, ty, left, top)
                t == 4 -> drawPillar(canvas, paint, left, top)
                t == 5 -> drawBrazier(canvas, paint, tx, left, top)
                t == 6 -> drawProp(canvas, paint, tx, ty, left, top)
                t == 0 && lvl[ty][tx] > 0f -> drawRaised(canvas, paint, tx, ty, left, top)
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
            if (torch[ty][tx]) {
                drawTorch(canvas, paint, tx, ty, left + ts / 2f, tb + h * 0.5f)
            } else if (v % 13 == 4) {
                drawBanner(canvas, paint, left + ts / 2f, tb, h, v)
            } else if (v % 17 == 5) {
                // rachadura / musgo
                paint.color = 0x553F7D3A
                canvas.drawRect(left + ts * 0.2f, tb + h * 0.55f, left + ts * 0.55f, bottom - 2f, paint)
                paint.color = 0x44000000
                canvas.drawRect(left + ts * 0.62f, tb + 3f, left + ts * 0.66f, bottom - 3f, paint)
            }
        }
    }

    private fun drawBanner(canvas: Canvas, paint: Paint, cx: Float, tb: Float, h: Float, v: Int) {
        val sway = sin(VoxelArt.time * 1.6f + v) * 1.5f
        paint.style = Paint.Style.FILL
        paint.color = 0xFF3E2723.toInt()
        canvas.drawRect(cx - 11f, tb + 1f, cx + 11f, tb + 4f, paint)
        paint.color = if (v % 2 == 0) 0xFF9E2B39.toInt() else 0xFF2E4C8F.toInt()
        path.reset()
        path.moveTo(cx - 9f, tb + 4f)
        path.lineTo(cx + 9f, tb + 4f)
        path.lineTo(cx + 9f + sway, tb + h * 0.95f)
        path.lineTo(cx + sway * 1.4f, tb + h * 0.75f)
        path.lineTo(cx - 9f + sway, tb + h * 0.95f)
        path.close()
        canvas.drawPath(path, paint)
        paint.color = 0xFFE0B040.toInt()
        canvas.drawCircle(cx + sway * 0.4f, tb + h * 0.38f, 3.2f, paint)
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
        val th = theme[ty][tx]
        paint.style = Paint.Style.FILL
        paint.color = 0xFF26232F.toInt()
        canvas.drawRect(left, tt, right, tb, paint)
        var topCol = if (l >= 3f) 0xFFB8B2CF.toInt()
        else if (l >= 2f) 0xFFA39DB8.toInt()
        else if (l >= 1.5f) 0xFF958FAA.toInt()
        else if (l >= 1f) 0xFF8A849D.toInt()
        else 0xFF7A7489.toInt()
        topCol = when (th) {
            1 -> VoxelArt.mix(topCol, 0xFF6FA070.toInt(), 0.22f)
            2 -> VoxelArt.mix(topCol, 0xFF6F7FB0.toInt(), 0.28f)
            3 -> VoxelArt.mix(topCol, 0xFFB06A50.toInt(), 0.25f)
            else -> topCol
        }
        paint.color = topCol
        canvas.drawRect(left + 1.5f, tt + 1.5f, right - 1.5f, tb - 1.5f, paint)
        paint.color = 0x22000000
        canvas.drawRect(left + ts / 2f - 0.75f, tt + 1.5f, left + ts / 2f + 0.75f, tb - 1.5f, paint)
        canvas.drawRect(left + 1.5f, tt + ts / 2f - 0.75f, right - 1.5f, tt + ts / 2f + 0.75f, paint)
        paint.color = 0x40FFFFFF
        canvas.drawRect(left + 1.5f, tt + 1.5f, right - 1.5f, tt + 4.5f, paint)
        canvas.drawRect(left + 1.5f, tt + 1.5f, left + 4.5f, tb - 1.5f, paint)
        if (l >= 1f) {
            // moldura dourada nos blocos altos
            paint.color = 0x55E0B040
            canvas.drawRect(left + 5f, tt + 5f, right - 5f, tt + 7f, paint)
        }
        if (v % 5 == 0) {
            paint.color = 0x663F7D3A
            canvas.drawRect(left + ts * 0.15f, tt + ts * 0.2f, left + ts * 0.45f, tt + ts * 0.4f, paint)
        }
        val sh = if (ty + 1 < height && !isSolidTile(tiles[ty + 1][tx])) lvl[ty + 1][tx] * LEVEL_PX else h
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

    private fun drawPillar(canvas: Canvas, paint: Paint, left: Float, top: Float) {
        val ts = tileSize
        val cx = left + ts / 2f
        val base = top + ts - 4f
        val hh = ts * 1.25f
        paint.style = Paint.Style.FILL
        paint.color = 0x44000000
        canvas.drawOval(cx - ts * 0.42f, base - ts * 0.08f, cx + ts * 0.42f, base + ts * 0.12f, paint)
        // base
        paint.color = 0xFF3A3646.toInt()
        canvas.drawRect(cx - ts * 0.34f, base - ts * 0.16f, cx + ts * 0.34f, base, paint)
        paint.color = 0xFF6A6580.toInt()
        canvas.drawRect(cx - ts * 0.34f, base - ts * 0.16f, cx + ts * 0.34f, base - ts * 0.13f, paint)
        // fuste
        paint.color = 0xFF6E6980.toInt()
        canvas.drawRect(cx - ts * 0.24f, base - hh, cx + ts * 0.24f, base - ts * 0.16f, paint)
        paint.color = 0xFF8F8AA3.toInt()
        canvas.drawRect(cx - ts * 0.24f, base - hh, cx - ts * 0.12f, base - ts * 0.16f, paint)
        paint.color = 0xFF55506A.toInt()
        canvas.drawRect(cx + ts * 0.12f, base - hh, cx + ts * 0.24f, base - ts * 0.16f, paint)
        paint.color = 0x22000000
        canvas.drawRect(cx - ts * 0.24f, base - hh * 0.55f, cx + ts * 0.24f, base - hh * 0.55f + 2f, paint)
        // capitel
        paint.color = 0xFF3A3646.toInt()
        canvas.drawRect(cx - ts * 0.34f, base - hh - ts * 0.16f, cx + ts * 0.34f, base - hh, paint)
        paint.color = 0xFFA39DB8.toInt()
        canvas.drawRect(cx - ts * 0.34f, base - hh - ts * 0.16f, cx + ts * 0.34f, base - hh - ts * 0.12f, paint)
    }

    private fun drawBrazier(canvas: Canvas, paint: Paint, tx: Int, left: Float, top: Float) {
        val ts = tileSize
        val cx = left + ts / 2f
        val base = top + ts - 5f
        val t = VoxelArt.time
        paint.style = Paint.Style.FILL
        paint.color = 0x44000000
        canvas.drawOval(cx - ts * 0.34f, base - ts * 0.07f, cx + ts * 0.34f, base + ts * 0.1f, paint)
        // pedestal
        paint.color = 0xFF3A3646.toInt()
        canvas.drawRect(cx - ts * 0.22f, base - ts * 0.12f, cx + ts * 0.22f, base, paint)
        paint.color = 0xFF55506A.toInt()
        canvas.drawRect(cx - ts * 0.12f, base - ts * 0.42f, cx + ts * 0.12f, base - ts * 0.12f, paint)
        // tigela
        paint.color = 0xFF2B2833.toInt()
        canvas.drawRect(cx - ts * 0.3f, base - ts * 0.58f, cx + ts * 0.3f, base - ts * 0.42f, paint)
        paint.color = 0xFF6A6580.toInt()
        canvas.drawRect(cx - ts * 0.3f, base - ts * 0.58f, cx + ts * 0.3f, base - ts * 0.54f, paint)
        // chama
        val f1 = sin(t * 13f + tx * 3f) * 2f
        val f2 = sin(t * 17f + tx) * 1.5f
        val fy = base - ts * 0.56f
        paint.color = 0xFFFF6F00.toInt()
        path.reset()
        path.moveTo(cx - ts * 0.24f, fy)
        path.lineTo(cx + f1, fy - ts * 0.5f)
        path.lineTo(cx + ts * 0.24f, fy)
        path.close()
        canvas.drawPath(path, paint)
        paint.color = 0xFFFFB300.toInt()
        path.reset()
        path.moveTo(cx - ts * 0.15f, fy)
        path.lineTo(cx + f2, fy - ts * 0.36f)
        path.lineTo(cx + ts * 0.15f, fy)
        path.close()
        canvas.drawPath(path, paint)
        paint.color = 0xFFFFF59D.toInt()
        path.reset()
        path.moveTo(cx - ts * 0.07f, fy)
        path.lineTo(cx + f2 * 0.5f, fy - ts * 0.2f)
        path.lineTo(cx + ts * 0.07f, fy)
        path.close()
        canvas.drawPath(path, paint)
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
