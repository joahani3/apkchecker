package com.releasewatch.app.data.download

import com.releasewatch.app.data.network.model.GithubAsset
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

object GithubAssetFetcher {

    private val httpClient = OkHttpClient()

    // The asset's API url redirects to a pre-signed, time-limited blob storage URL. Replaying
    // the Authorization header on that redirected request makes the signed URL's host reject
    // it, so the redirect is resolved here and callers only ever see the final URL.
    suspend fun resolveFinalUrl(asset: GithubAsset, token: String?): String = withContext(Dispatchers.IO) {
        try {
            val requestBuilder = Request.Builder()
                .url(asset.apiUrl)
                .header("Accept", "application/octet-stream")
            token?.let { requestBuilder.header("Authorization", "Bearer $it") }

            httpClient.newCall(requestBuilder.build()).execute().use { response ->
                response.request.url.toString()
            }
        } catch (e: Exception) {
            asset.apiUrl
        }
    }

    suspend fun downloadToFile(asset: GithubAsset, token: String?, destination: File): Boolean =
        withContext(Dispatchers.IO) {
            try {
                val resolvedUrl = resolveFinalUrl(asset, token)
                val request = Request.Builder().url(resolvedUrl).build()
                httpClient.newCall(request).execute().use { response ->
                    val body = response.body
                    if (!response.isSuccessful || body == null) return@withContext false
                    destination.outputStream().use { out -> body.byteStream().copyTo(out) }
                    true
                }
            } catch (e: Exception) {
                false
            }
        }
}
