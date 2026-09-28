#!/usr/bin/env bash
# ============================================
#  Java Quiz - console (text) mode for Mac/Linux
#  Run with:  ./run-console.sh
# ============================================
cd "$(dirname "$0")"

if ! command -v javac >/dev/null 2>&1; then
    echo " [!] JDK not found. Install from https://adoptium.net"
    exit 1
fi

javac --release 8 *.java || exit 1
java QuizApp questions.csv
