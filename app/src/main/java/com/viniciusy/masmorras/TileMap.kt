private fun buildVillage() {
    val centerX = 48
    val centerY = 36

    // ───────────────────────────
    // Praça central e piso urbano
    // ───────────────────────────
    for (y in 27..45) {
        for (x in 39..57) {
            if (!inB(x, y)) continue
            val dx = x - centerX
            val dy = y - centerY
            val d = hypot(dx.toFloat(), dy.toFloat())
            if (d < 8.3f) {
                ground[y][x] = 2
                if (d < 6.5f && hash(x, y, 99) % 5 == 0) tiles[y][x] = 0
            } else if (d < 10.2f && hash(x, y, 50) % 3 == 0) {
                ground[y][x] = 1
            }
        }
    }

    // ───────────────────────────
    // Estradas principais
    // ───────────────────────────
    fun road(x0: Int, y0: Int, x1: Int, y1: Int) {
        for (y in minOf(y0, y1)..maxOf(y0, y1)) {
            for (x in minOf(x0, x1)..maxOf(x0, x1)) {
                if (!inB(x, y)) continue
                if (tiles[y][x] == 0) ground[y][x] = 1
            }
        }
    }

    road(20, 36, 76, 36)
    road(48, 18, 48, 54)
    road(30, 25, 30, 47)
    road(66, 25, 66, 47)
    road(35, 28, 63, 28)
    road(35, 44, 63, 44)

    // ───────────────────────────
    // Muros gerais e fortificação
    // ───────────────────────────
    for (x in 22..74) {
        if (inB(x, 19) && tiles[19][x] == 0) {
            tiles[19][x] = 7
            ground[19][x] = 2
        }
        if (inB(x, 53) && tiles[53][x] == 0) {
            tiles[53][x] = 7
            ground[53][x] = 2
        }
    }
    for (y in 19..53) {
        if (inB(22, y) && tiles[y][22] == 0) {
            tiles[y][22] = 7
            ground[y][22] = 2
        }
        if (inB(74, y) && tiles[y][74] == 0) {
            tiles[y][74] = 7
            ground[y][74] = 2
        }
    }

    // ───────────────────────────
    // Casas / blocos de vila
    // ───────────────────────────
    fun placeVillageHouse(
        tx: Int, ty: Int, w: Int, h: Int,
        kind: Int = 2, dir: Int = 0
    ): Boolean {
        if (!canPlace(tx, ty, w, h, 1)) return false
        addObj(kind, tx, ty, w, h)
        when (dir) {
            1 -> doorPath(tx + w / 2, ty - 1, w, h, 1)
            2 -> doorPath(tx + w, ty + h / 2, w, h, 2)
            3 -> doorPath(tx - 1, ty + h / 2, w, h, 3)
            else -> doorPath(tx + w / 2, ty + h, w, h, 0)
        }
        return true
    }

    val stoneHomes = listOf(
        intArrayOf(24, 25, 4, 3, 2, 0),
        intArrayOf(28, 29, 4, 3, 2, 0),
        intArrayOf(32, 23, 5, 4, 2, 0),
        intArrayOf(35, 30, 5, 4, 2, 0),
        intArrayOf(24, 39, 4, 3, 2, 0),
        intArrayOf(28, 42, 4, 3, 2, 0),
        intArrayOf(59, 25, 5, 4, 2, 2),
        intArrayOf(63, 29, 5, 4, 2, 2),
        intArrayOf(60, 39, 4, 3, 2, 2),
        intArrayOf(64, 42, 4, 3, 2, 2)
    )

    for (house in stoneHomes) {
        placeVillageHouse(
            house[0], house[1],
            house[2], house[3],
            house[4], house[5]
        )
    }

    placeVillageHouse(38, 17, 6, 5, 8, 0)
    placeVillageHouse(39, 41, 6, 5, 8, 0)
    placeVillageHouse(57, 17, 7, 5, 10, 2)
    placeVillageHouse(60, 36, 5, 5, 11, 1)

    for (stall in listOf(
        intArrayOf(43, 24),
        intArrayOf(46, 24),
        intArrayOf(50, 24),
        intArrayOf(43, 48),
        intArrayOf(46, 48),
        intArrayOf(50, 48)
    )) {
        val sx = stall[0]
        val sy = stall[1]
        if (inB(sx, sy) && tiles[sy][sx] == 0) addObj(4, sx, sy, 2, 1)
    }

    addObj(3, 48, 36)

    for (post in listOf(
        intArrayOf(35, 31), intArrayOf(41, 31), intArrayOf(54, 31), intArrayOf(61, 31),
        intArrayOf(35, 41), intArrayOf(41, 41), intArrayOf(54, 41), intArrayOf(61, 41),
        intArrayOf(47, 20), intArrayOf(47, 52)
    )) {
        val px = post[0]
        val py = post[1]
        if (inB(px, py) && tiles[py][px] == 0) addObj(5, px, py)
    }

    for (y in 15..57) {
        for (x in 24..72) {
            if (tiles[y][x] != 0) continue
            val dist = hypot((x + 0.5f - centerX).toFloat(), (y + 0.5f - centerY).toFloat())
            if (dist > 16f && dist < 20f && hash(x, y, 13) % 10 == 0) {
                addObj(0, x, y)
            }
            if (dist > 13f && dist < 16f && hash(x, y, 14) % 7 == 0) {
                ground[y][x] = 4
            }
        }
    }

    for (y in 24..48) {
        for (x in 34..62) {
            if (tiles[y][x] != 0) continue
            val dx = x - centerX
            val dy = y - centerY
            val d = hypot(dx.toFloat(), dy.toFloat())
            if (d > 7.5f && d < 9.2f && hash(x, y, 16) % 5 == 0) {
                ground[y][x] = 1
                tiles[y][x] = 6
            }
        }
    }

    tiles[36][48] = 0
    ground[36][48] = 2
    tiles[37][48] = 0
    ground[37][48] = 2
    tiles[36][49] = 0
    ground[36][49] = 2
}
