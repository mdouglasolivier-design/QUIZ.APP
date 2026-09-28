@echo off
rem ============================================
rem  Java Quiz - one-click launcher for Windows
rem  Double-click this file: the graphical quiz opens.
rem ============================================
title Java Quiz
cd /d "%~dp0"

where javac >nul 2>nul
if errorlevel 1 (
    echo.
    echo  [!] Java Development Kit (JDK) not found.
    echo      Install it from https://adoptium.net ^(choose "Temurin JDK"^),
    echo      then reopen this file.
    echo.
    pause
    exit /b 1
)

echo Compiling...
javac --release 8 *.java
if errorlevel 1 (
    echo.
    echo  [!] Compilation failed. Check the .java files for errors.
    pause
    exit /b 1
)

java QuizGui questions.csv
