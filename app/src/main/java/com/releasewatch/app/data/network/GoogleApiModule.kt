package com.releasewatch.app.data.network

import com.squareup.moshi.Moshi
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory

object GoogleApiModule {

    private const val OAUTH_BASE_URL = "https://oauth2.googleapis.com/"
    private const val PUBLISHER_BASE_URL = "https://androidpublisher.googleapis.com/"

    fun createOAuthApi(): GoogleOAuthApi {
        val moshi = Moshi.Builder().build()
        val retrofit = Retrofit.Builder()
            .baseUrl(OAUTH_BASE_URL)
            .client(OkHttpClient.Builder().build())
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
        return retrofit.create(GoogleOAuthApi::class.java)
    }

    fun createAndroidPublisherApi(accessTokenProvider: () -> String?): AndroidPublisherApi {
        val authInterceptor = Interceptor { chain ->
            val requestBuilder = chain.request().newBuilder()
            accessTokenProvider()?.let { token ->
                requestBuilder.addHeader("Authorization", "Bearer $token")
            }
            chain.proceed(requestBuilder.build())
        }

        val okHttpClient = OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
            .build()

        val moshi = Moshi.Builder().build()
        val retrofit = Retrofit.Builder()
            .baseUrl(PUBLISHER_BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
        return retrofit.create(AndroidPublisherApi::class.java)
    }
}
