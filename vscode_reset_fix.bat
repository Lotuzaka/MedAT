@echo off
echo ========================================
echo VS CODE CACHE RESET & SYSTEM FIXES
echo ========================================
echo.

echo WARNUNG: Das wird VS Code Settings zurücksetzen!
echo Backup deine wichtigen Einstellungen zuerst.
echo.
pause

echo Step 1: Close VS Code completely
taskkill /f /im Code.exe 2>nul
timeout /t 3

echo Step 2: Clear VS Code caches
echo Clearing workspace storage...
if exist "%APPDATA%\Code\User\workspaceStorage" (
    rd /s /q "%APPDATA%\Code\User\workspaceStorage"
    echo Workspace storage cleared
)

echo Clearing logs...
if exist "%APPDATA%\Code\logs" (
    rd /s /q "%APPDATA%\Code\logs"
    echo Logs cleared
)

echo Clearing crash reports...
if exist "%APPDATA%\Code\CrashPads" (
    rd /s /q "%APPDATA%\Code\CrashPads"
    echo Crash reports cleared
)

echo Step 3: GPU Registry Reset (Advanced)
echo This resets Windows GPU settings for VS Code
reg add "HKEY_CURRENT_USER\Software\Microsoft\DirectX\UserGpuPreferences" /v "C:\Users\%USERNAME%\AppData\Local\Programs\Microsoft VS Code\Code.exe" /t REG_SZ /d "GpuPreference=1;" /f 2>nul

echo Step 4: Test with fresh profile
echo Creating test with isolated profile...
pause

echo Starting VS Code with clean profile...
code --user-data-dir="%TEMP%\vscode-test-profile"

echo.
echo TEST: Does focus switching work better with clean profile?
echo If YES: Settings/Extensions issue
echo If NO: System-level problem
echo.
pause

echo Step 5: Windows Display Settings Check
echo Check Windows Settings > Display > Graphics Settings
echo Add VS Code and set to "High Performance"
echo.
pause

echo Step 6: Alternative: Try VS Code Insiders
echo Download from: https://code.visualstudio.com/insiders/
echo Often has fixes for known issues
pause
