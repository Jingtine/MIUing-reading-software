param([string]$Jdk = 'D:\Software\JDK-17')
$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path -Parent $PSScriptRoot
$classes = Join-Path $repoRoot 'core/build/manual-tests'
New-Item -ItemType Directory -Force -Path $classes | Out-Null
$sources = @(Get-ChildItem -LiteralPath "$repoRoot/core/src" -Recurse -Filter '*.java' | ForEach-Object FullName)
& "$Jdk/bin/javac.exe" -encoding UTF-8 -d $classes @sources
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
& "$Jdk/bin/java.exe" -cp $classes com.miuing.core.PublicationParserTest
exit $LASTEXITCODE
