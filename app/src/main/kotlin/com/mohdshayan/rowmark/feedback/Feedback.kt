package com.mohdshayan.rowmark.feedback

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.os.Build
import com.mohdshayan.rowmark.R
import com.mohdshayan.rowmark.data.prefs.AppPrefs
import com.mohdshayan.rowmark.data.prefs.Settings
import com.mohdshayan.rowmark.data.repo.CountOutcome
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

/**
 * The tick for every count and the bell and buzz when a repeat or shaping step comes due. It works from
 * the pad, a key, the widget and the tile alike, so it uses the Vibrator rather than a view.
 */
class Feedback(context: Context, prefs: AppPrefs, scope: CoroutineScope) {
    private val appContext = context.applicationContext
    val settings: StateFlow<Settings> = prefs.settings.stateIn(scope, SharingStarted.Eagerly, Settings())

    private val vibrator: Vibrator? by lazy {
        if (Build.VERSION.SDK_INT >= 31) {
            (appContext.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            appContext.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }

    private val pool: SoundPool by lazy {
        SoundPool.Builder().setMaxStreams(2)
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
            .build()
    }
    private var bellId = 0
    private var bellLoaded = false

    private fun ensureBell() {
        if (bellId == 0) {
            pool.setOnLoadCompleteListener { _, _, status -> bellLoaded = status == 0 }
            bellId = pool.load(appContext, R.raw.repeat_bell, 1)
        }
    }

    fun warmUp() { if (settings.value.bell) ensureBell() }

    fun tick() {
        if (!settings.value.haptics) return
        val v = vibrator ?: return
        if (!v.hasVibrator()) return
        runCatching { v.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK)) }
    }

    fun alert() {
        val s = settings.value
        if (s.bell) {
            ensureBell()
            if (bellLoaded) pool.play(bellId, 1f, 1f, 1, 0, 1f)
        }
        if (s.alertVibrate) {
            val v = vibrator ?: return
            if (v.hasVibrator()) runCatching { v.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 60, 60, 120), -1)) }
        }
    }

    /** Called for every outcome the repository emits. */
    fun onOutcome(outcome: CountOutcome) {
        if (outcome.result.isEmpty) return
        tick()
        if (outcome.result.alerts.isNotEmpty() || outcome.dueSchedules.isNotEmpty()) alert()
    }
}
