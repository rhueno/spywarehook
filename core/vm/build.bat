@echo off
setlocal EnableExtensions
cd /d "%~dp0"

set "ASM=lib\asm-9.7.1.jar;lib\asm-tree-9.7.1.jar;lib\asm-commons-9.7.1.jar"
set "RT_OUT=rt\target\classes"
set "TR_OUT=tr\target\classes"

if not defined JAVA_HOME (
  for %%D in ("C:\Program Files\Microsoft\jdk-25.0.3.9-hotspot" "D:\graalvm-jdk-21.0.10") do if exist "%%~D" set "JAVA_HOME=%%~D"
)
if defined JAVA_HOME set "PATH=%JAVA_HOME%\bin;%PATH%"

if not exist "%RT_OUT%" mkdir "%RT_OUT%"
if not exist "%TR_OUT%" mkdir "%TR_OUT%"
if not exist "tr\target" mkdir "tr\target"

echo [*] rt...
dir /s /b rt\src\*.java > "%TEMP%\nf_rt.txt"
javac -encoding UTF-8 --release 17 -d "%RT_OUT%" @"%TEMP%\nf_rt.txt"
if errorlevel 1 exit /b 1

echo [*] tr...
dir /s /b tr\src\*.java > "%TEMP%\nf_tr.txt"
javac -encoding UTF-8 --release 17 -cp "%RT_OUT%;%ASM%" -d "%TR_OUT%" @"%TEMP%\nf_tr.txt"
if errorlevel 1 exit /b 1

echo [*] shield.jar...
set "FAT=%TEMP%\nf_shield_fat"
if exist "%FAT%" rmdir /s /q "%FAT%"
mkdir "%FAT%"
xcopy /E /I /Y /Q "%RT_OUT%\*" "%FAT%\" >nul
xcopy /E /I /Y /Q "%TR_OUT%\*" "%FAT%\" >nul
pushd "%FAT%"
jar xf "%~dp0lib\asm-9.7.1.jar" >nul 2>&1
jar xf "%~dp0lib\asm-tree-9.7.1.jar" >nul 2>&1
jar xf "%~dp0lib\asm-commons-9.7.1.jar" >nul 2>&1
popd
(echo Main-Class: nf.tr.Shield) > "%TEMP%\nf_sh_mf.txt"
jar cfm "tr\target\shield.jar" "%TEMP%\nf_sh_mf.txt" -C "%FAT%" .
rmdir /s /q "%FAT%"
echo [+] shield.jar ok
exit /b 0
