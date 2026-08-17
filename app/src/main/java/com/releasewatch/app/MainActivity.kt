package com.releasewatch.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.releasewatch.app.navigation.ReleaseWatchNavGraph
import com.releasewatch.app.ui.theme.ReleaseWatchTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ReleaseWatchRoot()
        }
    }
}

@Composable
private fun ReleaseWatchRoot() {
    ReleaseWatchTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            ReleaseWatchNavGraph()
        }
    }
}
