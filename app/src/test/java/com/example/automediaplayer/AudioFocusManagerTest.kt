package com.example.automediaplayer

import android.content.Context
import android.media.AudioFocusRequest
import android.media.AudioManager
import androidx.test.core.app.ApplicationProvider
import com.example.automediaplayer.manager.AudioFocusListener
import com.example.automediaplayer.manager.AudioFocusManager
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.util.ReflectionHelpers

/**
 * 第 6 周 CP6：AudioFocusManager 单元测试。
 *
 * 使用 Robolectric 提供 Android 运行环境，用 Mockito 替换 AudioManager，
 * 再用反射注入私有字段，从而在不接触真实音频设备的前提下验证四类焦点回调。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31])
class AudioFocusManagerTest {

    private lateinit var audioManager: AudioManager
    private lateinit var focusManager: AudioFocusManager
    private lateinit var listener: RecordingFocusListener

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        audioManager = mock(AudioManager::class.java)
        focusManager = AudioFocusManager(context)
        // AudioFocusManager 的 audioManager 是 private val，测试通过反射注入 mock。
        ReflectionHelpers.setField(focusManager, "audioManager", audioManager)
        listener = RecordingFocusListener()
    }

    private fun grantFocus() {
        `when`(audioManager.requestAudioFocus(any(AudioFocusRequest::class.java)))
            .thenReturn(AudioManager.AUDIOFOCUS_REQUEST_GRANTED)
    }

    private fun dispatch(change: Int) {
        ReflectionHelpers.callInstanceMethod<Any>(
            focusManager,
            "dispatchFocusChange",
            ReflectionHelpers.ClassParameter.from(Int::class.javaPrimitiveType!!, change)
        )
    }

    @Test
    fun requestFocusGrantedReturnsTrueAndHasFocus() {
        grantFocus()

        assertTrue(focusManager.requestAudioFocus(listener))
        assertTrue(focusManager.hasFocus())
        verify(audioManager).requestAudioFocus(any(AudioFocusRequest::class.java))
    }

    @Test
    fun requestFocusDeniedReturnsFalseAndHasNoFocus() {
        `when`(audioManager.requestAudioFocus(any(AudioFocusRequest::class.java)))
            .thenReturn(AudioManager.AUDIOFOCUS_REQUEST_FAILED)

        assertFalse(focusManager.requestAudioFocus(listener))
        assertFalse(focusManager.hasFocus())
    }

    @Test
    fun abandonFocusClearsStateAndCallsSystem() {
        grantFocus()
        focusManager.requestAudioFocus(listener)

        focusManager.abandonAudioFocus()

        assertFalse(focusManager.hasFocus())
        verify(audioManager).abandonAudioFocusRequest(any(AudioFocusRequest::class.java))
    }

    @Test
    fun gainCallbackRestoresFocusAndNotifiesListener() {
        grantFocus()
        focusManager.requestAudioFocus(listener)
        dispatch(AudioManager.AUDIOFOCUS_LOSS_TRANSIENT)
        assertFalse(focusManager.hasFocus())

        dispatch(AudioManager.AUDIOFOCUS_GAIN)

        assertTrue(focusManager.hasFocus())
        assertTrue(listener.gained == 1)
    }

    @Test
    fun transientLossClearsFocusAndPausesListener() {
        grantFocus()
        focusManager.requestAudioFocus(listener)

        dispatch(AudioManager.AUDIOFOCUS_LOSS_TRANSIENT)

        assertFalse(focusManager.hasFocus())
        assertTrue(listener.transientLoss == 1)
        assertTrue(listener.gained == 0)
    }

    @Test
    fun canDuckKeepsFocusAndNotifiesListener() {
        grantFocus()
        focusManager.requestAudioFocus(listener)

        dispatch(AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK)

        assertTrue(focusManager.hasFocus())
        assertTrue(listener.canDuck == 1)
        assertTrue(listener.transientLoss == 0)
    }

    @Test
    fun permanentLossClearsFocusAndNotifiesListener() {
        grantFocus()
        focusManager.requestAudioFocus(listener)

        dispatch(AudioManager.AUDIOFOCUS_LOSS)

        assertFalse(focusManager.hasFocus())
        assertTrue(listener.permanentLoss == 1)
    }

    private class RecordingFocusListener : AudioFocusListener {
        var gained = 0
        var transientLoss = 0
        var canDuck = 0
        var permanentLoss = 0

        override fun onAudioFocusGained() {
            gained++
        }

        override fun onAudioFocusLostTransient() {
            transientLoss++
        }

        override fun onAudioFocusLostTransientCanDuck() {
            canDuck++
        }

        override fun onAudioFocusLostPermanent() {
            permanentLoss++
        }
    }
}