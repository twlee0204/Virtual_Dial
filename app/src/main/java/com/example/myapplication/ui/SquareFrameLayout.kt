package com.example.myapplication.ui

import android.content.Context
import android.util.AttributeSet
import android.util.TypedValue
import android.widget.FrameLayout
import kotlin.math.min

/** 사용 가능한 폭에 맞춰 정사각형이 되되, 최대 크기를 넘지 않는 FrameLayout. */
class SquareFrameLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private val maxSizePx = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP, MAX_SIZE_DP, resources.displayMetrics
    ).toInt()

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val availW = MeasureSpec.getSize(widthMeasureSpec)
        val availH = MeasureSpec.getSize(heightMeasureSpec)
        val wMode = MeasureSpec.getMode(widthMeasureSpec)
        val hMode = MeasureSpec.getMode(heightMeasureSpec)

        var size = maxSizePx
        if (wMode != MeasureSpec.UNSPECIFIED) size = min(size, availW)
        if (hMode != MeasureSpec.UNSPECIFIED && availH > 0) size = min(size, availH)
        size = size.coerceAtLeast(0)

        val exact = MeasureSpec.makeMeasureSpec(size, MeasureSpec.EXACTLY)
        super.onMeasure(exact, exact)
    }

    private companion object {
        const val MAX_SIZE_DP = 240f
    }
}
