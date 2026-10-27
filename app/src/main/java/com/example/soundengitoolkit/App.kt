package com.example.soundengitoolkit

import android.app.Application
import com.example.soundengitoolkit.di.AppCheckSetup
import com.example.soundengitoolkit.di.AppContainer

class App : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        AppCheckSetup.install(this)   // debug provider in debug builds, no-op in release
        container = AppContainer(initialUseFake = false)   // true = run on FakeCommandSource (debug only)
    }
}