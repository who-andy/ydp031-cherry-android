param([Parameter(Mandatory=$true)][string]$OutputDir)
$ErrorActionPreference = 'Stop'
$csc = Join-Path $env:WINDIR 'Microsoft.NET\Framework64\v4.0.30319\csc.exe'
$null = New-Item -ItemType Directory -Path $OutputDir -Force
$exe = Join-Path $OutputDir 'Start-Windows.exe'
& $csc /nologo /codepage:65001 /platform:x64 /optimize+ /target:exe /reference:System.Web.Extensions.dll "/out:$exe" "$PSScriptRoot\StartWindows.cs"
if ($LASTEXITCODE) { throw 'Windows launcher compilation failed' }
