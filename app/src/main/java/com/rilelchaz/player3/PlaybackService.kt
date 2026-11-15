package com.rilelchaz.player3

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.util.BitmapLoader
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.rilelchaz.player3.data.ArtLoader
import java.util.concurrent.Callable
import java.util.concurrent.Executors

@OptIn(UnstableApi::class)
class PlaybackService : MediaSessionService() {

    private var session: MediaSession? = null
    private var exo: ExoPlayer? = null
    private var outputMode = 0
    private val audioManager by lazy { getSystemService(Context.AUDIO_SERVICE) as AudioManager }

    private val deviceCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) = applyOutput()
        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) = applyOutput()
    }

    override fun onCreate() {
        super.onCreate()
        val player = ExoPlayer.Builder(this)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                /* handleAudioFocus = */ true
            )
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .build()
        exo = player

        val openApp = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        session = MediaSession.Builder(this, player)
            .setCallback(Callback(this, player))
            .setSessionActivity(openApp)
            .setBitmapLoader(ArtBitmapLoader(this))
            .build()

        audioManager.registerAudioDeviceCallback(deviceCallback, Handler(Looper.getMainLooper()))
    }

    /** Принудительный выход звука. mode: 0 авто, 1 динамик, 2 разговорный, 3 проводные, 4 bluetooth, 5 usb. */
    fun setOutput(mode: Int, pauseOnUnplug: Boolean) {
        outputMode = mode
        exo?.setHandleAudioBecomingNoisy(pauseOnUnplug)
        applyOutput()
    }

    private fun applyOutput() {
        val p = exo ?: return
        val wanted: Set<Int>? = when (outputMode) {
            1 -> setOf(2)               // TYPE_BUILTIN_SPEAKER
            2 -> setOf(1)               // TYPE_BUILTIN_EARPIECE
            3 -> setOf(3, 4, 5)         // WIRED_HEADSET, WIRED_HEADPHONES, LINE_ANALOG
            4 -> setOf(8, 7, 26, 27)    // BT A2DP, BT SCO, BLE headset, BLE speaker
            5 -> setOf(22, 11, 12)      // USB headset, USB device, USB accessory
            else -> null
        }
        val device = wanted?.let { w ->
            audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS).firstOrNull { it.type in w }
        }
        p.setPreferredAudioDevice(device) // null = как решит система
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onTaskRemoved(rootIntent: Intent?) {
        val p = session?.player
        if (p == null || !p.playWhenReady || p.mediaItemCount == 0) stopSelf()
    }

    override fun onDestroy() {
        try {
            audioManager.unregisterAudioDeviceCallback(deviceCallback)
        } catch (e: Exception) {
        }
        session?.run {
            player.release()
            release()
        }
        session = null
        exo = null
        super.onDestroy()
    }

    private class Callback(
        private val service: PlaybackService,
        private val player: ExoPlayer,
    ) : MediaSession.Callback {

        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo
        ): MediaSession.ConnectionResult {
            val commands = MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon()
                .add(SessionCommand(CMD_SESSION_ID, Bundle.EMPTY))
                .add(SessionCommand(CMD_OUTPUT, Bundle.EMPTY))
                .build()
            return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                .setAvailableSessionCommands(commands)
                .build()
        }

        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle
        ): ListenableFuture<SessionResult> {
            when (customCommand.customAction) {
                CMD_SESSION_ID -> {
                    val extras = Bundle().apply { putInt(EXTRA_SESSION_ID, player.audioSessionId) }
                    return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS, extras))
                }
                CMD_OUTPUT -> {
                    service.setOutput(args.getInt(EXTRA_MODE, 0), args.getBoolean(EXTRA_NOISY, true))
                    return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                }
            }
            return super.onCustomCommand(session, controller, customCommand, args)
        }
    }

    /** Достаёт обложку трека для уведомления и экрана блокировки. */
    private class ArtBitmapLoader(private val context: Context) : BitmapLoader {
        private val executor = Executors.newSingleThreadExecutor()

        override fun supportsMimeType(mimeType: String): Boolean = true

        override fun decodeBitmap(data: ByteArray): ListenableFuture<Bitmap> =
            Futures.submit(Callable {
                BitmapFactory.decodeByteArray(data, 0, data.size)
                    ?: throw IllegalStateException("decode failed")
            }, executor)

        override fun loadBitmap(uri: Uri): ListenableFuture<Bitmap> =
            Futures.submit(Callable {
                ArtLoader.load(context, uri, 512) ?: throw IllegalStateException("no artwork")
            }, executor)
    }

    companion object {
        const val CMD_SESSION_ID = "com.rilelchaz.player3.GET_SESSION_ID"
        const val CMD_OUTPUT = "com.rilelchaz.player3.SET_OUTPUT"
        const val EXTRA_SESSION_ID = "sessionId"
        const val EXTRA_MODE = "mode"
        const val EXTRA_NOISY = "noisy"
    }
}
