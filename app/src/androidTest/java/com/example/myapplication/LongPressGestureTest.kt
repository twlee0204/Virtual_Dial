package com.example.myapplication

import android.content.Context
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.myapplication.ui.CircularGestureTouchListener
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.cos
import kotlin.math.sin

/** 오버레이 패드와 같은 설정(길게 누르기 활성화)의 리스너 동작을 검증한다. */
@RunWith(AndroidJUnit4::class)
class LongPressGestureTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private lateinit var view: View
    private var starts = 0
    private var ends = 0
    private var steps = 0

    @Before
    fun setUp() {
        starts = 0
        ends = 0
        steps = 0
        instrumentation.runOnMainSync {
            val context = ApplicationProvider.getApplicationContext<Context>()
            view = View(context)
            view.layout(0, 0, 400, 400)
            view.setOnTouchListener(
                CircularGestureTouchListener(
                    longPressMs = LONG_PRESS_MS,
                    getSensitivityDeg = { 10.0 },
                    onGestureStart = { starts++ },
                    onRotationStep = { _, count -> steps += count },
                    onGestureEnd = { ends++ }
                )
            )
        }
    }

    private fun send(action: Int, downTime: Long, x: Float, y: Float) {
        instrumentation.runOnMainSync {
            val event = MotionEvent.obtain(downTime, SystemClock.uptimeMillis(), action, x, y, 0)
            view.dispatchTouchEvent(event)
            event.recycle()
        }
    }

    private fun circle(downTime: Long, degrees: Int) {
        val r = 120f
        var d = 0
        while (d < degrees) {
            d += 5
            val rad = Math.toRadians(d.toDouble())
            send(MotionEvent.ACTION_MOVE, downTime, 200f + r * cos(rad).toFloat(), 200f + r * sin(rad).toFloat())
        }
    }

    @Test
    fun shortTap_doesNotActivate() {
        val t = SystemClock.uptimeMillis()
        send(MotionEvent.ACTION_DOWN, t, 320f, 200f)
        send(MotionEvent.ACTION_UP, t, 320f, 200f)
        instrumentation.waitForIdleSync()
        Thread.sleep(LONG_PRESS_MS + 200)
        assertEquals(0, starts)
        assertEquals(0, ends)
        assertEquals(0, steps)
    }

    @Test
    fun rotatingBeforeLongPress_doesNotActivate() {
        val t = SystemClock.uptimeMillis()
        send(MotionEvent.ACTION_DOWN, t, 320f, 200f)
        circle(t, 180) // 즉시 돌리기 시작 -> 길게 누르기 취소
        Thread.sleep(LONG_PRESS_MS + 200)
        send(MotionEvent.ACTION_UP, t, 200f, 320f)
        assertEquals(0, starts)
        assertEquals(0, steps)
    }

    @Test
    fun longPressThenRotate_adjusts() {
        val t = SystemClock.uptimeMillis()
        send(MotionEvent.ACTION_DOWN, t, 320f, 200f)
        Thread.sleep(LONG_PRESS_MS + 200)
        instrumentation.waitForIdleSync()
        assertEquals(1, starts)

        // 활성화 직후 손가락이 있던 지점에서 이어서 돌린다.
        circle(t, 180)
        assertTrue("expected rotation steps, was $steps", steps > 0)

        send(MotionEvent.ACTION_UP, t, 80f, 200f)
        assertEquals(1, ends)
        assertFalse(view.isPressed)
    }

    private companion object {
        const val LONG_PRESS_MS = 400L
    }
}
