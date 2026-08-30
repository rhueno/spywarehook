@echo off
setlocal EnableExtensions
cd /d "%~dp0"

if not exist target\noface-protected.jar (
    echo [*] jar yok, build.bat...
    call build.bat
    if errorlevel 1 exit /b 1
)

set "JAVA_EXE="
if exist bin\jdk.txt set /p JAVA_EXE=<bin\jdk.txt
if not defined JAVA_EXE if defined JAVA_HOME set "JAVA_EXE=%JAVA_HOME%\bin\java.exe"
if not exist "%JAVA_EXE%" (
    for /f "delims=" %%J in ('where java 2^>nul') do (
        set "JAVA_EXE=%%J"
        goto :ok
    )
)
:ok
if not exist "%JAVA_EXE%" (
    echo [!] java bulunamadi
    exit /b 1
)

echo [*] %JAVA_EXE%
"%JAVA_EXE%" --enable-native-access=ALL-UNNAMED -jar target\noface-protected.jar %*
exit /b %ERRORLEVEL%
