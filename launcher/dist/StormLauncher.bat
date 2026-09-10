@echo off
setlocal
rem One/Life Core — pre-game UI for joining servers and syncing Java mods.
rem When this script lives inside the Steam workshop item
rem (steamapps\workshop\content\108600\<id>\mods\storm\launcher\) the game install
rem sits seven directories up in the same Steam library; use its bundled JRE.
set "PZ=%~dp0..\..\..\..\..\..\..\common\ProjectZomboid"
if exist "%PZ%\jre64\bin\javaw.exe" (
  start "One/Life Core" "%PZ%\jre64\bin\javaw.exe" -jar "%~dp0storm-launcher.jar" %*
  exit /b
)
start "One/Life Core" javaw -jar "%~dp0storm-launcher.jar" %*
