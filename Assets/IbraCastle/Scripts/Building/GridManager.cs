using System;
using System.Collections.Generic;
using UnityEngine;

namespace IbraCastle
{
    /// <summary>
    /// شبكة ساحة اللعب + إيجاد الطريق A*.
    /// القلعة بالوسط، الحافة الخارجية محجوزة لظهور الأعداء.
    /// الجدران "مكلفة" مو مستحيلة: العدو يلف إذا اللفة أقصر، وإلا يكسر الجدار.
    /// كل الإحداثيات محلية (Local) بالنسبة لهذا الـ Transform (الساحة).
    /// </summary>
    public class GridManager : MonoBehaviour
    {
        [Header("Grid")]
        [SerializeField] int width = 13;
        [SerializeField] int height = 13;
        [SerializeField] float cellSize = 1f;
        [SerializeField, Tooltip("حجم القلعة بالمربعات (فردي)")] int castleSize = 3;

        [Header("References")]
        [SerializeField] Health castle;

        [Header("Debug")]
        [SerializeField] bool drawGizmos = true;

        public int Width => width;
        public int Height => height;
        public float CellSize => cellSize;
        public Health Castle => castle;
        public Vector2 SizeLocal => new Vector2(width * cellSize, height * cellSize);

        /// <summary>ينطلق عند بناء أو هدم أي شي — الأعداء يعيدون حساب طريقهم.</summary>
        public event Action Changed;

        Building[] buildings;
        Vector2Int castleMin, castleMax;

        // A* buffers (بدون حجز ذاكرة كل مرة)
        float[] gScore;
        int[] cameFrom;
        bool[] closed;
        readonly List<int> open = new List<int>(64);

        static readonly Vector2Int[] Dirs =
        {
            new Vector2Int(1, 0), new Vector2Int(-1, 0), new Vector2Int(0, 1), new Vector2Int(0, -1),
            new Vector2Int(1, 1), new Vector2Int(1, -1), new Vector2Int(-1, 1), new Vector2Int(-1, -1)
        };

        void Awake() => Init();

        void Init()
        {
            if (castleSize % 2 == 0) castleSize++;
            buildings = new Building[width * height];
            var c = new Vector2Int(width / 2, height / 2);
            int half = castleSize / 2;
            castleMin = c - new Vector2Int(half, half);
            castleMax = c + new Vector2Int(half, half);
            int n = width * height;
            gScore = new float[n];
            cameFrom = new int[n];
            closed = new bool[n];
        }

        // ───────────── Coordinates ─────────────
        Vector3 Origin => new Vector3(-width * cellSize * 0.5f, 0f, -height * cellSize * 0.5f);

        public Vector3 CellToLocal(Vector2Int c) =>
            Origin + new Vector3((c.x + 0.5f) * cellSize, 0f, (c.y + 0.5f) * cellSize);

        public Vector2Int LocalToCell(Vector3 p) =>
            new Vector2Int(Mathf.FloorToInt((p.x - Origin.x) / cellSize), Mathf.FloorToInt((p.z - Origin.z) / cellSize));

        public bool InBounds(Vector2Int c) => c.x >= 0 && c.y >= 0 && c.x < width && c.y < height;
        public bool IsCastle(Vector2Int c) => c.x >= castleMin.x && c.x <= castleMax.x && c.y >= castleMin.y && c.y <= castleMax.y;
        public bool IsEdge(Vector2Int c) => c.x == 0 || c.y == 0 || c.x == width - 1 || c.y == height - 1;

        int Idx(Vector2Int c) => c.y * width + c.x;
        Vector2Int FromIdx(int i) => new Vector2Int(i % width, i / width);

        public Vector2Int Clamp(Vector2Int c) => new Vector2Int(Mathf.Clamp(c.x, 0, width - 1), Mathf.Clamp(c.y, 0, height - 1));

        // ───────────── Buildings ─────────────
        public Building GetBuilding(Vector2Int c) => InBounds(c) ? buildings[Idx(c)] : null;

        public bool CanBuild(Vector2Int c) => InBounds(c) && !IsEdge(c) && !IsCastle(c) && buildings[Idx(c)] == null;

        public void SetBuilding(Vector2Int c, Building b)
        {
            if (!InBounds(c)) return;
            buildings[Idx(c)] = b;
            Changed?.Invoke();
        }

        public void ClearBuilding(Vector2Int c, Building b)
        {
            if (!InBounds(c) || buildings[Idx(c)] != b) return;
            buildings[Idx(c)] = null;
            Changed?.Invoke();
        }

        /// <summary>مربع "صلب" = قلعة أو مبنى ما ينمشى عليه (جدار/برج).</summary>
        public bool IsSolid(Vector2Int c)
        {
            if (!InBounds(c)) return true;
            if (IsCastle(c)) return true;
            var b = buildings[Idx(c)];
            return b != null && !b.Walkable;
        }

        float EnterCost(Vector2Int c)
        {
            if (IsCastle(c)) return 1f;
            var b = buildings[Idx(c)];
            return b == null ? 1f : Mathf.Max(1f, b.PathCost);
        }

        public Vector2Int RandomEdgeCell()
        {
            int side = UnityEngine.Random.Range(0, 4);
            switch (side)
            {
                case 0: return new Vector2Int(UnityEngine.Random.Range(0, width), 0);
                case 1: return new Vector2Int(UnityEngine.Random.Range(0, width), height - 1);
                case 2: return new Vector2Int(0, UnityEngine.Random.Range(0, height));
                default: return new Vector2Int(width - 1, UnityEngine.Random.Range(0, height));
            }
        }

        // ───────────── A* Pathfinding ─────────────
        float Heuristic(int idx)
        {
            var c = FromIdx(idx);
            int dx = Mathf.Max(castleMin.x - c.x, 0, c.x - castleMax.x);
            int dy = Mathf.Max(castleMin.y - c.y, 0, c.y - castleMax.y);
            int mn = Mathf.Min(dx, dy), mx = Mathf.Max(dx, dy);
            return (mx - mn) + mn * 1.4142f; // octile
        }

        /// <summary>
        /// طريق من مربع البداية (غير مشمول) إلى أول مربع من القلعة (مشمول).
        /// دائماً يرجع طريق لأن الجدران مكلفة مو مستحيلة.
        /// </summary>
        public List<Vector2Int> FindPath(Vector2Int start, List<Vector2Int> result = null)
        {
            result ??= new List<Vector2Int>();
            result.Clear();
            start = Clamp(start);

            int n = width * height;
            for (int i = 0; i < n; i++) { gScore[i] = float.MaxValue; cameFrom[i] = -1; closed[i] = false; }
            open.Clear();

            int s = Idx(start);
            gScore[s] = 0f;
            open.Add(s);
            int goal = -1;

            while (open.Count > 0)
            {
                int bestI = 0; float bestF = float.MaxValue;
                for (int i = 0; i < open.Count; i++)
                {
                    int id = open[i];
                    float f = gScore[id] + Heuristic(id);
                    if (f < bestF) { bestF = f; bestI = i; }
                }
                int cur = open[bestI];
                open[bestI] = open[open.Count - 1];
                open.RemoveAt(open.Count - 1);

                if (closed[cur]) continue;
                closed[cur] = true;

                var c = FromIdx(cur);
                if (IsCastle(c)) { goal = cur; break; }

                foreach (var d in Dirs)
                {
                    var nb = c + d;
                    if (!InBounds(nb)) continue;
                    int ni = Idx(nb);
                    if (closed[ni]) continue;

                    bool diag = d.x != 0 && d.y != 0;
                    // ممنوع قص الزوايا بين جدارين
                    if (diag && (IsSolid(new Vector2Int(c.x + d.x, c.y)) || IsSolid(new Vector2Int(c.x, c.y + d.y)))) continue;

                    float g = gScore[cur] + (diag ? 1.4142f : 1f) * EnterCost(nb);
                    if (g < gScore[ni])
                    {
                        gScore[ni] = g;
                        cameFrom[ni] = cur;
                        open.Add(ni);
                    }
                }
            }

            if (goal < 0) return result;
            for (int i = goal; i != s && i >= 0; i = cameFrom[i]) result.Add(FromIdx(i));
            result.Reverse();
            return result;
        }

        // ───────────── Gizmos ─────────────
        void OnDrawGizmos()
        {
            if (!drawGizmos) return;
            if (buildings == null || buildings.Length != width * height) Init();
            Gizmos.matrix = transform.localToWorldMatrix;
            for (int x = 0; x < width; x++)
                for (int y = 0; y < height; y++)
                {
                    var c = new Vector2Int(x, y);
                    Gizmos.color = IsCastle(c) ? new Color(1f, .8f, .2f, .5f)
                                 : IsEdge(c) ? new Color(1f, .3f, .3f, .25f)
                                 : new Color(.3f, 1f, .4f, .15f);
                    Gizmos.DrawWireCube(CellToLocal(c), new Vector3(cellSize * .95f, .01f, cellSize * .95f));
                }
        }
    }
}
