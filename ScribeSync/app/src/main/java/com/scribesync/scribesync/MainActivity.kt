package com.scribesync.scribesync

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.scribesync.scribesync.navigation.NavGraph
import com.scribesync.scribesync.ui.theme.ScribeSyncTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ScribeSyncTheme {
                NavGraph()
            }
        }
    }
}
