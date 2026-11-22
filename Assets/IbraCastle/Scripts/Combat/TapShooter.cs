using UnityEngine;

namespace IbraCastle
{
    /// <summary>
    /// اللمس للضرب: تلمس الجندي فيتضرر مباشرة (Ray + SphereCast متسامح مع الإصبع).
    /// الأعداء لازم يكون عليهم Collider بطبقة Enemy.
    /// </summary>
    public class TapShooter : MonoBehaviour
    {
        [SerializeField] Camera cam;
        [SerializeField] float damage = 10f;
        [SerializeField] float cooldown = 0.2f;
        [SerializeField, Tooltip("نصف قطر اللمسة بالمتر (عالمي) — كبّره إذا الإصابة صعبة")] float tapRadius = 0.025f;
        [SerializeField] float maxDistance = 10f;
        [SerializeField] LayerMask enemyMask = ~0;

        [Header("Feedback")]
        [SerializeField] GameObject hitVfx;
        [SerializeField] AudioClip hitSfx;
        [SerializeField] AudioClip missSfx;
        [SerializeField] bool vibrateOnHit = false;

        float cooldownTimer;

        void Awake()
        {
            if (cam == null) cam = Camera.main;
        }

        void Update()
        {
            cooldownTimer -= Time.deltaTime;

            var gm = GameManager.Instance;
            if (gm == null || !gm.IsPlaying) return;
            if (BuildManager.Instance != null && BuildManager.Instance.IsDragging) return;

            if (PointerInput.Get(out var pos) != PointerPhase.Began) return;
            if (PointerInput.IsOverUI(pos)) return;
            if (cooldownTimer > 0f) return;

            cooldownTimer = cooldown;
            Shoot(pos, gm);
        }

        void Shoot(Vector2 screenPos, GameManager gm)
        {
            if (cam == null) cam = Camera.main;
            var ray = cam.ScreenPointToRay(screenPos);

            if (Physics.SphereCast(ray, tapRadius, out var hit, maxDistance, enemyMask, QueryTriggerInteraction.Collide))
            {
                var enemy = hit.collider.GetComponentInParent<EnemyAI>();
                if (enemy != null && !enemy.IsDead)
                {
                    enemy.Health.TakeDamage(damage);
                    if (hitVfx != null && gm.Board != null)
                        SimplePool.Spawn(hitVfx, gm.Board, gm.ToBoardLocal(hit.point), Quaternion.identity);
                    if (hitSfx != null) AudioSource.PlayClipAtPoint(hitSfx, hit.point, 0.8f);
                    if (vibrateOnHit) Vibrate();
                    return;
                }
            }

            if (missSfx != null) AudioSource.PlayClipAtPoint(missSfx, cam.transform.position, 0.3f);
        }

        static void Vibrate()
        {
#if UNITY_ANDROID || UNITY_IOS
            Handheld.Vibrate();
#endif
        }
    }
}
