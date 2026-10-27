package com.example.soundengitoolkit.ai.data

import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.GenerativeBackend
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import org.json.JSONArray
import org.json.JSONObject
import com.example.soundengitoolkit.ai.domain.AiError
import com.example.soundengitoolkit.ai.domain.AiException
import com.example.soundengitoolkit.ai.domain.CommandReply
import com.example.soundengitoolkit.ai.domain.CommandSource

/** Offline, free, instant. Understands only a few fixed phrases. */
class RulesSource : CommandSource {
    override suspend fun ask(prompt: String): CommandReply {
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
        return CommandReply(JSONObject().put("actions", a).toString(), "rules")
    }
}

/**
 * Gemini through Firebase AI Logic. No API key in the app;
 * enable App Check in the Firebase console to protect the quota.
 * The system prompt is passed in, so this class does not depend on [Ai].
 * Firebase SDK failures are turned into [AiException] here: SDK types do not leave this class.
 */
class FirebaseSource(
    private val systemPrompt: String,
    // Check the current model name in Firebase AI Logic docs; names change over time.
    modelName: String = "gemini-3.5-flash"
) : CommandSource {
    // lazy: Firebase is touched only on the first cloud call, so a missing
    // google-services.json can no longer crash the app at startup.
    private val model by lazy {
        Firebase.ai(backend = GenerativeBackend.googleAI()).generativeModel(
            modelName = modelName,
            generationConfig = generationConfig { responseMimeType = "application/json" },
            systemInstruction = content { text(systemPrompt) }
        )
    }

    override suspend fun ask(prompt: String): CommandReply {
        val text = try {
            model.generateContent(prompt).text
        } catch (e: CancellationException) {
            throw e
        } catch (e: java.io.IOException) {
            throw e                                        // mapped to Network by AiErrorMapper
        } catch (e: Exception) {
            throw AiException(AiError.AiUnavailable, e)    // Firebase not configured, quota, model refused
        } ?: throw AiException(AiError.AiUnavailable)      // empty reply
        return CommandReply(text.replace("```json", "").replace("```", "").trim(), "cloud")
    }
}

/** Rules first (free, offline). If nothing recognized, fall back to the cloud model. Stateless. */
class HybridSource(private val rules: CommandSource, private val cloud: CommandSource) : CommandSource {
    override suspend fun ask(prompt: String): CommandReply {
        val r = rules.ask(prompt)
        if (JSONObject(r.json).getJSONArray("actions").length() > 0) return r
        return try {
            cloud.ask(prompt).copy(source = "cloud")
        } catch (e: CancellationException) {
            throw e                                        // cancellation is not a cloud failure
        } catch (e: Exception) {
            // For diagnosing cloud failures, temporarily add here:
            // android.util.Log.w("HybridSource", "cloud failed", e)
            CommandReply(r.json, "rules (cloud failed)")
        }
    }
}