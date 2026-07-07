package com.example.chatapp

import android.content.Context
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object RetrofitClient {

    private var retrofit: Retrofit? = null
    private var apiService: ApiService? = null

    /**
     * Inicializa o RetrofitClient com context para AuthInterceptor.
     * Deve ser chamado uma vez no Application ou LoginActivity.
     */
    fun init(context: Context) {
        val appContext = context.applicationContext

        val okHttpClient = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .addHeader("Accept-Charset", "UTF-8")
                    .addHeader("Accept", "application/json; charset=utf-8")
                    .build()
                chain.proceed(request)
            }
            .addInterceptor(AuthInterceptor(appContext))
            .build()

        retrofit = Retrofit.Builder()
            .baseUrl(BuildConfig.API_BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        apiService = retrofit!!.create(ApiService::class.java)
    }

    val api: ApiService
        get() {
            if (apiService == null) {
                // Fallback sem AuthInterceptor (para antes do init)
                val fallbackClient = OkHttpClient.Builder()
                    .connectTimeout(30, TimeUnit.SECONDS)
                    .readTimeout(30, TimeUnit.SECONDS)
                    .writeTimeout(30, TimeUnit.SECONDS)
                    .addInterceptor { chain ->
                        val request = chain.request().newBuilder()
                            .addHeader("Accept-Charset", "UTF-8")
                            .addHeader("Accept", "application/json; charset=utf-8")
                            .build()
                        chain.proceed(request)
                    }
                    .build()

                apiService = Retrofit.Builder()
                    .baseUrl(BuildConfig.API_BASE_URL)
                    .client(fallbackClient)
                    .addConverterFactory(GsonConverterFactory.create())
                    .build()
                    .create(ApiService::class.java)
            }
            return apiService!!
        }
}
