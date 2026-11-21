# 电脑工具

普通用户打开安装器后，向导会调用 host-tools.sh 准备主机环境。Mac 使用 Homebrew 的 Lima 和 libusb、Apple 的编译器；Ubuntu 使用系统软件包。两者都从 Rockchip 仓库下载固定版本的 rkdeveloptool，校验后在本机编译。Mac 的 USB 操作在宿主机执行，Linux 虚拟机仅负责镜像制作，使用 CherryPen 共享目录。

[第一次刷机](start.md) · [Rockchip 工具源码](https://github.com/rockchip-linux/rkdeveloptool/tree/304f073752fd25c854e1bcf05d8e7f925b1f4e14)
