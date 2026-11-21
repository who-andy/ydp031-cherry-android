# 磁盘演练和故障排查

## 先在文件上走一遍

准备好目标笔自己的原厂备份和由它生成的 update 目录。在有足够空间的文件系统上制作一份独立副本。以下步骤只对副本写入，使用的参数与 USB 流程一致。YOUR_BACKUP_SHA256 是备份时记录的 64 位十六进制摘要。

```sh
cp /private/path/stock.img /private/path/practice.img
python3 install.py check --disk-copy /private/path/practice.img --backup /private/path/stock.img --package /private/path/update --confirm-serial YOUR_SERIAL
python3 install.py install --disk-copy /private/path/practice.img --backup /private/path/stock.img --package /private/path/update --confirm-serial YOUR_SERIAL
python3 install.py restore --disk-copy /private/path/practice.img --backup /private/path/stock.img --backup-sha256 YOUR_BACKUP_SHA256 --confirm-serial YOUR_SERIAL
cmp /private/path/stock.img /private/path/practice.img
```

`cmp` 成功时不输出内容，退出状态为 0。请始终保留最初从设备读取的 stock.img。用硬链接作为练习副本会被安装器拒绝。

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
