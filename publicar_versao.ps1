param (
    [string]$Versao = "",
    [int]$CodigoVersao = 0
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

# Lê versionCode e versionName do app/build.gradle caso não informados
$gradleContent = Get-Content "app/build.gradle" -Raw

if ([string]::IsNullOrWhiteSpace($Versao)) {
    if ($gradleContent -match 'versionName\s+"([^"]+)"' -or $gradleContent -match "versionName\s+'([^']+)'") {
        $Versao = $matches[1]
    } else {
        $Versao = (Get-Date -Format "yyyyMMdd.HHmm")
    }
}

if ($CodigoVersao -le 0) {
    if ($gradleContent -match 'versionCode\s+(\d+)') {
        $CodigoVersao = [int]$matches[1]
    } else {
        $CodigoVersao = 1
    }
}

$versaoTag = "v" + $Versao.TrimStart("v")
$tituloRelease = "AppMesas $versaoTag"
$downloadUrl = "https://github.com/$repo/releases/download/$versaoTag/AppMesas.apk"

Write-Host "=============================================" -ForegroundColor Cyan
Write-Host "Iniciando publicação: $tituloRelease (Code $CodigoVersao)" -ForegroundColor Cyan
Write-Host "=============================================" -ForegroundColor Cyan

# 1. Commit e push do código no Git
git add .
try {
    git commit -m "Atualização $versaoTag (code $CodigoVersao)"
} catch {
    Write-Host "Sem novas alterações de código para commit." -ForegroundColor Yellow
}
git push origin main

# 2. Atualiza a Release no GitHub
Write-Host "Atualizando Release no GitHub..." -ForegroundColor Yellow
$releasesJson = & $ghCli api "repos/$repo/releases" | ConvertFrom-Json

if ($releasesJson -and $releasesJson.Count -gt 0) {
    $primeiraRelease = $releasesJson[0]
    $releaseId = $primeiraRelease.id
    $releaseTag = $primeiraRelease.tag_name

    # Altera título e limpa descrição
    & $ghCli api -X PATCH "repos/$repo/releases/$releaseId" -f name="$tituloRelease" -f body="" | Out-Null

    # Substitui o APK na release
    & $ghCli release upload "$releaseTag" "$apkSrc" --clobber
    Write-Host "✓ GitHub: APK e título atualizados com sucesso!" -ForegroundColor Green
} else {
    & $ghCli release create "$versaoTag" "$apkSrc" --title "$tituloRelease" --notes ""
    Write-Host "✓ GitHub: Primeira release criada!" -ForegroundColor Green
}

# 3. Atualiza os dados no Firebase Realtime Database
if (Test-Path $firebaseCli) {
    Write-Host "Atualizando Firebase Realtime Database..." -ForegroundColor Yellow
    $tempPayload = [System.IO.Path]::GetTempFileName() + ".json"
    $payloadData = @{
        versionCode = $CodigoVersao
        versionName = $Versao
        downloadUrl = $downloadUrl
        changelog = "Nova versão $versaoTag disponível"
    } | ConvertTo-Json

    Set-Content -Path $tempPayload -Value $payloadData -Encoding UTF8
    try {
        & $firebaseCli database:set / "$tempPayload" --project "$firebaseProject" --instance "$firebaseInstance" --force | Out-Null
        Write-Host "✓ Firebase: Versão atualizada para Code $CodigoVersao ($Versao)!" -ForegroundColor Green
    } finally {
        Remove-Item -Force -ErrorAction SilentlyContinue "$tempPayload"
    }
} else {
    Write-Host "Aviso: Firebase CLI não encontrado em $firebaseCli" -ForegroundColor Yellow
}

Write-Host "=============================================" -ForegroundColor Green
Write-Host "Publicação concluída com sucesso no GitHub e Firebase!" -ForegroundColor Green
Write-Host "=============================================" -ForegroundColor Green
