@echo off
echo ========================================
echo VS CODE HARDWARE ACCELERATION DISABLE
echo ========================================
echo.

echo Test 1: Disable GPU completely
echo Command: code --disable-gpu --disable-gpu-sandbox
echo.
pause

echo Test 2: Disable additional rendering features
echo Command: code --disable-gpu --disable-software-rasterizer --disable-dev-shm-usage
echo.
pause

echo Test 3: Force software rendering
echo Command: code --disable-gpu --disable-gpu-sandbox --disable-software-rasterizer --use-gl=swiftshader
echo.
pause

echo Test 4: Windows-specific workaround
echo Command: code --disable-gpu --disable-gpu-sandbox --disable-features=VizDisplayCompositor
echo.
pause

echo Test each option and report which one works best!
echo If none work, the issue is deeper in the system.
pause
