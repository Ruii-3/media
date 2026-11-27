package com.example.automediaplayer

import android.Manifest
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.os.Build
import android.os.SystemClock
import android.view.View
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.UiController
import androidx.test.espresso.ViewAction
import androidx.test.espresso.action.ViewActions.scrollTo
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withContentDescription
import androidx.test.espresso.matcher.ViewMatchers.withEffectiveVisibility
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.espresso.matcher.ViewMatchers.Visibility
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import com.example.automediaplayer.ui.MainActivity
import org.hamcrest.Matcher
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

/**
 * 扩展练习：主界面 Espresso UI 自动化测试。
 *
 * 覆盖三个不依赖真实音频文件的稳定场景：
 * 1. 车机横屏主框架与收藏筛选按钮；
 * 2. 播放设置对话框；
 * 3. 座舱场景演示对话框。
 *
 * 音频扫描、真实播放和 USB 插拔属于设备/外设相关场景，保留在 CP6 真机集成测试中。
 */
@RunWith(AndroidJUnit4::class)
class MainActivityEspressoTest {

    private companion object {
        const val ORIENTATION_TIMEOUT_MS = 5_000L
    }

    private val permissionRule: GrantPermissionRule = GrantPermissionRule.grant(
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        },
        *if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            emptyArray()
        }
    )

    private val activityRule = ActivityScenarioRule(MainActivity::class.java)

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(permissionRule).around(activityRule)

    @Before
    fun forceCarLandscapeLayout() {
        activityRule.scenario.onActivity {
            it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        }
        awaitLandscape(activityRule.scenario)
    }

    @Test
    fun mainScreenShowsCarLayoutAndFavoriteFilter() {
        onView(withId(R.id.tv_app_name)).check(matches(withText(R.string.app_name)))
        onView(withId(R.id.tv_now_playing_label)).check(matches(isDisplayed()))
        onView(withId(R.id.tv_music_list)).check(matches(withText(R.string.music_list)))
        onView(withId(R.id.btn_play_pause))
            .check(matches(withContentDescription(R.string.play)))
        // 不同模拟器分辨率可能裁切底部按钮边缘，验证控件有效可见即可。
        onView(withId(R.id.btn_prev))
            .check(matches(withEffectiveVisibility(Visibility.VISIBLE)))
        onView(withId(R.id.btn_next))
            .check(matches(withEffectiveVisibility(Visibility.VISIBLE)))

        onView(withId(R.id.btn_filter_favorites))
            .check(matches(withText(R.string.all_music)))
            .perform(clickIgnoringPartialVisibility())
        onView(withId(R.id.btn_filter_favorites))
            .check(matches(withText(R.string.favorites)))
            .perform(clickIgnoringPartialVisibility())
        onView(withId(R.id.btn_filter_favorites))
            .check(matches(withText(R.string.all_music)))
    }

    @Test
    fun playSettingsDialogShowsAllModesAndDismisses() {
        onView(withId(R.id.btn_settings)).perform(clickIgnoringPartialVisibility())

        onView(withId(R.id.rb_order)).inRoot(isDialog()).check(matches(isDisplayed()))
        onView(withId(R.id.rb_loop_all)).inRoot(isDialog()).check(matches(isDisplayed()))
        onView(withId(R.id.rb_loop_one)).inRoot(isDialog()).check(matches(isDisplayed()))
        onView(withId(R.id.rb_shuffle)).inRoot(isDialog()).check(matches(isDisplayed()))
        onView(withId(R.id.switch_favorites_only)).inRoot(isDialog())
            .check(matches(isDisplayed()))

        Espresso.pressBack()
        onView(withId(R.id.btn_settings)).check(matches(isDisplayed()))
    }

    @Test
    fun cockpitDemoDialogShowsFocusAndUsbEntries() {
        onView(withId(R.id.btn_cockpit_demo)).perform(clickIgnoringPartialVisibility())

        onView(withId(R.id.btn_demo_duck)).inRoot(isDialog()).check(matches(isDisplayed()))
        onView(withId(R.id.btn_demo_transient)).inRoot(isDialog())
            .check(matches(isDisplayed()))
        onView(withId(R.id.btn_demo_usb_insert)).inRoot(isDialog()).perform(scrollTo())
            .check(matches(isDisplayed()))
        onView(withId(R.id.btn_demo_usb_remove)).inRoot(isDialog()).perform(scrollTo())
            .check(matches(isDisplayed()))

        Espresso.pressBack()
        onView(withId(R.id.btn_cockpit_demo)).check(matches(isDisplayed()))
    }

    private fun awaitLandscape(scenario: ActivityScenario<MainActivity>) {
        val deadline = SystemClock.uptimeMillis() + ORIENTATION_TIMEOUT_MS
        while (SystemClock.uptimeMillis() < deadline) {
            var landscape = false
            scenario.onActivity {
                landscape = it.resources.configuration.orientation ==
                    Configuration.ORIENTATION_LANDSCAPE
            }
            if (landscape) return
            SystemClock.sleep(100)
        }
        throw AssertionError("MainActivity 未在 5 秒内切换为车机横屏布局")
    }

    /**
     * 车机 16:9 屏幕上收藏按钮带左侧图标，部分设备会因背景/内边距计算导致
     * Espresso 判定可见面积不足 90%，但该控件实际可点击。此处直接触发点击回调。
     */
    private fun clickIgnoringPartialVisibility(): ViewAction {
        return object : ViewAction {
            override fun getConstraints(): Matcher<View> =
                withEffectiveVisibility(Visibility.VISIBLE)

            override fun getDescription(): String = "click view via performClick"

            override fun perform(uiController: UiController, view: View) {
                view.performClick()
                uiController.loopMainThreadUntilIdle()
            }
        }
    }
}
