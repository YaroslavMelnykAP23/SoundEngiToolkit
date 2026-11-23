package com.example.soundengitoolkit.ai.data

import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.GenerativeBackend
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.ThinkingLevel
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import com.google.firebase.ai.type.thinkingConfig
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
    // Checked against firebase.google.com/docs/ai-logic/models on 2026-11-23.
    // Tried on 2026-11-23: gemini-3.8-flash -> "high demand"; gemini-3.5-flash (routed to 3.6-flash) -> timeouts.
    // gemini-3.5-flash-lite (Stable, featured, no billing) answered in seconds and is enough for this short task.
    modelName: String = MODEL,
    private val temperature: Float = TEMPERATURE
) : CommandSource {
    // lazy: Firebase is touched only on the first cloud call, so a missing
    // google-services.json can no longer crash the app at startup.
    private val model by lazy {
        Firebase.ai(backend = GenerativeBackend.googleAI()).generativeModel(
            modelName = modelName,
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = REPLY_SCHEMA          // the model must return JSON of this shape
                // Short task: little reasoning needed, lower latency.
                thinkingConfig = thinkingConfig { thinkingLevel = THINKING }
                temperature = this@FirebaseSource.temperature
            },
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

    companion object {
        const val MODEL = "gemini-3.5-flash-lite"
        /** LOW, not MINIMAL: MINIMAL is not supported by every model and fails with 400. */
        val THINKING = ThinkingLevel.LOW
        /** Low temperature: the same command should give the same actions. */
        const val TEMPERATURE = 0.2f

        /** {"actions":[{fx, why, ...optional fields}], "reply": string}. Ranges are checked again on the device. */
        val REPLY_SCHEMA: Schema = Schema.obj(
            mapOf(
                "actions" to Schema.array(
                    Schema.obj(
                        mapOf(
                            "fx" to Schema.enumeration(listOf("bpm", "delay", "reverb", "compressor", "eq")),
                            "why" to Schema.string(),
                            "value" to Schema.double(),
                            "on" to Schema.boolean(),
                            "division" to Schema.integer(),
                            "feedback" to Schema.double(),
                            "mix" to Schema.double(),
                            "size" to Schema.double(),
                            "threshold" to Schema.double(),
                            "ratio" to Schema.double(),
                            "low" to Schema.double(),
                            "mid" to Schema.double(),
                            "high" to Schema.double()
                        ),
                        optionalProperties = listOf(
                            "value", "on", "division", "feedback", "mix", "size",
                            "threshold", "ratio", "low", "mid", "high"
                        )
                    )
                ),
                "reply" to Schema.string()
            )
        )
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
            System.err.println("HybridSource: cloud failed: $e")   // TEMP lab6 diagnostics (shows in Logcat as System.err)
            e.cause?.let { System.err.println("HybridSource: cause: $it") }
            CommandReply(r.json, "rules (cloud failed)")
        }
    }
}