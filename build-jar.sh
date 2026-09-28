#!/usr/bin/env bash
# ============================================
#  Build a distributable QuizApp.jar (Mac/Linux)
#  After this, run with:  java -jar QuizApp.jar   (graphical quiz)
# ============================================
cd "$(dirname "$0")"

if ! command -v javac >/dev/null 2>&1; then
    echo " [!] JDK not found. Install from https://adoptium.net"
    exit 1
fi

javac --release 8 *.java || exit 1
jar cfe QuizApp.jar QuizGui *.class questions.csv
echo
echo " Done! QuizApp.jar created."
echo " Run it with:   java -jar QuizApp.jar"
