package com.viniciusy.masmorras

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.math.cos
import kotlin.random.Random

/**
 * Mundo aberto: vilarejo no centro, floresta ao redor, rio com ponte e ruínas do chefe.
 * O chão é pré-renderizado em blocos (chunks) de bitmap -> quase zero custo por frame.
 */
class TileMap(val width: Int = 96, val height: Int = 72, val tileSize: Float = 64f) {

    // 0 = livre, 1 = floresta densa (sólido), 2 = água rasa (andável),
    // 6 = caixote/barril (sólido), 7 = objeto sólido (casa, árvore, pedra...), 8 = água funda (sólida)
    val tiles = Array(height) { IntArray(width) }
    // 0 grama, 1 terra, 2 pedra/praça, 3 areia, 4 grama escura, 5 tábuas
    private val ground = Array(height) { IntArray(width) }
    private val treeEdge = Array(height) { BooleanArray(width) }
    private val reach = Array(height) { BooleanArray(width) }
    val lvl = Array(height) { FloatArray(width) } // mantido por compatibilidade (sem relevo)

    companion object {
        const val LEVEL_PX = 26f
        private const val CH = 8 // tiles por chunk
        private const val MAX_CHUNKS = 64
    }

    private class Obj(val kind: Int, val tx: Int, val ty: Int, val w: Int, val h: Int, val v: Int, val style: Int = -1, val dir: Int = 0) {
        @Volatile var spr: VoxelSprites.Spr? = null
    }

    // kinds: 0 árvore, 1 pedra, 2 casa, 3 poço, 4 barraca, 5 poste, 6 pilar, 8 taverna, 9 celeiro, 10 ferraria,
    // 11 igreja, 12 fonte, 13 banco, 14 canteiro, 15 mural de avisos, 16 carroça, 17 varal, 18 horta, 19 cerca,
    // 20 carpinteiro, 21 alfaiate.  dir: 0 porta ao sul, 1 ao norte, 2 a leste, 3 a oeste
    private val objs = ArrayList<Obj>()
    private val rowObjs = Array(height) { ArrayList<Obj>() }

    private val seed = Random.nextInt(1000, 1000000)
    private val ph = FloatArray(6) { Random.nextFloat() * 6.28f }

    private val vcx = 48.5f
    private val vcy = 36.5f
    private val bossTx = 14
    private val bossTy = 14

    // cache de chunks
    private val cw = (width + CH - 1) / CH
    private val chh = (height + CH - 1) / CH
    private val chunks = arrayOfNulls<Bitmap>(cw * chh)
    private val lru = ArrayList<Int>()
    private val bakePaint = Paint()
    private val bmpPaint = Paint().apply { isFilterBitmap = false; isAntiAlias = false }

    init {
        generate()
    }

    private fun col(v: Long): Int = v.toInt()

    // ───────────────────────── utilidades ─────────────────────────

    private fun hash(x: Int, y: Int, k: Int): Int {
        var h = x * 374761393 + y * 668265263 + k * 1274126177 + seed * 31
        h = (h xor (h ushr 13)) * 1274126177
        return (h xor (h ushr 16)) and 0x7FFFFFFF
    }

    private fun rnd(x: Int, y: Int, k: Int): Float = (hash(x, y, k) % 10000) / 10000f

    private fun noise(x: Float, y: Float): Float =
        (sin(x * 0.13f + ph[0]) + sin(y * 0.11f + ph[1]) + sin((x + y) * 0.07f + ph[2]) + sin((x - y) * 0.09f + ph[3])) / 4f

    private fun riverX(y: Int): Float = 77f + sin(y * 0.16f + ph[4]) * 3f

    private fun inB(x: Int, y: Int) = x in 0 until width && y in 0 until height

    // ───────────────────────── GERAÇÃO ─────────────────────────

    private fun generate() {
        // base de grama
        for (y in 0 until height) for (x in 0 until width) {
            ground[y][x] = if (noise(x.toFloat(), y.toFloat()) > 0.28f) 4 else 0
        }
        // floresta de borda
        for (y in 0 until height) for (x in 0 until width) {
            val d = minOf(x, y, width - 1 - x, height - 1 - y)
            val lim = 4 + (noise(x * 1.7f, y * 1.7f) * 1.5f + 1.5f).toInt()
            if (d < lim) tiles[y][x] = 1
        }
        // rio
        for (y in 0 until height) {
            val cx = riverX(y)
            for (x in (cx - 4.5f).toInt()..(cx + 4.5f).toInt()) {
                if (!inB(x, y) || tiles[y][x] == 1) continue
                val d = abs(x + 0.5f - cx)
                if (d < 1.6f) tiles[y][x] = 8
                else if (d < 2.8f) tiles[y][x] = 2
                else if (d < 3.8f && tiles[y][x] == 0) ground[y][x] = 3
            }
        }
        // lagoa
        for (y in 42..58) for (x in 14..35) {
            if (!inB(x, y) || tiles[y][x] == 1) continue
            val dx = (x + 0.5f - 24f) / 5.5f
            val dy = (y + 0.5f - 50f) / 3.6f
            val d = kotlin.math.sqrt(dx * dx + dy * dy)
            if (d < 0.65f) tiles[y][x] = 8
            else if (d < 0.95f) tiles[y][x] = 2
            else if (d < 1.2f) ground[y][x] = 3
        }
        // estradas principais
        fun road(x0: Int, y0: Int, x1: Int, y1: Int) {
            for (y in y0..y1) for (x in x0..x1) {
                if (inB(x, y) && tiles[y][x] == 0) ground[y][x] = 1
            }
        }
        road(4, 35, width - 5, 37)       // leste-oeste
        road(47, 4, 49, height - 5)      // norte-sul
        road(20, 14, 21, 35)             // trilha para as ruínas
        road(14, 13, 21, 14)
        // ponte
        for (yy in 35..37) {
            val cx = riverX(yy)
            for (x in (cx - 3.8f).toInt()..(cx + 3.8f).toInt()) {
                if (inB(x, yy)) { tiles[yy][x] = 0; ground[yy][x] = 5 }
            }
        }
        // praça
        for (y in 28..46) for (x in 40..58) {
            val d = hypot(x + 0.5f - vcx, y + 0.5f - vcy)
            if (tiles[y][x] != 0) continue
            if (d < 6.2f) ground[y][x] = 2
            else if (d < 7.0f && hash(x, y, 5) % 2 == 0) ground[y][x] = 1
        }
        buildRuins()
        buildVillage()
        scatterNature()
        cleanReach()
        for (y in 0 until height) for (x in 0 until width) {
            if (tiles[y][x] != 1) continue
            var near = false
            for (dy in -2..2) for (dx in -2..2) {
                val nx = x + dx
                val ny = y + dy
                if (inB(nx, ny) && tiles[ny][nx] != 1) near = true
            }
            treeEdge[y][x] = near
        }
    }

    private fun addObj(kind: Int, tx: Int, ty: Int, w: Int = 1, h: Int = 1, solid: Boolean = true, style: Int = -1, dir: Int = 0): Obj {
        val o = Obj(kind, tx, ty, w, h, hash(tx, ty, 7), style, dir)
        objs.add(o)
        rowObjs[ty + h - 1].add(o)
        if (solid) for (y in ty until ty + h) for (x in tx until tx + w) if (inB(x, y)) tiles[y][x] = 7
        return o
    }

    private fun canPlace(x: Int, y: Int, w: Int, h: Int, m: Int): Boolean {
        for (yy in y - m until y + h + m) for (xx in x - m until x + w + m) {
            if (!inB(xx, yy)) return false
            if (tiles[yy][xx] != 0) return false
            val g = ground[yy][xx]
            if (g == 1 || g == 2 || g == 3 || g == 5) return false
        }
        return true
    }

    /** Trilha de terra saindo da porta, na direção em que a casa está virada. */
    private fun doorPath(x: Int, y: Int, w: Int, h: Int, dir: Int) {
        var cx: Int; var cy: Int; val dx: Int; val dy: Int
        when (dir) {
            1 -> { cx = x + w / 2; cy = y - 1; dx = 0; dy = -1 }
            2 -> { cx = x + w; cy = y + h - 1; dx = 1; dy = 0 }
            3 -> { cx = x - 1; cy = y + h - 1; dx = -1; dy = 0 }
            else -> { cx = x + w / 2; cy = y + h; dx = 0; dy = 1 }
        }
        for (i in 0 until 16) {
            if (!inB(cx, cy) || tiles[cy][cx] != 0) return
            val g = ground[cy][cx]
            if (g == 1 || g == 2 || g == 5) return
            ground[cy][cx] = 1
            cx += dx; cy += dy
        }
    }

    private fun buildRuins() {
        for (y in bossTy - 8..bossTy + 8) for (x in bossTx - 8..bossTx + 8) {
            if (!inB(x, y) || tiles[y][x] == 1) continue
            val d = hypot(x - bossTx + 0.5f, y - bossTy + 0.5f)
            if (d < 3.6f && tiles[y][x] == 0) ground[y][x] = 2
            else if (d < 7f && tiles[y][x] == 0 && ground[y][x] == 0) ground[y][x] = 4
        }
        for (i in 0 until 8) {
            val a = i * 0.7854f
            val px = (bossTx + 0.5f + cos(a) * 5f).toInt()
            val py = (bossTy + 0.5f + sin(a) * 5f).toInt()
            if (inB(px, py) && tiles[py][px] == 0) addObj(6, px, py)
        }
    }

    private fun free(x: Int, y: Int, w: Int, h: Int): Boolean {
        for (yy in y until y + h) for (xx in x until x + w) if (!inB(xx, yy) || tiles[yy][xx] != 0) return false
        return true
    }

    private fun put(kind: Int, x: Int, y: Int, w: Int = 1, h: Int = 1, style: Int = -1, dir: Int = 0) {
        if (free(x, y, w, h)) addObj(kind, x, y, w, h, true, style, dir)
    }

    private fun buildVillage() {
        val centerX = 48
        val centerY = 36

        // Praça central e piso urbano
        for (y in 27..45) {
            for (x in 39..57) {
                if (!inB(x, y)) continue
                val dx = x - centerX
                val dy = y - centerY
                val d = hypot(dx.toFloat(), dy.toFloat())
                if (d < 8.3f) {
                    ground[y][x] = 2
                    if (d < 6.5f && hash(x, y, 99) % 5 == 0) tiles[y][x] = 0
                } else if (d < 10.2f && hash(x, y, 50) % 3 == 0) {
                    ground[y][x] = 1
                }
            }
        }

        fun road(x0: Int, y0: Int, x1: Int, y1: Int) {
            for (y in minOf(y0, y1)..maxOf(y0, y1)) {
                for (x in minOf(x0, x1)..maxOf(x0, x1)) {
                    if (!inB(x, y)) continue
                    if (tiles[y][x] == 0) ground[y][x] = 1
                }
            }
        }

        road(20, 36, 76, 36)
        road(48, 18, 48, 54)
        road(30, 25, 30, 47)
        road(66, 25, 66, 47)
        road(35, 28, 63, 28)
        road(35, 44, 63, 44)

        for (x in 22..74) {
            if (inB(x, 19) && tiles[19][x] == 0) {
                tiles[19][x] = 7
                ground[19][x] = 2
            }
            if (inB(x, 53) && tiles[53][x] == 0) {
                tiles[53][x] = 7
                ground[53][x] = 2
            }
        }
        for (y in 19..53) {
            if (inB(22, y) && tiles[y][22] == 0) {
                tiles[y][22] = 7
                ground[y][22] = 2
            }
            if (inB(74, y) && tiles[y][74] == 0) {
                tiles[y][74] = 7
                ground[y][74] = 2
            }
        }

        fun placeVillageHouse(tx: Int, ty: Int, w: Int, h: Int, kind: Int = 2, dir: Int = 0): Boolean {
            if (!canPlace(tx, ty, w, h, 1)) return false
            addObj(kind, tx, ty, w, h)
            when (dir) {
                1 -> doorPath(tx + w / 2, ty - 1, w, h, 1)
                2 -> doorPath(tx + w, ty + h / 2, w, h, 2)
                3 -> doorPath(tx - 1, ty + h / 2, w, h, 3)
                else -> doorPath(tx + w / 2, ty + h, w, h, 0)
            }
            return true
        }

        val stoneHomes = listOf(
            intArrayOf(24, 25, 4, 3, 2, 0),
            intArrayOf(28, 29, 4, 3, 2, 0),
            intArrayOf(32, 23, 5, 4, 2, 0),
            intArrayOf(35, 30, 5, 4, 2, 0),
            intArrayOf(24, 39, 4, 3, 2, 0),
            intArrayOf(28, 42, 4, 3, 2, 0),
            intArrayOf(59, 25, 5, 4, 2, 2),
            intArrayOf(63, 29, 5, 4, 2, 2),
            intArrayOf(60, 39, 4, 3, 2, 2),
            intArrayOf(64, 42, 4, 3, 2, 2)
        )

        for (house in stoneHomes) {
            placeVillageHouse(
                house[0], house[1],
                house[2], house[3],
                house[4], house[5]
            )
        }

        placeVillageHouse(38, 17, 6, 5, 8, 0)
        placeVillageHouse(39, 41, 6, 5, 8, 0)
        placeVillageHouse(57, 17, 7, 5, 10, 2)
        placeVillageHouse(60, 36, 5, 5, 11, 1)

        for (stall in listOf(
            intArrayOf(43, 24),
            intArrayOf(46, 24),
            intArrayOf(50, 24),
            intArrayOf(43, 48),
            intArrayOf(46, 48),
            intArrayOf(50, 48)
        )) {
            val sx = stall[0]
            val sy = stall[1]
            if (inB(sx, sy) && tiles[sy][sx] == 0) addObj(4, sx, sy, 2, 1)
        }

        addObj(3, 48, 36)

        for (post in listOf(
            intArrayOf(35, 31), intArrayOf(41, 31), intArrayOf(54, 31), intArrayOf(61, 31),
            intArrayOf(35, 41), intArrayOf(41, 41), intArrayOf(54, 41), intArrayOf(61, 41),
            intArrayOf(47, 20), intArrayOf(47, 52)
        )) {
            val px = post[0]
            val py = post[1]
            if (inB(px, py) && tiles[py][px] == 0) addObj(5, px, py)
        }

        for (y in 15..57) {
            for (x in 24..72) {
                if (tiles[y][x] != 0) continue
                val dist = hypot((x + 0.5f - centerX).toFloat(), (y + 0.5f - centerY).toFloat())
                if (dist > 16f && dist < 20f && hash(x, y, 13) % 10 == 0) {
                    addObj(0, x, y)
                }
                if (dist > 13f && dist < 16f && hash(x, y, 14) % 7 == 0) {
                    ground[y][x] = 4
                }
            }
        }

        for (y in 24..48) {
            for (x in 34..62) {
                if (tiles[y][x] != 0) continue
                val dx = x - centerX
                val dy = y - centerY
                val d = hypot(dx.toFloat(), dy.toFloat())
                if (d > 7.5f && d < 9.2f && hash(x, y, 16) % 5 == 0) {
                    ground[y][x] = 1
                    tiles[y][x] = 6
                }
            }
        }

        tiles[36][48] = 0
        ground[36][48] = 2
        tiles[37][48] = 0
        ground[37][48] = 2
        tiles[36][49] = 0
        ground[36][49] = 2
    }

    private fun scatterNature() {
        for (y in 0 until height) for (x in 0 until width) {
            if (tiles[y][x] != 0) continue
            val g = ground[y][x]
            if (g == 1 || g == 2 || g == 3 || g == 5) continue
            if (hypot(x - bossTx.toFloat(), y - bossTy.toFloat()) < 8.5f) continue
            val dxv = (x + 0.5f - vcx) / 24f
            val dyv = (y + 0.5f - vcy) / 18f
            val inVillage = dxv * dxv + dyv * dyv < 1f
            val r = rnd(x, y, 11)
            if (inVillage) {
                if (r < 0.012f && canPlace(x, y, 1, 1, 1)) addObj(0, x, y)
                continue
            }
            val dens = 0.4f + 1.2f * (noise(x * 0.6f + 7f, y * 0.6f) * 0.5f + 0.5f)
            val base = if (g == 4) 0.09f else 0.04f
            if (r < base * dens) addObj(0, x, y)
            else if (r > 0.988f) addObj(1, x, y)
        }
    }

    private fun cleanReach() {
        val sx = 48
        val sy = 39
        tiles[sy][sx] = 0
        val queue = ArrayDeque<Int>()
        queue.add(sy * width + sx)
        reach[sy][sx] = true
        val dirs = arrayOf(0 to 1, 0 to -1, 1 to 0, -1 to 0)
        while (queue.isNotEmpty()) {
            val cur = queue.removeFirst()
            val cx = cur % width
            val cy = cur / width
            for ((dx, dy) in dirs) {
                val nx = cx + dx
                val ny = cy + dy
                if (!inB(nx, ny) || reach[ny][nx]) continue
                val t = tiles[ny][nx]
                if (t != 0 && t != 2) continue
                reach[ny][nx] = true
                queue.add(ny * width + nx)
            }
        }
        for (y in 0 until height) for (x in 0 until width) {
            if ((tiles[y][x] == 0 || tiles[y][x] == 2) && !reach[y][x]) tiles[y][x] = 1
        }
    }

    fun heightAt(wx: Float, wy: Float): Float = 0f

    fun isWall(wx: Float, wy: Float): Boolean {
        val tx = (wx / tileSize).toInt()
        val ty = (wy / tileSize).toInt()
        if (wx < 0f || wy < 0f || tx >= width || ty >= height) return true
        val t = tiles[ty][tx]
        return t == 1 || t == 6 || t == 7 || t == 8
    }

    fun inSafeZone(wx: Float, wy: Float): Boolean =
        hypot(wx / tileSize - vcx, wy / tileSize - vcy) < 11.5f

    fun worldWidth() = width * tileSize
    fun worldHeight() = height * tileSize

    fun getPlayerSpawn(): Pair<Float, Float> = Pair(48.5f * tileSize, 39.5f * tileSize)

    fun getBossSpawn(): Pair<Float, Float> = Pair((bossTx + 0.5f) * tileSize, (bossTy + 0.5f) * tileSize)

    fun getEnemySpawns(count: Int): List<Pair<Float, Float>> {
        val list = mutableListOf<Pair<Float, Float>>()
        var tries = 0
        while (list.size < count && tries < 3000) {
            tries++
            val tx = Random.nextInt(6, width - 6)
            val ty = Random.nextInt(6, height - 6)
            if (tiles[ty][tx] != 0 || !reach[ty][tx]) continue
            if (hypot(tx + 0.5f - vcx, ty + 0.5f - vcy) < 17f) continue
            if (hypot(tx - bossTx.toFloat(), ty - bossTy.toFloat()) < 9f) continue
            val pack = Random.nextInt(2, 4)
            for (k in 0 until pack) {
                if (list.size >= count) break
                val ox = tx + Random.nextInt(-2, 3)
                val oy = ty + Random.nextInt(-2, 3)
                if (inB(ox, oy) && tiles[oy][ox] == 0) {
                    list.add((ox + 0.5f) * tileSize to (oy + 0.5f) * tileSize)
                }
            }
        }
        return list
    }

    fun prewarm(wx: Float, wy: Float) {
        val cpx = CH * tileSize
        val ccx = (wx / cpx).toInt()
        val ccy = (wy / cpx).toInt()
        for (cy in ccy - 2..ccy + 2) for (cx in ccx - 2..ccx + 2) {
            if (cx in 0 until cw && cy in 0 until chh) getChunk(cx, cy, true)
        }
        startSpriteThread(wx, wy)
    }

    @Volatile private var treesReady = false
    private var spritesStarted = false

    private fun startSpriteThread(wx: Float, wy: Float) {
        if (spritesStarted) return
        spritesStarted = true
        val order = objs.sortedBy {
            val dx = (it.tx + it.w / 2f) * tileSize - wx
            val dy = (it.ty + it.h.toFloat()) * tileSize - wy
            dx * dx + dy * dy
        }
        Thread {
            try {
                for (k in 0..6) treeSprites[k] = VoxelSprites.tree(k)
                treesReady = true
                for (o in order) o.spr = makeSprite(o)
            } catch (e: Throwable) {
                e.printStackTrace()
            }
        }.start()
    }

    private fun getChunk(cx: Int, cy: Int, bake: Boolean): Bitmap? {
        val idx = cy * cw + cx
        val b = chunks[idx]
        if (b != null) {
            if (lru.isEmpty() || lru[lru.size - 1] != idx) {
                lru.remove(idx)
                lru.add(idx)
            }
            return b
        }
        if (!bake) return null
        val px = (CH * tileSize).toInt()
        val arr = IntArray(px * px)
        for (ly in 0 until CH) for (lx in 0 until CH) {
            val tx = cx * CH + lx
            val ty = cy * CH + ly
            if (tx >= width || ty >= height) continue
            bakeTile(arr, px, tx, ty, lx * 64, ly * 64)
        }
        val bmp = Bitmap.createBitmap(px, px, Bitmap.Config.RGB_565)
        bmp.setPixels(arr, 0, px, 0, 0, px, px)
        chunks[idx] = bmp
        lru.add(idx)
        if (lru.size > MAX_CHUNKS) {
            val old = lru.removeAt(0)
            chunks[old] = null
        }
        return bmp
    }

    fun drawFloor(canvas: Canvas, paint: Paint, camX: Float, camY: Float, viewW: Float, viewH: Float) {
        val ts = tileSize
        val cpx = CH * ts
        val c0x = (camX / cpx).toInt().coerceAtLeast(0)
        val c1x = ((camX + viewW) / cpx).toInt().coerceAtMost(cw - 1)
        val c0y = (camY / cpx).toInt().coerceAtLeast(0)
        val c1y = ((camY + viewH) / cpx).toInt().coerceAtMost(chh - 1)
        var budget = 1
        for (cy in c0y..c1y) for (cx in c0x..c1x) {
            val had = chunks[cy * cw + cx] != null
            val b = getChunk(cx, cy, budget > 0)
            if (b != null) {
                if (!had) budget--
                canvas.drawBitmap(
                    b, Math.round(cx * cpx - camX).toFloat(), Math.round(cy * cpx - camY).toFloat(), bmpPaint
                )
            } else {
                paint.style = Paint.Style.FILL
                paint.color = col(0xFF3F7A38)
                canvas.drawRect(cx * cpx - camX, cy * cpx - camY, (cx + 1) * cpx - camX, (cy + 1) * cpx - camY, paint)
            }
        }
        if (budget > 0) {
            loop@ for (cy in (c0y - 1).coerceAtLeast(0)..(c1y + 1).coerceAtMost(chh - 1)) {
                for (cx in (c0x - 1).coerceAtLeast(0)..(c1x + 1).coerceAtMost(cw - 1)) {
                    if (chunks[cy * cw + cx] == null) {
                        getChunk(cx, cy, true)
                        break@loop
                    }
                }
            }
        }
        val sx = (camX / ts).toInt().coerceAtLeast(0)
        val ex = ((camX + viewW) / ts).toInt().coerceAtMost(width - 1)
        val sy = (camY / ts).toInt().coerceAtLeast(0)
        val ey = ((camY + viewH) / ts).toInt().coerceAtMost(height - 1)
        paint.style = Paint.Style.FILL
        for (ty in sy..ey) for (tx in sx..ex) {
            val t = tiles[ty][tx]
            if (t != 2 && t != 8) continue
            val left = tx * ts - camX
            val top = ty * ts - camY
            paint.color = if (t == 2) 0x44FFFFFF else 0x30FFFFFF
            for (k in 0..1) {
                val wy = top + ts * (0.25f + 0.4f * k) + sin(VoxelArt.time * 2f + tx + k * 1.7f) * 3f
                val wx = left + ts * 0.1f + sin(VoxelArt.time * 1.3f + ty + k) * 6f + k * 18f
                canvas.drawRect(wx, wy, wx + ts * 0.38f, wy + 2.5f, paint)
            }
        }
    }

    private val DIRS = arrayOf(intArrayOf(-1, 0), intArrayOf(1, 0), intArrayOf(0, -1), intArrayOf(0, 1))

    private fun soft(g: Int) = g == 0 || g == 1 || g == 3 || g == 4

    private fun put4(arr: IntArray, stride: Int, x: Int, y: Int, c: Int) {
        val col = c or (0xFF shl 24)
        for (yy in 0..3) {
            val b = (y + yy) * stride + x
            for (xx in 0..3) arr[b + xx] = col
        }
    }

    private fun groundTexId(g: Int, tx: Int, ty: Int): Int = when (g) {
        1 -> if (hash(tx, ty, 21) % 3 == 0) Tex.DIRT2 else Tex.DIRT
        3 -> Tex.SAND
        4 -> Tex.DGRASS
        else -> if (noise(tx * 0.8f, ty * 0.8f) > 0.1f) Tex.GRASS2 else Tex.GRASS
    }

    private fun bakeTile(arr: IntArray, stride: Int, tx: Int, ty: Int, ox: Int, oy: Int) {
        val t = tiles[ty][tx]
        val g = ground[ty][tx]
        val water = t == 2 || t == 8
        val nbG = IntArray(4) { -1 }
        val forest = BooleanArray(4)
        val wat = BooleanArray(4)
        val nonPlank = BooleanArray(4)
        for (k in 0..3) {
            val nx = tx + DIRS[k][0]
            val ny = ty + DIRS[k][1]
            if (!inB(nx, ny)) continue
            val nt = tiles[ny][nx]
            forest[k] = nt == 1
            wat[k] = nt == 2 || nt == 8
            val ng = ground[ny][nx]
            nonPlank[k] = ng != 5
            if (!water && soft(g) && nt != 1 && nt != 2 && nt != 8 && soft(ng) && ng != g) nbG[k] = ng
        }
        val sxo = hash(tx, ty, 1) and 15
        val syo = (hash(tx, ty, 1) shr 4) and 15
        val plaza = hypot(tx + 0.5f - vcx, ty + 0.5f - vcy)
        var tex: IntArray? = null
        if (t != 1 && !water && g != 5) {
            val id = when (g) {
                2 -> if (plaza < 5.3f) (if ((tx + ty) % 2 == 0) Tex.FLAG else Tex.FLAG3) else if (plaza < 6.4f) Tex.FLAG2 else Tex.COBBLE
                else -> groundTexId(g, tx, ty)
            }
            tex = Tex.get(id)
        }
        val wTex = Tex.get(if (t == 2) Tex.WATER else Tex.DEEP)
        val fTex = Tex.get(Tex.LEAF_D)
        val pTex = Tex.get(Tex.PLANKS_L)
        for (j in 0..15) for (i in 0..15) {
            val hv = hash(tx * 16 + i, ty * 16 + j, 90)
            var c: Int
            if (t == 1) {
                c = Tex.sh(fTex[((j + syo) and 15) * 16 + ((i + sxo) and 15)], 0.38f)
            } else if (water) {
                c = wTex[((j + syo) and 15) * 16 + ((i + sxo) and 15)]
                val r0 = minOf(minOf(if (!wat[0]) i else 99, if (!wat[1]) 15 - i else 99), minOf(if (!wat[2]) j else 99, if (!wat[3]) 15 - j else 99))
                if (r0 == 0 && hv % 100 < 78) c = 0xDDF2F7
                else if (r0 == 1 && hv % 100 < 30) c = 0xA8DCE8
            } else if (g == 5) {
                c = pTex[(i and 15) * 16 + (j and 15)]
                if (j <= 1 && ty > 0 && ground[ty - 1][tx] != 5) {
                    c = if (j == 0) 0x6D4524 else 0x4A2F18
                } else if (j >= 14 && ty + 1 < height && ground[ty + 1][tx] != 5) {
                    c = if (j == 15) 0x4A2F18 else 0x6D4524
                }
            } else {
                var tt = tex!!
                var gt = g
                for (k in 0..3) {
                    if (nbG[k] < 0) continue
                    val ring = when (k) { 0 -> i; 1 -> 15 - i; 2 -> j; else -> 15 - j }
                    if (ring > 1) continue
                    val pr = if (ring == 0) 55 else 22
                    if (hash(tx * 16 + i, ty * 16 + j, 91 + k) % 100 < pr) { gt = nbG[k]; break }
                }
                if (gt != g) tt = Tex.get(groundTexId(gt, tx, ty))
                c = tt[((j + syo) and 15) * 16 + ((i + sxo) and 15)]
                if (soft(g)) {
                    var f = 1f
                    for (k in 0..3) if (forest[k]) {
                        val ring = when (k) { 0 -> i; 1 -> 15 - i; 2 -> j; else -> 15 - j }
                        val ff = if (ring == 0) 0.72f else if (ring == 1) 0.82f else if (ring == 2) 0.92f else 1f
                        if (ff < f) f = ff
                    }
                    if (f < 1f) c = Tex.sh(c, f)
                }
            }
            put4(arr, stride, ox + i * 4, oy + j * 4, c)
        }
        if (t == 0 && (g == 0 || g == 4)) grassDecor(arr, stride, tx, ty, ox, oy, g == 4)
    }

    private fun grassDecor(arr: IntArray, stride: Int, tx: Int, ty: Int, ox: Int, oy: Int, dark: Boolean) {
        val h = hash(tx, ty, 3)
        if (h % 5 == 0) {
            val ti = 2 + hash(tx, ty, 4) % 12
            val tj = 3 + hash(tx, ty, 5) % 11
            put4(arr, stride, ox + ti * 4, oy + tj * 4, if (dark) 0x2A6A2E else 0x3C8A34)
            put4(arr, stride, ox + ti * 4, oy + (tj - 1) * 4, if (dark) 0x4E9A4C else 0x7BCB62)
        }
        if (!dark && h % 6 == 1) {
            val ti = 3 + hash(tx, ty, 6) % 10
            val tj = 3 + hash(tx, ty, 7) % 10
            val fc = intArrayOf(0xF5F5F5, 0xFFE066, 0xFF8FB1, 0x9FC5FF, 0xE53935)
            val c = fc[hash(tx, ty, 8) % 5]
            put4(arr, stride, ox + ti * 4, oy + (tj + 1) * 4, 0x2F7A2F)
            put4(arr, stride, ox + (ti - 1) * 4, oy + tj * 4, c)
            put4(arr, stride, ox + (ti + 1) * 4, oy + tj * 4, c)
            put4(arr, stride, ox + ti * 4, oy + (tj - 1) * 4, c)
            put4(arr, stride, ox + ti * 4, oy + tj * 4, 0xFFD54F)
        } else if (dark && h % 17 == 1) {
            val ti = 3 + hash(tx, ty, 6) % 10
            val tj = 3 + hash(tx, ty, 7) % 10
            put4(arr, stride, ox + ti * 4, oy + (tj + 1) * 4, 0xE9E0D0)
            for (d in -1..1) put4(arr, stride, ox + (ti + d) * 4, oy + tj * 4, if (d == 0) 0xFFFFFF else 0xD6453A)
        }
    }

    private val treeSprites = arrayOfNulls<VoxelSprites.Spr>(7)
    private val miscSprites = HashMap<Int, VoxelSprites.Spr>()

    private fun treeVariant(v: Int, g: Int): Int {
        val r = v % 100
        return if (g == 4) {
            if (r < 55) 2 else if (r < 70) 3 else if (r < 82) 0 else if (r < 92) 4 else 6
        } else {
            if (r < 35) 0 else if (r < 50) 1 else if (r < 62) 4 else if (r < 72) 5 else if (r < 86) 2 else if (r < 92) 6 else 3
        }
    }

    private fun misc(key: Int, make: () -> VoxelSprites.Spr): VoxelSprites.Spr =
        miscSprites[key] ?: make().also { miscSprites[key] = it }

    private fun makeSprite(o: Obj): VoxelSprites.Spr = when (o.kind) {
        0 -> treeSprites[treeVariant(o.v, ground[o.ty][o.tx])]!!
        1 -> misc(100 + o.v % 4) { VoxelSprites.rock(o.v % 4) }
        3 -> misc(300) { VoxelSprites.well() }
        4 -> misc(400 + o.style) { VoxelSprites.stall(o.style) }
        5 -> misc(500) { VoxelSprites.lamp() }
        6 -> misc(600 + o.v % 2) { VoxelSprites.pillar(o.v % 2) }
        12 -> misc(1200) { VoxelSprites.fountain() }
        13 -> misc(1300) { VoxelSprites.bench() }
        14 -> misc(1400 + o.v % 3) { VoxelSprites.flowerbed(o.v % 3) }
        15 -> misc(1500) { VoxelSprites.noticeBoard() }
        16 -> misc(1600) { VoxelSprites.cart() }
        17 -> misc(1700 + o.w) { VoxelSprites.clothesline(o.w) }
        18 -> misc(1800 + o.v % 3) { VoxelSprites.garden(o.v % 3) }
        19 -> misc(1900 + o.w) { VoxelSprites.fence(o.w) }
        else -> VoxelSprites.house(o.kind, if (o.style >= 0) o.style else o.v % 4, o.w, o.h, o.v, o.dir)
    }

    private fun blit(canvas: Canvas, s: VoxelSprites.Spr, cx: Float, baseY: Float) {
        canvas.drawBitmap(
            s.bmp, Math.round(cx - s.padL).toFloat(), Math.round(baseY - (s.bmp.height - s.padB)).toFloat(), bmpPaint
        )
    }

    fun drawRow(canvas: Canvas, paint: Paint, ty: Int, camX: Float, camY: Float, viewW: Float) {
        if (ty < 0 || ty >= height) return
        val ts = tileSize
        val sx = (camX / ts).toInt().coerceAtLeast(0)
        val ex = ((camX + viewW) / ts).toInt().coerceAtMost(width - 1)
        for (tx in sx..ex) {
            val t = tiles[ty][tx]
            if (t == 1) {
                if (treeEdge[ty][tx] && treesReady) {
                    val v = hash(tx, ty, 12)
                    if (v % 3 == 0) continue
                    val off = ((v shr 5) % 20 - 10).toFloat()
                    val variant = if (v % 10 < 6) 2 + (v / 16) % 2 else if (v % 10 < 8) 0 else if (v % 10 < 9) 4 else 6
                    val s = treeSprites[variant] ?: continue
                    blit(canvas, s, tx * ts - camX + ts / 2f + off, (ty + 1) * ts - camY - 4f)
                }
            } else if (t == 6) {
                drawProp(canvas, paint, tx, ty, tx * ts - camX, ty * ts - camY)
            }
        }
        val list = rowObjs[ty]
        for (i in 0 until list.size) {
            val o = list[i]
            val s = o.spr ?: continue
            val cx = (o.tx + o.w / 2f) * ts - camX
            if (cx + s.bmp.width < 0f || cx - s.bmp.width > viewW) continue
            val baseY = (o.ty + o.h) * ts - camY - 4f
            blit(canvas, s, cx, baseY)
            effects(canvas, paint, o, s, Math.round(cx - s.padL).toFloat(), Math.round(baseY - (s.bmp.height - s.padB)).toFloat())
        }
    }

    private fun effects(c: Canvas, p: Paint, o: Obj, s: VoxelSprites.Spr, dx: Float, dy: Float) {
        val t = VoxelArt.time
        p.style = Paint.Style.FILL
        when (o.kind) {
            2, 8, 10, 20, 21 -> {
                if (s.fx <= 0f) return
                val x = dx + s.fx
                val y = dy + s.fy
                if (o.kind == 10) {
                    val g = 0.7f + 0.3f * sin(t * 9f)
                    p.color = VoxelArt.alpha(col(0xFFFF8A2B), 0.3f * g)
                    c.drawCircle(x, y, 24f, p)
                }
                for (i in 0..2) {
                    val tt = (t * 0.5f + i * 0.33f) % 1f
                    val px = x - 4f + tt * 16f + sin(tt * 6f + i) * 3f
                    p.color = VoxelArt.alpha(col(0xFFE6E6E6), 0.55f * (1f - tt))
                    val sz = 8f + tt * 8f
                    c.drawRect(px, y - tt * 56f, px + sz, y - tt * 56f + sz, p)
                }
            }
            5 -> {
                val fl = 1f + 0.05f * sin(t * 7f + o.tx)
                p.color = 0x14FFD27A
                c.drawCircle(dx + s.fx, dy + s.fy, 58f * fl, p)
                p.color = 0x22FFD27A
                c.drawCircle(dx + s.fx, dy + s.fy, 32f * fl, p)
            }
            12 -> {
                val x = dx + s.fx
                val y = dy + s.fy
                for (i in 0..7) {
                    val tt = (t * 0.8f + i / 8f) % 1f
                    val a = i * 0.785f
                    val px = x + cos(a) * tt * 34f
                    val py = y - (1f - (2f * tt - 1f) * (2f * tt - 1f)) * 30f + tt * tt * 26f
                    p.color = VoxelArt.alpha(col(0xFFBFE6F5), 0.85f * (1f - tt * 0.5f))
                    c.drawRect(px - 3f, py - 3f, px + 3f, py + 3f, p)
                }
                p.color = VoxelArt.alpha(col(0xFFDFF4FB), 0.8f)
                c.drawRect(x - 3f, y - 22f - 4f * sin(t * 6f), x + 3f, y + 4f, p)
            }
        }
    }

    private fun drawProp(canvas: Canvas, paint: Paint, tx: Int, ty: Int, left: Float, top: Float) {
        val ts = tileSize
        val v = hash(tx, ty, 13)
        val cx = left + ts / 2f
        val base = top + ts - 6f
        paint.style = Paint.Style.FILL
        paint.color = 0x44000000
        canvas.drawOval(cx - ts * 0.42f, base - ts * 0.08f, cx + ts * 0.42f, base + ts * 0.12f, paint)
        if (v % 2 == 0) {
            VoxelArt.box(canvas, paint, cx, base, ts * 0.74f, ts * 0.58f, ts * 0.22f, col(0xFF9C6B3A), col(0xFFB98550))
            paint.color = col(0xFF6D4524)
            canvas.drawRect(cx - ts * 0.37f, base - ts * 0.31f, cx + ts * 0.37f, base - ts * 0.27f, paint)
            canvas.drawRect(cx - ts * 0.37f, base - ts * 0.58f, cx - ts * 0.31f, base, paint)
            canvas.drawRect(cx + ts * 0.31f, base - ts * 0.58f, cx + ts * 0.37f, base, paint)
        } else {
            VoxelArt.box(canvas, paint, cx, base, ts * 0.6f, ts * 0.66f, ts * 0.14f, col(0xFF8D5A2B), col(0xFFA9743F))
            paint.color = col(0xFF37474F)
            canvas.drawRect(cx - ts * 0.3f, base - ts * 0.5f, cx + ts * 0.3f, base - ts * 0.44f, paint)
            canvas.drawRect(cx - ts * 0.3f, base - ts * 0.2f, cx + ts * 0.3f, base - ts * 0.14f, paint)
        }
    }
}
