package com.scribesync.scribesync

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.scribesync.scribesync.navigation.NavGraph
import com.scribesync.scribesync.ui.theme.ScribeSyncTheme
import com.scribesync.scribesync.ui.viewmodel.MeetingViewModel

class MainActivity : ComponentActivity() {
    private val viewModel: MeetingViewModel by viewModels { MeetingViewModel.Factory }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ScribeSyncTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    NavGraph(viewModel = viewModel)
                }
            }
        }
    }
}
