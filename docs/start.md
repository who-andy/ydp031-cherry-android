# 下载与准备

1. 在 [RC1 下载页](https://github.com/who-andy/ydp031-cherry-android/releases/tag/v0.1.0-rc.1) 下载 `Cherry-Installer-rc1-source.zip` 和 `SHA256SUMS`，放在同一个目录。该附件包含安装器、教程、实机图片和测试。
2. 在该目录执行以下命令。第一条应当显示 ZIP 校验成功。在 Linux 上也可使用 `sha256sum -c SHA256SUMS`。
3. 解压后的目录也是后续所有命令的工作目录。自测使用普通文件，不需要连接词典笔。

```sh
shasum -a 256 -c SHA256SUMS
unzip Cherry-Installer-rc1-source.zip -d cherry-installer
cd cherry-installer
python3 --version
python3 -m unittest discover -s tests -v
python3 install.py --help
```

## 先确认材料

- **目标笔**须为 YDP031CHNSTD、16 GB，原厂 B 槽版本 3.4.6，保留原厂 boot A。安装器读取分区表、启动镜像和设备身份。型号名称相近的其他词典笔未经验证。
- **本地平台目录**含设备无关的内核模板、安卓系统和驱动、板级程序及 APK。[平台清单](../platform-files.json)给出 68 项的路径和 SHA-256，[应用清单](../apps.json)给出应用和拆分 APK 的摘要。本 release 尚无整套平台目录的公开下载来源。没有配套目录时，可完成上面的自测，安装流程停在材料验证这一步。
- **目标笔自己的原厂备份**用于导入匹配的词典界面、OCR、翻译和朗读资源，以及恢复数据。由同型号的另一支笔生成的 update 不可互换。请将备份、校验值和生成文件放在自己的电脑或外置硬盘。
- **电脑**需要 Python 3.9 或更高版本，并预留约 40 GiB 空间。macOS 可负责 USB 备份、校验、写入和恢复。生成镜像需要 Linux 管理员权限和 loop 挂载能力，Apple Silicon Mac 可使用 ARM64 Linux 虚拟机，并共享备份、安装器和输出目录。Linux 工具包括 util-linux、GNU coreutils、e2fsprogs 和 lz4。

## 确认维护连接

使用设备所有者已有的维护方式进入 Rockchip Loader。如果原厂系统已经授权了本机 ADB，开发机上验证过以下入口。在 Mac 或实际连接 USB 的主机上运行。将 YOUR_SERIAL 换成设备自己的序列号。

```sh
adb devices
adb -s YOUR_SERIAL reboot bootloader
rkdeveloptool ld
```

安装器期望列表里只有一支目标笔，状态为 Loader，USB 标识为 2207:330d。ADB 授权依赖设备屏幕确认。主机工具取自 [Rockchip 上游 rkdeveloptool](https://github.com/rockchip-linux/rkdeveloptool/tree/304f073752fd25c854e1bcf05d8e7f925b1f4e14)。

接着进入[安装与恢复](installation.md)。先执行材料校验和备份，再生成这支笔的安装文件。
