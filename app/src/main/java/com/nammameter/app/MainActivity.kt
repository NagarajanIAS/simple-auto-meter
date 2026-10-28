package com.nammameter.app

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import com.nammameter.app.ui.AppNavigation
import com.nammameter.app.ui.theme.NammaMeterTheme

// AppCompatActivity (not ComponentActivity) so the per-app language switch works below Android 13.
class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            NammaMeterTheme {
                AppNavigation()
            }
        }
    }
}
