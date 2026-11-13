# Compila o myfriend e instala no relogio por Wi-Fi (depuracao sem fio).
# Uso:  instalar.bat              compila, conecta e instala por cima
#       instalar.bat -Mostrador   tambem abre o seletor de mostrador no myfriend
#       instalar.bat -SemCompilar instala o APK que ja existe
# O ultimo endereco que funcionou fica em .relogio (fora do git).
param([switch]$Mostrador, [switch]$SemCompilar)

$ErrorActionPreference = 'Stop'
$raiz = $PSScriptRoot
$apk = Join-Path $raiz 'app\build\outputs\apk\release\app-release.apk'
$memoria = Join-Path $raiz '.relogio'

function Falha($msg) { Write-Host $msg -ForegroundColor Red; exit 1 }

# ---------------------------------------------------------------- adb
$adb = $null
$cmd = Get-Command adb -ErrorAction SilentlyContinue
if ($cmd) { $adb = $cmd.Source }
if (-not $adb) {
    $candidatos = @(
        "$env:LOCALAPPDATA\Microsoft\WinGet\Packages\Google.PlatformTools_*\platform-tools\adb.exe",
        "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
    )
    foreach ($c in $candidatos) {
        $achado = Get-Item $c -ErrorAction SilentlyContinue | Select-Object -First 1
        if ($achado) { $adb = $achado.FullName; break }
    }
}
if (-not $adb) { Falha 'Nao achei o adb. Instale com: winget install Google.PlatformTools' }

# ---------------------------------------------------------------- compilar
if (-not $SemCompilar) {
    if (-not $env:JAVA_HOME) {
        $jdk = Get-Item 'C:\Program Files\Eclipse Adoptium\jdk-21*' -ErrorAction SilentlyContinue | Select-Object -First 1
        if ($jdk) { $env:JAVA_HOME = $jdk.FullName }
    }
    Write-Host 'Compilando...' -ForegroundColor Cyan
    & (Join-Path $raiz 'gradlew.bat') -p $raiz --offline -q assembleRelease
    if ($LASTEXITCODE -ne 0) { Falha 'A compilacao falhou.' }
}
if (-not (Test-Path $apk)) { Falha "Nao achei o APK em $apk" }

# ---------------------------------------------------------------- conectar
function Conectado($endereco) {
    $linhas = & $adb devices
    foreach ($l in $linhas) { if ($l -match ('^' + [regex]::Escape($endereco) + '\s+device')) { return $true } }
    return $false
}

function Tentar($endereco) {
    if (-not $endereco) { return $false }
    & $adb connect $endereco | Out-Null
    return (Conectado $endereco)
}

$alvo = $null
$ip = $null
if (Test-Path $memoria) {
    $salvo = (Get-Content $memoria -TotalCount 1).Trim()
    if ($salvo -match '^(.+):(\d+)$') { $ip = $Matches[1] }
    Write-Host "Tentando $salvo..." -ForegroundColor Cyan
    if (Tentar $salvo) { $alvo = $salvo }
}

if (-not $alvo) {
    # Descoberta automatica (funciona em Wi-Fi de casa; hotspot de celular costuma bloquear).
    $servicos = & $adb mdns services
    foreach ($s in $servicos) {
        if ($s -match '_adb-tls-connect\._tcp\S*\s+(\d+\.\d+\.\d+\.\d+:\d+)') {
            if (Tentar $Matches[1]) { $alvo = $Matches[1]; break }
        }
    }
}

while (-not $alvo) {
    Write-Host ''
    Write-Host 'No relogio: Configuracoes > Opcoes do desenvolvedor > Depuracao sem fio.' -ForegroundColor Yellow
    if ($ip) { $dica = "Porta (IP $ip) ou IP:porta" } else { $dica = 'IP:porta' }
    $entrada = (Read-Host $dica).Trim()
    if (-not $entrada) { Falha 'Cancelado.' }
    if ($entrada -match '^\d+$') {
        if (-not $ip) { Write-Host 'Preciso do IP tambem (IP:porta).'; continue }
        $entrada = "${ip}:$entrada"
    }
    if (Tentar $entrada) {
        $alvo = $entrada
    } else {
        Write-Host "Nao conectou em $entrada. Confira se o Wi-Fi do relogio esta ligado e se o PC ja foi pareado (adb pair IP:porta-de-pareamento)." -ForegroundColor Red
        if ($entrada -match '^(.+):\d+$') { $ip = $Matches[1] }
    }
}
Set-Content -Path $memoria -Value $alvo -Encoding ascii
Write-Host "Conectado em $alvo" -ForegroundColor Green

# ---------------------------------------------------------------- instalar
Write-Host 'Instalando...' -ForegroundColor Cyan
$saida = (& $adb -s $alvo install -r $apk 2>&1 | Out-String)
if ($saida -match 'INSTALL_FAILED_UPDATE_INCOMPATIBLE') {
    Write-Host 'O app do relogio foi assinado com outra chave. Para instalar e preciso desinstalar,' -ForegroundColor Yellow
    Write-Host 'e isso APAGA o bichinho (moedas, itens e status).' -ForegroundColor Yellow
    if ((Read-Host 'Desinstalar e instalar de novo? (s/N)') -notmatch '^[sS]') { Falha 'Nada foi alterado no relogio.' }
    & $adb -s $alvo uninstall com.myfriend | Out-Null
    $saida = (& $adb -s $alvo install $apk 2>&1 | Out-String)
    $Mostrador = $true
}
if ($saida -notmatch 'Success') { Falha "A instalacao falhou:`n$saida" }

$versao = (& $adb -s $alvo shell dumpsys package com.myfriend | Select-String 'versionName' | Select-Object -First 1)
Write-Host ("Instalado! " + "$versao".Trim()) -ForegroundColor Green

if ($Mostrador) {
    & $adb -s $alvo shell am start -n com.myfriend/.MainActivity --ez set_watchface true | Out-Null
    Write-Host 'Abri o seletor de mostrador no relogio: toque no myfriend para confirmar.' -ForegroundColor Yellow
}
