@echo off
setlocal

set "ROOT=%~dp0"
set "GUI_DIR=%ROOT%tools\bridge-gui"

if not exist "%GUI_DIR%\package.json" (
    echo Bridge GUI package was not found: "%GUI_DIR%"
    pause
    exit /b 1
)

where npm.cmd >nul 2>nul
if errorlevel 1 (
    echo npm.cmd was not found. Install Node.js first.
    pause
    exit /b 1
)

if not defined DABOYEO_BRIDGE_PYTHON (
    if exist "%USERPROFILE%\.cache\codex-runtimes\codex-primary-runtime\dependencies\python\python.exe" (
        set "DABOYEO_BRIDGE_PYTHON=%USERPROFILE%\.cache\codex-runtimes\codex-primary-runtime\dependencies\python\python.exe"
    )
)

if not defined DABOYEO_CODEX_COMMAND (
    if exist "%LOCALAPPDATA%\OpenAI\Codex\bin\codex.exe" (
        set "DABOYEO_CODEX_COMMAND=%LOCALAPPDATA%\OpenAI\Codex\bin\codex.exe"
    )
)

cd /d "%GUI_DIR%"
if errorlevel 1 (
    echo Failed to enter "%GUI_DIR%"
    pause
    exit /b 1
)

if not exist "node_modules\electron" (
    echo Installing Electron dependencies...
    call npm.cmd install
    if errorlevel 1 (
        echo npm install failed.
        pause
        exit /b 1
    )
)

echo Starting DABOYEO Bridge GUI...
call npm.cmd start
if errorlevel 1 (
    echo Bridge GUI exited with an error.
    pause
    exit /b 1
)

endlocal
