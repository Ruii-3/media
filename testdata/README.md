# 测试音频

本目录用于存放第 1 周 CP1 演示与本地扫描/播放测试的音频文件，不会被打包进 APK。

当前测试文件：

- `周传雄 - 男人·海洋.mp3`：4 分 02 秒，用于验证 MediaStore 扫描、标题/歌手/专辑展示、时长过滤与单曲播放。

推送到模拟器：

```powershell
adb push "testdata\周传雄 - 男人·海洋.mp3" "/sdcard/Music/周传雄 - 男人·海洋.mp3"
adb shell am broadcast -a android.intent.action.MEDIA_SCANNER_SCAN_FILE -d "file:///sdcard/Music/周传雄 - 男人·海洋.mp3"
```

真机/台架演示时，把该文件复制到 FAT32 U 盘即可；本周要求先插入 U 盘再启动应用。

## 多模拟器注意

同时运行多个模拟器时，`adb push` 必须用 `-s` 指定目标设备，否则文件会进入默认设备：

```powershell
adb devices
adb -s emulator-5558 push "testdata\周传雄 - 男人·海洋.mp3" "/sdcard/Music/周传雄 - 男人·海洋.mp3"
adb -s emulator-5558 shell "am broadcast -a android.intent.action.MEDIA_SCANNER_SCAN_FILE -d 'file:///sdcard/Music/周传雄 - 男人·海洋.mp3'"
```

车机 AVD 可能同时存在 Driver 用户 0 和 10。`adb push` 默认写入用户 0 的 `/sdcard`，如果应用运行在用户 10，会看不到该文件；演示时保持应用与文件在同一用户即可。
