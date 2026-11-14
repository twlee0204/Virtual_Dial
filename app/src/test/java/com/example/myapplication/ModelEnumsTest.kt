package com.example.myapplication

import com.example.myapplication.model.SensitivityLevel
import com.example.myapplication.model.ThemeColorOption
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelEnumsTest {

    @Test
    fun sensitivity_orderedFromLowToHigh() {
        assertTrue(SensitivityLevel.LOW.angleDeg > SensitivityLevel.NORMAL.angleDeg)
        assertTrue(SensitivityLevel.NORMAL.angleDeg > SensitivityLevel.HIGH.angleDeg)
    }

    @Test
    fun sensitivity_fromId_roundTripsAndFallsBack() {
        SensitivityLevel.entries.forEach { assertEquals(it, SensitivityLevel.fromId(it.id)) }
        assertEquals(SensitivityLevel.DEFAULT, SensitivityLevel.fromId(0))
        assertEquals(SensitivityLevel.DEFAULT, SensitivityLevel.fromId(99))
        assertEquals(SensitivityLevel.NORMAL, SensitivityLevel.DEFAULT)
    }

    @Test
    fun sensitivity_anglesStayInTrackerRange() {
        SensitivityLevel.entries.forEach { assertTrue(it.angleDeg in 6.0..35.0) }
    }

    @Test
    fun themeColor_hasFourOptions() {
        assertEquals(4, ThemeColorOption.entries.size)
    }

    @Test
    fun themeColor_legacyNamesFallBackToDefault() {
        assertEquals(ThemeColorOption.DEFAULT, ThemeColorOption.fromName("SUNSET_CORAL"))
        assertEquals(ThemeColorOption.DEFAULT, ThemeColorOption.fromName("PURE_WHITE"))
        assertEquals(ThemeColorOption.DEFAULT, ThemeColorOption.fromName(null))
        assertEquals(ThemeColorOption.DEFAULT, ThemeColorOption.fromName(""))
    }

    @Test
    fun themeColor_validNamesRoundTrip() {
        ThemeColorOption.entries.forEach { assertEquals(it, ThemeColorOption.fromName(it.name)) }
    }
}
