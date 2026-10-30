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
 * Camada "chão" (floor): ids 0..19.  Camada "objetos" (obj): paredes 1..19, props 20+.
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

    // paredes (altas, sólidas)
    const val W_STONE = 1
    const val W_BRICK = 2
    const val W_DARK = 3
    const val W_MOSS = 4
    const val W_WOOD = 5
    const val W_SAND = 6

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

    /** file = nome do arquivo opcional em assets/tiles/<file>.png (se existir, substitui a textura de teste). */
    class Def(val id: Int, val name: String, val file: String, val vary: Boolean = false)

    val floors = listOf(
        Def(F_VOID, "Vazio", "floor_void", true),
        Def(F_STONE, "Laje", "floor_stone"),
        Def(F_BRICK, "Tijolo", "floor_brick"),
        Def(F_DARK, "Pedra escura", "floor_dark"),
        Def(F_MOSS, "Pedra musgo", "floor_moss", true),
        Def(F_GRASS, "Grama", "floor_grass", true),
        Def(F_DIRT, "Terra", "floor_dirt", true),
        Def(F_SAND, "Areia", "floor_sand", true),
        Def(F_PLANKS, "Tábuas", "floor_planks"),
        Def(F_CARPET, "Tapete", "floor_carpet"),
        Def(F_WATER, "Água", "floor_water", true),
        Def(F_LAVA, "Lava", "floor_lava", true)
    )

    val walls = listOf(
        Def(W_STONE, "Parede pedra", "wall_stone"),
        Def(W_BRICK, "Parede tijolo", "wall_brick"),
        Def(W_DARK, "Parede escura", "wall_dark"),
        Def(W_MOSS, "Parede musgo", "wall_moss"),
        Def(W_WOOD, "Parede madeira", "wall_wood"),
        Def(W_SAND, "Arenito", "wall_sand")
    )

    val props = listOf(
        Def(P_CRATE, "Caixote", "prop_crate"),
        Def(P_BARREL, "Barril", "prop_barrel"),
        Def(P_PILLAR, "Pilar", "prop_pillar"),
        Def(P_BRAZIER, "Braseiro", "prop_brazier"),
        Def(P_ROCK, "Pedra", "prop_rock"),
        Def(P_TREE, "Árvore", "prop_tree"),
        Def(P_BONES, "Ossos", "prop_bones"),
        Def(P_TUFT, "Flores", "prop_tuft"),
        Def(P_CHEST, "Baú", "prop_chest")
    )

    fun floorDef(id: Int): Def = floors.firstOrNull { it.id == id } ?: floors[0]
    fun wallDef(id: Int): Def? = walls.firstOrNull { it.id == id }
    fun propDef(id: Int): Def? = props.firstOrNull { it.id == id }

    fun isWall(o: Int) = o in 1..19
    fun isProp(o: Int) = o >= 20
    fun solidFloor(f: Int) = f == F_VOID || f == F_WATER || f == F_LAVA
    fun solidObj(o: Int) = o != 0 && o != P_BONES && o != P_TUFT
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
    private val tiles = HashMap<String, Array<Bitmap>>()
    private val wallSprites = HashMap<String, Bitmap>()

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
            if (am != null) am.open("tiles/$file.png").use { bmp = BitmapFactory.decodeStream(it) }
        } catch (e: Exception) {
            bmp = null
        }
        bmp ?: gen(file)
    }

    /** Bloco 64x64 (nearest). variant 0..3 = rotações (só para texturas "naturais"). */
    fun tile(def: MT.Def, variant: Int): Bitmap {
        val arr = tiles.getOrPut(def.file) {
            val src = base(def.file)
            val s = Bitmap.createScaledBitmap(src, TS, TS, false)
            Array(4) { k ->
                if (k == 0 || !def.vary) s else {
                    val m = Matrix()
                    m.postRotate(90f * k, TS / 2f, TS / 2f)
                    Bitmap.createBitmap(s, 0, 0, TS, TS, m, false)
                }
            }
        }
        return arr[if (def.vary) variant and 3 else 0]
    }

    /** Sprite de parede: topo (64x64) + face frontal (64xWALL_H) embaixo. Altura total = TS + WALL_H. */
    fun wall(def: MT.Def): Bitmap = wallSprites.getOrPut(def.file) {
        val src = base(def.file)
        val bmp = Bitmap.createBitmap(TS, TS + WALL_H, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val p = Paint()
        p.isFilterBitmap = false
        p.isAntiAlias = false
        // topo (mais claro)
        c.drawBitmap(src, null, Rect(0, 0, TS, TS), p)
        p.color = 0x30FFFFFF
        c.drawRect(0f, 0f, TS.toFloat(), TS.toFloat(), p)
        // frente (mais escura, escurece para baixo)
        c.drawBitmap(src, null, Rect(0, TS, TS, TS + WALL_H), p)
        p.color = 0x50000000
        c.drawRect(0f, TS.toFloat(), TS.toFloat(), (TS + WALL_H).toFloat(), p)
        p.color = 0x38000000
        c.drawRect(0f, (TS + WALL_H - 12).toFloat(), TS.toFloat(), (TS + WALL_H).toFloat(), p)
        // contornos (estilo bloco)
        p.color = 0x70000000
        c.drawRect(0f, (TS - 2).toFloat(), TS.toFloat(), (TS + 2).toFloat(), p)
        p.color = 0x40000000
        c.drawRect(0f, 0f, TS.toFloat(), 2f, p)
        c.drawRect(0f, 0f, 2f, TS.toFloat(), p)
        c.drawRect((TS - 2).toFloat(), 0f, TS.toFloat(), TS.toFloat(), p)
        c.drawRect(0f, TS.toFloat(), 2f, (TS + WALL_H).toFloat(), p)
        c.drawRect((TS - 2).toFloat(), TS.toFloat(), TS.toFloat(), (TS + WALL_H).toFloat(), p)
        bmp
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

    fun get(id: Int): Bitmap = cache.getOrPut(id) {
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
