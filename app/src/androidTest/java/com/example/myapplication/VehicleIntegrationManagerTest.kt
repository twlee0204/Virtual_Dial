package com.example.myapplication

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.myapplication.model.ControlFunction
import com.example.myapplication.vehicle.VehicleIntegrationManager
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class VehicleIntegrationManagerTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
    }

    @Test
    fun getInstance_returnsSameSingletonInstance() {
        val instance1 = VehicleIntegrationManager.getInstance(context)
        val instance2 = VehicleIntegrationManager.getInstance(context)
        assertNotNull(instance1)
        assertSame(instance1, instance2)
    }

    @Test
    fun updateVehicleValue_doesNotCrashForAnyControlFunction() {
        val manager = VehicleIntegrationManager.getInstance(context)
        ControlFunction.entries.forEach { function ->
            manager.updateVehicleValue(function, function.defaultInitialValue)
            manager.updateVehicleValue(function, function.minValue)
            manager.updateVehicleValue(function, function.maxValue)
        }
    }

    @Test
    fun initializeAndRelease_handlesLifecycleSafely() {
        val manager = VehicleIntegrationManager.getInstance(context)
        manager.initialize()
        // Repeated calls to initialize should be idempotent
        manager.initialize()
        manager.release()
        // Repeated calls to release should be idempotent
        manager.release()
    }
}
