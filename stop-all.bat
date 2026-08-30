@echo off
setlocal EnableExtensions
cd /d "%~dp0"

echo [*] stopping spywarehook stack...

powershell -NoProfile -ExecutionPolicy Bypass -Command ^
  "$ports=3000,3001; foreach($p in $ports){ Get-NetTCPConnection -LocalPort $p -State Listen -EA SilentlyContinue | ForEach-Object { Stop-Process -Id $_.OwningProcess -Force -EA SilentlyContinue } };" ^
  "Get-CimInstance Win32_Process -EA SilentlyContinue | Where-Object { $_.CommandLine -and ( ($_.CommandLine -match 'main\.py') -or ($_.CommandLine -match 'hub\\server\.mjs') -or ($_.CommandLine -match 'hub\\watch\.bat') -or ($_.CommandLine -match 'next.*(start|dist\\bin\\next)') -or ($_.Name -eq 'caddy.exe') ) } | ForEach-Object { Stop-Process -Id $_.ProcessId -Force -EA SilentlyContinue };" ^
  "Get-Process caddy -EA SilentlyContinue | Stop-Process -Force -EA SilentlyContinue"

taskkill /F /FI "WINDOWTITLE eq noface-caddy*" >nul 2>&1
taskkill /F /FI "WINDOWTITLE eq noface-hub*" >nul 2>&1
taskkill /F /FI "WINDOWTITLE eq noface-web*" >nul 2>&1
taskkill /F /FI "WINDOWTITLE eq noface-bot*" >nul 2>&1

ping -n 3 127.0.0.1 >nul
echo [+] stopped
endlocal
