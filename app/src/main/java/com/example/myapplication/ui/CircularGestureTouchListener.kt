package com.example.myapplication.ui

import android.view.HapticFeedbackConstants
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot

/**
 * 뷰 중심을 축으로 한 원형 회전 제스처 리스너. (시계: 증가, 반시계: 감소)
 *
 * - [longPressMs] > 0 이면 그 시간 동안 움직이지 않고 눌러야 조절이 활성화된다.
 *   활성화 전에 손가락이 터치 슬롭 이상 움직이거나 떼어지면 아무 동작도 하지 않는다. (오작동 방지)
 *   0 이면 터치 즉시 활성화된다.
 * - 터치를 받는 뷰(v)는 투명화하지 않는다. 눈에 보이는 요소(visualView)만 짧게 페이드한다.
 * - 중심 부근(데드존)에서는 각도가 불안정하므로 무시하고, 벗어나는 순간 기준 각도를 다시 잡는다.
 * - 첫 번째 손가락만 추적하며, 부모(ScrollView)가 제스처를 가로채지 못하게 한다.
 */
class CircularGestureTouchListener(
    private val visualView: View? = null,
    private val longPressMs: Long = 0L,
    private val getSensitivityDeg: () -> Double = { 16.0 },
    private val onGestureStart: () -> Unit,
    private val onRotationStep: (isClockwise: Boolean, stepCount: Int) -> Unit,
    private val onGestureEnd: () -> Unit
) : View.OnTouchListener {

    private val tracker = RotationTracker()
    private var activePointerId = MotionEvent.INVALID_POINTER_ID
    private var insideDeadZone = true

    /** 길게 누르기 대기 중 */
    private var pending = false

    /** 조절 활성화됨 */
    private var active = false

    private var downX = 0f
    private var downY = 0f
    private var lastX = 0f
    private var lastY = 0f
    private var pendingView: View? = null
    private val handler = Handler(Looper.getMainLooper())

    private val activateRunnable = Runnable {
        val v = pendingView ?: return@Runnable
        if (pending && v.isAttachedToWindow) activate(v)
    }

    override fun onTouch(v: View, event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                resetState()
                v.parent?.requestDisallowInterceptTouchEvent(true)
                activePointerId = event.getPointerId(0)
                downX = event.x
                downY = event.y
                lastX = downX
                lastY = downY

                if (longPressMs <= 0L) {
                    activate(v)
                } else {
                    pending = true
                    pendingView = v
                    handler.postDelayed(activateRunnable, longPressMs)
                }
                return true
            }

            MotionEvent.ACTION_POINTER_DOWN -> {
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                if (activePointerId == MotionEvent.INVALID_POINTER_ID) return false
                val index = event.findPointerIndex(activePointerId)
                if (index < 0) return true
                val x = event.getX(index)
                val y = event.getY(index)
                lastX = x
                lastY = y

                if (pending) {
                    // 길게 누르는 도중 움직이면 활성화하지 않는다.
                    val slop = ViewConfiguration.get(v.context).scaledTouchSlop
                    if (hypot(x - downX, y - downY) > slop) cancelPending()
                    return true
                }
                if (!active) return true

                if (!isOutsideDeadZone(v, x, y)) {
                    insideDeadZone = true
                    return true
                }
                if (insideDeadZone) {
                    // 데드존을 막 벗어남: 기준 각도를 여기서 다시 잡는다.
                    insideDeadZone = false
                    if (tracker.isActive) tracker.reanchor(angleOf(v, x, y)) else tracker.start(angleOf(v, x, y))
                    return true
                }

                val steps = tracker.update(angleOf(v, x, y), getSensitivityDeg())
                if (steps != 0) {
                    v.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                    onRotationStep(steps > 0, abs(steps))
                }
                return true
            }

            MotionEvent.ACTION_POINTER_UP -> {
                if (event.getPointerId(event.actionIndex) == activePointerId) {
                    finish(v, performClick = false, fadeDelay = 0)
                }
                return true
            }

            MotionEvent.ACTION_UP -> {
                val wasActive = active
                finish(v, performClick = !wasActive, fadeDelay = 300)
                return true
            }

            MotionEvent.ACTION_CANCEL -> {
                finish(v, performClick = false, fadeDelay = 0)
                return true
            }
        }
        return false
    }

    private fun activate(v: View) {
        pending = false
        pendingView = null
        active = true
        insideDeadZone = true
        tracker.stop()
        if (isOutsideDeadZone(v, lastX, lastY)) {
            insideDeadZone = false
            tracker.start(angleOf(v, lastX, lastY))
        }
        fade(1f, 0, 120)
        v.isPressed = true
        v.performHapticFeedback(
            if (longPressMs > 0L) HapticFeedbackConstants.LONG_PRESS else HapticFeedbackConstants.VIRTUAL_KEY
        )
        onGestureStart()
    }

    private fun cancelPending() {
        pending = false
        handler.removeCallbacks(activateRunnable)
        pendingView = null
    }

    private fun resetState() {
        handler.removeCallbacks(activateRunnable)
        pending = false
        pendingView = null
        active = false
        tracker.stop()
        insideDeadZone = true
    }

    private fun finish(v: View, performClick: Boolean, fadeDelay: Long) {
        val wasActive = active
        handler.removeCallbacks(activateRunnable)
        activePointerId = MotionEvent.INVALID_POINTER_ID
        pending = false
        pendingView = null
        active = false
        tracker.stop()
        v.isPressed = false
        v.parent?.requestDisallowInterceptTouchEvent(false)
        if (performClick) v.performClick()
        if (wasActive) {
            fade(0f, fadeDelay, 200)
            onGestureEnd()
        }
    }

    private fun isOutsideDeadZone(v: View, x: Float, y: Float): Boolean {
        val dx = x - v.width / 2f
        val dy = y - v.height / 2f
        val minDist = DEAD_ZONE_DP * v.resources.displayMetrics.density
        return dx * dx + dy * dy >= minDist * minDist
    }

    private fun angleOf(v: View, x: Float, y: Float): Double =
        atan2((y - v.height / 2f).toDouble(), (x - v.width / 2f).toDouble())

    private fun fade(alpha: Float, delay: Long, duration: Long) {
        val target = visualView ?: return
        target.animate().cancel()
        target.animate().alpha(alpha).setStartDelay(delay).setDuration(duration).start()
    }

    private companion object {
        const val DEAD_ZONE_DP = 10f
    }
}
