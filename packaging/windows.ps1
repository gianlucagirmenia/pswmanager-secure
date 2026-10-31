param(
    [Parameter(Mandatory=$true)][string]$Jar,
    [Parameter(Mandatory=$true)][string]$RuntimeHome,
    [string]$JpackageHome = $env:JPACKAGE_HOME
)
$ErrorActionPreference = 'Stop'

# Only package artifacts already built by Maven. Never include the working directory/database.
$jarPath = (Resolve-Path -LiteralPath $Jar).Path
$runtimePath = (Resolve-Path -LiteralPath $RuntimeHome).Path
if (!(Test-Path -LiteralPath (Join-Path $runtimePath 'bin\java.exe'))) {
    throw 'Runtime Java non valido.'
}
$candidates = @()
if ($JpackageHome) { $candidates += (Join-Path $JpackageHome 'bin\jpackage.exe') }
$candidates += (Join-Path $runtimePath 'bin\jpackage.exe')
if ($env:JAVA_HOME) { $candidates += (Join-Path $env:JAVA_HOME 'bin\jpackage.exe') }
$onPath = Get-Command jpackage.exe -ErrorAction SilentlyContinue
if ($onPath) { $candidates += $onPath.Source }
# The local JDK used on this workstation; other machines can set JPACKAGE_HOME.
$candidates += 'C:\Program Files\java\java27\jdk-27.0.1.0.3\bin\jpackage.exe'
$packager = $candidates | Where-Object { Test-Path -LiteralPath $_ } | Select-Object -First 1
if (!$packager) {
    throw 'jpackage non trovato. Impostare JPACKAGE_HOME sulla directory di un JDK completo (21 o successivo), poi riavviare STS4.'
}

$buildDirectory = Split-Path -Parent $jarPath
$inputDirectory = Join-Path $buildDirectory ('jpackage-input-' + [guid]::NewGuid().ToString('N'))
$destination = Join-Path $buildDirectory 'desktop'
$appDirectory = Join-Path $destination 'Password123'
if (Test-Path -LiteralPath $appDirectory) {
    throw "Il pacchetto esiste già: $appDirectory. Chiudere Password123 e usare Maven clean install con il profilo windows-desktop."
}
$null = New-Item -ItemType Directory -Path $inputDirectory -Force
Copy-Item -LiteralPath $jarPath -Destination $inputDirectory

& $packager --type app-image --name Password123 --app-version 1.0.0 `
    --vendor DurdenCorp --description 'Password manager locale' `
    --input $inputDirectory --main-jar ([IO.Path]::GetFileName($jarPath)) `
    --main-class org.springframework.boot.loader.JarLauncher `
    --runtime-image $runtimePath --dest $destination
if ($LASTEXITCODE -ne 0) { throw "jpackage non riuscito (codice $LASTEXITCODE)." }
Write-Host "Applicazione creata: $appDirectory\Password123.exe"
Write-Host 'Per distribuire il programma copiare tutta la cartella Password123, incluso il runtime.'
