package com.example.taycancolorproxy

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.media.AudioManager
import android.os.Build

class SilentPlayer {

    companion object {
        private const val SAMPLE_RATE = 44100
        private const val CHANNEL_COUNT = 1
        private const val BYTES_PER_SAMPLE = 2 // PCM 16 bits
    }

    @Volatile
    private var running = false

    private var audioTrack: AudioTrack? = null
    private var worker: Thread? = null

    @Synchronized
    fun start() {
        if (running) return

        val minBufferSize = AudioTrack.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )

        if (minBufferSize <= 0) return

        val bufferSizeBytes = maxOf(minBufferSize * 2, SAMPLE_RATE / 5 * BYTES_PER_SAMPLE)

        val track = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val attributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()

                val format = AudioFormat.Builder()
                    .setSampleRate(SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .build()

                AudioTrack.Builder()
                    .setAudioAttributes(attributes)
                    .setAudioFormat(format)
                    .setBufferSizeInBytes(bufferSizeBytes)
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .build()
            } else {
                @Suppress("DEPRECATION")
                AudioTrack(
                    AudioManager.STREAM_MUSIC,
                    SAMPLE_RATE,
                    AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    bufferSizeBytes,
                    AudioTrack.MODE_STREAM
                )
            }
        } catch (_: Exception) {
            return
        }

        if (track.state != AudioTrack.STATE_INITIALIZED) {
            track.release()
            return
        }

        try {
            track.setVolume(0f)
            track.play()
        } catch (_: Exception) {
            track.release()
            return
        }

        audioTrack = track
        running = true

        val silence = ShortArray(bufferSizeBytes / BYTES_PER_SAMPLE)

        worker = Thread({
            while (running) {
                try {
                    val written = track.write(silence, 0, silence.size)
                    if (written < 0) break
                } catch (_: Exception) {
                    break
                }
            }
        }, "SilentPlayer").apply {
            isDaemon = true
            start()
        }
    }

    fun stop() {
        val trackToStop: AudioTrack?
        val workerToStop: Thread?

        synchronized(this) {
            running = false
            trackToStop = audioTrack
            workerToStop = worker
            audioTrack = null
            worker = null
        }

        try {
            trackToStop?.pause()
            trackToStop?.flush()
            trackToStop?.stop()
        } catch (_: Exception) {
            // Le lecteur était peut-être déjà arrêté.
        }

        try {
            trackToStop?.release()
        } catch (_: Exception) {
            // Le lecteur était peut-être déjà libéré.
        }

        workerToStop?.interrupt()
    }
}
