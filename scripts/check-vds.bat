@echo off
setlocal EnableExtensions
cd /d "%~dp0\.."

set "VDS_IP=162.35.231.9"
set "FAIL=0"

echo [*] vds check
echo     ip %VDS_IP%
echo.

powershell -NoProfile -Command ^
  "$ip='%VDS_IP%';" ^
  "Write-Host '=== DNS ===';" ^
  "foreach($d in 'spywarehook.com','www.spywarehook.com','spywarehook.org','www.spywarehook.org'){" ^
  "  try{$a=(Resolve-DnsName $d -Type A -EA Stop|? Type -eq 'A'|%% IPAddress)-join ', '; Write-Host ($d+' => '+$a)}" ^
  "  catch{Write-Host ($d+' => FAIL'); $global:fail=1}" ^
  "};" ^
  "Write-Host '';" ^
  "Write-Host '=== LISTEN ===';" ^
  "foreach($p in 80,443,3000,3001){" ^
  "  $c=Get-NetTCPConnection -State Listen -LocalPort $p -EA SilentlyContinue|Select -First 1;" ^
  "  if($c){$n=(Get-Process -Id $c.OwningProcess -EA SilentlyContinue).ProcessName; Write-Host ('OK  '+$p+' '+$c.LocalAddress+' '+$n)}" ^
  "  else{Write-Host ('MISS '+$p); $global:fail=1}" ^
  "};" ^
  "Write-Host '';" ^
  "Write-Host '=== FIREWALL ===';" ^
  "foreach($n in 'noface-http','noface-https'){" ^
  "  $r=Get-NetFirewallRule -DisplayName $n -EA SilentlyContinue;" ^
  "  if($r -and $r.Enabled -eq 'True'){Write-Host ('OK  '+$n)}" ^
  "  else{Write-Host ('MISS '+$n); $global:fail=1}" ^
  "};" ^
  "Write-Host '';" ^
  "Write-Host '=== TOOLS ===';" ^
  "foreach($t in 'node','python','java','caddy'){" ^
  "  $c=Get-Command $t -EA SilentlyContinue;" ^
  "  if(-not $c -and $t -eq 'caddy' -and (Test-Path '%~dp0..\caddy\caddy.exe')){$c=[PSCustomObject]@{Source='%~dp0..\caddy\caddy.exe'}};" ^
  "  if(-not $c -and $t -eq 'java'){" ^
  "    foreach($p in @('C:\\Program Files\\Java\\jdk-21.0.12\\bin\\java.exe','C:\\Program Files\\Java\\jdk-21\\bin\\java.exe')){" ^
  "      if(Test-Path $p){$c=[PSCustomObject]@{Source=$p}; break}" ^
  "    }" ^
  "  };" ^
  "  if($c){Write-Host ('OK  '+$t+' '+$c.Source)}" ^
  "  else{Write-Host ('MISS '+$t); $global:fail=1}" ^
  "};" ^
  "Write-Host '';" ^
  "Write-Host '=== HTTP LOCAL ===';" ^
  "try{$r=Invoke-WebRequest 'http://127.0.0.1:3000' -UseBasicParsing -TimeoutSec 5 -MaximumRedirection 0; Write-Host ('OK  panel '+$r.StatusCode)}" ^
  "catch{$c=$_.Exception.Response.StatusCode.value__; if($c){Write-Host ('OK  panel '+$c)} else {Write-Host 'MISS panel'; $global:fail=1}};" ^
  "try{$r=Invoke-WebRequest 'http://127.0.0.1:3001' -UseBasicParsing -TimeoutSec 5; Write-Host ('OK  hub '+$r.StatusCode)}" ^
  "catch{Write-Host 'MISS hub'; $global:fail=1};" ^
  "if($global:fail){exit 1}else{exit 0}"
if errorlevel 1 set "FAIL=1"

echo.
if "%FAIL%"=="1" (
  echo [!] sorun var
  echo     cloudflare A kayitlari origin: %VDS_IP%
  echo     SSL mode: Full veya Full strict + origin cert
  exit /b 1
)
echo [+] ok
exit /b 0
