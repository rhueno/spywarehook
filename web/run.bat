@echo off
setlocal EnableExtensions
cd /d "%~dp0"

if not exist "%~dp0..\bot\.env" (
  echo [!] bot\.env yok
  exit /b 1
)

where node >nul 2>&1
if errorlevel 1 (
  echo [!] node bulunamadi
  exit /b 1
)

if not exist .env (
  copy /Y .env.example .env >nul
  echo [!] web\.env olusturuldu — BOT_TOKEN / PANEL_* doldur
)

if not exist node_modules (
  call npm install
  if errorlevel 1 exit /b 1
)

set NODE_ENV=production
set PORT=3000
set HOSTNAME=0.0.0.0

if /I "%~1"=="rebuild" goto :build
if not exist .next\BUILD_ID goto :build
goto :run

:build
echo [*] next build
call npm run build
if errorlevel 1 exit /b 1

:run
start "noface-hub" /MIN cmd /c "cd /d ""%~dp0"" && hub\watch.bat"
echo [*] next start :%PORT%
call npm run start -- -p %PORT% -H 0.0.0.0
exit /b %ERRORLEVEL%
