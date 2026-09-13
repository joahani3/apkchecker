package com.releasewatch.app.ui.bookshelf

import android.webkit.WebView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.releasewatch.app.ui.viewModelFactory
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookshelfScreen(onBack: () -> Unit) {
    val viewModel: BookshelfViewModel = viewModelFactory { BookshelfViewModel(it.gitHubRepository) }
    val context = LocalContext.current
    val uiState = viewModel.uiState

    // Rendered from private GitHub content, not user input, so JS/DOM inspection risk is the
    // same as opening the local BOOKSHELF.html file directly.
    val webView = remember {
        WebView(context).apply {
            settings.javaScriptEnabled = true
        }
    }

    LaunchedEffect(uiState.html) {
        val html = uiState.html ?: return@LaunchedEffect
        // loadDataWithBaseURL can hit the WebView Binder transaction limit on a multi-MB
        // payload (the bookshelf inlines mermaid.js), so write to a file and load that instead.
        val file = File(context.cacheDir, "bookshelf.html")
        file.writeText(html)
        webView.loadUrl("file://${file.absolutePath}")
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("책장") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로")
                    }
                }
            )
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when {
                uiState.isLoading -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                uiState.errorMessage != null -> Text(
                    text = uiState.errorMessage,
                    modifier = Modifier.align(Alignment.Center).padding(24.dp)
                )
                else -> AndroidView(factory = { webView }, modifier = Modifier.fillMaxSize())
            }
        }
    }
}
