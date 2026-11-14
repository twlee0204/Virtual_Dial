package com.example.myapplication.model

import java.util.Locale

enum class ControlFunction(
    val title: String,
    val symbol: String,
    val minValue: Float,
    val maxValue: Float,
    val step: Float,
    val defaultInitialValue: Float
) {
    VOLUME(
        title = "미디어 볼륨",
        symbol = "VOL",
        minValue = 0f,
        maxValue = 100f,
        step = 2f,
        defaultInitialValue = 46f
    ),
    CLIMATE_TEMP(
        title = "공조 온도",
        symbol = "TEMP",
        minValue = 17.0f,
        maxValue = 30.0f,
        step = 0.5f,
        defaultInitialValue = 22.0f
    ),
    FAN_SPEED(
        title = "공조 풍량",
        symbol = "FAN",
        minValue = 1f,
        maxValue = 8f,
        step = 1f,
        defaultInitialValue = 3f
    ),
    BRIGHTNESS(
        title = "화면 밝기",
        symbol = "BRT",
        minValue = 10f,
        maxValue = 100f,
        step = 5f,
        defaultInitialValue = 70f
    ),
    SEAT_HEATING(
        title = "시트 열선/통풍",
        symbol = "SEAT",
        minValue = -3f,
        maxValue = 3f,
        step = 1f,
        defaultInitialValue = 0f
    );

    fun formatValue(value: Float, locale: Locale = Locale.getDefault()): String {
        val safe = if (value.isNaN()) defaultInitialValue else value
        return when (this) {
            VOLUME -> "${safe.toInt()}%"
            CLIMATE_TEMP -> String.format(locale, "%.1f°C", safe)
            FAN_SPEED -> "${safe.toInt()}단"
            BRIGHTNESS -> "${safe.toInt()}%"
            SEAT_HEATING -> {
                val level = safe.toInt()
                when {
                    level > 0 -> "열선 ${level}단"
                    level < 0 -> "통풍 ${-level}단"
                    else -> "OFF"
                }
            }
        }
    }

    /** 범위 안으로 보정하고 step 배수로 맞춘다. NaN 은 기본값으로 대체한다. */
    fun snapValue(value: Float): Float {
        if (value.isNaN()) return defaultInitialValue
        val clamped = value.coerceIn(minValue, maxValue)
        val steps = Math.round((clamped - minValue) / step)
        val snapped = minValue + (steps * step)
        return (Math.round(snapped * 10f) / 10f).coerceIn(minValue, maxValue)
    }

    /** [steps] 칸만큼 시계(+)/반시계(-) 방향으로 값을 조정한 결과를 반환한다. */
    fun adjust(current: Float, isClockwise: Boolean, steps: Int = 1): Float {
        val count = steps.coerceAtLeast(0)
        val delta = step * count
        return snapValue(snapValue(current) + if (isClockwise) delta else -delta)
    }

    fun getFraction(value: Float): Float {
        val clamped = snapValue(value)
        return if (maxValue > minValue) {
            (clamped - minValue) / (maxValue - minValue)
        } else {
            0f
        }
    }

    companion object {
        /** 저장된 이름으로부터 안전하게 복원한다. 알 수 없는 값이면 [default]. */
        fun fromName(name: String?, default: ControlFunction): ControlFunction {
            if (name == null) return default
            return entries.firstOrNull { it.name == name } ?: default
        }
    }
}
