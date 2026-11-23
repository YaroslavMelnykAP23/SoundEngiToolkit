package com.example.soundengitoolkit.ai.data

import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.GenerativeBackend
import com.google.firebase.ai.type.RequestTimeoutException
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.ServerException
import com.google.firebase.ai.type.ThinkingLevel
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import com.google.firebase.ai.type.thinkingConfig
import kotlinx.coroutines.CancellationException
import org.json.JSONObject
import com.example.soundengitoolkit.ai.domain.AiError
import com.example.soundengitoolkit.ai.domain.AiException
import com.example.soundengitoolkit.ai.domain.AiRequest
import com.example.soundengitoolkit.ai.domain.CommandReply
import com.example.soundengitoolkit.ai.domain.CommandSource
import com.example.soundengitoolkit.ai.domain.SimpleCommandParser

/** Offline, free, instant. Answers only simple, unambiguous commands; otherwise returns no actions. */
class RulesSource : CommandSource {
    override suspend fun ask(request: AiRequest): CommandReply =
        CommandReply(SimpleCommandParser.toJson(SimpleCommandParser.parse(request.prompt).orEmpty()), "rules")
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

    override suspend fun ask(request: AiRequest): CommandReply {
        val text = try {
            model.generateContent(request.toUserMessage()).text   // state + history + prompt as JSON
        } catch (e: CancellationException) {
            throw e
        } catch (e: java.io.IOException) {
            throw e                                        // mapped to Network by AiErrorMapper
        } catch (e: RequestTimeoutException) {
            throw AiException(AiError.Timeout, e)          // SDK's own timeout
        } catch (e: ServerException) {
            throw AiException(if (isBusy(e)) AiError.Busy else AiError.AiUnavailable, e)
        } catch (e: Exception) {
            // Offline, the SDK throws its own exception with an IOException (UnknownHost...) inside: that is Network.
            if (e.hasIoCause()) throw AiException(AiError.Network, e)
            throw AiException(AiError.AiUnavailable, e)    // Firebase not configured, App Check, quota, model refused
        } ?: throw AiException(AiError.AiUnavailable)      // empty reply
        return CommandReply(text.replace("```json", "").replace("```", "").trim(), "cloud")
    }

    private fun Throwable.hasIoCause(): Boolean =
        generateSequence(cause) { it.cause }.take(10).any { it is java.io.IOException }

    /** "This model is currently experiencing high demand" (seen 2026-11-23) and other temporary overloads. */
    private fun isBusy(e: ServerException): Boolean {
        val m = e.message.orEmpty().lowercase()
        return "high demand" in m || "overloaded" in m || "503" in m || "unavailable" in m
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

/**
 * Simple commands -> rules (offline, free); everything else -> cloud with state and history.
 * Cloud errors are NOT hidden behind an empty rules reply any more (was L-25 / B-6): the caller sees them.
 */
class HybridSource(private val rules: CommandSource, private val cloud: CommandSource) : CommandSource {
    override suspend fun ask(request: AiRequest): CommandReply {
        val r = rules.ask(request)
        if (JSONObject(r.json).getJSONArray("actions").length() > 0) return r
        return cloud.ask(request).copy(source = "cloud")
    }
}
