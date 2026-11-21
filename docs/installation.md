# 有道词典笔 3（YDP031）一步步安装 Android

**开始前核对三样东西**：你的笔是实测完整型号 **YDP031CHNSTD（16 GB）**，原厂 B 槽为 **OS 3.4.6**；电脑已经通过[五项自测](start.md)；你手上已经有完整的 `platform` 文件夹。只有公开下载包时，可以先看[装完是什么样子](results.md)。

这份教程用于首次安装。已经装有本项目双系统的笔，请保留现有安装。RC1 已在磁盘副本上验证首次安装和恢复，另一支未经改装的笔尚未完成首次冷启动验收。

## 第 1 步　把文件放对地方

在个人文件夹下打开 `CherryPen → installer`。把配套的整个 `platform` 文件夹放进去，和 `install.py` 并排。打开终端，复制下面两行。

```sh
cd "$HOME/CherryPen/installer"
python3 install.py verify-platform --platform ./platform
```

**看到「平台组件校验通过，文件数 68」再继续。** 找不到文件时，看看是否多套了一层 `platform/platform`。如果手头只有 `platform-files.json`，还缺实际组件。

接着按[准备电脑工具](tools.md)里自己电脑对应的那一节操作。后面每一段都写明了在哪台电脑上运行。

## 第 2 步　让电脑认出自己的笔

在实际连接 USB 的电脑上操作。用能传数据的线连接词典笔，保持在原厂界面。这里沿用设备所有者已经可以使用的 USB 调试入口。

```sh
cd "$HOME/CherryPen/installer"
export PATH="$HOME/CherryPen/tools:$PATH"
adb devices
```

表头下应当只有一行，左边是这支笔自己的序列号，右边是 `device`。若显示 `unauthorized`，先解锁笔，在屏幕上确认是你自己的电脑并允许 USB 调试，再运行一次。若只有表头，先检查数据线和 USB 连接。**看见一支状态为 `device` 的笔后**，再复制下面几行。

```sh
mkdir -p "$HOME/CherryPen/private"
adb get-serialno > "$HOME/CherryPen/private/serial.txt"
cat "$HOME/CherryPen/private/serial.txt"
```

屏幕上显示的号码应和刚才列表左边一致。后续命令会从这个文件读取号码，无需手动替换示例字符。接下来让笔重启到维护模式（Loader）。

```sh
adb -s "$(cat ../private/serial.txt)" reboot bootloader
```

等几秒再执行：

```sh
rkdeveloptool ld
```

看到一支设备，行中含 `Vid=0x2207,Pid=0x330d`，末尾是 **`Loader`**，就可以备份。此时笔的屏幕可能没有安卓桌面，以电脑上这行结果为准。

## 第 3 步　保存自己的原厂备份

仍在连接 USB 的电脑上操作。备份文件约 15.5 GB；电脑会读取两遍进行比对，期间保持连接。下面把结果同时保存到 `backup.log`，以后恢复时会用到。

```sh
if [ -e ../private/stock.img ]; then
  echo "这个文件夹已经有备份，请先核对原来的 backup.log。"
else
  python3 install.py backup --loader rkdeveloptool --serial "$(cat ../private/serial.txt)" --output ../private/stock.img | tee -a ../private/backup.log
fi
```

**等到出现「备份已回读，SHA-256」以及一长串字符。** 如果命令报错或没有这句话，先解决备份问题。再检查这份备份属于哪支笔：

```sh
python3 install.py inspect --backup ../private/stock.img
```

输出中的 `serial` 应与第 2 步一致。备份、日志和序列号在 `CherryPen/private` 里，请保管好；如果目录中已经有上次成功的备份，保留它和配套的日志，直接核对原来的日志。

## 第 4 步　用备份制作这支笔的安装文件

**Mac 用户**在 Mac 的终端复制这一段。命令会把制作工作交给第 1 步准备好的 Linux 虚拟机，USB 仍接在 Mac 上。

```sh
cd "$HOME/CherryPen/installer"
limactl shell --workdir "$PWD" cherry-pen sudo python3 install.py prepare --backup ../private/stock.img --platform ./platform --output ../private/update
```

**Ubuntu 直连用户**只运行下面这一段。

```sh
cd "$HOME/CherryPen/installer"
sudo python3 install.py prepare --backup ../private/stock.img --platform ./platform --output ../private/update
```

看到 **「本机安装文件已生成，差异块数」** 即完成，数字可以不同。此时只改动了电脑上的副本。若提示输出目录已存在，先确认是否已经成功生成；不同备份各自保管对应的 update 目录。

## 第 5 步　先核对，再写入

回到连着 USB 的电脑，词典笔应仍在 Loader 模式。第一段只检查设备、备份和安装文件能否对应。

```sh
cd "$HOME/CherryPen/installer"
export PATH="$HOME/CherryPen/tools:$PATH"
python3 install.py check --loader rkdeveloptool --backup ../private/stock.img --package ../private/update --confirm-serial "$(cat ../private/serial.txt)"
```

看到 **「设备、原厂回退、备份、写入范围和差异块已核验」** 再运行下一行。**从这里开始实际写入词典笔**，程序会修改用户分区、安卓启动位置和启动选择，保留两个原厂 system 分区和原厂 boot B。

```sh
python3 install.py install --loader rkdeveloptool --backup ../private/stock.img --package ../private/update --confirm-serial "$(cat ../private/serial.txt)"
```

等到 **「安装写入与回读完成」** 再重启：

```sh
rkdeveloptool rd
```

## 第 6 步　开机后怎么验收

1. 等安卓桌面出现，打开「设备模式」，点「学习模式」，等显示已就绪。
2. 回桌面，用笔尖在纸上扫一行字。能自动弹出词典、看到释义，再点喇叭听到发音，就验证了最常用的功能。
3. 想回到原厂时，在安卓打开「系统切换」并选「进入原厂系统」。想再回安卓，在原厂桌面找绿色「返回安卓」。[完整使用手册](usage.md)

安装后若要更新原厂固件，先恢复原厂备份。原厂更新可能使用安卓当前占用的启动位置。

## 恢复

**恢复会把用户分区退回到备份当天。** 安装后新增的笔记、文件和安卓数据，先另存一份。让同一支笔进入 Loader，回到连着 USB 的电脑。若人在安卓，可先用「系统切换」进入原厂，再按第 2 步进入 Loader。

```sh
cd "$HOME/CherryPen/installer"
export PATH="$HOME/CherryPen/tools:$PATH"
cat ../private/backup.log
```

确认日志里有备份成功的那一行，再执行下面的命令。它会自动读取当时记录的校验值和序列号；完整恢复只需原始备份，无需重新生成 update。

```sh
python3 install.py restore --loader rkdeveloptool --backup ../private/stock.img --backup-sha256 "$(awk '/备份已回读/{hash=$NF} END{print hash}' ../private/backup.log)" --confirm-serial "$(cat ../private/serial.txt)"
```

看到 **「已恢复备份中的用户分区、启动 A 与引导记录」** 再执行 `rkdeveloptool rd`，观察能否回到原厂界面。如果 Linux 提示没有 USB 访问权限，请检查当前用户对该 Loader 的访问权限；在自己管理的 Ubuntu 电脑上，可用 `sudo` 和本地工具的完整路径运行需访问 USB 的命令。[中断、校验失败和文件演练](recovery.md)
