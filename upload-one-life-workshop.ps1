$ErrorActionPreference = 'Stop'

$steamCmd = 'C:\Users\matth\SteamCMD\steamcmd.exe'
$workshopVdf = Join-Path $PSScriptRoot 'build\workshop.vdf'

if (-not (Test-Path -LiteralPath $steamCmd)) {
    throw "SteamCMD was not found at $steamCmd"
}

if (-not (Test-Path -LiteralPath $workshopVdf)) {
    throw "Workshop definition was not found at $workshopVdf"
}

Write-Host 'Publishing One/Life Core as xpcry (Steam visibility: unlisted)...' -ForegroundColor Magenta
Write-Host 'Enter Steam credentials only in this SteamCMD window.' -ForegroundColor Yellow

$uploadExitCode = 1
do {
    & $steamCmd +login xpcry +workshop_build_item $workshopVdf +quit
    $uploadExitCode = $LASTEXITCODE
    $workshopDefinition = Get-Content -LiteralPath $workshopVdf -Raw
    $publishedIdMatch = [regex]::Match($workshopDefinition, '"publishedfileid"\s+"(?<id>\d+)"')
    $publishedId = if ($publishedIdMatch.Success) { $publishedIdMatch.Groups['id'].Value } else { '0' }

    if ($uploadExitCode -eq 0 -and $publishedId -ne '0') {
        Write-Host "SteamCMD published One/Life Core successfully as Workshop item $publishedId." -ForegroundColor Green
        break
    }

    Write-Host 'The login or initial upload did not complete.' -ForegroundColor Red
    $retry = Read-Host 'Press Enter to try the password again, or type Q to quit'
    if ($retry -eq 'q') {
        break
    }
} while ($true)

if ($publishedId -eq '0') {
    Write-Host 'No Workshop item was created.' -ForegroundColor Yellow
}

Read-Host 'Press Enter to close this window'
exit $uploadExitCode
