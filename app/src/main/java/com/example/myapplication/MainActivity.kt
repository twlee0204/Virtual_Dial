package com.example.myapplication

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import com.example.myapplication.model.AppPreferences
import com.example.myapplication.model.ControlFunction
import com.example.myapplication.model.DialSide
import com.example.myapplication.model.SensitivityLevel
import com.example.myapplication.model.ThemeColorOption
import com.example.myapplication.service.RotaryOverlayService
import com.example.myapplication.ui.CircularGestureTouchListener

/**
 * 설정 화면. 좁은 창(세로 1창)과 넓은 창(가로) 모두에서 동작하도록 한 열 스크롤 레이아웃을 쓰고,
 * 모든 상태는 [AppPreferences] 에 저장되어 창 크기 변경/포그라운드 전환 후에도 복원된다.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var prefs: AppPreferences
    private var beepSoundHelper: com.example.myapplication.util.BeepSoundHelper? = null

    private class DialViews(
        val side: DialSide,
        val title: TextView,
        val value: TextView,
        val zone: View,
        val prev: View,
        val next: View
    )

    private lateinit var dials: List<DialViews>
    private lateinit var sensitivityButtons: Map<SensitivityLevel, Button>
    private lateinit var sensitivityDesc: TextView
    private lateinit var overlaySwitch: SwitchCompat
    private lateinit var overlayStatus: TextView
    private lateinit var swatches: List<View>

    private var updatingUi = false
    private var pendingOverlayEnable = false
    private var overlayMessageRes: Int? = null
    private var receiverRegistered = false

    private val settingsReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val wasChecked = overlaySwitch.isChecked
            refreshAll()
            // 서비스가 스스로 오버레이를 껐다면(권한/윈도우 실패) 이유를 보여준다.
            if (wasChecked && !prefs.isGlobalOverlayEnabled) {
                overlayMessageRes = R.string.overlay_failed
                renderOverlayState()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        prefs = AppPreferences(this)
        beepSoundHelper = com.example.myapplication.util.BeepSoundHelper(this)
        pendingOverlayEnable = savedInstanceState?.getBoolean(STATE_PENDING_OVERLAY, false) ?: false

        bindViews()
        refreshAll()
    }

    override fun onDestroy() {
        beepSoundHelper?.release()
        beepSoundHelper = null
        super.onDestroy()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(STATE_PENDING_OVERLAY, pendingOverlayEnable)
    }

    override fun onStart() {
        super.onStart()
        if (!receiverRegistered) {
            ContextCompat.registerReceiver(
                this,
                settingsReceiver,
                IntentFilter(RotaryOverlayService.ACTION_SETTINGS_UPDATED),
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
            receiverRegistered = true
        }
        refreshAll()
    }

    override fun onResume() {
        super.onResume()
        syncOverlayState()
    }

    override fun onStop() {
        if (receiverRegistered) {
            runCatching { unregisterReceiver(settingsReceiver) }
            receiverRegistered = false
        }
        super.onStop()
    }

    // ---- 뷰 바인딩 ------------------------------------------------------------------------

    private fun bindViews() {
        dials = listOf(
            bindDial(DialSide.LEFT, R.id.dial_left),
            bindDial(DialSide.RIGHT, R.id.dial_right)
        )

        sensitivityDesc = findViewById(R.id.tv_sens_desc)
        sensitivityButtons = mapOf(
            SensitivityLevel.LOW to findViewById<Button>(R.id.btn_sens_low),
            SensitivityLevel.NORMAL to findViewById<Button>(R.id.btn_sens_normal),
            SensitivityLevel.HIGH to findViewById<Button>(R.id.btn_sens_high)
        )
        sensitivityButtons.forEach { (level, button) ->
            button.setOnClickListener {
                prefs.sensitivity = level
                renderSensitivity()
                notifySettingsChanged()
            }
        }

        overlaySwitch = findViewById(R.id.switch_global_overlay)
        overlayStatus = findViewById(R.id.tv_overlay_status)
        overlaySwitch.setOnCheckedChangeListener { _, checked ->
            if (!updatingUi) onOverlayToggled(checked)
        }

        swatches = listOf(R.id.color_0, R.id.color_1, R.id.color_2, R.id.color_3).map { findViewById(it) }
        ThemeColorOption.entries.forEachIndexed { index, option ->
            val item = swatches[index]
            val label = getString(option.labelRes)
            item.findViewById<TextView>(R.id.swatch_label).text = label
            item.contentDescription = label
            item.setOnClickListener {
                prefs.themeColorOption = option
                renderColors()
                notifySettingsChanged()
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun bindDial(side: DialSide, rootId: Int): DialViews {
        val root = findViewById<View>(rootId)
        val views = DialViews(
            side = side,
            title = root.findViewById(R.id.dial_title),
            value = root.findViewById(R.id.dial_value),
            zone = root.findViewById(R.id.dial_zone),
            prev = root.findViewById<ImageButton>(R.id.dial_prev),
            next = root.findViewById<ImageButton>(R.id.dial_next)
        )
        views.prev.setOnClickListener { cycleFunction(side, -1) }
        views.next.setOnClickListener { cycleFunction(side, +1) }
        views.zone.setOnTouchListener(
            CircularGestureTouchListener(
                visualView = null,
                getSensitivityDeg = { prefs.getSensitivityAngleDeg() },
                onGestureStart = {},
                onRotationStep = { isClockwise, steps ->
                    val func = prefs.getFunction(side)
                    val currentValue = prefs.getValue(func)
                    if (func.isAtBoundary(currentValue, isClockwise)) {
                        beepSoundHelper?.playBoundaryWarningBeep()
                    } else {
                        beepSoundHelper?.playStepBeep()
                    }
                    prefs.adjustValue(func, isClockwise, steps)
                    dials.forEach { renderDial(it) }
                    notifySettingsChanged()
                },
                onGestureEnd = {}
            )
        )
        return views
    }

    private fun cycleFunction(side: DialSide, direction: Int) {
        val all = ControlFunction.entries
        val index = all.indexOf(prefs.getFunction(side))
        val next = all[(index + direction + all.size) % all.size]
        prefs.setFunction(side, next)
        dials.first { it.side == side }.let { renderDial(it) }
        notifySettingsChanged()
    }

    // ---- 렌더링 ---------------------------------------------------------------------------

    private fun refreshAll() {
        dials.forEach { renderDial(it) }
        renderSensitivity()
        renderColors()
        renderOverlayState()
    }

    private fun renderDial(views: DialViews) {
        val function = prefs.getFunction(views.side)
        val valueText = function.formatValue(prefs.getValue(function))
        views.title.text = function.title
        views.value.text = valueText
        views.zone.contentDescription = "${function.title} $valueText"
    }

    private fun renderSensitivity() {
        val current = prefs.sensitivity
        sensitivityButtons.forEach { (level, button) -> button.isSelected = level == current }
        sensitivityDesc.setText(
            when (current) {
                SensitivityLevel.LOW -> R.string.sensitivity_desc_low
                SensitivityLevel.NORMAL -> R.string.sensitivity_desc_normal
                SensitivityLevel.HIGH -> R.string.sensitivity_desc_high
            }
        )
    }

    private fun renderColors() {
        val selected = prefs.themeColorOption
        val density = resources.displayMetrics.density
        val borderSelected = ContextCompat.getColor(this, R.color.pleos_text_primary)
        val borderNormal = ContextCompat.getColor(this, R.color.pleos_divider)
        ThemeColorOption.entries.forEachIndexed { index, option ->
            val color = option.resolve(this)
            val isSelected = option == selected
            val swatch = swatches[index].findViewById<ImageView>(R.id.swatch)
            swatch.background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(color)
                setStroke((if (isSelected) 4 else 2).times(density).toInt(), if (isSelected) borderSelected else borderNormal)
            }
            val onColor = if (ColorUtils.calculateLuminance(color) > 0.4) 0xFF0B0C0E.toInt() else 0xFFFFFFFF.toInt()
            swatch.imageTintList = ColorStateList.valueOf(onColor)
            swatch.imageAlpha = if (isSelected) 255 else 0
            swatches[index].isSelected = isSelected
        }
    }

    private fun renderOverlayState() {
        updatingUi = true
        overlaySwitch.isChecked = prefs.isGlobalOverlayEnabled
        updatingUi = false
        val res = overlayMessageRes
            ?: if (prefs.isGlobalOverlayEnabled) R.string.overlay_on else R.string.overlay_off
        overlayStatus.setText(res)
    }

    // ---- 글로벌 오버레이 ----------------------------------------------------------------

    private fun onOverlayToggled(checked: Boolean) {
        overlayMessageRes = null
        when {
            !checked -> {
                pendingOverlayEnable = false
                prefs.isGlobalOverlayEnabled = false
                RotaryOverlayService.stopService(this)
            }
            RotaryOverlayService.canDrawOverlays(this) -> enableOverlay()
            else -> {
                pendingOverlayEnable = true
                overlayMessageRes = R.string.overlay_need_permission
                if (!openOverlayPermissionSettings()) {
                    pendingOverlayEnable = false
                    overlayMessageRes = R.string.overlay_unsupported
                }
            }
        }
        renderOverlayState()
    }

    private fun enableOverlay() {
        prefs.isGlobalOverlayEnabled = true
        if (!RotaryOverlayService.startService(this)) {
            prefs.isGlobalOverlayEnabled = false
            overlayMessageRes = R.string.overlay_failed
        }
    }

    /** 권한 화면 진입 시도. 기기에 따라 없을 수 있으므로 여러 경로를 순서대로 시도한다. */
    private fun openOverlayPermissionSettings(): Boolean {
        val packageUri = Uri.parse("package:$packageName")
        val candidates = listOf(
            Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, packageUri),
            Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION),
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, packageUri)
        )
        for (intent in candidates) {
            try {
                startActivity(intent)
                return true
            } catch (e: Exception) {
                Log.w(TAG, "Settings intent unavailable: ${intent.action}", e)
            }
        }
        return false
    }

    /** 화면에 돌아왔을 때 설정 상태와 실제 서비스/권한 상태를 일치시킨다. */
    private fun syncOverlayState() {
        val canDraw = RotaryOverlayService.canDrawOverlays(this)
        when {
            pendingOverlayEnable -> {
                pendingOverlayEnable = false
                if (canDraw) {
                    overlayMessageRes = null
                    enableOverlay()
                } else {
                    prefs.isGlobalOverlayEnabled = false
                    overlayMessageRes = R.string.overlay_need_permission
                }
            }
            prefs.isGlobalOverlayEnabled -> {
                if (!canDraw) {
                    prefs.isGlobalOverlayEnabled = false
                    overlayMessageRes = R.string.overlay_need_permission
                } else if (!RotaryOverlayService.isRunning && !RotaryOverlayService.startService(this)) {
                    prefs.isGlobalOverlayEnabled = false
                    overlayMessageRes = R.string.overlay_failed
                }
            }
        }
        renderOverlayState()
    }

    private fun notifySettingsChanged() {
        sendBroadcast(Intent(RotaryOverlayService.ACTION_SETTINGS_UPDATED).setPackage(packageName))
    }

    private companion object {
        const val TAG = "MainActivity"
        const val STATE_PENDING_OVERLAY = "pending_overlay_enable"
    }
}
