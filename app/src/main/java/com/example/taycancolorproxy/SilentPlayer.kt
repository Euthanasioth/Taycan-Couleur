package com.example.taycancolorproxy

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack

class SilentPlayer {
    private var track: AudioTrack? = null
    private var thread: Thread? = null
    @Volatile private var running = false

    fun start() {
        if (running) return
        try {
            val sampleRate = 8000
            val minBuf = AudioTrack.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )
            if (minBuf <= 0) return
            val t = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(minBuf * 2)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()
            track = t
            running = true
            t.play()
            thread = Thread {
                val buf = ByteArray(minBuf)
                while (running) {
                    val n = t.write(buf, 0, buf.size)
                    if (n < 0) break
                }
            }
            thread?.start()
        } catch (e: Exception) {
            running = false
        }
    }

    fun stop() {
        running = false
        try { track?.stop() } catch (e: Exception) { }
        try { thread?.join(500) } catch (e: Exception) { }
        try { track?.release() } catch (e: Exception) { }
        track = null
        thread = null
    }

    fun restart() {
        stop()
        start()
    }
}
