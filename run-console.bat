@echo off
rem ============================================
rem  Java Quiz - console (text) mode for Windows
rem  Use this if you prefer typing answers in a terminal.
rem ============================================
title Java Quiz (Console)
cd /d "%~dp0"

where javac >nul 2>nul
if errorlevel 1 (
    echo  [!] JDK not found. Install from https://adoptium.net
    pause
    exit /b 1
)

javac --release 8 *.java || (echo  [!] Compilation failed & pause & exit /b 1)

java QuizApp questions.csv
echo.
pause
