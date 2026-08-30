@echo off
setlocal EnableExtensions
cd /d "%~dp0\.."
:loop
node hub\server.mjs
echo [hub] exit %ERRORLEVEL% — restart 2s
timeout /t 2 /nobreak >nul
goto loop
