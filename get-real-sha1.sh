#!/bin/bash

# Get the ACTUAL SHA-1 from the installed app on your phone

echo "========================================="
echo "Getting REAL SHA-1 from your phone"
echo "========================================="

# Check if device is connected
if ! adb devices | grep -q "device$"; then
    echo "❌ No device connected via ADB"
    echo "Make sure:"
    echo "1. Phone connected via USB"
    echo "2. USB Debugging enabled"
    echo "3. Computer authorized on phone"
    exit 1
fi

echo "✅ Device connected"
echo ""
echo "Getting APK path from device..."

# Get the APK path from the installed app
APK_PATH=$(adb shell pm path com.sam.cloudcounter 2>/dev/null | head -1 | cut -d':' -f2 | tr -d '\r\n')

if [ -z "$APK_PATH" ]; then
    echo "❌ CloudCounter app not found on device"
    echo "Make sure the app is installed"
    exit 1
fi

echo "✅ Found app at: $APK_PATH"
echo ""
echo "Pulling APK from device..."

# Pull the APK from device
adb pull "$APK_PATH" temp_cloudcounter.apk >/dev/null 2>&1

if [ ! -f "temp_cloudcounter.apk" ]; then
    echo "❌ Failed to pull APK"
    exit 1
fi

echo "✅ APK pulled successfully"
echo ""
echo "Extracting SHA-1 certificate..."

# Extract the SHA-1 from the APK
SHA1=$(unzip -p temp_cloudcounter.apk META-INF/*.RSA 2>/dev/null | keytool -printcert 2>/dev/null | grep "SHA1:" | head -1)

if [ -z "$SHA1" ]; then
    # Try DSA format
    SHA1=$(unzip -p temp_cloudcounter.apk META-INF/*.DSA 2>/dev/null | keytool -printcert 2>/dev/null | grep "SHA1:" | head -1)
fi

if [ -z "$SHA1" ]; then
    echo "❌ Could not extract SHA-1 from APK"
    rm -f temp_cloudcounter.apk
    exit 1
fi

# Clean up
rm -f temp_cloudcounter.apk

# Extract just the hex part
SHA1_HEX=$(echo "$SHA1" | sed 's/.*SHA1: //' | tr -d ' :' | tr '[:upper:]' '[:lower:]')

echo "========================================="
echo "🎯 FOUND THE REAL SHA-1!"
echo "========================================="
echo ""
echo "Full line: $SHA1"
echo ""
echo "📋 COPY THIS SHA-1:"
echo "   $SHA1_HEX"
echo ""
echo "========================================="
echo "NEXT STEPS:"
echo "========================================="
echo "1. Copy the SHA-1 above: $SHA1_HEX"
echo ""
echo "2. Go to Firebase Console:"
echo "   https://console.firebase.google.com/project/cloudcounter-sam/settings/general"
echo ""
echo "3. Find your Android app (com.sam.cloudcounter)"
echo ""
echo "4. In 'SHA certificate fingerprints' section:"
echo "   - Click 'Add fingerprint'"
echo "   - Paste: $SHA1_HEX"
echo "   - Click Save"
echo ""
echo "5. Download the NEW google-services.json"
echo ""
echo "6. Replace CloudCounter/app/google-services.json"
echo ""
echo "7. Push to GitHub and rebuild"
echo ""
echo "========================================="

# Compare with existing SHA-1s
echo "Current SHA-1s in your google-services.json:"
echo "• 390a458fdfc7c18739d20da26b8b08b970060303"
echo "• 9f62f491a8a7b24e257e4dbb45576b6f6bec36b8"
echo "• 2771559520eeb24d0b8f375e4a8e75d96918143b"
echo ""
echo "Your APK SHA-1:"
echo "• $SHA1_HEX"
echo ""

if grep -q "$SHA1_HEX" CloudCounter/app/google-services.json 2>/dev/null; then
    echo "✅ This SHA-1 is already in your google-services.json"
    echo "❌ But it's still not working - there might be another issue"
else
    echo "❌ This SHA-1 is NOT in your google-services.json"
    echo "🔧 You MUST add this to Firebase Console!"
fi

echo ""
echo "========================================="