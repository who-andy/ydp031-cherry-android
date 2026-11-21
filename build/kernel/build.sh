#!/bin/sh
set -eu
here=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
dest=${1:?请提供一个新的内核工作目录}
mkdir "$dest"
cd "$dest"
rev=9ead5f3cbd6e0abd0ac70002205993c953c227ea
curl -fL "https://codeload.github.com/rockchip-linux/kernel/tar.gz/$rev" -o upstream.tar.gz
tar xf upstream.tar.gz
cd "kernel-$rev"
cp "$here/isp1-dev.c" drivers/media/platform/rockchip/isp1/dev.c
cp "$here/cherry.config" .config
export ARCH=arm64 KBUILD_BUILD_USER=who-andy KBUILD_BUILD_HOST=cherry
make CC=gcc olddefconfig
make CC=gcc -j"$(getconf _NPROCESSORS_ONLN)" Image
echo "生成的内核位于 $PWD/arch/arm64/boot/Image"
