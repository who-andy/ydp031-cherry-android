# 已有完整平台文件后，怎样准备电脑

只有要实际安装时才需要本页。先完成[公开版自测](start.md)，在电脑上为备份和安装文件预留约 **40 GiB** 空间。

## Apple Silicon Mac

下面的命令都在 **Mac 的终端**运行。ADB 用来和已授权的原厂系统通信；rkdeveloptool 用来在笔进入维护模式后读写；Lima 在 Mac 里提供一套 Linux，只负责制作安装文件。

1. 输入 `brew --version`。看到 Homebrew 版本号就继续。若提示找不到命令，从官网安装 Homebrew，完成页面提示的 PATH 设置，再重新打开终端。[Homebrew 官网](https://brew.sh/)
2. 安装电脑工具。已有 ADB 可先用 `adb version` 检查，能看到版本号就跳过第一行。

```sh
brew install --cask android-platform-tools
brew install lima libusb
```

3. 输入 `rkdeveloptool -v`。看到版本号就跳过下面这一段。若找不到命令，先输入 `xcode-select -p`；若报错，运行 `xcode-select --install`，等系统安装完成。接着复制下面命令，在本机编译项目验证过的 Rockchip 版本。看到 `rkdeveloptool ver 1.3` 即完成。[Rockchip 对应版本](https://github.com/rockchip-linux/rkdeveloptool/tree/304f073752fd25c854e1bcf05d8e7f925b1f4e14)

```sh
mkdir -p "$HOME/CherryPen/tools"
cd "$HOME/CherryPen/tools"
rev=304f073752fd25c854e1bcf05d8e7f925b1f4e14
curl -fL "https://codeload.github.com/rockchip-linux/rkdeveloptool/tar.gz/$rev" -o rockchip.tar.gz
tar -xzf rockchip.tar.gz
cd "rkdeveloptool-$rev"
sed 's/@RKDevelopTool_Mac_PACKAGE_VERSION@/1.3/' config.h.in > config.h
clang++ -std=c++11 -I"$(brew --prefix libusb)/include/libusb-1.0" -L"$(brew --prefix libusb)/lib" *.cpp -lusb-1.0 -liconv -o ../rkdeveloptool
"$HOME/CherryPen/tools/rkdeveloptool" -v
```

4. 新建名为 `cherry-pen` 的 Ubuntu 虚拟机。它只共享这份教程使用的 `CherryPen` 文件夹。首次启动会下载 Ubuntu，需要等待。以后已有这台虚拟机时，可用 `limactl start cherry-pen` 启动。[Lima 使用说明](https://lima-vm.io/docs/usage/)

```sh
cd "$HOME/CherryPen/installer"
limactl start --name=cherry-pen --mount-only="$HOME/CherryPen:w" --tty=false template:ubuntu
limactl shell --workdir "$PWD" cherry-pen sudo apt-get update
limactl shell --workdir "$PWD" cherry-pen sudo apt-get install -y python3 util-linux coreutils e2fsprogs lz4
limactl shell --workdir "$PWD" cherry-pen ls install.py
```

最后一行显示 `install.py`，说明两个系统能看到同一份安装器。USB 线始终接在 Mac，由 Mac 负责备份和写入。

## Ubuntu 电脑直连词典笔

直接使用 Ubuntu 的读者无需 Lima。下面安装 ADB、镜像制作工具和编译 Rockchip 工具所需的软件。

```sh
sudo apt-get update
sudo apt-get install -y python3 adb curl unzip util-linux coreutils e2fsprogs lz4 build-essential dh-autoreconf libudev-dev libusb-1.0-0-dev pkg-config
```

若 `rkdeveloptool -v` 已经显示项目使用的上游 1.3 版，可直接继续。否则在终端执行以下命令。最后一行应显示 `rkdeveloptool ver 1.3`。[Rockchip 编译说明](https://github.com/rockchip-linux/rkdeveloptool/blob/304f073752fd25c854e1bcf05d8e7f925b1f4e14/Readme.txt)

```sh
mkdir -p "$HOME/CherryPen/tools"
cd "$HOME/CherryPen/tools"
rev=304f073752fd25c854e1bcf05d8e7f925b1f4e14
curl -fL "https://codeload.github.com/rockchip-linux/rkdeveloptool/tar.gz/$rev" -o rockchip.tar.gz
tar -xzf rockchip.tar.gz
cd "rkdeveloptool-$rev"
bash autogen.sh && ./configure CXXFLAGS="-O2 -Wno-error" && make && cp rkdeveloptool ../rkdeveloptool
"$HOME/CherryPen/tools/rkdeveloptool" -v
```

[回到安装步骤](installation.md)
