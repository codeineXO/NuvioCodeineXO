$ErrorActionPreference = 'Stop'

$taskVsWhere = 'C:\Program Files (x86)\Microsoft Visual Studio\Installer\vswhere.exe'
$taskVcvars = & $taskVsWhere -latest -products '*' -requires Microsoft.VisualStudio.Component.VC.Tools.x86.x64 -find 'VC\Auxiliary\Build\vcvars64.bat' | Select-Object -First 1
if (-not $taskVcvars) { throw 'Visual Studio C++ tools are required.' }

$taskJdk = $env:JAVA_HOME
if (-not $taskJdk -or -not (Test-Path -LiteralPath (Join-Path $taskJdk 'include\jni.h'))) {
    $taskJdk = Split-Path (Split-Path (Get-Command javac.exe).Source)
}
$taskSdkRoot = Join-Path $env:USERPROFILE '.nuget\packages\microsoft.web.webview2'
$taskSdk = Get-ChildItem -LiteralPath $taskSdkRoot -Directory | Sort-Object { [version]$_.Name } -Descending | Select-Object -First 1
if (-not $taskSdk) { throw 'The WebView2 NuGet SDK is required.' }

$taskOutput = Join-Path ([IO.Path]::GetTempPath()) ('nuvio-controls-tests-' + [guid]::NewGuid().ToString('N'))
[IO.Directory]::CreateDirectory($taskOutput) | Out-Null
$taskSource = Join-Path $PSScriptRoot 'player_controls_recovery_test.cpp'
$taskExe = Join-Path $taskOutput 'player-controls-recovery-test.exe'
$taskInclude = Join-Path $taskSdk.FullName 'build\native\include'
$taskLoader = Join-Path $taskSdk.FullName 'build\native\x64\WebView2Loader.dll.lib'
$taskCompile = @"
@echo off
call "$taskVcvars" >nul
if errorlevel 1 exit /b %errorlevel%
cl /nologo /EHsc /std:c++17 /DUNICODE /D_UNICODE /DNOMINMAX /DWIN32_LEAN_AND_MEAN /permissive- "$taskSource" /I"$taskJdk\include" /I"$taskJdk\include\win32" /I"$taskInclude" /Fo"$taskOutput\test.obj" /Fe"$taskExe" /link /NOLOGO /INCREMENTAL:NO "$taskLoader" Ole32.lib User32.lib Gdi32.lib Dwmapi.lib Shell32.lib D3d11.lib Advapi32.lib
exit /b %errorlevel%
"@
$taskBatch = Join-Path $taskOutput 'compile.cmd'
[IO.File]::WriteAllText($taskBatch, $taskCompile)
& $env:ComSpec /d /c $taskBatch
if ($LASTEXITCODE -ne 0) { throw "Native regression test compilation failed ($LASTEXITCODE)." }

Copy-Item -LiteralPath (Join-Path $taskSdk.FullName 'build\native\x64\WebView2Loader.dll') -Destination $taskOutput
& $taskExe $taskOutput
if ($LASTEXITCODE -ne 0) { throw "Native regression tests failed ($LASTEXITCODE). Artifacts: $taskOutput" }
