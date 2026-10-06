package com.example.soundengitoolkit

import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.GenerativeBackend
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import org.json.JSONArray
import org.json.JSONObject

/** Turns a user prompt into a JSON string {"actions":[...]} for Ai.apply(). */
interface CommandSource {
    suspend fun ask(prompt: String): String
}

/** Offline, free, instant. Understands only a few fixed phrases. */
class RulesSource : CommandSource {
    override suspend fun ask(prompt: String): String {
        val a = JSONArray(); val s = prompt.lowercase()
        Regex("(\\d+)\\s*bpm|bpm\\s*(\\d+)").find(s)?.let {
            a.put(JSONObject().put("fx", "bpm").put("value", (it.groupValues[1] + it.groupValues[2]).toInt()))
        }
        if ("delay" in s || "ділей" in s || "дилей" in s) {
            val d = Regex("1/(\\d+)").find(s)?.groupValues?.get(1)?.toInt() ?: 8
            a.put(JSONObject().put("fx", "delay").put("on", true).put("division", d))
        }
        if ("compress" in s || "компрес" in s) a.put(JSONObject().put("fx", "compressor").put("on", true))
        if ("reverb" in s || "ревер" in s) a.put(JSONObject().put("fx", "reverb").put("on", true))
        return JSONObject().put("actions", a).toString()
    }
}

/**
 * Gemini through Firebase AI Logic. No API key in the app;
 * enable App Check in the Firebase console to protect the quota.
 */
class FirebaseSource(
    // Check the current model name in Firebase AI Logic docs; names change over time.
    modelName: String = "gemini-2.5-flash"
) : CommandSource {
    // lazy: Firebase is touched only on the first cloud call, so a missing
    // google-services.json can no longer crash the app at startup.
    private val model by lazy {
        Firebase.ai(backend = GenerativeBackend.googleAI()).generativeModel(
            modelName = modelName,
            generationConfig = generationConfig { responseMimeType = "application/json" },
            systemInstruction = content { text(Ai.SYSTEM) }
        )
    }

    override suspend fun ask(prompt: String): String {
        val text = model.generateContent(prompt).text ?: error("Empty AI response")
        return text.replace("```json", "").replace("```", "").trim()
    }
}

/** Rules first (free, offline). If nothing recognized, fall back to the cloud model. */
class HybridSource(private val rules: CommandSource, private val cloud: CommandSource) : CommandSource {
    override suspend fun ask(prompt: String): String {
        val r = rules.ask(prompt)
        if (JSONObject(r).getJSONArray("actions").length() > 0) return r
        return runCatching { cloud.ask(prompt) }.getOrDefault(r)
    }
}