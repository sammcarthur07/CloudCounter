#!/bin/bash

# Check SHA-1 of an installed APK or APK file

echo "========================================="
echo "APK SHA-1 Checker"
echo "========================================="
echo ""
echo "Choose method:"
echo "1) Check installed app on device (via adb)"
echo "2) Check APK file"
echo "3) Check what's currently in Firebase"
echo ""
read -p "Select option: " choice

case $choice in
    1)
        echo "Getting SHA-1 from installed app..."
        if command -v adb &> /dev/null; then
            # Get the APK path from device
            APK_PATH=$(adb shell pm path com.sam.cloudcounter | cut -d':' -f2 | tr -d '\r\n')
            if [ -n "$APK_PATH" ]; then
                echo "Found app at: $APK_PATH"
                # Pull the APK temporarily
                adb pull "$APK_PATH" temp_app.apk
                echo "Extracting certificate..."
                unzip -p temp_app.apk META-INF/*.RSA 2>/dev/null | keytool -printcert 2>/dev/null | grep SHA1 || \
                unzip -p temp_app.apk META-INF/*.DSA 2>/dev/null | keytool -printcert 2>/dev/null | grep SHA1 || \
                echo "Could not extract SHA-1"
                rm -f temp_app.apk
            else
                echo "App not found on device"
            fi
        else
            echo "adb not found"
        fi
        ;;
    2)
        read -p "Enter APK file path: " apk_file
        if [ -f "$apk_file" ]; then
            echo "Extracting SHA-1 from: $apk_file"
            unzip -p "$apk_file" META-INF/*.RSA 2>/dev/null | keytool -printcert 2>/dev/null | grep SHA1 || \
            unzip -p "$apk_file" META-INF/*.DSA 2>/dev/null | keytool -printcert 2>/dev/null | grep SHA1 || \
            echo "Could not extract SHA-1"
        else
            echo "File not found: $apk_file"
        fi
        ;;
    3)
        echo "Current SHA-1s in Firebase (from google-services.json):"
        echo "1. 390a458fdfc7c18739d20da26b8b08b970060303"
        echo "2. 9f62f491a8a7b24e257e4dbb45576b6f6bec36b8" 
        echo "3. 2771559520eeb24d0b8f375e4a8e75d96918143b"
        echo ""
        echo "Compare these with the SHA-1 from your APK above"
        ;;
    *)
        echo "Invalid option"
        ;;
esac

echo ""
echo "========================================="
echo "If SHA-1s don't match, you need to:"
echo "1. Add the ACTUAL APK SHA-1 to Firebase Console"
echo "2. Download new google-services.json" 
echo "3. Rebuild the app"
echo "========================================="