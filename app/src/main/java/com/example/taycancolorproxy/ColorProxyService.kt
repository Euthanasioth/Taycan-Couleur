package com.example.taycancolorproxy

import android.graphics.Bitmap
import android.graphics.Color
import android.media.MediaDescription
import android.media.MediaMetadata
import android.media.browse.MediaBrowser
import android.media.session.MediaController
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Bundle
import android.service.media.MediaBrowserService

class ColorProxyService : MediaBrowserService() {

    private lateinit var session: MediaSession
    private var lastSourceMetadata: MediaMetadata? = null

    companion object {
        private var instance: ColorProxyService? = null

        fun attachSourceController(sourceController: MediaController) {
            instance?.attach(sourceController)
        }

        val palette = listOf(
            "Rose / Violet" to Pair("#FF3DBB", "#8A2BE2"),
            "Bleu" to Pair("#00C6FF", "#0072FF"),
            "Vert" to Pair("#00F260", "#0575E6"),
            "Orange" to Pair("#FF8C00", "#FF3D00"),
            "Rouge" to Pair("#FF416C", "#FF4B2B"),
            "Jaune" to Pair("#FFE259", "#FFA751"),
            "Turquoise" to Pair("#00FFA3", "#00C2FF"),
            "Violet foncé" to Pair("#7F00FF", "#E100FF"),
            "Corail" to Pair("#FF9966", "#FF5E62"),
            "Bleu nuit" to Pair("#0F2027", "#2C5364"),
            "Rose pastel" to Pair("#FFAFBD", "#FFC3A0"),
            "Vert lime" to Pair("#A8E063", "#56AB2F"),
            "Or" to Pair("#FFD700", "#FFA500"),
            "Cyan" to Pair("#00FFFF", "#0080FF"),
            "Magenta" to Pair("#FF00CC", "#333399"),
            "Bronze" to Pair("#C09B6D", "#8B5E3C"),
            "Menthe" to Pair("#00B09B", "#96C93D"),
            "Lavande" to Pair("#C471ED", "#F64F59"),
            "Sunset" to Pair("#FF512F", "#F09819"),
            "Noir / Argent" to Pair("#434343", "#B0B0B0")
        )
    }

    override fun onCreate() {
        super.onCreate()
        instance = this

        session = MediaSession(this, "TaycanColorProxy")
        session.setFlags(
            MediaSession.FLAG_HANDLES_MEDIA_BUTTONS or
            MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS
        )
        sessionToken = session.sessionToken
        session.isActive = true

        session.setCallback(object : MediaSession.Callback() {
            override fun onPlayFromMediaId(mediaId: String, extras: Bundle?) {
                selectColor(mediaId)
            }
        })

        session.setPlaybackState(buildState(PlaybackState.STATE_PAUSED, 0))
    }

    private fun selectColor(mediaId: String) {
        val index = mediaId.toIntOrNull() ?: return
        if (index < 0 || index >= palette.size) return
        val (_, colors) = palette[index]

        getSharedPreferences("taycan_couleur", MODE_PRIVATE).edit()
            .putInt("colorIndex", index)
            .putString("color1", colors.first)
            .putString("color2", colors.second)
            .apply()

        updateMetadata(lastSourceMetadata)
    }

    private fun attach(sourceController: MediaController) {
        session.setCallback(object : MediaSession.Callback() {
            override fun onPlay() { sourceController.transportControls.play() }
            override fun onPause() { sourceController.transportControls.pause() }
            override fun onSkipToNext() { sourceController.transportControls.skipToNext() }
            override fun onSkipToPrevious() { sourceController.transportControls.skipToPrevious() }
            override fun onStop() { sourceController.transportControls.stop() }
            override fun onPlayFromMediaId(mediaId: String, extras: Bundle?) {
                selectColor(mediaId)
            }
        })

        val callback = object : MediaController.Callback() {
            override fun onMetadataChanged(metadata: MediaMetadata?) {
                updateMetadata(metadata)
            }
            override fun onPlaybackStateChanged(state: PlaybackState?) {
                session.setPlaybackState(buildState(
                    state?.state ?: PlaybackState.STATE_PAUSED,
                    state?.position ?: 0
                ))
            }
        }
        sourceController.registerCallback(callback)

        updateMetadata(sourceController.metadata)
        val currentState = sourceController.playbackState
        session.setPlaybackState(buildState(
            cur
