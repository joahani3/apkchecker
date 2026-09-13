package com.releasewatch.app.ui.bookshelf

import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
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
import androidx.webkit.WebViewAssetLoader
import com.releasewatch.app.ui.viewModelFactory
import java.io.File

private const val BOOKSHELF_VIRTUAL_URL = "https://appassets.androidplatform.net/bookshelf/bookshelf.html"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookshelfScreen(onBack: () -> Unit) {
    val viewModel: BookshelfViewModel = viewModelFactory { BookshelfViewModel(it.gitHubRepository) }
    val context = LocalContext.current
    val uiState = viewModel.uiState

    // Rendered from private GitHub content, not user input, so JS/DOM inspection risk is the
    // same as opening the local BOOKSHELF.html file directly.
    val webView = remember {
        val cacheSubDir = File(context.cacheDir, "bookshelf").apply { mkdirs() }
        // file:// URLs into app-private storage get blocked (net::ERR_ACCESS_DENIED) on recent
        // WebView builds, so serve the cached HTML over a virtual https domain instead —
        // Google's recommended way to load local content into a WebView.
        val assetLoader = WebViewAssetLoader.Builder()
            .addPathHandler("/bookshelf/", WebViewAssetLoader.InternalStoragePathHandler(context, cacheSubDir))
            .build()

        WebView(context).apply {
            settings.javaScriptEnabled = true
            webViewClient = object : WebViewClient() {
                override fun shouldInterceptRequest(
                    view: WebView,
                    request: WebResourceRequest
                ): WebResourceResponse? = assetLoader.shouldInterceptRequest(request.url)
            }
        }
    }

    LaunchedEffect(uiState.html) {
        val html = uiState.html ?: return@LaunchedEffect
        File(context.cacheDir, "bookshelf/bookshelf.html").writeText(html)
        webView.loadUrl(BOOKSHELF_VIRTUAL_URL)
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
