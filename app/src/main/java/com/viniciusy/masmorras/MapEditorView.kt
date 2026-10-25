package com.viniciusy.masmorras

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/** Editor de mapas: toque para pintar, dois dedos para mover/dar zoom. */
class MapEditorView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    companion object {
        const val L_FLOOR = 0
        const val L_WALL = 1
        const val L_PROP = 2
        const val L_ENT = 3
        const val L_HOUSE = 4
        const val L_TERRAIN = 5
        const val L_BRIDGE = 6
        const val L_BUILD = 7

        const val T_BRUSH = 0
        const val T_RECT = 1
        const val T_FILL = 2
        const val T_ERASE = 3
        const val T_PAN = 4
        const val T_LINE = 5
        const val T_PICK = 6
        const val T_COPY = 7

        private const val MODE_NONE = 0
        private const val MODE_DRAW = 1
        private const val MODE_PAN = 2

        private val srcTop = Rect(0, 0, MapTex.TS, MapTex.TS)
        private val srcFront = Rect(0, MapTex.TS, MapTex.TS, MapTex.TS + MapTex.WALL_H)
        private val srcProp = Rect(0, 0, PropArt.W, PropArt.H)

        /** Itens da aba Relevo: (id, nome). 1..6 = nível, 10/11 = subir/baixar, 12 = aplanar, 20..23 = rampas, 30 = colina. */
        val terrainItems: List<Pair<Int, String>> = listOf(
            10 to "Subir +1", 11 to "Baixar −1", 12 to "Aplanar", 30 to "Colina",
            20 to "Rampa ↑ norte", 21 to "Rampa → leste", 22 to "Rampa ↓ sul", 23 to "Rampa ← oeste",
            1 to "Nível 1", 2 to "Nível 2", 3 to "Nível 3", 4 to "Nível 4", 5 to "Nível 5", 6 to "Nível 6"
        )

        private fun drawTerrainIcon(c: Canvas, v: Int) {
            val p = Paint(Paint.ANTI_ALIAS_FLAG)
            p.isFilterBitmap = false
            c.drawBitmap(MapTex.tile(MT.floorDef(MT.F_GRASS), 0), 0f, 0f, p)
            val lv = when {
                v in 1..6 -> v
                v == 10 -> 2
                v == 11 -> 1
                v == 30 -> 3
                v in 20..23 -> 1
                else -> 0
            }
            if (lv > 0) {
                p.style = Paint.Style.FILL
                p.color = (lv * 0x1C shl 24) or 0xFFFFFF
                c.drawRect(0f, 0f, 64f, 64f, p)
                p.color = 0x66000000
                c.drawRect(0f, 64f - lv * 4f, 64f, 64f, p)
            }
            if (v == 30) {
                p.style = Paint.Style.FILL
                for (k in 0..2) {
                    p.color = ((0x38 + k * 0x20) shl 24) or 0xFFFFFF

                    c.drawRect(8f + k * 9f, 8f + k * 9f, 56f - k * 9f, 56f - k * 9f, p)
                }
                return
            }
            val tp = Paint(Paint.ANTI_ALIAS_FLAG)
            tp.textAlign = Paint.Align.CENTER
            tp.isFakeBoldText = true
            tp.color = 0xFFFFFFFF.toInt()
            tp.setShadowLayer(3f, 0f, 0f, 0xFF000000.toInt())
            tp.textSize = 34f
            val label = when (v) {
                10 -> "+1"
                11 -> "-1"
                12 -> "0"
                20 -> "↑"
                21 -> "→"
                22 -> "↓"
                23 -> "←"
                else -> v.toString()
            }
            c.drawText(label, 32f, 45f, tp)
        }

        /** valor de entidade: 0 = início do jogador, 1 = inimigo, 2 = inimigo forte, 3 = chefe */
        val entNames = arrayOf("Início", "Inimigo", "Inimigo forte", "Chefe")

        /** Parede vista de cima, com andares: o topo sobe um pouco por andar e a face aparece embaixo. */
        private fun drawWallFlat(c: Canvas, p: Paint, def: MT.Def?, capDef: MT.Def?, hgt: Int, geom: Int, l: Float, t: Float, r: Float, b: Float) {
            if (def == null) return
            val mask = geom and 15
            val lo = geom shr 4
            val spr = MapTex.wall(def, mask, hgt, lo, capDef, 0)
            val e = (b - t) * 0.28f
            val up = (hgt - 1) * e
            c.drawBitmap(spr, srcTop, RectF(l, t - up, r, b - up), p)
            val n = hgt - lo
            if (n > 0 && (mask and MapTex.M_S) == 0) {
                c.drawBitmap(spr, Rect(0, MapTex.TS, MapTex.TS, MapTex.TS + n * MapTex.WALL_H), RectF(l, b - n * e, r, b), p)
            }
        }

        /** Desenha uma casa (modelo) no canvas, célula a célula. */
        fun drawTemplate(c: Canvas, p: Paint, tp: Template, l: Float, t: Float, cell: Float, time: Float) {
            val rf = RectF()
            for (y in 0 until tp.h) for (x in 0 until tp.w) {
                val f = tp.floor[y * tp.w + x]
                if (f < 0) continue
                rf.set(l + x * cell, t + y * cell, l + (x + 1) * cell + 1f, t + (y + 1) * cell + 1f)
                c.drawBitmap(MapTex.tile(MT.floorDef(f), 0, time), srcTop, rf, p)
            }
            for (y in 0 until tp.h) for (x in 0 until tp.w) {
                val o = tp.obj[y * tp.w + x]
                if (o <= 0) continue
                val cl = l + x * cell
                val ct = t + y * cell
                if (MT.isWall(o)) {
                    val wh = { xx: Int, yy: Int ->
                        if (xx in 0 until tp.w && yy in 0 until tp.h && MT.isWall(tp.obj[yy * tp.w + xx])) tp.hgt[yy * tp.w + xx].coerceIn(1, 4) else 0
                    }
                    val geom = wallGeomOf(tp.w, tp.h, x, y, wh)
                    val cp = tp.cap[y * tp.w + x]
                    drawWallFlat(c, p, MT.wallDef(o), if (cp != 0) MT.wallDef(cp) else null, wh(x, y).coerceAtLeast(1), geom, cl, ct, cl + cell + 1f, ct + cell + 1f)
                } else drawPropFlat(c, p, o, cl, ct + cell, cell, time)
            }
        }

        private fun drawPropFlat(c: Canvas, p: Paint, id: Int, l: Float, b: Float, size: Float, time: Float = 0f) {
            val h = size * 1.1f
            val w = h * PropArt.W / PropArt.H
            val left = l + (size - w) / 2f
            c.drawBitmap(PropArt.get(id, time), srcProp, RectF(left, b - h, left + w, b), p)
        }

        private fun drawEntMarker(c: Canvas, p: Paint, tp: Paint, v: Int, cx: Float, cy: Float, rad: Float) {
            val color = when (v) {
                0 -> 0xFF43A047.toInt()
                1 -> 0xFFE53935.toInt()
                2 -> 0xFFFB8C00.toInt()
                else -> 0xFF8E24AA.toInt()
            }
            val rr = if (v == 3) rad * 1.15f else rad
            p.style = Paint.Style.FILL
            p.color = 0xFF000000.toInt()
            c.drawCircle(cx, cy, rr + rad * 0.12f, p)
            p.color = color
            c.drawCircle(cx, cy, rr, p)
            tp.textSize = rr * 1.2f
            tp.color = 0xFFFFFFFF.toInt()
            val label = when (v) { 0 -> "J"; 1 -> "1"; 2 -> "2"; else -> "C" }
            c.drawText(label, cx, cy + tp.textSize * 0.35f, tp)
        }

        /** Miniatura 64x64 de um item da paleta. */
        fun preview(layer: Int, v: Int): Bitmap {
            val bmp = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888)
            val c = Canvas(bmp)
            val p = Paint()
            p.isFilterBitmap = false
            p.isAntiAlias = false
            when (layer) {
                L_FLOOR -> c.drawBitmap(MapTex.tile(MT.floorDef(v), 0), 0f, 0f, p)
                L_WALL -> drawWallFlat(c, p, MT.wallDef(v), null, 1, 0, 0f, 0f, 64f, 64f)
                L_PROP -> {
                    c.drawBitmap(MapTex.tile(MT.floorDef(MT.F_STONE), 0), 0f, 0f, p)
                    drawPropFlat(c, p, v, 0f, 64f, 64f)
                }
                L_BRIDGE -> {
                    c.drawBitmap(MapTex.tile(MT.floorDef(MT.F_WATER), 0), 0f, 0f, p)
                    c.drawBitmap(MapTex.bridge(v, false), 0f, 0f, p)
                }
                L_TERRAIN -> drawTerrainIcon(c, v)

                L_BUILD -> c.drawColor(0xFF2A2F36.toInt())
                L_HOUSE -> {
                    val tpl = Houses.template(v)
                    c.drawColor(0xFF2A3A28.toInt())
                    val cs = 60f / max(tpl.w, tpl.h)
                    drawTemplate(c, p, tpl, 2f + (60f - tpl.w * cs) / 2f, 2f + (60f - tpl.h * cs) / 2f, cs, 0f)
                }
                else -> {
                    val tp = Paint(Paint.ANTI_ALIAS_FLAG)
                    tp.textAlign = Paint.Align.CENTER
                    tp.isFakeBoldText = true
                    p.isAntiAlias = true
                    c.drawColor(0xFF2A2F36.toInt())
                    drawEntMarker(c, p, tp, v, 32f, 32f, 22f)
                }
            }
            return bmp
        }
    }

    var map: MapData? = null
        private set
    var layer = L_FLOOR
    var tool = T_BRUSH
    var showGrid = true
        set(v) { field = v; invalidate() }
    val selected = intArrayOf(MT.F_STONE, MT.W_STONE, MT.P_CRATE, 0, 0, 10, MT.B_WOOD, 0)
    /** Altura (1..4) da colina do pincel de relevo. */
    var hillPeak = 3
    /** Opções do construtor de cômodos (aba Obra). */
    val buildOpts = BuildOpts()
    var dirty = false
    var onHistoryChanged: (() -> Unit)? = null
    var onMessage: ((String) -> Unit)? = null
    /** Avisa a tela quando o editor mudou aba/ferramenta/seleção sozinho (conta-gotas, copiar). */
    var onPick: (() -> Unit)? = null
    /** Andares da parede que será pintada (1..4) e material dos andares de cima (0 = igual). */
    var wallHeight = 1
    var wallCap = 0

    private var cell = 48f
    private var offX = 0f
    private var offY = 0f
    private var needFit = true

    private val undoStack = ArrayList<MapSnap>()
    private val redoStack = ArrayList<MapSnap>()
    val canUndo: Boolean get() = undoStack.isNotEmpty()
    val canRedo: Boolean get() = redoStack.isNotEmpty()

    private val paint = Paint()
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }
    private val bmpPaint = Paint().apply { isFilterBitmap = false; isAntiAlias = false }
    private val rf = RectF()
    private val ghostPaint = Paint().apply { isFilterBitmap = false; isAntiAlias = false; alpha = 190 }

    private var mode = MODE_NONE
    /** Ferramenta efetiva do traço atual (na aba Obra, pincel/linha/balde viram retângulo). */
    private var curTool = T_BRUSH
    private var strokeSeen = BooleanArray(0)
    private var before: MapSnap? = null
    private var lastCx = -1
    private var lastCy = -1
    private var rectSx = 0
    private var rectSy = 0
    private var rectEx = 0
    private var rectEy = 0
    private var panLastX = 0f
    private var panLastY = 0f
    private var lastMx = 0f
    private var lastMy = 0f
    private var lastDist = 1f

    fun setMap(m: MapData) {
        map = m
        undoStack.clear()
        redoStack.clear()
        dirty = false
        needFit = true
        if (width > 0) fit()
        onHistoryChanged?.invoke()
        invalidate()
    }

    fun fit() {
        val m = map ?: return
        if (width == 0 || height == 0) return
        cell = min(width.toFloat() / m.w, height.toFloat() / m.h).coerceIn(20f, 96f)
        offX = (width - m.w * cell) / 2f
        offY = (height - m.h * cell) / 2f
        needFit = false
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (needFit) fit()
    }

    // ───────────────────────── histórico ─────────────────────────

    private fun pushUndo(s: MapSnap) {
        undoStack.add(s)
        if (undoStack.size > 60) undoStack.removeAt(0)
        redoStack.clear()
        onHistoryChanged?.invoke()
    }

    fun undo() {
        val m = map ?: return
        if (undoStack.isEmpty()) return
        redoStack.add(m.snapshot())
        m.restore(undoStack.removeAt(undoStack.size - 1))
        dirty = true
        onHistoryChanged?.invoke()
        invalidate()
    }

    fun redo() {
        val m = map ?: return
        if (redoStack.isEmpty()) return
        undoStack.add(m.snapshot())
        m.restore(redoStack.removeAt(redoStack.size - 1))
        dirty = true
        onHistoryChanged?.invoke()
        invalidate()
    }

    // ───────────────────────── edição ─────────────────────────

    private fun applyCell(m: MapData, x: Int, y: Int, erase: Boolean) {
        if (!m.inB(x, y)) return
        val i = y * m.w + x
        when (layer) {
            L_FLOOR -> m.floor[i] = if (erase) MT.F_VOID else selected[L_FLOOR]
            L_WALL -> {
                m.obj[i] = if (erase) 0 else selected[L_WALL]
                m.hgt[i] = if (erase) 0 else wallHeight
                m.cap[i] = if (erase || wallHeight <= 1) 0 else wallCap
            }
            L_PROP -> {
                m.obj[i] = if (erase) 0 else selected[L_PROP]
                m.hgt[i] = 0
                m.cap[i] = 0
            }
            L_TERRAIN -> applyTerrain(m, x, y, erase)
            L_BRIDGE -> m.bridge[i] = if (erase) 0 else selected[L_BRIDGE]
            else -> {
                if (erase) {
                    m.ents.removeAll { it.x == x && it.y == y }
                } else {
                    val v = selected[L_ENT]
                    if (v == 0) {
                        m.spawnX = x
                        m.spawnY = y
                    } else {
                        m.ents.removeAll { it.x == x && it.y == y }
                        if (m.ents.size < 200) m.ents.add(MapEnt(x, y, v - 1))
                    }
                }
            }
        }
    }

    private fun seenCell(m: MapData, i: Int): Boolean {
        if (strokeSeen.size != m.w * m.h) strokeSeen = BooleanArray(m.w * m.h)

        if (strokeSeen[i]) return true
        strokeSeen[i] = true
        return false
    }

    /** Aplica o item de relevo escolhido na célula (subir, baixar, nível, rampa, colina). */
    private fun applyTerrain(m: MapData, x: Int, y: Int, erase: Boolean) {
        val i = y * m.w + x
        if (erase) { m.elev[i] = 0; return }
        val sel = selected[L_TERRAIN]
        val cur = m.elev[i]
        when {
            sel in 1..6 -> m.elev[i] = MT.elevOf(sel, 0)
            sel == 10 -> if (!seenCell(m, i)) m.elev[i] = MT.elevOf(MT.level(cur) + 1, MT.ramp(cur))
            sel == 11 -> if (!seenCell(m, i)) m.elev[i] = MT.elevOf(MT.level(cur) - 1, MT.ramp(cur))
            sel == 12 -> m.elev[i] = 0
            sel in 20..23 -> {
                val dir = sel - 19
                val lv = MT.level(cur)
                m.elev[i] = MT.elevOf(lv, dir)
                // o lado alto da rampa precisa ter um nível a mais: sobe o vizinho se estiver mais baixo
                val nx = x + (if (dir == 2) 1 else if (dir == 4) -1 else 0)
                val ny = y + (if (dir == 3) 1 else if (dir == 1) -1 else 0)
                if (m.inB(nx, ny)) {
                    val n = m.elev[ny * m.w + nx]
                    if (MT.ramp(n) == 0 && MT.level(n) < lv + 1 && lv + 1 <= MT.MAX_LEVEL) m.elev[ny * m.w + nx] = MT.elevOf(lv + 1, 0)
                }
            }
            sel == 30 -> hill(m, x, y, hillPeak)
        }
    }


    /** Colina em degraus circulares, com uma rampa em cada direção (norte, sul, leste e oeste) para subir. */
    private fun hill(m: MapData, cx: Int, cy: Int, peak: Int) {
        for (dy in -peak..peak) for (dx in -peak..peak) {
            val x = cx + dx
            val y = cy + dy
            if (!m.inB(x, y)) continue
            val d = floor(sqrt((dx * dx + dy * dy).toFloat()) + 0.001f).toInt()
            if (d > peak) continue
            val lv = peak - d
            var ramp = 0
            if (lv < peak) {
                if (dy == 0 && dx != 0) ramp = if (dx > 0) 4 else 2
                else if (dx == 0 && dy != 0) ramp = if (dy > 0) 1 else 3
            }
            if (lv == 0 && ramp == 0) continue
            m.elev[y * m.w + x] = MT.elevOf(lv, ramp)
        }
    }

    /** Rampa comprida: preenche a área subindo um nível a cada célula, a partir da altura do chão na ponta baixa. */
    private fun fillRamp(m: MapData, x0: Int, y0: Int, x1: Int, y1: Int, dir: Int) {
        val vertical = dir == 1 || dir == 3
        val len = if (vertical) y1 - y0 + 1 else x1 - x0 + 1
        val across = if (vertical) x1 - x0 + 1 else y1 - y0 + 1
        for (a in 0 until across) {
            // ponta baixa: dir 1 (sobe p/ norte) = linha de baixo; 2 (leste) = coluna da esquerda; 3 = linha de cima; 4 = coluna da direita
            val lowX = when (dir) { 2 -> x0; 4 -> x1; else -> x0 + a }
            val lowY = when (dir) { 1 -> y1; 3 -> y0; else -> y0 + a }
            val base = m.levelAt(lowX, lowY)
            for (k in 0 until len) {
                val x = when (dir) { 2 -> x0 + k; 4 -> x1 - k; else -> x0 + a }
                val y = when (dir) { 1 -> y1 - k; 3 -> y0 + k; else -> y0 + a }

                if (!m.inB(x, y)) continue
                m.elev[y * m.w + x] = MT.elevOf(min(MT.MAX_LEVEL, base + k), dir)
            }
            // platô depois da ponta alta
            val endX = when (dir) { 2 -> x1 + 1; 4 -> x0 - 1; else -> x0 + a }
            val endY = when (dir) { 1 -> y0 - 1; 3 -> y1 + 1; else -> y0 + a }
            if (m.inB(endX, endY) && MT.ramp(m.elev[endY * m.w + endX]) == 0 && MT.level(m.elev[endY * m.w + endX]) < min(MT.MAX_LEVEL, base + len)) {
                m.elev[endY * m.w + endX] = MT.elevOf(min(MT.MAX_LEVEL, base + len), 0)
            }
        }
    }

    private fun line(m: MapData, x0: Int, y0: Int, x1: Int, y1: Int, erase: Boolean) {
        var x = x0
        var y = y0
        val dx = abs(x1 - x0)
        val dy = -abs(y1 - y0)
        val sx = if (x0 < x1) 1 else -1
        val sy = if (y0 < y1) 1 else -1
        var err = dx + dy
        var guard = 0
        while (guard++ < 4000) {
            applyCell(m, x, y, erase)
            if (x == x1 && y == y1) break
            val e2 = 2 * err
            if (e2 >= dy) { err += dy; x += sx }
            if (e2 <= dx) { err += dx; y += sy }
        }
    }

    private fun lineCells(x0: Int, y0: Int, x1: Int, y1: Int): List<Pair<Int, Int>> {
        val out = ArrayList<Pair<Int, Int>>()
        var x = x0
        var y = y0
        val dx = abs(x1 - x0)
        val dy = -abs(y1 - y0)
        val sx = if (x0 < x1) 1 else -1
        val sy = if (y0 < y1) 1 else -1
        var err = dx + dy
        var guard = 0
        while (guard++ < 4000) {
            out.add(x to y)
            if (x == x1 && y == y1) break
            val e2 = 2 * err
            if (e2 >= dy) { err += dy; x += sx }
            if (e2 <= dx) { err += dx; y += sy }
        }
        return out
    }

    private fun floodFill(m: MapData, sx: Int, sy: Int) {
        if (!m.inB(sx, sy)) return
        val arr = when (layer) {
            L_FLOOR -> m.floor
            L_TERRAIN -> m.elev
            L_BRIDGE -> m.bridge
            else -> m.obj
        }
        val repl: Int
        if (layer == L_TERRAIN) {
            val sel = selected[L_TERRAIN]
            repl = when {
                sel in 1..6 -> MT.elevOf(sel, 0)
                sel == 12 -> 0
                else -> { onMessage?.invoke("O balde só funciona com Nível ou Aplanar"); return }
            }
        } else repl = selected[layer]

        val target = arr[sy * m.w + sx]
        if (target == repl) return
        val seen = BooleanArray(m.w * m.h)
        val queue = IntArray(m.w * m.h)
        var head = 0
        var tail = 0
        queue[tail++] = sy * m.w + sx
        seen[sy * m.w + sx] = true
        while (head < tail) {
            val cur = queue[head++]
            arr[cur] = repl
            if (layer == L_WALL) { m.hgt[cur] = wallHeight; m.cap[cur] = if (wallHeight > 1) wallCap else 0 } else if (layer == L_PROP) { m.hgt[cur] = 0; m.cap[cur] = 0 }
            val cx = cur % m.w
            val cy = cur / m.w
            if (cx > 0) { val n = cur - 1; if (!seen[n] && arr[n] == target) { seen[n] = true; queue[tail++] = n } }
            if (cx < m.w - 1) { val n = cur + 1; if (!seen[n] && arr[n] == target) { seen[n] = true; queue[tail++] = n } }
            if (cy > 0) { val n = cur - m.w; if (!seen[n] && arr[n] == target) { seen[n] = true; queue[tail++] = n } }
            if (cy < m.h - 1) { val n = cur + m.w; if (!seen[n] && arr[n] == target) { seen[n] = true; queue[tail++] = n } }
        }
    }

    private fun cellAt(x: Float, y: Float): Pair<Int, Int> =
        Pair(floor((x - offX) / cell).toInt(), floor((y - offY) / cell).toInt())

    /** Aba em que o toque pinta células (as outras abas têm comportamento próprio). */
    private fun isCellLayer() = layer != L_ENT && layer != L_HOUSE && layer != L_BUILD

    private fun beginStroke(m: MapData, x: Float, y: Float) {
        before = m.snapshot()
        strokeSeen = BooleanArray(m.w * m.h)
        val (cx, cy) = cellAt(x, y)
        lastCx = cx
        lastCy = cy
        if (layer == L_HOUSE && curTool != T_COPY && curTool != T_PICK) {
            if (curTool == T_ERASE) onMessage?.invoke("Para apagar, use a borracha nas abas Chão, Paredes, Objetos, Relevo ou Pontes")
            invalidate()
            return
        }
        if (layer == L_BUILD && curTool == T_ERASE) {
            onMessage?.invoke("Para apagar, use a borracha nas abas Chão, Paredes ou Objetos")
            invalidate()
            return
        }
        when (curTool) {
            T_BRUSH, T_ERASE -> applyCell(m, cx, cy, curTool == T_ERASE)
            T_RECT, T_LINE, T_COPY -> {

                if (layer == L_ENT && curTool != T_COPY) onMessage?.invoke("Para entidades use o pincel")
                rectSx = cx; rectSy = cy; rectEx = cx; rectEy = cy
            }
            T_PICK -> pick(m, cx, cy)
            T_FILL -> {
                if (layer == L_ENT) onMessage?.invoke("Para entidades use o pincel")
                else floodFill(m, cx, cy)
            }
        }
        invalidate()
    }

    private fun moveStroke(m: MapData, x: Float, y: Float) {
        val (cx, cy) = cellAt(x, y)
        if (cx == lastCx && cy == lastCy) return
        if ((layer == L_HOUSE && curTool != T_COPY && curTool != T_PICK) || (layer == L_BUILD && curTool == T_ERASE)) {
            lastCx = cx
            lastCy = cy
            invalidate()
            return
        }
        when (curTool) {
            T_BRUSH, T_ERASE -> line(m, lastCx, lastCy, cx, cy, curTool == T_ERASE)
            T_RECT, T_LINE, T_COPY -> { rectEx = cx; rectEy = cy }
        }
        lastCx = cx
        lastCy = cy
        invalidate()
    }

    /** Carimba a casa escolhida com o centro na célula (cx, cy). */
    private fun stampHouse(m: MapData, cx: Int, cy: Int) {
        val tp = Houses.template(selected[L_HOUSE])
        val x0 = cx - tp.w / 2
        val y0 = cy - tp.h / 2
        for (y in 0 until tp.h) for (x in 0 until tp.w) {
            val mx = x0 + x
            val my = y0 + y
            if (!m.inB(mx, my)) continue
            val i = my * m.w + mx
            val f = tp.floor[y * tp.w + x]
            val o = tp.obj[y * tp.w + x]
            if (f >= 0) m.floor[i] = f
            if (o >= 0) { m.obj[i] = o; m.hgt[i] = tp.hgt[y * tp.w + x]; m.cap[i] = tp.cap[y * tp.w + x] }
            if (tp.elev[y * tp.w + x] >= 0) m.elev[i] = tp.elev[y * tp.w + x]
            if (tp.bridge[y * tp.w + x] >= 0) m.bridge[i] = tp.bridge[y * tp.w + x]
        }
        // apaga inimigos que ficaram dentro de paredes
        m.ents.removeAll { e -> m.inB(e.x, e.y) && MT.solidObj(m.obj[e.y * m.w + e.x]) }
    }

    /** Conta-gotas: pega o bloco tocado (e a altura da parede) e volta para o pincel. */
    private fun pick(m: MapData, cx: Int, cy: Int) {
        if (!m.inB(cx, cy)) return
        val i = cy * m.w + cx
        if (layer == L_TERRAIN) {
            val lv = MT.level(m.elev[i])
            selected[L_TERRAIN] = if (lv == 0) 12 else lv
            tool = T_BRUSH
            onPick?.invoke()
            return
        }
        if (layer == L_BRIDGE) {
            if (m.bridge[i] != 0) selected[L_BRIDGE] = m.bridge[i]
            tool = T_BRUSH
            onPick?.invoke()
            return
        }
        val o = m.obj[i]
        when {
            layer == L_FLOOR || o == 0 -> {
                layer = L_FLOOR
                selected[L_FLOOR] = m.floor[i]
            }
            MT.isWall(o) -> {
                layer = L_WALL
                selected[L_WALL] = o
                wallHeight = m.hgt[i].coerceIn(1, 4)
                wallCap = m.cap[i]
            }
            MT.isProp(o) -> {
                layer = L_PROP
                selected[L_PROP] = o
            }
            else -> {
                layer = L_FLOOR
                selected[L_FLOOR] = m.floor[i]
            }
        }
        tool = T_BRUSH
        onPick?.invoke()
    }

    /** Copia a área marcada para a lista de Casas ("Cópia"), pronta para carimbar em outro lugar. */
    private fun copyArea(m: MapData) {
        val x0 = max(0, min(rectSx, rectEx))
        val x1 = min(m.w - 1, max(rectSx, rectEx))
        val y0 = max(0, min(rectSy, rectEy))
        val y1 = min(m.h - 1, max(rectSy, rectEy))
        if (x1 < x0 || y1 < y0) return
        val tp = Template(x1 - x0 + 1, y1 - y0 + 1)
        for (yy in y0..y1) for (xx in x0..x1) {
            val si = yy * m.w + xx
            val ti = (yy - y0) * tp.w + (xx - x0)
            tp.floor[ti] = m.floor[si]
            tp.obj[ti] = m.obj[si]
            tp.hgt[ti] = m.hgt[si]
            tp.cap[ti] = m.cap[si]
            tp.elev[ti] = m.elev[si]
            tp.bridge[ti] = m.bridge[si]
        }
        Houses.clipboard = tp
        layer = L_HOUSE
        selected[L_HOUSE] = Houses.CLIP_ID
        tool = T_BRUSH
        onMessage?.invoke("Área copiada (${tp.w}×${tp.h}). Toque no mapa para carimbar")
        onPick?.invoke()
    }

    private fun endStroke(m: MapData) {
        if (layer == L_HOUSE && curTool != T_ERASE && curTool != T_COPY && curTool != T_PICK) stampHouse(m, lastCx, lastCy)
        if (curTool == T_LINE && isCellLayer()) {
            line(m, rectSx, rectSy, rectEx, rectEy, false)
        }
        if (curTool == T_COPY) copyArea(m)
        if (curTool == T_RECT && layer == L_BUILD) {
            onMessage?.invoke(RoomBuilder.build(m, rectSx, rectSy, rectEx, rectEy, buildOpts))
        } else if (curTool == T_RECT && isCellLayer()) {
            val x0 = max(0, min(rectSx, rectEx))
            val x1 = min(m.w - 1, max(rectSx, rectEx))
            val y0 = max(0, min(rectSy, rectEy))
            val y1 = min(m.h - 1, max(rectSy, rectEy))
            val sel = selected[L_TERRAIN]
            if (layer == L_TERRAIN && sel in 20..23) fillRamp(m, x0, y0, x1, y1, sel - 19)
            else for (yy in y0..y1) for (xx in x0..x1) applyCell(m, xx, yy, false)
        }
        val b = before
        before = null
        if (b != null && !m.sameAs(b)) {
            pushUndo(b)
            dirty = true
        }
        invalidate()
    }

    private fun cancelStroke(m: MapData) {
        val b = before
        before = null
        if (b != null) m.restore(b)
        invalidate()
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        val m = map ?: return false
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (tool == T_PAN) {
                    mode = MODE_PAN
                    panLastX = e.x
                    panLastY = e.y
                } else {
                    mode = MODE_DRAW
                    curTool = if (layer == L_BUILD && (tool == T_BRUSH || tool == T_LINE || tool == T_FILL)) T_RECT else tool
                    beginStroke(m, e.x, e.y)
                }
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                if (mode == MODE_DRAW) cancelStroke(m)
                mode = MODE_PAN
                if (e.pointerCount >= 2) {
                    lastMx = (e.getX(0) + e.getX(1)) / 2f
                    lastMy = (e.getY(0) + e.getY(1)) / 2f
                    lastDist = max(1f, hypot(e.getX(0) - e.getX(1), e.getY(0) - e.getY(1)))
                }
            }
            MotionEvent.ACTION_MOVE -> {
                if (mode == MODE_DRAW) {
                    moveStroke(m, e.x, e.y)
                } else if (mode == MODE_PAN) {
                    if (e.pointerCount >= 2) {
                        val mx = (e.getX(0) + e.getX(1)) / 2f
                        val my = (e.getY(0) + e.getY(1)) / 2f
                        val dist = max(1f, hypot(e.getX(0) - e.getX(1), e.getY(0) - e.getY(1)))
                        val wx = (lastMx - offX) / cell
                        val wy = (lastMy - offY) / cell
                        cell = (cell * dist / lastDist).coerceIn(14f, 160f)
                        offX = mx - wx * cell
                        offY = my - wy * cell
                        lastMx = mx
                        lastMy = my
                        lastDist = dist
                    } else {
                        offX += e.x - panLastX
                        offY += e.y - panLastY
                        panLastX = e.x
                        panLastY = e.y
                    }
                    invalidate()
                }
            }
            MotionEvent.ACTION_POINTER_UP -> {
                if (mode == MODE_PAN && e.pointerCount - 1 < 2) mode = MODE_NONE
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (mode == MODE_DRAW) {
                    if (e.actionMasked == MotionEvent.ACTION_CANCEL) cancelStroke(m) else endStroke(m)
                }
                mode = MODE_NONE
            }
        }
        return true
    }

    // ───────────────────────── desenho ─────────────────────────

    /** Marca o relevo de uma célula no editor (visto de cima). */
    private fun drawElevCell(canvas: Canvas, m: MapData, x: Int, y: Int, e: Int, l: Float, t: Float) {
        val lv = MT.level(e)
        val rp = MT.ramp(e)
        paint.style = Paint.Style.FILL
        if (lv > 0) {
            paint.color = ((lv * 0x14 + 0x10) shl 24) or 0xFFFFFF
            canvas.drawRect(l, t, l + cell + 1f, t + cell + 1f, paint)
        }
        if (rp != 0) {
            // faixas mais claras para o lado alto
            for (k in 0 until 4) {
                val a = (0x10 + k * 0x14) shl 24 or 0xFFFFFF
                paint.color = a
                val f0 = k / 4f
                val f1 = (k + 1) / 4f
                when (rp) {

                    1 -> canvas.drawRect(l, t + cell * (1f - f1), l + cell + 1f, t + cell * (1f - f0) + 1f, paint)
                    3 -> canvas.drawRect(l, t + cell * f0, l + cell + 1f, t + cell * f1 + 1f, paint)
                    2 -> canvas.drawRect(l + cell * f0, t, l + cell * f1 + 1f, t + cell + 1f, paint)
                    else -> canvas.drawRect(l + cell * (1f - f1), t, l + cell * (1f - f0) + 1f, t + cell + 1f, paint)
                }
            }
        }
        // penhasco: borda escura onde o vizinho do sul é mais baixo
        val zs = lv + (if (rp == 3) 1 else 0)
        val zn = m.levelAt(x, y + 1) + (if (MT.ramp(if (m.inB(x, y + 1)) m.elev[(y + 1) * m.w + x] else 0) == 1) 1 else 0)
        if (zs > zn) {
            paint.color = 0xB0000000.toInt()
            canvas.drawRect(l, t + cell - max(3f, cell * 0.1f), l + cell + 1f, t + cell + 1f, paint)
        }
        if (cell >= 22f && (layer == L_TERRAIN || rp != 0)) {
            textPaint.color = 0xFFFFFFFF.toInt()
            textPaint.setShadowLayer(3f, 0f, 0f, 0xFF000000.toInt())
            textPaint.textSize = cell * 0.4f
            val label = when (rp) { 1 -> "↑"; 2 -> "→"; 3 -> "↓"; 4 -> "←"; else -> "" } + (if (layer == L_TERRAIN) lv.toString() else "")
            if (label.isNotEmpty()) canvas.drawText(label, l + cell * 0.5f, t + cell * 0.66f, textPaint)
            textPaint.clearShadowLayer()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawColor(0xFF14181D.toInt())
        val m = map ?: return
        val x0 = max(0, floor(-offX / cell).toInt())
        val y0 = max(0, floor(-offY / cell).toInt())
        val x1 = min(m.w - 1, floor((width - offX) / cell).toInt())
        val y1 = min(m.h - 1, floor((height - offY) / cell).toInt())
        val time = MapTex.clock()

        // chão
        for (y in y0..y1) for (x in x0..x1) {
            val def = MT.floorDef(m.floor[y * m.w + x])
            val bmp = MapTex.tile(def, 0, time)
            val l = offX + x * cell
            val t = offY + y * cell
            rf.set(l, t, l + cell + 1f, t + cell + 1f)
            canvas.drawBitmap(bmp, srcTop, rf, bmpPaint)
        }
        // pontes
        for (y in y0..y1) for (x in x0..x1) {
            val br = m.bridge[y * m.w + x]
            if (br == 0) continue
            val l = offX + x * cell
            val t = offY + y * cell
            rf.set(l, t, l + cell + 1f, t + cell + 1f)
            canvas.drawBitmap(MapTex.bridge(br, m.bridgeVertical(x, y)), srcTop, rf, bmpPaint)
        }
        // relevo: clareia as células altas, marca penhascos (borda escura ao sul) e rampas (faixas + seta)
        for (y in y0..y1) for (x in x0..x1) {
            val e = m.elev[y * m.w + x]
            if (e == 0) continue
            drawElevCell(canvas, m, x, y, e, offX + x * cell, offY + y * cell)
        }
        // paredes e props, de cima para baixo
        for (y in y0..y1) for (x in x0..x1) {
            val o = m.obj[y * m.w + x]
            if (o == 0) continue
            val l = offX + x * cell
            val t = offY + y * cell
            if (MT.isWall(o)) {
                val cpId = m.cap[y * m.w + x]
                drawWallFlat(canvas, bmpPaint, MT.wallDef(o), if (cpId != 0) MT.wallDef(cpId) else null, m.wallH(x, y).coerceAtLeast(1), m.wallGeom(x, y), l, t, l + cell + 1f, t + cell + 1f)
                val hh = m.wallH(x, y)
                if (hh > 1 && cell >= 22f) {
                    textPaint.textSize = cell * 0.3f
                    textPaint.color = 0xFFFFFFFF.toInt()
                    canvas.drawText("" + hh, l + cell * 0.22f, t + cell * 0.95f, textPaint)
                }
            }
            else drawPropFlat(canvas, bmpPaint, o, l, t + cell, cell, time + ((x * 7 + y * 13) % 7) * 0.13f)
        }
        // grade
        if (showGrid && cell >= 22f) {
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 1f
            paint.color = 0x22FFFFFF
            for (x in x0..x1 + 1) {
                val lx = offX + x * cell
                canvas.drawLine(lx, offY + y0 * cell, lx, offY + (y1 + 1) * cell, paint)
            }
            for (y in y0..y1 + 1) {
                val ly = offY + y * cell
                canvas.drawLine(offX + x0 * cell, ly, offX + (x1 + 1) * cell, ly, paint)
            }
        }
        // entidades
        for (en in m.ents) {
            if (en.x < x0 || en.x > x1 || en.y < y0 || en.y > y1) continue
            drawEntMarker(canvas, paint, textPaint, en.type + 1, offX + (en.x + 0.5f) * cell, offY + (en.y + 0.5f) * cell, cell * 0.34f)
        }
        if (m.spawnX in x0..x1 && m.spawnY in y0..y1) {
            drawEntMarker(canvas, paint, textPaint, 0, offX + (m.spawnX + 0.5f) * cell, offY + (m.spawnY + 0.5f) * cell, cell * 0.34f)
        }
        // pré-visualização do retângulo
        if (mode == MODE_DRAW && curTool == T_LINE && isCellLayer()) {
            paint.style = Paint.Style.FILL
            paint.color = 0x55FFC107
            for ((lx, ly) in lineCells(rectSx, rectSy, rectEx, rectEy)) {
                rf.set(offX + lx * cell, offY + ly * cell, offX + (lx + 1) * cell, offY + (ly + 1) * cell)
                canvas.drawRect(rf, paint)
            }
        } else if (mode == MODE_DRAW && ((curTool == T_RECT && (isCellLayer() || layer == L_BUILD)) || curTool == T_COPY)) {
            val ax = min(rectSx, rectEx)
            val bx = max(rectSx, rectEx)
            val ay = min(rectSy, rectEy)
            val by = max(rectSy, rectEy)
            rf.set(offX + ax * cell, offY + ay * cell, offX + (bx + 1) * cell, offY + (by + 1) * cell)
            paint.style = Paint.Style.FILL
            paint.color = 0x33FFC107
            canvas.drawRect(rf, paint)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 3f
            paint.color = 0xFFFFC107.toInt()
            canvas.drawRect(rf, paint)
            if (layer == L_BUILD || curTool == T_COPY) {
                textPaint.textSize = cell * 0.42f
                textPaint.color = 0xFFFFFFFF.toInt()
                canvas.drawText("" + (bx - ax + 1) + "×" + (by - ay + 1), (rf.left + rf.right) / 2f, rf.top - cell * 0.12f, textPaint)
            }
        } else if (mode == MODE_DRAW && layer == L_HOUSE && curTool != T_ERASE) {
            val tp = Houses.template(selected[L_HOUSE])
            val gx = offX + (lastCx - tp.w / 2) * cell
            val gy = offY + (lastCy - tp.h / 2) * cell
            drawTemplate(canvas, ghostPaint, tp, gx, gy, cell, time)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 3f
            paint.color = 0xFFFFC107.toInt()
            canvas.drawRect(gx, gy, gx + tp.w * cell, gy + tp.h * cell, paint)
        } else if (mode == MODE_DRAW && curTool != T_RECT && curTool != T_LINE && layer != L_HOUSE && layer != L_BUILD) {
            rf.set(offX + lastCx * cell, offY + lastCy * cell, offX + (lastCx + 1) * cell, offY + (lastCy + 1) * cell)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 2f
            paint.color = 0xCCFFFFFF.toInt()
            canvas.drawRect(rf, paint)
        }
        // borda do mapa
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 4f
        paint.color = 0xFFFFC107.toInt()
        canvas.drawRect(offX, offY, offX + m.w * cell, offY + m.h * cell, paint)
        // mantém a água, a lava e o fogo animados
        postInvalidateDelayed(70)
    }
}
