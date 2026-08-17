package com.releasewatch.app.data.download

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.widget.Toast
import com.releasewatch.app.data.network.model.GithubAsset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

object ApkDownloader {

    private val httpClient = OkHttpClient()

    suspend fun download(context: Context, asset: GithubAsset, token: String?) {
        val resolvedUrl = resolveFinalUrl(asset.browserDownloadUrl, token)

        val request = DownloadManager.Request(Uri.parse(resolvedUrl)).apply {
            setTitle(asset.name)
            setDescription("ReleaseWatch")
            setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, asset.name)
            setMimeType("application/vnd.android.package-archive")
        }

        val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        downloadManager.enqueue(request)
        Toast.makeText(context, "${asset.name} 다운로드를 시작합니다", Toast.LENGTH_SHORT).show()
    }

    // browser_download_url redirects to a pre-signed S3 URL. DownloadManager replays every
    // request header (including Authorization) on the redirected request, and S3 rejects a
    // pre-signed URL that also carries an Authorization header. Resolving the redirect here
    // lets OkHttp drop Authorization once the host changes, so DownloadManager only ever sees
    // the final, self-authenticating URL.
    private suspend fun resolveFinalUrl(url: String, token: String?): String = withContext(Dispatchers.IO) {
        try {
            val requestBuilder = Request.Builder()
                .url(url)
                .header("Accept", "application/octet-stream")
            token?.let { requestBuilder.header("Authorization", "Bearer $it") }

            httpClient.newCall(requestBuilder.build()).execute().use { response ->
                response.request.url.toString()
            }
        } catch (e: Exception) {
            url
        }
    }
}
