package com.example.soundengitoolkit

import android.Manifest
import kotlin.math.*
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.onSizeChanged
import kotlinx.coroutines.delay
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    private val engine = Engine()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        engine.start()
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                Surface(color = Color(0xFF111111), contentColor = Color.White) { Screen(engine) }
            }
        }
    }

    override fun onDestroy() { engine.stop(); super.onDestroy() }
}

@Composable
fun Screen(e: Engine) {
    val scope = rememberCoroutineScope()
    var rec by remember { mutableStateOf(false) }
    var bpm by remember { mutableStateOf(120f) }
    var prompt by remember { mutableStateOf("add delay 1/8 at 120 bpm and turn on compressor") }
    var log by remember { mutableStateOf("") }
    val fxState = remember { mutableStateMapOf("EQ" to false, "Compressor" to false, "Delay" to false, "Reverb" to false) }
    var status by remember { mutableStateOf("") }
    var vol by remember { mutableFloatStateOf(0f) }
    val ctx = LocalContext.current

    fun begin() {
        val ok = e.startRecording { n -> status = "Recorded %.1f s".format(n / SR) }
        rec = ok
        status = if (ok) "Recording..." else "Cannot open microphone"
    }

    val perm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) begin()
        else status = "Microphone permission denied. Enable it in Settings > Apps > SoundEngiToolkit > Permissions"
    }

    fun sync() {
        fxState["EQ"] = e.eq.on; fxState["Compressor"] = e.comp.on
        fxState["Delay"] = e.delay.on; fxState["Reverb"] = e.reverb.on; bpm = e.bpm
    }

    Column(Modifier.fillMaxSize().background(Color(0xFF111111)).safeDrawingPadding()
        .verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("SoundEngiToolkit", style = MaterialTheme.typography.titleLarge)

        // Master volume: really changes what you hear (unlike the oscilloscope view gain)
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(if (vol <= -60f) "Volume: mute" else "Volume %+.0f dB".format(vol),
                Modifier.width(130.dp), style = MaterialTheme.typography.bodySmall)
            Slider(vol, { vol = it; e.masterDb = it }, Modifier.weight(1f), valueRange = -60f..12f)
        }

        // Sampler
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {
                if (rec) { e.stopRecording(); rec = false; status = "Saving..." }
                else if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO)
                    == PackageManager.PERMISSION_GRANTED) begin()
                else perm.launch(Manifest.permission.RECORD_AUDIO)
            }) { Text(if (rec) "■ Stop" else "● Record") }
        }
        ScopePanel(e, bpm)
        if (status.isNotEmpty()) Text(status, style = MaterialTheme.typography.bodySmall)

        // Synth keys C4..C5 + sample pitched on long-press-free row
        Row(Modifier.fillMaxWidth().height(90.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            listOf(60, 62, 64, 65, 67, 69, 71, 72).forEach { note ->
                Box(Modifier.weight(1f).fillMaxHeight().background(Color(0xFFEEEEEE))
                    .pointerInput(note) {
                        detectTapGestures(onPress = { e.synth.on(note); tryAwaitRelease(); e.synth.off(note) })
                    })
            }
        }

        // Effects
        Text("BPM ${bpm.toInt()}")
        Slider(bpm, { bpm = it; e.bpm = it }, valueRange = 60f..200f)
        fxState.keys.toList().forEach { name ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(name)
                Switch(fxState[name] == true, { v ->
                    fxState[name] = v
                    when (name) { "EQ" -> e.eq.on = v; "Compressor" -> e.comp.on = v
                        "Delay" -> e.delay.on = v; else -> e.reverb.on = v }
                })
            }
        }

        // AI
        OutlinedTextField(prompt, { prompt = it }, Modifier.fillMaxWidth(), label = { Text("AI command") })
        Button(onClick = {
            scope.launch {
                log = try {
                    withContext(Dispatchers.IO) { Ai.apply(e, Ai.ask(prompt)) }
                } catch (ex: Throwable) { "Error: ${ex.javaClass.simpleName}: ${ex.message}" }
                sync()
            }
        }) { Text("Run") }
        Text(log, style = MaterialTheme.typography.bodySmall)
    }
}


private class Cols(val mn: FloatArray, val mx: FloatArray, val peakDb: Float, val rmsDb: Float)

/** Per-pixel min/max envelope of d[start, start+len) plus peak/RMS of that range. */
private fun columns(d: FloatArray, start: Float, len: Float, w: Int): Cols {
    val mn = FloatArray(w); val mx = FloatArray(w)
    val perPx = len / w
    var pk = 0f; var sum = 0.0; var cnt = 0
    for (x in 0 until w) {
        val a = (start + x * perPx).toInt().coerceIn(0, d.size - 1)
        val b = (start + (x + 1) * perPx).toInt().coerceIn(a + 1, d.size)
        var lo = d[a]; var hi = d[a]
        for (i in a until b) { val v = d[i]; if (v < lo) lo = v; if (v > hi) hi = v; sum += v * v }
        cnt += b - a; mn[x] = lo; mx[x] = hi
        pk = max(pk, max(abs(lo), abs(hi)))
    }
    val rms = sqrt(sum / max(1, cnt)).toFloat()
    return Cols(mn, mx, 20f * log10(max(pk, 1e-6f)), 20f * log10(max(rms, 1e-6f)))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScopePanel(e: Engine, bpm: Float) {
    val bpmU = rememberUpdatedState(bpm)
    var live by remember { mutableStateOf(false) }            // false = whole recording, true = live output
    var viewGainDb by remember { mutableFloatStateOf(0f) }    // display only, does not change the sound
    var data by remember { mutableStateOf(e.sampler.data) }
    var vStart by remember { mutableFloatStateOf(0f) }        // first visible sample
    var vLen by remember { mutableFloatStateOf(max(1f, e.sampler.data.size.toFloat())) } // whole signal
    var liveMs by remember { mutableFloatStateOf(25f) }
    var wPx by remember { mutableIntStateOf(0) }
    var tick by remember { mutableIntStateOf(0) }
    var cursor by remember { mutableIntStateOf(0) }
    var loopA by remember { mutableIntStateOf(0) }
    var loopB by remember { mutableIntStateOf(e.sampler.data.size) }
    var loopOn by remember { mutableStateOf(false) }
    var snap by remember { mutableStateOf(false) }
    var playPos by remember { mutableIntStateOf(-1) }
    val pts = remember { FloatArray(1024) }
    val cnt = remember { IntArray(1) }
    val lv = remember { FloatArray(2) }
    val minLen = 32f

    fun total() = max(1f, data.size.toFloat())
    fun spb() = SR * 60f / bpmU.value                       // samples per beat
    fun fit() { vStart = 0f; vLen = total() }
    fun zoomBy(f: Float, focus: Float) {
        val anchor = vStart + vLen * focus
        vLen = (vLen / f).coerceIn(min(minLen, total()), total())
        vStart = (anchor - vLen * focus).coerceIn(0f, total() - vLen)
    }
    fun snapS(x: Float): Int {
        val v = if (snap) (x / spb()).roundToInt() * spb() else x
        return v.roundToInt().coerceIn(0, data.size)
    }

    LaunchedEffect(Unit) {                                   // new recording -> show all, reset markers
        while (true) {
            delay(100)
            val d = e.sampler.data
            if (d !== data) { data = d; fit(); cursor = 0; loopA = 0; loopB = d.size }
        }
    }
    LaunchedEffect(loopA, loopB, loopOn) {
        e.sampler.loopStart = loopA; e.sampler.loopEnd = loopB; e.sampler.loopOn = loopOn
    }
    LaunchedEffect(live, liveMs) {
        while (true) {
            withFrameNanos { }
            if (live) { cnt[0] = e.scope(pts, (liveMs * SR / 1000f).toInt()); e.levels(lv); tick++ }
            else playPos = if (e.sampler.playing) e.sampler.pos else -1
        }
    }
    val cols = remember(data, vStart, vLen, wPx, live) {
        if (live || wPx <= 0 || data.isEmpty()) null else columns(data, vStart, vLen, wPx)
    }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FilterChip(selected = !live, onClick = { live = false }, label = { Text("Recording") })
            FilterChip(selected = live, onClick = { live = true }, label = { Text("Live") })
            if (!live) OutlinedButton(onClick = { fit() }) { Text("Fit all") }
        }

        if (!live) {
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { e.sampler.play(cursor) }, enabled = data.isNotEmpty()) { Text("▶ Play from cursor") }
                Button(onClick = { e.sampler.stop() }) { Text("■") }
            }
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Switch(loopOn, { loopOn = it }); Text("Loop A-B")
                Spacer(Modifier.width(12.dp))
                Switch(snap, { snap = it }); Text("Snap to beats")
            }
        }

        if (live) {
            val t = (ln(liveMs / 2f) / ln(45f)).coerceIn(0f, 1f)
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Window %.0f ms".format(liveMs), Modifier.width(120.dp), style = MaterialTheme.typography.bodySmall)
                Slider(t, { liveMs = 2f * 45f.pow(it) }, Modifier.weight(1f))
            }
        } else {
            val minL = min(minLen, total())
            val canZoom = total() > minL * 1.01f
            val t = if (canZoom) (ln(total() / vLen) / ln(total() / minL)).coerceIn(0f, 1f) else 0f
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Zoom x%.1f".format(total() / vLen), Modifier.width(120.dp), style = MaterialTheme.typography.bodySmall)
                Slider(t, { nt -> zoomBy(vLen / (total() * (minL / total()).pow(nt)), 0.5f) },
                    Modifier.weight(1f), enabled = canZoom)
            }
        }
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("View gain +${viewGainDb.toInt()} dB", Modifier.width(120.dp), style = MaterialTheme.typography.bodySmall)
            Slider(viewGainDb, { viewGainDb = it }, Modifier.weight(1f), valueRange = 0f..40f)
        }

        Canvas(
            Modifier.fillMaxWidth().height(200.dp).background(Color(0xFF0A1A0A))
                .onSizeChanged { wPx = it.width }
                .pointerInput(live, data) {
                    if (live || data.isEmpty()) return@pointerInput
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val w = size.width.toFloat()
                        fun xOf(smp: Int) = (smp - vStart) / vLen * w
                        fun sOf(x: Float) = vStart + x / w * vLen
                        val slop = 28.dp.toPx()
                        val dA = abs(down.position.x - xOf(loopA)); val dB = abs(down.position.x - xOf(loopB))
                        val marker = if (min(dA, dB) > slop) -1 else if (dA <= dB) 0 else 1
                        if (marker >= 0) {                                   // drag a loop marker
                            do {
                                val ev = awaitPointerEvent()
                                val ch = ev.changes.firstOrNull() ?: break
                                if (ch.pressed) {
                                    val smp = snapS(sOf(ch.position.x))
                                    if (marker == 0) loopA = smp.coerceIn(0, max(0, loopB - 64))
                                    else loopB = smp.coerceIn(min(data.size, loopA + 64), data.size)
                                    ch.consume()
                                }
                            } while (ev.changes.any { it.pressed })
                        } else {                                             // pinch / pan, or tap = cursor
                            var moved = false; var acc = 0f
                            do {
                                val ev = awaitPointerEvent()
                                val zoom = ev.calculateZoom(); val pan = ev.calculatePan(); val c = ev.calculateCentroid()
                                acc += abs(pan.x) + abs(zoom - 1f) * w
                                if (ev.changes.size > 1 || acc > viewConfiguration.touchSlop) {
                                    moved = true
                                    if (zoom != 1f) zoomBy(zoom, (c.x / w).coerceIn(0f, 1f))
                                    vStart = (vStart - pan.x * vLen / w).coerceIn(0f, max(0f, total() - vLen))
                                    ev.changes.forEach { if (it.positionChanged()) it.consume() }
                                }
                            } while (ev.changes.any { it.pressed })
                            if (!moved) {
                                cursor = snapS(sOf(down.position.x)).coerceIn(0, max(0, data.size - 1))
                                if (e.sampler.playing) e.sampler.play(cursor)
                            }
                        }
                    }
                }
        ) {
            if (tick < 0) return@Canvas
            val mid = size.height / 2
            val g = 10f.pow(viewGainDb / 20f)
            fun amp(db: Float): Float = 10f.pow((db + viewGainDb) / 20f).coerceIn(0f, 1f)
            fun yOf(a: Float) = mid - a * mid * 0.95f
            fun mapV(v: Float): Float = (v * g).coerceIn(-1f, 1f)

            val grid = Color(0x3300FF66); val wave = Color(0xFF00FF66)
            drawLine(grid, Offset(0f, mid), Offset(size.width, mid))
            for (db in listOf(0f, -6f, -12f, -24f, -48f)) {
                val a = amp(db)
                if (a > 0f && a < 1f || db == 0f) {
                    drawLine(grid, Offset(0f, yOf(a)), Offset(size.width, yOf(a)))
                    drawLine(grid, Offset(0f, yOf(-a)), Offset(size.width, yOf(-a)))
                }
            }

            if (live) {
                for (i in 1..3) drawLine(grid, Offset(size.width * i / 4, 0f), Offset(size.width * i / 4, size.height))
                val n = cnt[0]
                if (n < 2) return@Canvas
                val path = Path()
                for (i in 0 until n) {
                    val x = size.width * i / (n - 1); val y = yOf(mapV(pts[i]))
                    if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                drawPath(path, wave, style = Stroke(width = 2f))
                return@Canvas
            }

            // beat / bar grid (4/4), depends on BPM; thinned out when zoomed far out
            if (data.isNotEmpty()) {
                val spbF = SR * 60f / bpm
                val pxBeat = spbF / vLen * size.width
                val step = when { pxBeat >= 10f -> 1; pxBeat * 4 >= 10f -> 4; pxBeat * 16 >= 10f -> 16; else -> 0 }
                if (step > 0) {
                    var bt = ceil(vStart / spbF / step).toInt() * step
                    while (true) {
                        val x = (bt * spbF - vStart) / vLen * size.width
                        if (x > size.width) break
                        val bar = bt % 4 == 0
                        drawLine(if (bar) Color(0x66FFFFFF) else Color(0x25FFFFFF), Offset(x, 0f), Offset(x, size.height),
                            strokeWidth = if (bar) 2f else 1f)
                        bt += step
                    }
                }
            }

            val c = cols ?: return@Canvas
            if (vLen / wPx >= 2f) {                       // many samples per pixel: min/max envelope
                for (x in 0 until wPx) {
                    val xf = x + 0.5f
                    drawLine(wave, Offset(xf, yOf(mapV(c.mx[x]))), Offset(xf, yOf(mapV(c.mn[x]))), strokeWidth = 1.5f)
                }
            } else {                                      // zoomed in: draw the actual samples
                val i0 = vStart.toInt(); val i1 = min(data.size - 1, (vStart + vLen).toInt() + 1)
                val path = Path()
                for (i in i0..i1) {
                    val x = (i - vStart) / vLen * size.width; val y = yOf(mapV(data[i]))
                    if (i == i0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                drawPath(path, wave, style = Stroke(width = 2f))
            }

            fun xs(smp: Int) = (smp - vStart) / vLen * size.width
            val xA = xs(loopA); val xB = xs(loopB)
            val l = xA.coerceIn(0f, size.width); val r = xB.coerceIn(0f, size.width)
            if (r > l) drawRect(Color(if (loopOn) 0x3340A0FF else 0x1A40A0FF), Offset(l, 0f), Size(r - l, size.height))
            val amber = Color(0xFFFFB300); val hw = 14.dp.toPx(); val hh = 18.dp.toPx()
            if (xA in 0f..size.width) {
                drawLine(amber, Offset(xA, 0f), Offset(xA, size.height), strokeWidth = 3f)
                drawRect(amber, Offset(xA, 0f), Size(hw, hh))
            }
            if (xB in 0f..size.width) {
                drawLine(amber, Offset(xB, 0f), Offset(xB, size.height), strokeWidth = 3f)
                drawRect(amber, Offset(xB - hw, 0f), Size(hw, hh))
            }
            val xC = xs(cursor)
            if (xC in 0f..size.width) drawLine(Color(0xFF40C4FF), Offset(xC, 0f), Offset(xC, size.height), strokeWidth = 3f)
            if (playPos >= 0) {
                val xP = xs(playPos)
                if (xP in 0f..size.width) drawLine(Color.White, Offset(xP, 0f), Offset(xP, size.height), strokeWidth = 2f)
            }
        }

        val info = if (live) "Peak %.1f dBFS    RMS %.1f dBFS".format(lv[0], lv[1])
        else if (data.isEmpty()) "No recording yet: press Record, or switch to Live"
        else {
            val beats = cursor / (SR * 60f / bpm)
            "View %.2f-%.2f s of %.2f s    Peak %.1f dBFS    RMS %.1f dBFS\nCursor %.2f s (bar %d, beat %d)    Loop %.2f-%.2f s".format(
                vStart / SR, (vStart + vLen) / SR, data.size / SR, cols?.peakDb ?: -120f, cols?.rmsDb ?: -120f,
                cursor / SR, beats.toInt() / 4 + 1, beats.toInt() % 4 + 1, loopA / SR, loopB / SR)
        }
        Text(if (tick < 0) "" else info, style = MaterialTheme.typography.bodySmall)
        Text(
            if (live) "Grid: 0, -6, -12, -24, -48 dBFS above and below centre."
            else "Tap: cursor (blue). Drag orange markers: loop A-B. Pinch/drag: zoom and move. " +
                    "White lines: bars, dim lines: beats (4/4, %.0f BPM).".format(bpm),
            style = MaterialTheme.typography.labelSmall, color = Color(0xFFAAAAAA)
        )
    }
}