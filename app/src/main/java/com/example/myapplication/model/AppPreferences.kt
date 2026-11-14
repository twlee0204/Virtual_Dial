package com.example.myapplication.model

import android.content.Context
import android.content.SharedPreferences

/**
 * 기능별 수치, 포인트 색상, 다이얼 기능 매핑, 감도 설정을 관리하는 저장소.
 * 앱 화면과 백그라운드 오버레이 서비스가 같은 값을 공유한다.
 */
class AppPreferences(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var isGlobalOverlayEnabled: Boolean
        get() = prefs.getBoolean(KEY_GLOBAL_OVERLAY, false)
        set(value) = prefs.edit().putBoolean(KEY_GLOBAL_OVERLAY, value).apply()

    fun getFunction(side: DialSide): ControlFunction = when (side) {
        DialSide.LEFT -> ControlFunction.fromName(
            prefs.getString(KEY_LEFT_FUNCTION, null), ControlFunction.CLIMATE_TEMP
        )
        DialSide.RIGHT -> ControlFunction.fromName(
            prefs.getString(KEY_RIGHT_FUNCTION, null), ControlFunction.VOLUME
        )
    }

    fun setFunction(side: DialSide, function: ControlFunction) {
        val key = if (side == DialSide.LEFT) KEY_LEFT_FUNCTION else KEY_RIGHT_FUNCTION
        prefs.edit().putString(key, function.name).apply()
    }

    var themeColorOption: ThemeColorOption
        get() = ThemeColorOption.fromName(prefs.getString(KEY_THEME_COLOR, null))
        set(value) = prefs.edit().putString(KEY_THEME_COLOR, value.name).apply()

    var sensitivity: SensitivityLevel
        get() = SensitivityLevel.fromId(prefs.getInt(KEY_SENSITIVITY, SensitivityLevel.DEFAULT.id))
        set(value) = prefs.edit().putInt(KEY_SENSITIVITY, value.id).apply()

    fun getSensitivityAngleDeg(): Double = sensitivity.angleDeg

    /** 저장된 값이 손상(NaN/범위 밖)되어 있어도 항상 유효한 값을 반환한다. */
    fun getValue(function: ControlFunction): Float =
        function.snapValue(prefs.getFloat(KEY_VAL_PREFIX + function.name, function.defaultInitialValue))

    fun setValue(function: ControlFunction, value: Float) {
        prefs.edit().putFloat(KEY_VAL_PREFIX + function.name, function.snapValue(value)).apply()
    }

    /** 현재 값에서 [steps] 칸 조정한 뒤 저장하고 새 값을 반환한다. */
    fun adjustValue(function: ControlFunction, isClockwise: Boolean, steps: Int = 1): Float {
        val next = function.adjust(getValue(function), isClockwise, steps)
        setValue(function, next)
        return next
    }

    fun clearAll() {
        prefs.edit().clear().apply()
    }

    companion object {
        const val PREFS_NAME = "pleos_rotary_prefs"
        private const val KEY_GLOBAL_OVERLAY = "is_global_overlay_enabled"
        private const val KEY_LEFT_FUNCTION = "left_function"
        private const val KEY_RIGHT_FUNCTION = "right_function"
        private const val KEY_THEME_COLOR = "theme_color"
        private const val KEY_SENSITIVITY = "rotary_sensitivity"
        private const val KEY_VAL_PREFIX = "val_"
    }
}
