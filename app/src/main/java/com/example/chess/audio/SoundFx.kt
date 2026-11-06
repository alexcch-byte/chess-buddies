package com.example.chess.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.exp
import kotlin.math.sin
import kotlin.random.Random

/** Small synthesized sound effects, so the app ships with no audio files. */
class SoundFx(private val isEnabled: () -> Boolean) {

    enum class Effect { SELECT, MOVE, CAPTURE, CHECK, ILLEGAL, STAR, SUCCESS, WIN, LOSE, OOPS, PROMOTE }

    private enum class Wave { SINE, TRIANGLE, SQUARE }

    private class Note(
        val freq: Float,
        val ms: Int,
        val wave: Wave = Wave.SINE,
        val volume: Float = 0.5f,
        val endFreq: Float = freq,
        val noise: Float = 0f,
        val decay: Float = 6f,
    )

    private val sampleRate = 22050
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val cache = ConcurrentHashMap<Effect, ShortArray>()
    private val trackSemaphore = Semaphore(3)

    fun play(effect: Effect) {
        if (!isEnabled()) return
        scope.launch {
            try {
                trackSemaphore.withPermit {
                    val pcm = cache.getOrPut(effect) { render(notesFor(effect)) }
                    var track: AudioTrack? = null
                    try {
                        val builtTrack = AudioTrack.Builder()
                            .setAudioAttributes(
                                AudioAttributes.Builder()
                                    .setUsage(AudioAttributes.USAGE_GAME)
                                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                                    .build()
                            )
                            .setAudioFormat(
                                AudioFormat.Builder()
                                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                                    .setSampleRate(sampleRate)
                                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                                    .build()
                            )
                            .setBufferSizeInBytes(pcm.size * 2)
                            .setTransferMode(AudioTrack.MODE_STATIC)
                            .build()
                        track = builtTrack
                        builtTrack.write(pcm, 0, pcm.size)
                        builtTrack.play()
                        delay(pcm.size * 1000L / sampleRate + 80)
                    } finally {
                        try {
                            track?.stop()
                        } catch (_: Exception) {}
                        try {
                            track?.release()
                        } catch (_: Exception) {}
                    }
                }
            } catch (_: Exception) {
                // Sound is a nice extra; never crash the game over it.
            }
        }
    }

    fun release() {
        scope.cancel()
    }

    private fun notesFor(effect: Effect): List<Note> = when (effect) {
        Effect.SELECT -> listOf(Note(1250f, 30, volume = 0.18f, decay = 9f))
        Effect.MOVE -> listOf(Note(240f, 75, Wave.TRIANGLE, 0.55f, endFreq = 150f, noise = 0.25f, decay = 9f))
        Effect.CAPTURE -> listOf(
            Note(330f, 45, Wave.TRIANGLE, 0.5f, endFreq = 220f, noise = 0.35f, decay = 7f),
            Note(170f, 110, Wave.TRIANGLE, 0.6f, endFreq = 100f, noise = 0.3f, decay = 6f),
        )
        Effect.CHECK -> listOf(Note(880f, 90, Wave.TRIANGLE, 0.4f, decay = 3f), Note(660f, 140, Wave.TRIANGLE, 0.4f, decay = 3f))
        Effect.ILLEGAL -> listOf(Note(160f, 140, Wave.SQUARE, 0.12f, endFreq = 120f, decay = 4f))
        Effect.STAR -> listOf(Note(1568f, 70, volume = 0.35f, decay = 3f), Note(2093f, 220, volume = 0.35f, decay = 5f))
        Effect.SUCCESS -> listOf(523f, 659f, 784f).map { Note(it, 95, Wave.TRIANGLE, 0.4f, decay = 2f) } +
            Note(1047f, 300, Wave.TRIANGLE, 0.45f, decay = 4f)
        Effect.WIN -> listOf(523f, 659f, 784f, 1047f, 784f).map { Note(it, 120, Wave.TRIANGLE, 0.42f, decay = 1.5f) } +
            Note(1047f, 520, Wave.TRIANGLE, 0.45f, decay = 3f)
        Effect.LOSE -> listOf(Note(392f, 220, Wave.TRIANGLE, 0.35f, decay = 2f), Note(330f, 220, Wave.TRIANGLE, 0.35f, decay = 2f), Note(262f, 420, Wave.TRIANGLE, 0.35f, decay = 3f))
        Effect.OOPS -> listOf(Note(440f, 120, Wave.TRIANGLE, 0.35f, decay = 3f), Note(330f, 220, Wave.TRIANGLE, 0.35f, endFreq = 300f, decay = 4f))
        Effect.PROMOTE -> listOf(Note(600f, 260, volume = 0.35f, endFreq = 1500f, decay = 2f), Note(1760f, 260, volume = 0.3f, decay = 5f))
    }

    private fun render(notes: List<Note>): ShortArray {
        val total = notes.sumOf { sampleRate * it.ms / 1000 }
        val out = ShortArray(total)
        var offset = 0
        for (note in notes) {
            val n = sampleRate * note.ms / 1000
            val attack = (sampleRate * 0.004).toInt().coerceAtLeast(1)
            var phase = 0.0
            for (i in 0 until n) {
                val t = i.toFloat() / n
                val freq = note.freq + (note.endFreq - note.freq) * t
                phase += 2.0 * PI * freq / sampleRate
                val s = sin(phase)
                var sample = when (note.wave) {
                    Wave.SINE -> s.toFloat()
                    Wave.TRIANGLE -> (2.0 / PI * asin(s)).toFloat()
                    Wave.SQUARE -> if (s >= 0) 1f else -1f
                }
                if (note.noise > 0f) sample = sample * (1f - note.noise) + (Random.nextFloat() * 2f - 1f) * note.noise
                val env = (if (i < attack) i.toFloat() / attack else 1f) * exp(-note.decay * t) * (1f - t * t * t)
                val v = (sample * env * note.volume * Short.MAX_VALUE).toInt()
                out[offset + i] = v.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
            }
            offset += n
        }
        return out
    }
}
