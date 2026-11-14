package com.example.myapplication

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.myapplication.model.AppPreferences
import com.example.myapplication.model.ControlFunction
import com.example.myapplication.model.DialSide
import com.example.myapplication.model.SensitivityLevel
import com.example.myapplication.model.ThemeColorOption
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppPreferencesTest {

    private lateinit var context: Context
    private lateinit var prefs: AppPreferences

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        prefs = AppPreferences(context)
        prefs.clearAll()
    }

    @Test
    fun defaults() {
        assertEquals(ControlFunction.CLIMATE_TEMP, prefs.getFunction(DialSide.LEFT))
        assertEquals(ControlFunction.VOLUME, prefs.getFunction(DialSide.RIGHT))
        assertEquals(SensitivityLevel.NORMAL, prefs.sensitivity)
        assertEquals(ThemeColorOption.DEFAULT, prefs.themeColorOption)
        assertFalse(prefs.isGlobalOverlayEnabled)
    }

    @Test
    fun valuesPersistAndAreSnapped() {
        prefs.setValue(ControlFunction.CLIMATE_TEMP, 22.3f)
        assertEquals(22.5f, AppPreferences(context).getValue(ControlFunction.CLIMATE_TEMP), 0.0001f)
    }

    @Test
    fun adjustValue_savesAndClamps() {
        assertEquals(24.0f, prefs.adjustValue(ControlFunction.CLIMATE_TEMP, true, 4), 0.0001f)
        assertEquals(24.0f, prefs.getValue(ControlFunction.CLIMATE_TEMP), 0.0001f)
        assertEquals(30.0f, prefs.adjustValue(ControlFunction.CLIMATE_TEMP, true, 1000), 0.0001f)
    }

    @Test
    fun corruptedStoredValues_fallBackSafely() {
        context.getSharedPreferences(AppPreferences.PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putString("left_function", "REMOVED")
            .putString("theme_color", "SUNSET_CORAL")
            .putInt("rotary_sensitivity", 42)
            .putFloat("val_VOLUME", Float.NaN)
            .commit()
        val fresh = AppPreferences(context)
        assertEquals(ControlFunction.CLIMATE_TEMP, fresh.getFunction(DialSide.LEFT))
        assertEquals(ThemeColorOption.DEFAULT, fresh.themeColorOption)
        assertEquals(SensitivityLevel.NORMAL, fresh.sensitivity)
        assertEquals(ControlFunction.VOLUME.defaultInitialValue, fresh.getValue(ControlFunction.VOLUME), 0.0001f)
    }

    @Test
    fun functionAndSettingsPersist() {
        prefs.setFunction(DialSide.LEFT, ControlFunction.FAN_SPEED)
        prefs.sensitivity = SensitivityLevel.HIGH
        prefs.themeColorOption = ThemeColorOption.PLEOS_GREEN
        val fresh = AppPreferences(context)
        assertEquals(ControlFunction.FAN_SPEED, fresh.getFunction(DialSide.LEFT))
        assertEquals(SensitivityLevel.HIGH, fresh.sensitivity)
        assertEquals(ThemeColorOption.PLEOS_GREEN, fresh.themeColorOption)
    }
}
