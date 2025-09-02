#!/bin/bash

# Script to get local debug SHA-1 fingerprint

echo "========================================="
echo "LOCAL DEBUG SHA-1 FINGERPRINT"
echo "========================================="

# Check if debug.keystore exists
if [ -f ~/.android/debug.keystore ]; then
    echo "Debug keystore found!"
    echo ""
    echo "Your local SHA-1:"
    keytool -list -v -keystore ~/.android/debug.keystore \
        -alias androiddebugkey -storepass android -keypass android 2>/dev/null \
        | grep SHA1
    echo ""
else
    echo "Debug keystore not found at ~/.android/debug.keystore"
    echo "Run Android Studio once to generate it, or run:"
    echo "keytool -genkey -v -keystore ~/.android/debug.keystore -storepass android -alias androiddebugkey -keypass android -keyalg RSA -keysize 2048 -validity 10000"
fi

echo "========================================="
echo "HOW TO ADD TO FIREBASE:"
echo "========================================="
echo "1. Copy the SHA1 value above (everything after 'SHA1:')"
echo "2. Go to Firebase Console:"
echo "   https://console.firebase.google.com/project/cloudcounter-sam/settings/general"
echo "3. Scroll to 'Your apps' → Android app"
echo "4. Click 'Add fingerprint'"
echo "5. Paste the SHA1 (without spaces or colons)"
echo "6. Click Save"
echo ""
echo "You can add MULTIPLE SHA-1s:"
echo "- Your local debug SHA-1 (from this script)"
echo "- GitHub Actions SHA-1 (from GitHub workflow)"
echo "- Release SHA-1 (when you have a release key)"
echo "========================================="