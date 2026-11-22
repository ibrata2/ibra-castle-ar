using System.Collections.Generic;
using UnityEngine;

namespace IbraCastle
{
    /// <summary>
    /// ذكاء الجندي: يمشي على طريق A* للقلعة. إذا المربع الجاي جدار/برج/قلعة → يوقف ويضرب.
    /// يعيد حساب طريقه كل ما تتغير الساحة.
    /// Animator اختياري — يستخدم: Float "Speed"، Trigger "Attack"، Trigger "Die".
    /// بدون Animator يسوي مشية كرتونية (هزّة) تلقائياً.
    /// </summary>
    [RequireComponent(typeof(Health))]
    public class EnemyAI : MonoBehaviour
    {
        public static readonly List<EnemyAI> All = new List<EnemyAI>();

        [Header("Movement (cells/sec)")]
        [SerializeField] float moveSpeed = 1.2f;
        [SerializeField] float turnSpeed = 600f;
        [SerializeField] float separationRadius = 0.35f;

        [Header("Attack")]
        [SerializeField] float attackDamage = 8f;
        [SerializeField] float attackInterval = 1f;
        [SerializeField, Tooltip("مسافة الوقوف عن مركز المربع المسدود")] float attackStopDistance = 0.75f;

        [Header("Reward")]
        [SerializeField] int goldReward = 5;

        [Header("Visual")]
        [SerializeField] Animator animator;
        [SerializeField] Transform visual;
        [SerializeField] float aimHeight = 0.45f;
        [SerializeField] GameObject deathVfx;
        [SerializeField] AudioClip deathSfx;

        public Health Health { get; private set; }
        public bool IsDead => Health == null || Health.IsDead;
        public Vector3 AimPoint => transform.TransformPoint(0f, aimHeight, 0f);
        public float RemainingPath => path.Count - pathIndex;
        public int GoldReward => goldReward;

        static readonly int SpeedHash = Animator.StringToHash("Speed");
        static readonly int AttackHash = Animator.StringToHash("Attack");
        static readonly int DieHash = Animator.StringToHash("Die");

        GridManager grid;
        readonly List<Vector2Int> path = new List<Vector2Int>();
        int pathIndex;
        bool needsRepath;
        float attackTimer;
        float walkCycle;
        Vector3 visualBasePos;
        float speedMultiplier = 1f;

        [RuntimeInitializeOnLoadMethod(RuntimeInitializeLoadType.SubsystemRegistration)]
        static void ResetStatics() => All.Clear();

        void Awake()
        {
            Health = GetComponent<Health>();
            Health.Died += OnDied;
            if (visual != null) visualBasePos = visual.localPosition;
            if (animator == null) animator = GetComponentInChildren<Animator>();
        }

        void OnEnable() => All.Add(this);

        void OnDisable()
        {
            All.Remove(this);
            if (grid != null) grid.Changed -= OnGridChanged;
        }

        /// <summary>يُستدعى من EnemySpawner بعد الإنشاء.</summary>
        public void Init(GridManager g, Vector2Int spawnCell, float healthMultiplier, float speedMul = 1f)
        {
            grid = g;
            speedMultiplier = speedMul;
            transform.localPosition = grid.CellToLocal(spawnCell);
            Health.SetMax(Health.Max * healthMultiplier, true);
            grid.Changed += OnGridChanged;
            Repath();
            if (path.Count > 0)
            {
                var dir = grid.CellToLocal(path[0]) - transform.localPosition;
                if (dir.sqrMagnitude > 1e-6f) transform.localRotation = Quaternion.LookRotation(dir);
            }
        }

        void OnGridChanged() => needsRepath = true;

        void Repath()
        {
            needsRepath = false;
            grid.FindPath(grid.LocalToCell(transform.localPosition), path);
            pathIndex = 0;
        }

        void Update()
        {
            if (IsDead || grid == null) return;
            var gm = GameManager.Instance;
            if (gm == null || !gm.IsPlaying) { SetAnimSpeed(0f); return; }

            if (needsRepath) Repath();
            if (pathIndex >= path.Count) { Repath(); if (path.Count == 0) return; }

            var next = path[pathIndex];
            var nextPos = grid.CellToLocal(next);
            var blocker = GetBlocker(next);
            var pos = transform.localPosition;
            var toNext = nextPos - pos; toNext.y = 0f;
            float dist = toNext.magnitude;

            if (blocker != null && dist <= attackStopDistance * grid.CellSize)
            {
                Face(toNext);
                Attack(blocker);
                return;
            }

            // حركة
            float step = moveSpeed * speedMultiplier * grid.CellSize * Time.deltaTime;
            Vector3 move = dist > 1e-5f ? toNext / dist * Mathf.Min(step, dist) : Vector3.zero;
            move += Separation() * Time.deltaTime;
            transform.localPosition = pos + move;
            Face(toNext);
            SetAnimSpeed(1f);
            Waddle(true);

            if (blocker == null && dist <= step + 0.02f) pathIndex++;
        }

        Health GetBlocker(Vector2Int cell)
        {
            if (grid.IsCastle(cell)) return grid.Castle;
            var b = grid.GetBuilding(cell);
            if (b != null && !b.Walkable) return b.Health;
            return null;
        }

        void Attack(Health target)
        {
            SetAnimSpeed(0f);
            Waddle(false);
            attackTimer -= Time.deltaTime;
            if (attackTimer > 0f) return;
            attackTimer = attackInterval;
            if (animator != null) animator.SetTrigger(AttackHash);
            else if (visual != null) visual.localPosition = visualBasePos + Vector3.forward * 0.15f; // دفعة كرتونية
            if (target != null && !target.IsDead) target.TakeDamage(attackDamage);
        }

        Vector3 Separation()
        {
            Vector3 push = Vector3.zero;
            float r2 = separationRadius * separationRadius;
            var p = transform.localPosition;
            foreach (var o in All)
            {
                if (o == this) continue;
                var d = p - o.transform.localPosition; d.y = 0f;
                float m = d.sqrMagnitude;
                if (m < 1e-6f || m > r2) continue;
                push += d / m * 0.05f;
            }
            return Vector3.ClampMagnitude(push, moveSpeed);
        }

        void Face(Vector3 dir)
        {
            if (dir.sqrMagnitude < 1e-6f) return;
            var look = Quaternion.LookRotation(dir, Vector3.up);
            transform.localRotation = Quaternion.RotateTowards(transform.localRotation, look, turnSpeed * Time.deltaTime);
        }

        void SetAnimSpeed(float s)
        {
            if (animator != null) animator.SetFloat(SpeedHash, s);
        }

        void Waddle(bool walking)
        {
            if (animator != null || visual == null) return;
            if (walking)
            {
                walkCycle += Time.deltaTime * 12f * speedMultiplier;
                visual.localPosition = visualBasePos + Vector3.up * Mathf.Abs(Mathf.Sin(walkCycle)) * 0.08f;
                visual.localRotation = Quaternion.Euler(0f, 0f, Mathf.Sin(walkCycle) * 8f);
            }
            else
            {
                visual.localPosition = Vector3.Lerp(visual.localPosition, visualBasePos, Time.deltaTime * 10f);
                visual.localRotation = Quaternion.Slerp(visual.localRotation, Quaternion.identity, Time.deltaTime * 10f);
            }
        }

        void OnDied(Health _)
        {
            All.Remove(this);
            if (grid != null) grid.Changed -= OnGridChanged;

            var gm = GameManager.Instance;
            if (gm != null)
            {
                gm.AddGold(goldReward);
                if (deathVfx != null && gm.Board != null)
                    SimplePool.Spawn(deathVfx, gm.Board, transform.localPosition, Quaternion.identity);
            }
            if (deathSfx != null) AudioSource.PlayClipAtPoint(deathSfx, transform.position, 0.6f);

            foreach (var c in GetComponentsInChildren<Collider>()) c.enabled = false;
            if (animator != null) { animator.SetTrigger(DieHash); Destroy(gameObject, 1.2f); }
            else StartCoroutine(ShrinkAndDestroy());
        }

        System.Collections.IEnumerator ShrinkAndDestroy()
        {
            var s = transform.localScale;
            for (float t = 0f; t < 1f; t += Time.deltaTime / 0.3f)
            {
                transform.localScale = s * (1f - t);
                yield return null;
            }
            Destroy(gameObject);
        }
    }
}
