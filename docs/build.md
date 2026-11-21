# 自己构建

普通用户只需[下载安装器](start.md)。本页面向要修改程序、重新打包或编译内核的人。

## 从源码打包安装器

需要 Python 3.9+。检出下列版本，并将[同版公开 ZIP](https://github.com/who-andy/ydp031-cherry-android/releases/download/v0.2.0-rc.1/Cherry-YDP031-Installer.zip)解压到下载目录。ZIP 中包含经过适配的板级组件和镜像差异，源码中的清单记录每项摘要。

```sh
git clone --branch v0.2.0-rc.1 --depth 1 https://github.com/who-andy/ydp031-cherry-android.git
cd ydp031-cherry-android
python3 -m unittest discover -s tests -v
python3 build/package.py --platform "$HOME/Downloads/Cherry-YDP031/platform" --output "$HOME/Downloads/Cherry-local.zip"
```

解压自己生成的 ZIP，在里面运行 `bash Start.command --self-test`。打包器只纳入清单里的发行文件。Android 基础镜像、Mali 和 Via 在用户运行时从上游取得；两套系统镜像在 Linux 制作阶段由公开差异和已校验输入还原。要研究差异生成方式，可看 [image_recipe.py](../build/image_recipe.py) 与 [image_parts.py](../image_parts.py)。

## 编译 ARM64 内核

使用 **ARM64 Ubuntu**，安装构建工具后，从仓库根目录运行：

```sh
sudo apt-get install build-essential bc bison flex libssl-dev libelf-dev curl
sh build/kernel/build.sh "$HOME/cherry-kernel-build"
```

脚本从 Rockchip 取固定提交，套用扫描头 ISP 驱动修改和本机型配置，生成 `arch/arm64/boot/Image`，构建标识使用 `who-andy@cherry`。传入一个新的、空间充足的目录。源配置使用通用设备占位符；自行构建的二进制可能因编译器与时间而与发行文件不同。内核单独编译成功后，还需要匹配的设备树、启动文件与板级驱动才能开机。

## 编译词典、系统切换和设备模式应用

准备 JDK 21 和 Android SDK，安装 Build Tools 36.0.0 以及含 android.jar 的平台包。设置 `ANDROID_HOME`，以 `ANDROID_PLATFORM` 指定实际安装的目录名（默认 android-37.0）。使用自己的签名密钥；首次可运行：

```sh
keytool -genkeypair -keystore "$HOME/cherry-local.p12" -storetype PKCS12 -alias cherry -keyalg RSA -validity 3650
export CHERRY_KEYSTORE="$HOME/cherry-local.p12"
sh build/apps/build.sh dictionary "$PWD/dictionary-local.apk"
sh build/apps/build.sh switch "$PWD/switch-local.apk"
sh build/apps/build.sh modes "$PWD/modes-local.apk"
```

这些命令生成自行签名的 APK。发行版会校验应用摘要，换入自签名 APK 属于另一个镜像版本。构建词典应用仅生成安卓宿主；原厂拼接、OCR、翻译资源和词典页面仍由安装器从设备备份导入。

## 组件来源

- Android 使用 API 27 的 ARM64 基础文件。[Android SDK 条款](https://developer.android.com/studio/terms)
- 图形库取自 Rockchip Android 板级仓库的固定版本。[上游文件](https://gitlab.com/rockchip_android_r/rk/platform/vendor/rockchip/common/-/tree/126be84e8d9db7c0b9cebed52dbfe376111da205/gpu/MaliTDVx)
- 应用包括 [Magisk](https://github.com/topjohnwu/Magisk)、[KOReader](https://github.com/koreader/koreader)、[glmark2](https://github.com/glmark2/glmark2) 和来自[开发者网站](https://www.viayoo.com/)的 Via。
- Rockchip 内核与源码修改见 [build/kernel](../build/kernel)，对应版本由 build.sh 固定。第三方驱动、原厂资源和已编译板级组件各有来源。本教程覆盖上述三个可独立构建的部分；整套系统的一键全源码重编仍未提供。

glmark2 采用 2023.01 版，项目使用的 Android SurfaceView 源文件位于 [build/glmark2](../build/glmark2)。Magisk 管理器为 v31.1，KOReader 为 v2026.07.1；可按这些版本号查找上游提供的对应源码及许可证。

## 编译 Windows 启动器

在 Windows x64 的 PowerShell 中，从仓库根目录运行 `./build/windows/build.ps1 -OutputDir ./out`。脚本使用系统的 .NET Framework 编译器。使用 Python 3.9+ 执行下面的命令，将生成的 EXE 与同版通用平台文件打包。

```powershell
python build/package.py --platform C:\Cherry-YDP031\platform --windows-launcher .\out\Start-Windows.exe --output .\Cherry-Windows-local.zip
```
