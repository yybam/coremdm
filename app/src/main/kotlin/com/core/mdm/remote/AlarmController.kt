package com.core.mdm.remote

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object AlarmController {

    private var player: MediaPlayer? = null

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    fun playAlarm(context: Context) {
        stopAlarm()
        val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            ?: run { Log.e("AlarmController", "No alarm/ringtone URI"); return }
        Log.d("AlarmController", "Preparing alarm: $uri")
        try {
            val mp = MediaPlayer()
            mp.setDataSource(context.applicationContext, uri)
            mp.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            mp.isLooping = true
            mp.setOnPreparedListener { mediaPlayer ->
                if (player === mediaPlayer) {
                    mediaPlayer.start()
                    _isPlaying.value = true
                    Log.d("AlarmController", "Alarm started")
                } else {
                    Log.d("AlarmController", "onPrepared for stale player — releasing")
                    runCatching { mediaPlayer.release() }
                }
            }
            mp.setOnErrorListener { _, what, extra ->
                Log.e("AlarmController", "MediaPlayer error: what=$what extra=$extra")
                false
            }
            player = mp
            mp.prepareAsync()
        } catch (e: Exception) {
            Log.e("AlarmController", "Failed to set up alarm: ${e.message}", e)
            player?.release()
            player = null
        }
    }

    fun stopAlarm() {
        val mp = player
        player = null
        _isPlaying.value = false
        runCatching { mp?.stop() }
        runCatching { mp?.release() }
    }
}
