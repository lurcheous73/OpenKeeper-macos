# Build a self-contained OpenKeeper Windows x64 portable package using JDK jpackage.
$ErrorActionPreference = 'Stop'

$ProjectDir = if ($env:OPENKEEPER_PROJECT_DIR) { $env:OPENKEEPER_PROJECT_DIR } else { (Get-Location).Path }
$AppName = 'OpenKeeper'
$MainJar = if ($env:OPENKEEPER_MAIN_JAR) { $env:OPENKEEPER_MAIN_JAR } else { 'OpenKeeper.jar' }
$MainClass = if ($env:OPENKEEPER_MAIN_CLASS) { $env:OPENKEEPER_MAIN_CLASS } else { 'toniarts.openkeeper.Main' }
$InputDir = Join-Path $ProjectDir "build\install\$AppName\lib"
$OutputDir = Join-Path $ProjectDir 'build\windows'
$JPackage = Join-Path $env:JAVA_HOME 'bin\jpackage.exe'

if (-not $env:JAVA_HOME -or -not (Test-Path $JPackage)) {
    throw "jpackage.exe was not found. A full JDK 25 is required (JAVA_HOME=$env:JAVA_HOME)."
}
if (-not (Test-Path $InputDir)) { throw "installDist input directory does not exist: $InputDir" }
if (-not (Test-Path (Join-Path $InputDir $MainJar))) { throw "OpenKeeper main JAR does not exist: $MainJar" }

Remove-Item $OutputDir -Recurse -Force -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Force -Path $OutputDir | Out-Null

Write-Host 'Building native Windows x64 app image'
Write-Host "  Java:       $env:JAVA_HOME"
Write-Host "  main JAR:   $MainJar"
Write-Host "  main class: $MainClass"

& $JPackage `
    --type app-image `
    --name $AppName `
    --dest $OutputDir `
    --input $InputDir `
    --main-jar $MainJar `
    --main-class $MainClass `
    --vendor 'OpenKeeper' `
    --description 'Open source Dungeon Keeper II engine' `
    --java-options '-Dvisualvm.display.name=OpenKeeper'
if ($LASTEXITCODE -ne 0) { throw "jpackage failed with exit code $LASTEXITCODE" }

$AppDir = Join-Path $OutputDir $AppName
$Exe = Join-Path $AppDir "$AppName.exe"
if (-not (Test-Path $Exe)) { throw "jpackage completed without producing $Exe" }

$Zip = Join-Path $OutputDir 'OpenKeeper-Windows-x86_64.zip'
Compress-Archive -Path $AppDir -DestinationPath $Zip -CompressionLevel Optimal -Force
$Hash = (Get-FileHash -Algorithm SHA256 $Zip).Hash.ToLowerInvariant()
"$Hash  OpenKeeper-Windows-x86_64.zip" | Set-Content -Encoding ascii "$Zip.sha256"
Write-Host "Built: $Zip"
