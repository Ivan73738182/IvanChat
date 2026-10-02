package com.ivangames.ivanchat

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator

object SoundHelper {

    private var tone: ToneGenerator? = null

    fun playSend(ctx: Context) {
        try {
            if (tone == null) {
                tone = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 40)
            }
            tone?.startTone(ToneGenerator.TONE_PROP_ACK, 80)
        } catch (e: Exception) {
            // ignore
        }
    }

    fun playReceive(ctx: Context) {
        try {
            if (tone == null) {
                tone = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 40)
            }
            tone?.startTone(ToneGenerator.TONE_PROP_BEEP, 100)
        } catch (e: Exception) {
            // ignore
        }
    }

    fun release() {
        tone?.release()
        tone = null
    }
}
