# VS Code Performance Debug Script
# Run this step by step to diagnose freezing issues

Write-Host "=== VS Code Performance Diagnosis Script ===" -ForegroundColor Green
Write-Host "Follow the steps in order and report results" -ForegroundColor Yellow

Write-Host "`n1. SAFE MODE TEST" -ForegroundColor Cyan
Write-Host "Running: code --disable-extensions"
Write-Host "Test focus switching in safe mode, then close VS Code and continue"
# Start-Process "code" -ArgumentList "--disable-extensions" -Wait

Write-Host "`n2. MINIMAL EXTENSIONS TEST" -ForegroundColor Cyan  
Write-Host "After safe mode test, enable ONLY these extensions:"
Write-Host "- redhat.java"
Write-Host "- github.copilot"
Write-Host "- github.copilot-chat"
Write-Host "Test focus switching again"

Write-Host "`n3. PROCESS MONITORING" -ForegroundColor Cyan
Write-Host "Current VS Code processes:"
Get-Process | Where-Object {$_.ProcessName -like "*code*"} | Select-Object ProcessName, Id, CPU, WorkingSet | Format-Table

Write-Host "`n4. FILE SIZE CHECK" -ForegroundColor Cyan
Write-Host "Target folder size:"
if (Test-Path "target") {
    $size = (Get-ChildItem "target" -Recurse -File | Measure-Object -Property Length -Sum).Sum / 1MB
    Write-Host "Target folder: $([math]::Round($size, 2)) MB"
} else {
    Write-Host "No target folder found"
}

Write-Host "`n5. EXTENSION HOST PROFILING" -ForegroundColor Cyan
Write-Host "In VS Code, run:"
Write-Host "Ctrl+Shift+P -> 'Developer: Profile Extension Host'"
Write-Host "Reproduce the focus issue for 1 minute, then stop profiling"

Write-Host "`n6. RUNNING EXTENSIONS CHECK" -ForegroundColor Cyan
Write-Host "In VS Code, run:"
Write-Host "Ctrl+Shift+P -> 'Developer: Show Running Extensions'"
Write-Host "Look for high Load Time and CPU usage"

Write-Host "`n7. GPU DISABLE TEST" -ForegroundColor Cyan
Write-Host "Test with GPU disabled:"
Write-Host "code --disable-gpu"

Write-Host "`nReport back with results from each step!" -ForegroundColor Green
