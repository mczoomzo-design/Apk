@echo off
REM ============================================================
REM  build_exe.bat  -  สร้างไฟล์ .exe ของ Epson Waste Ink Tool
REM  รันบน Windows (ดับเบิลคลิกไฟล์นี้)
REM ============================================================
chcp 65001 >nul
cd /d "%~dp0"

echo [1/3] ติดตั้ง/อัปเดตไลบรารีที่ต้องใช้ (ez-reset + pyinstaller)...
python -m pip install --upgrade pip >nul
python -m pip install pyinstaller
python -m pip install git+https://github.com/CiRIP/ez-reset
if errorlevel 1 (
    echo.
    echo ** ติดตั้งไลบรารีไม่สำเร็จ - ตรวจว่าติดตั้ง Python และ Git แล้ว **
    pause
    exit /b 1
)

echo.
echo [2/3] กำลัง build เป็น .exe (แบบไฟล์เดียว, หน้าต่าง GUI)...
python -m PyInstaller ^
    --noconfirm ^
    --onefile ^
    --windowed ^
    --name "EpsonWasteInkTool" ^
    --collect-data ez_reset ^
    epson_waste_tool.py
if errorlevel 1 (
    echo.
    echo ** build ไม่สำเร็จ **
    pause
    exit /b 1
)

echo.
echo [3/3] เสร็จแล้ว! ไฟล์อยู่ที่:  dist\EpsonWasteInkTool.exe
echo (ดับเบิลคลิกเปิดใช้ได้เลย ไม่ต้องมี Python)
echo.
pause
