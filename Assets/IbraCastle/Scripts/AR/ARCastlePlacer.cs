using System;
using System.Collections.Generic;
using UnityEngine;
using UnityEngine.XR.ARFoundation;
using UnityEngine.XR.ARSubsystems;

namespace IbraCastle
{
    /// <summary>
    /// وضع القلعة بالواقع المعزز:
    /// 1) الكاميرا تكشف سطح أفقي  2) تظهر علامة بحجم الساحة وسط الشاشة
    /// 3) لمسة (أو زر "ضع القلعة") → تنزل الساحة وتنثبت بـ ARAnchor وتختفي شبكة الأسطح.
    /// بالمحرر (بدون AR) يستخدم أرضية وهمية y=0 للتجربة.
    /// </summary>
    public class ARCastlePlacer : MonoBehaviour
    {
        [Header("AR")]
        [SerializeField] ARRaycastManager raycastManager;
        [SerializeField] ARPlaneManager planeManager;
        [SerializeField] Camera cam;

        [Header("Board")]
        [SerializeField, Tooltip("البريفاب اللي فيه GridManager + القلعة + الأرضية")] GameObject boardPrefab;
        [SerializeField, Tooltip("حجم الساحة: 0.06 = ساحة 13 مربع ≈ 78 سم")] float boardScale = 0.06f;
        [SerializeField] Vector2 scaleRange = new Vector2(0.03f, 0.15f);

        [Header("Reticle")]
        [SerializeField, Tooltip("مربع/إطار بحجم 1×1 يتمدد لحجم الساحة")] Transform reticle;

        [Header("Editor testing")]
        [SerializeField] bool editorFallbackPlane = true;

        public bool HasValidPose { get; private set; }
        public bool IsPlaced { get; private set; }
        public float BoardScale => boardScale;
        public event Action<Transform> Placed;

        static readonly List<ARRaycastHit> hits = new List<ARRaycastHit>();
        Pose pose;
        Vector2 boardSizeLocal = new Vector2(13f, 13f);

        void Awake()
        {
            if (cam == null) cam = Camera.main;
            var g = boardPrefab != null ? boardPrefab.GetComponentInChildren<GridManager>() : null;
            if (g != null) boardSizeLocal = g.SizeLocal;
            if (reticle != null) reticle.gameObject.SetActive(false);
        }

        void Update()
        {
            if (IsPlaced) return;

            UpdatePose();

            if (PointerInput.Get(out var p) == PointerPhase.Began && !PointerInput.IsOverUI(p))
                TryPlace();
        }

        void UpdatePose()
        {
            HasValidPose = false;
            var center = new Vector2(Screen.width * 0.5f, Screen.height * 0.5f);

            if (raycastManager != null && raycastManager.Raycast(center, hits, TrackableType.PlaneWithinPolygon))
            {
                foreach (var h in hits)
                {
                    if (h.trackable is ARPlane plane && plane.alignment != PlaneAlignment.HorizontalUp) continue;
                    pose = h.pose;
                    HasValidPose = true;
                    break;
                }
            }

#if UNITY_EDITOR
            if (!HasValidPose && editorFallbackPlane && cam != null)
            {
                var ray = cam.ScreenPointToRay(center);
                if (new Plane(Vector3.up, Vector3.zero).Raycast(ray, out float d))
                {
                    pose = new Pose(ray.GetPoint(d), Quaternion.identity);
                    HasValidPose = true;
                }
            }
#endif

            if (HasValidPose && cam != null)
            {
                // الساحة تواجه اللاعب
                var fwd = Vector3.ProjectOnPlane(pose.position - cam.transform.position, Vector3.up);
                if (fwd.sqrMagnitude > 1e-4f) pose.rotation = Quaternion.LookRotation(fwd.normalized, Vector3.up);
            }

            if (reticle != null)
            {
                reticle.gameObject.SetActive(HasValidPose);
                if (HasValidPose)
                {
                    reticle.SetPositionAndRotation(pose.position, pose.rotation);
                    reticle.localScale = new Vector3(boardSizeLocal.x * boardScale, 1f, boardSizeLocal.y * boardScale);
                }
            }
        }

        /// <summary>سلايدر حجم الساحة (0..1) قبل الوضع.</summary>
        public void SetScaleNormalized(float t) => boardScale = Mathf.Lerp(scaleRange.x, scaleRange.y, Mathf.Clamp01(t));

        /// <summary>زر "ضع القلعة".</summary>
        public void TryPlace()
        {
            if (IsPlaced || !HasValidPose || boardPrefab == null) return;

            var board = Instantiate(boardPrefab, pose.position, pose.rotation);
            board.transform.localScale = Vector3.one * boardScale;
            if (board.GetComponent<ARAnchor>() == null) board.AddComponent<ARAnchor>(); // تثبيت بالعالم الحقيقي

            IsPlaced = true;
            if (reticle != null) reticle.gameObject.SetActive(false);
            StopPlaneDetection();

            GameManager.Instance.BeginGame(board.transform);
            Placed?.Invoke(board.transform);
        }

        void StopPlaneDetection()
        {
            if (planeManager == null) return;
            foreach (var plane in planeManager.trackables) plane.gameObject.SetActive(false);
            planeManager.enabled = false;
        }
    }
}
