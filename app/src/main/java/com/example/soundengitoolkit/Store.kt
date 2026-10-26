package com.example.soundengitoolkit

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.util.UUID

/** Cursor, loop markers and loop switch of the oscilloscope (in samples). */
class ViewState(var cursor: Int = 0, var loopA: Int = 0, var loopB: Int = 0, var loopOn: Boolean = false)

/** Effect settings <-> JSON. Same keys as the AI actions (see docs/api-contract.md). */
object FxState {
    fun toJson(e: Engine): String = JSONObject()
        .put("eq", JSONObject().put("on", e.eq.on)
            .put("low", e.eq.lowDb.toDouble()).put("mid", e.eq.midDb.toDouble()).put("high", e.eq.highDb.toDouble()))
        .put("comp", JSONObject().put("on", e.comp.on)
            .put("threshold", e.comp.thr.toDouble()).put("ratio", e.comp.ratio.toDouble()))
        .put("delay", JSONObject().put("on", e.delay.on)
            .put("division", e.delay.denom).put("feedback", e.delay.fb.toDouble()).put("mix", e.delay.mix.toDouble()))
        .put("reverb", JSONObject().put("on", e.reverb.on)
            .put("mix", e.reverb.mix.toDouble()).put("size", e.reverb.size.toDouble()))
        .toString()

    /** Call after e.bpm is set: the delay time depends on it. */
    fun apply(e: Engine, json: String) {
        val j = JSONObject(json)
        fun JSONObject.f(k: String, d: Float) = optDouble(k, d.toDouble()).toFloat()
        j.optJSONObject("eq")?.let { e.eq.set(it.f("low", 0f), it.f("mid", 0f), it.f("high", 0f)); e.eq.on = it.optBoolean("on") }
        j.optJSONObject("comp")?.let { e.comp.set(it.f("threshold", -18f), it.f("ratio", 4f)); e.comp.on = it.optBoolean("on") }
        j.optJSONObject("delay")?.let {
            e.delay.denom = it.optInt("division", 8); e.delay.fb = it.f("feedback", 0.4f); e.delay.mix = it.f("mix", 0.35f)
            e.delay.sync(e.bpm); e.delay.on = it.optBoolean("on")
        }
        j.optJSONObject("reverb")?.let {
            e.reverb.mix = it.f("mix", 0.25f); e.reverb.size = it.f("size", 0.84f); e.reverb.on = it.optBoolean("on")
        }
    }
}

/** Local library: WAV files in filesDir/projects + Room metadata + AI command history. */
class ProjectStore(context: Context) {
    private val db = AppDb.get(context)
    private val dir = File(context.filesDir, "projects").apply { mkdirs() }

    val projects = db.projects().all()
    val history = db.ai().recent()

    /** Saves the dry recording as WAV plus effect settings, BPM, cursor and loop markers. */
    suspend fun save(e: Engine, name: String, vs: ViewState): ProjectEntity = withContext(Dispatchers.IO) {
        val data = e.sampler.data
        require(data.isNotEmpty()) { "Nothing to save: record something first" }
        val fileName = "${UUID.randomUUID()}.wav"        // file name never comes from user input
        val tmp = File(dir, "$fileName.tmp"); val dst = File(dir, fileName)
        try {
            Wav.write(tmp, data)
            check(tmp.renameTo(dst)) { "Cannot write file" }
        } catch (t: Throwable) { tmp.delete(); throw t }
        val p = ProjectEntity(
            name = name.trim().ifEmpty { "Untitled" }, createdAt = System.currentTimeMillis(), bpm = e.bpm,
            cursorPos = vs.cursor, loopA = vs.loopA, loopB = vs.loopB, loopOn = vs.loopOn,
            durationSamples = data.size, wavFile = fileName, effectsJson = FxState.toJson(e)
        )
        try { p.copy(id = db.projects().insert(p)) } catch (t: Throwable) { dst.delete(); throw t }
    }

    /** Replaces the current recording and effect settings. Cursor/markers are restored by the UI. */
    suspend fun load(p: ProjectEntity, e: Engine) {
        val data = withContext(Dispatchers.IO) { Wav.read(File(dir, p.wavFile)) }
        e.sampler.stop()
        e.bpm = p.bpm
        FxState.apply(e, p.effectsJson)
        e.sampler.load(data)
    }

    suspend fun delete(p: ProjectEntity) = withContext(Dispatchers.IO) {
        db.projects().delete(p); File(dir, p.wavFile).delete()
    }

    suspend fun logAi(prompt: String, json: String, log: String, source: String, ok: Boolean) =
        withContext(Dispatchers.IO) {
            db.ai().insert(AiEntryEntity(prompt = prompt, json = json, log = log, source = source, ok = ok,
                createdAt = System.currentTimeMillis()))
            db.ai().trim()
        }

    suspend fun clearHistory() = withContext(Dispatchers.IO) { db.ai().clear() }
}
