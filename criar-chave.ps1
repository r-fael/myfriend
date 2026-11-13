# Cria a chave fixa de assinatura do myfriend (rode UMA vez so).
# Gera myfriend-upload.jks e keystore.properties, os dois fora do git.
# GUARDE COPIA DOS DOIS num lugar seguro: sem eles nao da para atualizar o app.
$ErrorActionPreference = 'Stop'
$raiz = $PSScriptRoot
$jks = Join-Path $raiz 'myfriend-upload.jks'
$props = Join-Path $raiz 'keystore.properties'

if (Test-Path $jks) { Write-Host 'A chave myfriend-upload.jks ja existe. Nada foi feito.' -ForegroundColor Yellow; exit 0 }

$keytool = $null
$cmd = Get-Command keytool -ErrorAction SilentlyContinue
if ($cmd) { $keytool = $cmd.Source }
if (-not $keytool) {
    $jdk = Get-Item 'C:\Program Files\Eclipse Adoptium\jdk-21*' -ErrorAction SilentlyContinue | Select-Object -First 1
    if ($jdk) { $keytool = Join-Path $jdk.FullName 'bin\keytool.exe' }
}
if (-not $keytool -or -not (Test-Path $keytool)) { Write-Host 'Nao achei o keytool (vem com o JDK).' -ForegroundColor Red; exit 1 }

function LerSenha($texto) {
    $segura = Read-Host $texto -AsSecureString
    $ptr = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($segura)
    try { return [Runtime.InteropServices.Marshal]::PtrToStringBSTR($ptr) } finally { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($ptr) }
}

$senha = LerSenha 'Escolha uma senha para a chave (minimo 6 caracteres)'
if ($senha.Length -lt 6) { Write-Host 'Senha curta demais.' -ForegroundColor Red; exit 1 }
if ((LerSenha 'Repita a senha') -ne $senha) { Write-Host 'As senhas nao batem.' -ForegroundColor Red; exit 1 }
$nome = Read-Host 'Seu nome (aparece no certificado)'
if (-not $nome) { $nome = 'myfriend' }

# A senha vai por variavel de ambiente para nao aparecer na linha de comando.
$env:MYFRIEND_KS_PASS = $senha
& $keytool -genkeypair -keystore $jks -storetype PKCS12 -alias myfriend -keyalg RSA -keysize 2048 `
    -validity 10000 -dname "CN=$nome" -storepass:env MYFRIEND_KS_PASS -keypass:env MYFRIEND_KS_PASS
$codigo = $LASTEXITCODE
Remove-Item Env:MYFRIEND_KS_PASS
if ($codigo -ne 0) { Write-Host 'O keytool falhou.' -ForegroundColor Red; exit 1 }

$linhas = @('storeFile=myfriend-upload.jks', "storePassword=$senha", 'keyAlias=myfriend', "keyPassword=$senha")
[IO.File]::WriteAllLines($props, $linhas)

Write-Host ''
Write-Host 'Chave criada: myfriend-upload.jks + keystore.properties' -ForegroundColor Green
Write-Host 'Faca backup dos dois (gerenciador de senhas, pendrive). Eles NAO vao para o git.' -ForegroundColor Yellow
Write-Host 'Na proxima instalacao o relogio vai pedir para desinstalar uma ultima vez (a assinatura mudou).' -ForegroundColor Yellow
