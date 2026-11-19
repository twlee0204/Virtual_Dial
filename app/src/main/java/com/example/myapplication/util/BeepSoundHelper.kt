package com.example.myapplication.util

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.util.Log

/**
 * 다이얼 회전 시 10ms 비프음 및 한계 범위(최대/최소) 도달 시 10ms 3회 비프음을 재생하는 헬퍼.
 */
class BeepSoundHelper(@Suppress("UNUSED_PARAMETER") context: Context) {

    private var toneGenerator: ToneGenerator? = null

    init {
        try {
            toneGenerator = ToneGenerator(AudioManager.STREAM_SYSTEM, 80)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to create ToneGenerator", e)
        }
    }

    /** 일반 단계 회전 시 10ms 비프음 재생 */
    fun playStepBeep() {
        try {
            toneGenerator?.startTone(ToneGenerator.TONE_PROP_BEEP, 10)
        } catch (e: Exception) {
            Log.w(TAG, "playStepBeep failed", e)
        }
    }

    /** 최대/최소 한계 범위에서 추가 회전 시 10ms 비프음 3회 연속 재생 */
    fun playBoundaryWarningBeep() {
        val tg = toneGenerator ?: return
        Thread {
            try {
                repeat(3) {
                    tg.startTone(ToneGenerator.TONE_PROP_BEEP, 10)
                    Thread.sleep(30)
                }
            } catch (e: Exception) {
                Log.w(TAG, "playBoundaryWarningBeep failed", e)
            }
        }.start()
    }

    fun release() {
        runCatching { toneGenerator?.release() }
        toneGenerator = null
    }

    companion object {
        private const val TAG = "BeepSoundHelper"
    }
}
