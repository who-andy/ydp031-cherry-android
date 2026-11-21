# 磁盘演练和故障排查

## 先在文件上走一遍

先完成[安装教程](installation.md)的第 1 至 4 步，确认 `CherryPen/private` 里已经有 `stock.img`、`backup.log`、`serial.txt` 和 `update`。演练需要额外约 16 GB 可用空间。下面整段在 Mac 或 Ubuntu 的终端中进行，无需连接笔。

先做一个可丢弃的新副本，并检查安装文件：

```sh
cd "$HOME/CherryPen/installer"
cp -n ../private/stock.img ../private/practice.img
python3 install.py check --disk-copy ../private/practice.img --backup ../private/stock.img --package ../private/update --confirm-serial "$(cat ../private/serial.txt)"
```

看到「已核验」再练习写入。这里的目标是 `practice.img`：

```sh
python3 install.py install --disk-copy ../private/practice.img --backup ../private/stock.img --package ../private/update --confirm-serial "$(cat ../private/serial.txt)"
```

看到「安装写入与回读完成」后，用原备份恢复副本，然后逐字节比较：

```sh
python3 install.py restore --disk-copy ../private/practice.img --backup ../private/stock.img --backup-sha256 "$(awk '/备份已回读/{hash=$NF} END{print hash}' ../private/backup.log)" --confirm-serial "$(cat ../private/serial.txt)"
cmp ../private/stock.img ../private/practice.img && echo '演练通过，恢复后的副本与原备份完全一致'
```

如果已经有名为 practice.img 的文件，先弄清它的用途。这份演练会对它写入。请保留最初读取的 stock.img。

## 常见情况

| 看到什么 | 如何处理 |
| --- | --- |
| 平台组件缺失或校验失败 | 核对提示中的相对路径和 platform-files.json 对应的 SHA-256。公开源码 ZIP 本身没有完整 platform。 |
| 只显示 ADB、Loader 列表为空或列出多台设备 | 回到实际连接 USB 的主机核对维护模式、数据线和连接设备数。使用已有授权的维护入口，再运行 rkdeveloptool ld。 |
| 识别到其他序列号、分区表或原厂 B 不匹配 | 核对手上的笔、备份及用户目录。使用该笔自己的备份，保留现有数据；当前 RC 用于已列出的原厂布局。 |
| 提示已经有双系统或需要原厂 boot A | 此程序用于首次安装，升级已有安装需要对应升级包。 |
| 无法挂载 ext4 或普通用户权限不足 | 在具备 loop 设备和挂载能力的 Linux 系统中运行 prepare。检查备份与平台路径在 Linux 内可见。 |
| 写入时断线 | 重新连接同一支笔进入 Loader，用原来的备份和同一份 update 先运行 check，再运行 install。已写入且回读匹配的部分会跳过。 |
| 已经启动使用过安卓，现在想回到安装前 | 先另存新数据，再按备份 SHA-256 执行独立 restore。恢复会覆盖安装以来的用户分区更改。 |
| 原厂恢复成功后 Loader 仍有设备 | 安装器完成回读后保留维护连接。使用同一工具执行 rkdeveloptool rd，再观察实际启动画面。 |

报告问题时，请附型号、原厂版本、主机系统、失败的步骤和错误文字。公开反馈可隐去设备序列号与本地路径；私人备份和个人扫描内容请保存在本机。
