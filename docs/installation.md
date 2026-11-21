# 安装与恢复

普通用户请按[第一次刷机](start.md)操作。安装、备份和恢复共用下载包内的 Start.command。

## 命令行入口

运行 `python3 wizard.py --help` 查看向导参数。`--self-test` 只读检查解压包；`--download-only` 从发布者下载匹配的运行文件。两者都不访问设备。

需要调试单独环节时，运行 `python3 install.py --help`。首次安装依次经过 identify、backup、prepare、install；install 在写入之前自行执行完整的设备、备份及范围检查。独立恢复使用 restore、备份文件及其最初的摘要。向导会自动提供对应参数，拷贝别人的序列号或磁盘备份无法通过核验。

## 恢复

详见[恢复到安装之前](start.md#恢复到安装之前)。
