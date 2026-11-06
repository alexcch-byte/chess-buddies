package com.example.chess.audio

import android.content.Context
import android.media.AudioAttributes
import android.os.SystemClock
import android.speech.tts.TextToSpeech
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.Locale

/** Turns the words on screen into words that sound right when read out loud. */
object Narration {
    // Pictures, arrows and symbols that a voice would read as "light bulb" or "right arrow".
    private val NOT_SPOKEN = Regex("[\\p{So}\\p{Sk}\\u200D\\uFE0F\\u20E3\\u2190-\\u21FF]")
    private val SPACES = Regex("\\s+")

    fun clean(text: String): String = NOT_SPOKEN.replace(text, " ").replace(SPACES, " ").trim()
}

/**
 * Sir Hops' voice: reads lesson instructions aloud with the device's own text-to-speech engine, so the app ships with no
 * recordings and children who can't read yet can still follow along. One instance is shared by every lesson screen.
 */
class Narrator private constructor(context: Context) {
    private var tts: TextToSpeech? = null
    private var ready = false
    private var pending: String? = null
    private var owner: Any? = null
    private val recent = HashMap<String, Long>()

    /** False when this device has no voice we can use, so the screen can hide its "Listen" button. */
    var available by mutableStateOf(true)
        private set

    init {
        tts = try {
            TextToSpeech(context) { status -> onReady(status) }
        } catch (_: Exception) {
            available = false
            null
        }
    }

    private fun onReady(status: Int) {
        val engine = tts
        if (status != TextToSpeech.SUCCESS || engine == null) {
            available = false
            return
        }
        var result = engine.setLanguage(Locale.US)
        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
            result = engine.setLanguage(Locale.getDefault())
        }
        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
            available = false
            return
        }
        engine.setSpeechRate(0.92f)
        engine.setPitch(1.08f)
        engine.setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
        )
        ready = true
        pending?.let {
            pending = null
            engine.speak(it, TextToSpeech.QUEUE_FLUSH, null, UTTERANCE)
        }
    }

    /** A lesson screen takes over the voice. Lines read a moment ago are fair game again. */
    fun attach(screen: Any) {
        owner = screen
        recent.clear()
    }

    /** Silences the voice, unless a newer screen has already taken over (screens are swapped, not stacked, between lessons). */
    fun detach(screen: Any) {
        if (owner !== screen) return
        owner = null
        stop()
    }

    /**
     * Reads [text] out loud, interrupting whatever is being said. A line that was just read isn't repeated (the coach says
     * the same instruction again after every ordinary move) unless the child asked for it with [force].
     */
    fun say(text: String, force: Boolean = false) {
        val spoken = Narration.clean(text)
        if (spoken.isEmpty() || !available) return
        val now = SystemClock.elapsedRealtime()
        if (!force && now - (recent[spoken] ?: -REPEAT_GAP_MS) < REPEAT_GAP_MS) return
        recent[spoken] = now
        val engine = tts
        if (!ready || engine == null) {
            pending = spoken
            return
        }
        engine.speak(spoken, TextToSpeech.QUEUE_FLUSH, null, UTTERANCE)
    }

    fun stop() {
        pending = null
        if (ready) tts?.stop()
    }

    companion object {
        private const val UTTERANCE = "sir-hops"
        private const val REPEAT_GAP_MS = 25_000L

        @Volatile private var instance: Narrator? = null

        fun get(context: Context): Narrator =
            instance ?: synchronized(this) {
                instance ?: Narrator(context.applicationContext).also { instance = it }
            }
    }
}
