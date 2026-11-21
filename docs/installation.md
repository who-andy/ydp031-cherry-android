# Cherry Android 词典笔安装器

适配有道 YDP031CHNSTD、16 GB eMMC、原厂 B 槽 OS 3.4.6。适用范围由两份 GPT、四份设备标识、原厂启动镜像和实际资源的校验结果共同确定。其他版本会在准备阶段退出。

这是首次安装候选版。已经装过项目双系统的词典笔请保留原有安装，使用专门的升级包。安装器源码包需要单独提供与清单匹配的平台组件。本地离线版的 platform 目录已备齐这些输入。两种包使用相同的安装程序。

## 安装前

电脑需要 Python 3.9 或以上。生成安装文件需要 Linux、管理员权限、util-linux、e2fsprogs、GNU coreutils 和 lz4。Apple Silicon Mac 可以在 ARM64 Linux 虚拟机内执行准备，USB 备份、校验、写入和恢复可以在 Mac 完成。将需跨系统使用的目录放在双方都能访问的磁盘，建议预留 40 GiB。主机侧需要可正常使用的 Rockchip rkdeveloptool，设备由所有者通过已有的维护方式进入 Loader。

安装器保留原厂 system A、system B、boot B 和原分区表。安卓使用 userdisk 中独立的 2.5 GiB 数据文件；文件实际占用随使用增长。安装会替换 boot A，在原厂桌面注册「返回安卓」，最后设置 A 槽试启动两次。使用双系统期间，原厂 OTA 可能占用同一个 A 槽；恢复原厂备份后再更新原厂固件。

## 备份和生成

下列命令在解压后的目录运行。将 YOUR_SERIAL 换成笔的设备序列号，将各路径换成自己的实际路径。备份和生成的 update 目录含本机数据，应由设备所有者单独保管。

```sh
python3 install.py verify-platform --platform ./platform
python3 install.py backup --loader rkdeveloptool --serial YOUR_SERIAL --output /private/path/stock.img
python3 install.py inspect --backup /private/path/stock.img
```

备份覆盖 15,518,924,800 字节的 eMMC 用户区，包含安装涉及的全部区域。安装器会第二次读取设备，与本地备份逐一校验摘要。将输出的 SHA-256 和备份一起保存。工具对两个 eMMC 硬件启动区均无写入操作。

在 Linux 上从这支笔的原厂备份生成安装文件。源文件系统以只读方式挂载。程序从中重建原厂扫描、翻译、界面和发音所需的资源，并在新的文件系统副本中登记应用。备份中的词库、发音库与原厂桌面应用随用户分区保留。输出目录必须是一个新路径。

```sh
sudo python3 install.py prepare --backup /private/path/stock.img --platform ./platform --output /private/path/update
```

## 写入

目标笔在 Loader 下保持连接。校验阶段会检查实际设备身份、原厂系统、每个预期改动块和启动回退记录。安装时先写用户分区，再写 boot A，最后写启动选择。每个写入块均回读核对。中断后可用同一份备份和 update 重新运行安装命令。

```sh
python3 install.py check --loader rkdeveloptool --backup /private/path/stock.img --package /private/path/update --confirm-serial YOUR_SERIAL
python3 install.py install --loader rkdeveloptool --backup /private/path/stock.img --package /private/path/update --confirm-serial YOUR_SERIAL
rkdeveloptool rd
```

首次进入安卓会安装 Magisk 管理器、原版词典、系统切换、设备模式、Via、KOReader、Geekbench 和 glmark2。设备模式控制学习和游戏后台；系统切换用于回原厂。调试电脑需要在安卓界面获得 USB 授权。

## 恢复

恢复将 userdisk、boot A 和 A/B 启动选择还原到制作备份时的状态。备份之后新产生的安卓数据、原厂笔记和其他用户分区改动都会被覆盖。先保存需要留下的内容。独立恢复仅需要原始备份及其在备份时记录的 SHA-256。

```sh
python3 install.py restore --loader rkdeveloptool --backup /private/path/stock.img --backup-sha256 YOUR_BACKUP_SHA256 --confirm-serial YOUR_SERIAL
rkdeveloptool rd
```

要在普通文件上演练，把 check、install、restore 命令中的 `--loader rkdeveloptool` 换成 `--disk-copy /private/path/disposable-copy.img`。该文件须是整盘备份的独立副本。恢复后可对副本和备份执行 `cmp`。

## 平台材料

platform-files.json 列出平台文件的精确路径和 SHA-256，apps.json 列出应用及拆分 APK。开发者准备的本地 platform 包含板级适配程序、设备无关的启动模板、系统和驱动镜像、空数据模板及对应应用。安装器会检查每个文件，按备份中的序列号生成内核和切换组件。原厂导入清单 factory-files.json 只有资源路径、版本转换信息和摘要。源码 ZIP 单独使用时，需另外准备这一版 platform；目前没有公开下载地址。

## 验证范围

已有适配版在开发用词典笔上完成过安卓启动、扫描翻译、发音与双向切换验收。本版首次安装流程另行在由真实原厂文件重建的磁盘副本上验证。未经项目改动的第二支实机，其首次冷启动及原厂桌面返回仍待验证。
