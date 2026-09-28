@echo off
rem ============================================
rem  Build a distributable QuizApp.jar (Windows)
rem  After this, run with:  java -jar QuizApp.jar   (graphical quiz)
rem ============================================
cd /d "%~dp0"

where javac >nul 2>nul
if errorlevel 1 (
    echo  [!] JDK not found. Install from https://adoptium.net
    pause
    exit /b 1
)

javac --release 8 *.java || (echo  [!] Compilation failed & pause & exit /b 1)
jar cfe QuizApp.jar QuizGui *.class questions.csv
echo.
echo  Done! QuizApp.jar created.
echo  Run it with:   java -jar QuizApp.jar
pause
