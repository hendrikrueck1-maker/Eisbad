package de.eisbad.app

import kotlinx.coroutines.delay
import java.util.SortedMap
import java.util.TreeMap
import kotlin.math.abs

/**
 * Ablauf: Vorbereitung -> N x (Atmung, Atem halten, Erholungsatem) -> Einstieg -> Kälte.
 * Die Ansagen passen sich an die gewählten Zeiten an (Halbzeit, Restzeit, Minuten, Motivation).
 */
class SessionRunner(
    private val s: AppSettings,
    private val say: (String) -> Unit,
    private val beep: (Boolean) -> Unit,
) {
    private fun ui(
        phase: Phase,
        title: String,
        big: String,
        sub: String,
        progress: Float = 0f,
    ) {
        SessionBus.state.value = UiState(true, phase, title, big, sub, progress)
    }

    private fun fmt(sec: Int) = "%d:%02d".format(sec / 60, sec % 60)

    suspend fun run() {
        prep()
        for (r in 1..s.rounds) {
            say(if (r == s.rounds && s.rounds > 1) "round_last" else "round_$r")
            delay(3000)
            breathing(r)
            hold(r)
            recovery(r)
        }
        entry()
        cold()
    }

    private suspend fun prep() {
        say("intro")
        for (t in s.prepSec downTo 1) {
            ui(Phase.PREP, "Vorbereitung", "$t", "Setz dich bequem hin", 1f - t / s.prepSec.toFloat())
            delay(1000)
        }
    }

    private suspend fun breathing(r: Int) {
        val total = s.breaths
        val cycleMs = (s.breathSec * 1000).toLong()
        val inMs = (cycleMs * 0.55).toLong()
        SessionBus.skip = false
        for (b in 1..total) {
            if (SessionBus.skip) break
            ui(
                Phase.BREATH, "Runde $r von ${s.rounds}", "$b", "von $total Atemzügen",
                b / total.toFloat(),
            )
            if (b == 1 && r == 1) say("breath_start")
            else if (total >= 9 && b == total / 3) say("breath_third")
            else if (total >= 9 && b == total * 2 / 3) say("breath_twothirds")
            else if (total >= 10 && b == total - 2) say("breath_last")

            if (s.breathCues) beep(true)
            delay(inMs)
            if (s.breathCues) beep(false)
            delay(cycleMs - inMs)
        }
        SessionBus.skip = false
        say("breath_exhale_hold")
    }

    private suspend fun hold(r: Int) {
        var t = 0
        SessionBus.skip = false
        while (t < s.holdSec && !SessionBus.skip) {
            ui(
                Phase.HOLD, "Runde $r von ${s.rounds}", fmt(t),
                "Atem anhalten – mit \"Weiter\" beenden", t / s.holdSec.toFloat(),
            )
            delay(1000)
            t++
            if (t % 30 == 0 && t < s.holdSec - 5 && t <= 300) say("hold_$t")
            else if (t == s.holdSec - 10 && s.holdSec > 40) say("hold_10s_left")
        }
        SessionBus.skip = false
        say("hold_end")
    }

    private suspend fun recovery(r: Int) {
        for (t in s.recoverySec downTo 1) {
            ui(
                Phase.RECOVERY, "Runde $r von ${s.rounds}", "$t", "Tief einatmen und halten",
                1f - t / s.recoverySec.toFloat(),
            )
            delay(1000)
        }
        say("recovery_end")
        delay(3000)
    }

    private suspend fun entry() {
        say("entry_prepare")
        for (t in s.entrySec downTo 1) {
            ui(
                Phase.ENTRY, "Einstieg", "$t", "Steig ins Wasser – der Timer startet automatisch",
                1f - t / s.entrySec.toFloat(),
            )
            if (t == 10 && s.entrySec > 15) say("entry_10")
            delay(1000)
        }
    }

    private suspend fun cold() {
        val total = s.coldSec
        val cues = buildColdCues(total)
        for (t in 0 until total) {
            ui(
                Phase.COLD, "Kälte", fmt(total - t), "${fmt(t)} geschafft",
                t / total.toFloat(),
            )
            if (t == 0) say("cold_start") else cues[t]?.let { say(it) }
            delay(1000)
        }
        say("cold_end")
    }

    private fun buildColdCues(total: Int): SortedMap<Int, String> {
        val cues = TreeMap<Int, String>()
        fun put(t: Int, key: String) {
            if (t in 1 until total && !cues.containsKey(t)) cues[t] = key
        }
        // Restzeit-Ansagen (nur wenn genug Platz nach dem Start)
        for (rem in listOf(10, 30, 60)) if (total > rem + 20) put(total - rem, "rem_$rem")
        // Halbzeit
        if (total >= 120) put(total / 2, "cold_halftime")
        // volle Minuten
        var m = 1
        while (m * 60 < total - 15 && m <= 10) {
            put(m * 60, "min_$m"); m++
        }
        // Motivation, alle ~40 s, nur wenn kein anderer Hinweis in der Nähe
        var k = 0
        var t = 25
        while (t < total - 25) {
            if (cues.keys.none { abs(it - t) < 10 }) {
                put(t, "motiv_${k % 10 + 1}"); k++
            }
            t += 40
        }
        return cues
    }
}
