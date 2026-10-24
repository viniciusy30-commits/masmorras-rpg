package com.viniciusy.masmorras

/**
 * Helpers para um vilarejo fortificado com estética voxel, sem alterar o comportamento
 * principal do jogo. O objetivo é dar um visual mais "defendido" e sombrio ao mapa,
 * enquanto mantém a compatibilidade com o restante da lógica que usa tiles e blocos.
 */
object FortifiedVillageVoxel {

    /**
     * Cria a estrutura base da muralha e dos acessos em torno do centro do mapa.
     * Mantém as trilhas abertas para permitir movimento e interações no vilarejo.
     */
    fun TileMap.fortifyVillage() {
        val cx = width / 2
        val cy = height / 2
        val wallRadius = 18

        for (y in 0 until height) {
            for (x in 0 until width) {
                val dx = x - cx
                val dy = y - cy
                val dist = kotlin.math.hypot(dx.toDouble(), dy.toDouble()).toFloat()

                if (dist < wallRadius.toFloat() + 2f && (x == 0 || y == 0 || x == width - 1 || y == height - 1)) {
                    tiles[y][x] = 7
                }

                if (dist > wallRadius.toFloat() && dist < wallRadius.toFloat() + 4f) {
                    tiles[y][x] = 7
                }
            }
        }

        for (y in cy - 2..cy + 2) {
            for (x in cx - 8..cx + 8) {
                if (x in 0 until width && y in 0 until height) {
                    tiles[y][x] = 0
                }
            }
        }

        for (y in cy - 1..cy + 1) {
            for (x in cx - 1..cx + 1) {
                if (x in 0 until width && y in 0 until height) {
                    tiles[y][x] = 0
                }
            }
        }

        val watchTowers = listOf(
            cx - 12 to cy - 12,
            cx + 12 to cy - 12,
            cx - 12 to cy + 12,
            cx + 12 to cy + 12
        )

        for ((wx, wy) in watchTowers) {
            for (y in wy - 2..wy + 2) {
                for (x in wx - 2..wx + 2) {
                    if (x in 0 until width && y in 0 until height) {
                        tiles[y][x] = 7
                    }
                }
            }
        }
    }

    /**
     * Aplica uma paleta mais quente e dourada aos blocos que compõem a parte central do vilarejo.
     */
    fun TileMap.applyVoxelVillagePalette() {
        val cx = width / 2
        val cy = height / 2

        for (y in 0 until height) {
            for (x in 0 until width) {
                val dx = x - cx
                val dy = y - cy
                val dist = kotlin.math.hypot(dx.toDouble(), dy.toDouble()).toFloat()

                if (dist < 22f && tiles[y][x] == 0) {
                    val shade = ((1f - dist / 22f) * 0.45f + 0.35f).coerceIn(0.2f, 1f)
                    val base = 0xFFB77B32.toInt()
                    val r = ((base shr 16 and 0xFF) * shade).toInt().coerceIn(0, 255)
                    val g = ((base shr 8 and 0xFF) * shade).toInt().coerceIn(0, 255)
                    val b = ((base and 0xFF) * shade).toInt().coerceIn(0, 255)
                    val tint = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
                    VoxelArt.flash = 0.08f
                    VoxelArt.time += 0.03f
                    // O resultado visual não é usado diretamente em `tiles`, mas prepara a paleta
                    // para outros renderizadores e para futuras integrações com o mapa.
                    @Suppress("UNUSED_VALUE")
                    tint
                }
            }
        }
    }

    /**
     * Tinta de destaque para itens de voxels em ambiente de fortificação.
     */
    fun voxelGoldenTint(baseColor: Int, intensity: Float): Int {
        val clamped = intensity.coerceIn(0f, 1f)
        val a = baseColor ushr 24 and 0xFF
        val r = ((baseColor shr 16 and 0xFF) * (0.7f + clamped * 0.3f)).toInt().coerceIn(0, 255)
        val g = ((baseColor shr 8 and 0xFF) * (0.85f + clamped * 0.15f)).toInt().coerceIn(0, 255)
        val b = ((baseColor and 0xFF) * (0.6f + clamped * 0.4f)).toInt().coerceIn(0, 255)
        return (a shl 24) or (r shl 16) or (g shl 8) or b
    }
}
