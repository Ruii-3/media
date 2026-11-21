# 测试音频

本目录用于 CP1 演示与本地扫描/播放测试，MP3 不打包进 APK，也已被 `.gitignore` 排除，不会上传到 Git 仓库。

当前本地测试文件（3 首）：

- `周传雄 - 男人·海洋.mp3`：04:02，验证 MediaStore 扫描、标题/歌手/专辑展示、时长过滤与单曲播放。
- `雨一直下_华语群星.mp3`：04:54，元数据为“雨一直下 / 张宇 / 大人的情歌”。
- `贺一航 - 一个人的寂寞两个人的错 [mqms2].mp3`：03:32，验证多曲切换与队列顺序。

推送到车机 `/sdcard/Music/`：

```powershell
adb -s 设备序列号 shell mkdir -p /sdcard/Music
adb -s 设备序列号 push "testdata\周传雄 - 男人·海洋.mp3" /sdcard/Music/
adb -s 设备序列号 push "testdata\雨一直下_华语群星.mp3" /sdcard/Music/
adb -s 设备序列号 push "testdata\贺一航 - 一个人的寂寞两个人的错 [mqms2].mp3" /sdcard/Music/
adb -s 设备序列号 shell cmd media scan /sdcard/Music
```

真机/台架演示时，把三首 MP3 复制到 FAT32 U 盘根目录或 `Music/` 子目录，
先插 U 盘再启动应用，验证 USB 挂载后的重新扫描。

## 多设备注意

同时连接多个模拟器/真机时，`adb` 必须用 `-s` 指定目标设备，否则命令会落到默认设备：

```powershell
adb devices
adb -s 设备序列号 push "testdata\周传雄 - 男人·海洋.mp3" /sdcard/Music/
```

车机 AVD 可能同时存在 Driver 用户 0 和 10。`adb push` 默认写入用户 0 的 `/sdcard`，
如果应用运行在用户 10，会看不到该文件；演示时保持应用与文件在同一用户即可。