package com.example.careguard

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.example.careguard.ui.CareGuardApp
import com.example.careguard.ui.theme.CareGuardTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            CareGuardTheme {
                CareGuardApp()
            }
        }
    }
}
