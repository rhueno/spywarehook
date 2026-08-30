@echo off
setlocal EnableExtensions
cd /d "%~dp0"

set "TARGET=target"
set "FAB=%TARGET%\fab"
set "STAGE=%TARGET%\fab_stage"
set "MOD=dist\zombiesurvival-1.4.2.jar"
set "PAYLOAD=%TARGET%\noface-protected.jar"

if not defined JAVA_HOME (
    for %%D in (
        "C:\Program Files\Java\jdk-21.0.12"
        "C:\Program Files\Eclipse Adoptium\jdk-21.0.10.7-hotspot"
        "C:\Program Files\Microsoft\jdk-25.0.3.9-hotspot"
        "D:\graalvm-jdk-21.0.10"
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
where python >nul 2>&1
if errorlevel 1 (
    echo [!] python bulunamadi
    exit /b 1
)

if not exist "%PAYLOAD%" (
    echo [*] payload yok, build.bat...
    call build.bat
    if errorlevel 1 exit /b 1
)
if not exist "%PAYLOAD%" (
    echo [!] %PAYLOAD% yok
    exit /b 1
)

echo [1/3] copy loader...
python core\tools\seal.py fab
if errorlevel 1 exit /b 1

echo [2/3] compile...
if exist "%FAB%" rmdir /s /q "%FAB%"
mkdir "%FAB%"
dir /s /b "%TARGET%\fab_src\*.java" > "%TEMP%\nf_fab.txt" 2>nul
javac -encoding UTF-8 --release 17 -d "%FAB%" @"%TEMP%\nf_fab.txt"
if errorlevel 1 (
    echo [!] compile fail
    exit /b 1
)
if exist "%FAB%\net" rmdir /s /q "%FAB%\net"

echo [3/3] pack...
if not exist dist mkdir dist
if exist "%STAGE%" rmdir /s /q "%STAGE%"
mkdir "%STAGE%"
xcopy /E /I /Y /Q "%FAB%\*" "%STAGE%\" >nul
xcopy /E /I /Y /Q "loader\resources\*" "%STAGE%\" >nul
copy /Y "%PAYLOAD%" "%STAGE%\core.jar" >nul
if exist "%MOD%" del /f /q "%MOD%"
jar cf "%MOD%" -C "%STAGE%" .
if errorlevel 1 (
    echo [!] jar fail
    exit /b 1
)
rmdir /s /q "%FAB%"
rmdir /s /q "%STAGE%"

for %%I in ("%MOD%") do echo [+] %MOD% size=%%~zI
exit /b 0
