# 电脑端工具对照

遇到提示或报错时，可以用这张表判断该去哪个系统检查。

| 工具 | 在哪里运行 | 做什么 |
| --- | --- | --- |
| ADB | Mac、Ubuntu 或 Windows 宿主机 | 与原厂系统已授权的 USB 调试连接通信，请求进入维护模式。 |
| rkdeveloptool | Mac、Ubuntu 或 Windows 的 WSL 2 内 | 识别 Rockchip Loader 设备，读出备份，写入后回读。 |
| Lima | Apple Silicon Mac | 提供 Ubuntu 环境处理 ext4 镜像。USB 操作在 Mac 上执行。 |
| WSL 2 与 usbipd-win | Windows 11 x64 | usbipd-win 将 Loader 设备共享给 Ubuntu-24.04，Ubuntu 负责备份、制作镜像和写入。 |

Mac 和 Ubuntu 上，安装器会下载并校验固定版本的 Rockchip 工具源码，再在本机编译。需要安装哪些依赖、在哪个窗口操作，分别见 [Mac / Ubuntu 教程](start.md)和 [Windows 教程](windows.md)。[工具准备脚本](../host-tools.sh)
