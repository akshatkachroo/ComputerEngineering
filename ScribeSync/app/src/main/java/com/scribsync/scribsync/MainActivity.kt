package com.scribsync.scribsync

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.scribsync.scribsync.navigation.NavGraph
import com.scribsync.scribsync.ui.theme.ScribeSyncTheme

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
