param (
    [string]$Versao = "",
    [string]$Notas = "Nova versão do AppMesas compilada e publicada automaticamente."
)

$ErrorActionPreference = "Stop"
$apkSrc = "C:\Users\Windows Lite BR\Downloads\download\AppMesas.apk"
$ghCli = "C:\Program Files\GitHub CLI\gh.exe"

if (-not (Test-Path $apkSrc)) {
    Write-Error "Arquivo APK não encontrado em: $apkSrc"
}

if (-not (Test-Path $ghCli)) {
    Write-Error "GitHub CLI não encontrado em: $ghCli"
}

# Se não informada a versão, tenta ler do build.gradle
if ([string]::IsNullOrWhiteSpace($Versao)) {
    $gradleContent = Get-Content "app/build.gradle" -Raw
    if ($gradleContent -match 'versionName\s+"([^"]+)"' -or $gradleContent -match "versionName\s+'([^']+)'") {
        $Versao = "v" + $matches[1]
    } else {
        $Versao = "v" + (Get-Date -Format "yyyyMMdd.HHmm")
    }
}

Write-Host "=============================================" -ForegroundColor Cyan
Write-Host "Publicando versão $Versao no GitHub..." -ForegroundColor Cyan
Write-Host "=============================================" -ForegroundColor Cyan

# Commit e push do código fonte
git add .
try {
    git commit -m "Release $Versao"
} catch {
    Write-Host "Sem novas alterações no código para commit." -ForegroundColor Yellow
}
git push origin main

# Cria a release e anexa o APK
& $ghCli release create $Versao "$apkSrc" --title "AppMesas $Versao" --notes "$Notas"

Write-Host "Sucesso! Release $Versao publicada com o APK anexado." -ForegroundColor Green
