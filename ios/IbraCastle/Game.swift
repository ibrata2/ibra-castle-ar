import SceneKit
import simd

typealias V3 = SIMD3<Float>

struct Cell: Hashable { var x: Int; var y: Int }

enum GameState { case placing, building, wave, gameOver, victory }

enum BType: CaseIterable {
    case wall, tower, spike
    var cost: Int { switch self { case .wall: return 10; case .tower: return 50; case .spike: return 30 } }
    var hp: Float { switch self { case .wall: return 150; case .tower: return 120; case .spike: return 0 } }
    var walkable: Bool { self == .spike }
    var pathCost: Float { switch self { case .wall: return 25; case .tower: return 40; case .spike: return 1 } }
    var label: String { switch self { case .wall: return "جدار"; case .tower: return "برج سهام"; case .spike: return "فخ مسامير" } }
    var emoji: String { switch self { case .wall: return "🧱"; case .tower: return "🏹"; case .spike: return "📌" } }
}

enum EType: CaseIterable {
    case swordsman, spearman, knight, boss
    var hp: Float { [30, 20, 90, 400][idx] }
    var speed: Float { [1.2, 1.8, 0.7, 0.6][idx] }
    var dmg: Float { [8, 5, 20, 40][idx] }
    var gold: Int { [5, 4, 12, 60][idx] }
    var unlock: Int { [1, 2, 4, 5][idx] }
    var weight: Int { [10, 7, 4, 0][idx] }
    var budget: Int { [1, 1, 3, 0][idx] }
    var size: Float { [1, 1, 1.15, 1.6][idx] }
    var isBoss: Bool { self == .boss }
    private var idx: Int { switch self { case .swordsman: return 0; case .spearman: return 1; case .knight: return 2; case .boss: return 3 } }
    var template: SCNNode {
        switch self { case .swordsman: return Models.swordsman; case .spearman: return Models.spearman
                      case .knight: return Models.knight; case .boss: return Models.boss }
    }
}

final class Building {
    let type: BType, cell: Cell, node: SCNNode
    var hp: Float
    var head: SCNNode?, spikes: SCNNode?
    var cooldown: Float = 0, retarget: Float = 0
    weak var target: Enemy?
    var usesLeft = 40, spikeTimer: Float = 0, spikeAnim: Float = 1, pop: Float = 0, flashT: Float = 0
    var bar: SCNNode?, barFill: SCNNode?
    var destroyed = false
    init(type: BType, cell: Cell, node: SCNNode) { self.type = type; self.cell = cell; self.node = node; hp = type.hp }
}

final class Enemy {
    let type: EType, node: SCNNode, visual: SCNNode, bar: SCNNode, barFill: SCNNode
    var hp: Float, maxHp: Float, speedMul: Float = 1
    var path: [Cell] = [], pathIndex = 0, needsRepath = true
    var attackTimer: Float = 0, walk = Float.random(in: 0...6), lunge: Float = 0, flashT: Float = 0
    var dead = false, deathT: Float = 0
    var pos: V3 { get { node.simdPosition } set { node.simdPosition = newValue } }
    var remaining: Int { path.count - pathIndex }
    init(type: EType, node: SCNNode, visual: SCNNode, bar: SCNNode, barFill: SCNNode) {
        self.type = type; self.node = node; self.visual = visual; self.bar = bar; self.barFill = barFill
        hp = type.hp; maxHp = type.hp
    }
}

final class Projectile {
    let node: SCNNode, start: V3, dmg: Float
    weak var target: Enemy?
    var lastTarget: V3, travel: Float = 0, total: Float, life: Float = 0
    init(node: SCNNode, target: Enemy, start: V3, aim: V3, dmg: Float) {
        self.node = node; self.target = target; self.start = start; self.lastTarget = aim; self.dmg = dmg
        total = max(0.01, simd_length(aim - start))
    }
}

final class Fx {
    let node: SCNNode, dur: Float, vel: V3, grow: Float, spin: Float
    var t: Float = 0
    init(node: SCNNode, dur: Float, vel: V3, grow: Float, spin: Float) { self.node = node; self.dur = dur; self.vel = vel; self.grow = grow; self.spin = spin }
}

func yawOf(_ d: V3) -> Float { atan2(d.x, d.z) }
func flat(_ v: V3) -> V3 { V3(v.x, 0, v.z) }
func clamp01(_ v: Float) -> Float { min(max(v, 0), 1) }
func moveTowardsAngle(_ cur: Float, _ target: Float, _ maxDelta: Float) -> Float {
    var d = (target - cur).truncatingRemainder(dividingBy: 2 * .pi)
    if d > .pi { d -= 2 * .pi }
    if d < -.pi { d += 2 * .pi }
    return cur + min(max(d, -maxDelta), maxDelta)
}

struct Ray {
    let origin: V3, dir: V3
    func at(_ t: Float) -> V3 { origin + dir * t }
    func hitPlaneY(_ h: Float = 0) -> V3? {
        if abs(dir.y) < 1e-6 { return nil }
        let t = (h - origin.y) / dir.y
        return t > 0 ? at(t) : nil
    }
    func hitSphere(_ c: V3, _ r: Float) -> Float? {
        let oc = origin - c
        let b = simd_dot(oc, dir)
        let cc = simd_dot(oc, oc) - r * r
        let disc = b * b - cc
        if disc < 0 { return nil }
        let t = -b - sqrt(disc)
        return t > 0 ? t : nil
    }
}

/// منطق اللعبة كامل (بإحداثيات الساحة) — نفس منطق نسخة الأندرويد.
final class Game {
    static let startGold = 120, totalWaves = 10, waveBonus = 25
    static let castleMaxHp: Float = 500, tapDamage: Float = 10
    static let towerRange: Float = 3.5, towerRate: Float = 1.2, towerDmg: Float = 10, spikeDmg: Float = 8

    let w = 13, h = 13
    private let castleMin = Cell(x: 5, y: 5), castleMax = Cell(x: 7, y: 7)

    let root = SCNNode()
    private var castleNode = SCNNode()

    private(set) var state: GameState = .placing
    private(set) var gold = Game.startGold
    private(set) var wave = 0
    private(set) var castleHp = Game.castleMaxHp
    private(set) var enemiesLeft = 0
    var onToast: ((String) -> Void)?
    var onHit: (() -> Void)?

    private var buildings: [Building?]
    private var buildingList: [Building] = []
    private(set) var enemies: [Enemy] = []
    private var projectiles: [Projectile] = []
    private var fxs: [Fx] = []
    private var spawnQueue: [EType] = []
    private var spawnCells: [Cell] = []
    private var spawnTimer: Float = 0
    private var castleFlash: Float = 0
    private var tapCooldown: Float = 0

    private var ghost: SCNNode?
    private var ghostMarker: SCNNode?
    private var ghostType: BType?
    private var ghostCell: Cell?
    private(set) var ghostValid = false

    init() {
        buildings = Array(repeating: nil, count: w * h)
        gScore = Array(repeating: 0, count: w * h)
        cameFrom = Array(repeating: -1, count: w * h)
        closed = Array(repeating: false, count: w * h)
        reset()
    }

    var isPlaying: Bool { state == .building || state == .wave }

    func reset() {
        root.childNodes.forEach { $0.removeFromParentNode() }
        buildings = Array(repeating: nil, count: w * h)
        buildingList.removeAll(); enemies.removeAll(); projectiles.removeAll(); fxs.removeAll(); spawnQueue.removeAll()
        ghost = nil; ghostMarker = nil; ghostType = nil
        root.addChildNode(Models.ground.clone())
        castleNode = Models.castle.clone()
        root.addChildNode(castleNode)
        gold = Game.startGold; wave = 0; castleHp = Game.castleMaxHp; enemiesLeft = 0
    }

    func onPlaced() { if state == .placing { state = .building } }
    func restart() { reset(); state = .building }

    // MARK: الشبكة
    func cellCenter(_ c: Cell) -> V3 { V3(Float(c.x) - Float(w) / 2 + 0.5, 0, Float(c.y) - Float(h) / 2 + 0.5) }
    func cellOf(_ p: V3) -> Cell { Cell(x: Int(floor(p.x + Float(w) / 2)), y: Int(floor(p.z + Float(h) / 2))) }
    func inBounds(_ c: Cell) -> Bool { c.x >= 0 && c.y >= 0 && c.x < w && c.y < h }
    func isCastle(_ c: Cell) -> Bool { (castleMin.x...castleMax.x).contains(c.x) && (castleMin.y...castleMax.y).contains(c.y) }
    func isEdge(_ c: Cell) -> Bool { c.x == 0 || c.y == 0 || c.x == w - 1 || c.y == h - 1 }
    private func idx(_ c: Cell) -> Int { c.y * w + c.x }
    func buildingAt(_ c: Cell) -> Building? { inBounds(c) ? buildings[idx(c)] : nil }
    func canBuild(_ c: Cell) -> Bool { inBounds(c) && !isEdge(c) && !isCastle(c) && buildings[idx(c)] == nil }

    private func isSolid(_ c: Cell) -> Bool {
        if !inBounds(c) || isCastle(c) { return true }
        if let b = buildings[idx(c)] { return !b.type.walkable }
        return false
    }
    private func enterCost(_ c: Cell) -> Float {
        if isCastle(c) { return 1 }
        guard let b = buildings[idx(c)] else { return 1 }
        return max(1, b.type.pathCost)
    }
    private func randomEdgeCell() -> Cell {
        switch Int.random(in: 0..<4) {
        case 0: return Cell(x: Int.random(in: 0..<w), y: 0)
        case 1: return Cell(x: Int.random(in: 0..<w), y: h - 1)
        case 2: return Cell(x: 0, y: Int.random(in: 0..<h))
        default: return Cell(x: w - 1, y: Int.random(in: 0..<h))
        }
    }

    // MARK: A*
    private var gScore: [Float], cameFrom: [Int], closed: [Bool]
    private var open: [Int] = []
    private let dirs = [Cell(x: 1, y: 0), Cell(x: -1, y: 0), Cell(x: 0, y: 1), Cell(x: 0, y: -1),
                        Cell(x: 1, y: 1), Cell(x: 1, y: -1), Cell(x: -1, y: 1), Cell(x: -1, y: -1)]

    private func heuristic(_ i: Int) -> Float {
        let x = i % w, y = i / w
        let dx = max(castleMin.x - x, 0, x - castleMax.x)
        let dy = max(castleMin.y - y, 0, y - castleMax.y)
        let mn = Float(min(dx, dy)), mx = Float(max(dx, dy))
        return (mx - mn) + mn * 1.4142
    }

    /// الطريق من البداية (غير مشمولة) لحد أول مربع من القلعة.
    func findPath(_ startIn: Cell) -> [Cell] {
        let start = Cell(x: min(max(startIn.x, 0), w - 1), y: min(max(startIn.y, 0), h - 1))
        for i in 0..<(w * h) { gScore[i] = .greatestFiniteMagnitude; cameFrom[i] = -1; closed[i] = false }
        open.removeAll(keepingCapacity: true)
        let s = idx(start)
        gScore[s] = 0; open.append(s)
        var goal = -1
        while !open.isEmpty {
            var bi = 0; var bf = Float.greatestFiniteMagnitude
            for i in 0..<open.count {
                let f = gScore[open[i]] + heuristic(open[i])
                if f < bf { bf = f; bi = i }
            }
            let cur = open.remove(at: bi)
            if closed[cur] { continue }
            closed[cur] = true
            let c = Cell(x: cur % w, y: cur / w)
            if isCastle(c) { goal = cur; break }
            for d in dirs {
                let nb = Cell(x: c.x + d.x, y: c.y + d.y)
                if !inBounds(nb) { continue }
                let ni = idx(nb)
                if closed[ni] { continue }
                let diag = d.x != 0 && d.y != 0
                if diag && (isSolid(Cell(x: c.x + d.x, y: c.y)) || isSolid(Cell(x: c.x, y: c.y + d.y))) { continue }
                let g = gScore[cur] + (diag ? 1.4142 : 1) * enterCost(nb)
                if g < gScore[ni] { gScore[ni] = g; cameFrom[ni] = cur; open.append(ni) }
            }
        }
        if goal < 0 { return [] }
        var out: [Cell] = []
        var i = goal
        while i != s && i >= 0 { out.append(Cell(x: i % w, y: i / w)); i = cameFrom[i] }
        return out.reversed()
    }

    private func gridChanged() { enemies.forEach { $0.needsRepath = true } }

    // MARK: البناء بالسحب
    func beginGhost(_ t: BType) {
        endGhost(place: false)
        guard isPlaying else { return }
        ghostType = t
        let g = makeBuildingNode(t)
        g.opacity = 0.55
        g.isHidden = true
        let marker = SCNNode(geometry: SCNBox(width: 0.96, height: 0.02, length: 0.96, chamferRadius: 0.05))
        marker.simdPosition = V3(0, 0.02, 0)
        g.addChildNode(marker)
        ghostMarker = marker
        ghost = g
        root.addChildNode(g)
    }

    func moveGhost(_ p: V3?) {
        guard let g = ghost, let t = ghostType else { return }
        guard let p = p else { g.isHidden = true; ghostValid = false; ghostCell = nil; return }
        let c = cellOf(p)
        guard inBounds(c) else { g.isHidden = true; ghostValid = false; ghostCell = nil; return }
        g.isHidden = false
        g.simdPosition = cellCenter(c)
        ghostCell = c
        ghostValid = canBuild(c) && gold >= t.cost && (t.walkable || !enemies.contains { !$0.dead && cellOf($0.pos) == c })
        ghostMarker?.geometry?.firstMaterial = Models.unlit(ghostValid ? 0x51CF66 : 0xFA5252)
    }

    func endGhost(place: Bool) {
        let t = ghostType, c = ghostCell
        ghost?.removeFromParentNode()
        ghost = nil; ghostMarker = nil; ghostType = nil; ghostCell = nil
        if place, let t = t, let c = c, ghostValid, isPlaying, gold >= t.cost, canBuild(c) {
            gold -= t.cost
            placeBuilding(t, c)
        }
        ghostValid = false
    }

    private func makeBuildingNode(_ t: BType) -> SCNNode {
        let n = SCNNode()
        switch t {
        case .wall: n.addChildNode(Models.wall.clone())
        case .tower:
            n.addChildNode(Models.towerBase.clone())
            let head = Models.towerHead.clone(); head.simdPosition = V3(0, 1.1, 0); head.name = "head"
            n.addChildNode(head)
        case .spike:
            n.addChildNode(Models.spikeBase.clone())
            let s = Models.spikes.clone(); s.simdPosition = V3(0, -0.25, 0); s.name = "spikes"
            n.addChildNode(s)
        }
        return n
    }

    private func placeBuilding(_ t: BType, _ c: Cell) {
        let n = makeBuildingNode(t)
        n.simdPosition = cellCenter(c)
        let b = Building(type: t, cell: c, node: n)
        b.head = n.childNode(withName: "head", recursively: false)
        b.spikes = n.childNode(withName: "spikes", recursively: false)
        if t != .spike {
            let (bar, fill) = Models.makeBar()
            bar.simdPosition = n.simdPosition + V3(0, t == .tower ? 1.95 : 1.05, 0)
            bar.isHidden = true
            root.addChildNode(bar)
            b.bar = bar; b.barFill = fill
        }
        n.simdScale = V3(repeating: 0.01)
        root.addChildNode(n)
        buildings[idx(c)] = b
        buildingList.append(b)
        gridChanged()
    }

    private func damageBuilding(_ b: Building, _ dmg: Float) {
        if b.destroyed { return }
        b.hp -= dmg
        b.flashT = 0.15
        if b.hp <= 0 { demolish(b) }
    }

    private func demolish(_ b: Building) {
        if b.destroyed { return }
        b.destroyed = true
        b.node.removeFromParentNode()
        b.bar?.removeFromParentNode()
        if buildings[idx(b.cell)] === b { buildings[idx(b.cell)] = nil }
        buildingList.removeAll { $0 === b }
        for _ in 0..<5 {
            spawnFx(Models.puff, b.node.simdPosition + V3(0, 0.4, 0), 0.5,
                    V3(Float.random(in: -0.5...0.5), 0.8, Float.random(in: -0.5...0.5)), 0.35)
        }
        gridChanged()
    }

    // MARK: الموجات
    func startWave() {
        guard state == .building, wave < Game.totalWaves else { return }
        wave += 1
        state = .wave
        spawnQueue.removeAll()
        var budget = Int((6 * pow(1.3, Float(wave - 1))).rounded())
        let pool = EType.allCases.filter { !$0.isBoss && wave >= $0.unlock }
        var guardN = 500
        while budget > 0 && guardN > 0 {
            guardN -= 1
            let ok = pool.filter { $0.budget <= budget }
            if ok.isEmpty { break }
            var r = Int.random(in: 0..<ok.reduce(0) { $0 + $1.weight })
            var pick = ok[0]
            for t in ok { r -= t.weight; if r < 0 { pick = t; break } }
            spawnQueue.append(pick)
            budget -= max(1, pick.budget)
        }
        if wave % 5 == 0 { spawnQueue.append(.boss) }
        spawnCells = [randomEdgeCell(), randomEdgeCell()]
        spawnTimer = 0.3
    }

    private func spawnEnemy(_ t: EType) {
        let cell = spawnCells.randomElement()!
        let visual = t.template.clone()
        let n = SCNNode()
        n.simdPosition = cellCenter(cell)
        n.simdScale = V3(repeating: t.size)
        n.addChildNode(visual)
        let (bar, fill) = Models.makeBar()
        let e = Enemy(type: t, node: n, visual: visual, bar: bar, barFill: fill)
        e.maxHp = t.hp * (1 + 0.12 * Float(wave - 1)); e.hp = e.maxHp
        e.speedMul = 1 + 0.02 * Float(wave - 1)
        e.path = findPath(cell); e.needsRepath = false
        if let first = e.path.first { n.simdEulerAngles.y = yawOf(cellCenter(first) - n.simdPosition) }
        bar.isHidden = true
        root.addChildNode(n); root.addChildNode(bar)
        enemies.append(e)
    }

    // MARK: اللمس للضرب
    func tap(_ ray: Ray) {
        guard isPlaying, tapCooldown <= 0 else { return }
        tapCooldown = 0.2
        if hitAt(ray, radius: 0.45, dmg: Game.tapDamage) { onHit?() }
    }

    private var handCooldown: Float = 0

    /// ضربة بالإيد الحقيقية — مدى أوسع وضرر أكبر شوية.
    @discardableResult
    func handHit(_ ray: Ray) -> Bool {
        guard isPlaying, handCooldown <= 0 else { return false }
        handCooldown = 0.25
        return hitAt(ray, radius: 0.75, dmg: 14)
    }

    private func hitAt(_ ray: Ray, radius: Float, dmg: Float) -> Bool {
        var best: Enemy?; var bestT = Float.greatestFiniteMagnitude
        for e in enemies where !e.dead {
            let c = e.pos + V3(0, 0.4 * e.type.size, 0)
            if let t = ray.hitSphere(c, radius * e.type.size), t < bestT { bestT = t; best = e }
        }
        guard let e = best else { return false }
        damageEnemy(e, dmg)
        spawnFx(Models.spark, ray.at(bestT), 0.25, .zero, 0.45)
        return true
    }

    private func damageEnemy(_ e: Enemy, _ dmg: Float) {
        if e.dead { return }
        e.hp -= dmg
        e.flashT = 0.12
        if e.hp <= 0 { killEnemy(e) }
    }

    private func killEnemy(_ e: Enemy) {
        e.dead = true; e.deathT = 0
        e.bar.removeFromParentNode()
        gold += e.type.gold
        spawnFx(Models.coin, e.pos + V3(0, 0.7, 0), 0.8, V3(0, 1.2, 0), 0, spin: 9.4)
        for _ in 0..<4 {
            spawnFx(Models.puff, e.pos + V3(0, 0.3, 0), 0.45, V3(Float.random(in: -0.5...0.5), 0.6, Float.random(in: -0.5...0.5)), 0.3)
        }
    }

    private func spawnFx(_ m: SCNNode, _ p: V3, _ dur: Float, _ vel: V3, _ size: Float, spin: Float = 0) {
        let n = m.clone()
        n.simdPosition = p
        n.simdScale = V3(repeating: spin != 0 ? 1 : max(size, 0.01))
        root.addChildNode(n)
        fxs.append(Fx(node: n, dur: dur, vel: vel, grow: size, spin: spin))
    }

    // MARK: التحديث كل فريم
    func update(_ dtIn: Float) {
        let dt = min(dtIn, 0.05)
        tapCooldown -= dt
        handCooldown -= dt
        updateFx(dt)
        updateBuildingsVisual(dt)
        castleFlash = max(0, castleFlash - dt)
        let cs = 1 + sin(min(1, castleFlash / 0.15) * .pi) * 0.04
        castleNode.simdScale = V3(cs, 2 - cs, cs)

        guard isPlaying else { return }

        if state == .wave && !spawnQueue.isEmpty {
            spawnTimer -= dt
            if spawnTimer <= 0 {
                let t = spawnQueue.removeFirst()
                spawnEnemy(t)
                spawnTimer = t.isBoss ? 1.8 : 0.9
            }
        }

        var i = enemies.count - 1
        while i >= 0 {
            if i < enemies.count {
                let e = enemies[i]
                if e.dead {
                    e.deathT += dt
                    let k = 1 - clamp01(e.deathT / 0.3)
                    e.node.simdScale = V3(repeating: max(0.001, e.type.size * k))
                    if e.deathT > 0.3 { e.node.removeFromParentNode(); enemies.remove(at: i) }
                } else {
                    updateEnemy(e, dt)
                    if state == .gameOver { return }
                }
            }
            i -= 1
        }

        for b in buildingList {
            switch b.type {
            case .tower: updateTower(b, dt)
            case .spike: updateSpike(b, dt)
            default: break
            }
        }

        updateProjectiles(dt)

        enemiesLeft = spawnQueue.count + enemies.filter { !$0.dead }.count
        if state == .wave && spawnQueue.isEmpty && !enemies.contains(where: { !$0.dead }) {
            gold += Game.waveBonus
            if wave >= Game.totalWaves { state = .victory }
            else { state = .building; onToast?("+\(Game.waveBonus) ذهب — صدّيت الموجة!") }
        }
    }

    private enum Blocker { case castle, building(Building) }

    private func blockerAt(_ c: Cell) -> Blocker? {
        if isCastle(c) { return .castle }
        if let b = buildingAt(c), !b.type.walkable { return .building(b) }
        return nil
    }

    private func updateEnemy(_ e: Enemy, _ dt: Float) {
        if e.needsRepath { e.path = findPath(cellOf(e.pos)); e.pathIndex = 0; e.needsRepath = false }
        if e.pathIndex >= e.path.count { e.path = findPath(cellOf(e.pos)); e.pathIndex = 0; if e.path.isEmpty { return } }

        let next = e.path[e.pathIndex]
        let target = cellCenter(next)
        let blocker = blockerAt(next)
        let to = flat(target - e.pos)
        let dist = simd_length(to)
        e.lunge = max(0, e.lunge - dt * 4)
        e.flashT = max(0, e.flashT - dt)
        let hitPunch: Float = e.flashT > 0 ? 1.12 : 1

        if let blk = blocker, dist <= 0.75 {
            e.node.simdEulerAngles.y = moveTowardsAngle(e.node.simdEulerAngles.y, yawOf(to), 10.5 * dt)
            e.visual.simdPosition = V3(0, 0, sin(e.lunge * .pi) * 0.18)
            e.visual.simdEulerAngles = .zero
            e.attackTimer -= dt
            if e.attackTimer <= 0 {
                e.attackTimer = 1
                e.lunge = 1
                switch blk {
                case .castle:
                    castleHp = max(0, castleHp - e.type.dmg)
                    castleFlash = 0.15
                    if castleHp <= 0 { state = .gameOver; return }
                case .building(let b): damageBuilding(b, e.type.dmg)
                }
            }
        } else {
            let step = e.type.speed * e.speedMul * dt
            var move = dist > 1e-5 ? to / dist * min(step, dist) : .zero
            var push = V3.zero
            for o in enemies where o !== e && !o.dead {
                let d = flat(e.pos - o.pos)
                let m = simd_length_squared(d)
                if m < 1e-6 || m > 0.12 { continue }
                push += d / m * 0.04
            }
            if simd_length(push) > 1 { push = simd_normalize(push) }
            move += push * dt
            e.pos = e.pos + move
            if dist > 1e-4 { e.node.simdEulerAngles.y = moveTowardsAngle(e.node.simdEulerAngles.y, yawOf(to), 10.5 * dt) }
            e.walk += dt * 12 * e.speedMul * min(max(e.type.speed / 1.2, 0.6), 1.5)
            e.visual.simdPosition = V3(0, abs(sin(e.walk)) * 0.07, 0)
            e.visual.simdEulerAngles = V3(0, 0, sin(e.walk) * 0.14)
            if blocker == nil && dist <= step + 0.02 { e.pathIndex += 1 }
        }
        e.visual.simdScale = V3(hitPunch, 2 - hitPunch, hitPunch)

        e.bar.simdPosition = e.pos + V3(0, 1.05 * e.type.size, 0)
        e.bar.isHidden = e.hp >= e.maxHp
        e.barFill.simdScale = V3(max(0.001, 0.64 * clamp01(e.hp / e.maxHp)), 1, 1)
    }

    private func updateTower(_ b: Building, _ dt: Float) {
        b.retarget -= dt
        let r2 = Game.towerRange * Game.towerRange
        let valid: Bool = {
            guard let t = b.target, !t.dead else { return false }
            return simd_length_squared(flat(t.pos - b.node.simdPosition)) <= r2
        }()
        if b.retarget <= 0 || !valid {
            b.retarget = 0.2
            var best: Enemy?; var bestRem = Int.max
            for e in enemies where !e.dead {
                if simd_length_squared(flat(e.pos - b.node.simdPosition)) > r2 { continue }
                if e.remaining < bestRem { bestRem = e.remaining; best = e }
            }
            b.target = best
        }
        b.cooldown -= dt
        guard let t = b.target, let head = b.head else { return }
        head.simdEulerAngles.y = moveTowardsAngle(head.simdEulerAngles.y, yawOf(t.pos - b.node.simdPosition), 9.4 * dt)
        if b.cooldown <= 0 {
            b.cooldown = 1 / Game.towerRate
            let start = b.node.simdPosition + V3(0, 1.35, 0)
            let aim = t.pos + V3(0, 0.4 * t.type.size, 0)
            let n = Models.arrow.clone()
            n.simdPosition = start
            root.addChildNode(n)
            projectiles.append(Projectile(node: n, target: t, start: start, aim: aim, dmg: Game.towerDmg))
        }
    }

    private func updateSpike(_ b: Building, _ dt: Float) {
        b.spikeTimer -= dt
        if b.spikeTimer > 0 { return }
        var hit = false
        for e in enemies where !e.dead {
            if simd_length_squared(flat(e.pos - b.node.simdPosition)) > 0.55 * 0.55 { continue }
            damageEnemy(e, Game.spikeDmg); hit = true
        }
        if !hit { return }
        b.spikeTimer = 1
        b.spikeAnim = 0
        b.usesLeft -= 1
        if b.usesLeft <= 0 { demolish(b) }
    }

    private func updateBuildingsVisual(_ dt: Float) {
        for b in buildingList {
            if b.pop < 1 {
                b.pop = min(1, b.pop + dt / 0.25)
                b.node.simdScale = V3(repeating: max(0.01, b.pop * (1 + sin(b.pop * .pi) * 0.25)))
            } else if b.flashT > 0 {
                let s = 1 + b.flashT * 0.6
                b.node.simdScale = V3(s, 2 - s, s)
            } else {
                b.node.simdScale = V3(repeating: 1)
            }
            b.flashT = max(0, b.flashT - dt)
            if b.type.hp > 0 {
                b.bar?.isHidden = b.hp >= b.type.hp
                b.barFill?.simdScale = V3(max(0.001, 0.64 * clamp01(b.hp / b.type.hp)), 1, 1)
            }
            if let s = b.spikes, b.spikeAnim < 1 {
                b.spikeAnim += dt / 0.45
                let a = b.spikeAnim
                let k = a < 0.15 ? a / 0.15 : 1 - (a - 0.15) / 0.85
                s.simdPosition = V3(0, -0.25 + 0.27 * clamp01(k), 0)
            }
        }
    }

    private func updateProjectiles(_ dt: Float) {
        projectiles.removeAll { p in
            p.life += dt
            if let t = p.target, !t.dead { p.lastTarget = t.pos + V3(0, 0.4 * t.type.size, 0) }
            p.travel += 9 * dt
            let k = clamp01(p.travel / max(p.total, 0.01))
            let prev = p.node.simdPosition
            var pos = simd_mix(p.start, p.lastTarget, V3(repeating: k))
            pos.y += sin(k * .pi) * 0.6 * min(1, p.total / 3)
            p.node.simdPosition = pos
            let v = pos - prev
            if simd_length_squared(v) > 1e-8 {
                let yaw = yawOf(v)
                let pitch = -atan2(v.y, simd_length(flat(v)))
                p.node.simdOrientation = simd_quatf(angle: yaw, axis: V3(0, 1, 0)) * simd_quatf(angle: pitch, axis: V3(1, 0, 0))
            }
            if k >= 1 || p.life > 3 {
                if k >= 1, let t = p.target, !t.dead {
                    damageEnemy(t, p.dmg)
                    spawnFx(Models.spark, pos, 0.2, .zero, 0.3)
                }
                p.node.removeFromParentNode()
                return true
            }
            return false
        }
    }

    private func updateFx(_ dt: Float) {
        fxs.removeAll { f in
            f.t += dt
            let k = clamp01(f.t / f.dur)
            f.node.simdPosition += f.vel * dt
            if f.spin != 0 {
                f.node.simdEulerAngles.y += f.spin * dt
                f.node.simdScale = V3(repeating: max(0.001, 1 - k * k))
            } else {
                f.node.simdScale = V3(repeating: max(0.01, f.grow * (1 + k)))
                f.node.opacity = CGFloat(1 - k)
            }
            if k >= 1 { f.node.removeFromParentNode(); return true }
            return false
        }
    }
}
