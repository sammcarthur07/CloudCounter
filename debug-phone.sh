#!/bin/bash

# Live debugging script for Cloud Counter app on phone

echo "====================================="
echo "Cloud Counter Live Debug Monitor"
echo "====================================="
echo ""
echo "Choose what to monitor:"
echo "1) All app logs"
echo "2) Google Sign-In issues"
echo "3) Firebase authentication"
echo "4) Errors only"
echo "5) Network requests"
echo "6) Custom grep pattern"
echo "7) Clear logcat and start fresh"
echo ""
read -p "Select option (1-7): " choice

case $choice in
    1)
        echo "Monitoring all app logs..."
        adb logcat | grep --line-buffered "com.sam.cloudcounter"
        ;;
    2)
        echo "Monitoring Google Sign-In..."
        adb logcat | grep --line-buffered -E "GoogleSignIn|AuthUI|Firebase.*Auth|SignIn|OAuth|GoogleAuth|Developer error|SHA|fingerprint"
        ;;
    3)
        echo "Monitoring Firebase auth..."
        adb logcat | grep --line-buffered -E "FirebaseAuth|FirebaseUser|Firebase.*Auth|authentication|sign.?in"
        ;;
    4)
        echo "Monitoring errors only..."
        adb logcat *:E | grep --line-buffered "com.sam.cloudcounter"
        ;;
    5)
        echo "Monitoring network..."
        adb logcat | grep --line-buffered -E "OkHttp|Retrofit|Firebase.*Network|Network"
        ;;
    6)
        read -p "Enter grep pattern: " pattern
        echo "Monitoring for: $pattern"
        adb logcat | grep --line-buffered -E "$pattern"
        ;;
    7)
        echo "Clearing logcat..."
        adb logcat -c
        echo "Logcat cleared! Starting fresh monitoring..."
        adb logcat | grep --line-buffered "com.sam.cloudcounter"
        ;;
    *)
        echo "Invalid option"
        ;;
esac