#!/bin/sh
cd "$(dirname "$0")" || exit 1
export PATH="/opt/homebrew/bin:/usr/local/bin:$PATH"
if ! command -v python3 >/dev/null; then
    echo '请先从 python.org 安装 Python 3.9 或更高版本，再打开本文件。'
    exit 1
fi
exec python3 -B wizard.py "$@"
