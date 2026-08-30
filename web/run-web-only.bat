@echo off
setlocal EnableExtensions
cd /d "%~dp0"

title NoFace - Sadece Web Sitesi

echo ======================================================
echo    NOFACE - SADECE WEB SITE (Guvonli Calistirma)
echo    (Hub, Bot, C2, Caddy calistirilmaz)
echo ======================================================
echo.

where node >nul 2>&1
if errorlevel 1 (
  echo [!] Node.js sistemde bulunamadi. Lutfen Node.js yukleyin.
  pause
  exit /b 1
)

if not exist node_modules (
  echo [*] Ilk kurulum: npm install yapiliyor...
  call npm install
  if errorlevel 1 (
    echo [!] npm install sirasinda hata olustu.
    pause
    exit /b 1
  )
)

set PORT=3000

echo [*] Sadece Next.js web uygulamasi baslatiliyor...
echo [*] Site acildiginda tarayicinizdan erisebilirsiniz:
echo     http://localhost:%PORT%
echo.
echo (Kapatmak icin bu pencerede Ctrl+C tuslarina basabilirsiniz)
echo.

call npm run dev -- -p %PORT%
if errorlevel 1 (
  echo.
  echo [!] Dev modunda hata alindiysa production baslatiliyor...
  if not exist .next\BUILD_ID (
    call npm run build
  )
  call npm run start -- -p %PORT%
)

pause
