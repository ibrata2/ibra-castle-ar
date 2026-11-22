using System;
using UnityEngine;

namespace IbraCastle
{
    /// <summary>
    /// السحب للبناء: تسحب أيقونة من شريط البناء، يظهر "شبح" المبنى على الساحة
    /// (أخضر = مكان صالح، أحمر = ممنوع)، وتفلت إصبعك حتى يُبنى.
    /// </summary>
    public class BuildManager : MonoBehaviour
    {
        public static BuildManager Instance { get; private set; }

        [SerializeField] BuildingData[] buildings;

        [Header("Ghost preview")]
        [SerializeField, Tooltip("URP Unlit شفاف")] Material ghostMaterial;
        [SerializeField] Color validColor = new Color(0.3f, 1f, 0.45f, 0.55f);
        [SerializeField] Color invalidColor = new Color(1f, 0.3f, 0.3f, 0.55f);
        [SerializeField, Tooltip("رفع الشبح فوق الإصبع حتى يبين")] float fingerOffsetPixels = 90f;

        [SerializeField] Camera cam;

        public BuildingData[] Buildings => buildings;
        public bool IsDragging => current != null;
        public event Action<Building> Built;

        BuildingData current;
        GameObject ghost;
        Renderer[] ghostRenderers;
        MaterialPropertyBlock mpb;
        Vector2Int hoverCell;
        bool hoverValid;

        static readonly int BaseColorId = Shader.PropertyToID("_BaseColor");
        static readonly int ColorId = Shader.PropertyToID("_Color");

        void Awake()
        {
            Instance = this;
            mpb = new MaterialPropertyBlock();
            if (cam == null) cam = Camera.main;
        }

        GameManager GM => GameManager.Instance;
        bool CanBuildNow => GM != null && GM.IsPlaying && GM.Grid != null;

        public void BeginDrag(int index, Vector2 screenPos)
        {
            if (!CanBuildNow || index < 0 || index >= buildings.Length) return;
            CancelDrag();
            current = buildings[index];
            ghost = CreateGhost(current.prefab.gameObject);
            UpdateDrag(screenPos);
        }

        public void UpdateDrag(Vector2 screenPos)
        {
            if (current == null || ghost == null) return;
            if (!CanBuildNow) { CancelDrag(); return; }

            var board = GM.Board;
            var grid = GM.Grid;
            if (cam == null) cam = Camera.main;

            var ray = cam.ScreenPointToRay(screenPos + new Vector2(0f, fingerOffsetPixels));
            var plane = new Plane(board.up, board.position);

            if (!plane.Raycast(ray, out float dist))
            {
                ghost.SetActive(false);
                hoverValid = false;
                return;
            }

            var local = board.InverseTransformPoint(ray.GetPoint(dist));
            hoverCell = grid.LocalToCell(local);

            bool inBounds = grid.InBounds(hoverCell);
            ghost.SetActive(inBounds);
            if (!inBounds) { hoverValid = false; return; }

            ghost.transform.localPosition = grid.CellToLocal(hoverCell);
            hoverValid = grid.CanBuild(hoverCell)
                         && GM.CanAfford(current.cost)
                         && (current.prefab.Walkable || !EnemyOnCell(hoverCell));
            Tint(hoverValid ? validColor : invalidColor);
        }

        public void EndDrag(Vector2 screenPos)
        {
            if (current == null) return;
            UpdateDrag(screenPos);
            if (hoverValid && GM.TrySpend(current.cost)) Place(current, hoverCell);
            CancelDrag();
        }

        public void CancelDrag()
        {
            if (ghost != null) Destroy(ghost);
            ghost = null;
            current = null;
            hoverValid = false;
        }

        void Place(BuildingData data, Vector2Int cell)
        {
            var b = Instantiate(data.prefab, GM.Board);
            b.transform.localPosition = GM.Grid.CellToLocal(cell);
            b.transform.localRotation = Quaternion.identity;
            b.Init(cell);
            GM.Grid.SetBuilding(cell, b);
            Built?.Invoke(b);
        }

        bool EnemyOnCell(Vector2Int cell)
        {
            var grid = GM.Grid;
            foreach (var e in EnemyAI.All)
                if (grid.LocalToCell(e.transform.localPosition) == cell) return true;
            return false;
        }

        /// <summary>نسخة شكلية فقط: بدون سكربتات ولا تصادم.</summary>
        GameObject CreateGhost(GameObject prefab)
        {
            var holder = new GameObject("GhostHolder");
            holder.SetActive(false); // حتى لا يشتغل Awake لسكربتات النسخة
            var g = Instantiate(prefab, holder.transform);

            foreach (var cv in g.GetComponentsInChildren<Canvas>(true))
                if (cv != null) DestroyImmediate(cv.gameObject); // شريط الصحة
            foreach (var mb in g.GetComponentsInChildren<MonoBehaviour>(true))
                if (mb != null) DestroyImmediate(mb);
            foreach (var col in g.GetComponentsInChildren<Collider>(true)) DestroyImmediate(col);
            foreach (var an in g.GetComponentsInChildren<Animator>(true)) an.enabled = false;

            ghostRenderers = g.GetComponentsInChildren<Renderer>(true);
            if (ghostMaterial != null)
                foreach (var r in ghostRenderers)
                {
                    var mats = new Material[r.sharedMaterials.Length];
                    for (int i = 0; i < mats.Length; i++) mats[i] = ghostMaterial;
                    r.sharedMaterials = mats;
                    r.shadowCastingMode = UnityEngine.Rendering.ShadowCastingMode.Off;
                }

            g.transform.SetParent(GM.Board, false);
            g.transform.localRotation = Quaternion.identity;
            g.name = prefab.name + "_Ghost";
            Destroy(holder);
            g.SetActive(true);
            return g;
        }

        void Tint(Color c)
        {
            if (ghostRenderers == null) return;
            foreach (var r in ghostRenderers)
            {
                if (r == null) continue;
                r.GetPropertyBlock(mpb);
                mpb.SetColor(BaseColorId, c);
                mpb.SetColor(ColorId, c);
                r.SetPropertyBlock(mpb);
            }
        }
    }
}
