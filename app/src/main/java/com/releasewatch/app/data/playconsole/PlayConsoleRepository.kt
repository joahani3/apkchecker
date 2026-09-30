package com.releasewatch.app.data.playconsole

import android.util.Log
import com.releasewatch.app.data.auth.TokenStore
import com.releasewatch.app.data.network.AndroidPublisherApi
import com.releasewatch.app.data.network.GitHubApi
import com.releasewatch.app.data.network.GoogleApiModule
import com.releasewatch.app.data.network.GoogleOAuthApi
import com.releasewatch.app.data.network.model.ServiceAccountJson
import com.releasewatch.app.data.network.model.TrackReleaseDto
import com.squareup.moshi.Moshi
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody

// Reads Play Console's internal-testing/production track versions via the Android Publisher API.
// The service account key isn't shipped in the APK (it would be trivially extractable from a
// distributed build); instead it's fetched at runtime from a private GitHub repo using the same
// PAT already used for the bookshelf feature, then held only in memory for this process.
class PlayConsoleRepository(
    private val gitHubApi: GitHubApi,
    private val tokenStore: TokenStore
) {
    private val oauthApi: GoogleOAuthApi by lazy { GoogleApiModule.createOAuthApi() }
    private val publisherApi: AndroidPublisherApi by lazy {
        GoogleApiModule.createAndroidPublisherApi { accessToken }
    }
    private val serviceAccountAdapter = Moshi.Builder().build().adapter(ServiceAccountJson::class.java)

    private val mutex = Mutex()
    private var credentials: ServiceAccountJson? = null
    private var credentialsFetchFailed = false
    private var accessToken: String? = null
    private var accessTokenExpiryMs: Long = 0

    suspend fun fetchTrackVersions(packageName: String): PlayTrackVersions? {
        ensureAccessToken() ?: return null
        return try {
            val editId = insertEdit(packageName) ?: return null
            val closed = getTrackVersion(packageName, editId, "alpha")
            val production = getTrackVersion(packageName, editId, "production")
            deleteEditQuietly(packageName, editId)
            if (closed == null && production == null) null
            else PlayTrackVersions(closedTesting = closed, production = production)
        } catch (e: Exception) {
            Log.w(TAG, "fetchTrackVersions($packageName) failed", e)
            null
        }
    }

    private suspend fun insertEdit(packageName: String): String? {
        val emptyBody = "".toRequestBody("application/json".toMediaType())
        val response = publisherApi.insertEdit(packageName, emptyBody)
        if (!response.isSuccessful) {
            Log.w(TAG, "insertEdit($packageName) failed: HTTP ${response.code()} ${response.errorBody()?.string()}")
            return null
        }
        return response.body()?.id
    }

    private suspend fun getTrackVersion(packageName: String, editId: String, track: String): String? {
        val response = publisherApi.getTrack(packageName, editId, track)
        if (!response.isSuccessful) {
            // 404 just means the app has no release on this track yet; anything else is worth a look.
            if (response.code() != 404) {
                Log.w(TAG, "getTrack($packageName, $track) failed: HTTP ${response.code()} ${response.errorBody()?.string()}")
            }
            return null
        }
        val release = response.body()?.releases?.firstOrNull() ?: return null
        return formatRelease(release)
    }

    private suspend fun deleteEditQuietly(packageName: String, editId: String) {
        try {
            publisherApi.deleteEdit(packageName, editId)
        } catch (e: Exception) {
            // Best-effort cleanup only; unused edits expire on their own after a few hours.
        }
    }

    private fun formatRelease(release: TrackReleaseDto): String {
        val label = release.name?.takeIf { it.isNotBlank() }
        val codes = release.versionCodes?.takeIf { it.isNotEmpty() }?.joinToString(",")
        return when {
            label != null && codes != null -> "$label ($codes)"
            label != null -> label
            codes != null -> "코드 $codes"
            else -> "정보 없음"
        }
    }

    private suspend fun ensureAccessToken(): String? = mutex.withLock {
        val now = System.currentTimeMillis()
        accessToken?.let { if (now < accessTokenExpiryMs) return@withLock it }

        val creds = ensureCredentials() ?: return@withLock null
        val tokenUri = creds.tokenUri ?: DEFAULT_TOKEN_URI
        val jwt = JwtSigner.createSignedJwt(
            clientEmail = creds.clientEmail,
            privateKeyPem = creds.privateKey,
            scope = ANDROID_PUBLISHER_SCOPE,
            audience = tokenUri
        )

        val response = try {
            oauthApi.getAccessToken(assertion = jwt)
        } catch (e: Exception) {
            Log.w(TAG, "OAuth token exchange failed", e)
            return@withLock null
        }
        if (!response.isSuccessful) {
            Log.w(TAG, "OAuth token exchange failed: HTTP ${response.code()} ${response.errorBody()?.string()}")
            return@withLock null
        }
        val body = response.body() ?: return@withLock null

        accessToken = body.accessToken
        accessTokenExpiryMs = now + (body.expiresIn - 60).coerceAtLeast(0) * 1000
        body.accessToken
    }

    private suspend fun ensureCredentials(): ServiceAccountJson? {
        credentials?.let { return it }
        if (credentialsFetchFailed) return null

        return try {
            val owner = tokenStore.getUsername() ?: return null
            val response = gitHubApi.getRawFileContent(owner, SECRET_REPO, SECRET_PATH)
            if (!response.isSuccessful) {
                Log.w(TAG, "Fetching $SECRET_REPO/$SECRET_PATH failed: HTTP ${response.code()}")
                credentialsFetchFailed = true
                return null
            }
            val json = response.body()?.string() ?: return null
            val parsed = serviceAccountAdapter.fromJson(json)
            if (parsed == null) {
                Log.w(TAG, "$SECRET_PATH did not parse as a service-account JSON key")
                credentialsFetchFailed = true
                return null
            }
            credentials = parsed
            parsed
        } catch (e: Exception) {
            Log.w(TAG, "Fetching Play Console service-account credentials failed", e)
            credentialsFetchFailed = true
            null
        }
    }

    private companion object {
        const val TAG = "PlayConsoleRepository"
        const val SECRET_REPO = "coding-bookshelf"
        const val SECRET_PATH = "play-service-account.json"
        const val ANDROID_PUBLISHER_SCOPE = "https://www.googleapis.com/auth/androidpublisher"
        const val DEFAULT_TOKEN_URI = "https://oauth2.googleapis.com/token"
    }
}
