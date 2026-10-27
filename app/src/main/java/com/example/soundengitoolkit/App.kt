package com.example.soundengitoolkit

import android.app.Application

class App : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(initialUseFake = false)   // true = run on FakeCommandSource (debug only)
    }
}