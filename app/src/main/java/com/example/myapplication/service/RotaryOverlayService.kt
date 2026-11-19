package com.example.myapplication.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.util.Log
import android.util.TypedValue
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.widget.TextView
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.myapplication.MainActivity
import com.example.myapplication.R
import com.example.myapplication.model.AppPreferences
import com.example.myapplication.model.DialSide
import com.example.myapplication.ui.CircularControlOverlayView
import com.example.myapplication.ui.CircularGestureTouchListener
import com.example.myapplication.util.BeepSoundHelper
import kotlin.math.min

/**
 * 다른 앱 위에서도 화면 아래 좌/우 모서리를 돌려 값을 조절하는 오버레이 서비스.
 *
 * 안정성 원칙
 * - startForeground 를 가장 먼저 호출한다. (호출 전에 stopSelf 하면 ForegroundServiceDidNotStartInTimeException)
 * - 권한/윈도우 추가 실패 시 설정을 꺼 두고 스스로 종료한다. 시스템이 재시작하지 않도록 START_NOT_STICKY.
 * - 터치 패드는 시스템 UI 영역(GNB, 상태 바)을 침범하지 않는다. (FLAG_LAYOUT_NO_LIMITS 미사용)
 */
class RotaryOverlayService : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var appPreferences: AppPreferences
    private lateinit var themedContext: Context
    private var beepSoundHelper: BeepSoundHelper? = null

    private var leftTouchView: View? = null
    private var rightTouchView: View? = null
    private var centerHudView: CircularControlOverlayView? = null
    private var receiverRegistered = false

    private val settingsReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            updateTouchPadUI()
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        updateTouchPadUI()
    }

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        appPreferences = AppPreferences(this)
        themedContext = ContextThemeWrapper(this, R.style.Theme_MyApplication)
        beepSoundHelper = BeepSoundHelper(this)

        ContextCompat.registerReceiver(
            this,
            settingsReceiver,
            IntentFilter(ACTION_SETTINGS_UPDATED),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        receiverRegistered = true
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // 1) 무조건 먼저 포그라운드 승격
        if (!startAsForeground()) {
            disableAndStop()
            return START_NOT_STICKY
        }
        // 2) 권한 확인
        if (!canDrawOverlays(this)) {
            Log.w(TAG, "Overlay permission not granted. Stopping service.")
            disableAndStop()
            return START_NOT_STICKY
        }
        // 3) 뷰는 한 번만 추가
        if (leftTouchView == null && !attachOverlayViews()) {
            disableAndStop()
            return START_NOT_STICKY
        }
        updateTouchPadUI()
        return START_NOT_STICKY
    }

    private fun startAsForeground(): Boolean {
        return try {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel),
                NotificationManager.IMPORTANCE_LOW
            ).apply { setShowBadge(false) }
            getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)

            val pendingIntent = PendingIntent.getActivity(
                this,
                0,
                Intent(this, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            val notification = NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle(getString(R.string.notification_title))
                .setContentText(getString(R.string.notification_text))
                .setSmallIcon(R.drawable.ic_stat_dial)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .build()

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "startForeground failed", e)
            false
        }
    }

    /** 오버레이를 쓸 수 없는 상황: 설정을 끄고 UI 에 알린 뒤 종료한다. */
    private fun disableAndStop() {
        appPreferences.isGlobalOverlayEnabled = false
        runCatching { sendBroadcast(Intent(ACTION_SETTINGS_UPDATED).setPackage(packageName)) }
        runCatching { stopForeground(STOP_FOREGROUND_REMOVE) }
        stopSelf()
    }

    private fun dp(value: Float): Int = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP, value, resources.displayMetrics
    ).toInt()

    private fun attachOverlayViews(): Boolean {
        return try {
            val inflater = LayoutInflater.from(themedContext)
            val padSize = dp(PAD_SIZE_DP)
            val metrics = resources.displayMetrics
            val hudSize = min(dp(HUD_SIZE_DP), min(metrics.widthPixels, metrics.heightPixels))

            val left = inflater.inflate(R.layout.view_corner_touch_pad, null)
            setupPad(left, DialSide.LEFT)
            windowManager.addView(left, padParams(padSize, Gravity.BOTTOM or Gravity.START))
            leftTouchView = left

            val right = inflater.inflate(R.layout.view_corner_touch_pad, null)
            setupPad(right, DialSide.RIGHT)
            windowManager.addView(right, padParams(padSize, Gravity.BOTTOM or Gravity.END))
            rightTouchView = right

            val hud = CircularControlOverlayView(themedContext)
            val hudParams = WindowManager.LayoutParams(
                hudSize,
                hudSize,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                PixelFormat.TRANSLUCENT
            ).apply { gravity = Gravity.CENTER }
            windowManager.addView(hud, hudParams)
            centerHudView = hud
            true
        } catch (e: Exception) {
            Log.e(TAG, "attachOverlayViews failed", e)
            detachOverlayViews()
            false
        }
    }

    private val touchDisableHandlers = mutableMapOf<View, android.os.Handler>()
    private val touchDisableRunnables = mutableMapOf<View, Runnable>()

    private fun setPadTouchable(view: View, touchable: Boolean) {
        val params = view.layoutParams as? WindowManager.LayoutParams ?: return
        if (touchable) {
            params.flags = params.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
        } else {
            params.flags = params.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        }
        runCatching { windowManager.updateViewLayout(view, params) }
    }

    private fun temporarilyDisableTouch(view: View, durationMs: Long = 3000L) {
        setPadTouchable(view, false)
        val handler = touchDisableHandlers.getOrPut(view) { android.os.Handler(android.os.Looper.getMainLooper()) }
        val oldRunnable = touchDisableRunnables[view]
        if (oldRunnable != null) {
            handler.removeCallbacks(oldRunnable)
        }
        val newRunnable = Runnable {
            setPadTouchable(view, true)
        }
        touchDisableRunnables[view] = newRunnable
        handler.postDelayed(newRunnable, durationMs)
    }

    private fun padParams(size: Int, gravityFlags: Int) = WindowManager.LayoutParams(
        size,
        size,
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
        PixelFormat.TRANSLUCENT
    ).apply { gravity = gravityFlags }

    private fun setupPad(view: View, side: DialSide) {
        view.setOnTouchListener(
            CircularGestureTouchListener(
                visualView = view.findViewById(R.id.pad_visual),
                // 길게 눌러야만 조절 활성화: 스치는 터치나 짧은 탭으로 조절창이 뜨지 않는다.
                longPressMs = ViewConfiguration.getLongPressTimeout().toLong().coerceAtLeast(MIN_LONG_PRESS_MS),
                getSensitivityDeg = { appPreferences.getSensitivityAngleDeg() },
                onGestureStart = {
                    val function = appPreferences.getFunction(side)
                    centerHudView?.showControl(
                        function = function,
                        value = appPreferences.getValue(function),
                        accentColor = appPreferences.themeColorOption.resolve(themedContext)
                    )
                },
                onRotationStep = { isClockwise, steps ->
                    val function = appPreferences.getFunction(side)
                    val currentValue = appPreferences.getValue(function)
                    if (function.isAtBoundary(currentValue, isClockwise)) {
                        beepSoundHelper?.playBoundaryWarningBeep()
                    } else {
                        beepSoundHelper?.playStepBeep()
                    }
                    val next = appPreferences.adjustValue(function, isClockwise, steps)
                    centerHudView?.updateValue(next, isClockwise)
                    updateTouchPadUI()
                    sendBroadcast(Intent(ACTION_SETTINGS_UPDATED).setPackage(packageName))
                },
                onGestureEnd = { centerHudView?.scheduleHide(HUD_HIDE_DELAY_MS) },
                onShortTap = {
                    // 짧은 탭/단순 터치 시: 해당 영역 아래의 다른 앱 버튼이 눌리도록 터치를 3초간 완전히 투과시킴
                    temporarilyDisableTouch(view, 3000L)
                }
            )
        )
    }

    private fun updateTouchPadUI() {
        bindPad(leftTouchView, DialSide.LEFT)
        bindPad(rightTouchView, DialSide.RIGHT)
        centerHudView?.setThemeColor(appPreferences.themeColorOption.resolve(themedContext))
    }

    private fun bindPad(view: View?, side: DialSide) {
        view ?: return
        val function = appPreferences.getFunction(side)
        view.findViewById<TextView>(R.id.tv_pad_title)?.text = function.title
        view.findViewById<TextView>(R.id.tv_pad_value)?.text =
            function.formatValue(appPreferences.getValue(function))
    }

    private fun detachOverlayViews() {
        touchDisableHandlers.forEach { (view, handler) ->
            touchDisableRunnables[view]?.let { handler.removeCallbacks(it) }
        }
        touchDisableHandlers.clear()
        touchDisableRunnables.clear()
        // HUD 는 detach 시 스스로 콜백/애니메이션을 정리한다.
        centerHudView?.let { runCatching { windowManager.removeView(it) } }
        leftTouchView?.let { runCatching { windowManager.removeView(it) } }
        rightTouchView?.let { runCatching { windowManager.removeView(it) } }
        leftTouchView = null
        rightTouchView = null
        centerHudView = null
    }

    override fun onDestroy() {
        isRunning = false
        if (receiverRegistered) {
            runCatching { unregisterReceiver(settingsReceiver) }
            receiverRegistered = false
        }
        detachOverlayViews()
        beepSoundHelper?.release()
        beepSoundHelper = null
        super.onDestroy()
    }

    companion object {
        const val ACTION_SETTINGS_UPDATED = "com.example.myapplication.ACTION_SETTINGS_UPDATED"
        private const val TAG = "RotaryOverlayService"
        private const val NOTIFICATION_ID = 2001
        private const val CHANNEL_ID = "channel_rotary_overlay"
        private const val PAD_SIZE_DP = 144f
        private const val HUD_SIZE_DP = 280f
        private const val HUD_HIDE_DELAY_MS = 800L
        private const val MIN_LONG_PRESS_MS = 400L

        @Volatile
        var isRunning: Boolean = false
            private set

        /** 일부 기기(AAOS 포함)에서는 권한 확인 자체가 실패할 수 있으므로 안전하게 감싼다. */
        fun canDrawOverlays(context: Context): Boolean =
            runCatching { Settings.canDrawOverlays(context) }.getOrDefault(false)

        /** @return 서비스 시작 요청 성공 여부. 권한이 없거나 시스템이 거부하면 false. */
        fun startService(context: Context): Boolean {
            if (!canDrawOverlays(context)) return false
            return try {
                ContextCompat.startForegroundService(context, Intent(context, RotaryOverlayService::class.java))
                true
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start service", e)
                false
            }
        }

        fun stopService(context: Context) {
            try {
                context.stopService(Intent(context, RotaryOverlayService::class.java))
            } catch (e: Exception) {
                Log.e(TAG, "Failed to stop service", e)
            }
        }
    }
}
