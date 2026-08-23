package com.example.taycancolorproxy

import android.graphics.Bitmap
import android.graphics.Color
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

        private const val ACTION_CHANGE_COLOR = "ACTION_CHANGE_COLOR"

        val palette = listOf(
            "#FF3DBB" to "#8A2BE2",
            "#00C6FF" to "#0072FF",
            "#00F260" to "#0575E6",
            "#FF8C00" to "#FF3D00",
            "#FF416C" to "#FF4B2B",
            "#FFE259" to "#FFA751",
            "#00FFA3" to "#00C2FF",
            "#7F00FF" to "#E100FF",
            "#FF9966" to "#FF5E62",
            "#0F2027" to "#2C5364",
            "#FFAFBD" to "#FFC3A0",
            "#A8E063" to "#56AB2F",
            "#FFD700" to "#FFA500",
            "#00FFFF" to "#0080FF",
            "#FF00CC" to "#333399",
            "#C09B6D" to "#8B5E3C",
            "#00B09B" to "#96C93D",
            "#C471ED" to "#F64F59",
            "#FF512F" to "#F09819",
            "#434343" to "#B0B0B0"
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
            override fun onCustomAction(action: String, extras: Bundle?) {
                if (action == ACTION_CHANGE_COLOR) cycleColor()
            }
        })

        session.setPlaybackState(buildState(PlaybackState.STATE_PAUSED, 0))
    }

    private fun cycleColor() {
        val prefs = getSharedPreferences("taycan_couleur", MODE_PRIVATE)
        val currentIndex = prefs.getInt("colorIndex", 0)
        val nextIndex = (currentIndex + 1) % palette.size
        val (c1, c2) = palette[nextIndex]
        prefs.edit()
            .putInt("colorIndex", nextIndex)
            .putString("color1", c1)
            .putString("color2", c2)
            .apply()

        updateMetadata(lastSourceMetadata)
    }

    private fun attach(sourceController: MediaController) {
        session.setCallback(object : MediaSession.Callback() {
            override fun onPlay() { sourceController.transportControls.pl
