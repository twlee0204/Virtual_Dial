package com.example.myapplication

import android.app.Application
import com.example.myapplication.vehicle.VehicleIntegrationManager

class MyApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        VehicleIntegrationManager.getInstance(this).initialize()
    }

    override fun onTerminate() {
        VehicleIntegrationManager.getInstance(this).release()
        super.onTerminate()
    }
}
