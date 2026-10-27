package com.example.careguard

import android.app.Application
import com.example.careguard.service.NotificationHelper

class CareGuardApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        NotificationHelper.createAlertChannel(this)
    }
}
