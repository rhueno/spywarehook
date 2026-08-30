@echo off
setlocal EnableExtensions
cd /d "%~dp0\.."

set "IN=target\noface.jar"
set "OUT=target\noface-protected.jar"

if not exist "%IN%" (
    echo [!] %IN% yok
    exit /b 1
)

copy /Y "%IN%" "%OUT%" >nul
if errorlevel 1 (
    echo [!] copy fail
    exit /b 1
)

echo [+] shield skip %OUT%
exit /b 0
