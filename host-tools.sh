#!/bin/sh
# Prepare the USB host and the Linux image builder. Runs before entering Loader.
set -eu
root=${1:?需要工作目录}
tools="$root/tools"
mkdir -p "$tools"
case "$(uname -s)" in
Darwin)
    command -v brew >/dev/null || { echo '请先安装 Homebrew（https://brew.sh），按官网提示完成 shellenv，然后重新打开安装器。'; exit 1; }
    xcode-select -p >/dev/null || { echo '请先运行 xcode-select --install，等安装完成再打开安装器。'; exit 1; }
    command -v adb >/dev/null || brew install --cask android-platform-tools
    brew install lima libusb
    ;;
Linux)
    command -v apt-get >/dev/null || { echo '此版直接支持 Ubuntu。'; exit 1; }
    sudo apt-get update
    sudo apt-get install -y python3 adb curl util-linux coreutils e2fsprogs lz4 build-essential dh-autoreconf libudev-dev libusb-1.0-0-dev pkg-config
    ;;
*) exit 1 ;;
esac
if [ ! -x "$tools/rkdeveloptool-304f073" ]; then
    tmp=$(mktemp -d "$tools/build.XXXXXXXX")
    trap 'rm -rf "$tmp"' EXIT
    rev=304f073752fd25c854e1bcf05d8e7f925b1f4e14
    curl -fLsS --retry 3 "https://codeload.github.com/rockchip-linux/rkdeveloptool/tar.gz/$rev" -o "$tmp/src.tar.gz"
    python3 -c 'import hashlib,sys; assert hashlib.sha256(open(sys.argv[1],"rb").read()).hexdigest()=="389ba41af6986c16f1eeebdc1febcb0bf4b8acb7abd694d3d652e78504215843"' "$tmp/src.tar.gz"
    tar xf "$tmp/src.tar.gz" -C "$tmp"
    cd "$tmp/rkdeveloptool-$rev"
    if [ "$(uname -s)" = Darwin ]; then
        sed 's/@RKDevelopTool_Mac_PACKAGE_VERSION@/1.3/' config.h.in > config.h
        clang++ -std=c++11 -I"$(brew --prefix libusb)/include/libusb-1.0" -L"$(brew --prefix libusb)/lib" ./*.cpp -lusb-1.0 -liconv -o rkdeveloptool
    else
        bash autogen.sh
        ./configure CXXFLAGS="-O2 -Wno-error"
        make
    fi
    ./rkdeveloptool -v
    cp rkdeveloptool "$tmp/tool"
    mv "$tmp/tool" "$tools/rkdeveloptool-304f073"
fi
if [ "$(uname -s)" = Darwin ]; then
    vm=cherry-pen-rc2
    if limactl list --format '{{.Name}}' | grep -qx "$vm"; then
        limactl start --tty=false "$vm"
    else
        limactl start --name="$vm" --mount-only="$root:w" --tty=false template:ubuntu
    fi
    limactl shell --workdir "$root" "$vm" sudo apt-get update
    limactl shell --workdir "$root" "$vm" sudo apt-get install -y python3 util-linux coreutils e2fsprogs lz4
fi
