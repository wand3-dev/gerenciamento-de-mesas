param (
    [string]$Versao = ""
)

$ErrorActionPreference = "Stop"
$apkSrc = "C:\Users\Windows Lite BR\Downloads\download\AppMesas.apk"
$ghCli = "C:\Program Files\GitHub CLI\gh.exe"
$repo = "wand3-dev/gerenciamento-de-mesas"

if (-not (Test-Path $apkSrc)) {
    Write-Error "Arquivo APK não encontrado em: $apkSrc"
}

if (-not (Test-Path $ghCli)) {
    Write-Error "GitHub CLI não encontrado em: $ghCli"
}

# Se não informada a versão, lê do app/build.gradle
if ([string]::IsNullOrWhiteSpace($Versao)) {
    $gradleContent = Get-Content "app/build.gradle" -Raw
    if ($gradleContent -match 'versionName\s+"([^"]+)"' -or $gradleContent -match "versionName\s+'([^']+)'") {
        $Versao = "v" + $matches[1]
    } else {
        $Versao = "v" + (Get-Date -Format "yyyyMMdd.HHmm")
    }
}

$tituloRelease = "AppMesas $Versao"

Write-Host "=============================================" -ForegroundColor Cyan
Write-Host "Atualizando release única para: $tituloRelease" -ForegroundColor Cyan
Write-Host "=============================================" -ForegroundColor Cyan

# 1. Commit e push do código no git
git add .
try {
    git commit -m "Atualização $Versao"
} catch {
    Write-Host "Sem alterações de código para commit." -ForegroundColor Yellow
}
git push origin main

# 2. Busca a primeira release existente no repositório
$releasesJson = & $ghCli api "repos/$repo/releases" | ConvertFrom-Json

if ($releasesJson -and $releasesJson.Count -gt 0) {
    $primeiraRelease = $releasesJson[0]
    $releaseId = $primeiraRelease.id
    $releaseTag = $primeiraRelease.tag_name

    Write-Host "Atualizando release existente (ID: $releaseId, Tag: $releaseTag)..." -ForegroundColor Yellow

    # Altera apenas o título pelo nome da versão e limpa descrição
    & $ghCli api -X PATCH "repos/$repo/releases/$releaseId" -f name="$tituloRelease" -f body="" | Out-Null

    # Substitui o APK na mesma release (--clobber sobrescreve o arquivo anterior)
    Write-Host "Substituindo APK na release..." -ForegroundColor Yellow
    & $ghCli release upload "$releaseTag" "$apkSrc" --clobber

    Write-Host "✓ Release atualizada com sucesso para '$tituloRelease' sem criar novas descrições!" -ForegroundColor Green
    Write-Host "Link da Release: https://github.com/$repo/releases/tag/$releaseTag" -ForegroundColor Cyan
} else {
    Write-Host "Nenhuma release encontrada. Criando a primeira release..." -ForegroundColor Yellow
    & $ghCli release create "$Versao" "$apkSrc" --title "$tituloRelease" --notes ""
    Write-Host "✓ Primeira release criada com sucesso!" -ForegroundColor Green
}
