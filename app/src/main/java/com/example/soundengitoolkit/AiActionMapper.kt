package com.example.soundengitoolkit

import org.json.JSONObject

/** JSON {"actions":[...]} -> typed actions. Pure parsing: never touches the Engine. */
object AiActionMapper {
    /** Throws JSONException on malformed JSON, missing "actions" or missing "fx". */
    fun parse(json: String): List<AiAction> {
        val arr = JSONObject(json).getJSONArray("actions")
        return List(arr.length()) { i -> map(arr.getJSONObject(i)) }
    }

    private fun map(o: JSONObject): AiAction {
        fun f(k: String): Float? = if (o.has(k)) o.getDouble(k).toFloat() else null
        fun b(k: String): Boolean? = if (o.has(k)) o.optBoolean(k) else null
        fun i(k: String): Int? = if (o.has(k)) o.optInt(k) else null
        return when (val fx = o.getString("fx")) {
            "bpm" -> AiAction.Bpm(f("value"))
            "delay" -> AiAction.DelayFx(b("on"), i("division"), f("feedback"), f("mix"))
            "reverb" -> AiAction.ReverbFx(b("on"), f("mix"), f("size"))
            "compressor" -> AiAction.CompressorFx(b("on"), f("threshold"), f("ratio"))
            "eq" -> AiAction.EqFx(b("on"), f("low"), f("mid"), f("high"))
            else -> AiAction.Unknown(fx)
        }
    }
}