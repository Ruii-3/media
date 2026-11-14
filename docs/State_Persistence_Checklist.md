# 状态持久化验证清单（CP4-④）

- 验证日期：2026-11-14
- 应用版本：`AutoMediaPlayer 4.0`（`versionCode=4`）
- 设备：Android Automotive 模拟器 `emulator-5554`，Android 15，1408×792
- 存储文件：`/data/user/10/com.example.automediaplayer/shared_prefs/AutoMediaPlayerPrefs.xml`

## 一、必测项勾选结果

- [x] **播放状态正确保存和恢复**：歌曲 id、列表位置、播放/暂停态均写入并在强杀后恢复。
- [x] **播放进度准确恢复**：保存 `200703ms`（03:20），重启后 `MediaSession.position=200703`。
- [x] **播放模式正确恢复**：保存 `LOOP_ONE`，重启后模式文字与设置单选项均为“单曲循环”。
- [x] **状态过期处理正常**：时间戳改为 25 小时前后启动，不恢复旧状态，持久化文件被清为 `<map />`。
- [x] **空状态处理正常**：删除持久化文件后启动，应用不崩溃、不自动播放，歌单正常显示。

## 二、附加验证

- [x] **force-stop 强杀场景**：未执行 `onDestroy()`，依靠 5 秒保底保存恢复。
- [x] **暂停态跨进程恢复**：恢复后仍为 `STATE_PAUSED`，不会错误自动播放。
- [x] **无效歌曲 id**：单元测试验证 `currentMusicId <= 0` 时状态无效。
- [x] **时间戳缺失**：单元测试验证缺失时间戳按过期处理。
- [x] **构建与单元测试**：`assembleDebug testDebugUnitTest` 通过，4 个用例全部通过。

## 三、实测记录表

| 场景 | 操作 | 保存值 / 预期 | 实测结果 | 证据 |
| --- | --- | --- | --- | --- |
| 播放中强杀 | 播放约 27 秒后 `force-stop`，再启动 | 恢复同一歌曲并继续播放 | `STATE_PLAYING`，歌曲《男人海洋》，进度继续增长 | `docs/cp4_before_kill.png`、`docs/cp4_after_restore.png` |
| 暂停 + 单曲循环 | 暂停并等待 5 秒后 `force-stop`，再启动 | 歌曲/03:20/单曲循环/暂停 | `STATE_PAUSED(2)`，`position=200703`，`LOOP_ONE` | `docs/cp4_restored_paused.png` |
| 24 小时过期 | 将时间戳改为 25 小时前后启动 | 不恢复并清空旧状态 | 持久化文件变为 `<map />`，界面为未知曲目 | `docs/cp4_expired.png` |
| 无历史状态 | 删除 SharedPreferences 后启动 | 正常空启动，不自动播放 | 进程正常，3 首歌正常展示 | `docs/cp4_empty_state.png` |

## 四、强杀恢复原始快照

保存前（也是强杀后重新读取到的值）：

```xml
<?xml version='1.0' encoding='utf-8' standalone='yes' ?>
<map>
    <int name="playback_progress" value="200703" />
    <int name="current_position" value="1" />
    <boolean name="is_playing" value="false" />
    <long name="current_music_id" value="1000000018" />
    <string name="play_mode">LOOP_ONE</string>
    <long name="timestamp" value="1794638812464" />
</map>
```

恢复后 `dumpsys media_session`：

```text
Media button session is com.example.automediaplayer/AutoMediaPlayer/20 (userId=10)
state=PlaybackState {state=PAUSED(2), position=200703, buffered position=0,
    speed=0.0, updated=1281402, actions=1847, custom actions=[], active item id=-1, error=null}
metadata: size=5, description=男人海洋, 周传雄, 男人.海洋 (台湾版)
```

## 五、过期状态原始快照

启动前人为写入 2023 年时间戳：

```xml
<long name="timestamp" value="1700000000000" />
```

启动后系统自动清理为：

```xml
<?xml version='1.0' encoding='utf-8' standalone='yes' ?>
<map />
```

同时界面回到“未知曲目”，没有自动播放，也没有恢复旧的 03:20 进度。

## 六、复现命令

```powershell
# 查看当前状态
adb shell cat /data/user/10/com.example.automediaplayer/shared_prefs/AutoMediaPlayerPrefs.xml

# 强杀并重启
adb shell am force-stop --user 10 com.example.automediaplayer
adb shell am start --user 10 -n com.example.automediaplayer/.ui.MainActivity

# 查看恢复后的会话状态
adb shell dumpsys media_session
```

> 演示提示：强杀前请至少等待 5 秒，让 `AUTO_SAVE_INTERVAL_MS` 的保底保存完成。
> 若刚拖动进度条就立刻强杀，1 秒防抖保存可能尚未落盘。

## 七、结论

五项必测项全部通过。状态持久化满足 CP4 对“强杀恢复”的要求，且过期状态与空状态
均有明确降级路径，不会崩溃，也不会把旧进度错误应用到新歌单。
