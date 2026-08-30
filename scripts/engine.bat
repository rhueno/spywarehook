@echo off
setlocal EnableExtensions
cd /d "%~dp0\.."

if not exist "target\noface-protected.jar" (
    echo [!] target\noface-protected.jar yok
    exit /b 1
)
if not exist "dropper\res\app.zip" (
    echo [!] dropper\res\app.zip yok — once pack-exe jlink adimi
    exit /b 1
)

if not exist "dropper\res" mkdir "dropper\res"
copy /Y "target\noface-protected.jar" "dropper\res\core.jar" >nul
cd dropper
call pack.bat
if errorlevel 1 (
    cd ..
    exit /b 1
)
cd ..
exit /b 0
