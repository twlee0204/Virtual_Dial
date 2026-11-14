package com.example.myapplication

import com.example.myapplication.ui.RotationTracker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs

/** 실제 프로덕션 클래스 [RotationTracker] 를 검증한다. */
class RotaryGestureMathTest {

    private fun rad(deg: Double) = Math.toRadians(deg)

    @Test
    fun clockwiseRotation_producesPositiveSteps() {
        val t = RotationTracker()
        t.start(0.0)
        assertEquals(0, t.update(rad(10.0), 16.0))
        assertEquals(1, t.update(rad(17.0), 16.0))
    }

    @Test
    fun counterClockwiseRotation_producesNegativeSteps() {
        val t = RotationTracker()
        t.start(0.0)
        assertEquals(-1, t.update(rad(-17.0), 16.0))
    }

    @Test
    fun largeJump_producesMultipleSteps_andKeepsRemainder() {
        val t = RotationTracker()
        t.start(0.0)
        assertEquals(2, t.update(rad(35.0), 16.0)) // 35 = 2*16 + 3
        assertEquals(1, t.update(rad(48.0), 16.0)) // 3 + 13 = 16
    }

    @Test
    fun wrapAroundAtPi_isHandled() {
        val t = RotationTracker()
        t.start(rad(175.0))
        // 175° -> -175° 는 시계 방향으로 10° 이동이다.
        assertEquals(0, t.update(rad(-175.0), 16.0))
        assertEquals(1, t.update(rad(-165.0), 16.0))
    }

    @Test
    fun fullCircle_accumulatesExpectedSteps() {
        val t = RotationTracker()
        t.start(0.0)
        var total = 0
        var angle = 0.0
        repeat(360) {
            angle += 1.0
            var a = rad(angle)
            if (a > PI) a -= 2 * PI
            total += t.update(a, 10.0)
        }
        assertTrue("expected about 36 steps but was $total", abs(total - 36) <= 1)
    }

    @Test
    fun reversingDirection_cancelsAccumulation() {
        val t = RotationTracker()
        t.start(0.0)
        assertEquals(0, t.update(rad(12.0), 16.0))
        assertEquals(0, t.update(rad(0.0), 16.0))
        assertEquals(0, t.update(rad(-12.0), 16.0))
        assertEquals(-1, t.update(rad(-17.0), 16.0))
    }

    @Test
    fun update_beforeStart_orAfterStop_isIgnored() {
        val t = RotationTracker()
        assertEquals(0, t.update(rad(90.0), 16.0))
        t.start(0.0)
        t.stop()
        assertEquals(0, t.update(rad(90.0), 16.0))
    }

    @Test
    fun nonFiniteAngles_areIgnored() {
        val t = RotationTracker()
        t.start(0.0)
        assertEquals(0, t.update(Double.NaN, 16.0))
        assertEquals(0, t.update(Double.POSITIVE_INFINITY, 16.0))
        assertEquals(1, t.update(rad(17.0), 16.0))
    }

    @Test
    fun thresholdIsClamped() {
        assertEquals(RotationTracker.MIN_THRESHOLD_DEG, RotationTracker.sanitizeThreshold(0.0), 0.0)
        assertEquals(RotationTracker.MAX_THRESHOLD_DEG, RotationTracker.sanitizeThreshold(500.0), 0.0)
        assertEquals(16.0, RotationTracker.sanitizeThreshold(Double.NaN), 0.0)
        // 0도 임계값이어도 0 으로 나누지 않고 6도 기준으로 동작한다.
        val t = RotationTracker()
        t.start(0.0)
        assertEquals(1, t.update(rad(7.0), 0.0))
    }

    @Test
    fun reanchor_keepsAccumulationButIgnoresJump() {
        val t = RotationTracker()
        t.start(0.0)
        assertEquals(0, t.update(rad(10.0), 16.0))
        t.reanchor(rad(100.0)) // 중심 근처에서 각도가 튄 상황
        assertEquals(0, t.update(rad(105.0), 16.0)) // 10 + 5 = 15 < 16
        assertEquals(1, t.update(rad(107.0), 16.0)) // 17
    }
}
