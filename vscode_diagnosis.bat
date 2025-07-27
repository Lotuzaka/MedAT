@echo off
echo ========================================
echo VS CODE PERFORMANCE DIAGNOSIS
echo ========================================
echo.

echo 1. SAFE MODE TEST
echo Run: code --disable-extensions
echo Test focus switching, then close and continue
pause

echo.
echo 2. MINIMAL EXTENSIONS TEST
echo Will start VS Code with only core Java extensions
echo Close current VS Code first!
pause

echo Starting VS Code with minimal extensions...
code --disable-extensions --enable-extension redhat.java --enable-extension vscjava.vscode-maven

echo.
echo Test focus switching now!
echo If better: Extension problem confirmed
echo If same: System/GPU problem
pause

echo.
echo 3. GPU DISABLE TEST
echo Testing without GPU acceleration...
code --disable-gpu

echo.
echo Test focus switching again!
pause

echo.
echo 4. PROCESS MONITORING SETUP
echo Run this in PowerShell while testing:
echo Get-Process Code ^| Sort-Object CPU -Descending ^| Select-Object Id, ProcessName, CPU, WorkingSet -First 5
echo.
echo 5. PROFILER INSTRUCTIONS
echo In VS Code: Ctrl+Shift+P
echo Type: Developer: Profile Extension Host
echo Start profiling, reproduce issue for 1 minute, stop
echo Screenshot the results!
echo.
echo 6. RUNNING EXTENSIONS CHECK
echo In VS Code: Ctrl+Shift+P  
echo Type: Developer: Show Running Extensions
echo Look for high Load Time and Activation Events
echo Screenshot this too!

echo.
echo DIAGNOSIS COMPLETE - Report results!
pause
