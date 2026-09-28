#!/usr/bin/env bash
# ============================================
#  Java Quiz - one-click launcher for Mac/Linux
#  Run with:  ./run.sh   (opens the graphical quiz)
# ============================================
cd "$(dirname "$0")"

if ! command -v javac >/dev/null 2>&1; then
    echo
    echo " [!] Java Development Kit (JDK) not found."
    echo "     Install it from https://adoptium.net"
    echo "     or:  sudo apt install default-jdk   (Ubuntu/Debian)"
    echo "          brew install --cask temurin    (macOS)"
    echo
    exit 1
fi

echo "Compiling..."
javac --release 8 *.java || exit 1

java QuizGui questions.csv
