package com.panzane.multiblindtest

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.panzane.multiblindtest.ui.BlindtestApp
import com.panzane.multiblindtest.ui.theme.MultiBlindtestTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MultiBlindtestTheme {
                BlindtestApp()
            }
        }
    }
}
