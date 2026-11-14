package com.example.myapplication.ui

import android.content.Context
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View
import androidx.core.content.ContextCompat
import com.example.myapplication.R
import com.example.myapplication.model.ControlFunction
import kotlin.math.min

/**
 * 조작 중 화면 가운데에 잠깐 표시되는 원형 HUD.
 *
 * Pleos 가이드(Glanceability)에 맞춰 장식 효과(글로우, 펄스, 오버슈트)를 제거하고
 * 큰 숫자 + 진행 호 + 방향만 평평하게 보여준다. 모든 치수는 dp/sp 기반이다.
 */
class CircularControlOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val arcPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val titlePaint = textPaint(Typeface.BOLD)
    private val valuePaint = textPaint(Typeface.BOLD)
    private val directionPaint = textPaint(Typeface.BOLD)

    private val arcRect = RectF()

    private var function: ControlFunction = ControlFunction.VOLUME
    private var value: Float = function.defaultInitialValue
    private var accentColor: Int = 0
    private var direction: Int = 0 // 1 증가, -1 감소, 0 없음

    private var popupColor = 0
    private var borderColor = 0
    private var trackColor = 0
    private var primaryTextColor = 0
    private var secondaryTextColor = 0

    private val hideRunnable = Runnable { animateTo(0f) }

    init {
        alpha = 0f
        loadThemeColors()
        accentColor = ContextCompat.getColor(context, R.color.dial_blue)
    }

    private fun textPaint(style: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.DEFAULT, style)
    }

    private fun dp(v: Float) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, resources.displayMetrics)
    private fun sp(v: Float) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, v, resources.displayMetrics)

    private fun loadThemeColors() {
        popupColor = ContextCompat.getColor(context, R.color.pleos_popup)
        borderColor = ContextCompat.getColor(context, R.color.pleos_divider)
        trackColor = ContextCompat.getColor(context, R.color.pleos_track)
        primaryTextColor = ContextCompat.getColor(context, R.color.pleos_text_primary)
        secondaryTextColor = ContextCompat.getColor(context, R.color.pleos_text_secondary)
    }

    // ---- 공개 API ----------------------------------------------------------------------

    fun setThemeColor(color: Int) {
        if (accentColor != color) {
            accentColor = color
            invalidate()
        }
    }

    fun showControl(function: ControlFunction, value: Float, accentColor: Int, isClockwise: Boolean? = null) {
        this.function = function
        this.value = function.snapValue(value)
        this.accentColor = accentColor
        direction = when (isClockwise) {
            true -> 1
            false -> -1
            null -> 0
        }
        removeCallbacks(hideRunnable)
        animateTo(1f)
        invalidate()
    }

    fun updateValue(newValue: Float, isClockwise: Boolean) {
        value = function.snapValue(newValue)
        direction = if (isClockwise) 1 else -1
        removeCallbacks(hideRunnable)
        if (alpha < 1f) animateTo(1f)
        invalidate()
    }

    fun scheduleHide(delayMs: Long) {
        removeCallbacks(hideRunnable)
        postDelayed(hideRunnable, delayMs)
    }

    // ---- 생명주기 ------------------------------------------------------------------------

    override fun onConfigurationChanged(newConfig: Configuration?) {
        super.onConfigurationChanged(newConfig)
        loadThemeColors()
        invalidate()
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(hideRunnable)
        animate().cancel()
        super.onDetachedFromWindow()
    }

    private fun animateTo(target: Float) {
        // 취소 시에는 후속 동작이 실행되지 않으므로, 진행 중 페이드가 새 표시를 지우지 않는다.
        animate().cancel()
        animate().alpha(target).setStartDelay(0).setDuration(FADE_MS).start()
    }

    // ---- 레이아웃/그리기 ---------------------------------------------------------------

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val desired = dp(DEFAULT_SIZE_DP).toInt()
        val w = resolveSize(desired, widthMeasureSpec)
        val h = resolveSize(desired, heightMeasureSpec)
        val size = min(w, h)
        setMeasuredDimension(size, size)
    }

    override fun onDraw(canvas: Canvas) {
        val size = min(width, height).toFloat()
        if (size <= 0f) return
        val cx = width / 2f
        val cy = height / 2f
        val radius = size / 2f - dp(4f)

        fillPaint.color = popupColor
        canvas.drawCircle(cx, cy, radius, fillPaint)
        borderPaint.color = borderColor
        borderPaint.strokeWidth = dp(2f)
        canvas.drawCircle(cx, cy, radius, borderPaint)

        // 진행 호 (아래가 열린 270도)
        val ringStroke = dp(12f)
        val ringRadius = radius - dp(16f)
        arcRect.set(cx - ringRadius, cy - ringRadius, cx + ringRadius, cy + ringRadius)
        trackPaint.color = trackColor
        trackPaint.strokeWidth = ringStroke
        canvas.drawArc(arcRect, ARC_START, ARC_SWEEP, false, trackPaint)
        val fraction = function.getFraction(value)
        if (fraction > 0f) {
            arcPaint.color = accentColor
            arcPaint.strokeWidth = ringStroke
            canvas.drawArc(arcRect, ARC_START, ARC_SWEEP * fraction, false, arcPaint)
        }

        val innerWidth = ringRadius * 1.5f

        // 제목
        titlePaint.color = secondaryTextColor
        titlePaint.textSize = sp(18f)
        fitText(titlePaint, function.title, innerWidth, sp(18f), sp(14f))
        canvas.drawText(function.title, cx, cy - radius * 0.38f, titlePaint)

        // 값 (가장 크게)
        val valueText = function.formatValue(value)
        valuePaint.color = primaryTextColor
        fitText(valuePaint, valueText, innerWidth, sp(52f), sp(24f))
        val valueBaseline = cy - (valuePaint.ascent() + valuePaint.descent()) / 2f
        canvas.drawText(valueText, cx, valueBaseline, valuePaint)

        // 방향
        if (direction != 0) {
            val label = context.getString(
                if (direction > 0) R.string.direction_increase else R.string.direction_decrease
            )
            directionPaint.color = accentColor
            fitText(directionPaint, label, innerWidth, sp(20f), sp(14f))
            canvas.drawText(label, cx, cy + radius * 0.52f, directionPaint)
        }
    }

    /** 폭에 맞을 때까지 글자 크기를 줄인다 (최소 [minSize] 이상). */
    private fun fitText(paint: Paint, text: String, maxWidth: Float, maxSize: Float, minSize: Float) {
        var size = maxSize
        paint.textSize = size
        while (size > minSize && paint.measureText(text) > maxWidth) {
            size = maxOf(size - sp(1f), minSize)
            paint.textSize = size
        }
    }

    private companion object {
        const val DEFAULT_SIZE_DP = 280f
        const val FADE_MS = 130L
        const val ARC_START = 135f
        const val ARC_SWEEP = 270f
    }
}
