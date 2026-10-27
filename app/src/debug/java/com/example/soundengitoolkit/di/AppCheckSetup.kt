package com.example.soundengitoolkit.di

import android.app.Application
import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.appcheck.appCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory

/**
 * Debug build: App Check with the debug provider.
 * On the first run Logcat prints a debug secret (tag DebugAppCheckProvider);
 * add it in Firebase console -> App Check -> Apps -> Manage debug tokens.
 */
object AppCheckSetup {
    fun install(app: Application) {
        try {
            Firebase.appCheck.installAppCheckProviderFactory(DebugAppCheckProviderFactory.getInstance())
        } catch (e: IllegalStateException) {
            // Firebase not configured (no google-services.json): the app still works on rules.
            Log.w("AppCheckSetup", "App Check not installed: ${e.message}")
        }
    }
}
