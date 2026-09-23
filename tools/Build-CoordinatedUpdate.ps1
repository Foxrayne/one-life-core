[CmdletBinding()]
param(
    [Parameter(Mandatory=$true)][string]$GameJar,
    [string]$WorkshopRoot
)
$ErrorActionPreference = 'Stop'
$coreRoot = Split-Path $PSScriptRoot -Parent
if (-not $WorkshopRoot) { $WorkshopRoot = Split-Path $coreRoot -Parent }
$expectedGameHash = '2BCE6DC1FE23FE8C475045276506A5EC1DA5257FD146F9F8B072251E953C5A10'
$GameJar = (Resolve-Path -LiteralPath $GameJar).Path
if ((Get-FileHash -LiteralPath $GameJar -Algorithm SHA256).Hash -ne $expectedGameHash) {
    throw 'Not the audited updated server JAR. Review compatibility before building another version.'
}

function Run-Checked([string]$Executable, [string[]]$Arguments) {
    & $Executable @Arguments
    if ($LASTEXITCODE -ne 0) { throw "$Executable failed with exit code $LASTEXITCODE" }
}
function Run-ModScript([string]$Relative, [string[]]$Arguments) {
    $scriptFile = Join-Path $WorkshopRoot $Relative
    # Existing test suites resolve their Lua fixtures relative to their project.
    Push-Location (Split-Path (Split-Path $scriptFile -Parent) -Parent)
    try {
        Run-Checked 'powershell.exe' (@('-NoProfile', '-File', $scriptFile) + $Arguments)
    } finally { Pop-Location }
}

Push-Location $coreRoot
try {
    # Only local verification/assembly. No installStorm, upload, publish or deploy tasks.
    Run-Checked '.\gradlew.bat' @('test', 'clientPatchTest', ':launcher:test',
        'stageOneLifeUpdate', "-PgameJar=$GameJar", '-PofflineTests',
        '-Penv=prod', '-PskipSigning', '--console=plain')
    $coreJar = Join-Path $coreRoot 'build/libs/storm-42.20.4_2.11.0.jar'
    $inputs = @('-GameJar', $GameJar, '-CoreJar', $coreJar)
    Run-ModScript 'World Essentials/java/build.ps1' ($inputs + @('-CoreRoot', $coreRoot, '-Package'))
    Run-ModScript 'Server Leaderboard/tools/Build-Java.ps1' ($inputs + @('-CoreRoot', $coreRoot))
    Run-ModScript 'Server Economy/tools/build-java.ps1' ($inputs + @('-CoreRoot', $coreRoot))
    Run-ModScript 'Zone Director/tools/Build-Java.ps1' $inputs
    Run-ModScript 'Zone Director/tools/Test-Java.ps1' $inputs
    Run-ModScript 'Random Events/tools/build-java.ps1' ($inputs + @('-CoreRoot', $coreRoot))
    Run-ModScript 'Server Economy/tools/validate-server-economy.ps1' @()

    $weJars = @(Get-ChildItem -LiteralPath (Join-Path $WorkshopRoot 'World Essentials/Contents') -Recurse -Filter '*.jar')
    if ($weJars.Count -ne 1) { throw 'Expected exactly one World Essentials Java artifact' }
    $classpath = "$GameJar;$coreJar;$($weJars[0].FullName);$(Join-Path $coreRoot 'build/install/storm/lib/*')"
    Run-Checked 'java' @('-cp', $classpath, (Join-Path $PSScriptRoot 'VerifyPackagedRespawn.java'))

    $stage = Join-Path $coreRoot 'build/local-update'
    foreach ($mod in @('World Essentials', 'Server Leaderboard', 'Server Economy', 'Zone Director', 'Random Events')) {
        $sourceRoot = Join-Path $WorkshopRoot $mod
        $jars = @(Get-ChildItem -LiteralPath (Join-Path $sourceRoot 'Contents') -Recurse -Filter '*.jar')
        if ($jars.Count -eq 0) { throw "No packaged Java artifact in $mod" }
        foreach ($artifact in $jars) {
            $relative = $artifact.FullName.Substring($sourceRoot.Length).TrimStart('\','/')
            $destination = Join-Path $stage (Join-Path 'Java-mod-artifacts' (Join-Path $mod $relative))
            New-Item -ItemType Directory -Path (Split-Path $destination -Parent) -Force | Out-Null
            Copy-Item -LiteralPath $artifact.FullName -Destination $destination -Force
        }
    }
    $artifacts = @(Get-ChildItem -LiteralPath $stage -Recurse -File |
        Where-Object { $_.Extension -eq '.jar' } | ForEach-Object {
            [ordered]@{ path=$_.FullName.Substring($stage.Length).TrimStart('\','/');
                sha256=(Get-FileHash -LiteralPath $_.FullName -Algorithm SHA256).Hash.ToLowerInvariant() }
        })
    $manifest = [ordered]@{
        upstream='c1d0ec3043caf9fa6d237bd05eb3abc797d7d034'; core='2.11.0'; launcher='1.2.7';
        gameSha256=$expectedGameHash.ToLowerInvariant();
        nativeSha256='baa9213172885e82310a40886359fd831c766dc726469f60dd75831a51a2bbe0';
        liveServerTested=$false; published=$false; artifacts=$artifacts
    }
    [IO.File]::WriteAllText((Join-Path $stage 'artifact-manifest.json'),
        ($manifest | ConvertTo-Json -Depth 8), [Text.UTF8Encoding]::new($false))
    Write-Host "Local update verified and staged: $stage. Nothing published."
} finally { Pop-Location }
