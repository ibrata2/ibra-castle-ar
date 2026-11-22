using System;
using System.Collections;
using System.Collections.Generic;
using UnityEngine;

namespace IbraCastle
{
    [Serializable]
    public class EnemyType
    {
        public string name = "جندي";
        public EnemyAI prefab;
        [Tooltip("من أي موجة يبدأ يظهر")] public int unlockWave = 1;
        [Tooltip("احتمالية الظهور النسبية")] public int weight = 10;
        [Tooltip("كم يأخذ من ميزانية الموجة")] public int budgetCost = 1;
        [Tooltip("زعيم: يظهر نهاية كل N موجات بدل الظهور العشوائي")] public bool isBoss;
        public int bossEveryNWaves = 5;
    }

    /// <summary>
    /// مولّد الموجات: كل موجة لها "ميزانية" تكبر تدريجياً، تتوزع على أنواع الأعداء المتاحة.
    /// الأعداء يقوون (صحة) مع كل موجة. زعيم كل 5 موجات. مكافأة ذهب نهاية كل موجة.
    /// </summary>
    public class EnemySpawner : MonoBehaviour
    {
        [SerializeField] EnemyType[] enemyTypes;

        [Header("Waves")]
        [SerializeField] int totalWaves = 10;
        [SerializeField] int baseBudget = 6;
        [SerializeField] float budgetGrowth = 1.3f;
        [SerializeField] float spawnInterval = 0.9f;
        [SerializeField, Tooltip("زيادة صحة الأعداء لكل موجة (0.12 = 12%)")] float healthGrowthPerWave = 0.12f;
        [SerializeField] float speedGrowthPerWave = 0.02f;
        [SerializeField, Tooltip("عدد نقاط الظهور بالموجة")] int spawnPointsPerWave = 2;

        [Header("Rewards & flow")]
        [SerializeField] int waveClearBonus = 25;
        [SerializeField, Tooltip("0 = ينتظر زر (ابدأ الموجة)")] float autoStartDelay = 0f;

        public int CurrentWave { get; private set; }
        public int TotalWaves => totalWaves;
        public bool IsSpawning { get; private set; }
        public int EnemiesRemaining => pendingSpawns + EnemyAI.All.Count;
        public float AutoStartCountdown { get; private set; } = -1f;

        public event Action<int, int> WaveStarted;   // (wave, total)
        public event Action<int, int> WaveCleared;   // (wave, bonus)

        int pendingSpawns;
        readonly List<EnemyType> queue = new List<EnemyType>();
        readonly List<Vector2Int> spawnCells = new List<Vector2Int>();

        GameManager GM => GameManager.Instance;

        void Update()
        {
            if (GM == null) return;

            if (GM.State == GameState.WaveRunning && !IsSpawning && EnemyAI.All.Count == 0)
                CompleteWave();

            if (AutoStartCountdown > 0f && GM.State == GameState.Building)
            {
                AutoStartCountdown -= Time.deltaTime;
                if (AutoStartCountdown <= 0f) StartNextWave();
            }
        }

        /// <summary>زر "ابدأ الموجة".</summary>
        public void StartNextWave()
        {
            if (GM == null || GM.State != GameState.Building || CurrentWave >= totalWaves) return;
            AutoStartCountdown = -1f;
            CurrentWave++;
            GM.SetState(GameState.WaveRunning);
            WaveStarted?.Invoke(CurrentWave, totalWaves);
            StartCoroutine(SpawnWave(CurrentWave));
        }

        void CompleteWave()
        {
            GM.AddGold(waveClearBonus);
            WaveCleared?.Invoke(CurrentWave, waveClearBonus);

            if (CurrentWave >= totalWaves) { GM.WinGame(); return; }

            GM.SetState(GameState.Building);
            if (autoStartDelay > 0f) AutoStartCountdown = autoStartDelay;
        }

        IEnumerator SpawnWave(int wave)
        {
            IsSpawning = true;
            BuildQueue(wave);
            pendingSpawns = queue.Count;

            spawnCells.Clear();
            for (int i = 0; i < Mathf.Max(1, spawnPointsPerWave); i++) spawnCells.Add(GM.Grid.RandomEdgeCell());

            float hpMul = 1f + healthGrowthPerWave * (wave - 1);
            float spdMul = 1f + speedGrowthPerWave * (wave - 1);

            for (int i = 0; i < queue.Count; i++)
            {
                if (!GM.IsPlaying) break;
                var cell = spawnCells[UnityEngine.Random.Range(0, spawnCells.Count)];
                var e = Instantiate(queue[i].prefab, GM.Board);
                e.Init(GM.Grid, cell, hpMul, spdMul);
                pendingSpawns--;
                yield return new WaitForSeconds(queue[i].isBoss ? spawnInterval * 2f : spawnInterval);
            }

            pendingSpawns = 0;
            IsSpawning = false;
        }

        void BuildQueue(int wave)
        {
            queue.Clear();
            int budget = Mathf.RoundToInt(baseBudget * Mathf.Pow(budgetGrowth, wave - 1));

            var pool = new List<EnemyType>();
            foreach (var t in enemyTypes)
                if (t.prefab != null && !t.isBoss && wave >= t.unlockWave) pool.Add(t);

            int guard = 500;
            while (budget > 0 && pool.Count > 0 && guard-- > 0)
            {
                var pick = WeightedPick(pool, budget);
                if (pick == null) break;
                queue.Add(pick);
                budget -= Mathf.Max(1, pick.budgetCost);
            }

            // الزعيم آخر الموجة
            foreach (var t in enemyTypes)
                if (t.prefab != null && t.isBoss && t.bossEveryNWaves > 0 && wave % t.bossEveryNWaves == 0)
                    queue.Add(t);
        }

        static EnemyType WeightedPick(List<EnemyType> pool, int budget)
        {
            int total = 0;
            foreach (var t in pool) if (t.budgetCost <= budget) total += Mathf.Max(1, t.weight);
            if (total == 0) return null;
            int r = UnityEngine.Random.Range(0, total);
            foreach (var t in pool)
            {
                if (t.budgetCost > budget) continue;
                r -= Mathf.Max(1, t.weight);
                if (r < 0) return t;
            }
            return null;
        }
    }
}
