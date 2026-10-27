package de.eisbad.app

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.suspendCancellableCoroutine
import org.json.JSONObject
import java.util.Locale
import java.util.UUID
import kotlin.coroutines.resume

/** Texte aller Ansagen (assets/phrases.json): key -> Sprache -> Text. */
class PhraseBook(ctx: Context) {
    private val root: JSONObject =
        ctx.assets.open("phrases.json").bufferedReader().use { JSONObject(it.readText()) }

    fun text(key: String, lang: String): String? =
        root.optJSONObject(key)?.optString(lang)?.takeIf { it.isNotBlank() }
}

/** Wählbare Stimmen (assets/voices.json); die Clips liegen in assets/voice/<id>/<lang>/<key>.mp3. */
data class VoiceSet(val id: String, val label: String)

fun loadVoiceSets(ctx: Context): List<VoiceSet> = try {
    val root = ctx.assets.open("voices.json").bufferedReader().use { JSONObject(it.readText()) }
    val arr = root.getJSONArray("sets")
    List(arr.length()) { i ->
        val o = arr.getJSONObject(i)
        VoiceSet(o.getString("id"), o.getString("label"))
    }
} catch (e: Exception) {
    emptyList()
}

/**
 * Spielt Ansagen ab. Reihenfolge: gewählte Stimme (assets/voice/<stimme>/<lang>/<key>.mp3),
 * dann die alten Standard-Clips (assets/voice/<lang>/<key>.mp3), zuletzt die Android-Systemstimme.
 */
class VoicePlayer(private val ctx: Context, private val book: PhraseBook) {
    private var tts: TextToSpeech? = null
    private var ttsReady = false

    init {
        tts = TextToSpeech(ctx) { status -> ttsReady = status == TextToSpeech.SUCCESS }
    }

    suspend fun play(key: String, lang: String, voice: String, volume: Float) {
        if (voice.isNotBlank() && playAsset("voice/$voice/$lang/$key.mp3", volume)) return
        if (playAsset("voice/$lang/$key.mp3", volume)) return
        val text = book.text(key, lang) ?: return
        speakTts(text, lang, volume)
    }

    private suspend fun playAsset(path: String, vol: Float): Boolean =
        suspendCancellableCoroutine { cont ->
            val mp = MediaPlayer()
            cont.invokeOnCancellation {
                try {
                    mp.release()
                } catch (_: Exception) {
                }
            }
            try {
                ctx.assets.openFd(path).use { afd ->
                    mp.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                }
                mp.setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                mp.setVolume(vol, vol)
                mp.setOnCompletionListener {
                    mp.release()
                    if (cont.isActive) cont.resume(true)
                }
                mp.setOnErrorListener { _, _, _ ->
                    mp.release()
                    if (cont.isActive) cont.resume(false)
                    true
                }
                mp.prepare()
                mp.start()
            } catch (e: Exception) {
                try {
                    mp.release()
                } catch (_: Exception) {
                }
                if (cont.isActive) cont.resume(false)
            }
        }

    private suspend fun speakTts(text: String, lang: String, vol: Float): Boolean {
        val t = tts ?: return false
        if (!ttsReady) return false
        t.language = if (lang == "de") Locale.GERMAN else Locale.ENGLISH
        t.setPitch(0.7f)
        t.setSpeechRate(0.85f)
        return suspendCancellableCoroutine { cont ->
            val id = UUID.randomUUID().toString()
            t.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {}
                override fun onDone(utteranceId: String?) {
                    if (utteranceId == id && cont.isActive) cont.resume(true)
                }

                @Deprecated("Deprecated in API 21")
                override fun onError(utteranceId: String?) {
                    if (utteranceId == id && cont.isActive) cont.resume(false)
                }
            })
            val params = Bundle().apply { putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, vol) }
            t.speak(text, TextToSpeech.QUEUE_FLUSH, params, id)
            cont.invokeOnCancellation { t.stop() }
        }
    }

    fun release() {
        tts?.stop()
        tts?.shutdown()
        tts = null
    }
}

/** Hintergrundmusik aus einer vom Nutzer gewählten Datei, in Schleife, mit Ducking. */
class MusicPlayer(ctx: Context, uri: Uri, private val baseVol: Float) {
    private var mp: MediaPlayer? = null

    init {
        val player = MediaPlayer()
        try {
            player.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            player.setDataSource(ctx, uri)
            player.isLooping = true
            player.setVolume(baseVol, baseVol)
            player.prepare()
            player.start()
            mp = player
        } catch (e: Exception) {
            try {
                player.release()
            } catch (_: Exception) {
            }
            mp = null
        }
    }

    /** Musik leiser, solange die Stimme spricht. */
    fun duck(on: Boolean) {
        val v = if (on) baseVol * 0.35f else baseVol
        try {
            mp?.setVolume(v, v)
        } catch (_: Exception) {
        }
    }

    fun release() {
        try {
            mp?.stop()
        } catch (_: Exception) {
        }
        mp?.release()
        mp = null
    }
}
