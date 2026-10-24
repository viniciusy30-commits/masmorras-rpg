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

    private class Obj(val kind: Int, val tx: Int, val ty: Int, val w: Int, val h: Int, val v: Int)

    // kinds: 0 árvore, 1 pedra, 2 casa, 3 poço, 4 barraca, 5 poste, 6 pilar, 8 taverna, 9 celeiro, 10 ferraria
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
        road(34, 47, 68, 48)             // rua sul do vilarejo
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

    private fun addObj(kind: Int, tx: Int, ty: Int, w: Int = 1, h: Int = 1, solid: Boolean = true): Obj {
        val o = Obj(kind, tx, ty, w, h, hash(tx, ty, 7))
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

    private fun doorPath(x: Int, y0: Int) {
        for (y in y0 until minOf(height, y0 + 16)) {
            if (!inB(x, y) || tiles[y][x] != 0) return
            val g = ground[y][x]
            if (g == 1 || g == 2 || g == 5) return
            ground[y][x] = 1
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

    private fun buildVillage() {
        // casas (x, y, w, h, kind)
        val houses = listOf(
            intArrayOf(34, 28, 4, 3, 2), intArrayOf(39, 22, 4, 3, 2), intArrayOf(53, 24, 4, 3, 10),
            intArrayOf(58, 29, 4, 3, 2), intArrayOf(64, 28, 4, 3, 2), intArrayOf(27, 29, 5, 4, 9),
            intArrayOf(57, 38, 6, 4, 8), intArrayOf(64, 40, 4, 3, 2),
            intArrayOf(35, 44, 4, 3, 2), intArrayOf(41, 44, 4, 3, 2), intArrayOf(52, 44, 4, 3, 2),
            intArrayOf(64, 44, 4, 3, 2)
        )
        for (h in houses) {
            if (!canPlace(h[0], h[1], h[2], h[3], 0)) continue
            addObj(h[4], h[0], h[1], h[2], h[3])
            doorPath(h[0] + h[2] / 2, h[1] + h[3])
        }
        // poço, barracas, postes
        addObj(3, 48, 36)
        for (s in listOf(intArrayOf(43, 32), intArrayOf(53, 32), intArrayOf(43, 40), intArrayOf(53, 40))) {
            addObj(4, s[0], s[1], 2, 1)
        }
        for (l in listOf(
            intArrayOf(46, 34), intArrayOf(50, 34), intArrayOf(46, 38), intArrayOf(50, 38),
            intArrayOf(38, 34), intArrayOf(58, 34), intArrayOf(40, 49), intArrayOf(46, 49),
            intArrayOf(52, 49), intArrayOf(58, 49)
        )) {
            if (tiles[l[1]][l[0]] == 0) addObj(5, l[0], l[1])
        }
        // caixotes e barris perto das casas
        for (o in objs.toList()) {
            if (o.kind != 2 && o.kind != 8 && o.kind != 9 && o.kind != 10) continue
            val fy = o.ty + o.h - 1
            for (px in intArrayOf(o.tx - 1, o.tx + o.w)) {
                if (inB(px, fy) && tiles[fy][px] == 0 && ground[fy][px] != 1 && ground[fy][px] != 2 &&
                    hash(px, fy, 9) % 100 < 60
                ) tiles[fy][px] = 6
            }
        }
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
                // poucas árvores, afastadas das construções
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

    // ───────────────────────── COLISÃO / SPAWN ─────────────────────────

    fun heightAt(wx: Float, wy: Float): Float = 0f

    fun isWall(wx: Float, wy: Float): Boolean {
        val tx = (wx / tileSize).toInt()
        val ty = (wy / tileSize).toInt()
        if (wx < 0f || wy < 0f || tx >= width || ty >= height) return true
        val t = tiles[ty][tx]
        return t == 1 || t == 6 || t == 7 || t == 8
    }

    /** Zona segura do vilarejo: inimigos não entram. */
    fun inSafeZone(wx: Float, wy: Float): Boolean =
        hypot(wx / tileSize - vcx, wy / tileSize - vcy) < 11.5f

    fun worldWidth() = width * tileSize
    fun worldHeight() = height * tileSize

    fun getPlayerSpawn(): Pair<Float, Float> = Pair(48.5f * tileSize, 39.5f * tileSize)

    fun getBossSpawn(): Pair<Float, Float> = Pair((bossTx + 0.5f) * tileSize, (bossTy + 0.5f) * tileSize)

    /** Gera grupos (bandos) de 2-3 inimigos espalhados fora do vilarejo. */
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

    // ───────────────────────── CHÃO EM CACHE ─────────────────────────

    /** Renderiza antecipadamente os chunks ao redor de uma posição (chamar ao iniciar o jogo). */
    fun prewarm(wx: Float, wy: Float) {
        val cpx = CH * tileSize
        val ccx = (wx / cpx).toInt()
        val ccy = (wy / cpx).toInt()
        for (cy in ccy - 2..ccy + 2) for (cx in ccx - 2..ccx + 2) {
            if (cx in 0 until cw && cy in 0 until chh) getChunk(cx, cy, true)
        }
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
        val bmp = Bitmap.createBitmap(px, px, Bitmap.Config.RGB_565)
        val c = Canvas(bmp)
        for (ly in 0 until CH) for (lx in 0 until CH) {
            val tx = cx * CH + lx
            val ty = cy * CH + ly
            if (tx >= width || ty >= height) continue
            bakeTile(c, bakePaint, tx, ty, lx * tileSize, ly * tileSize)
        }
        chunks[idx] = bmp
        lru.add(idx)
        if (lru.size > MAX_CHUNKS) {
            val old = lru.removeAt(0)
            chunks[old] = null // sem recycle(): o desenho em hardware ainda pode referenciar
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
        var budget = 2
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
        // pré-carrega vizinhos (1 por frame)
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
        // ondulações animadas da água
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

    // ───────────────────────── BAKE DE TEXTURAS ─────────────────────────

    private fun groundBase(g: Int): Int = when (g) {
        0 -> col(0xFF4F9444)
        1 -> col(0xFF8A6A44)
        2 -> col(0xFF8E8A86)
        3 -> col(0xFFD8C489)
        4 -> col(0xFF35703A)
        else -> col(0xFF8A5A2B)
    }

    private fun groundColor(g: Int, tx: Int, ty: Int): Int {
        val f = 0.93f + 0.07f * noise(tx.toFloat(), ty.toFloat()) + (hash(tx, ty, 1) % 100) / 100f * 0.08f
        return VoxelArt.shade(groundBase(g), f)
    }

    private fun bakeTile(c: Canvas, bp: Paint, tx: Int, ty: Int, lx: Float, ly: Float) {
        val ts = tileSize
        val t = tiles[ty][tx]
        bp.style = Paint.Style.FILL
        if (t == 1) {
            bp.color = col(0xFF14281A)
            c.drawRect(lx, ly, lx + ts, ly + ts, bp)
            for (k in 0..3) {
                val r1 = rnd(tx, ty, 10 + k) * 54f
                val r2 = rnd(tx, ty, 20 + k) * 54f
                bp.color = if (k % 2 == 0) col(0xFF1C3A24) else col(0xFF0E1C12)
                c.drawRect(lx + r1, ly + r2, lx + r1 + 9f, ly + r2 + 7f, bp)
            }
            return
        }
        if (t == 2 || t == 8) {
            bakeWater(c, bp, tx, ty, lx, ly, t)
            return
        }
        val g = ground[ty][tx]
        val base = groundColor(g, tx, ty)
        bp.color = base
        c.drawRect(lx, ly, lx + ts, ly + ts, bp)
        when (g) {
            0, 4 -> bakeGrass(c, bp, tx, ty, lx, ly, base, g == 4)
            1 -> bakeDirt(c, bp, tx, ty, lx, ly, base)
            2 -> bakeCobble(c, bp, tx, ty, lx, ly)
            3 -> bakeSand(c, bp, tx, ty, lx, ly, base)
            else -> bakePlanks(c, bp, tx, ty, lx, ly)
        }
        bakeEdges(c, bp, tx, ty, lx, ly, g)
    }

    private fun bakeGrass(c: Canvas, bp: Paint, tx: Int, ty: Int, lx: Float, ly: Float, base: Int, dark: Boolean) {
        if (hash(tx, ty, 2) % 5 == 0) {
            bp.color = 0x16000000
            c.drawOval(lx + rnd(tx, ty, 3) * 20f, ly + rnd(tx, ty, 4) * 20f, lx + 44f, ly + 40f, bp)
        }
        for (k in 0 until 7) {
            val x = lx + rnd(tx, ty, 30 + k) * (tileSize - 6f)
            val y = ly + rnd(tx, ty, 40 + k) * (tileSize - 10f)
            bp.color = VoxelArt.shade(base, if (k % 2 == 0) 1.22f else 0.76f)
            c.drawRect(x, y, x + 2.2f, y + 5f + rnd(tx, ty, 50 + k) * 4f, bp)
            if (k % 3 == 0) c.drawRect(x + 3f, y + 2f, x + 5.2f, y + 8f, bp)
        }
        if (!dark && hash(tx, ty, 3) % 9 == 0) {
            val cols = intArrayOf(col(0xFFF5F5F5), col(0xFFFFE066), col(0xFFFF8FB1), col(0xFF9FC5FF))
            val fc = cols[hash(tx, ty, 4) % 4]
            val fx = lx + 10f + rnd(tx, ty, 6) * 36f
            val fy = ly + 10f + rnd(tx, ty, 7) * 36f
            for (k in 0..2) {
                val ox = fx + (k - 1) * 7f
                val oy = fy + (k % 2) * 6f
                bp.color = fc
                c.drawRect(ox, oy, ox + 3.5f, oy + 3.5f, bp)
                bp.color = col(0xFFFFD54F)
                c.drawRect(ox + 1f, oy + 1f, ox + 2.5f, oy + 2.5f, bp)
            }
        } else if (dark && hash(tx, ty, 8) % 23 == 0) {
            val mx = lx + 16f + rnd(tx, ty, 9) * 28f
            val my = ly + 20f
            bp.color = col(0xFFE9E0D0)
            c.drawRect(mx, my + 3f, mx + 3f, my + 9f, bp)
            bp.color = col(0xFFD6453A)
            c.drawRect(mx - 3f, my - 1f, mx + 6f, my + 4f, bp)
        }
    }

    private fun bakeDirt(c: Canvas, bp: Paint, tx: Int, ty: Int, lx: Float, ly: Float, base: Int) {
        if (hash(tx, ty, 2) % 3 == 0) {
            bp.color = 0x1E000000
            c.drawOval(lx + 4f, ly + 8f + rnd(tx, ty, 3) * 20f, lx + 60f, ly + 24f + rnd(tx, ty, 3) * 20f, bp)
        }
        for (k in 0 until 6) {
            val x = lx + rnd(tx, ty, 30 + k) * 56f
            val y = ly + rnd(tx, ty, 40 + k) * 56f
            val s = 2.5f + rnd(tx, ty, 50 + k) * 3f
            bp.color = VoxelArt.shade(base, if (k % 2 == 0) 1.25f else 0.72f)
            c.drawRect(x, y, x + s, y + s * 0.8f, bp)
        }
    }

    private fun bakeSand(c: Canvas, bp: Paint, tx: Int, ty: Int, lx: Float, ly: Float, base: Int) {
        bp.color = 0x30FFFFFF
        for (k in 0..1) {
            val y = ly + 12f + k * 26f + rnd(tx, ty, 60 + k) * 8f
            c.drawRect(lx + 6f, y, lx + 30f + rnd(tx, ty, 62 + k) * 24f, y + 2f, bp)
        }
        for (k in 0..3) {
            val x = lx + rnd(tx, ty, 30 + k) * 58f
            val y = ly + rnd(tx, ty, 40 + k) * 58f
            bp.color = VoxelArt.shade(base, 0.8f)
            c.drawRect(x, y, x + 2f, y + 2f, bp)
        }
    }

    private fun bakeCobble(c: Canvas, bp: Paint, tx: Int, ty: Int, lx: Float, ly: Float) {
        bp.color = col(0xFF55514E)
        c.drawRect(lx, ly, lx + tileSize, ly + tileSize, bp)
        for (r in 0..1) {
            val off = if (r == 0) 0f else 16f
            for (k in 0..2) {
                val sx = k * 32f - off
                val l = maxOf(sx, 0f) + 1.5f
                val rgt = minOf(sx + 32f, tileSize) - 1.5f
                if (rgt - l < 4f) continue
                val f = 0.88f + (hash(tx * 3 + k, ty * 2 + r, 70) % 100) / 100f * 0.26f
                bp.color = VoxelArt.shade(col(0xFF98938E), f)
                c.drawRect(lx + l, ly + r * 32f + 1.5f, lx + rgt, ly + r * 32f + 30.5f, bp)
                bp.color = 0x30FFFFFF
                c.drawRect(lx + l, ly + r * 32f + 1.5f, lx + rgt, ly + r * 32f + 4f, bp)
                bp.color = 0x28000000
                c.drawRect(lx + l, ly + r * 32f + 27f, lx + rgt, ly + r * 32f + 30.5f, bp)
            }
        }
        if (hash(tx, ty, 71) % 14 == 0) {
            bp.color = 0x553F7D3A
            c.drawRect(lx + 12f, ly + 30f, lx + 30f, ly + 34f, bp)
        }
    }

    private fun bakePlanks(c: Canvas, bp: Paint, tx: Int, ty: Int, lx: Float, ly: Float) {
        for (k in 0..3) {
            val f = 0.85f + (hash(tx * 4 + k, ty, 80) % 100) / 100f * 0.3f
            bp.color = VoxelArt.shade(col(0xFF8A5A2B), f)
            c.drawRect(lx + k * 16f, ly, lx + k * 16f + 14.5f, ly + tileSize, bp)
            bp.color = 0x33000000
            c.drawRect(lx + k * 16f + 14.5f, ly, lx + k * 16f + 16f, ly + tileSize, bp)
            bp.color = col(0xFF3B2412)
            c.drawRect(lx + k * 16f + 6f, ly + 6f, lx + k * 16f + 8.5f, ly + 8.5f, bp)
            c.drawRect(lx + k * 16f + 6f, ly + 55f, lx + k * 16f + 8.5f, ly + 57.5f, bp)
        }
        // corrimões nas bordas norte/sul da ponte
        if (ty > 0 && ground[ty - 1][tx] != 5) {
            bp.color = col(0xFF4A2F18)
            c.drawRect(lx, ly, lx + tileSize, ly + 7f, bp)
            bp.color = col(0xFF6D4524)
            c.drawRect(lx, ly, lx + tileSize, ly + 3f, bp)
        }
        if (ty + 1 < height && ground[ty + 1][tx] != 5) {
            bp.color = col(0xFF4A2F18)
            c.drawRect(lx, ly + tileSize - 8f, lx + tileSize, ly + tileSize, bp)
            bp.color = col(0xFF6D4524)
            c.drawRect(lx, ly + tileSize - 8f, lx + tileSize, ly + tileSize - 5f, bp)
        }
    }

    private fun bakeWater(c: Canvas, bp: Paint, tx: Int, ty: Int, lx: Float, ly: Float, t: Int) {
        val ts = tileSize
        bp.color = if (t == 2) col(0xFF3C95B5) else col(0xFF1E6A93)
        c.drawRect(lx, ly, lx + ts, ly + ts, bp)
        val f = 0.94f + (hash(tx, ty, 1) % 100) / 100f * 0.1f
        bp.color = if (t == 2) VoxelArt.alpha(VoxelArt.shade(col(0xFF3C95B5), f), 0.5f) else 0x22000000
        c.drawRect(lx + 6f, ly + 10f, lx + 40f, ly + 30f, bp)
        // espuma / transição onde encosta em terra
        val dirs = arrayOf(intArrayOf(-1, 0), intArrayOf(1, 0), intArrayOf(0, -1), intArrayOf(0, 1))
        for (d in dirs) {
            val nx = tx + d[0]
            val ny = ty + d[1]
            if (!inB(nx, ny)) continue
            val nt = tiles[ny][nx]
            if (nt == 2 || nt == 8) {
                if (t == 2 && nt == 8) {
                    bp.color = 0x331E6A93
                    strip(c, bp, lx, ly, d[0], d[1], 14f)
                }
                continue
            }
            bp.color = 0x99FFFFFF.toInt()
            strip(c, bp, lx, ly, d[0], d[1], 3.5f)
            bp.color = 0x44FFFFFF
            strip(c, bp, lx, ly, d[0], d[1], 8f)
        }
    }

    private fun strip(c: Canvas, bp: Paint, lx: Float, ly: Float, dx: Int, dy: Int, w: Float) {
        val ts = tileSize
        when {
            dx < 0 -> c.drawRect(lx, ly, lx + w, ly + ts, bp)
            dx > 0 -> c.drawRect(lx + ts - w, ly, lx + ts, ly + ts, bp)
            dy < 0 -> c.drawRect(lx, ly, lx + ts, ly + w, bp)
            else -> c.drawRect(lx, ly + ts - w, lx + ts, ly + ts, bp)
        }
    }

    /** Mistura suave entre tipos de chão vizinhos + sombra de árvores da floresta. */
    private fun bakeEdges(c: Canvas, bp: Paint, tx: Int, ty: Int, lx: Float, ly: Float, g: Int) {
        val dirs = arrayOf(intArrayOf(-1, 0), intArrayOf(1, 0), intArrayOf(0, -1), intArrayOf(0, 1))
        val soft = g == 0 || g == 1 || g == 3 || g == 4
        for (d in dirs) {
            val nx = tx + d[0]
            val ny = ty + d[1]
            if (!inB(nx, ny)) continue
            val nt = tiles[ny][nx]
            if (nt == 1 && (g == 0 || g == 4)) {
                bp.color = 0x30000000
                strip(c, bp, lx, ly, d[0], d[1], 18f)
                bp.color = 0x28000000
                strip(c, bp, lx, ly, d[0], d[1], 9f)
                continue
            }
            if (!soft || nt == 2 || nt == 8) continue
            val ng = ground[ny][nx]
            if (ng == g || ng == 2 || ng == 5) continue
            val nc = groundColor(ng, nx, ny) and 0x00FFFFFF
            bp.color = nc or 0x28000000
            strip(c, bp, lx, ly, d[0], d[1], 14f)
            bp.color = nc or 0x40000000
            strip(c, bp, lx, ly, d[0], d[1], 8f)
            bp.color = nc or 0x60000000
            strip(c, bp, lx, ly, d[0], d[1], 3f)
        }
    }

    // ───────────────────────── OBJETOS (ordenados por linha) ─────────────────────────

    fun drawRow(canvas: Canvas, paint: Paint, ty: Int, camX: Float, camY: Float, viewW: Float) {
        if (ty < 0 || ty >= height) return
        val ts = tileSize
        val sx = (camX / ts).toInt().coerceAtLeast(0)
        val ex = ((camX + viewW) / ts).toInt().coerceAtMost(width - 1)
        for (tx in sx..ex) {
            val t = tiles[ty][tx]
            if (t == 1) {
                if (treeEdge[ty][tx]) {
                    val v = hash(tx, ty, 12)
                    val s = 0.95f + (v % 30) / 100f
                    val off = ((v shr 5) % 20 - 10).toFloat()
                    drawTree(canvas, paint, tx * ts - camX + ts / 2f + off, (ty + 1) * ts - camY - 4f, s, v)
                }
            } else if (t == 6) {
                drawProp(canvas, paint, tx, ty, tx * ts - camX, ty * ts - camY)
            }
        }
        val list = rowObjs[ty]
        for (i in 0 until list.size) {
            val o = list[i]
            val l = o.tx * ts - camX - ts * 1.5f
            val r = (o.tx + o.w) * ts - camX + ts * 1.5f
            if (r < 0f || l > viewW) continue
            when (o.kind) {
                0 -> drawTree(canvas, paint, (o.tx + 0.5f) * ts - camX, (o.ty + 1) * ts - camY - 4f, 0.9f + (o.v % 25) / 100f, o.v)
                1 -> drawRock(canvas, paint, o, camX, camY)
                3 -> drawWell(canvas, paint, o, camX, camY)
                4 -> drawStall(canvas, paint, o, camX, camY)
                5 -> drawLamp(canvas, paint, o, camX, camY)
                6 -> drawPillar(canvas, paint, o, camX, camY)
                else -> drawHouse(canvas, paint, o, camX, camY)
            }
        }
    }

    private fun drawTree(c: Canvas, p: Paint, cx: Float, base: Float, s: Float, v: Int) {
        p.style = Paint.Style.FILL
        p.color = 0x40000000
        c.drawOval(cx - 26f * s, base - 9f * s, cx + 30f * s, base + 9f * s, p)
        val sway = sin(VoxelArt.time * 1.6f + (v % 97)) * 1.6f * s
        p.color = col(0xFF5B3A1E)
        c.drawRect(cx - 6f * s, base - 30f * s, cx + 6f * s, base, p)
        p.color = col(0xFF7A5230)
        c.drawRect(cx - 6f * s, base - 30f * s, cx - 1f * s, base, p)
        p.color = col(0xFF3D2612)
        c.drawRect(cx + 3f * s, base - 30f * s, cx + 6f * s, base, p)
        if (v % 3 == 0) {
            // pinheiro em camadas
            val tones = intArrayOf(col(0xFF1B5230), col(0xFF256A3C), col(0xFF328048))
            val ws = floatArrayOf(52f, 40f, 27f)
            for (i in 0..2) {
                val bot = base - 22f * s - i * 17f * s
                val w = ws[i] * s / 2f
                val ox = sway * (i * 0.6f)
                p.color = tones[i]
                c.drawRoundRect(cx - w + ox, bot - 22f * s, cx + w + ox, bot, 4f, 4f, p)
                p.color = 0x30FFFFFF
                c.drawRect(cx - w + ox + 2f, bot - 22f * s, cx + w + ox - 2f, bot - 18f * s, p)
                p.color = 0x30000000
                c.drawRect(cx + w * 0.45f + ox, bot - 20f * s, cx + w + ox, bot, p)
            }
            p.color = col(0xFF3C9A55)
            c.drawRect(cx - 5f * s + sway * 1.6f, base - 96f * s, cx + 5f * s + sway * 1.6f, base - 82f * s, p)
        } else {
            val autumn = v % 11 == 0
            val tones = if (autumn) intArrayOf(col(0xFFB5651D), col(0xFFD9822B), col(0xFFEDA23F))
            else if (v % 2 == 0) intArrayOf(col(0xFF2C7A38), col(0xFF38913F), col(0xFF4DAA4B))
            else intArrayOf(col(0xFF3B7A2E), col(0xFF4A9238), col(0xFF63AB46))
            val boxes = arrayOf(
                floatArrayOf(-31f, -66f, 31f, -24f),
                floatArrayOf(-23f, -88f, 23f, -56f),
                floatArrayOf(-15f, -104f, 15f, -80f)
            )
            for (i in 0..2) {
                val b = boxes[i]
                val ox = sway * (i + 1) * 0.5f
                p.color = tones[0]
                c.drawRoundRect(cx + b[0] * s + ox, base + b[1] * s, cx + b[2] * s + ox, base + b[3] * s, 9f * s, 9f * s, p)
                p.color = tones[1]
                c.drawRoundRect(cx + b[0] * s + ox, base + b[1] * s, cx + b[2] * s * 0.8f + ox, base + b[3] * s * 0.85f + b[1] * s * 0.15f, 9f * s, 9f * s, p)
                p.color = tones[2]
                c.drawRect(cx + b[0] * s * 0.7f + ox, base + b[1] * s + 4f, cx + b[0] * s * 0.7f + 12f * s + ox, base + b[1] * s + 9f, p)
            }
        }
    }

    private fun drawRock(c: Canvas, p: Paint, o: Obj, camX: Float, camY: Float) {
        val ts = tileSize
        val cx = (o.tx + 0.5f) * ts - camX
        val base = (o.ty + 1) * ts - camY - 6f
        p.style = Paint.Style.FILL
        p.color = 0x44000000
        c.drawOval(cx - 28f, base - 8f, cx + 28f, base + 8f, p)
        p.color = col(0xFF7E7E86)
        c.drawRoundRect(cx - 24f, base - 24f, cx + 22f, base, 9f, 9f, p)
        p.color = col(0xFF9A9AA3)
        c.drawRoundRect(cx - 12f, base - 38f, cx + 14f, base - 12f, 8f, 8f, p)
        p.color = 0x30FFFFFF
        c.drawRect(cx - 9f, base - 36f, cx + 9f, base - 31f, p)
        p.color = 0x30000000
        c.drawRect(cx + 10f, base - 30f, cx + 22f, base, p)
        if (o.v % 3 == 0) {
            p.color = 0xAA3F7D3A.toInt()
            c.drawRect(cx - 22f, base - 26f, cx - 6f, base - 20f, p)
        }
    }

    private fun drawPillar(c: Canvas, p: Paint, o: Obj, camX: Float, camY: Float) {
        val ts = tileSize
        val cx = (o.tx + 0.5f) * ts - camX
        val base = (o.ty + 1) * ts - camY - 4f
        val tall = if (o.v % 2 == 0) 112f else 70f
        p.style = Paint.Style.FILL
        p.color = 0x44000000
        c.drawOval(cx - 26f, base - 8f, cx + 30f, base + 8f, p)
        p.color = col(0xFF8D877C)
        c.drawRect(cx - 22f, base - 14f, cx + 22f, base, p)
        p.color = col(0xFFB8B2A6)
        c.drawRect(cx - 15f, base - tall, cx + 15f, base - 14f, p)
        p.color = 0x30FFFFFF
        c.drawRect(cx - 15f, base - tall, cx - 8f, base - 14f, p)
        p.color = 0x35000000
        c.drawRect(cx + 7f, base - tall, cx + 15f, base - 14f, p)
        if (tall > 100f) {
            p.color = col(0xFF9E988C)
            c.drawRect(cx - 20f, base - tall - 10f, cx + 20f, base - tall, p)
        } else {
            p.color = col(0xFFB8B2A6)
            c.drawRect(cx - 15f, base - tall - 6f, cx - 2f, base - tall, p)
        }
        p.color = 0x663F7D3A
        c.drawRect(cx - 15f, base - 40f, cx - 4f, base - 24f, p)
    }

    private fun drawWell(c: Canvas, p: Paint, o: Obj, camX: Float, camY: Float) {
        val ts = tileSize
        val cx = (o.tx + 0.5f) * ts - camX
        val base = (o.ty + 1) * ts - camY - 6f
        p.style = Paint.Style.FILL
        p.color = 0x44000000
        c.drawOval(cx - 36f, base - 10f, cx + 40f, base + 10f, p)
        p.color = col(0xFF8A8580)
        c.drawRect(cx - 27f, base - 26f, cx + 27f, base, p)
        p.color = col(0xFF6F6A66)
        for (k in 0..2) c.drawRect(cx - 27f + k * 18f, base - 26f, cx - 25.5f + k * 18f, base, p)
        c.drawRect(cx - 27f, base - 13f, cx + 27f, base - 11.5f, p)
        p.color = col(0xFFA5A09A)
        c.drawRect(cx - 29f, base - 33f, cx + 29f, base - 24f, p)
        p.color = col(0xFF1E5F86)
        c.drawRect(cx - 21f, base - 31f, cx + 21f, base - 26f, p)
        p.color = col(0xFF5B3A1E)
        c.drawRect(cx - 25f, base - 66f, cx - 19f, base - 28f, p)
        c.drawRect(cx + 19f, base - 66f, cx + 25f, base - 28f, p)
        p.color = col(0xFF8E3B2A)
        c.drawRect(cx - 34f, base - 78f, cx + 34f, base - 62f, p)
        p.color = col(0xFFB04E39)
        c.drawRect(cx - 22f, base - 88f, cx + 22f, base - 78f, p)
        p.color = 0x30FFFFFF
        c.drawRect(cx - 34f, base - 78f, cx + 34f, base - 75f, p)
        p.color = col(0xFFD7C9A0)
        c.drawRect(cx - 1f, base - 62f, cx + 1f, base - 40f, p)
        p.color = col(0xFF6D4524)
        c.drawRect(cx - 5f, base - 42f, cx + 5f, base - 34f, p)
    }

    private fun drawStall(c: Canvas, p: Paint, o: Obj, camX: Float, camY: Float) {
        val ts = tileSize
        val left = o.tx * ts - camX
        val right = (o.tx + o.w) * ts - camX
        val base = (o.ty + 1) * ts - camY - 4f
        p.style = Paint.Style.FILL
        p.color = 0x40000000
        c.drawRect(left + 6f, base - 6f, right + 8f, base + 8f, p)
        p.color = col(0xFF5B3A1E)
        c.drawRect(left + 4f, base - 88f, left + 10f, base, p)
        c.drawRect(right - 10f, base - 88f, right - 4f, base, p)
        p.color = col(0xFF8A5A2B)
        c.drawRect(left + 6f, base - 30f, right - 6f, base, p)
        p.color = col(0xFFA8743C)
        c.drawRect(left + 6f, base - 34f, right - 6f, base - 28f, p)
        // mercadorias
        val gc = intArrayOf(col(0xFFD6453A), col(0xFF6FBF4A), col(0xFFF2C14E), col(0xFFE59B3C))
        var x = left + 12f
        var k = 0
        while (x < right - 18f) {
            p.color = gc[(o.v + k) % 4]
            c.drawRect(x, base - 46f, x + 12f, base - 34f, p)
            p.color = 0x30FFFFFF
            c.drawRect(x, base - 46f, x + 12f, base - 43f, p)
            x += 16f
            k++
        }
        // toldo listrado
        var sx = left
        var i = 0
        while (sx < right) {
            p.color = if (i % 2 == 0) col(0xFFC63D32) else col(0xFFF1E9D8)
            c.drawRect(sx, base - 100f, minOf(sx + 16f, right), base - 76f, p)
            sx += 16f
            i++
        }
        p.color = 0x30000000
        c.drawRect(left, base - 80f, right, base - 76f, p)
    }

    private fun drawLamp(c: Canvas, p: Paint, o: Obj, camX: Float, camY: Float) {
        val ts = tileSize
        val cx = (o.tx + 0.5f) * ts - camX
        val base = (o.ty + 1) * ts - camY - 8f
        p.style = Paint.Style.FILL
        p.color = 0x40000000
        c.drawOval(cx - 12f, base - 4f, cx + 14f, base + 5f, p)
        p.color = col(0xFF2E2A26)
        c.drawRect(cx - 3f, base - 70f, cx + 3f, base, p)
        c.drawRect(cx - 7f, base - 6f, cx + 7f, base, p)
        val fl = 1f + 0.05f * sin(VoxelArt.time * 7f + o.tx)
        p.color = 0x14FFD27A
        c.drawCircle(cx, base - 76f, 52f * fl, p)
        p.color = 0x22FFD27A
        c.drawCircle(cx, base - 76f, 30f * fl, p)
        p.color = col(0xFF2E2A26)
        c.drawRect(cx - 8f, base - 88f, cx + 8f, base - 68f, p)
        p.color = col(0xFFFFD27A)
        c.drawRect(cx - 5f, base - 85f, cx + 5f, base - 71f, p)
        p.color = col(0xFFFFF1C2)
        c.drawRect(cx - 2f, base - 82f, cx + 2f, base - 75f, p)
    }

    private fun drawHouse(c: Canvas, p: Paint, o: Obj, camX: Float, camY: Float) {
        val ts = tileSize
        val left = o.tx * ts - camX
        val right = (o.tx + o.w) * ts - camX
        val fy = (o.ty + o.h) * ts - camY - 2f
        val w = right - left
        val wallH = ts * 1.25f
        val roofH = o.h * ts * 0.62f + 14f
        val style = o.v % 3
        val wallC = when {
            o.kind == 9 -> col(0xFF9C6B3A)
            o.kind == 10 -> col(0xFFBDB5A6)
            style == 0 -> col(0xFFE8DCC0)
            style == 1 -> col(0xFFD9C7A0)
            else -> col(0xFFC9C0AE)
        }
        val roofC = when {
            o.kind == 9 -> col(0xFF8E3B2A)
            o.kind == 8 -> col(0xFF7A3B26)
            o.kind == 10 -> col(0xFF4D5560)
            style == 0 -> col(0xFFA8452F)
            style == 1 -> col(0xFF4F6D8C)
            else -> col(0xFFB8963F)
        }
        p.style = Paint.Style.FILL
        // sombra no chão
        p.color = 0x33000000
        c.drawRect(left + 10f, fy - 4f, right + 24f, fy + 14f, p)
        // parede
        p.color = wallC
        c.drawRect(left, fy - wallH, right, fy, p)
        if (o.kind == 9) {
            p.color = col(0xFF845A30)
            var x = left + 10f
            while (x < right) { c.drawRect(x, fy - wallH, x + 2f, fy, p); x += 14f }
        }
        // base de pedra
        p.color = col(0xFF77706A)
        c.drawRect(left, fy - 14f, right, fy, p)
        p.color = 0x40000000
        var jx = left + 16f
        while (jx < right) { c.drawRect(jx, fy - 14f, jx + 1.5f, fy, p); jx += 16f }
        // vigas
        p.color = col(0xFF5A3A22)
        c.drawRect(left, fy - wallH, left + 7f, fy, p)
        c.drawRect(right - 7f, fy - wallH, right, fy, p)
        c.drawRect(left, fy - wallH, right, fy - wallH + 7f, p)
        c.drawRect(left, fy - 16f, right, fy - 12f, p)
        p.color = 0x40000000
        c.drawRect(left + 7f, fy - wallH + 7f, right - 7f, fy - wallH + 20f, p)
        // porta
        val dx = left + w / 2f
        if (o.kind == 9) {
            p.color = col(0xFF3B2412)
            c.drawRect(dx - 30f, fy - 62f, dx + 30f, fy, p)
            p.color = col(0xFF6B4423)
            c.drawRect(dx - 27f, fy - 59f, dx - 1.5f, fy, p)
            c.drawRect(dx + 1.5f, fy - 59f, dx + 27f, fy, p)
            p.color = col(0xFF3B2412)
            c.drawRect(dx - 27f, fy - 34f, dx + 27f, fy - 31f, p)
        } else {
            p.color = col(0xFF3B2412)
            c.drawRect(dx - 17f, fy - 54f, dx + 17f, fy, p)
            p.color = col(0xFF6B4423)
            c.drawRect(dx - 14f, fy - 51f, dx + 14f, fy, p)
            p.color = col(0xFF55351B)
            c.drawRect(dx - 1f, fy - 51f, dx + 1f, fy, p)
            p.color = col(0xFFE0B85C)
            c.drawRect(dx + 6f, fy - 26f, dx + 10f, fy - 22f, p)
            p.color = col(0xFFA39D95)
            c.drawRect(dx - 22f, fy - 5f, dx + 22f, fy, p)
        }
        // janelas
        if (o.kind != 9) {
            val fr = if (w > ts * 5f) floatArrayOf(0.14f, 0.3f, 0.7f, 0.86f) else floatArrayOf(0.2f, 0.8f)
            for (f in fr) {
                val wx = left + w * f
                val wy = fy - wallH * 0.62f
                p.color = col(0xFF3B2412)
                c.drawRect(wx - 15f, wy - 14f, wx + 15f, wy + 14f, p)
                p.color = col(0xFFFFD27A)
                c.drawRect(wx - 12f, wy - 11f, wx + 12f, wy + 11f, p)
                p.color = 0x30FFFFFF
                c.drawRect(wx - 12f, wy - 11f, wx + 12f, wy - 6f, p)
                p.color = col(0xFF3B2412)
                c.drawRect(wx - 1.2f, wy - 11f, wx + 1.2f, wy + 11f, p)
                c.drawRect(wx - 12f, wy - 1.2f, wx + 12f, wy + 1.2f, p)
                c.drawRect(wx - 18f, wy + 14f, wx + 18f, wy + 18f, p)
            }
        }
        // telhado
        val eave = fy - wallH + 6f
        val top = eave - roofH
        p.color = roofC
        c.drawRect(left - 12f, top, right + 12f, eave, p)
        val rows = (roofH / 14f).toInt().coerceAtLeast(2)
        val rh = roofH / rows
        for (r in 0 until rows) {
            if (r % 2 == 1) {
                p.color = 0x18000000
                c.drawRect(left - 12f, top + r * rh, right + 12f, top + (r + 1) * rh, p)
            }
            p.color = 0x30000000
            c.drawRect(left - 12f, top + (r + 1) * rh - 2f, right + 12f, top + (r + 1) * rh, p)
        }
        p.color = VoxelArt.shade(roofC, 1.3f)
        c.drawRect(left - 12f, top, right + 12f, top + 8f, p)
        p.color = 0x28000000
        c.drawRect(right + 4f, top, right + 12f, eave, p)
        // beiral
        p.color = col(0xFF4A2F18)
        c.drawRect(left - 12f, eave - 2f, right + 12f, eave + 5f, p)
        // chaminé + fumaça
        val chx = right - ts * 0.75f
        p.color = col(0xFF6E625A)
        c.drawRect(chx, top - 20f, chx + 20f, top + roofH * 0.45f, p)
        p.color = col(0xFF8A7D73)
        c.drawRect(chx - 3f, top - 26f, chx + 23f, top - 18f, p)
        if (o.kind == 10) {
            val g = 0.7f + 0.3f * sin(VoxelArt.time * 9f)
            p.color = VoxelArt.alpha(col(0xFFFF8A2B), 0.35f * g)
            c.drawCircle(chx + 10f, top - 30f, 22f, p)
        }
        for (i in 0..2) {
            val tt = (VoxelArt.time * 0.5f + i * 0.33f) % 1f
            p.color = VoxelArt.alpha(col(0xFFDDDDDD), 0.45f * (1f - tt))
            c.drawCircle(chx + 10f + tt * 14f + sin(tt * 6f + i) * 3f, top - 30f - tt * 46f, 6f + tt * 8f, p)
        }
        // detalhes por tipo
        if (o.kind == 8) {
            // placa pendurada
            p.color = col(0xFF3B2412)
            c.drawRect(right - 10f, fy - wallH + 22f, right + 22f, fy - wallH + 26f, p)
            p.color = col(0xFF8A5A2B)
            c.drawRect(right + 4f, fy - wallH + 26f, right + 24f, fy - wallH + 48f, p)
            p.color = col(0xFFF2C14E)
            c.drawRect(right + 9f, fy - wallH + 32f, right + 19f, fy - wallH + 43f, p)
        }
        if (o.kind == 10) {
            // bigorna
            p.color = col(0xFF2B2F36)
            c.drawRect(left - 26f, fy - 18f, left - 6f, fy - 10f, p)
            c.drawRect(left - 21f, fy - 10f, left - 11f, fy, p)
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
