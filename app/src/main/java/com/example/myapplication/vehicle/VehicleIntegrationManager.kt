package com.example.myapplication.vehicle

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.provider.Settings
import android.util.Log
import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.api.model.area.SeatArea
import ai.pleos.playground.vehicle.constant.area.SeatValue
import ai.pleos.playground.vehicle.constant.unit.TemperatureUnit
import ai.pleos.playground.vehicle.listener.HvacFanSpeedListener
import ai.pleos.playground.vehicle.listener.HvacSeatTemperatureListener
import ai.pleos.playground.vehicle.listener.HvacSeatVentilationListener
import ai.pleos.playground.vehicle.listener.HvacTemperatureListener
import com.example.myapplication.model.AppPreferences
import com.example.myapplication.model.ControlFunction
import com.example.myapplication.service.RotaryOverlayService
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * PLEOS Vehicle SDK 및 에뮬레이터(Virtual Console: http://127.0.0.1:8088/)와
 * 앱의 제어 기능(ControlFunction) 간의 실시간 연동을 담당하는 매니저.
 */
@SuppressLint("MissingPermission")
class VehicleIntegrationManager private constructor(context: Context) {

    private val appContext: Context = context.applicationContext
    private var vehicle: Vehicle? = null
    private var isInitialized = false

    private val seatAreaLeft = SeatArea(SeatValue.ROW_1_LEFT)

    private val tempListener = object : HvacTemperatureListener {
        override fun onHvacTemperatureUpdated(temperature: Float?) {
            temperature?.let {
                Log.d(TAG, "onHvacTemperatureUpdated: $it")
                updateFromVehicle(ControlFunction.CLIMATE_TEMP, it)
            }
        }
        override fun onFailed(e: Exception) {
            Log.w(TAG, "tempListener onFailed", e)
        }
    }

    private val fanListener = object : HvacFanSpeedListener {
        override fun onHvacFanSpeedUpdated(speed: Int?) {
            speed?.let {
                Log.d(TAG, "onHvacFanSpeedUpdated: $it")
                updateFromVehicle(ControlFunction.FAN_SPEED, it.toFloat())
            }
        }
        override fun onFailed(e: Exception) {
            Log.w(TAG, "fanListener onFailed", e)
        }
    }

    private val seatTempListener = object : HvacSeatTemperatureListener {
        override fun onHvacSeatTemperatureUpdated(temperature: Int?) {
            temperature?.let { level ->
                Log.d(TAG, "onHvacSeatTemperatureUpdated: $level")
                if (level > 0) {
                    updateFromVehicle(ControlFunction.SEAT_HEATING, level.toFloat())
                } else if (level == 0) {
                    val prefs = AppPreferences(appContext)
                    if (prefs.getValue(ControlFunction.SEAT_HEATING) > 0) {
                        updateFromVehicle(ControlFunction.SEAT_HEATING, 0f)
                    }
                }
            }
        }
        override fun onFailed(e: Exception) {
            Log.w(TAG, "seatTempListener onFailed", e)
        }
    }

    private val seatVentListener = object : HvacSeatVentilationListener {
        override fun onHvacSeatVentilationUpdated(ventilation: Int?) {
            ventilation?.let { level ->
                Log.d(TAG, "onHvacSeatVentilationUpdated: $level")
                if (level > 0) {
                    updateFromVehicle(ControlFunction.SEAT_HEATING, -level.toFloat())
                } else if (level == 0) {
                    val prefs = AppPreferences(appContext)
                    if (prefs.getValue(ControlFunction.SEAT_HEATING) < 0) {
                        updateFromVehicle(ControlFunction.SEAT_HEATING, 0f)
                    }
                }
            }
        }
        override fun onFailed(e: Exception) {
            Log.w(TAG, "seatVentListener onFailed", e)
        }
    }

    @Synchronized
    fun initialize() {
        if (isInitialized) return
        try {
            val v = Vehicle(appContext)
            v.initialize()
            vehicle = v
            isInitialized = true

            registerListeners()
            syncInitialVehicleState()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize Vehicle SDK", e)
        }
    }

    private fun registerListeners() {
        val v = vehicle ?: return
        runCatching { v.getHVAC().registerHvacTemperature(seatAreaLeft, tempListener) }
            .onFailure { Log.w(TAG, "registerHvacTemperature failed", it) }
        runCatching { v.getHVAC().registerHvacFanSpeed(seatAreaLeft, fanListener) }
            .onFailure { Log.w(TAG, "registerHvacFanSpeed failed", it) }
        runCatching { v.getSeat().registerHvacSeatTemperature(seatAreaLeft, seatTempListener) }
            .onFailure { Log.w(TAG, "registerHvacSeatTemperature failed", it) }
        runCatching { v.getSeat().registerHvacSeatVentilation(seatAreaLeft, seatVentListener) }
            .onFailure { Log.w(TAG, "registerHvacSeatVentilation failed", it) }
    }

    private fun syncInitialVehicleState() {
        val v = vehicle ?: return
        runCatching {
            v.getHVAC().getHvacTemperature(
                seatAreaLeft,
                TemperatureUnit.CELSIUS,
                { temp -> temp?.let { updateFromVehicle(ControlFunction.CLIMATE_TEMP, it) } },
                { e -> Log.w(TAG, "getHvacTemperature failed", e) }
            )
        }
        runCatching {
            v.getHVAC().getHvacFanSpeed(
                seatAreaLeft,
                { speed -> speed?.let { updateFromVehicle(ControlFunction.FAN_SPEED, it.toFloat()) } },
                { e -> Log.w(TAG, "getHvacFanSpeed failed", e) }
            )
        }
        runCatching {
            v.getSeat().getHvacSeatTemperature(
                seatAreaLeft,
                { temp -> temp?.let { if (it > 0) updateFromVehicle(ControlFunction.SEAT_HEATING, it.toFloat()) } },
                { e -> Log.w(TAG, "getHvacSeatTemperature failed", e) }
            )
        }
        runCatching {
            v.getSeat().getHvacSeatVentilationState(
                seatAreaLeft,
                { vent -> vent?.let { if (it > 0) updateFromVehicle(ControlFunction.SEAT_HEATING, -it.toFloat()) } },
                { e -> Log.w(TAG, "getHvacSeatVentilationState failed", e) }
            )
        }
    }

    private fun updateFromVehicle(function: ControlFunction, value: Float) {
        val prefs = AppPreferences(appContext)
        val current = prefs.getValue(function)
        val snapped = function.snapValue(value)
        if (abs(current - snapped) > 0.01f) {
            prefs.setValue(function, snapped, notifyVehicle = false)
            appContext.sendBroadcast(
                Intent(RotaryOverlayService.ACTION_SETTINGS_UPDATED).setPackage(appContext.packageName)
            )
        }
    }

    /**
     * 노브 제스처나 UI 조작에 의해 값이 변경되었을 때 PLEOS 에뮬레이터/차량 서비스로 전송.
     */
    fun updateVehicleValue(function: ControlFunction, value: Float) {
        val snapped = function.snapValue(value)
        val v = vehicle

        when (function) {
            ControlFunction.CLIMATE_TEMP -> {
                runCatching {
                    v?.getHVAC()?.setHvacTemperature(
                        seatAreaLeft,
                        snapped,
                        TemperatureUnit.CELSIUS,
                        { _, _ -> Log.d(TAG, "setHvacTemperature complete: $snapped") },
                        { e -> Log.w(TAG, "setHvacTemperature failed", e) }
                    )
                }.onFailure { Log.w(TAG, "setHvacTemperature exception", it) }
            }
            ControlFunction.FAN_SPEED -> {
                runCatching {
                    v?.getHVAC()?.setHvacFanSpeed(
                        seatAreaLeft,
                        snapped.toInt(),
                        { _, _ -> Log.d(TAG, "setHvacFanSpeed complete: ${snapped.toInt()}") },
                        { e -> Log.w(TAG, "setHvacFanSpeed failed", e) }
                    )
                }.onFailure { Log.w(TAG, "setHvacFanSpeed exception", it) }
            }
            ControlFunction.SEAT_HEATING -> {
                val level = snapped.toInt()
                runCatching {
                    if (level > 0) {
                        v?.getSeat()?.setHvacSeatTemperature(
                            seatAreaLeft,
                            level,
                            { _, _ -> },
                            { e -> Log.w(TAG, "setHvacSeatTemperature failed", e) }
                        )
                        v?.getSeat()?.setHvacSeatVentilation(
                            seatAreaLeft,
                            0,
                            { _, _ -> },
                            { e -> Log.w(TAG, "setHvacSeatVentilation reset failed", e) }
                        )
                    } else if (level < 0) {
                        v?.getSeat()?.setHvacSeatVentilation(
                            seatAreaLeft,
                            -level,
                            { _, _ -> },
                            { e -> Log.w(TAG, "setHvacSeatVentilation failed", e) }
                        )
                        v?.getSeat()?.setHvacSeatTemperature(
                            seatAreaLeft,
                            0,
                            { _, _ -> },
                            { e -> Log.w(TAG, "setHvacSeatTemperature reset failed", e) }
                        )
                    } else {
                        v?.getSeat()?.setHvacSeatTemperature(
                            seatAreaLeft,
                            0,
                            { _, _ -> },
                            { e -> Log.w(TAG, "setHvacSeatTemperature reset failed", e) }
                        )
                        v?.getSeat()?.setHvacSeatVentilation(
                            seatAreaLeft,
                            0,
                            { _, _ -> },
                            { e -> Log.w(TAG, "setHvacSeatVentilation reset failed", e) }
                        )
                    }
                }.onFailure { Log.w(TAG, "SEAT_HEATING update exception", it) }
            }
            ControlFunction.VOLUME -> {
                runCatching {
                    val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
                    if (audioManager != null) {
                        val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                        val targetVol = ((snapped / 100f) * maxVol).roundToInt().coerceIn(0, maxVol)
                        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, targetVol, 0)
                        Log.d(TAG, "setStreamVolume MUSIC to $targetVol / $maxVol")
                    }
                }.onFailure { Log.w(TAG, "VOLUME control failed", it) }
            }
            ControlFunction.BRIGHTNESS -> {
                runCatching {
                    val target255 = ((snapped / 100f) * 255f).roundToInt().coerceIn(0, 255)
                    if (Settings.System.canWrite(appContext)) {
                        Settings.System.putInt(appContext.contentResolver, Settings.System.SCREEN_BRIGHTNESS, target255)
                        Log.d(TAG, "SCREEN_BRIGHTNESS set to $target255")
                    }
                }.onFailure { Log.w(TAG, "BRIGHTNESS control failed", it) }
            }
        }
    }

    @Synchronized
    fun release() {
        if (!isInitialized) return
        val v = vehicle
        if (v != null) {
            runCatching { v.getHVAC().unregisterHvacTemperature(seatAreaLeft, tempListener) }
            runCatching { v.getHVAC().unregisterHvacFanSpeed(seatAreaLeft, fanListener) }
            runCatching { v.getSeat().unregisterHvacSeatTemperature(seatAreaLeft, seatTempListener) }
            runCatching { v.getSeat().unregisterHvacSeatVentilation(seatAreaLeft, seatVentListener) }
            runCatching { v.release() }
            vehicle = null
        }
        isInitialized = false
    }

    companion object {
        private const val TAG = "VehicleIntegrationMgr"

        @Volatile
        private var instance: VehicleIntegrationManager? = null

        fun getInstance(context: Context): VehicleIntegrationManager {
            return instance ?: synchronized(this) {
                instance ?: VehicleIntegrationManager(context).also { instance = it }
            }
        }
    }
}
