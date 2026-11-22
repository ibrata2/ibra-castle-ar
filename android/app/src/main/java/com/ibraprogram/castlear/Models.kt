package com.ibraprogram.castlear

/**
 * كل مجسمات اللعبة مبنية بالكود — ستايل كرتوني Low-Poly بألوان زاهية.
 * المقياس: مربع واحد = 1 وحدة، الأمام = +Z.
 */
object Models {
    const val GRASS = 0x7ED957; const val GRASS2 = 0x6CC64A; const val SAND = 0xE9C98A
    const val DIRT = 0xA9744A; const val DIRT_DARK = 0x7D5233
    const val STONE = 0xBFC4CC; const val STONE_DARK = 0x8E949E; const val STONE_LIGHT = 0xDADFE6
    const val ROOF = 0xE84A4A; const val WOOD = 0xA06D3D; const val WOOD_DARK = 0x6B4423
    const val GOLD = 0xFFC93C; const val SKIN = 0xF7CBA3; const val STEEL = 0xD9E0E8
    const val BLUE = 0x3D7BFF; const val GREEN = 0x37B24D; const val RED = 0xE03131
    const val PURPLE = 0x8E44DD; const val DARK = 0x2D2D36; const val WHITE = 0xFFFFFF

    lateinit var ground: Mesh
    lateinit var castle: Mesh
    lateinit var wall: Mesh
    lateinit var towerBase: Mesh
    lateinit var towerHead: Mesh
    lateinit var spikeBase: Mesh
    lateinit var spikes: Mesh
    lateinit var arrow: Mesh
    lateinit var swordsman: Mesh
    lateinit var spearman: Mesh
    lateinit var knight: Mesh
    lateinit var boss: Mesh
    lateinit var barBg: Mesh
    lateinit var barFill: Mesh
    lateinit var reticle: Mesh
    lateinit var puff: Mesh
    lateinit var spark: Mesh
    lateinit var coin: Mesh

    fun build(gridW: Int, gridH: Int) {
        ground = buildGround(gridW, gridH)
        castle = buildCastle()
        wall = buildWall()
        towerBase = buildTowerBase()
        towerHead = buildTowerHead()
        spikeBase = MeshBuilder().apply {
            box(0f, 0.04f, 0f, 0.9f, 0.08f, 0.9f, WOOD_DARK, WOOD)
            for (i in -1..1) box(i * 0.3f, 0.085f, 0f, 0.05f, 0.01f, 0.8f, DARK)
        }.build()
        spikes = MeshBuilder().apply {
            box(0f, 0.02f, 0f, 0.78f, 0.04f, 0.78f, STONE_DARK)
            for (i in -1..1) for (j in -1..1) cone(V3(i * 0.26f, 0.04f, j * 0.26f), 0.08f, 0.32f, STEEL, 5)
        }.build()
        arrow = MeshBuilder().apply {
            box(0f, 0f, 0f, 0.04f, 0.04f, 0.5f, WOOD)
            cone(V3(0f, 0f, 0.25f), 0.05f, 0.13f, STEEL, 4, Axis.Z)
            box(0f, 0.035f, -0.22f, 0.012f, 0.06f, 0.1f, RED)
            box(0.035f, 0f, -0.22f, 0.06f, 0.012f, 0.1f, RED)
        }.build()
        swordsman = buildSoldier(BLUE, helmet = STEEL, weapon = 0)
        spearman = buildSoldier(GREEN, helmet = WOOD, weapon = 1)
        knight = buildSoldier(STEEL, helmet = STEEL, weapon = 2, plume = RED, bulky = true)
        boss = buildBoss()
        barBg = MeshBuilder().apply { box(0f, 0f, 0f, 0.66f, 0.1f, 0.02f, DARK) }.build()
        barFill = MeshBuilder().apply { box(0.5f, 0f, 0.012f, 1f, 0.07f, 0.02f, 0x51CF66) }.build()
        reticle = MeshBuilder().apply {
            val t = 0.012f; val h = 0.004f
            box(0f, h, 0.5f, 1f, h, t, GOLD); box(0f, h, -0.5f, 1f, h, t, GOLD)
            box(0.5f, h, 0f, t, h, 1f, GOLD); box(-0.5f, h, 0f, t, h, 1f, GOLD)
            for (sx in intArrayOf(-1, 1)) for (sz in intArrayOf(-1, 1)) {
                box(sx * 0.46f, h * 2, sz * 0.5f, 0.08f, h, t * 2.5f, WHITE)
                box(sx * 0.5f, h * 2, sz * 0.46f, t * 2.5f, h, 0.08f, WHITE)
            }
            cylinder(V3(0f, 0f, 0f), 0.05f, h * 2, GOLD, 12)
        }.build()
        puff = MeshBuilder().apply { sphere(V3.ZERO, 0.5f, 0xF1F3F5, 6, 4) }.build()
        spark = MeshBuilder().apply { sphere(V3.ZERO, 0.5f, 0xFFE066, 6, 4) }.build()
        coin = MeshBuilder().apply { cylinder(V3(-0.03f, 0f, 0f), 0.14f, 0.06f, GOLD, 10, Axis.X, 0xFFE38A) }.build()
    }

    private fun buildGround(w: Int, h: Int) = MeshBuilder().apply {
        val ox = -w / 2f; val oz = -h / 2f
        for (x in 0 until w) for (y in 0 until h) {
            val edge = x == 0 || y == 0 || x == w - 1 || y == h - 1
            val c = if (edge) SAND else if ((x + y) % 2 == 0) GRASS else GRASS2
            box(ox + x + 0.5f, -0.06f, oz + y + 0.5f, 1f, 0.12f, 1f, c)
        }
        box(0f, -0.32f, 0f, w + 0.0f, 0.4f, h + 0.0f, DIRT)
        box(0f, -0.7f, 0f, w - 1.2f, 0.4f, h - 1.2f, DIRT_DARK)
        // صخور وأشجار صغيرة بالزوايا
        for ((sx, sz) in listOf(1 to 1, -1 to 1, 1 to -1, -1 to -1)) {
            val cx = sx * (w / 2f - 0.5f); val cz = sz * (h / 2f - 0.5f)
            cylinder(V3(cx, 0f, cz), 0.07f, 0.3f, WOOD_DARK, 5)
            cone(V3(cx, 0.25f, cz), 0.3f, 0.55f, 0x2F9E44, 6)
            cone(V3(cx, 0.55f, cz), 0.22f, 0.4f, 0x40C057, 6)
        }
    }.build()

    private fun buildCastle() = MeshBuilder().apply {
        // القاعدة والأسوار
        box(0f, 0.45f, 0f, 2.5f, 0.9f, 2.5f, STONE, STONE_LIGHT)
        for (i in -2..2) {
            val p = i * 0.55f
            box(p, 0.98f, 1.2f, 0.28f, 0.16f, 0.16f, STONE_LIGHT)
            box(p, 0.98f, -1.2f, 0.28f, 0.16f, 0.16f, STONE_LIGHT)
            box(1.2f, 0.98f, p, 0.16f, 0.16f, 0.28f, STONE_LIGHT)
            box(-1.2f, 0.98f, p, 0.16f, 0.16f, 0.28f, STONE_LIGHT)
        }
        // الأبراج الأربعة
        for ((sx, sz) in listOf(1 to 1, -1 to 1, 1 to -1, -1 to -1)) {
            val b = V3(sx * 1.2f, 0f, sz * 1.2f)
            cylinder(b, 0.42f, 1.6f, STONE, 8, Axis.Y, STONE_LIGHT)
            cylinder(b + V3(0f, 1.6f, 0f), 0.5f, 0.12f, STONE_DARK, 8)
            cone(b + V3(0f, 1.72f, 0f), 0.52f, 0.75f, ROOF, 8)
            box(b.x, 2.6f, b.z, 0.02f, 0.3f, 0.02f, WOOD_DARK)
            box(b.x + 0.1f, 2.68f, b.z, 0.2f, 0.12f, 0.01f, GOLD)
        }
        // البرج الأوسط
        box(0f, 1.2f, 0f, 1.25f, 1.1f, 1.25f, STONE, STONE_LIGHT)
        cone(V3(0f, 1.75f, 0f), 0.95f, 0.9f, ROOF, 4)
        box(0f, 2.85f, 0f, 0.03f, 0.4f, 0.03f, WOOD_DARK)
        box(0.15f, 2.95f, 0f, 0.3f, 0.18f, 0.01f, GOLD)
        // البوابة والنوافذ
        box(0f, 0.32f, 1.26f, 0.7f, 0.64f, 0.04f, WOOD_DARK)
        cylinder(V3(0f, 0.64f, 1.24f), 0.35f, 0.06f, WOOD_DARK, 8, Axis.Z)
        box(0f, 0.32f, 1.29f, 0.04f, 0.6f, 0.02f, DARK)
        for (sx in intArrayOf(-1, 1)) box(sx * 0.3f, 1.35f, 0.64f, 0.16f, 0.26f, 0.02f, DARK)
    }.build()

    private fun buildWall() = MeshBuilder().apply {
        box(0f, 0.36f, 0f, 0.92f, 0.72f, 0.92f, STONE, STONE_LIGHT)
        box(0f, 0.76f, 0f, 0.96f, 0.08f, 0.96f, WOOD, WOOD)
        for ((x, y) in listOf(-0.25f to 0.2f, 0.2f to 0.45f, -0.1f to 0.58f, 0.3f to 0.15f))
            for (z in floatArrayOf(0.47f, -0.47f)) box(x, y, z, 0.28f, 0.14f, 0.02f, STONE_DARK)
    }.build()

    private fun buildTowerBase() = MeshBuilder().apply {
        box(0f, 0.12f, 0f, 0.9f, 0.24f, 0.9f, STONE_DARK, STONE)
        for ((sx, sz) in listOf(1 to 1, -1 to 1, 1 to -1, -1 to -1))
            box(sx * 0.3f, 0.62f, sz * 0.3f, 0.12f, 0.8f, 0.12f, WOOD)
        box(0f, 0.6f, 0.3f, 0.6f, 0.06f, 0.05f, WOOD_DARK)
        box(0f, 0.6f, -0.3f, 0.6f, 0.06f, 0.05f, WOOD_DARK)
        box(0f, 1.05f, 0f, 0.86f, 0.1f, 0.86f, WOOD_DARK, WOOD)
        for (i in -2..2) {
            box(i * 0.2f, 1.17f, 0.41f, 0.06f, 0.16f, 0.04f, WOOD)
            box(i * 0.2f, 1.17f, -0.41f, 0.06f, 0.16f, 0.04f, WOOD)
        }
    }.build()

    /** الرأس يدور — مركزه (0,0,0) فوق المنصة. */
    private fun buildTowerHead() = MeshBuilder().apply {
        cylinder(V3(0f, 0f, 0f), 0.28f, 0.08f, WOOD_DARK, 8)
        box(0f, 0.16f, 0.05f, 0.14f, 0.12f, 0.5f, WOOD)
        box(0f, 0.2f, 0.22f, 0.6f, 0.05f, 0.05f, WOOD_DARK)
        box(0f, 0.24f, 0.12f, 0.03f, 0.03f, 0.45f, STEEL)
        for (sx in intArrayOf(-1, 1)) box(sx * 0.3f, 0.35f, -0.3f, 0.04f, 0.5f, 0.04f, WOOD)
        cone(V3(0f, 0.58f, 0f), 0.55f, 0.4f, ROOF, 4)
    }.build()

    /** weapon: 0 سيف+درع، 1 رمح، 2 صولجان+درع كبير */
    private fun buildSoldier(tunic: Int, helmet: Int, weapon: Int, plume: Int? = null, bulky: Boolean = false) = MeshBuilder().apply {
        val w = if (bulky) 1.25f else 1f
        // رجلين
        box(-0.09f * w, 0.1f, 0f, 0.1f * w, 0.2f, 0.12f, DARK)
        box(0.09f * w, 0.1f, 0f, 0.1f * w, 0.2f, 0.12f, DARK)
        // الجسم
        box(0f, 0.33f, 0f, 0.34f * w, 0.28f, 0.24f * w, tunic)
        box(0f, 0.22f, 0f, 0.36f * w, 0.05f, 0.26f * w, WOOD_DARK)
        box(0f, 0.36f, 0.125f * w, 0.1f, 0.1f, 0.01f, GOLD)
        // الراس الكبير (ستايل كرتوني)
        sphere(V3(0f, 0.6f, 0f), 0.17f, SKIN, 8, 5)
        box(-0.055f, 0.62f, 0.155f, 0.035f, 0.05f, 0.02f, DARK)
        box(0.055f, 0.62f, 0.155f, 0.035f, 0.05f, 0.02f, DARK)
        // الخوذة
        sphere(V3(0f, 0.66f, 0f), 0.19f, helmet, 8, 4, 0.75f)
        box(0f, 0.6f, 0.17f, 0.04f, 0.1f, 0.03f, helmet)
        if (plume != null) box(0f, 0.86f, -0.02f, 0.05f, 0.14f, 0.22f, plume)
        // الذراعين
        box(-0.22f * w, 0.33f, 0f, 0.08f, 0.22f, 0.08f, tunic)
        box(0.22f * w, 0.33f, 0f, 0.08f, 0.22f, 0.08f, tunic)
        when (weapon) {
            0 -> {
                box(0.24f * w, 0.42f, 0.14f, 0.03f, 0.03f, 0.34f, STEEL)
                box(0.24f * w, 0.42f, 0.0f, 0.12f, 0.03f, 0.03f, WOOD_DARK)
                cylinder(V3(-0.27f * w, 0.33f, 0f), 0.13f, 0.04f, WOOD, 8, Axis.X, GOLD)
            }
            1 -> {
                cylinder(V3(0.25f, 0f, 0.06f), 0.02f, 0.95f, WOOD, 5)
                cone(V3(0.25f, 0.95f, 0.06f), 0.045f, 0.14f, STEEL, 4)
            }
            else -> {
                cylinder(V3(0.3f, 0.2f, 0.12f), 0.025f, 0.4f, WOOD_DARK, 5)
                sphere(V3(0.3f, 0.62f, 0.12f), 0.07f, STEEL, 6, 4)
                box(-0.32f, 0.36f, 0f, 0.04f, 0.38f, 0.3f, STEEL)
                box(-0.345f, 0.36f, 0f, 0.01f, 0.3f, 0.06f, RED)
                box(-0.345f, 0.4f, 0f, 0.01f, 0.06f, 0.22f, RED)
            }
        }
    }.build()

    private fun buildBoss() = MeshBuilder().apply {
        box(-0.14f, 0.13f, 0f, 0.16f, 0.26f, 0.16f, DARK)
        box(0.14f, 0.13f, 0f, 0.16f, 0.26f, 0.16f, DARK)
        sphere(V3(0f, 0.48f, 0f), 0.3f, PURPLE, 8, 5)
        box(0f, 0.32f, 0f, 0.5f, 0.06f, 0.42f, WOOD_DARK)
        sphere(V3(0f, 0.86f, 0f), 0.2f, 0x9BD17A, 8, 5)
        box(-0.07f, 0.88f, 0.18f, 0.06f, 0.04f, 0.02f, RED)
        box(0.07f, 0.88f, 0.18f, 0.06f, 0.04f, 0.02f, RED)
        sphere(V3(0f, 0.95f, 0f), 0.22f, DARK, 8, 4, 0.6f)
        cone(V3(-0.18f, 1.0f, 0f), 0.06f, 0.25f, 0xF8F0E3, 5)
        cone(V3(0.18f, 1.0f, 0f), 0.06f, 0.25f, 0xF8F0E3, 5)
        for (sx in intArrayOf(-1, 1)) {
            sphere(V3(sx * 0.3f, 0.66f, 0f), 0.11f, STONE_DARK, 6, 4)
            cone(V3(sx * 0.33f, 0.74f, 0f), 0.04f, 0.12f, STEEL, 4)
            box(sx * 0.34f, 0.46f, 0.02f, 0.11f, 0.3f, 0.11f, 0x9BD17A)
        }
        box(-0.2f, 0.62f, -0.2f, 0.5f, 0.6f, 0.04f, 0x6741D9)
        cylinder(V3(0.36f, 0.3f, 0.12f), 0.03f, 0.75f, WOOD_DARK, 5)
        box(0.36f, 1.0f, 0.24f, 0.04f, 0.26f, 0.22f, STEEL)
    }.build()
}
