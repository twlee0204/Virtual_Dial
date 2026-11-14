package com.example.myapplication

import com.example.myapplication.model.ControlFunction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class ControlFunctionTest {

    private val ko = Locale.KOREA

    @Test
    fun testClimateTempSnapAndFormat() {
        val tempFunc = ControlFunction.CLIMATE_TEMP
        assertEquals(17.0f, tempFunc.minValue)
        assertEquals(30.0f, tempFunc.maxValue)
        assertEquals(0.5f, tempFunc.step)

        assertEquals(17.0f, tempFunc.snapValue(15.0f))
        assertEquals(30.0f, tempFunc.snapValue(35.0f))
        assertEquals(22.5f, tempFunc.snapValue(22.3f))
        assertEquals(22.5f, tempFunc.snapValue(22.6f))

        assertEquals(0.0f, tempFunc.getFraction(17.0f), 0.001f)
        assertEquals(1.0f, tempFunc.getFraction(30.0f), 0.001f)
        assertEquals(0.5f, tempFunc.getFraction(23.5f), 0.001f)

        assertEquals("22.5°C", tempFunc.formatValue(22.5f, ko))
    }

    @Test
    fun formatValue_usesGivenLocale_notDefault() {
        // 소수점 구분자가 쉼표인 로케일에서도 지정한 로케일이 우선이어야 한다.
        assertEquals("22,5°C", ControlFunction.CLIMATE_TEMP.formatValue(22.5f, Locale.GERMANY))
        assertEquals("22.5°C", ControlFunction.CLIMATE_TEMP.formatValue(22.5f, Locale.US))
    }

    @Test
    fun testVolumeSnapAndFormat() {
        val volFunc = ControlFunction.VOLUME
        assertEquals(0f, volFunc.snapValue(-10f))
        assertEquals(100f, volFunc.snapValue(120f))
        assertEquals(46f, volFunc.snapValue(45.8f))
        assertEquals("46%", volFunc.formatValue(46f, ko))
    }

    @Test
    fun testFanSpeedSnapAndFormat() {
        val fanFunc = ControlFunction.FAN_SPEED
        assertEquals(3f, fanFunc.snapValue(3.4f))
        assertEquals("3단", fanFunc.formatValue(3f, ko))
    }

    @Test
    fun testBrightnessSnapAndFormat() {
        val brtFunc = ControlFunction.BRIGHTNESS
        assertEquals(70f, brtFunc.snapValue(72f))
        assertEquals("70%", brtFunc.formatValue(70f, ko))
    }

    @Test
    fun testSeatHeatingFormat() {
        val seatFunc = ControlFunction.SEAT_HEATING
        assertEquals("열선 3단", seatFunc.formatValue(3f, ko))
        assertEquals("열선 1단", seatFunc.formatValue(1f, ko))
        assertEquals("OFF", seatFunc.formatValue(0f, ko))
        assertEquals("통풍 1단", seatFunc.formatValue(-1f, ko))
        assertEquals("통풍 3단", seatFunc.formatValue(-3f, ko))
    }

    @Test
    fun snapValue_nanFallsBackToDefault() {
        ControlFunction.entries.forEach {
            assertEquals(it.name, it.defaultInitialValue, it.snapValue(Float.NaN), 0.0001f)
            assertEquals(it.name, it.minValue, it.snapValue(Float.NEGATIVE_INFINITY), 0.0001f)
            assertEquals(it.name, it.maxValue, it.snapValue(Float.POSITIVE_INFINITY), 0.0001f)
        }
    }

    @Test
    fun formatValue_nanDoesNotCrash() {
        ControlFunction.entries.forEach { assertTrue(it.formatValue(Float.NaN, ko).isNotEmpty()) }
    }

    @Test
    fun adjust_clockwiseIncreasesAndCounterClockwiseDecreases() {
        val temp = ControlFunction.CLIMATE_TEMP
        assertEquals(22.5f, temp.adjust(22.0f, isClockwise = true), 0.0001f)
        assertEquals(21.5f, temp.adjust(22.0f, isClockwise = false), 0.0001f)
        assertEquals(24.0f, temp.adjust(22.0f, isClockwise = true, steps = 4), 0.0001f)
    }

    @Test
    fun adjust_clampsAtBounds() {
        ControlFunction.entries.forEach {
            assertEquals(it.maxValue, it.adjust(it.maxValue, true, 3), 0.0001f)
            assertEquals(it.minValue, it.adjust(it.minValue, false, 3), 0.0001f)
            assertEquals(it.maxValue, it.adjust(it.minValue, true, 10_000), 0.0001f)
        }
    }

    @Test
    fun adjust_negativeStepsAreIgnored() {
        val vol = ControlFunction.VOLUME
        assertEquals(46f, vol.adjust(46f, true, -5), 0.0001f)
    }

    @Test
    fun adjust_alwaysStaysOnStepGrid() {
        ControlFunction.entries.forEach { f ->
            var v = f.defaultInitialValue
            repeat(200) { i ->
                v = f.adjust(v, isClockwise = i % 3 != 0, steps = 1 + i % 4)
                assertTrue("${f.name}: $v out of range", v in f.minValue..f.maxValue)
                assertEquals("${f.name}: $v not idempotent", v, f.snapValue(v), 0.0001f)
            }
        }
    }

    @Test
    fun fromName_fallsBackToDefault() {
        assertEquals(ControlFunction.FAN_SPEED, ControlFunction.fromName("FAN_SPEED", ControlFunction.VOLUME))
        assertEquals(ControlFunction.VOLUME, ControlFunction.fromName("REMOVED_FUNCTION", ControlFunction.VOLUME))
        assertEquals(ControlFunction.VOLUME, ControlFunction.fromName(null, ControlFunction.VOLUME))
        assertEquals(ControlFunction.VOLUME, ControlFunction.fromName("", ControlFunction.VOLUME))
    }

    @Test
    fun defaults_areInRange() {
        ControlFunction.entries.forEach {
            assertTrue(it.defaultInitialValue in it.minValue..it.maxValue)
            assertEquals(it.defaultInitialValue, it.snapValue(it.defaultInitialValue), 0.0001f)
        }
    }
}
