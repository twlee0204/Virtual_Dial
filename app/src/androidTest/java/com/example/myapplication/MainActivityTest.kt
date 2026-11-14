package com.example.myapplication

import android.content.Context
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDescendantOfA
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.isSelected
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.myapplication.model.AppPreferences
import com.example.myapplication.model.ControlFunction
import com.example.myapplication.model.DialSide
import com.example.myapplication.model.SensitivityLevel
import com.example.myapplication.model.ThemeColorOption
import org.hamcrest.Matchers.allOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.cos
import kotlin.math.sin

@RunWith(AndroidJUnit4::class)
class MainActivityTest {

    private lateinit var prefs: AppPreferences

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        prefs = AppPreferences(context)
        prefs.clearAll()
    }

    private fun inLeft(id: Int) = allOf(withId(id), isDescendantOfA(withId(R.id.dial_left)))

    @Test
    fun launches_andShowsDefaults() {
        ActivityScenario.launch(MainActivity::class.java).use {
            onView(inLeft(R.id.dial_title)).check(matches(withText(ControlFunction.CLIMATE_TEMP.title)))
            onView(withId(R.id.btn_sens_normal)).check(matches(isSelected()))
        }
    }

    @Test
    fun nextAndPrev_cycleFunctionAndPersist() {
        ActivityScenario.launch(MainActivity::class.java).use {
            onView(inLeft(R.id.dial_next)).perform(click())
            val expected = ControlFunction.entries[(ControlFunction.CLIMATE_TEMP.ordinal + 1) % ControlFunction.entries.size]
            onView(inLeft(R.id.dial_title)).check(matches(withText(expected.title)))
            assertEquals(expected, prefs.getFunction(DialSide.LEFT))

            onView(inLeft(R.id.dial_prev)).perform(click())
            assertEquals(ControlFunction.CLIMATE_TEMP, prefs.getFunction(DialSide.LEFT))
        }
    }

    @Test
    fun sensitivityButton_persists() {
        ActivityScenario.launch(MainActivity::class.java).use {
            onView(withId(R.id.btn_sens_high)).perform(click())
            onView(withId(R.id.btn_sens_high)).check(matches(isSelected()))
            assertEquals(SensitivityLevel.HIGH, prefs.sensitivity)
        }
    }

    @Test
    fun swatchSelection_persists() {
        ActivityScenario.launch(MainActivity::class.java).use {
            onView(withId(R.id.color_1)).perform(click())
            assertEquals(ThemeColorOption.entries[1], prefs.themeColorOption)
        }
    }

    @Test
    fun circularGesture_clockwiseIncreasesValue() {
        val before = prefs.getValue(ControlFunction.CLIMATE_TEMP)
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val zone = activity.findViewById<View>(R.id.dial_left).findViewById<View>(R.id.dial_zone)
                drawCircle(zone, clockwise = true, degrees = 180)
            }
            assertTrue(prefs.getValue(ControlFunction.CLIMATE_TEMP) > before)
        }
    }

    @Test
    fun circularGesture_counterClockwiseDecreasesValue() {
        val before = prefs.getValue(ControlFunction.CLIMATE_TEMP)
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val zone = activity.findViewById<View>(R.id.dial_left).findViewById<View>(R.id.dial_zone)
                drawCircle(zone, clockwise = false, degrees = 180)
            }
            assertTrue(prefs.getValue(ControlFunction.CLIMATE_TEMP) < before)
        }
    }

    @Test
    fun recreate_keepsState() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            onView(inLeft(R.id.dial_next)).perform(click())
            scenario.recreate()
            val expected = ControlFunction.entries[(ControlFunction.CLIMATE_TEMP.ordinal + 1) % ControlFunction.entries.size]
            onView(inLeft(R.id.dial_title)).check(matches(allOf(isDisplayed(), withText(expected.title))))
        }
    }

    private fun drawCircle(zone: View, clockwise: Boolean, degrees: Int) {
        val cx = zone.width / 2f
        val cy = zone.height / 2f
        val radius = minOf(zone.width, zone.height) * 0.35f
        val sign = if (clockwise) 1 else -1
        val downTime = SystemClock.uptimeMillis()
        fun point(deg: Int) = floatArrayOf(
            cx + radius * cos(Math.toRadians(deg.toDouble() * sign)).toFloat(),
            cy + radius * sin(Math.toRadians(deg.toDouble() * sign)).toFloat()
        )

        val start = point(0)
        zone.dispatchTouchEvent(MotionEvent.obtain(downTime, downTime, MotionEvent.ACTION_DOWN, start[0], start[1], 0))
        var deg = 0
        while (deg < degrees) {
            deg += 5
            val p = point(deg)
            val t = downTime + deg
            zone.dispatchTouchEvent(MotionEvent.obtain(downTime, t, MotionEvent.ACTION_MOVE, p[0], p[1], 0))
        }
        val end = point(degrees)
        zone.dispatchTouchEvent(
            MotionEvent.obtain(downTime, downTime + degrees + 1, MotionEvent.ACTION_UP, end[0], end[1], 0)
        )
    }
}
