using System.Collections.Generic;
using UnityEngine;

namespace IbraCastle
{
    /// <summary>
    /// مخزن كائنات بسيط (Object Pool) للسهام والمؤثرات — يقلل التقطيع على الموبايل.
    /// </summary>
    public static class SimplePool
    {
        static readonly Dictionary<GameObject, Stack<GameObject>> pools = new Dictionary<GameObject, Stack<GameObject>>();

        public static T Spawn<T>(T prefab, Transform parent, Vector3 localPos, Quaternion localRot) where T : Component
        {
            var go = Spawn(prefab.gameObject, parent, localPos, localRot);
            return go.GetComponent<T>();
        }

        public static GameObject Spawn(GameObject prefab, Transform parent, Vector3 localPos, Quaternion localRot)
        {
            if (prefab == null) return null;
            if (!pools.TryGetValue(prefab, out var stack)) { stack = new Stack<GameObject>(); pools[prefab] = stack; }

            GameObject go = null;
            while (stack.Count > 0 && go == null) go = stack.Pop(); // تخطي الكائنات المحذوفة (تغيير المشهد)

            if (go == null)
            {
                go = Object.Instantiate(prefab, parent);
                var tag = go.GetComponent<PooledObject>();
                if (tag == null) tag = go.AddComponent<PooledObject>();
                tag.Prefab = prefab;
            }
            else
            {
                go.transform.SetParent(parent, false);
            }

            go.transform.localPosition = localPos;
            go.transform.localRotation = localRot;
            go.transform.localScale = prefab.transform.localScale;
            go.SetActive(true);
            return go;
        }

        /// <summary>رجوع الكائن للمخزن بدل الحذف.</summary>
        public static void Despawn(GameObject go)
        {
            if (go == null || !go.activeSelf) return;
            var tag = go.GetComponent<PooledObject>();
            if (tag == null || tag.Prefab == null) { Object.Destroy(go); return; }
            go.SetActive(false);
            if (!pools.TryGetValue(tag.Prefab, out var stack)) { stack = new Stack<GameObject>(); pools[tag.Prefab] = stack; }
            stack.Push(go);
        }

        /// <summary>تفريغ المخزن (يُستدعى تلقائياً عند تحميل مشهد جديد).</summary>
        [RuntimeInitializeOnLoadMethod(RuntimeInitializeLoadType.SubsystemRegistration)]
        static void ResetStatics()
        {
            pools.Clear();
            UnityEngine.SceneManagement.SceneManager.sceneUnloaded -= OnSceneUnloaded;
            UnityEngine.SceneManagement.SceneManager.sceneUnloaded += OnSceneUnloaded;
        }

        static void OnSceneUnloaded(UnityEngine.SceneManagement.Scene _) => pools.Clear();
    }

    public class PooledObject : MonoBehaviour
    {
        [HideInInspector] public GameObject Prefab;
    }
}
