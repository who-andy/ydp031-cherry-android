# Windows 11 刷机

适用于 Windows 11 x64 和有道词典笔 3 **YDP031CHNSTD、16 GB、原厂 B 槽 OS 3.4.6**。Windows 负责连接词典笔，Ubuntu（WSL 2）负责备份、制作和写入。请在安装 Ubuntu 的磁盘上至少留出 50 GiB 空间。Windows 版目前处于测试阶段，Windows 电脑连接实机的完整刷入、开机和恢复尚待验证。

## 第一次准备电脑

1. 在开始菜单搜索「终端」，右键选择「以管理员身份运行」，执行 `wsl --install -d Ubuntu-24.04`。按系统提示重启，再打开 **Ubuntu-24.04**，按提示设置 Linux 用户名和密码。之后安装工具时，Ubuntu 提示输入 sudo 密码就是指这个密码。
2. 在 Windows 终端运行 `wsl --update` 和 `wsl -l -v`。确认 Ubuntu-24.04 右侧的 VERSION 是 2。若它显示 1，运行 `wsl --set-version Ubuntu-24.04 2`，等待转换完成。
3. 在 Windows 终端分别执行下面两行，完成 USB 转接工具和 Android 工具安装。关闭终端，再打开一个新窗口。

```powershell
winget install --interactive --exact --id dorssel.usbipd-win
winget install --exact --id Google.PlatformTools
```

4. 在笔上按[首次连接说明](start.md#第一次连接怎样确认权限)开启自己的 USB 调试授权。用数据线连接，在 **Windows** 终端运行 `adb devices`，目标序列号右侧应显示 `device`。若显示 `unauthorized`，看笔上的授权提示。若列表为空，先排查 USB 数据线和设备管理器中的 ADB 驱动。已由所有者进入 Loader 的笔可以从维护模式直接开始。

[微软 WSL 安装说明](https://learn.microsoft.com/windows/wsl/install) · [微软 USB 转接说明](https://learn.microsoft.com/windows/wsl/connect-usb)

## 下载与安装

下载 [Windows ZIP](https://github.com/who-andy/ydp031-cherry-android/releases/latest/download/Cherry-YDP031-Windows.zip)，在资源管理器选择「全部解压」，双击 **Start-Windows.exe**。选 4 可先检查下载包，不需要接笔或安装 WSL。选 2 可提前下载 Android 基础文件、图形库和浏览器。

选 **1 安装双系统**，只保留目标笔连接。安装器会先准备所需工具，再通过 Windows ADB 请求进入维护模式。USB 设备重新出现后，Windows 可能弹出管理员确认框，用于将这台设备共享给 WSL。随后程序识别词典笔、备份整笔，并生成安装文件。核对屏幕提示的序列号，输入确认后才开始写入。

首次进入安卓后，打开「设备模式」，选择「学习模式」，回到桌面直接扫一个词，再试一次发音和系统切换。[日常使用](usage.md)

## 备份和恢复

备份保存在 Ubuntu 用户主目录下的 `CherryPen/private/设备序列号/`。在 Ubuntu 终端运行 `explorer.exe "$(wslpath -w "$HOME/CherryPen/private")"` 可用资源管理器打开。妥善保存 stock.img 和 stock.img.sha256。卸载或注销 Ubuntu 发行版会删除其中的备份，操作前先复制到其他磁盘。

用同一个 Windows 用户、Ubuntu 发行版和 Linux 账户重新打开启动器，选 **3 恢复**。恢复会覆盖安装以来新增的数据。安装和恢复都会校验目标设备，并在写入后回读。重启后观察笔上实际出现的界面。[恢复中遇到问题](recovery.md)

## USB 连接问题

- 在 Windows 终端运行 `usbipd list`。词典笔进入 Loader 后，VID:PID 应为 `2207:330d`。窗口如报告多个匹配设备，拔掉其他 Rockchip 设备后再试。
- 若端口已连接其他客户端，先结束那次连接。重新打开安装器时会重新识别设备，安装器会保留原备份。
- USB 共享设置可能在重启后保留。如需取消，在管理员终端用 `usbipd list` 核对目标，再运行 `usbipd unbind --busid <目标端口>`。
