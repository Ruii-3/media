# 扩展练习完成说明

本次选择并完成三项扩展练习：

1. 持续集成：GitHub Actions 自动运行单元测试、构建 APK，并在模拟器运行 UI/安全测试。
2. UI 测试：使用 Espresso 验证车机主界面、播放设置和座舱场景演示对话框。
3. 安全测试：为 MediaBrowserService 增加签名级绑定权限和调用方校验，并覆盖可信/不可信路径。

## 一、GitHub Actions 持续集成

工作流文件：`.github/workflows/android-ci.yml`

### 触发条件

- 推送到 `master`
- 任意 Pull Request
- 在 GitHub Actions 页面手动触发 `workflow_dispatch`

### 两个 Job

| Job | 执行内容 | 产物 |
| --- | --- | --- |
| `unit-tests-and-apk` | 安装 JDK 17 与 Android SDK，执行 `:app:testDebugUnitTest`、`:app:assembleDebug` | 单元测试 HTML/XML 报告、debug APK |
| `instrumentation-tests` | 启动 Android 14（API 34）x86_64 模拟器，执行 `:app:connectedDebugAndroidTest` | Espresso/安全测试报告与原始 XML |

测试报告和 APK 通过 `actions/upload-artifact@v4` 保存，即使测试失败也会尽力上传报告。

本地等价命令：

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug --console=plain
```

在已连接设备或模拟器上运行仪器测试：

```powershell
.\gradlew.bat :app:connectedDebugAndroidTest --console=plain
```

## 二、Espresso UI 自动化测试

测试文件：`app/src/androidTest/java/com/example/automediaplayer/MainActivityEspressoTest.kt`

| 用例 | 验证内容 |
| --- | --- |
| UI-01 | 主界面品牌区、正在播放区、歌曲列表、播放/上一首/下一首控件可见；收藏筛选可在“全部/收藏”间切换 |
| UI-02 | 播放设置对话框展示四种播放模式与“只看收藏”开关，并可正常关闭 |
| UI-03 | 座舱场景演示对话框展示焦点与 USB 模拟入口，并可正常关闭 |

测试启动时根据系统版本授予音频读取权限，并把 Activity 稳定切换到横屏，避免手机模拟器默认竖屏造成车机布局误报。

不纳入 Espresso 的场景：

- 真实音频起播、进度递增和通知栏控制需要媒体文件与系统媒体环境。
- USB 插拔、副驾屏和厂商硬按键依赖外设或特定车机。

这些仍由 `docs/CP6_Integration_Test_Report.md` 中的真机用例负责，不能用 UI 测试替代真机结论。

## 三、MediaBrowser 连接安全测试

### 安全设计：双层防护

1. **Binder 绑定层**
   - Manifest 声明签名级权限：
     `com.example.automediaplayer.permission.BIND_MEDIA_BROWSER`
   - `MusicPlayService` 使用 `android:permission` 引用该权限。
   - 只有与本应用使用相同签名的应用才能绑定服务。
2. **业务入口层**
   - `MediaBrowserAccessPolicy` 在 `onGetRoot()` 再校验调用包名、调用 UID 与服务所属包名、UID。
   - 任一不匹配即记录安全日志并返回 `null`，MediaBrowser 连接失败。

这种设计避免只依赖 `exported=false`，也避免仅凭客户端传入的包名字符串做判断。

### 测试用例

| 用例 | 层级 | 验证内容 |
| --- | --- | --- |
| SEC-01 | JVM | 本应用包名 + 本应用 UID 可访问 |
| SEC-02 | JVM | 外部包名即使伪造相同 UID 也被拒绝 |
| SEC-03 | JVM | 相同包名但 UID 不一致时被拒绝 |
| SEC-04 | JVM | 空包名或 `null` 包名被拒绝 |
| SEC-05 | Android | MediaBrowserService 声明 `exported=true`，并绑定签名级权限 |
| SEC-06 | Android | 本应用可信客户端可以连接并取得媒体根节点 |

测试文件：

- `app/src/test/java/com/example/automediaplayer/security/MediaBrowserAccessPolicyTest.kt`
- `app/src/androidTest/java/com/example/automediaplayer/MediaBrowserSecurityTest.kt`

### 安全边界说明

- 签名级权限不是面向普通用户的“是否允许”弹窗，而是用于隔离不同签名的应用。
- 系统媒体控制主要通过 `MediaSession` 消费状态，不需要直接绑定本应用的 MediaBrowserService。
- 若后续需要接入 Android Auto 或第三方车机客户端，应显式增加可信签名/包名白名单，而不是直接去掉校验。

## 四、本地验证与 CI 验证

### 本机实测结果（2026-11-27）

- JVM 测试：**36 例通过，0 失败**。
  - 原有 CP6 测试 32 例
  - 新增 MediaBrowser 安全策略测试 4 例
- Android 仪器测试：**6 例通过，0 失败**。
  - `MainActivityEspressoTest` 3 例
  - `MediaBrowserSecurityTest` 2 例
  - `ExampleInstrumentedTest` 1 例
- 运行环境：`Medium_Phone(AVD)`，Android 17 / API 37，竖屏设备强制切换为 2400×1080 横屏。
- Espresso 已由 3.5.1 升级到 **3.7.0**；3.5.1 在 API 37 上会调用已移除的
  `InputManager.getInstance()`，导致所有 UI 操作失败。

本机执行命令：

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest --console=plain
.\gradlew.bat :app:connectedDebugAndroidTest --console=plain
```

`connectedDebugAndroidTest` 要求设备/模拟器已启动且关闭动画。GitHub Actions 工作流已自动完成这两项。

### 报告位置

- JVM HTML：`app/build/reports/tests/testDebugUnitTest/index.html`
- 仪器测试 HTML：`app/build/reports/androidTests/connected/debug/index.html`
- 仪器测试 XML：`app/build/outputs/androidTest-results/connected/debug/`
- debug APK：`app/build/outputs/apk/debug/app-debug.apk`
- 仪器测试 APK：`app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk`
- JVM 测试与构建日志：`docs/extended_build_run.log`
- 仪器测试日志：`docs/extended_instrumented_test_run.log`

在 GitHub 仓库的 **Actions → Android CI** 页面可以查看每次推送的自动执行结果并下载报告/APK。
