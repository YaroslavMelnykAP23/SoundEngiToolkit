package com.example.soundengitoolkit.ai.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SimpleCommandParserTest {
    private fun p(s: String) = SimpleCommandParser.parse(s)

    // ---- simple -> rules ----

    @Test fun e1_fullCommand() = assertEquals(
        listOf(AiAction.Bpm(120f), AiAction.DelayFx(true, 8, null, null), AiAction.CompressorFx(true, null, null)),
        p("add delay 1/8 at 120 bpm and turn on compressor")
    )

    @Test fun reverbOff() = assertEquals(listOf(AiAction.ReverbFx(false, null, null)), p("reverb off"))
    @Test fun delayOff_doesNotTouchDivision() = assertEquals(listOf(AiAction.DelayFx(false, null, null, null)), p("delay off"))
    @Test fun ukrainianOff() = assertEquals(listOf(AiAction.CompressorFx(false, null, null)), p("вимкни компресор"))
    @Test fun ukrainianReverbOff() = assertEquals(listOf(AiAction.ReverbFx(false, null, null)), p("вимкни ревербацію"))
    @Test fun bareEffect_isOn() = assertEquals(listOf(AiAction.ReverbFx(true, null, null)), p("reverb"))
    @Test fun ukrainianDelayDivision() = assertEquals(listOf(AiAction.DelayFx(true, 16, null, null)), p("ділей 1/16"))
    @Test fun bpmOnly() = assertEquals(listOf(AiAction.Bpm(90f)), p("90 bpm"))
    @Test fun bpmGlued() = assertEquals(listOf(AiAction.Bpm(128f)), p("128bpm"))
    @Test fun twoParts_onAndOff() = assertEquals(
        listOf(AiAction.ReverbFx(false, null, null), AiAction.DelayFx(true, null, null, null)),
        p("reverb off, delay on")
    )
    @Test fun eqOn() = assertEquals(listOf(AiAction.EqFx(true, null, null, null)), p("turn on eq"))

    // ---- not simple -> null (cloud) ----

    @Test fun e2_warmer() = assertNull(p("make sound warmer"))
    @Test fun e3_relative() = assertNull(p("зроби delay довшим"))
    @Test fun e4_half() = assertNull(p("зменш reverb наполовину"))
    @Test fun unknownWord() = assertNull(p("hello"))
    @Test fun blank() = assertNull(p("   "))
    @Test fun numberWithoutBpm() = assertNull(p("reverb 50"))
    // Lab 7, BUG-04: out-of-range tempo is still a simple command; ActionClamp limits it (see ActionClampTest)
    @Test fun bpmOutOfRange_isTakenByRules() = assertEquals(listOf(AiAction.Bpm(300f)), p("300 bpm"))
    @Test fun englishTempoPhrase_isTakenByRules() = assertEquals(listOf(AiAction.Bpm(300f)), p("set the tempo to 300 bpm"))
    @Test fun unsupportedDivision() = assertNull(p("delay 1/32"))
    @Test fun twoEffectsInOnePart() = assertNull(p("reverb delay off"))
    @Test fun onAndOffInOnePart() = assertNull(p("turn reverb on off"))
    @Test fun offWithoutEffect() = assertNull(p("turn off"))
    @Test fun divisionWithoutDelay() = assertNull(p("reverb 1/8"))

    // ---- JSON for the action mapper ----

    @Test fun toJson_roundTripsThroughMapper() {
        val a = p("add delay 1/8 at 120 bpm and turn on compressor")!!
        assertEquals(a, AiActionMapper.parse(SimpleCommandParser.toJson(a)))
    }
}
