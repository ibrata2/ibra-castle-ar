using System.Collections.Generic;
using UnityEngine;
using UnityEngine.EventSystems;
#if ENABLE_INPUT_SYSTEM
using UnityEngine.InputSystem;
using UnityEngine.InputSystem.EnhancedTouch;
#endif

namespace IbraCastle
{
    public enum PointerPhase { None, Began, Held, Ended }

    /// <summary>
    /// قراءة اللمس/الماوس بشكل موحد — يشتغل مع نظام الإدخال الجديد أو القديم.
    /// Unified touch/mouse reader for both the new Input System and the legacy Input Manager.
    /// </summary>
    public static class PointerInput
    {
        static readonly List<RaycastResult> uiHits = new List<RaycastResult>();
        static PointerEventData pointerData;
        static EventSystem cachedSystem;

#if ENABLE_INPUT_SYSTEM
        static bool touchEnabled;
#endif

        public static PointerPhase Get(out Vector2 position)
        {
            position = Vector2.zero;
#if ENABLE_INPUT_SYSTEM
            if (!touchEnabled) { EnhancedTouchSupport.Enable(); touchEnabled = true; }

            var touches = UnityEngine.InputSystem.EnhancedTouch.Touch.activeTouches;
            if (touches.Count > 0)
            {
                var t = touches[0];
                position = t.screenPosition;
                switch (t.phase)
                {
                    case UnityEngine.InputSystem.TouchPhase.Began: return PointerPhase.Began;
                    case UnityEngine.InputSystem.TouchPhase.Ended:
                    case UnityEngine.InputSystem.TouchPhase.Canceled: return PointerPhase.Ended;
                    default: return PointerPhase.Held;
                }
            }

            var mouse = Mouse.current;
            if (mouse != null)
            {
                position = mouse.position.ReadValue();
                if (mouse.leftButton.wasPressedThisFrame) return PointerPhase.Began;
                if (mouse.leftButton.wasReleasedThisFrame) return PointerPhase.Ended;
                if (mouse.leftButton.isPressed) return PointerPhase.Held;
            }
            return PointerPhase.None;
#else
            if (Input.touchCount > 0)
            {
                var t = Input.GetTouch(0);
                position = t.position;
                switch (t.phase)
                {
                    case UnityEngine.TouchPhase.Began: return PointerPhase.Began;
                    case UnityEngine.TouchPhase.Ended:
                    case UnityEngine.TouchPhase.Canceled: return PointerPhase.Ended;
                    default: return PointerPhase.Held;
                }
            }

            position = Input.mousePosition;
            if (Input.GetMouseButtonDown(0)) return PointerPhase.Began;
            if (Input.GetMouseButtonUp(0)) return PointerPhase.Ended;
            if (Input.GetMouseButton(0)) return PointerPhase.Held;
            return PointerPhase.None;
#endif
        }

        /// <summary>هل اللمسة فوق عنصر واجهة؟ (أزرار، شريط البناء...)</summary>
        public static bool IsOverUI(Vector2 screenPos)
        {
            var es = EventSystem.current;
            if (es == null) return false;
            if (pointerData == null || cachedSystem != es) { pointerData = new PointerEventData(es); cachedSystem = es; }
            pointerData.position = screenPos;
            uiHits.Clear();
            es.RaycastAll(pointerData, uiHits);
            return uiHits.Count > 0;
        }
    }
}
