package com.example.soundengitoolkit.di

import android.app.Application

/**
 * Release build: the debug provider must not ship, so nothing is installed here.
 * Before enabling App Check enforcement, add a real provider (Play Integrity).
 */
object AppCheckSetup {
    fun install(app: Application) = Unit
}
