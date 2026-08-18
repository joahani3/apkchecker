package com.releasewatch.app.data.install

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import com.releasewatch.app.data.download.GithubAssetFetcher
import com.releasewatch.app.data.network.model.GithubAsset
import java.io.File
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object ApkPackageInspector {

    suspend fun resolvePackageName(context: Context, asset: GithubAsset, token: String?): String? =
        withContext(Dispatchers.IO) {
            val tempFile = File(context.cacheDir, "inspect-${UUID.randomUUID()}.apk")
            try {
                if (!GithubAssetFetcher.downloadToFile(asset, token, tempFile)) return@withContext null
                archiveInfo(context, tempFile.absolutePath)?.packageName
            } catch (e: Exception) {
                null
            } finally {
                tempFile.delete()
            }
        }

    @Suppress("DEPRECATION")
    private fun archiveInfo(context: Context, path: String) =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.packageManager.getPackageArchiveInfo(path, PackageManager.PackageInfoFlags.of(0))
        } else {
            context.packageManager.getPackageArchiveInfo(path, 0)
        }
}
