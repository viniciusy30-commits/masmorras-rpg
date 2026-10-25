package com.viniciusy.masmorras

/** Casa pronta para carimbar no editor. Valores -1 = não mexe naquela célula. */
class Template(val w: Int, val h: Int) {
    val floor = IntArray(w * h) { -1 }
    val obj = IntArray(w * h) { -1 }
    val hgt = IntArray(w * h)
    val cap = IntArray(w * h)

    fun fl(x: Int, y: Int, v: Int) { if (x in 0 until w && y in 0 until h) floor[y * w + x] = v }
    fun ob(x: Int, y: Int, v: Int) { if (x in 0 until w && y in 0 until h) obj[y * w + x] = v }
    /** Parede com andares: altura 1..4 e material dos andares de cima. */
    fun wl(x: Int, y: Int, v: Int, hg: Int, cp: Int = 0) { ob(x, y, v); if (x in 0 until w && y in 0 until h) { hgt[y * w + x] = hg; cap[y * w + x] = cp } }
}

class HouseDef(val id: Int, val name: String, val build: () -> Template)

object Houses {
    private val cache = HashMap<Int, Template>()

    /** Id especial: a área copiada no editor. */
    const val CLIP_ID = 9999
    var clipboard: Template? = null

    /** Casa aberta (dá para entrar): paredes em volta, porta e janelas na frente, piso e móveis. */
    private fun open(w: Int, h: Int, wall: Int, floorId: Int, furnish: (Template, Int) -> Unit): Template {
        val t = Template(w, h + 1)
        val door = w / 2
        for (y in 0 until h) for (x in 0 until w) {
            val edge = x == 0 || y == 0 || x == w - 1 || y == h - 1
            t.fl(x, y, floorId)
            t.ob(x, y, if (edge) wall else 0)
        }
        // janelas (frente e fundo, longe dos cantos e da porta)
        if (w >= 6) {
            t.ob(1, h - 1, MT.W_WINDOW); t.ob(w - 2, h - 1, MT.W_WINDOW)
        }
        if (w >= 7) { t.ob(2, 0, MT.W_WINDOW); t.ob(w - 3, 0, MT.W_WINDOW) }
        if (h >= 5) { t.ob(0, h / 2, MT.W_WINDOW); t.ob(w - 1, h / 2, MT.W_WINDOW) }
        t.ob(door, h - 1, MT.W_DOOR)
        // varanda: caminho de terra e enfeites
        for (x in 0 until w) t.ob(x, h, 0)
        t.fl(door, h, MT.F_DIRT)
        t.ob(door - 1, h, MT.P_VASE)
        t.ob(door + 1, h, MT.P_LAMP)
        furnish(t, door)
        return t
    }

    /** Casa fechada (cenário): telhado em cima e fachada com porta e janelas. */
    private fun closed(w: Int, h: Int, roof: Int, wall: Int): Template {
        val t = Template(w, h + 1)
        val door = w / 2
        for (y in 0 until h - 1) for (x in 0 until w) t.ob(x, y, roof)
        for (x in 0 until w) t.ob(x, h - 1, if (x % 2 == 1 && x != door - 1 && x != door + 1 && x != 0 && x != w - 1) MT.W_WINDOW else wall)
        t.ob(door, h - 1, MT.W_DOOR)
        for (x in 0 until w) t.ob(x, h, 0)
        t.fl(door, h, MT.F_DIRT)
        t.ob(door - 1, h, MT.P_VASE)
        t.ob(door + 1, h, MT.P_BARREL)
        return t
    }

    /** Casa maciça com 2 andares: fachada embaixo e telhado em cima, com chaminé. Cenário. */
    private fun solidHouse(w: Int, h: Int, wall: Int, roof: Int, win: Int): Template {
        val t = Template(w, h + 1)
        val door = w / 2
        for (y in 0 until h) for (x in 0 until w) t.wl(x, y, wall, 2, roof)
        for (x in 1 until w - 1) if (x % 2 == 1 && x != door - 1 && x != door + 1) t.wl(x, h - 1, win, 2, roof)
        t.wl(door, h - 1, MT.W_DOOR, 2, roof)
        t.wl(w - 2, 0, wall, 3, MT.W_CHIMNEY)
        for (x in 0 until w) t.ob(x, h, 0)
        t.fl(door, h, MT.F_DIRT)
        t.ob(door - 1, h, MT.P_VASE)
        t.ob(door + 1, h, MT.P_BARREL)
        return t
    }

    /** Castelo pequeno: muralha de 2 andares, torres de 3, portão levadiço e salão do trono. */
    private fun castle(w: Int, h: Int): Template {
        val t = Template(w, h + 1)
        val door = w / 2
        for (y in 0 until h) for (x in 0 until w) {
            val edge = x == 0 || y == 0 || x == w - 1 || y == h - 1
            t.fl(x, y, if (x in 2..w - 3 && y in 2..h - 3) MT.F_CHECKER else MT.F_STONE)
            t.ob(x, y, 0)
            if (edge) t.wl(x, y, MT.W_CASTLE, 2, MT.W_CASTLE)
        }
        for ((cx, cy) in listOf(0 to 0, w - 1 to 0, 0 to h - 1, w - 1 to h - 1)) t.wl(cx, cy, MT.W_CASTLE, 3, MT.W_MARBLE)
        t.wl(door, h - 1, MT.W_GATE_CASTLE, 2, MT.W_CASTLE)
        t.wl(1, h - 1, MT.W_BARRED, 2, MT.W_CASTLE); t.wl(w - 2, h - 1, MT.W_BARRED, 2, MT.W_CASTLE)
        t.wl(door, 0, MT.W_STAINED, 2, MT.W_CASTLE)
        for (x in 3..w - 4) t.fl(x, 1, MT.F_CARPET_B)
        for (y in 1 until h - 1) t.fl(door, y, MT.F_CARPET_B)
        t.ob(door, 1, MT.P_THRONE)
        t.ob(door - 2, 1, MT.P_BANNER); t.ob(door + 2, 1, MT.P_BANNER)
        t.ob(2, 2, MT.P_TORCH); t.ob(w - 3, 2, MT.P_TORCH)
        t.ob(2, h - 3, MT.P_ARMORSTAND); t.ob(w - 3, h - 3, MT.P_ARMORSTAND)
        t.ob(3, 3, MT.P_WEAPONRACK); t.ob(w - 4, 3, MT.P_CHANDELIER)
        for (x in 0 until w) t.ob(x, h, 0)
        t.fl(door, h, MT.F_COBBLE)
        return t
    }

    /** Muralha com portão levadiço e duas torres. */
    private fun gatehouse(w: Int): Template {
        val t = Template(w, 2)
        val door = w / 2
        for (x in 0 until w) t.wl(x, 0, MT.W_CASTLE, 2, MT.W_CASTLE)
        t.wl(0, 0, MT.W_CASTLE, 3, MT.W_MARBLE); t.wl(w - 1, 0, MT.W_CASTLE, 3, MT.W_MARBLE)
        t.wl(door, 0, MT.W_GATE_CASTLE, 2, MT.W_CASTLE)
        t.ob(door - 1, 1, MT.P_TORCH); t.ob(door + 1, 1, MT.P_TORCH)
        t.fl(door, 0, MT.F_COBBLE)
        return t
    }

    val list: List<HouseDef> = listOf(
        HouseDef(0, "Casinha de toras") {
            open(6, 5, MT.W_LOG, MT.F_PLANKS) { t, _ ->
                t.ob(1, 1, MT.P_BED); t.ob(2, 1, MT.P_WARDROBE); t.ob(4, 1, MT.P_FIREPLACE)
                t.ob(4, 3, MT.P_TABLE); t.ob(4, 2, MT.P_CHAIR); t.ob(1, 3, MT.P_VASE)
            }
        },
        HouseDef(1, "Casa de reboco") {
            open(7, 5, MT.W_HOUSE, MT.F_PLANKS) { t, _ ->
                t.ob(1, 1, MT.P_BED); t.ob(2, 1, MT.P_SHELF); t.ob(5, 1, MT.P_FIREPLACE)
                t.ob(5, 3, MT.P_TABLE); t.ob(4, 3, MT.P_CHAIR); t.ob(1, 3, MT.P_VASE)
            }
        },
        HouseDef(2, "Casa de pedra") {
            open(6, 5, MT.W_STONE, MT.F_STONE) { t, _ ->
                t.ob(1, 1, MT.P_BED); t.ob(4, 1, MT.P_SHELF); t.ob(1, 3, MT.P_BARREL); t.ob(2, 3, MT.P_CRATE); t.ob(4, 3, MT.P_CHEST)
            }
        },
        HouseDef(3, "Casa de tijolo") {
            open(7, 6, MT.W_BRICK, MT.F_TILES) { t, _ ->
                t.ob(1, 1, MT.P_BED); t.ob(2, 1, MT.P_WARDROBE); t.ob(5, 1, MT.P_FIREPLACE)
                t.ob(5, 3, MT.P_TABLE); t.ob(4, 3, MT.P_CHAIR); t.ob(1, 3, MT.P_SHELF); t.ob(1, 4, MT.P_SACKS)
            }
        },
        HouseDef(4, "Mansão") {
            open(11, 8, MT.W_HOUSE, MT.F_PARQUET) { t, door ->
                for (y in 3..5) for (x in 3..7) t.fl(x, y, MT.F_CARPET)
                for (x in 1..2) t.ob(x, 1, MT.P_SHELF)
                for (x in 8..9) t.ob(x, 1, MT.P_SHELF)
                t.ob(door, 1, MT.P_FIREPLACE); t.ob(3, 1, MT.P_BANNER); t.ob(7, 1, MT.P_BANNER)
                t.ob(1, 5, MT.P_STATUE); t.ob(9, 5, MT.P_STATUE)
                t.ob(3, 4, MT.P_TABLE); t.ob(2, 4, MT.P_CHAIR); t.ob(4, 4, MT.P_CHAIR)
                t.ob(7, 4, MT.P_CANDLES); t.ob(9, 3, MT.P_BED); t.ob(9, 2, MT.P_WARDROBE)
            }
        },
        HouseDef(5, "Taverna") {
            open(10, 7, MT.W_LOG, MT.F_PLANKS) { t, _ ->
                for (x in 1..3) t.ob(x, 1, MT.P_BARREL)
                t.ob(8, 1, MT.P_FIREPLACE); t.ob(4, 1, MT.P_TORCH); t.ob(6, 1, MT.P_TORCH)
                t.ob(2, 3, MT.P_TABLE); t.ob(1, 3, MT.P_CHAIR); t.ob(3, 3, MT.P_CHAIR)
                t.ob(2, 5, MT.P_TABLE); t.ob(1, 5, MT.P_CHAIR); t.ob(3, 5, MT.P_CHAIR)
                t.ob(7, 3, MT.P_TABLE); t.ob(8, 3, MT.P_CHAIR); t.ob(7, 5, MT.P_TABLE); t.ob(8, 5, MT.P_CHAIR)
            }
        },
        HouseDef(6, "Ferraria") {
            open(7, 6, MT.W_STONE, MT.F_COBBLE) { t, _ ->
                t.ob(1, 1, MT.P_FORGE); t.ob(2, 3, MT.P_ANVIL); t.ob(4, 1, MT.P_FIREWOOD)
                t.ob(5, 1, MT.P_BARREL); t.ob(5, 3, MT.P_SACKS); t.ob(5, 4, MT.P_CRATE); t.ob(1, 4, MT.P_BARREL)
            }
        },
        HouseDef(7, "Celeiro") {
            open(9, 6, MT.W_WOOD, MT.F_DIRT) { t, _ ->
                t.ob(1, 1, MT.P_HAY); t.ob(2, 1, MT.P_HAY); t.ob(1, 2, MT.P_HAY)
                t.ob(6, 1, MT.P_CRATE); t.ob(7, 1, MT.P_SACKS); t.ob(7, 2, MT.P_SACKS); t.ob(7, 4, MT.P_BARREL); t.ob(1, 4, MT.P_FIREWOOD)
            }
        },
        HouseDef(8, "Torre") {
            open(5, 5, MT.W_DARK, MT.F_DARK) { t, _ ->
                t.ob(1, 1, MT.P_BRAZIER); t.ob(3, 1, MT.P_BRAZIER); t.ob(2, 1, MT.P_ALTAR); t.ob(1, 3, MT.P_CHEST)
            }
        },
        HouseDef(9, "Cabana telhado vermelho") { closed(6, 4, MT.W_ROOF_RED, MT.W_HOUSE) },
        HouseDef(10, "Cabana telhado ardósia") { closed(6, 4, MT.W_ROOF_SLATE, MT.W_STONE) },
        HouseDef(11, "Cabana de palha") { closed(5, 4, MT.W_THATCH, MT.W_LOG) },
        HouseDef(12, "Casa grande telhada") { closed(9, 5, MT.W_ROOF_RED, MT.W_HOUSE) },
        HouseDef(13, "Casa 2 andares telha vermelha") { solidHouse(7, 5, MT.W_HOUSE, MT.W_ROOF_RED, MT.W_WINDOW) },
        HouseDef(14, "Casa enxaimel telhado verde") { solidHouse(6, 4, MT.W_TIMBER, MT.W_ROOF_GREEN, MT.W_WINDOW_ROUND) },
        HouseDef(15, "Casa de pedra telha azul") { solidHouse(8, 5, MT.W_STONE, MT.W_ROOF_BLUE, MT.W_STAINED) },
        HouseDef(16, "Casa de adobe") { solidHouse(6, 4, MT.W_ADOBE, MT.W_THATCH, MT.W_WINDOW) },
        HouseDef(17, "Castelo pequeno") { castle(11, 9) },
        HouseDef(18, "Portão de muralha") { gatehouse(7) },
        HouseDef(19, "Mausoléu") {
            open(7, 6, MT.W_MARBLE, MT.F_MARBLE) { t, _ ->
                t.ob(2, 1, MT.P_SARCOPHAGUS); t.ob(4, 1, MT.P_SARCOPHAGUS); t.ob(1, 1, MT.P_CANDLES); t.ob(5, 1, MT.P_CANDLES)
                t.ob(3, 3, MT.P_ALTAR); t.fl(3, 4, MT.F_RUNE); t.ob(1, 4, MT.P_GARGOYLE); t.ob(5, 4, MT.P_GARGOYLE)
            }
        }
    )

    fun template(id: Int): Template = if (id == CLIP_ID && clipboard != null) clipboard!! else cache.getOrPut(id) { (list.firstOrNull { it.id == id } ?: list[0]).build() }
}
