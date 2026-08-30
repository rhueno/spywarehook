@echo off
setlocal EnableExtensions
cd /d "%~dp0"

if exist bin rmdir /s /q bin
if exist target rmdir /s /q target
if exist core\seal\bin rmdir /s /q core\seal\bin
if exist core\vm\rt\target rmdir /s /q core\vm\rt\target
if exist core\vm\tr\target rmdir /s /q core\vm\tr\target
if exist dropper\dist rmdir /s /q dropper\dist
if exist dropper\out\host.js del /f /q dropper\out\host.js
if exist dropper\res\core.jar del /f /q dropper\res\core.jar
if exist dropper\res\app.zip del /f /q dropper\res\app.zip
if exist dropper\core.jar del /f /q dropper\core.jar
if exist dropper\app.zip del /f /q dropper\app.zip
if exist dist rmdir /s /q dist
if exist bot\work rmdir /s /q bot\work
del /q test-*.txt 2>nul
del /q test-*.bat 2>nul

echo [+] temiz
exit /b 0
