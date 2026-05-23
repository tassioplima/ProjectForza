<#
  build_and_install.ps1
  ─────────────────────
  Configura o wrapper Gradle, compila o APK de debug e instala no dispositivo Android via ADB.
  Requer: Android Studio instalado (ou Android SDK + JDK separados).

  Uso:
    .\build_and_install.ps1              # compila e instala
    .\build_and_install.ps1 -BuildOnly   # só compila (sem instalar)
    .\build_and_install.ps1 -InstallOnly # só instala APK já compilado
#>

param(
    [switch]$BuildOnly,
    [switch]$InstallOnly
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

# ── Cores para output ────────────────────────────────────────────────────────
function Write-Step  { param($msg) Write-Host "`n▶ $msg" -ForegroundColor Cyan   }
function Write-Ok    { param($msg) Write-Host "  ✔ $msg" -ForegroundColor Green  }
function Write-Warn  { param($msg) Write-Host "  ⚠ $msg" -ForegroundColor Yellow }
function Write-Fail  { param($msg) Write-Host "  ✖ $msg" -ForegroundColor Red; exit 1 }

$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path

# ── 1. Descobrir ANDROID_HOME ────────────────────────────────────────────────
Write-Step "Localizando Android SDK"

$sdkRoot = $env:ANDROID_HOME ?? $env:ANDROID_SDK_ROOT

if (-not $sdkRoot) {
    # Localização padrão do Android Studio no Windows
    $candidates = @(
        "$env:LOCALAPPDATA\Android\Sdk",
        "$env:USERPROFILE\AppData\Local\Android\Sdk"
    )
    foreach ($c in $candidates) {
        if (Test-Path $c) { $sdkRoot = $c; break }
    }
}

if (-not $sdkRoot -or -not (Test-Path $sdkRoot)) {
    Write-Fail @"
Android SDK não encontrado.
Instale o Android Studio em https://developer.android.com/studio
ou defina a variável de ambiente ANDROID_HOME apontando para o SDK.
"@
}
Write-Ok "SDK encontrado em: $sdkRoot"

# ── 2. Localizar o ADB ───────────────────────────────────────────────────────
$adb = Join-Path $sdkRoot "platform-tools\adb.exe"
if (-not (Test-Path $adb)) {
    Write-Warn "ADB não encontrado em $adb — a instalação via ADB será ignorada."
    $adb = $null
}

# ── 3. Configurar o Gradle Wrapper ──────────────────────────────────────────
Write-Step "Verificando Gradle Wrapper"

$wrapperJar = Join-Path $ScriptDir "gradle\wrapper\gradle-wrapper.jar"

if (-not (Test-Path $wrapperJar)) {
    Write-Step "Baixando gradle-wrapper.jar (Gradle 8.6)"
    $jarUrl = "https://raw.githubusercontent.com/gradle/gradle/v8.6.0/gradle/wrapper/gradle-wrapper.jar"
    try {
        Invoke-WebRequest -Uri $jarUrl -OutFile $wrapperJar -UseBasicParsing
        Write-Ok "gradle-wrapper.jar baixado."
    } catch {
        # Fallback: tentar via Gradle instalado no sistema
        $gradleCmd = Get-Command gradle -ErrorAction SilentlyContinue
        if ($gradleCmd) {
            Write-Warn "Download falhou. Tentando 'gradle wrapper'…"
            Push-Location $ScriptDir
            gradle wrapper --gradle-version 8.6
            Pop-Location
        } else {
            Write-Fail @"
Não foi possível baixar o gradle-wrapper.jar e o Gradle não está instalado.
Opções:
  1. Abra o projeto no Android Studio — ele configura tudo automaticamente.
  2. Instale o Gradle manualmente: https://gradle.org/install/
"@
        }
    }
} else {
    Write-Ok "gradle-wrapper.jar já existe."
}

# Criar gradlew.bat minimalista se não existir
$gradlewBat = Join-Path $ScriptDir "gradlew.bat"
if (-not (Test-Path $gradlewBat)) {
    @'
@rem Gradle Wrapper (Windows)
@echo off
setlocal
set DIRNAME=%~dp0
set JAVA_EXE=java.exe
%JAVA_EXE% -classpath "%DIRNAME%gradle\wrapper\gradle-wrapper.jar" ^
    org.gradle.wrapper.GradleWrapperMain %*
endlocal
'@ | Set-Content $gradlewBat -Encoding ASCII
    Write-Ok "gradlew.bat criado."
}

# ── 4. Compilar APK de debug ─────────────────────────────────────────────────
$apkPath = Join-Path $ScriptDir "app\build\outputs\apk\debug\app-debug.apk"

if (-not $InstallOnly) {
    Write-Step "Compilando APK de debug"
    Push-Location $ScriptDir
    try {
        & ".\gradlew.bat" assembleDebug
        if ($LASTEXITCODE -ne 0) { Write-Fail "Build falhou (exit code $LASTEXITCODE)." }
        Write-Ok "Build concluído."
    } finally {
        Pop-Location
    }
}

if (-not (Test-Path $apkPath)) {
    Write-Fail "APK não encontrado em: $apkPath"
}
Write-Ok "APK: $apkPath"

# ── 5. Instalar via ADB ──────────────────────────────────────────────────────
if (-not $BuildOnly) {
    if (-not $adb) {
        Write-Warn "ADB não disponível — instale manualmente ou copie o APK para o dispositivo."
    } else {
        Write-Step "Instalando via ADB"

        # Verificar dispositivos conectados
        $devices = & $adb devices | Select-String "device$"
        if (-not $devices) {
            Write-Warn @"
Nenhum dispositivo Android detectado.
Certifique-se de que:
  • O cabo USB está ligado, OU o Wi-Fi Debug está ativo.
  • A Depuração USB está ativada nas Opções de Programador.
Depois execute novamente: .\build_and_install.ps1 -InstallOnly
"@
        } else {
            & $adb install -r $apkPath
            if ($LASTEXITCODE -eq 0) {
                Write-Ok "App instalado com sucesso!"
                Write-Host "`n  Abra 'Forza Gallery' no seu dispositivo Android." -ForegroundColor Magenta
            } else {
                Write-Warn "ADB install retornou código $LASTEXITCODE. Verifique o dispositivo."
            }
        }
    }
}

Write-Host "`n✅ Concluído!" -ForegroundColor Green
