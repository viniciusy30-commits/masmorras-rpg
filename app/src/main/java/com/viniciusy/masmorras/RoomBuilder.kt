package com.viniciusy.masmorras

import kotlin.math.max
import kotlin.math.min

/** Opções do construtor de cômodos (aba Obra do editor). */
class BuildOpts {
    var wall = MT.W_HOUSE
    var floor = MT.F_PLANKS
    /** Andares das paredes (1..4). */
    var storeys = 1
    /** Material dos andares de cima (0 = igual ao de baixo). Só vale com 2+ andares. */
    var cap = 0
    var door = 0
    var window = 0
    /** Lado da porta: 0 sul, 1 norte, 2 leste, 3 oeste. */
    var side = 0
    var furn = 0
    /** true = construção maciça (cenário, não dá para entrar); false = cômodo aberto. */
    var solid = false
    /** Caminho de pedra e lampiões na frente da porta. */
    var porch = true
}

/**
 * Constrói um cômodo completo numa área arrastada no editor: paredes (com andares e telhado),
 * porta, janelas, piso, entrada e móveis posicionados conforme o tipo de cômodo.
 */
object RoomBuilder {
    val wallChoices = listOf(
        MT.W_HOUSE, MT.W_STONE, MT.W_BRICK, MT.W_LOG, MT.W_WOOD, MT.W_TIMBER, MT.W_ADOBE, MT.W_SAND, MT.W_CASTLE,
        MT.W_MARBLE, MT.W_DARK, MT.W_MOSS, MT.W_OBSIDIAN, MT.W_ICE, MT.W_HEDGE
    )
    val floorChoices = listOf(
        MT.F_PLANKS, MT.F_PARQUET, MT.F_STONE, MT.F_BRICK, MT.F_TILES, MT.F_MARBLE, MT.F_CARPET, MT.F_CARPET_B, MT.F_CARPET_G,
        MT.F_CHECKER, MT.F_COBBLE, MT.F_DARK, MT.F_DIRT, MT.F_SAND, MT.F_RUNE
    )

    val doorNames = listOf("Madeira", "Ferro", "Portão", "Arco", "Nenhuma")
    private val doorIds = listOf(MT.W_DOOR, MT.W_DOOR_IRON, MT.W_GATE_CASTLE, MT.W_ARCH, 0)
    val windowNames = listOf("Comum", "Redonda", "Vitral", "Grade", "Nenhuma")
    private val windowIds = listOf(MT.W_WINDOW, MT.W_WINDOW_ROUND, MT.W_STAINED, MT.W_BARRED, 0)
    val sideNames = listOf("Sul", "Norte", "Leste", "Oeste")
    val furnNames = listOf("Nenhum", "Quarto", "Sala", "Cozinha", "Biblioteca", "Banheiro", "Taverna", "Armazém", "Música", "Ferraria")

    private val dxs = intArrayOf(0, 0, 1, -1)
    private val dys = intArrayOf(1, -1, 0, 0)

    /** Constrói na área (x0,y0)-(x1,y1). Devolve a mensagem para mostrar ao jogador. */
    fun build(m: MapData, ax: Int, ay: Int, bx: Int, by: Int, o: BuildOpts): String {
        val x0 = max(0, min(ax, bx))
        val x1 = min(m.w - 1, max(ax, bx))
        val y0 = max(0, min(ay, by))
        val y1 = min(m.h - 1, max(ay, by))
        val w = x1 - x0 + 1
        val h = y1 - y0 + 1
        if (w < 3 || h < 3) return "Área pequena: arraste pelo menos 3×3"
        val storeys = o.storeys.coerceIn(1, 4)
        val cap = if (storeys > 1) o.cap else 0
        val side = o.side.coerceIn(0, 3)
        val doorId = doorIds[o.door.coerceIn(0, doorIds.size - 1)]
        val winId = windowIds[o.window.coerceIn(0, windowIds.size - 1)]

        // porta no meio do lado escolhido
        val dX = when (side) { 2 -> x1; 3 -> x0; else -> (x0 + x1) / 2 }
        val dY = when (side) { 0 -> y1; 1 -> y0; else -> (y0 + y1) / 2 }

        fun wall(x: Int, y: Int, id: Int, hg: Int = storeys, cp: Int = cap) {
            val i = y * m.w + x
            m.obj[i] = id
            m.hgt[i] = hg
            m.cap[i] = if (hg > 1) cp else 0
        }

        // limpa a área e remove entidades dentro dela
        for (y in y0..y1) for (x in x0..x1) {
            val i = y * m.w + x
            m.obj[i] = 0
            m.hgt[i] = 0
            m.cap[i] = 0
            m.bridge[i] = 0
            m.floor[i] = o.floor
        }
        m.ents.removeAll { it.x in x0..x1 && it.y in y0..y1 }

        // paredes
        for (y in y0..y1) for (x in x0..x1) {
            val edge = x == x0 || x == x1 || y == y0 || y == y1
            if (edge || o.solid) wall(x, y, o.wall)
        }

        // janelas: nas paredes, longe dos cantos e da porta, de duas em duas células
        if (winId != 0) {
            for (y in y0..y1) for (x in x0..x1) {
                val onX = y == y0 || y == y1
                val onY = x == x0 || x == x1
                if (!(onX || onY)) continue
                if (onX && onY) continue // canto
                if (o.solid && !((side == 0 && y == y1) || (side == 1 && y == y0) || (side == 2 && x == x1) || (side == 3 && x == x0))) continue
                val pos = if (onX) x - x0 else y - y0
                val len = if (onX) w else h
                if (pos < 1 || pos > len - 2) continue
                if (kotlin.math.abs(x - dX) + kotlin.math.abs(y - dY) <= 1) continue
                if (pos % 2 == 1) wall(x, y, winId)
            }
        }

        // porta
        wall(dX, dY, doorId, if (doorId == 0) 0 else storeys)

        // entrada: caminho e lampiões
        if (o.porch) {
            for (k in 1..2) {
                val px = dX + dxs[side] * k
                val py = dY + dys[side] * k
                if (!m.inB(px, py)) continue
                val i = py * m.w + px
                m.floor[i] = MT.F_COBBLE
                if (MT.isWall(m.obj[i])) continue
                m.obj[i] = 0
                if (k == 1) {
                    for (s in intArrayOf(-1, 1)) {
                        val lx = px + (if (side < 2) s else 0)
                        val ly = py + (if (side < 2) 0 else s)
                        if (m.inB(lx, ly) && m.obj[ly * m.w + lx] == 0) m.obj[ly * m.w + lx] = if (s < 0) MT.P_LAMP else MT.P_VASE
                    }
                }
            }
        }

        // construção maciça: chaminé quando tem telhado
        if (o.solid) {
            if (cap != 0 && w >= 4 && h >= 3 && cap != MT.W_CHIMNEY) {
                val cx = x1 - 1
                val cy = if (side == 1) y1 else y0
                wall(cx, cy, o.wall, min(4, storeys + 1), MT.W_CHIMNEY)
            }
            return "Construção maciça $w×$h pronta (porta ao ${sideNames[side].lowercase()})"
        }

        // interior: forma uma grade (profundidade a partir do fundo, lateral da esquerda para a direita)
        val ix0 = x0 + 1
        val ix1 = x1 - 1
        val iy0 = y0 + 1
        val iy1 = y1 - 1
        val iw = ix1 - ix0 + 1
        val ih = iy1 - iy0 + 1
        val depth = if (side < 2) ih else iw
        val wide = if (side < 2) iw else ih
        if (o.furn != 0 && depth > 0 && wide > 0) {
            val doorLane = when (side) {
                0 -> dX - ix0
                1 -> ix1 - dX
                else -> dY - iy0
            }
            val used = HashSet<Int>()
            fun cellOf(d: Int, l: Int): Pair<Int, Int> = when (side) {
                0 -> (ix0 + l) to (iy0 + d)
                1 -> (ix1 - l) to (iy1 - d)
                2 -> (ix0 + d) to (iy0 + l)
                else -> (ix1 - d) to (iy0 + l)
            }
            fun put(d: Int, l: Int, id: Int) {
                if (d !in 0 until depth || l !in 0 until wide) return
                if (d == depth - 1 && l == doorLane) return
                val (x, y) = cellOf(d, l)
                val i = y * m.w + x
                if (!used.add(i)) return
                m.obj[i] = id
            }
            fun floorAt(d: Int, l: Int, f: Int) {
                if (d !in 0 until depth || l !in 0 until wide) return
                val (x, y) = cellOf(d, l)
                m.floor[y * m.w + x] = f
            }
            fun rug(f: Int) {
                if (depth >= 4 && wide >= 4) for (d in 1 until depth - 1) for (l in 1 until wide - 1) floorAt(d, l, f)
            }
            val r = wide - 1
            val mid = wide / 2
            val dm = depth / 2
            when (o.furn) {
                1 -> { // quarto
                    rug(MT.F_CARPET)
                    put(0, 0, if (wide >= 4) MT.P_BIGBED else MT.P_BED)
                    put(0, r, MT.P_WARDROBE)
                    if (wide >= 4) put(0, 1, MT.P_FLOORLAMP)
                    if (wide >= 5) put(0, r - 1, MT.P_DRESSER)
                    if (depth >= 3) put(1, r, MT.P_MIRROR)
                    put(depth - 1, 0, MT.P_PLANTPOT)
                    if (depth >= 4 && wide >= 5) { put(dm, r, MT.P_TABLE); put(dm, r - 1, MT.P_CHAIR) }
                    if (depth >= 3) put(depth - 1, r, MT.P_VASE)
                }
                2 -> { // sala
                    rug(MT.F_CARPET_B)
                    put(0, mid, MT.P_FIREPLACE)
                    if (wide >= 5) { put(0, 0, MT.P_SHELF); put(0, r, MT.P_SHELF) }
                    put(1, 0, MT.P_FLOORLAMP)
                    put(1, r, MT.P_PLANTPOT)
                    if (depth >= 3) put(if (depth >= 4) 2 else 1, max(1, mid - 1), MT.P_SOFA)
                    if (depth >= 4 && wide >= 4) put(2, min(r - 1, mid + 1), MT.P_TABLE)
                    put(depth - 1, 0, MT.P_CLOCK)
                    put(depth - 1, r, MT.P_AQUARIUM)
                }
                3 -> { // cozinha
                    put(0, 0, MT.P_COUNTER)
                    if (wide >= 3) put(0, 1, MT.P_COUNTER)
                    if (wide >= 4) put(0, 2, MT.P_SINK)
                    put(0, r, MT.P_STOVE)
                    if (wide >= 5) put(0, r - 1, MT.P_CAULDRON)
                    if (depth >= 4 && wide >= 3) {
                        put(2, mid, MT.P_TABLE)
                        if (wide >= 5) { put(2, mid - 1, MT.P_STOOL); put(2, mid + 1, MT.P_STOOL) }
                    }
                    put(depth - 1, 0, MT.P_SACKS)
                    put(depth - 1, r, MT.P_BARREL)
                    if (depth >= 3) put(1, r, MT.P_FIREWOOD)
                }
                4 -> { // biblioteca
                    for (l in 0..r) put(0, l, MT.P_SHELF)
                    for (d in 1 until depth - 1) { put(d, 0, MT.P_SHELF); put(d, r, MT.P_SHELF) }
                    if (depth >= 4 && wide >= 4) {
                        rug(MT.F_CARPET_G)
                        put(dm, mid, MT.P_DESK)
                        put(min(depth - 2, dm + 1), mid, MT.P_CHAIR)
                    }
                    put(depth - 1, 0, MT.P_FLOORLAMP)
                    put(depth - 1, r, MT.P_PLANTPOT)
                }
                5 -> { // banheiro
                    for (d in 0 until depth) for (l in 0 until wide) floorAt(d, l, MT.F_TILES)
                    put(0, 0, MT.P_BATHTUB)
                    if (wide >= 2) put(0, 1, MT.P_SINK)
                    if (wide >= 3) put(0, r, MT.P_MIRROR)
                    if (depth >= 2) put(1, 0, MT.P_STOOL)
                    if (depth >= 3 && wide >= 3) put(1, r, MT.P_PLANTPOT)
                    put(depth - 1, 0, MT.P_BARREL)
                }
                6 -> { // taverna
                    for (l in 0 until wide) if (l != mid || wide < 5) put(0, l, if (l == 0 || l == r) MT.P_KEG else MT.P_COUNTER)
                    if (depth >= 3) {
                        put(1, 0, MT.P_BARREL); put(1, r, MT.P_BARREL)
                    }
                    if (depth >= 4 && wide >= 3) {
                        val td = min(depth - 2, 2)
                        put(td, 1, MT.P_TABLE); put(td, 0, MT.P_STOOL); put(td, 2, MT.P_STOOL)
                        if (wide >= 6) { put(td, r - 1, MT.P_TABLE); put(td, r, MT.P_STOOL); put(td, r - 2, MT.P_STOOL) }
                    }
                    put(depth - 1, 0, MT.P_BENCH)
                    put(depth - 1, r, MT.P_LANTERN)
                    if (depth >= 5) put(dm + 1, mid, MT.P_CHANDELIER)
                }
                7 -> { // armazém
                    for (l in 0..r) put(0, l, if (l % 2 == 0) MT.P_CRATE else MT.P_BARREL)
                    for (d in 1 until depth) {
                        put(d, 0, if (d % 2 == 1) MT.P_SACKS else MT.P_CRATE)
                        put(d, r, if (d % 2 == 1) MT.P_KEG else MT.P_HAY)
                    }
                    if (depth >= 4 && wide >= 5) { put(dm, mid, MT.P_CRATE); put(dm, mid - 1, MT.P_SACKS) }
                }
                8 -> { // música
                    rug(MT.F_CARPET)
                    put(0, 0, MT.P_FLOORLAMP)
                    if (wide >= 3) put(0, 1, MT.P_PIANO)
                    if (wide >= 5) put(0, r - 1, MT.P_HARP)
                    put(0, r, MT.P_FLOORLAMP)
                    if (depth >= 2 && wide >= 3) put(1, 1, MT.P_STOOL)
                    if (depth >= 4) { put(dm + 1, 0, MT.P_CANDLES); put(dm + 1, r, MT.P_CANDLES) }
                    put(depth - 1, 0, MT.P_PLANTPOT)
                    put(depth - 1, r, MT.P_VASE)
                }
                9 -> { // ferraria
                    put(0, 0, MT.P_ANVIL)
                    if (wide >= 3) put(0, 1, MT.P_FORGE)
                    if (wide >= 5) { put(0, r - 1, MT.P_WEAPONRACK) }
                    put(0, r, MT.P_ARMORSTAND)
                    if (depth >= 4 && wide >= 4) { put(dm, r, MT.P_DUMMY); put(dm, 0, MT.P_FIREWOOD) }
                    put(depth - 1, 0, MT.P_BARREL)
                    put(depth - 1, r, MT.P_CRATE)
                }
            }
        }
        val kind = furnNames[o.furn.coerceIn(0, furnNames.size - 1)]
        return "Cômodo $w×$h construído" + (if (o.furn != 0) " ($kind)" else "") + ", porta ao " + sideNames[side].lowercase()
    }
}
