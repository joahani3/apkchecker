package com.releasewatch.app.data.network

import com.releasewatch.app.data.auth.TokenStore
import com.squareup.moshi.Moshi
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory

object NetworkModule {

    private const val BASE_URL = "https://api.github.com/"

    fun createApi(tokenStore: TokenStore): GitHubApi {
        val authInterceptor = Interceptor { chain ->
            val original = chain.request()
            val requestBuilder = original.newBuilder()
                .addHeader("X-GitHub-Api-Version", "2022-11-28")

            // Only fall back to the default Accept header if the call site (e.g. a raw-content
            // request via @Headers) hasn't already set one.
            if (original.header("Accept") == null) {
                requestBuilder.addHeader("Accept", "application/vnd.github+json")
            }

            tokenStore.getToken()?.let { token ->
                requestBuilder.addHeader("Authorization", "Bearer $token")
            }

            chain.proceed(requestBuilder.build())
        }

        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }

        val okHttpClient = OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
            .addInterceptor(loggingInterceptor)
            .build()

        val moshi = Moshi.Builder().build()

        val retrofit = Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()

        return retrofit.create(GitHubApi::class.java)
    }
}
