package com.example.myapplication.ui

import kotlin.math.abs

/**
 * 원형 제스처의 각도 변화를 누적해 "몇 칸 돌았는지"를 계산하는 순수 로직.
 * Android 화면 좌표(y 아래쪽 +)에서 atan2(dy, dx) 가 증가하면 시계 방향이다.
 */
class RotationTracker {

    private var prevAngle = 0.0
    private var accumulated = 0.0
    private var active = false

    val isActive: Boolean get() = active

    fun start(angleRad: Double) {
        if (!angleRad.isFinite()) return
        prevAngle = angleRad
        accumulated = 0.0
        active = true
    }

    /** 기준 각도만 다시 잡는다 (누적값 유지). 중심 근처 불안정 구간을 벗어났을 때 사용. */
    fun reanchor(angleRad: Double) {
        if (!angleRad.isFinite()) return
        prevAngle = angleRad
    }

    fun stop() {
        active = false
        accumulated = 0.0
    }

    /**
     * @return 이번 갱신으로 새로 완성된 칸 수. 양수 = 시계 방향(증가), 음수 = 반시계 방향(감소).
     */
    fun update(angleRad: Double, thresholdDeg: Double): Int {
        if (!active || !angleRad.isFinite()) return 0

        var delta = angleRad - prevAngle
        while (delta > Math.PI) delta -= 2 * Math.PI
        while (delta < -Math.PI) delta += 2 * Math.PI
        prevAngle = angleRad
        accumulated += delta

        val threshold = Math.toRadians(sanitizeThreshold(thresholdDeg))
        if (abs(accumulated) < threshold) return 0

        val steps = (abs(accumulated) / threshold).toInt()
        val signed = if (accumulated > 0) steps else -steps
        accumulated -= signed * threshold
        return signed
    }

    companion object {
        const val MIN_THRESHOLD_DEG = 6.0
        const val MAX_THRESHOLD_DEG = 35.0
        private const val DEFAULT_THRESHOLD_DEG = 16.0

        fun sanitizeThreshold(deg: Double): Double =
            if (deg.isFinite()) deg.coerceIn(MIN_THRESHOLD_DEG, MAX_THRESHOLD_DEG) else DEFAULT_THRESHOLD_DEG
    }
}
