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

        const val T_BRUSH = 0
        const val T_RECT = 1
        const val T_FILL = 2
        const val T_ERASE = 3
        const val T_PAN = 4

        private const val MODE_NONE = 0
        private const val MODE_DRAW = 1
        private const val MODE_PAN = 2

        private val srcTop = Rect(0, 0, MapTex.TS, MapTex.TS)
        private val srcFront = Rect(0, MapTex.TS, MapTex.TS, MapTex.TS + MapTex.WALL_H)
        private val srcProp = Rect(0, 0, PropArt.W, PropArt.H)

        /** valor de entidade: 0 = início do jogador, 1 = inimigo, 2 = inimigo forte, 3 = chefe */
        val entNames = arrayOf("Início", "Inimigo", "Inimigo forte", "Chefe")

        private fun drawWallFlat(c: Canvas, p: Paint, def: MT.Def?, mask: Int, l: Float, t: Float, r: Float, b: Float) {
            if (def == null) return
            val spr = MapTex.wall(def, mask)
            val rf = RectF(l, t, r, b)
            c.drawBitmap(spr, srcTop, rf, p)
            if ((mask and MapTex.M_S) == 0) {
                val fh = (b - t) * 0.28f
                c.drawBitmap(spr, srcFront, RectF(l, b - fh, r, b), p)
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
                    fun wl(xx: Int, yy: Int): Boolean = xx in 0 until tp.w && yy in 0 until tp.h && MT.isWall(tp.obj[yy * tp.w + xx])
                    var m = 0
                    if (y == 0 || wl(x, y - 1)) m = m or MapTex.M_N
                    if (x == tp.w - 1 || wl(x + 1, y)) m = m or MapTex.M_E
                    if (y < tp.h - 1 && wl(x, y + 1)) m = m or MapTex.M_S
                    if (x == 0 || wl(x - 1, y)) m = m or MapTex.M_W
                    drawWallFlat(c, p, MT.wallDef(o), m, cl, ct, cl + cell + 1f, ct + cell + 1f)
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
                L_WALL -> drawWallFlat(c, p, MT.wallDef(v), 0, 0f, 0f, 64f, 64f)
                L_PROP -> {
                    c.drawBitmap(MapTex.tile(MT.floorDef(MT.F_STONE), 0), 0f, 0f, p)
                    drawPropFlat(c, p, v, 0f, 64f, 64f)
                }
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
    val selected = intArrayOf(MT.F_STONE, MT.W_STONE, MT.P_CRATE, 0, 0)
    var dirty = false
    var onHistoryChanged: (() -> Unit)? = null
    var onMessage: ((String) -> Unit)? = null

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
            L_WALL, L_PROP -> m.obj[i] = if (erase) 0 else selected[layer]
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

    private fun floodFill(m: MapData, sx: Int, sy: Int) {
        if (!m.inB(sx, sy)) return
        val arr = if (layer == L_FLOOR) m.floor else m.obj
        val repl = if (layer == L_FLOOR) selected[L_FLOOR] else selected[layer]
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

    private fun beginStroke(m: MapData, x: Float, y: Float) {
        before = m.snapshot()
        val (cx, cy) = cellAt(x, y)
        lastCx = cx
        lastCy = cy
        if (layer == L_HOUSE) {
            if (tool == T_ERASE) onMessage?.invoke("Para apagar, use a borracha nas abas Chão, Paredes ou Objetos")
            invalidate()
            return
        }
        when (tool) {
            T_BRUSH, T_ERASE -> applyCell(m, cx, cy, tool == T_ERASE)
            T_RECT -> {
                if (layer == L_ENT) onMessage?.invoke("Para entidades use o pincel")
                rectSx = cx; rectSy = cy; rectEx = cx; rectEy = cy
            }
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
        if (layer == L_HOUSE) {
            lastCx = cx
            lastCy = cy
            invalidate()
            return
        }
        when (tool) {
            T_BRUSH, T_ERASE -> line(m, lastCx, lastCy, cx, cy, tool == T_ERASE)
            T_RECT -> { rectEx = cx; rectEy = cy }
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
            if (o >= 0) m.obj[i] = o
        }
        // apaga inimigos que ficaram dentro de paredes
        m.ents.removeAll { e -> m.inB(e.x, e.y) && MT.solidObj(m.obj[e.y * m.w + e.x]) }
    }

    private fun endStroke(m: MapData) {
        if (layer == L_HOUSE && tool != T_ERASE) stampHouse(m, lastCx, lastCy)
        if (tool == T_RECT && layer != L_ENT && layer != L_HOUSE) {
            val x0 = max(0, min(rectSx, rectEx))
            val x1 = min(m.w - 1, max(rectSx, rectEx))
            val y0 = max(0, min(rectSy, rectEy))
            val y1 = min(m.h - 1, max(rectSy, rectEy))
            for (yy in y0..y1) for (xx in x0..x1) applyCell(m, xx, yy, false)
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
        // paredes e props, de cima para baixo
        for (y in y0..y1) for (x in x0..x1) {
            val o = m.obj[y * m.w + x]
            if (o == 0) continue
            val l = offX + x * cell
            val t = offY + y * cell
            if (MT.isWall(o)) drawWallFlat(canvas, bmpPaint, MT.wallDef(o), wallMask(m, x, y), l, t, l + cell + 1f, t + cell + 1f)
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
        if (mode == MODE_DRAW && tool == T_RECT && layer != L_ENT && layer != L_HOUSE) {
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
        } else if (mode == MODE_DRAW && layer == L_HOUSE && tool != T_ERASE) {
            val tp = Houses.template(selected[L_HOUSE])
            val gx = offX + (lastCx - tp.w / 2) * cell
            val gy = offY + (lastCy - tp.h / 2) * cell
            drawTemplate(canvas, ghostPaint, tp, gx, gy, cell, time)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 3f
            paint.color = 0xFFFFC107.toInt()
            canvas.drawRect(gx, gy, gx + tp.w * cell, gy + tp.h * cell, paint)
        } else if (mode == MODE_DRAW && tool != T_RECT && layer != L_HOUSE) {
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

    private fun wallMask(m: MapData, x: Int, y: Int): Int {
        fun wl(xx: Int, yy: Int) = m.inB(xx, yy) && MT.isWall(m.obj[yy * m.w + xx])
        var k = 0
        if (y == 0 || wl(x, y - 1)) k = k or MapTex.M_N
        if (x == m.w - 1 || wl(x + 1, y)) k = k or MapTex.M_E
        if (y < m.h - 1 && wl(x, y + 1)) k = k or MapTex.M_S
        if (x == 0 || wl(x - 1, y)) k = k or MapTex.M_W
        return k
    }
}
