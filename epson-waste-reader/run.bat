@echo off
REM ตัวช่วยรันบน Windows - อ่านสถานะซับหมึก Epson ผ่าน USB (อ่านอย่างเดียว)
chcp 65001 >nul
cd /d "%~dp0"
python check_waste_ink.py %*
echo.
pause
