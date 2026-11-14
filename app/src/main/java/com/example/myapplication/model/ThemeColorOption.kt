package com.example.myapplication.model

import android.content.Context
import androidx.annotation.ColorRes
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import com.example.myapplication.R

/**
 * 다이얼 포인트 색상. 실제 색은 주/야간별 리소스(dial_*)에서 가져오므로
 * 어느 테마에서도 배경 대비 4.5:1 이상이 유지된다.
 */
enum class ThemeColorOption(
    @param:StringRes val labelRes: Int,
    @param:ColorRes val colorRes: Int
) {
    HYUNDAI_BLUE(R.string.color_blue, R.color.dial_blue),
    PLEOS_GREEN(R.string.color_green, R.color.dial_green),
    HYPER_AMBER(R.string.color_amber, R.color.dial_amber),
    NEUTRAL_SILVER(R.string.color_silver, R.color.dial_gray);

    fun resolve(context: Context): Int = ContextCompat.getColor(context, colorRes)

    companion object {
        val DEFAULT = HYUNDAI_BLUE

        /** 이전 버전에서 저장된 값(SUNSET_CORAL, PURE_WHITE 등)은 기본값으로 대체한다. */
        fun fromName(name: String?): ThemeColorOption =
            entries.firstOrNull { it.name == name } ?: DEFAULT
    }
}
