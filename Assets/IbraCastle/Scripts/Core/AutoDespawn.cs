using UnityEngine;

namespace IbraCastle
{
    /// <summary>ضعه على المؤثرات (انفجار، غبار، عملة) حتى ترجع للمخزن بعد مدة.</summary>
    public class AutoDespawn : MonoBehaviour
    {
        [SerializeField] float lifetime = 1.2f;
        float timer;

        void OnEnable() => timer = lifetime;

        void Update()
        {
            timer -= Time.deltaTime;
            if (timer <= 0f) SimplePool.Despawn(gameObject);
        }
    }
}
