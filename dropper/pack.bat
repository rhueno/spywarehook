@echo off
setlocal EnableExtensions EnableDelayedExpansion
cd /d "%~dp0"

echo Building EXE...
if not exist "res\core.jar" (
    echo HATA: res\core.jar yok!
    exit /b 1
)
if not exist "res\app.zip" (
    echo HATA: res\app.zip yok!
    exit /b 1
)
if not exist "res\icon.ico" (
    echo HATA: res\icon.ico yok!
    exit /b 1
)
if not exist "src\boot.js" (
    echo HATA: src\boot.js yok!
    exit /b 1
)

where node >nul 2>&1
if errorlevel 1 (
    echo [!] node bulunamadi
    exit /b 1
)

if not exist "node_modules\electron" (
    echo [*] npm install...
    call npm install --no-fund --no-audit
    if errorlevel 1 exit /b 1
)

if not exist out mkdir out
if not exist dist mkdir dist

echo [1/3] mutate...
call npm run mutate
if errorlevel 1 exit /b 1

echo [2/3] host.js...
if not exist out mkdir out
copy /Y src\boot.js out\host.js >nul
if errorlevel 1 exit /b 1
if not exist "out\host.js" (
    echo HATA: out\host.js uretilemedi
    exit /b 1
)

if exist "dist\*.exe" del /f /q "dist\*.exe" >nul 2>&1
if exist "dist\*.blockmap" del /f /q "dist\*.blockmap" >nul 2>&1

echo [3/3] electron-builder...
set "CSC_IDENTITY_AUTO_DISCOVERY=false"
call npx electron-builder --win --x64
if errorlevel 1 exit /b 1

set "FOUND_EXE="
for /f "delims=" %%f in ('dir /b /o-d "dist\*.exe" 2^>nul') do (
  if not defined FOUND_EXE set "FOUND_EXE=dist\%%f"
)
if not defined FOUND_EXE (
    echo HATA: EXE olusturulamadi!
    exit /b 1
)

if exist "dist\builder-debug.yml" del "dist\builder-debug.yml" >nul 2>&1
if exist "dist\builder-effective-config.yaml" del "dist\builder-effective-config.yaml" >nul 2>&1
if exist "dist\win-unpacked" rmdir /s /q "dist\win-unpacked" >nul 2>&1

echo.
echo BUILD BASARILI
echo Dosya: !FOUND_EXE!
for %%A in ("!FOUND_EXE!") do echo Boyut: %%~zA
exit /b 0
