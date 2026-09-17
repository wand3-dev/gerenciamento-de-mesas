param (
    [string]$Versao = "",
    [string]$Changelog = "AppMesas atualizado com suporte a comandas e mesas dinamicas, Minhas Comandas e deteccao de troca de mesa."
)

$ErrorActionPreference = "Stop"
$apkSrc = "C:\Users\Windows Lite BR\Downloads\download\AppMesas.apk"
$ghCli = "C:\Program Files\GitHub CLI\gh.exe"
$firebaseCli = "C:\Users\Windows Lite BR\AppData\Roaming\npm\firebase.cmd"
$repo = "wand3-dev/gerenciamento-de-mesas"
$firebaseProject = "appmesas-238a0"
$firebaseInstance = "appmesas-238a0-default-rtdb"

if (-not (Test-Path $apkSrc)) {
    Write-Error "Arquivo APK não encontrado em: $apkSrc"
}

if (-not (Test-Path $ghCli)) {
    Write-Error "GitHub CLI não encontrado em: $ghCli"
}

# Lê versionCode e versionName do app/build.gradle
$gradleContent = Get-Content "app/build.gradle" -Raw
$vCode = 1
$vName = "1.0"

if ($gradleContent -match 'versionCode\s+(\d+)') {
    $vCode = [int]$matches[1]
}
if ($gradleContent -match 'versionName\s+"([^"]+)"' -or $gradleContent -match "versionName\s+'([^']+)'") {
    $vName = $matches[1]
}

if ([string]::IsNullOrWhiteSpace($Versao)) {
    $Versao = "v" + $vName
}

$tituloRelease = "AppMesas $Versao"

Write-Host "=============================================" -ForegroundColor Cyan
Write-Host "Iniciando publicacao de $tituloRelease (Code: $vCode)" -ForegroundColor Cyan
Write-Host "=============================================" -ForegroundColor Cyan

# 1. Commit e push do código fonte no GitHub
Write-Host "[1/3] Enviando codigo para o GitHub..." -ForegroundColor Yellow
git add .
try {
    git commit -m "Atualizacao $Versao (code $vCode)"
} catch {
    Write-Host "Sem alteracoes pendentes de codigo para commit." -ForegroundColor Gray
}
git push origin main

# 2. Atualiza a Release no GitHub (mantém uma única release)
Write-Host "[2/3] Atualizando APK e nome da Release no GitHub..." -ForegroundColor Yellow
$releasesJson = & $ghCli api "repos/$repo/releases" | ConvertFrom-Json
$downloadUrl = "https://github.com/$repo/releases/download/$Versao/AppMesas.apk"

if ($releasesJson -and $releasesJson.Count -gt 0) {
    $primeiraRelease = $releasesJson[0]
    $releaseId = $primeiraRelease.id
    $releaseTag = $primeiraRelease.tag_name

    & $ghCli api -X PATCH "repos/$repo/releases/$releaseId" -f name="$tituloRelease" -f body="" | Out-Null
    & $ghCli release upload "$releaseTag" "$apkSrc" --clobber
    $downloadUrl = "https://github.com/$repo/releases/download/$releaseTag/AppMesas.apk"
    Write-Host "OK: Release '$tituloRelease' atualizada com sucesso!" -ForegroundColor Green
} else {
    & $ghCli release create "$Versao" "$apkSrc" --title "$tituloRelease" --notes ""
    Write-Host "OK: Primeira release criada com sucesso!" -ForegroundColor Green
}

# 3. Atualiza o Firebase Realtime Database
Write-Host "[3/3] Sincronizando versao no Firebase Realtime Database..." -ForegroundColor Yellow
if (Test-Path $firebaseCli) {
    $tempJson = Join-Path $env:TEMP "firebase_rtdb_update.json"
    $objPayload = [PSCustomObject]@{
        versionCode = $vCode
        versionName = $vName
        downloadUrl = $downloadUrl
        changelog   = "$tituloRelease - $Changelog"
    }
    $rtdbPayload = $objPayload | ConvertTo-Json -Depth 5

    $utf8NoBom = New-Object System.Text.UTF8Encoding($false)
    [System.IO.File]::WriteAllText($tempJson, $rtdbPayload, $utf8NoBom)
    & $firebaseCli database:set / "$tempJson" --project $firebaseProject --instance $firebaseInstance -f | Out-Null
    Remove-Item $tempJson -Force -ErrorAction SilentlyContinue
    Write-Host "OK: Firebase Realtime Database atualizado: Versao $vName ($vCode)" -ForegroundColor Green
} else {
    Write-Warning "Firebase CLI nao encontrado para sincronizacao remota."
}

Write-Host "=============================================" -ForegroundColor Green
Write-Host "OK: Processo concluido com sucesso total!" -ForegroundColor Green
Write-Host "Download: $downloadUrl" -ForegroundColor Cyan
Write-Host "=============================================" -ForegroundColor Green
