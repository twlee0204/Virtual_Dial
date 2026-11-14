package com.example.myapplication

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.myapplication.model.AppPreferences
import com.example.myapplication.service.RotaryOverlayService
import org.junit.Assert.assertFalse
import org.junit.Assume.assumeFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RotaryOverlayServiceTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        AppPreferences(context).clearAll()
    }

    /** 권한이 없으면 서비스를 시작하지 않고 false 를 돌려줘야 한다 (크래시/먹통 방지). */
    @Test
    fun startService_withoutPermission_returnsFalse() {
        assumeFalse(RotaryOverlayService.canDrawOverlays(context))
        assertFalse(RotaryOverlayService.startService(context))
        assertFalse(AppPreferences(context).isGlobalOverlayEnabled)
    }

    /** 권한 없이 서비스가 직접 시작돼도 예외 없이 스스로 종료하고 설정을 꺼야 한다. */
    @Test
    fun directStart_withoutPermission_disablesPreferenceAndStops() {
        assumeFalse(RotaryOverlayService.canDrawOverlays(context))
        val prefs = AppPreferences(context)
        prefs.isGlobalOverlayEnabled = true

        context.startForegroundService(Intent(context, RotaryOverlayService::class.java))

        val deadline = System.currentTimeMillis() + 5_000
        while (System.currentTimeMillis() < deadline && prefs.isGlobalOverlayEnabled) {
            Thread.sleep(100)
        }
        assertFalse(prefs.isGlobalOverlayEnabled)
        Thread.sleep(300)
        assertFalse(RotaryOverlayService.isRunning)
    }

    @Test
    fun stopService_whenNotRunning_isSafe() {
        RotaryOverlayService.stopService(context)
        RotaryOverlayService.stopService(context)
    }
}
