package com.example.chatapp

import android.app.Application

class CoParentApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        // Garante o AuthInterceptor (refresh de token) em toda requisição,
        // sem depender de qual Activity abriu primeiro.
        RetrofitClient.init(this)
    }
}
