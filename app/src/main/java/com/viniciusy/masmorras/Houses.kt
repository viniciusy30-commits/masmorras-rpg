package com.viniciusy.masmorras

/** Casa pronta para carimbar no editor. Valores -1 = não mexe naquela célula. */
class Template(val w: Int, val h: Int) {
    val floor = IntArray(w * h) { -1 }
    val obj = IntArray(w * h) { -1 }

    fun fl(x: Int, y: Int, v: Int) { if (x in 0 until w && y in 0 until h) floor[y * w + x] = v }
    fun ob(x: Int, y: Int, v: Int) { if (x in 0 until w && y in 0 until h) obj[y * w + x] = v }
}

class HouseDef(val id: Int, val name: String, val build: () -> Template)

object Houses {
    private val cache = HashMap<Int, Template>()

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
        HouseDef(12, "Casa grande telhada") { closed(9, 5, MT.W_ROOF_RED, MT.W_HOUSE) }
    )

    fun template(id: Int): Template = cache.getOrPut(id) { (list.firstOrNull { it.id == id } ?: list[0]).build() }
}
