package de.eisbad.app

import android.content.Context

data class AppSettings(
    val rounds: Int = 3,
    val breaths: Int = 30,
    val breathSec: Float = 3.5f,
    val holdSec: Int = 90,
    val recoverySec: Int = 15,
    val prepSec: Int = 10,
    val entrySec: Int = 45,
    val coldSec: Int = 180,
    val lang: String = "de",
    val musicUri: String = "",
    val musicVol: Float = 0.5f,
    val voiceVol: Float = 1.0f,
    val breathCues: Boolean = true,
    val acknowledged: Boolean = false,
    val mode: String = "both",
    val voice: String = "florian",
    val theme: String = "schiefer",
) {
    fun save(ctx: Context) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putInt("rounds", rounds)
            .putInt("breaths", breaths)
            .putFloat("breathSec", breathSec)
            .putInt("holdSec", holdSec)
            .putInt("recoverySec", recoverySec)
            .putInt("prepSec", prepSec)
            .putInt("entrySec", entrySec)
            .putInt("coldSec", coldSec)
            .putString("lang", lang)
            .putString("musicUri", musicUri)
            .putFloat("musicVol", musicVol)
            .putFloat("voiceVol", voiceVol)
            .putBoolean("breathCues", breathCues)
            .putBoolean("acknowledged", acknowledged)
            .putString("mode", mode)
            .putString("voice", voice)
            .putString("theme", theme)
            .apply()
    }

    companion object {
        private const val PREFS = "eisbad"

        fun load(ctx: Context): AppSettings {
            val p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val d = AppSettings()
            return AppSettings(
                rounds = p.getInt("rounds", d.rounds),
                breaths = p.getInt("breaths", d.breaths),
                breathSec = p.getFloat("breathSec", d.breathSec),
                holdSec = p.getInt("holdSec", d.holdSec),
                recoverySec = p.getInt("recoverySec", d.recoverySec),
                prepSec = p.getInt("prepSec", d.prepSec),
                entrySec = p.getInt("entrySec", d.entrySec),
                coldSec = p.getInt("coldSec", d.coldSec),
                lang = p.getString("lang", d.lang) ?: d.lang,
                musicUri = p.getString("musicUri", d.musicUri) ?: d.musicUri,
                musicVol = p.getFloat("musicVol", d.musicVol),
                voiceVol = p.getFloat("voiceVol", d.voiceVol),
                breathCues = p.getBoolean("breathCues", d.breathCues),
                acknowledged = p.getBoolean("acknowledged", d.acknowledged),
                mode = p.getString("mode", d.mode) ?: d.mode,
                voice = p.getString("voice", d.voice) ?: d.voice,
                theme = p.getString("theme", d.theme) ?: d.theme,
            )
        }
    }
}
