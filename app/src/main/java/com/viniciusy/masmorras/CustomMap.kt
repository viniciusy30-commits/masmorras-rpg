package com.viniciusy.masmorras

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import kotlin.math.abs
import kotlin.math.sin

/** Mapa montado no editor, pronto para jogar. Chão desenhado por tile, paredes/props por fileira (ordem de profundidade). */
class CustomMap(private val data: MapData) : GameMap {

    override val width: Int = data.w
    override val height: Int = data.h
    override val tileSize: Float = 64f

    private val ts = 64f
    private val solid = BooleanArray(width * height)
    private val spawnCell: Pair<Int, Int>
    private val bmpPaint = Paint().apply { isFilterBitmap = false; isAntiAlias = false }
    private val src = Rect(0, 0, MapTex.TS, MapTex.TS)
    private val dst = RectF()
    private val srcR = Rect()

    /** Portas animadas: abertura 0..1 por célula. Só portas de ferro/portão bloqueiam enquanto fechadas. */
    private val doorOpen = FloatArray(width * height)
    private val doorCells = ArrayList<Int>()

    init {
        for (i in solid.indices) {
            solid[i] = (MT.solidFloor(data.floor[i]) && data.bridge[i] == 0) || MT.solidObj(data.obj[i])
            if (MT.isDoor(data.obj[i])) doorCells.add(i)
        }
        spawnCell = findFree(data.spawnX, data.spawnY)
    }

    /** Célula livre mais próxima de (cx, cy). */
    private fun findFree(cx: Int, cy: Int): Pair<Int, Int> {
        val sx = cx.coerceIn(0, width - 1)
        val sy = cy.coerceIn(0, height - 1)
        if (!solid[sy * width + sx]) return sx to sy
        val maxR = maxOf(width, height)
        for (r in 1..maxR) {
            for (dy in -r..r) for (dx in -r..r) {
                if (abs(dx) != r && abs(dy) != r) continue
                val x = sx + dx
                val y = sy + dy
                if (x in 0 until width && y in 0 until height && !solid[y * width + x]) return x to y
            }
        }
        return sx to sy
    }

    private fun hash(x: Int, y: Int): Int {
        var h = x * 374761393 + y * 668265263
        h = (h xor (h ushr 13)) * 1274126177
        return (h xor (h ushr 16)) and 0x7FFFFFFF
    }

    /** Altura do terreno (px) sob o ponto: níveis de relevo e rampas inclinadas. */
    override fun heightAt(wx: Float, wy: Float): Float {
        if (wx < 0f || wy < 0f) return 0f
        val tx = (wx / ts).toInt()
        val ty = (wy / ts).toInt()
        if (tx >= width || ty >= height) return 0f
        return data.surfaceZ(tx, ty, wx / ts - tx, wy / ts - ty)
    }

    /** Altura (px) em que objetos e paredes da célula ficam apoiados. */
    private fun cellZ(i: Int): Float {
        val e = data.elev[i]
        return MT.level(e) * MT.EH + (if (MT.ramp(e) != 0) MT.EH * 0.5f else 0f)
    }

    private fun bridgeAt(x: Int, y: Int): Boolean = x in 0 until width && y in 0 until height && data.bridge[y * width + x] != 0

    /** Ponte na vertical (norte-sul)? Segue as pontes vizinhas; sozinha, atravessa o rio. */
    private fun bridgeVertical(x: Int, y: Int): Boolean {

        val v = bridgeAt(x, y - 1) || bridgeAt(x, y + 1)
        val hz = bridgeAt(x - 1, y) || bridgeAt(x + 1, y)
        if (v && !hz) return true
        if (hz) return false
        val ns = isLiquid(floorAt(x, y - 1)) || isLiquid(floorAt(x, y + 1)) || floorAt(x, y - 1) == MT.F_VOID || floorAt(x, y + 1) == MT.F_VOID
        val ew = isLiquid(floorAt(x - 1, y)) || isLiquid(floorAt(x + 1, y)) || floorAt(x - 1, y) == MT.F_VOID || floorAt(x + 1, y) == MT.F_VOID
        return ew && !ns
    }

    override fun isWall(wx: Float, wy: Float): Boolean {
        if (wx < 0f || wy < 0f) return true
        val tx = (wx / ts).toInt()
        val ty = (wy / ts).toInt()
        if (tx >= width || ty >= height) return true
        val i = ty * width + tx
        if (MT.isLockedDoor(data.obj[i]) && (!MT.solidFloor(data.floor[i]) || data.bridge[i] != 0)) return doorOpen[i] < 0.8f
        return solid[i]
    }

    /** Transparência arquitetônica (telhado/paredes que bloqueiam a visão). Só visual. */
    val arch = ArchVis(data, ts.toInt())

    override fun update(px: Float, py: Float, dt: Float) {
        arch.update(px, py, dt)
        for (i in doorCells) {
            val cx = (i % width + 0.5f) * ts
            val cy = (i / width + 0.5f) * ts
            val near = abs(cx - px) < ts * 1.7f && abs(cy - py) < ts * 1.7f
            val tgt = if (near) 1f else 0f
            val cur = doorOpen[i]
            doorOpen[i] = if (tgt > cur) minOf(tgt, cur + dt * 4f) else maxOf(tgt, cur - dt * 2.2f)
        }
    }

    override fun inSafeZone(wx: Float, wy: Float): Boolean = false

    override fun worldWidth(): Float = width * ts
    override fun worldHeight(): Float = height * ts

    override fun getPlayerSpawn(): Pair<Float, Float> =
        Pair((spawnCell.first + 0.5f) * ts, (spawnCell.second + 0.5f) * ts)

    override fun getBossSpawn(): Pair<Float, Float> {
        val b = data.ents.firstOrNull { it.type == 2 }
        return if (b != null) Pair((b.x + 0.5f) * ts, (b.y + 0.5f) * ts) else getPlayerSpawn()
    }

    override fun getEnemySpawns(count: Int): List<Pair<Float, Float>> = emptyList()

    override fun createEnemies(): List<Enemy> {
        val list = ArrayList<Enemy>()
        for (e in data.ents) {
            if (!data.inB(e.x, e.y)) continue
            if (solid[e.y * width + e.x]) continue
            val en = Enemy((e.x + 0.5f) * ts, (e.y + 0.5f) * ts, e.type.coerceIn(0, 2))
            en.ground = heightAt(en.x, en.y)
            en.alt = en.ground

            list.add(en)
        }
        return list
    }

    override fun prewarm(wx: Float, wy: Float) {
        for (d in MT.floors) MapTex.tile(d, 0)
        for (d in MT.walls) for (m in 0..15) MapTex.wall(d, m)
        for (d in MT.props) PropArt.get(d.id)
    }

    private fun isWallCell(x: Int, y: Int): Boolean =
        x in 0 until width && y in 0 until height && MT.isWall(data.obj[y * width + x])

    private fun floorAt(x: Int, y: Int): Int =
        if (x in 0 until width && y in 0 until height) data.floor[y * width + x] else -1

    private fun isLiquid(f: Int) = f == MT.F_WATER || f == MT.F_LAVA || f == MT.F_SLIME

    /** Espuma animada nas margens da água. */
    private fun foam(canvas: Canvas, paint: Paint, x: Float, y: Float, side: Int, t: Float, seed: Int) {
        for (k in 0 until 8) {
            val o = sin(t * 2.2f + k * 0.9f + seed * 0.7f) * 2.2f
            val seg = ts / 8f
            val a = 0x30 + (0x30 * (0.5f + 0.5f * sin(t * 1.7f + k + seed))).toInt()
            for (layer in 0..1) {
                val th = if (layer == 0) 6f + o else 2.5f + o * 0.4f
                paint.color = ((if (layer == 0) a / 2 else a) shl 24) or 0xFFFFFF
                when (side) {
                    0 -> canvas.drawRect(x + k * seg, y, x + (k + 1) * seg + 0.5f, y + th, paint)
                    1 -> canvas.drawRect(x + k * seg, y + ts - th, x + (k + 1) * seg + 0.5f, y + ts, paint)
                    2 -> canvas.drawRect(x, y + k * seg, x + th, y + (k + 1) * seg + 0.5f, paint)
                    else -> canvas.drawRect(x + ts - th, y + k * seg, x + ts, y + (k + 1) * seg + 0.5f, paint)
                }
            }
        }
    }

    /** Brilho que vaza de lava/lodo para o chão vizinho. */
    private fun spill(canvas: Canvas, paint: Paint, x: Float, y: Float, side: Int, color: Int, pulse: Float) {
        val steps = intArrayOf(0x46, 0x2a, 0x14)
        for (i in 0..2) {
            val a = (steps[i] * pulse).toInt().coerceIn(0, 255)
            paint.color = (a shl 24) or color
            val o0 = i * 6f
            val o1 = o0 + 6f
            when (side) {
                0 -> canvas.drawRect(x, y + o0, x + ts, y + o1, paint)
                1 -> canvas.drawRect(x, y + ts - o1, x + ts, y + ts - o0, paint)
                2 -> canvas.drawRect(x + o0, y, x + o1, y + ts, paint)
                else -> canvas.drawRect(x + ts - o1, y, x + ts - o0, y + ts, paint)
            }
        }
    }

    override fun drawFloor(canvas: Canvas, paint: Paint, camX: Float, camY: Float, viewW: Float, viewH: Float) {
        val sx = (camX / ts).toInt().coerceAtLeast(0)
        val ex = ((camX + viewW) / ts).toInt().coerceAtMost(width - 1)
        val sy = (camY / ts).toInt().coerceAtLeast(0)
        val ey = ((camY + viewH) / ts).toInt().coerceAtMost(height - 1)
        val t = VoxelArt.time
        val pulse = 0.75f + 0.25f * sin(t * 2.4f)
        for (ty in sy..ey) for (tx in sx..ex) {
            val f = data.floor[ty * width + tx]
            val def = MT.floorDef(f)
            val bmp = MapTex.tile(def, hash(tx, ty) and 3, t)
            val x = Math.round(tx * ts - camX).toFloat()
            val y = Math.round(ty * ts - camY).toFloat()
            dst.set(x, y, x + ts + 1f, y + ts + 1f) // +1px evita frestas com zoom
            canvas.drawBitmap(bmp, src, dst, bmpPaint)

            paint.style = Paint.Style.FILL
            if (f == MT.F_WATER) {
                // espuma nas margens com terra
                val nb = intArrayOf(floorAt(tx, ty - 1), floorAt(tx, ty + 1), floorAt(tx - 1, ty), floorAt(tx + 1, ty))
                for (s in 0..3) if (nb[s] >= 0 && nb[s] != MT.F_WATER && !isLiquid(nb[s]) && nb[s] != MT.F_VOID) foam(canvas, paint, x, y, s, t, tx * 3 + ty)
            } else if (f == MT.F_LAVA) {
                val g = 0.5f + 0.5f * sin(t * 2.2f + tx * 0.7f + ty * 0.9f)
                paint.color = ((0x28 * g).toInt() shl 24) or 0xFFC040
                canvas.drawRect(x, y, x + ts, y + ts, paint)
                val nb = intArrayOf(floorAt(tx, ty - 1), floorAt(tx, ty + 1), floorAt(tx - 1, ty), floorAt(tx + 1, ty))
                for (s in 0..3) if (nb[s] >= 0 && nb[s] != MT.F_LAVA && nb[s] != MT.F_VOID) {
                    paint.color = 0x70120604
                    when (s) {
                        0 -> canvas.drawRect(x, y, x + ts, y + 4f, paint)
                        1 -> canvas.drawRect(x, y + ts - 4f, x + ts, y + ts, paint)
                        2 -> canvas.drawRect(x, y, x + 4f, y + ts, paint)
                        else -> canvas.drawRect(x + ts - 4f, y, x + ts, y + ts, paint)
                    }
                }
            } else {
                // chão comum: brilho vindo de lava/lodo vizinhos
                if (!isLiquid(f) && f != MT.F_VOID) {
                    val nb = intArrayOf(floorAt(tx, ty - 1), floorAt(tx, ty + 1), floorAt(tx - 1, ty), floorAt(tx + 1, ty))
                    for (s in 0..3) {
                        if (nb[s] == MT.F_LAVA) spill(canvas, paint, x, y, s, 0xFF7A20, pulse)
                        else if (nb[s] == MT.F_SLIME) spill(canvas, paint, x, y, s, 0x7CFF3C, pulse * 0.7f)
                    }
                }
            }
            // sombra embaixo das paredes
            if (isWallCell(tx, ty - 1) && !isWallCell(tx, ty)) {
                paint.color = 0x40000000
                canvas.drawRect(x, y, x + ts, y + 8f, paint)
                paint.color = 0x22000000
                canvas.drawRect(x, y + 8f, x + ts, y + 18f, paint)
            }
            // pontes por cima do chão (água, lava, vazio)
            val br = data.bridge[ty * width + tx]
            if (br != 0) canvas.drawBitmap(MapTex.bridge(br, bridgeVertical(tx, ty)), x, y, bmpPaint)
        }
    }

    override fun drawRow(canvas: Canvas, paint: Paint, ty: Int, camX: Float, camY: Float, viewW: Float) {
        if (ty < 0 || ty >= height) return
        val sx = (camX / ts).toInt().coerceAtLeast(0)
        val ex = ((camX + viewW) / ts).toInt().coerceAtMost(width - 1)
        val t = VoxelArt.time
        // terreno elevado desta fileira (topo + face do penhasco), na ordem de profundidade
        for (tx in sx..ex) {
            if (data.elev[ty * width + tx] != 0) drawTerrain(canvas, paint, tx, ty, camX, camY, t)
        }
        for (tx in sx..ex) {
            val o = data.obj[ty * width + tx]
            if (o == 0) continue
            val z = cellZ(ty * width + tx)
            val x = Math.round(tx * ts - camX).toFloat()
            if (MT.isWall(o)) {
                val def = MT.wallDef(o) ?: continue
                val gm = data.wallGeom(tx, ty)
                val hg = data.wallH(tx, ty).coerceAtLeast(1)
                val capDef = if (data.cap[ty * width + tx] != 0) MT.wallDef(data.cap[ty * width + tx]) else null
                val frame = if (MT.isDoor(o)) Math.round(doorOpen[ty * width + tx] * 3f).coerceIn(0, 3) else 0
                val bmp = MapTex.wall(def, gm and 15, hg, gm shr 4, capDef, frame)
                val wy = Math.round(ty * ts - hg * MapTex.WALL_H - camY - z).toFloat()
                val wi = ty * width + tx
                val wa = arch.wallA[wi]
                val ca = arch.capA[wi]
                if (wa >= 0.99f && ca >= 0.99f) {
                    canvas.drawBitmap(bmp, x, wy, bmpPaint)
                } else {
                    if (capDef != null && ca < 0.99f) {
                        val body = MapTex.wall(def, gm and 15, hg, gm shr 4, null, frame)
                        bmpPaint.alpha = (wa * 255f).toInt().coerceIn(0, 255)
                        canvas.drawBitmap(body, x, wy, bmpPaint)
                        if (ca > 0.02f) {
                            bmpPaint.alpha = (wa * ca * 255f).toInt().coerceIn(0, 255)
                            canvas.drawBitmap(bmp, x, wy, bmpPaint)
                        }
                    } else {
                        bmpPaint.alpha = (wa * 255f).toInt().coerceIn(0, 255)
                        canvas.drawBitmap(bmp, x, wy, bmpPaint)
                    }
                    bmpPaint.alpha = 255
                }
                if (o == MT.W_CHIMNEY) smoke(canvas, paint, x + 32f, wy + 30f, t, tx * 7 + ty)
            } else {
                val bmp = PropArt.get(o, t + hash(tx, ty) % 7 * 0.13f, data.hgt[ty * width + tx])
                val y = Math.round((ty + 1) * ts - PropArt.H - camY - z).toFloat()
                canvas.drawBitmap(bmp, x, y, bmpPaint)
                val pt = t + hash(tx, ty) % 7 * 0.13f
                val fq = PropArt.frac(o, pt)
                if (fq > 0.03f) {
                    bmpPaint.alpha = (fq * 255f).toInt().coerceIn(0, 255)
                    canvas.drawBitmap(PropArt.getNext(o, pt, data.hgt[ty * width + tx]), x, y, bmpPaint)
                    bmpPaint.alpha = 255
                }
                val g = MT.glows[o]
                if (g != null) {
                    val fl = 1f + g.flicker * (0.08f * sin(t * 9f + tx * 1.3f) + 0.05f * sin(t * 15f + ty))
                    val cx = x + 32f
                    val cy = y + g.cy
                    val r = g.radius * fl
                    paint.style = Paint.Style.FILL
                    paint.color = (0x12 shl 24) or g.color
                    canvas.drawCircle(cx, cy, r, paint)
                    paint.color = (0x1C shl 24) or g.color
                    canvas.drawCircle(cx, cy, r * 0.6f, paint)
                    paint.color = (0x2A shl 24) or g.color
                    canvas.drawCircle(cx, cy, r * 0.3f, paint)
                }
            }
        }
    }

    /** Topo (plano ou rampa inclinada) e face de penhasco de uma célula com relevo. */
    private fun drawTerrain(canvas: Canvas, paint: Paint, tx: Int, ty: Int, camX: Float, camY: Float, t: Float) {
        val i = ty * width + tx
        val e = data.elev[i]
        val rp = MT.ramp(e)
        val def = MT.floorDef(data.floor[i])
        val tile = MapTex.tile(def, hash(tx, ty) and 3, t)
        val x = Math.round(tx * ts - camX).toFloat()
        val y0 = ty * ts - camY
        val base = MT.level(e) * MT.EH
        val tsi = MapTex.TS

        if (rp == 0) {
            val top = Math.round(y0 - base).toFloat()
            dst.set(x, top, x + ts + 1f, top + ts + 1f)
            canvas.drawBitmap(tile, src, dst, bmpPaint)
            paint.style = Paint.Style.FILL
            val lv = MT.level(e)
            // quanto mais alto o nível, mais clara a superfície (luz vem de cima)
            paint.color = (minOf(lv * 7, 34) shl 24) or 0xFFF4D8
            canvas.drawRect(x, top, x + ts, top + ts, paint)
            // luz vinda do noroeste: bordas norte e oeste brilham, a leste escurece
            if (data.levelAt(tx, ty - 1) < lv) {
                paint.color = 0x5CFFFFFF
                canvas.drawRect(x, top, x + ts, top + 3f, paint)
                paint.color = 0x24FFFFFF
                canvas.drawRect(x, top + 3f, x + ts, top + 7f, paint)
            }
            if (data.levelAt(tx - 1, ty) < lv) {
                paint.color = 0x40FFFFFF
                canvas.drawRect(x, top, x + 3f, top + ts, paint)
                paint.color = 0x18FFFFFF
                canvas.drawRect(x + 3f, top, x + 6f, top + ts, paint)
            }
            if (data.levelAt(tx + 1, ty) < lv) {
                paint.color = 0x58000000
                canvas.drawRect(x + ts - 3f, top, x + ts, top + ts, paint)
                paint.color = 0x24000000
                canvas.drawRect(x + ts - 7f, top, x + ts - 3f, top + ts, paint)
            }
            // sombra de oclusão onde a vizinha ao norte é mais alta (ela cobre parte do chão aqui)
            if (data.levelAt(tx, ty - 1) > lv) {
                paint.color = 0x30000000
                canvas.drawRect(x, top, x + ts, top + 10f, paint)
            }
        } else if (rp == 1 || rp == 3) {
            // rampa para norte/sul: faixas horizontais, cada uma mais alta que a anterior
            val n = 8
            val step = tsi / n
            for (k in 0 until n) {
                val v0 = k / n.toFloat()
                val v1 = (k + 1) / n.toFloat()
                val za = base + (if (rp == 1) 1f - v0 else v0) * MT.EH
                val zb = base + (if (rp == 1) 1f - v1 else v1) * MT.EH
                val ta = Math.round(y0 + v0 * ts - za).toFloat()
                val tb = Math.round(y0 + v1 * ts - zb).toFloat() + 1f
                srcR.set(0, k * step, tsi, (k + 1) * step)
                dst.set(x, ta, x + ts + 1f, maxOf(tb, ta + 1f))
                canvas.drawBitmap(tile, srcR, dst, bmpPaint)
                // a rampa escurece no pé e clareia no alto, para parecer inclinada
                val fr = if (rp == 1) 1f - v0 else v0
                paint.style = Paint.Style.FILL
                paint.color = (((1f - fr) * 0x42).toInt() shl 24) or 0x101018
                canvas.drawRect(x, ta, x + ts + 1f, maxOf(tb, ta + 1f), paint)
                paint.color = (((fr) * 0x22).toInt() shl 24) or 0xFFF4D8
                canvas.drawRect(x, ta, x + ts + 1f, maxOf(tb, ta + 1f), paint)
            }

        } else {
            // rampa para leste/oeste: colunas finas com alturas diferentes
            val n = 16
            val step = tsi / n
            for (k in 0 until n) {
                val uc = (k + 0.5f) / n
                val z = base + (if (rp == 2) uc else 1f - uc) * MT.EH
                val top = Math.round(y0 - z).toFloat()
                srcR.set(k * step, 0, (k + 1) * step, tsi)
                dst.set(x + k * step, top, x + (k + 1) * step + 1f, top + ts + 1f)
                canvas.drawBitmap(tile, srcR, dst, bmpPaint)
                val fr = if (rp == 2) uc else 1f - uc
                paint.style = Paint.Style.FILL
                paint.color = (((1f - fr) * 0x42).toInt() shl 24) or 0x101018
                canvas.drawRect(x + k * step, top, x + (k + 1) * step + 1f, top + ts + 1f, paint)
                paint.color = (((fr) * 0x22).toInt() shl 24) or 0xFFF4D8
                canvas.drawRect(x + k * step, top, x + (k + 1) * step + 1f, top + ts + 1f, paint)
            }
        }
        // face de penhasco na borda sul, onde o vizinho é mais baixo
        val cliff = MapTex.cliff(def)
        val yS = (ty + 1) * ts - camY
        paint.style = Paint.Style.FILL
        for (k in 0 until 8) {
            val u = (k + 0.5f) / 8f
            val zs = data.surfaceZ(tx, ty, u, 1f)
            val zn = data.surfaceZ(tx, ty + 1, u, 0f)
            if (zs - zn < 0.5f) continue
            val tp = Math.round(yS - zs)
            val bt = Math.round(yS - zn)
            if (bt <= tp) continue
            val cx = x + k * 8f
            val sxp = k * 8
            val topPart = minOf(bt - tp, MapTex.CLIFF_TOP)
            srcR.set(sxp, 0, sxp + 8, topPart)
            dst.set(cx, tp.toFloat(), cx + 9f, (tp + topPart).toFloat())
            canvas.drawBitmap(cliff, srcR, dst, bmpPaint)
            var yy = tp + topPart

            val bodyH = MapTex.CLIFF_H - MapTex.CLIFF_TOP
            while (yy < bt) {
                val seg = minOf(bodyH, bt - yy)
                srcR.set(sxp, MapTex.CLIFF_TOP, sxp + 8, MapTex.CLIFF_TOP + seg)
                dst.set(cx, yy.toFloat(), cx + 9f, (yy + seg).toFloat())
                canvas.drawBitmap(cliff, srcR, dst, bmpPaint)
                yy += seg
            }
            // parede: clara na borda de cima e cada vez mais escura até o pé
            val hh = bt - tp
            for (b in 1..3) {
                val ya = tp + hh * b / 4
                val yb = tp + hh * (b + 1) / 4
                if (yb <= ya) continue
                paint.color = (CLIFF_SHADE[b] shl 24) or 0x08080E
                canvas.drawRect(cx, ya.toFloat(), cx + 9f, yb.toFloat(), paint)
            }
            // quina escura no pé da parede e sombra projetada no chão, mais longa quanto mais alta a parede
            paint.color = 0x66000000
            canvas.drawRect(cx, (bt - 3).toFloat(), cx + 9f, bt.toFloat(), paint)
            val sl = minOf(hh * 0.55f, 26f)
            paint.color = 0x52000000
            canvas.drawRect(cx, bt.toFloat(), cx + 9f, bt + sl * 0.34f, paint)
            paint.color = 0x34000000
            canvas.drawRect(cx, bt + sl * 0.34f, cx + 9f, bt + sl * 0.68f, paint)
            paint.color = 0x18000000
            canvas.drawRect(cx, bt + sl * 0.68f, cx + 9f, bt + sl, paint)
        }
    }

    private val CLIFF_SHADE = intArrayOf(0, 0x20, 0x3C, 0x5A)

    /** Fumaça subindo da chaminé. */
    private fun smoke(canvas: Canvas, paint: Paint, cx: Float, cy: Float, t: Float, seed: Int) {
        paint.style = Paint.Style.FILL
        for (k in 0 until 5) {
            val ph = (t * 0.45f + k * 0.2f + (seed % 5) * 0.13f) % 1f
            val px = cx + sin(t * 1.3f + k * 1.7f + seed) * 5f + ph * 14f
            val py = cy - ph * 70f
            val r = 5f + ph * 13f
            val a = ((1f - ph) * 0x58).toInt().coerceIn(0, 255)
            paint.color = (a shl 24) or 0xC8C8CC
            canvas.drawCircle(px, py, r, paint)
        }
    }
}
