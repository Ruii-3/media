package com.example.automediaplayer.manager

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.util.Log

/**
 * 第 5 周：把系统音频焦点的四种状态翻译成播放业务回调。
 *
 * 协作关系：MusicPlayService 持有本类，在开始播放前申请焦点，暂停/停止/销毁时释放；
 * 焦点变化时由本类回调 Service，Service 再决定暂停、降音、恢复或停止。
 */
interface AudioFocusListener {
    fun onAudioFocusGained()
    fun onAudioFocusLostTransient()
    fun onAudioFocusLostTransientCanDuck()
    fun onAudioFocusLostPermanent()
}

class AudioFocusManager(context: Context) {

    companion object {
        private const val TAG = "AudioFocusManager"
        const val AUDIO_FOCUS_REQUEST_CODE = 1001
    }

    private val audioManager = context.applicationContext
        .getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val mainHandler = Handler(Looper.getMainLooper())

    private var listener: AudioFocusListener? = null
    private var focusRequest: AudioFocusRequest? = null
    private var focused = false

    /** 申请 AUDIOFOCUS_GAIN；拿到焦点才允许真正开始播放。 */
    fun requestAudioFocus(listener: AudioFocusListener): Boolean {
        this.listener = listener
        // 短暂丢失后用户又主动点播放时，先释放旧的焦点请求，避免同一应用
        // 累积多个 AudioFocusRequest。
        focusRequest?.let { old ->
            runCatching { audioManager.abandonAudioFocusRequest(old) }
        }
        val attributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build()
        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(attributes)
            .setAcceptsDelayedFocusGain(false)
            // 本工程自行处理 CAN_DUCK，把音量降到 30%，不让系统直接暂停。
            .setWillPauseWhenDucked(false)
            .setOnAudioFocusChangeListener({ change -> dispatchFocusChange(change) }, mainHandler)
            .build()
        focusRequest = request
        focused = audioManager.requestAudioFocus(request) ==
            AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        Log.i(TAG, "requestAudioFocus: granted=$focused")
        return focused
    }

    /** 与 requestAudioFocus 成对调用，避免继续占用导航/电话的音频通道。 */
    fun abandonAudioFocus() {
        val request = focusRequest
        if (request != null) {
            audioManager.abandonAudioFocusRequest(request)
        }
        focusRequest = null
        focused = false
        listener = null
        Log.i(TAG, "abandonAudioFocus")
    }

    fun hasFocus(): Boolean = focused

    private fun dispatchFocusChange(change: Int) {
        when (change) {
            AudioManager.AUDIOFOCUS_GAIN -> {
                focused = true
                Log.i(TAG, "focus=GAIN")
                listener?.onAudioFocusGained()
            }
            AudioManager.AUDIOFOCUS_LOSS -> {
                focused = false
                Log.i(TAG, "focus=LOSS")
                listener?.onAudioFocusLostPermanent()
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                focused = false
                Log.i(TAG, "focus=LOSS_TRANSIENT")
                listener?.onAudioFocusLostTransient()
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                // CAN_DUCK 不真正失去焦点，只要求让出音量；恢复由 GAIN 回调完成。
                Log.i(TAG, "focus=LOSS_TRANSIENT_CAN_DUCK")
                listener?.onAudioFocusLostTransientCanDuck()
            }
            else -> Log.w(TAG, "focus=unknown($change)")
        }
    }
}
