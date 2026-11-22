using System;
using UnityEngine;
using UnityEngine.SceneManagement;

namespace IbraCastle
{
    public enum GameState { Placing, Building, WaveRunning, GameOver, Victory }

    /// <summary>
    /// مدير اللعبة: الذهب، حالة اللعبة، ساحة اللعب (Board) والقلعة.
    /// كل شي بالساحة (أعداء، مباني، سهام) يكون ابن للـ Board ويتحرك بإحداثياتها المحلية،
    /// فتغيير حجم الساحة بالـ AR ما يأثر على المنطق.
    /// </summary>
    public class GameManager : MonoBehaviour
    {
        public static GameManager Instance { get; private set; }

        [Header("Economy")]
        [SerializeField] int startingGold = 120;

        [Header("Performance")]
        [SerializeField] int targetFrameRate = 60;

        public int Gold { get; private set; }
        public GameState State { get; private set; } = GameState.Placing;
        public Transform Board { get; private set; }
        public GridManager Grid { get; private set; }
        public Health Castle => Grid != null ? Grid.Castle : null;

        public event Action<int> GoldChanged;
        public event Action<GameState> StateChanged;

        void Awake()
        {
            if (Instance != null && Instance != this) { Destroy(gameObject); return; }
            Instance = this;
            Gold = startingGold;
            Application.targetFrameRate = targetFrameRate;
            Screen.sleepTimeout = SleepTimeout.NeverSleep;
        }

        void OnDestroy()
        {
            if (Instance == this) Instance = null;
        }

        /// <summary>يُستدعى من ARCastlePlacer بعد ما تنزل القلعة على السطح.</summary>
        public void BeginGame(Transform board)
        {
            Grid = board.GetComponentInChildren<GridManager>();
            if (Grid == null) { Debug.LogError("[IbraCastle] Board prefab has no GridManager!"); return; }
            Board = Grid.transform; // كل المنطق بإحداثيات الشبكة
            if (Castle != null) Castle.Died += OnCastleDestroyed;
            else Debug.LogError("[IbraCastle] GridManager has no Castle Health assigned!");

            GoldChanged?.Invoke(Gold);
            SetState(GameState.Building);
        }

        public void SetState(GameState s)
        {
            if (State == s) return;
            if (State == GameState.GameOver || State == GameState.Victory) return; // نهائية
            State = s;
            StateChanged?.Invoke(s);
        }

        public void AddGold(int amount)
        {
            if (amount == 0) return;
            Gold = Mathf.Max(0, Gold + amount);
            GoldChanged?.Invoke(Gold);
        }

        public bool CanAfford(int cost) => Gold >= cost;

        public bool TrySpend(int cost)
        {
            if (Gold < cost) return false;
            Gold -= cost;
            GoldChanged?.Invoke(Gold);
            return true;
        }

        public void WinGame() => SetState(GameState.Victory);

        void OnCastleDestroyed(Health _) => SetState(GameState.GameOver);

        public void Restart()
        {
            SceneManager.LoadScene(SceneManager.GetActiveScene().buildIndex);
        }

        /// <summary>تحويل نقطة عالمية إلى إحداثيات الساحة.</summary>
        public Vector3 ToBoardLocal(Vector3 world) => Board != null ? Board.InverseTransformPoint(world) : world;

        public bool IsPlaying => State == GameState.Building || State == GameState.WaveRunning;
    }
}
