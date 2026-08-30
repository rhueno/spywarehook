@echo off
setlocal EnableExtensions
cd /d "%~dp0"

echo [*] spywarehook start-all
call "%~dp0stop-all.bat"

if not exist "%~dp0bot\.env" (
  echo [!] bot\.env missing
  exit /b 1
)
if not exist "%~dp0web\.env" (
  echo [!] web\.env missing
  exit /b 1
)
if not exist "%~dp0bot\.venv\Scripts\python.exe" (
  echo [!] bot venv missing — create bot\.venv first
  exit /b 1
)
where node >nul 2>&1
if errorlevel 1 (
  echo [!] node not found
  exit /b 1
)

if not exist "%~dp0web\node_modules" (
  echo [*] npm install web...
  pushd "%~dp0web"
  call npm install
  if errorlevel 1 popd & exit /b 1
  popd
)

if not exist "%~dp0web\.next\BUILD_ID" (
  echo [*] next build...
  pushd "%~dp0web"
  set NODE_ENV=production
  call npm run build
  if errorlevel 1 popd & exit /b 1
  popd
)

echo [*] caddy
start "noface-caddy" /MIN cmd /c "cd /d ""%~dp0caddy"" && run.bat"

echo [*] hub :3001
start "noface-hub" /MIN cmd /c "cd /d ""%~dp0web"" && hub\watch.bat"

echo [*] panel :3000
start "noface-web" /MIN cmd /c "cd /d ""%~dp0web"" && set NODE_ENV=production&& npm run start -- -p 3000 -H 127.0.0.1"

echo [*] telegram bot
start "noface-bot" /MIN cmd /c "cd /d ""%~dp0bot"" && .venv\Scripts\python.exe main.py"

ping -n 5 127.0.0.1 >nul
echo.
echo [+] up
echo     panel  http://127.0.0.1:3000
echo     hub    ws 127.0.0.1:3001
echo     sites  spywarehook.com / spywarehook.org
echo     bot    @spywarehookbot
echo.
echo stop: stop-all.bat
endlocal
