using System;
using UnityEngine;

namespace IbraCastle
{
    /// <summary>بيانات كل نوع دفاع يظهر بشريط البناء.</summary>
    [Serializable]
    public class BuildingData
    {
        public string displayName = "جدار";
        public Building prefab;
        public int cost = 10;
        public Sprite icon;
    }

    /// <summary>
    /// أساس كل دفاع (جدار، برج سهام، فخ مسامير).
    /// Walkable = الأعداء يمشون فوقه (الفخ). غير ذلك يسد الطريق ويكسرونه إذا ماكو لفة أقصر.
    /// </summary>
    public class Building : MonoBehaviour
    {
        [SerializeField] bool walkable = false;
        [SerializeField, Tooltip("كلفة المرور بالمربع لحساب الطريق. الجدار 25 = يفضّل يلف 25 مربع على أن يكسره")]
        float pathCost = 25f;
        [SerializeField] GameObject destroyVfx;
        [SerializeField] AudioClip placeSfx;

        public bool Walkable => walkable;
        public float PathCost => pathCost;
        public Vector2Int Cell { get; private set; }
        public Health Health { get; private set; }

        void Awake()
        {
            Health = GetComponent<Health>();
            if (Health != null) Health.Died += OnDied;
        }

        public void Init(Vector2Int cell)
        {
            Cell = cell;
            if (placeSfx != null) AudioSource.PlayClipAtPoint(placeSfx, transform.position, 0.7f);
            StartCoroutine(PopIn());
        }

        System.Collections.IEnumerator PopIn()
        {
            var target = transform.localScale;
            float t = 0f;
            while (t < 1f)
            {
                t += Time.deltaTime / 0.25f;
                float k = 1f + Mathf.Sin(Mathf.Clamp01(t) * Mathf.PI) * 0.25f; // نطة كرتونية
                transform.localScale = target * Mathf.Clamp01(t) * k;
                yield return null;
            }
            transform.localScale = target;
        }

        /// <summary>يُستدعى من الفخ عند تآكله، أو من الصحة عند الكسر.</summary>
        public void Demolish()
        {
            var gm = GameManager.Instance;
            if (gm != null && gm.Grid != null) gm.Grid.ClearBuilding(Cell, this);
            if (destroyVfx != null && gm != null && gm.Board != null)
                SimplePool.Spawn(destroyVfx, gm.Board, gm.ToBoardLocal(transform.position), Quaternion.identity);
            Destroy(gameObject);
        }

        void OnDied(Health _) => Demolish();
    }
}
