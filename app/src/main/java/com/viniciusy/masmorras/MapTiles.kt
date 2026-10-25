package com.viniciusy.masmorras

import android.content.Context
import android.content.res.AssetManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
import kotlin.math.abs

/**
 * Definição de todos os blocos que o editor oferece.
 * Camada "chão" (floor): ids 0..30.  Camada "objetos" (obj): paredes 1..19, props 20+.
 */
object MT {
    // chão
    const val F_VOID = 0
    const val F_STONE = 1
    const val F_BRICK = 2
    const val F_DARK = 3
    const val F_MOSS = 4
    const val F_GRASS = 5
    const val F_DIRT = 6
    const val F_SAND = 7
    const val F_PLANKS = 8
    const val F_CARPET = 9
    const val F_WATER = 10
    const val F_LAVA = 11
    const val F_COBBLE = 12
    const val F_MARBLE = 13
    const val F_SNOW = 14
    const val F_ICE = 15
    const val F_MUD = 16
    const val F_PARQUET = 17
    const val F_BASALT = 18
    const val F_GRAVEL = 19
    const val F_FLOWERY = 20
    const val F_TILES = 21
    const val F_SLIME = 22
    const val F_BRIDGE = 23
    const val F_FARM = 24
    const val F_ASH = 25
    const val F_DRY = 26
    const val F_CHECKER = 27
    const val F_CARPET_B = 28
    const val F_CARPET_G = 29
    const val F_RUNE = 30

    // paredes (altas, sólidas — a porta é atravessável)
    const val W_STONE = 1
    const val W_BRICK = 2
    const val W_DARK = 3
    const val W_MOSS = 4
    const val W_WOOD = 5
    const val W_SAND = 6
    const val W_HOUSE = 7
    const val W_LOG = 8
    const val W_ROOF_RED = 9
    const val W_ROOF_SLATE = 10
    const val W_THATCH = 11
    const val W_WINDOW = 12
    const val W_DOOR = 13
    const val W_GATE = 14
    const val W_OBSIDIAN = 15
    const val W_ICE = 16
    const val W_HEDGE = 17

    // paredes novas (ids altos, para não colidir com os objetos)
    const val W_TIMBER = 110
    const val W_ADOBE = 111
    const val W_CASTLE = 112
    const val W_MARBLE = 113
    const val W_WINDOW_ROUND = 114
    const val W_STAINED = 115
    const val W_BARRED = 116
    const val W_DOOR_IRON = 117
    const val W_GATE_CASTLE = 118
    const val W_ROOF_GREEN = 119
    const val W_ROOF_BLUE = 120
    const val W_ROOF_SHINGLE = 121
    const val W_CHIMNEY = 122
    const val W_ARCH = 123
    const val W_ROOF_PLUM = 124

    // props
    const val P_CRATE = 20
    const val P_BARREL = 21
    const val P_PILLAR = 22
    const val P_BRAZIER = 23
    const val P_ROCK = 24
    const val P_TREE = 25
    const val P_BONES = 26
    const val P_TUFT = 27
    const val P_CHEST = 28
    const val P_TORCH = 29
    const val P_CAMPFIRE = 30
    const val P_FOUNTAIN = 31
    const val P_BANNER = 32
    const val P_CRYSTAL_B = 33
    const val P_CRYSTAL_R = 34
    const val P_MUSHROOM = 35
    const val P_CAULDRON = 36
    const val P_CANDLES = 37
    const val P_LAMP = 38
    const val P_PORTAL = 39
    const val P_ALTAR = 40
    const val P_STATUE = 41
    const val P_TOMB = 42
    const val P_WELL = 43
    const val P_SIGN = 44
    const val P_HAY = 45
    const val P_BUSH = 46
    const val P_PINE = 47
    const val P_DEADTREE = 48
    const val P_FENCE = 49
    const val P_ANVIL = 50
    const val P_FORGE = 51
    const val P_BED = 52
    const val P_TABLE = 53
    const val P_CHAIR = 54
    const val P_SHELF = 55
    const val P_WARDROBE = 56
    const val P_FIREPLACE = 57
    const val P_WEB = 58
    const val P_SKULLS = 59
    const val P_FIREWOOD = 60
    const val P_SACKS = 61
    const val P_VASE = 62
    const val P_BIRCH = 63
    const val P_AUTUMN = 64
    const val P_CHERRY = 65
    const val P_PALM = 66
    const val P_CACTUS = 67
    const val P_TALLGRASS = 68
    const val P_REEDS = 69
    const val P_LILYPAD = 70
    const val P_BIGMUSH = 71
    const val P_LOG = 72
    const val P_STUMP = 73
    const val P_BOULDER = 74
    const val P_SNOWMAN = 75
    const val P_SNOWPINE = 76
    const val P_PUMPKINS = 77
    const val P_SUNFLOWERS = 78
    const val P_WHEAT = 79
    const val P_TENT = 80
    const val P_STALL = 81
    const val P_CART = 82
    const val P_BENCH = 83
    const val P_STOVE = 84
    const val P_THRONE = 85
    const val P_CHANDELIER = 86
    const val P_WEAPONRACK = 87
    const val P_ARMORSTAND = 88
    const val P_DUMMY = 89
    const val P_SARCOPHAGUS = 90
    const val P_CAGE = 91
    const val P_SPIKETRAP = 92
    const val P_GARGOYLE = 93
    const val P_TREASURE = 94
    const val P_ALCHEMY = 95
    const val P_CRYSTALBALL = 96
    const val P_MAGICCIRCLE = 97
    const val P_LANTERN = 98
    const val P_SCARECROW = 99
    const val P_KEG = 100
    const val P_TOTEM = 101

    /** file = nome em assets/tiles/<file>.png. Texturas animadas são tiras horizontais com `frames` quadros. */
    class Def(val id: Int, val name: String, val file: String, val vary: Boolean = false, val frames: Int = 1, val fps: Float = 8f)

    val floors = listOf(
        Def(F_VOID, "Vazio", "floor_void"),
        Def(F_STONE, "Laje", "floor_stone"),
        Def(F_BRICK, "Tijolo", "floor_brick"),
        Def(F_DARK, "Pedra escura", "floor_dark"),
        Def(F_MOSS, "Pedra musgo", "floor_moss"),
        Def(F_GRASS, "Grama", "floor_grass", true),
        Def(F_FLOWERY, "Grama florida", "floor_flowery", true),
        Def(F_DIRT, "Terra", "floor_dirt", true),
        Def(F_SAND, "Areia", "floor_sand", true),
        Def(F_GRAVEL, "Cascalho", "floor_gravel", true),
        Def(F_COBBLE, "Pedregulho", "floor_cobble"),
        Def(F_PLANKS, "Tábuas", "floor_planks"),
        Def(F_PARQUET, "Parquet", "floor_parquet"),
        Def(F_TILES, "Ladrilho", "floor_ladrilho"),
        Def(F_MARBLE, "Mármore", "floor_marble"),
        Def(F_CARPET, "Tapete", "floor_carpet"),
        Def(F_SNOW, "Neve", "floor_snow", true),
        Def(F_ICE, "Gelo", "floor_ice"),
        Def(F_MUD, "Lama", "floor_mud", true),
        Def(F_BASALT, "Basalto", "floor_basalt"),
        Def(F_WATER, "Água", "floor_water", false, 8, 6f),
        Def(F_LAVA, "Lava", "floor_lava", false, 8, 5f),
        Def(F_SLIME, "Lodo tóxico", "floor_slime", false, 8, 5f),
        Def(F_BRIDGE, "Ponte", "floor_bridge"),
        Def(F_FARM, "Terra arada", "floor_farm"),
        Def(F_ASH, "Cinzas", "floor_ash"),
        Def(F_DRY, "Capim seco", "floor_dry", true),
        Def(F_CHECKER, "Xadrez", "floor_checker"),
        Def(F_CARPET_B, "Tapete azul", "floor_carpet_b"),
        Def(F_CARPET_G, "Tapete verde", "floor_carpet_g"),
        Def(F_RUNE, "Piso rúnico", "floor_rune", false, 8, 6f)
    )

    val walls = listOf(
        Def(W_STONE, "Parede pedra", "wall_stone"),
        Def(W_BRICK, "Parede tijolo", "wall_brick"),
        Def(W_DARK, "Parede escura", "wall_dark"),
        Def(W_MOSS, "Parede musgo", "wall_moss"),
        Def(W_WOOD, "Parede madeira", "wall_wood"),
        Def(W_SAND, "Arenito", "wall_sand"),
        Def(W_HOUSE, "Parede casa", "wall_house"),
        Def(W_LOG, "Toras", "wall_log"),
        Def(W_WINDOW, "Janela", "wall_window"),
        Def(W_DOOR, "Porta", "wall_door"),
        Def(W_ROOF_RED, "Telhado vermelho", "wall_roof_red"),
        Def(W_ROOF_SLATE, "Telhado ardósia", "wall_roof_slate"),
        Def(W_THATCH, "Telhado palha", "wall_thatch"),
        Def(W_GATE, "Grade", "wall_gate"),
        Def(W_OBSIDIAN, "Obsidiana", "wall_obsidian"),
        Def(W_ICE, "Parede gelo", "wall_ice"),
        Def(W_HEDGE, "Sebe", "wall_hedge"),
        Def(W_TIMBER, "Enxaimel", "wall_timber"),
        Def(W_ADOBE, "Adobe", "wall_adobe"),
        Def(W_CASTLE, "Muralha", "wall_castle"),
        Def(W_MARBLE, "Mármore", "wall_marble"),
        Def(W_WINDOW_ROUND, "Janela redonda", "wall_window_round"),
        Def(W_STAINED, "Vitral", "wall_stained"),
        Def(W_BARRED, "Janela grade", "wall_barred"),
        Def(W_DOOR_IRON, "Porta de ferro", "wall_door_iron"),
        Def(W_GATE_CASTLE, "Portão levadiço", "wall_gate_castle"),
        Def(W_ARCH, "Arco", "wall_arch"),
        Def(W_ROOF_GREEN, "Telhado verde", "wall_roof_green"),
        Def(W_ROOF_BLUE, "Telhado azul", "wall_roof_blue"),
        Def(W_ROOF_SHINGLE, "Telhado madeira", "wall_roof_shingle"),
        Def(W_ROOF_PLUM, "Telhado ameixa", "wall_roof_plum"),
        Def(W_CHIMNEY, "Chaminé", "wall_chimney")
    )

    val props = listOf(
        Def(P_TREE, "Árvore", "prop_tree", false, 4, 2.5f),
        Def(P_PINE, "Pinheiro", "prop_pine", false, 4, 2.5f),
        Def(P_DEADTREE, "Árvore seca", "prop_deadtree", false, 4, 2f),
        Def(P_BUSH, "Arbusto", "prop_bush", false, 4, 2.5f),
        Def(P_TUFT, "Flores", "prop_tuft", false, 4, 2.5f),
        Def(P_ROCK, "Pedra", "prop_rock"),
        Def(P_FENCE, "Cerca", "prop_fence"),
        Def(P_HAY, "Feno", "prop_hay"),
        Def(P_WELL, "Poço", "prop_well", false, 4, 3f),
        Def(P_FOUNTAIN, "Fonte", "prop_fountain", false, 6, 8f),
        Def(P_SIGN, "Placa", "prop_sign"),
        Def(P_VASE, "Vaso", "prop_vase", false, 4, 2.5f),
        Def(P_TORCH, "Tocha", "prop_torch", false, 6, 10f),
        Def(P_LAMP, "Lampião", "prop_lamp", false, 6, 9f),
        Def(P_BRAZIER, "Braseiro", "prop_brazier", false, 6, 10f),
        Def(P_CAMPFIRE, "Fogueira", "prop_campfire", false, 6, 10f),
        Def(P_CANDLES, "Candelabro", "prop_candelabra", false, 6, 9f),
        Def(P_BANNER, "Estandarte", "prop_banner", false, 6, 6f),
        Def(P_CRYSTAL_B, "Cristal azul", "prop_crystal_blue", false, 6, 5f),
        Def(P_CRYSTAL_R, "Cristal rubi", "prop_crystal_red", false, 6, 5f),
        Def(P_MUSHROOM, "Cogumelos", "prop_mushrooms", false, 6, 5f),
        Def(P_PORTAL, "Portal", "prop_portal", false, 8, 9f),
        Def(P_ALTAR, "Altar rúnico", "prop_altar", false, 6, 5f),
        Def(P_CAULDRON, "Caldeirão", "prop_cauldron", false, 6, 8f),
        Def(P_STATUE, "Estátua", "prop_statue"),
        Def(P_PILLAR, "Pilar", "prop_pillar"),
        Def(P_TOMB, "Lápide", "prop_tomb"),
        Def(P_BONES, "Ossos", "prop_bones"),
        Def(P_SKULLS, "Caveiras", "prop_skulls"),
        Def(P_WEB, "Teia", "prop_web"),
        Def(P_CHEST, "Baú", "prop_chest", false, 6, 5f),
        Def(P_CRATE, "Caixote", "prop_crate"),
        Def(P_BARREL, "Barril", "prop_barrel"),
        Def(P_SACKS, "Sacos", "prop_sacks"),
        Def(P_FIREWOOD, "Lenha", "prop_firewood"),
        Def(P_ANVIL, "Bigorna", "prop_anvil", false, 4, 3f),
        Def(P_FORGE, "Forja", "prop_forge", false, 6, 10f),
        Def(P_BED, "Cama", "prop_bed"),
        Def(P_TABLE, "Mesa", "prop_table", false, 6, 9f),
        Def(P_CHAIR, "Cadeira", "prop_chair"),
        Def(P_SHELF, "Estante", "prop_bookshelf"),
        Def(P_WARDROBE, "Armário", "prop_wardrobe"),
        Def(P_FIREPLACE, "Lareira", "prop_fireplace", false, 6, 10f),
        Def(P_BIRCH, "Bétula", "prop_birch", false, 4, 3f),
        Def(P_AUTUMN, "Árvore de outono", "prop_autumn", false, 4, 3f),
        Def(P_CHERRY, "Cerejeira", "prop_cherry", false, 6, 4f),
        Def(P_PALM, "Palmeira", "prop_palm", false, 4, 3f),
        Def(P_CACTUS, "Cacto", "prop_cactus"),
        Def(P_TALLGRASS, "Capim alto", "prop_tallgrass", false, 4, 3f),
        Def(P_REEDS, "Juncos", "prop_reeds", false, 4, 3f),
        Def(P_LILYPAD, "Vitória-régia", "prop_lilypad", false, 4, 2f),
        Def(P_BIGMUSH, "Cogumelo gigante", "prop_bigmush"),
        Def(P_LOG, "Tronco", "prop_log"),
        Def(P_STUMP, "Toco", "prop_stump"),
        Def(P_BOULDER, "Rochedo", "prop_boulder"),
        Def(P_SNOWMAN, "Boneco de neve", "prop_snowman"),
        Def(P_SNOWPINE, "Pinheiro nevado", "prop_snowpine", false, 4, 3f),
        Def(P_PUMPKINS, "Abóboras", "prop_pumpkins"),
        Def(P_SUNFLOWERS, "Girassóis", "prop_sunflowers", false, 4, 3f),
        Def(P_WHEAT, "Trigo", "prop_wheat", false, 4, 3f),
        Def(P_TENT, "Tenda", "prop_tent"),
        Def(P_STALL, "Barraca", "prop_stall"),
        Def(P_CART, "Carroça", "prop_cart"),
        Def(P_BENCH, "Banco", "prop_bench"),
        Def(P_STOVE, "Fogão", "prop_stove", false, 6, 9f),
        Def(P_THRONE, "Trono", "prop_throne"),
        Def(P_CHANDELIER, "Lustre", "prop_chandelier", false, 6, 9f),
        Def(P_WEAPONRACK, "Armeiro", "prop_weaponrack"),
        Def(P_ARMORSTAND, "Armadura", "prop_armorstand"),
        Def(P_DUMMY, "Boneco de treino", "prop_dummy"),
        Def(P_SARCOPHAGUS, "Sarcófago", "prop_sarcophagus"),
        Def(P_CAGE, "Jaula", "prop_cage"),
        Def(P_SPIKETRAP, "Armadilha", "prop_spiketrap", false, 6, 6f),
        Def(P_GARGOYLE, "Gárgula", "prop_gargoyle"),
        Def(P_TREASURE, "Tesouro", "prop_treasure", false, 6, 5f),
        Def(P_ALCHEMY, "Alquimia", "prop_alchemy", false, 6, 5f),
        Def(P_CRYSTALBALL, "Bola de cristal", "prop_crystalball", false, 6, 5f),
        Def(P_MAGICCIRCLE, "Círculo mágico", "prop_magiccircle", false, 8, 8f),
        Def(P_LANTERN, "Lanterna", "prop_lantern", false, 6, 9f),
        Def(P_SCARECROW, "Espantalho", "prop_scarecrow", false, 4, 3f),
        Def(P_KEG, "Tonel", "prop_keg", false, 6, 6f),
        Def(P_TOTEM, "Totem", "prop_totem")
    )

    /** Materiais que o botão "Topo" do editor alterna (andares de cima das paredes). */
    val capChoices = listOf(0, W_ROOF_RED, W_ROOF_SLATE, W_THATCH, W_ROOF_GREEN, W_ROOF_BLUE, W_ROOF_SHINGLE, W_ROOF_PLUM, W_STONE, W_BRICK, W_CASTLE, W_MARBLE, W_HOUSE, W_TIMBER, W_WOOD, W_CHIMNEY)

    fun floorDef(id: Int): Def = floors.firstOrNull { it.id == id } ?: floors[0]
    fun wallDef(id: Int): Def? = walls.firstOrNull { it.id == id }
    fun propDef(id: Int): Def? = props.firstOrNull { it.id == id }

    fun isWall(o: Int) = o in 1..19 || o in 110..139
    fun isProp(o: Int) = o >= 20 && !isWall(o)
    fun solidFloor(f: Int) = f == F_VOID || f == F_WATER || f == F_LAVA || f == F_SLIME

    /** Portas animadas: abrem quando o herói chega perto. */
    fun isDoor(o: Int) = o == W_DOOR || o == W_DOOR_IRON || o == W_GATE_CASTLE
    /** Portas que só deixam passar depois de abertas. */
    fun isLockedDoor(o: Int) = o == W_DOOR_IRON || o == W_GATE_CASTLE

    private val walkProps = setOf(P_BONES, P_TUFT, P_WEB, P_SKULLS, P_TALLGRASS, P_REEDS, P_LILYPAD, P_WHEAT, P_SPIKETRAP, P_MAGICCIRCLE, P_SUNFLOWERS)
    /** Sólido para colisão. Portas de ferro/portão são tratadas à parte (abrem). */
    fun solidObj(o: Int) = o != 0 && o !in walkProps && o != W_DOOR && o != W_ARCH

    /** Luz colorida de props que brilham: cor, raio (px de tela), quanto tremula e altura do foco no sprite (0..96). */
    class Glow(val color: Int, val radius: Float, val flicker: Float, val cy: Float)
    val glows: Map<Int, Glow> = mapOf(
        P_BRAZIER to Glow(0xFFA040, 110f, 1f, 52f), P_TORCH to Glow(0xFFA040, 100f, 1f, 34f), P_CAMPFIRE to Glow(0xFF9030, 130f, 1f, 66f),
        P_CANDLES to Glow(0xFFC060, 90f, 0.8f, 36f), P_LAMP to Glow(0xFFC060, 120f, 0.5f, 34f), P_FORGE to Glow(0xFF8030, 110f, 1f, 76f),
        P_FIREPLACE to Glow(0xFF8030, 110f, 1f, 76f), P_CRYSTAL_B to Glow(0x40A8FF, 100f, 0.3f, 60f), P_CRYSTAL_R to Glow(0xFF4070, 100f, 0.3f, 60f),
        P_MUSHROOM to Glow(0x40E8D8, 80f, 0.3f, 72f), P_CAULDRON to Glow(0x60FF50, 80f, 0.4f, 58f), P_PORTAL to Glow(0xA060FF, 130f, 0.4f, 52f),
        P_ALTAR to Glow(0x60E0FF, 90f, 0.4f, 58f),
        P_STOVE to Glow(0xFF8030, 100f, 1f, 60f), P_CHANDELIER to Glow(0xFFC060, 130f, 0.8f, 30f), P_LANTERN to Glow(0xFFC060, 100f, 0.6f, 34f),
        P_CRYSTALBALL to Glow(0xA060FF, 90f, 0.4f, 50f), P_MAGICCIRCLE to Glow(0xB060FF, 100f, 0.4f, 70f), P_ALCHEMY to Glow(0x60FF80, 80f, 0.4f, 50f),
        P_TREASURE to Glow(0xFFD060, 80f, 0.5f, 60f)
    )
}

/**
 * Texturas de TESTE geradas por código (16x16, estilo bloco/Minecraft Dungeons).
 * Para trocar por arte final, basta colocar um PNG em app/src/main/assets/tiles/<nome>.png
 * (ex.: floor_stone.png, wall_brick.png). Qualquer tamanho quadrado funciona.
 */
object MapTex {
    const val TS = 64
    const val WALL_H = 40

    private var assets: AssetManager? = null
    private val raw = HashMap<String, Bitmap>()
    private val tiles = HashMap<String, Array<Array<Bitmap>>>()
    private val wallSprites = HashMap<String, Bitmap>()

    private val t0 = System.nanoTime()

    /** Relógio em segundos para animar tiles no editor. */
    fun clock(): Float = ((System.nanoTime() - t0) / 1_000_000L) / 1000f

    fun init(ctx: Context) {
        assets = ctx.applicationContext.assets
    }

    private fun h(x: Int, y: Int, k: Int): Int = Tex.h(x, y, k)
    private fun sh(c: Int, f: Float): Int = Tex.sh(c, f)

    private fun mix(a: Int, b: Int, t: Float): Int {
        val r = (((a shr 16) and 255) * (1f - t) + ((b shr 16) and 255) * t).toInt()
        val g = (((a shr 8) and 255) * (1f - t) + ((b shr 8) and 255) * t).toInt()
        val bl = ((a and 255) * (1f - t) + (b and 255) * t).toInt()
        return (r shl 16) or (g shl 8) or bl
    }

    private fun mk(fn: (Int, Int) -> Int): Bitmap {
        val a = IntArray(256)
        for (j in 0..15) for (i in 0..15) a[j * 16 + i] = fn(i, j) or (0xFF shl 24)
        return Bitmap.createBitmap(a, 16, 16, Bitmap.Config.ARGB_8888)
    }

    private fun noisy(c: Int, i: Int, j: Int, k: Int, amt: Int = 10): Int =
        sh(c, 1f + ((h(i, j, k) % (amt * 2 + 1)) - amt) / 100f)

    /** Blocos de pedra/tijolo com argamassa. bw/bh = tamanho do bloco; offset alterna as fileiras. */
    private fun bricks(i: Int, j: Int, base: Int, mortar: Int, k: Int, bw: Int, bh: Int, moss: Int = 0): Int {
        val row = j / bh
        val off = if (row % 2 == 0) 0 else bw / 2
        val xi = (i + off) % bw
        val yi = j % bh
        if (yi == 0 || xi == 0) return noisy(mortar, i, j, k + 1, 4)
        var c = noisy(base, i, j, k, 7)
        val bid = ((i + off) / bw) + row * 7
        c = sh(c, 0.93f + (h(bid, row, k + 3) % 14) / 100f)
        if (yi == 1 || xi == 1) c = sh(c, 1.12f)
        if (yi == bh - 1 || xi == bw - 1) c = sh(c, 0.88f)
        if (moss > 0 && h(i / 3, j / 3, k + 9) % 100 < moss) c = mix(c, 0x4F8A3E, 0.65f)
        return c
    }

    private fun gen(file: String): Bitmap = when (file) {
        "floor_void" -> mk { i, j -> noisy(0x0B0D12, i, j, 1, 25) }
        "floor_stone" -> mk { i, j -> bricks(i, j, 0x8E8A80, 0x5E5A53, 2, 8, 8) }
        "floor_brick" -> mk { i, j -> bricks(i, j, 0x86705C, 0x4F4338, 3, 8, 4) }
        "floor_dark" -> mk { i, j -> bricks(i, j, 0x4D505C, 0x2A2C34, 4, 8, 8) }
        "floor_moss" -> mk { i, j -> bricks(i, j, 0x858B76, 0x4E5246, 5, 8, 8, 34) }
        "floor_grass" -> mk { i, j ->
            var c = noisy(0x5BA245, i, j, 6, 6)
            val v = h(i, j, 61) % 13
            if (v == 0) c = 0x7BC65A else if (v == 1) c = 0x3F7F33
            c
        }
        "floor_dirt" -> mk { i, j ->
            var c = noisy(0x8A6A46, i, j, 7, 8)
            if (h(i, j, 71) % 17 == 0) c = 0x6A4F33
            if (h(i, j, 72) % 23 == 0) c = 0xA68560
            c
        }
        "floor_sand" -> mk { i, j ->
            var c = noisy(0xDCC888, i, j, 8, 5)
            if ((i + j * 2) % 9 == 0 && h(i, j, 81) % 3 != 0) c = 0xC9B274
            c
        }
        "floor_planks" -> mk { i, j ->
            val row = j / 4
            if (j % 4 == 0) noisy(0x6B4A2A, i, j, 9, 3)
            else if ((i + row * 5) % 12 == 0) noisy(0x6B4A2A, i, j, 9, 3)
            else {
                var c = sh(0xB08050, 0.94f + (h(row, (i + row * 5) / 12, 91) % 12) / 100f)
                if (h(i, j, 92) % 9 == 0) c = sh(c, 0.9f)
                if (j % 4 == 1) c = sh(c, 1.1f)
                c
            }
        }
        "floor_carpet" -> mk { i, j ->
            var c = sh(0xA32A2E, if ((i + j) % 2 == 0) 0.95f else 1.03f)
            if (abs(i - 7) + abs(j - 7) == 5 || abs(i - 7) + abs(j - 7) == 0) c = 0xD9B04A
            c
        }
        "floor_water" -> mk { i, j ->
            var c = noisy(0x2F6FCC, i, j, 10, 4)
            if (((i + (j / 2) * 3) % 8) < 2 && j % 2 == 0) c = 0x5C9BE8
            c
        }
        "floor_lava" -> mk { i, j ->
            var c = noisy(0xE8530E, i, j, 11, 6)
            if (h(i / 2, j / 2, 111) % 100 < 30) c = 0xFFB02E
            if (h(i / 3, j / 3, 112) % 100 < 20) c = 0x5A1B0A
            c
        }
        "wall_stone" -> mk { i, j -> bricks(i, j, 0xA29E94, 0x4F4C46, 12, 8, 8) }
        "wall_brick" -> mk { i, j -> bricks(i, j, 0x9C5B4A, 0x4E2F28, 13, 8, 4) }
        "wall_dark" -> mk { i, j -> bricks(i, j, 0x5E6170, 0x24262E, 14, 8, 8) }
        "wall_moss" -> mk { i, j -> bricks(i, j, 0x8C927C, 0x474B3E, 15, 8, 8, 40) }
        "wall_wood" -> mk { i, j ->
            if (i % 4 == 0) noisy(0x4E3419, i, j, 16, 3)
            else {
                var c = sh(0x8B5E34, 0.92f + (h(i / 4, 0, 161) % 16) / 100f)
                if (h(i, j, 162) % 8 == 0) c = sh(c, 0.88f)
                if ((j == 3 || j == 11) && i % 4 == 2) c = 0x2E2E34
                c
            }
        }
        "wall_sand" -> mk { i, j -> bricks(i, j, 0xD2B879, 0x9C8A52, 17, 8, 8) }
        else -> mk { i, j -> if ((i / 4 + j / 4) % 2 == 0) 0xFF00FF else 0x220022 } // textura ausente (magenta)
    }

    /** Textura original (PNG em assets/tiles se existir, senão a de teste). */
    fun base(file: String): Bitmap = raw.getOrPut(file) {
        var bmp: Bitmap? = null
        try {
            val am = assets
            if (am != null) am.open("tiles/$file.png").use {
                val o = BitmapFactory.Options()
                o.inScaled = false
                bmp = BitmapFactory.decodeStream(it, null, o)
            }
        } catch (e: Exception) {
            bmp = null
        }
        bmp ?: gen(file)
    }

    private fun frameCountOf(src: Bitmap, def: MT.Def): Int =
        if (def.frames > 1 && src.width % def.frames == 0 && src.width / def.frames >= 8) def.frames else 1

    /** Bloco 64x64 (nearest). variant 0..3 = rotações (só texturas "naturais"). time = segundos (texturas animadas). */
    fun tile(def: MT.Def, variant: Int, time: Float = 0f): Bitmap {
        val frames = tiles.getOrPut(def.file) {
            val src = base(def.file)
            val n = frameCountOf(src, def)
            val fw = src.width / n
            Array(n) { fi ->
                val cut = if (n == 1) src else Bitmap.createBitmap(src, fi * fw, 0, fw, src.height)
                val s = Bitmap.createScaledBitmap(cut, TS, TS, false)
                Array(4) { k ->
                    if (k == 0 || !def.vary) s else {
                        val m = Matrix()
                        m.postRotate(90f * k, TS / 2f, TS / 2f)
                        Bitmap.createBitmap(s, 0, 0, TS, TS, m, false)
                    }
                }
            }
        }
        val fi = if (frames.size > 1) ((time * def.fps).toInt() % frames.size + frames.size) % frames.size else 0
        return frames[fi][if (def.vary) variant and 3 else 0]
    }

    fun isAnimated(def: MT.Def) = def.frames > 1

    /** máscara de vizinhos que são parede: N=1, L=2, S=4, O=8 */
    const val M_N = 1
    const val M_E = 2
    const val M_S = 4
    const val M_W = 8

    const val FACE_SRC_H = 20

    /** Quantos quadros de animação a textura tem (portas: topo + 4 faces empilhadas). */
    fun wallFrames(def: MT.Def): Int {
        val src = base(def.file)
        return if (src.height > src.width) maxOf(1, (src.height - src.width) / FACE_SRC_H) else 1
    }

    /**
     * Sprite de parede empilhável: topo (64x64) em cima + faces frontais (uma por andar visível) embaixo.
     * hgt = andares (1..4). lo = andares de baixo escondidos pela parede da frente (só os de cima aparecem).
     * cap = material dos andares de cima (null = igual ao de baixo). frame = quadro da porta animada.
     * A máscara diz quais lados têm outra parede: nesses lados NÃO há contorno, então as paredes se emendam.
     * Altura do sprite = TS + (hgt - lo) * WALL_H. Desenhar em y = (linha * TS) - hgt * WALL_H.
     */
    fun wall(def: MT.Def, mask: Int = 0, hgt: Int = 1, lo: Int = 0, cap: MT.Def? = null, frame: Int = 0): Bitmap {
        val key = def.file + "#" + mask + "#" + hgt + "#" + lo + "#" + (cap?.file ?: "") + "#" + frame
        return wallSprites.getOrPut(key) { buildWall(def, mask, hgt, lo, cap, frame) }
    }

    private fun buildWall(def: MT.Def, mask: Int, hgt: Int, lo0: Int, cap: MT.Def?, frame: Int): Bitmap {
        val lo = lo0.coerceIn(0, hgt)
        val vis = hgt - lo
        val bmp = Bitmap.createBitmap(TS, TS + vis * WALL_H, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val p = Paint()
        p.isFilterBitmap = false
        p.isAntiAlias = false
        val T = TS.toFloat()
        fun rect(x0: Float, y0: Float, x1: Float, y1: Float, color: Int) {
            p.color = color
            c.drawRect(x0, y0, x1, y1, p)
        }
        val topDef = if (hgt > 1 && cap != null) cap else def
        val tsrc = base(topDef.file)
        val tw = tsrc.width
        val tsplit = tsrc.height > tw
        c.drawBitmap(tsrc, Rect(0, 0, tw, if (tsplit) tw else tsrc.height), Rect(0, 0, TS, TS), p)
        if (!tsplit) rect(0f, 0f, T, T, 0x30FFFFFF)
        val exN = (mask and M_N) == 0
        val exE = (mask and M_E) == 0
        val exS = (mask and M_S) == 0 && vis > 0
        val exW = (mask and M_W) == 0
        if (exN) { rect(0f, 0f, T, 2f, 0x80000000.toInt()); rect(0f, 2f, T, 5f, 0x30FFFFFF) }
        if (exW) { rect(0f, 0f, 2f, T, 0x80000000.toInt()); rect(2f, 0f, 5f, T, 0x26FFFFFF) }
        if (exE) { rect(T - 2f, 0f, T, T, 0x80000000.toInt()); rect(T - 5f, 0f, T - 2f, T, 0x26000000) }
        if (exS) { rect(0f, T - 6f, T, T - 2f, 0x22FFFFFF); rect(0f, T - 2f, T, T, 0x70000000) }
        if (!exS) return bmp
        // faces frontais: andar mais alto logo abaixo do topo, andar 0 no chão
        for (k in (hgt - 1) downTo lo) {
            val md = if (k == 0) def else (cap ?: def)
            val src = base(md.file)
            val w = src.width
            val split = src.height > w
            val nFr = if (split) maxOf(1, (src.height - w) / FACE_SRC_H) else 1
            val fi = if (k == 0) frame.coerceIn(0, nFr - 1) else 0
            val fr = if (split) Rect(0, w + fi * FACE_SRC_H, w, w + (fi + 1) * FACE_SRC_H) else Rect(0, 0, w, src.height)
            val y0 = TS + (hgt - 1 - k) * WALL_H
            c.drawBitmap(src, fr, Rect(0, y0, TS, y0 + WALL_H), p)
            val yy = y0.toFloat()
            if (!split) {
                rect(0f, yy, T, yy + WALL_H, 0x50000000)
                rect(0f, yy + WALL_H - 12f, T, yy + WALL_H, 0x38000000)
            }
            rect(0f, yy, T, yy + 3f, 0x55000000) // sombra do beiral
            if (exW) rect(0f, yy, 2f, yy + WALL_H, 0x80000000.toInt())
            if (exE) rect(T - 2f, yy, T, yy + WALL_H, 0x80000000.toInt())
            if (k > lo) rect(0f, yy + WALL_H - 1f, T, yy + WALL_H, 0x33000000) // junta entre andares
        }
        rect(0f, bmp.height - 2f, T, bmp.height.toFloat(), 0x66000000)
        return bmp
    }
}

/** Sprites de props (64x96, base do objeto na linha y=92). Desenhados uma vez. */
object PropArt {
    const val W = 64
    const val H = 96
    private val cache = HashMap<Int, Bitmap>()

    private fun c(v: Long): Int = v.toInt()

    private fun r(cv: Canvas, p: Paint, x0: Int, y0: Int, x1: Int, y1: Int, color: Int) {
        p.color = color
        cv.drawRect(x0.toFloat(), y0.toFloat(), x1.toFloat(), y1.toFloat(), p)
    }

    private val strips = HashMap<Int, Array<Bitmap>>()

    /** Quadros do sprite (PNG em assets/tiles/prop_*.png, tira horizontal). Sem arquivo, usa o desenho antigo. */
    private fun frames(id: Int): Array<Bitmap> = strips.getOrPut(id) {
        val def = MT.propDef(id)
        var out: Array<Bitmap>? = null
        if (def != null) {
            val src = MapTex.base(def.file)
            if (src.height == 48 && src.width % 32 == 0) {
                val n = src.width / 32
                out = Array(n) { fi -> Bitmap.createScaledBitmap(Bitmap.createBitmap(src, fi * 32, 0, 32, 48), W, H, false) }
            }
        }
        out ?: arrayOf(legacy(id))
    }

    fun get(id: Int, time: Float = 0f): Bitmap {
        val fr = frames(id)
        if (fr.size == 1) return fr[0]
        val fps = MT.propDef(id)?.fps ?: 8f
        return fr[((time * fps).toInt() % fr.size + fr.size) % fr.size]
    }

    private fun legacy(id: Int): Bitmap = cache.getOrPut(id) {
        val bmp = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
        val cv = Canvas(bmp)
        val p = Paint()
        p.isAntiAlias = false
        // sombra
        p.color = 0x55000000
        cv.drawOval(8f, 82f, 56f, 95f, p)
        when (id) {
            MT.P_CRATE -> {
                r(cv, p, 14, 56, 50, 92, c(0xFFA8763E))
                r(cv, p, 14, 44, 50, 56, c(0xFFC8945A))
                r(cv, p, 14, 56, 18, 92, c(0xFF7A5128))
                r(cv, p, 46, 56, 50, 92, c(0xFF7A5128))
                r(cv, p, 14, 88, 50, 92, c(0xFF7A5128))
                r(cv, p, 14, 70, 50, 74, c(0xFF7A5128))
                r(cv, p, 30, 56, 34, 92, c(0xFF7A5128))
                r(cv, p, 14, 44, 50, 46, c(0xFF7A5128))
                r(cv, p, 14, 44, 16, 56, c(0xFF7A5128))
                r(cv, p, 48, 44, 50, 56, c(0xFF7A5128))
            }
            MT.P_BARREL -> {
                r(cv, p, 16, 58, 48, 84, c(0xFF8A5A2B))
                r(cv, p, 19, 50, 45, 58, c(0xFF8A5A2B))
                r(cv, p, 19, 84, 45, 92, c(0xFF8A5A2B))
                r(cv, p, 16, 50, 24, 92, c(0xFFA06C39))
                r(cv, p, 40, 50, 48, 92, c(0xFF6D4521))
                r(cv, p, 20, 42, 44, 52, c(0xFFB07A44))
                r(cv, p, 20, 42, 44, 44, c(0xFF4A2E14))
                r(cv, p, 16, 58, 48, 62, c(0xFF3B4752))
                r(cv, p, 16, 78, 48, 82, c(0xFF3B4752))
            }
            MT.P_PILLAR -> {
                r(cv, p, 14, 84, 50, 92, c(0xFF9A968C))
                r(cv, p, 14, 84, 50, 86, c(0xFFB5B1A6))
                r(cv, p, 20, 24, 44, 84, c(0xFF8E8A80))
                r(cv, p, 20, 24, 26, 84, c(0xFFB0ACA2))
                r(cv, p, 38, 24, 44, 84, c(0xFF6F6B63))
                r(cv, p, 31, 24, 33, 84, c(0xFF7A766E))
                r(cv, p, 14, 14, 50, 26, c(0xFF9A968C))
                r(cv, p, 14, 14, 50, 18, c(0xFFB5B1A6))
                r(cv, p, 14, 24, 50, 26, c(0xFF6F6B63))
            }
            MT.P_BRAZIER -> {
                r(cv, p, 22, 72, 27, 92, c(0xFF3A3D45))
                r(cv, p, 37, 72, 42, 92, c(0xFF3A3D45))
                r(cv, p, 30, 66, 34, 92, c(0xFF2C2F36))
                r(cv, p, 16, 56, 48, 70, c(0xFF4A4E58))
                r(cv, p, 14, 54, 50, 58, c(0xFF6A6E78))
                r(cv, p, 18, 54, 46, 58, c(0xFFB83A12))
                r(cv, p, 24, 38, 40, 54, c(0xFFFF7A1A))
                r(cv, p, 28, 28, 36, 44, c(0xFFFFB02E))
                r(cv, p, 30, 34, 34, 50, c(0xFFFFE27A))
            }
            MT.P_ROCK -> {
                r(cv, p, 12, 78, 52, 92, c(0xFF7C7F86))
                r(cv, p, 18, 66, 46, 80, c(0xFF8D9098))
                r(cv, p, 24, 58, 40, 68, c(0xFF9EA1A9))
                r(cv, p, 24, 58, 32, 62, c(0xFFB5B8BF))
                r(cv, p, 42, 68, 46, 80, c(0xFF656870))
                r(cv, p, 46, 80, 52, 92, c(0xFF656870))
                r(cv, p, 12, 88, 52, 92, c(0xFF5B5E65))
            }
            MT.P_TREE -> {
                r(cv, p, 24, 86, 40, 92, c(0xFF6B4423))
                r(cv, p, 27, 58, 37, 92, c(0xFF6B4423))
                r(cv, p, 33, 58, 37, 92, c(0xFF55361B))
                r(cv, p, 8, 36, 56, 64, c(0xFF2F7A34))
                r(cv, p, 12, 22, 52, 44, c(0xFF3E9442))
                r(cv, p, 20, 8, 44, 28, c(0xFF55B04C))
                r(cv, p, 8, 56, 56, 64, c(0xFF256028))
                r(cv, p, 16, 26, 24, 30, c(0xFF7ACB63))
                r(cv, p, 34, 40, 44, 44, c(0xFF4FA64A))
                r(cv, p, 14, 44, 22, 48, c(0xFF2A6E2E))
            }
            MT.P_BONES -> {
                r(cv, p, 34, 76, 48, 88, c(0xFFE9E4D4))
                r(cv, p, 36, 86, 46, 90, c(0xFFD2CDBB))
                r(cv, p, 37, 79, 40, 83, c(0xFF2B2B2B))
                r(cv, p, 42, 79, 45, 83, c(0xFF2B2B2B))
                r(cv, p, 12, 84, 30, 87, c(0xFFE9E4D4))
                r(cv, p, 10, 82, 14, 86, c(0xFFE9E4D4))
                r(cv, p, 10, 85, 14, 89, c(0xFFE9E4D4))
                r(cv, p, 28, 82, 32, 86, c(0xFFE9E4D4))
                r(cv, p, 28, 85, 32, 89, c(0xFFE9E4D4))
                r(cv, p, 20, 74, 32, 77, c(0xFFD2CDBB))
            }
            MT.P_TUFT -> {
                r(cv, p, 20, 78, 23, 92, c(0xFF3F9A3A))
                r(cv, p, 26, 70, 29, 92, c(0xFF5BBF4F))
                r(cv, p, 32, 76, 35, 92, c(0xFF3F9A3A))
                r(cv, p, 38, 72, 41, 92, c(0xFF5BBF4F))
                r(cv, p, 44, 80, 47, 92, c(0xFF3F9A3A))
                r(cv, p, 25, 64, 31, 70, c(0xFFFFE066))
                r(cv, p, 37, 66, 43, 72, c(0xFFFF8FB1))
            }
            MT.P_CHEST -> {
                r(cv, p, 12, 66, 52, 92, c(0xFF8A5A2B))
                r(cv, p, 10, 52, 54, 68, c(0xFF9C6A34))
                r(cv, p, 10, 52, 54, 56, c(0xFFB98550))
                r(cv, p, 10, 62, 54, 66, c(0xFF3B4752))
                r(cv, p, 16, 52, 20, 92, c(0xFF3B4752))
                r(cv, p, 44, 52, 48, 92, c(0xFF3B4752))
                r(cv, p, 28, 64, 36, 76, c(0xFFFFC107))
                r(cv, p, 30, 68, 34, 72, c(0xFF8D6E00))
                r(cv, p, 12, 88, 52, 92, c(0xFF5E3C18))
            }
            else -> r(cv, p, 16, 60, 48, 92, c(0xFFFF00FF))
        }
        bmp
    }
}
