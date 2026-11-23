package com.ibraprogram.castlear

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

enum class GameState { PLACING, BUILDING, WAVE, GAME_OVER, VICTORY }

enum class BType(val cost: Int, val hp: Float, val walkable: Boolean, val pathCost: Float, val label: String) {
    WALL(10, 150f, false, 25f, "جدار"),
    TOWER(50, 120f, false, 40f, "برج سهام"),
    SPIKE(30, 0f, true, 1f, "فخ مسامير")
}

enum class EType(
    val hp: Float, val speed: Float, val dmg: Float, val gold: Int,
    val unlock: Int, val weight: Int, val budget: Int, val size: Float, val boss: Boolean
) {
    SWORDSMAN(30f, 1.2f, 8f, 5, 1, 10, 1, 1f, false),
    SPEARMAN(20f, 1.8f, 5f, 4, 2, 7, 1, 1f, false),
    KNIGHT(90f, 0.7f, 20f, 12, 4, 4, 3, 1.15f, false),
    BOSS(400f, 0.6f, 40f, 60, 5, 0, 0, 1.6f, true);

    fun mesh(): Mesh = when (this) {
        SWORDSMAN -> Models.swordsman; SPEARMAN -> Models.spearman
        KNIGHT -> Models.knight; BOSS -> Models.boss
    }
}

class Building(val type: BType, val cell: Cell, val node: Node) {
    var hp = type.hp
    var head: Node? = null
    var spikes: Node? = null
    var cooldown = 0f
    var retarget = 0f
    var target: Enemy? = null
    var usesLeft = 40
    var spikeTimer = 0f
    var spikeAnim = 1f
    var pop = 0f
    var flashT = 0f
    var bar: Node? = null
    var barFill: Node? = null
    var destroyed = false
}

class Enemy(val type: EType, val node: Node, val visual: Node, val bar: Node, val barFill: Node) {
    var hp = type.hp
    var maxHp = type.hp
    var speedMul = 1f
    val path = ArrayList<Cell>()
    var pathIndex = 0
    var needsRepath = true
    var attackTimer = 0f
    var walk = Random.nextFloat() * 6f
    var dead = false
    var deathT = 0f
    var flashT = 0f
    var lunge = 0f
    val remaining get() = path.size - pathIndex
}

class Projectile(val node: Node, var target: Enemy?, val start: V3, var lastTarget: V3, val dmg: Float) {
    var travel = 0f
    var total = max(0.01f, (lastTarget - start).len())
    var life = 0f
}

class Fx(val node: Node, val dur: Float, val vel: V3, val grow: Float, val spin: Float = 0f) {
    var t = 0f
}

/**
 * منطق اللعبة كامل (بإحداثيات الساحة):
 * شبكة 13×13، القلعة بالوسط، الأعداء من الحافات، A* مع جدران "مكلفة"،
 * الموجات والذهب والدفاعات والضرب باللمس.
 */
class Game {
    val w = 13
    val h = 13
    private val castleMin = Cell(5, 5)
    private val castleMax = Cell(7, 7)

    val root = Node()
    private val castleNode = Node()

    @Volatile var state = GameState.PLACING
    @Volatile var gold = START_GOLD
    @Volatile var wave = 0
    @Volatile var castleHp = CASTLE_HP
    @Volatile var enemiesLeft = 0
    @Volatile var toast: String? = null
    @Volatile var toastSeq = 0
    @Volatile var hitSeq = 0

    private val buildings = arrayOfNulls<Building>(w * h)
    private val buildingList = ArrayList<Building>()
    val enemies = ArrayList<Enemy>()
    private val projectiles = ArrayList<Projectile>()
    private val fxs = ArrayList<Fx>()
    private val spawnQueue = ArrayDeque<EType>()
    private val spawnCells = ArrayList<Cell>()
    private var spawnTimer = 0f
    private var castleFlash = 0f
    private var tapCooldown = 0f

    // الشبح أثناء السحب
    private var ghost: Node? = null
    private var ghostType: BType? = null
    private var ghostCell: Cell? = null
    var ghostValid = false
        private set

    companion object {
        const val START_GOLD = 120
        const val CASTLE_HP = 500f
        const val TOTAL_WAVES = 10
        const val TAP_DAMAGE = 10f
        const val HAND_DAMAGE = 14f
        const val TOWER_RANGE = 3.5f
        const val TOWER_RATE = 1.2f
        const val TOWER_DMG = 10f
        const val SPIKE_DMG = 8f
        const val WAVE_BONUS = 25
    }

    init { reset() }

    // ───────────── إعداد ─────────────
    fun reset() {
        root.children.clear()
        buildings.fill(null); buildingList.clear(); enemies.clear(); projectiles.clear(); fxs.clear()
        spawnQueue.clear(); ghost = null; ghostType = null
        root.add(Node(Models.ground))
        castleNode.mesh = Models.castle
        castleNode.scale = V3(1f, 1f, 1f)
        root.add(castleNode)
        gold = START_GOLD; wave = 0; castleHp = CASTLE_HP; enemiesLeft = 0
        state = if (state == GameState.PLACING) GameState.PLACING else GameState.BUILDING
    }

    fun onPlaced() { if (state == GameState.PLACING) state = GameState.BUILDING }

    fun restart() { state = GameState.BUILDING; reset(); state = GameState.BUILDING }

    val isPlaying get() = state == GameState.BUILDING || state == GameState.WAVE

    // ───────────── الشبكة ─────────────
    fun cellCenter(c: Cell) = V3(c.x - w / 2f + 0.5f, 0f, c.y - h / 2f + 0.5f)
    fun cellOf(p: V3) = Cell(kotlin.math.floor(p.x + w / 2f).toInt(), kotlin.math.floor(p.z + h / 2f).toInt())
    fun inBounds(c: Cell) = c.x in 0 until w && c.y in 0 until h
    fun isCastle(c: Cell) = c.x in castleMin.x..castleMax.x && c.y in castleMin.y..castleMax.y
    fun isEdge(c: Cell) = c.x == 0 || c.y == 0 || c.x == w - 1 || c.y == h - 1
    private fun idx(c: Cell) = c.y * w + c.x
    fun buildingAt(c: Cell) = if (inBounds(c)) buildings[idx(c)] else null
    fun canBuild(c: Cell) = inBounds(c) && !isEdge(c) && !isCastle(c) && buildings[idx(c)] == null

    private fun isSolid(c: Cell): Boolean {
        if (!inBounds(c)) return true
        if (isCastle(c)) return true
        val b = buildings[idx(c)]
        return b != null && !b.type.walkable
    }

    private fun enterCost(c: Cell): Float {
        if (isCastle(c)) return 1f
        val b = buildings[idx(c)] ?: return 1f
        return max(1f, b.type.pathCost)
    }

    private fun randomEdgeCell(): Cell = when (Random.nextInt(4)) {
        0 -> Cell(Random.nextInt(w), 0)
        1 -> Cell(Random.nextInt(w), h - 1)
        2 -> Cell(0, Random.nextInt(h))
        else -> Cell(w - 1, Random.nextInt(h))
    }

    // ───────────── A* ─────────────
    private val gScore = FloatArray(w * h)
    private val cameFrom = IntArray(w * h)
    private val closed = BooleanArray(w * h)
    private val open = ArrayList<Int>()
    private val dirs = arrayOf(Cell(1, 0), Cell(-1, 0), Cell(0, 1), Cell(0, -1), Cell(1, 1), Cell(1, -1), Cell(-1, 1), Cell(-1, -1))

    private fun heuristic(i: Int): Float {
        val x = i % w; val y = i / w
        val dx = maxOf(castleMin.x - x, 0, x - castleMax.x)
        val dy = maxOf(castleMin.y - y, 0, y - castleMax.y)
        val mn = min(dx, dy); val mx = max(dx, dy)
        return (mx - mn) + mn * 1.4142f
    }

    /** الطريق من البداية (غير مشمولة) لحد أول مربع من القلعة. دائماً يلقى طريق. */
    fun findPath(startIn: Cell, out: ArrayList<Cell>) {
        out.clear()
        val start = Cell(startIn.x.coerceIn(0, w - 1), startIn.y.coerceIn(0, h - 1))
        gScore.fill(Float.MAX_VALUE); cameFrom.fill(-1); closed.fill(false); open.clear()
        val s = idx(start)
        gScore[s] = 0f; open.add(s)
        var goal = -1
        while (open.isNotEmpty()) {
            var bi = 0; var bf = Float.MAX_VALUE
            for (i in open.indices) {
                val f = gScore[open[i]] + heuristic(open[i])
                if (f < bf) { bf = f; bi = i }
            }
            val cur = open[bi]
            open[bi] = open[open.size - 1]; open.removeAt(open.size - 1)
            if (closed[cur]) continue
            closed[cur] = true
            val c = Cell(cur % w, cur / w)
            if (isCastle(c)) { goal = cur; break }
            for (d in dirs) {
                val nb = c + d
                if (!inBounds(nb)) continue
                val ni = idx(nb)
                if (closed[ni]) continue
                val diag = d.x != 0 && d.y != 0
                if (diag && (isSolid(Cell(c.x + d.x, c.y)) || isSolid(Cell(c.x, c.y + d.y)))) continue
                val g = gScore[cur] + (if (diag) 1.4142f else 1f) * enterCost(nb)
                if (g < gScore[ni]) { gScore[ni] = g; cameFrom[ni] = cur; open.add(ni) }
            }
        }
        if (goal < 0) return
        var i = goal
        while (i != s && i >= 0) { out.add(Cell(i % w, i / w)); i = cameFrom[i] }
        out.reverse()
    }

    private fun gridChanged() { for (e in enemies) e.needsRepath = true }

    // ───────────── البناء بالسحب ─────────────
    fun beginGhost(type: BType) {
        endGhost(false)
        if (!isPlaying) return
        ghostType = type
        val g = makeBuildingNode(type)
        g.alpha = 0.55f
        ghost = g
        g.visible = false
        root.add(g)
    }

    /** p = نقطة على سطح الساحة، أو null إذا الإصبع برا. */
    fun moveGhost(p: V3?) {
        val g = ghost ?: return
        val t = ghostType ?: return
        if (p == null) { g.visible = false; ghostValid = false; ghostCell = null; return }
        val c = cellOf(p)
        if (!inBounds(c)) { g.visible = false; ghostValid = false; ghostCell = null; return }
        g.visible = true
        g.pos = cellCenter(c)
        ghostCell = c
        ghostValid = canBuild(c) && gold >= t.cost && (t.walkable || enemies.none { !it.dead && cellOf(it.node.pos) == c })
        g.tint = if (ghostValid) floatArrayOf(0.55f, 1.3f, 0.6f) else floatArrayOf(1.5f, 0.45f, 0.45f)
    }

    fun endGhost(place: Boolean) {
        val t = ghostType
        val c = ghostCell
        ghost?.removeFromParent()
        ghost = null; ghostType = null; ghostCell = null
        if (place && t != null && c != null && ghostValid && isPlaying && gold >= t.cost && canBuild(c)) {
            gold -= t.cost
            placeBuilding(t, c)
        }
        ghostValid = false
    }

    private fun makeBuildingNode(t: BType): Node {
        val n = Node()
        when (t) {
            BType.WALL -> n.mesh = Models.wall
            BType.TOWER -> { n.mesh = Models.towerBase; n.add(Node(Models.towerHead).apply { pos = V3(0f, 1.1f, 0f) }) }
            BType.SPIKE -> { n.mesh = Models.spikeBase; n.add(Node(Models.spikes).apply { pos = V3(0f, -0.25f, 0f) }) }
        }
        return n
    }

    private fun placeBuilding(t: BType, c: Cell) {
        val n = makeBuildingNode(t)
        n.pos = cellCenter(c)
        val b = Building(t, c, n)
        if (t == BType.TOWER) b.head = n.children[0]
        if (t == BType.SPIKE) b.spikes = n.children[0]
        if (t != BType.SPIKE) {
            val bar = Node(Models.barBg).apply { billboard = true; visible = false }
            val fill = Node(Models.barFill).apply { pos = V3(-0.32f, 0f, 0f); scale = V3(0.64f, 1f, 1f) }
            bar.add(fill); root.add(bar)
            bar.pos = n.pos + V3(0f, if (t == BType.TOWER) 1.95f else 1.05f, 0f)
            b.bar = bar; b.barFill = fill
        }
        n.setScale(0.01f)
        root.add(n)
        buildings[idx(c)] = b
        buildingList.add(b)
        gridChanged()
    }

    private fun damageBuilding(b: Building, dmg: Float) {
        if (b.destroyed) return
        b.hp -= dmg
        b.flashT = 0.15f
        if (b.hp <= 0f) demolish(b)
    }

    private fun demolish(b: Building) {
        if (b.destroyed) return
        b.destroyed = true
        b.node.removeFromParent()
        b.bar?.removeFromParent()
        if (buildings[idx(b.cell)] === b) buildings[idx(b.cell)] = null
        buildingList.remove(b)
        for (i in 0 until 5) spawnFx(Models.puff, b.node.pos + V3(0f, 0.4f, 0f), 0.5f,
            V3(Random.nextFloat() - 0.5f, 0.8f, Random.nextFloat() - 0.5f), 0.35f)
        gridChanged()
    }

    // ───────────── الموجات ─────────────
    fun startWave() {
        if (state != GameState.BUILDING || wave >= TOTAL_WAVES) return
        wave++
        state = GameState.WAVE
        spawnQueue.clear()
        var budget = (6 * 1.3f.pow(wave - 1)).roundToInt()
        val pool = EType.values().filter { !it.boss && wave >= it.unlock }
        var guard = 500
        while (budget > 0 && guard-- > 0) {
            val ok = pool.filter { it.budget <= budget }
            if (ok.isEmpty()) break
            var r = Random.nextInt(ok.sumOf { it.weight })
            var pick = ok[0]
            for (t in ok) { r -= t.weight; if (r < 0) { pick = t; break } }
            spawnQueue.addLast(pick)
            budget -= max(1, pick.budget)
        }
        if (wave % 5 == 0) spawnQueue.addLast(EType.BOSS)
        spawnCells.clear()
        repeat(2) { spawnCells.add(randomEdgeCell()) }
        spawnTimer = 0.3f
    }

    private fun spawnEnemy(t: EType) {
        val cell = spawnCells[Random.nextInt(spawnCells.size)]
        val visual = Node(t.mesh())
        val root2 = Node().apply { pos = cellCenter(cell); setScale(t.size) }
        root2.add(visual)
        val bar = Node(Models.barBg).apply { billboard = true }
        val fill = Node(Models.barFill).apply { pos = V3(-0.32f, 0f, 0f); scale = V3(0.64f, 1f, 1f) }
        bar.add(fill)
        val e = Enemy(t, root2, visual, bar, fill)
        e.maxHp = t.hp * (1f + 0.12f * (wave - 1)); e.hp = e.maxHp
        e.speedMul = 1f + 0.02f * (wave - 1)
        findPath(cell, e.path); e.needsRepath = false
        if (e.path.isNotEmpty()) e.node.yaw = yawOf(cellCenter(e.path[0]) - e.node.pos)
        root.add(root2); root.add(bar)
        enemies.add(e)
    }

    // ───────────── اللمس للضرب ─────────────
    fun tap(ray: Ray) {
        if (!isPlaying || tapCooldown > 0f) return
        tapCooldown = 0.2f
        if (hitAt(ray, 0.45f, TAP_DAMAGE)) hitSeq++
    }

    private var handCooldown = 0f

    /** ضربة بالإيد الحقيقية — مدى أوسع وضرر أكبر شوية. */
    fun handHit(ray: Ray): Boolean {
        if (!isPlaying || handCooldown > 0f) return false
        handCooldown = 0.25f
        val ok = hitAt(ray, 0.75f, HAND_DAMAGE)
        if (ok) hitSeq++
        return ok
    }

    private fun hitAt(ray: Ray, radius: Float, dmg: Float): Boolean {
        var best: Enemy? = null; var bestT = Float.MAX_VALUE
        for (e in enemies) {
            if (e.dead) continue
            val c = e.node.pos + V3(0f, 0.4f * e.type.size, 0f)
            val t = ray.hitSphere(c, radius * e.type.size) ?: continue
            if (t < bestT) { bestT = t; best = e }
        }
        val e = best ?: return false
        damageEnemy(e, dmg)
        spawnFx(Models.spark, ray.at(bestT), 0.25f, V3.ZERO, 0.45f)
        return true
    }

    private fun damageEnemy(e: Enemy, dmg: Float) {
        if (e.dead) return
        e.hp -= dmg
        e.flashT = 0.12f
        if (e.hp <= 0f) killEnemy(e)
    }

    private fun killEnemy(e: Enemy) {
        e.dead = true
        e.deathT = 0f
        e.bar.removeFromParent()
        gold += e.type.gold
        spawnFx(Models.coin, e.node.pos + V3(0f, 0.7f, 0f), 0.8f, V3(0f, 1.2f, 0f), 0f, 540f)
        for (i in 0 until 4) spawnFx(Models.puff, e.node.pos + V3(0f, 0.3f, 0f), 0.45f,
            V3(Random.nextFloat() - 0.5f, 0.6f, Random.nextFloat() - 0.5f), 0.3f)
    }

    private fun spawnFx(m: Mesh, p: V3, dur: Float, vel: V3, size: Float, spin: Float = 0f) {
        val n = Node(m).apply { pos = p; setScale(max(size, 0.01f)) }
        if (spin != 0f) n.setScale(1f)
        root.add(n)
        fxs.add(Fx(n, dur, vel, size, spin))
    }

    // ───────────── التحديث كل فريم ─────────────
    fun update(dtIn: Float, camLocal: V3) {
        val dt = min(dtIn, 0.05f)
        Node.billboardYaw = yawOf(camLocal.flat())
        tapCooldown -= dt
        handCooldown -= dt
        updateFx(dt)
        updateBuildingsVisual(dt)
        castleFlash = max(0f, castleFlash - dt)
        castleNode.flash = castleFlash / 0.15f * 0.6f
        val cs = 1f + sin(min(1f, castleFlash / 0.15f) * Math.PI.toFloat()) * 0.04f
        castleNode.scale = V3(cs, 2f - cs, cs)

        if (!isPlaying) return

        // الظهور
        if (state == GameState.WAVE && spawnQueue.isNotEmpty()) {
            spawnTimer -= dt
            if (spawnTimer <= 0f) {
                val t = spawnQueue.removeFirst()
                spawnEnemy(t)
                spawnTimer = if (t.boss) 1.8f else 0.9f
            }
        }

        for (i in enemies.indices.reversed()) {
            if (i >= enemies.size) continue
            val e = enemies[i]
            if (e.dead) {
                e.deathT += dt
                val k = 1f - clamp01(e.deathT / 0.3f)
                e.node.setScale(e.type.size * k)
                if (e.deathT > 0.3f) { e.node.removeFromParent(); enemies.removeAt(i) }
                continue
            }
            updateEnemy(e, dt)
            if (state == GameState.GAME_OVER) return
        }

        for (b in ArrayList(buildingList)) {
            when (b.type) {
                BType.TOWER -> updateTower(b, dt)
                BType.SPIKE -> updateSpike(b, dt)
                else -> {}
            }
        }

        updateProjectiles(dt)

        enemiesLeft = spawnQueue.size + enemies.count { !it.dead }
        if (state == GameState.WAVE && spawnQueue.isEmpty() && enemies.none { !it.dead }) {
            gold += WAVE_BONUS
            if (wave >= TOTAL_WAVES) {
                state = GameState.VICTORY
            } else {
                state = GameState.BUILDING
                toast = "+$WAVE_BONUS ذهب — صدّيت الموجة!"; toastSeq++
            }
        }
    }

    private fun blockerAt(c: Cell): Any? {
        if (isCastle(c)) return castleNode
        val b = buildingAt(c)
        return if (b != null && !b.type.walkable) b else null
    }

    private fun updateEnemy(e: Enemy, dt: Float) {
        if (e.needsRepath) { findPath(cellOf(e.node.pos), e.path); e.pathIndex = 0; e.needsRepath = false }
        if (e.pathIndex >= e.path.size) { findPath(cellOf(e.node.pos), e.path); e.pathIndex = 0; if (e.path.isEmpty()) return }

        val next = e.path[e.pathIndex]
        val target = cellCenter(next)
        val blocker = blockerAt(next)
        val to = (target - e.node.pos).flat()
        val dist = to.len()

        e.flashT = max(0f, e.flashT - dt)
        e.visual.flash = if (e.flashT > 0f) 0.7f else 0f
        e.lunge = max(0f, e.lunge - dt * 4f)

        if (blocker != null && dist <= 0.75f) {
            e.node.yaw = moveTowardsAngle(e.node.yaw, yawOf(to), 600f * dt)
            e.visual.pos = V3(0f, 0f, sin(e.lunge * Math.PI.toFloat()) * 0.18f)
            e.visual.roll = 0f
            e.attackTimer -= dt
            if (e.attackTimer <= 0f) {
                e.attackTimer = 1f
                e.lunge = 1f
                if (blocker === castleNode) {
                    castleHp = max(0f, castleHp - e.type.dmg)
                    castleFlash = 0.15f
                    if (castleHp <= 0f) { state = GameState.GAME_OVER; return }
                } else if (blocker is Building) damageBuilding(blocker, e.type.dmg)
            }
        } else {
            val step = e.type.speed * e.speedMul * dt
            var move = if (dist > 1e-5f) to / dist * min(step, dist) else V3.ZERO
            // فصل بسيط حتى لا يتكدسون
            var push = V3.ZERO
            for (o in enemies) {
                if (o === e || o.dead) continue
                val d = (e.node.pos - o.node.pos).flat()
                val m = d.lenSq()
                if (m < 1e-6f || m > 0.12f) continue
                push += d / m * 0.04f
            }
            if (push.len() > 1f) push = push.norm()
            move += push * dt
            e.node.pos = e.node.pos + move
            if (dist > 1e-4f) e.node.yaw = moveTowardsAngle(e.node.yaw, yawOf(to), 600f * dt)
            e.walk += dt * 12f * e.speedMul * (e.type.speed / 1.2f).coerceIn(0.6f, 1.5f)
            e.visual.pos = V3(0f, abs(sin(e.walk)) * 0.07f, 0f)
            e.visual.roll = sin(e.walk) * 8f
            if (blocker == null && dist <= step + 0.02f) e.pathIndex++
        }

        e.bar.pos = e.node.pos + V3(0f, 1.05f * e.type.size, 0f)
        e.bar.visible = e.hp < e.maxHp
        e.barFill.scale = V3(0.64f * clamp01(e.hp / e.maxHp), 1f, 1f)
    }

    private fun updateTower(b: Building, dt: Float) {
        b.retarget -= dt
        val t0 = b.target
        val valid = t0 != null && !t0.dead && (t0.node.pos - b.node.pos).flat().lenSq() <= TOWER_RANGE * TOWER_RANGE
        if (b.retarget <= 0f || !valid) {
            b.retarget = 0.2f
            var best: Enemy? = null; var bestRem = Int.MAX_VALUE
            for (e in enemies) {
                if (e.dead) continue
                if ((e.node.pos - b.node.pos).flat().lenSq() > TOWER_RANGE * TOWER_RANGE) continue
                if (e.remaining < bestRem) { bestRem = e.remaining; best = e }
            }
            b.target = best
        }
        b.cooldown -= dt
        val t = b.target ?: return
        val head = b.head ?: return
        head.yaw = moveTowardsAngle(head.yaw, yawOf(t.node.pos - b.node.pos), 540f * dt)
        if (b.cooldown <= 0f) {
            b.cooldown = 1f / TOWER_RATE
            val start = b.node.pos + V3(0f, 1.35f, 0f)
            val aim = t.node.pos + V3(0f, 0.4f * t.type.size, 0f)
            val n = Node(Models.arrow).apply { pos = start }
            root.add(n)
            projectiles.add(Projectile(n, t, start, aim, TOWER_DMG))
        }
    }

    private fun updateSpike(b: Building, dt: Float) {
        b.spikeTimer -= dt
        if (b.spikeTimer > 0f) return
        var hit = false
        for (e in ArrayList(enemies)) {
            if (e.dead) continue
            val d = (e.node.pos - b.node.pos).flat()
            if (d.lenSq() > 0.55f * 0.55f) continue
            damageEnemy(e, SPIKE_DMG); hit = true
        }
        if (!hit) return
        b.spikeTimer = 1f
        b.spikeAnim = 0f
        if (--b.usesLeft <= 0) demolish(b)
    }

    private fun updateBuildingsVisual(dt: Float) {
        for (b in buildingList) {
            if (b.pop < 1f) {
                b.pop = min(1f, b.pop + dt / 0.25f)
                b.node.setScale(max(0.01f, b.pop * (1f + sin(b.pop * Math.PI.toFloat()) * 0.25f)))
            }
            b.flashT = max(0f, b.flashT - dt)
            b.node.flash = if (b.flashT > 0f) 0.6f else 0f
            b.bar?.visible = b.hp < b.type.hp
            b.barFill?.scale = V3(0.64f * clamp01(b.hp / b.type.hp), 1f, 1f)
            val s = b.spikes
            if (s != null && b.spikeAnim < 1f) {
                b.spikeAnim += dt / 0.45f
                val a = b.spikeAnim
                val k = if (a < 0.15f) a / 0.15f else 1f - (a - 0.15f) / 0.85f
                s.pos = V3(0f, lerpF(-0.25f, 0.02f, clamp01(k)), 0f)
            }
        }
    }

    private fun updateProjectiles(dt: Float) {
        val it = projectiles.iterator()
        while (it.hasNext()) {
            val p = it.next()
            p.life += dt
            val tgt = p.target
            if (tgt != null && !tgt.dead) p.lastTarget = tgt.node.pos + V3(0f, 0.4f * tgt.type.size, 0f)
            p.total = max(p.total, 0.01f)
            p.travel += 9f * dt
            val k = clamp01(p.travel / p.total)
            val prev = p.node.pos
            var pos = V3.lerp(p.start, p.lastTarget, k)
            pos = pos + V3(0f, sin(k * Math.PI.toFloat()) * 0.6f * min(1f, p.total / 3f), 0f)
            p.node.pos = pos
            val v = pos - prev
            if (v.lenSq() > 1e-8f) {
                p.node.yaw = yawOf(v)
                p.node.pitch = -Math.toDegrees(kotlin.math.atan2(v.y.toDouble(), v.flat().len().toDouble())).toFloat()
            }
            if (k >= 1f || p.life > 3f) {
                if (k >= 1f && tgt != null && !tgt.dead) {
                    damageEnemy(tgt, p.dmg)
                    spawnFx(Models.spark, pos, 0.2f, V3.ZERO, 0.3f)
                }
                p.node.removeFromParent()
                it.remove()
            }
        }
    }

    private fun updateFx(dt: Float) {
        val it = fxs.iterator()
        while (it.hasNext()) {
            val f = it.next()
            f.t += dt
            val k = clamp01(f.t / f.dur)
            f.node.pos = f.node.pos + f.vel * dt
            if (f.spin != 0f) {
                f.node.yaw += f.spin * dt
                f.node.setScale(1f - k * k)
            } else {
                f.node.setScale(max(0.01f, f.grow * (1f + k)))
                f.node.alpha = 1f - k
            }
            if (k >= 1f) { f.node.removeFromParent(); it.remove() }
        }
    }
}
