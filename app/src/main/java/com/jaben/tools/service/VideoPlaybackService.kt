package com.jaben.tools.service

import android.app.PendingIntent
import android.content.Intent
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.jaben.tools.MainActivity

@OptIn(UnstableApi::class)
class VideoPlaybackService : MediaSessionService() {
    private var mediaSession: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        val dynamicHttpFactory = DataSource.Factory {
            val headers = buildMap {
                if (currentReferer.isNotBlank()) put("Referer", currentReferer)
                if (currentCookie.isNotBlank()) put("Cookie", currentCookie)
            }
            DefaultHttpDataSource.Factory()
                .setUserAgent(currentUserAgent.ifBlank { "Mozilla/5.0" })
                .setAllowCrossProtocolRedirects(true)
                .setDefaultRequestProperties(headers)
                .createDataSource()
        }

        val player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dynamicHttpFactory))
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                    .build(),
                true
            )
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .build()

        val launchIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val sessionActivity = PendingIntent.getActivity(
            this,
            0,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        mediaSession = MediaSession.Builder(this, player)
            .setSessionActivity(sessionActivity)
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = mediaSession?.player
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        mediaSession?.run {
            player.release()
            release()
            mediaSession = null
        }
        super.onDestroy()
    }

    companion object {
        @Volatile
        private var currentUserAgent: String = "Mozilla/5.0"

        @Volatile
        private var currentReferer: String = ""

        @Volatile
        private var currentCookie: String = ""

        fun updateHttpHeaders(userAgent: String, referer: String, cookie: String) {
            currentUserAgent = userAgent.ifBlank { "Mozilla/5.0" }
            currentReferer = referer
            currentCookie = cookie
        }
    }
}
