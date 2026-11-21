# 第一次使用：先在电脑上试跑

适用产品是**有道词典笔 3（型号 YDP031）**，开发机的完整型号为 **YDP031CHNSTD**。这一页只用电脑，不用连接笔。完成的标志是终端最后显示 `OK`。

## 1. 打开电脑的终端

- **Mac**：同时按 Command 和空格，输入「终端」，按回车。
- **Ubuntu Linux**：同时按 Ctrl、Alt 和 T。
- **Windows**：可以直接[看实机效果](results.md)。下面的命令适用于 Mac 和 Ubuntu，普通 PowerShell 不能直接运行这套安装器。

把下面一行复制到出现的窗口，按回车。

```sh
python3 --version
```

看到 `Python 3.9`、`3.10` 或更高的 Python 3 即可继续。如果 Mac 显示找不到命令，可从官网选择稳定版的 **macOS installer**，双击下载的安装包按提示安装，然后重新打开终端。Ubuntu 可运行 `sudo apt install python3 curl unzip`。[Python 官方下载](https://www.python.org/downloads/macos/)

## 2. 下载正确的包

[GitHub 下载页](https://github.com/who-andy/ydp031-cherry-android/releases/tag/v0.1.0-rc.1-docs.1)的 **Assets（附件）** 里有两个文件：`Cherry-YDP031-RC1.zip` 是安装器和教程；`SHA256SUMS` 是检查下载是否完整的“指纹单”。

**最简单的方式是粘贴下面一段，一次下载两个文件并放对位置。** 每条下载进度都到 100% 后继续。

```sh
mkdir -p "$HOME/CherryPen/download"
cd "$HOME/CherryPen/download"
base='https://github.com/who-andy/ydp031-cherry-android/releases/download/v0.1.0-rc.1-docs.1'
curl -fL "$base/Cherry-YDP031-RC1.zip" -o Cherry-YDP031-RC1.zip
curl -fL "$base/SHA256SUMS" -o SHA256SUMS
```

如果已用浏览器下载，可手动把这两个文件放进个人文件夹下的 `CherryPen/download`，保留原始 ZIP，然后在终端运行 `cd "$HOME/CherryPen/download"`。

## 3. 校验，再解压

**Mac**：

```sh
shasum -a 256 -c SHA256SUMS
```

**Ubuntu**：

```sh
sha256sum -c SHA256SUMS
```

看到 **`Cherry-YDP031-RC1.zip: OK`** 再继续。如果显示 `FAILED` 或找不到文件，先回第 2 步重新下载。第一次解压时运行下面两行。以后重新打开终端，只需运行第二行。

```sh
unzip -n "$HOME/CherryPen/download/Cherry-YDP031-RC1.zip" -d "$HOME/CherryPen/installer"
cd "$HOME/CherryPen/installer"
```

## 4. 试跑五项自测

```sh
python3 -m unittest discover -s tests -v
```

看到 **`Ran 5 tests`** 和 **`OK`**，说明电脑能运行安装器。这时笔里的内容没有任何变化。若看到 `Ran 0 tests`，重新执行第 3 步的 `cd`，输入 `ls`，确认能看到 `install.py` 和 `tests` 再试。

**这里只有公开 ZIP 的话，到这里就完成了。** 实际安装还需要一个叫 `platform` 的配套文件夹，内含本版匹配的安卓系统、驱动和应用，共 68 个文件，目前没有整套公开下载地址。源码旁边的 `platform-files.json` 只是文件清单。已经备齐完整文件夹的人可以继续看[实际安装](installation.md)。
