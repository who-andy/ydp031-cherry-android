# Cherry Android · 有道词典笔 3 双系统

**保留原厂词典系统，在 Android 8.1 里也能按原来的方式扫词。** 在学习模式下，打开着其他应用也可以直接落笔。沿纸面扫一个单词，抬笔就能看原厂横屏释义、听发音；扫完整句子可以看中英译文、听朗读。第一行抬笔后约一秒内接扫下一行，两行会合起来翻译。要用原厂系统时，在安卓桌面点「系统切换」；原厂桌面也有「返回安卓」。

![实机上的原厂横屏词典，显示英文原句和中文译文](docs/images/translation.png)

## 先确认你的设备

首次安装面向 **YDP031CHNSTD、16 GB、原厂 B 槽 OS 3.4.6**。请先核对机身或包装上的完整型号和设置里的系统版本。安装前准备一根 USB 数据线，并在存放备份和制作文件的电脑磁盘上预留 **50 GiB**。安装器会先保存整笔备份，再生成这支笔的安装文件。

开发笔已实测扫词、连续两行合并翻译、发音和双向系统切换。Windows 直连实机的完整刷入与恢复，以及第二支全新原厂笔的首次冷启动仍待验证。[实机截图、性能数据与验证范围](docs/results.md)

## 下载和安装

两个安装包都在同一个 [GitHub Release 下载页](https://github.com/who-andy/ydp031-cherry-android/releases/latest)。展开 **Assets**，只下载自己电脑对应的 ZIP，完整解压后按教程操作。

| 你的电脑 | 下载哪个文件 | 解压后如何开始 |
| --- | --- | --- |
| Windows 11 x64 | `Cherry-YDP031-Windows.zip` | 双击 `Start-Windows.exe`。首次使用先准备 WSL 2 和 USB 工具。[Windows 教程](docs/windows.md) |
| Apple Silicon Mac | `Cherry-YDP031-Installer.zip` | 双击 `Start.command`。[Mac / Ubuntu 教程](docs/start.md) |
| Ubuntu | `Cherry-YDP031-Installer.zip` | 在解压目录打开终端，运行 `bash Start.command`。[Mac / Ubuntu 教程](docs/start.md) |

## 装好后怎么用

第一次进入安卓，打开「设备模式」，选「学习模式」，回桌面扫一个词，再点喇叭听发音。「游戏模式」会暂停词典后台，把内存留给前台应用。[扫词、相机和系统切换](docs/usage.md)

如果连接中断、校验失败，或需要恢复到安装前，请保留备份文件和对应的校验记录。[故障排查与原厂恢复](docs/recovery.md)

## 原理与研发

| 想了解什么 | 阅读内容 |
| --- | --- |
| [安装器在背后做什么](docs/installation.md) | ADB 由谁授权，何时需要管理员权限，为什么要为每支笔生成安装文件，以及何时才真正写入。 |
| [这套双系统是怎样做出来的](docs/development.md) | 从实机色条、触摸轨迹，到原厂整行 OCR、跨行翻译和双向切换，每一步怎样判断是否做对了。 |
| [双系统怎样启动和存储](docs/how-it-works.md) | 原厂和安卓分别从哪里启动，词库、安卓数据和启动选择保存在什么位置。 |
| [自行构建](docs/build.md) | 从源码打包安装器，编译内核、Windows 启动器和三个自制安卓应用。 |

Cherry Android 是社区适配项目。
