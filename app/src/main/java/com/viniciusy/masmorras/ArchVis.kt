package com.viniciusy.masmorras

/**
 * Visibilidade arquitetônica inteligente (só visual, nada disso é salvo no mapa).
 *
 * - Paredes que ficam NA FRENTE do herói (e escondem ele) ficam translúcidas, só elas.
 * - Quando o herói está dentro de um cômodo fechado (área cercada por paredes/portas),
 *   os telhados (cap) das paredes que cercam o cômodo somem suavemente e as paredes
 *   de frente do cômodo inteiro ficam translúcidas, para ver os móveis.
 * - Por fora, tudo continua opaco e com telhado. Cenário externo nunca fica visível através
 *   de parede: as outras paredes continuam opacas.
 * Limite conhecido: cômodo com vão aberto (sem porta) vaza para fora e conta como "por fora".
 */
class ArchVis(private val d: MapData, private val ts: Int) {
    /** 0 = automático, 1 = desligado (tudo opaco). */
    var mode = 0
    var minWall = 0.30f          // opacidade mínima de parede bloqueando a visão
    var speed = 5f               // velocidade da transição (1/s)
    var maxRoomCells = 500       // acima disso a área não conta como cômodo fechado

    private val w = d.w
    private val h = d.h
    val wallA = FloatArray(w * h) { 1f }
    val capA = FloatArray(w * h) { 1f }
    private val tw = FloatArray(w * h) { 1f }
    private val tc = FloatArray(w * h) { 1f }
    private val inCur = BooleanArray(w * h)
    private val inAct = BooleanArray(w * h)
    private val cur = ArrayList<Int>()
    private val act = ArrayList<Int>()
    private val seen = IntArray(w * h)
    private var stamp = 0
    private val queue = IntArray(maxRoomCells + 8)
    private var qn = 0
    private var lastX = -999
    private var lastY = -999
    private var recalc = 0f
    var inside = false
        private set

    fun update(px: Float, py: Float, dt: Float) {
        val tx = (px / ts).toInt()
        val ty = (py / ts).toInt()
        recalc -= dt
        if (tx != lastX || ty != lastY || recalc <= 0f) {
            lastX = tx; lastY = ty; recalc = 0.5f
            compute(tx, ty)
        }
        step(dt)
    }

    private fun mark(i: Int, wt: Float, ct: Float) {
        if (wt < tw[i]) tw[i] = wt
        if (ct < tc[i]) tc[i] = ct
        if (!inCur[i]) { inCur[i] = true; cur.add(i) }
        if (!inAct[i]) { inAct[i] = true; act.add(i) }
    }

    private fun compute(tx: Int, ty: Int) {
        for (i in cur) { tw[i] = 1f; tc[i] = 1f; inCur[i] = false }
        cur.clear()
        inside = false
        if (mode == 1) return
        inside = floodRoom(tx, ty)
        // quem enxerga: o herói e, dentro de cômodo, todas as células do cômodo
        occluders(tx, ty, if (inside) 2 else 1)
        if (inside) {
            for (k in 0 until qn) {
                val c = queue[k]
                val x = c % w
                val y = c / w
                occluders(x, y, 0)
                for (dy in -1..1) for (dx in -1..1) {
                    if (dx == 0 && dy == 0) continue
                    val nx = x + dx
                    val ny = y + dy
                    if (d.wallH(nx, ny) > 0) mark(ny * w + nx, 1f, 0f)   // telhado do cômodo some
                }
            }
        }
    }

    /** Paredes ao sul da célula (x,y) cujo sprite alto cobre quem está em (x,y). */
    private fun occluders(x: Int, y: Int, spread: Int) {
        for (dx in -spread..spread) for (dy in 1..4) {
            val nx = x + dx
            val ny = y + dy
            val hg = d.wallH(nx, ny)
            if (hg > 0 && dy * ts - hg * MapTex.WALL_H < 44) mark(ny * w + nx, minWall, 0f)
        }
    }

    /** Preenche o espaço livre a partir da célula; true se for área fechada (cômodo). */
    private fun floodRoom(sx: Int, sy: Int): Boolean {
        qn = 0
        if (sx !in 0 until w || sy !in 0 until h || d.wallH(sx, sy) > 0) return false
        stamp++
        var head = 0
        queue[qn++] = sy * w + sx
        seen[sy * w + sx] = stamp
        while (head < qn) {
            val c = queue[head++]
            val x = c % w
            val y = c / w
            for (k in 0..3) {
                val nx = x + (if (k == 0) 1 else if (k == 1) -1 else 0)
                val ny = y + (if (k == 2) 1 else if (k == 3) -1 else 0)
                if (nx !in 0 until w || ny !in 0 until h) { qn = 0; return false }
                val ni = ny * w + nx
                if (seen[ni] == stamp || d.wallH(nx, ny) > 0) continue
                if (qn >= maxRoomCells) { qn = 0; return false }
                seen[ni] = stamp
                queue[qn++] = ni
            }
        }
        return true
    }

    private fun step(dt: Float) {
        val s = speed * dt
        var k = act.size - 1
        while (k >= 0) {
            val i = act[k]
            wallA[i] = approach(wallA[i], tw[i], s)
            capA[i] = approach(capA[i], tc[i], s)
            if (wallA[i] == tw[i] && capA[i] == tc[i] && tw[i] >= 1f && tc[i] >= 1f) {
                inAct[i] = false
                act.removeAt(k)
            }
            k--
        }
    }

    private fun approach(v: Float, t: Float, s: Float): Float =
        if (v < t) minOf(t, v + s) else maxOf(t, v - s)
}
