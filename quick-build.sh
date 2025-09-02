#!/data/data/com.termux/files/usr/bin/bash

# Quick Build Script for CloudCounter APK
# This will trigger the build and email you the APK

echo "🚀 Triggering CloudCounter APK build..."
echo "📧 Will email APK to: mcarthur.sp@gmail.com"
echo ""

# Get GitHub token
TOKEN=$(gh config get -h github.com oauth_token 2>/dev/null)

if [ -z "$TOKEN" ]; then
    echo "❌ Error: GitHub CLI not authenticated"
    echo "Run: gh auth login"
    exit 1
fi

# Trigger debug build
echo "Building Debug APK..."
RESPONSE=$(curl -X POST \
    -H "Accept: application/vnd.github.v3+json" \
    -H "Authorization: token $TOKEN" \
    "https://api.github.com/repos/sammcarthur07/CloudCounter/actions/workflows/firebase-build.yml/dispatches" \
    -d '{"ref": "main", "inputs": {"build_type": "debug", "distribution_notes": "Quick mobile build"}}' \
    -s -w "%{http_code}")

if [ "$RESPONSE" = "204" ]; then
    echo "✅ Build triggered successfully!"
    echo "📧 Check your email in 5-10 minutes for the APK"
    echo "🔗 Monitor: https://github.com/sammcarthur07/CloudCounter/actions"
else
    echo "❌ Build trigger failed (HTTP $RESPONSE)"
    echo "🔗 Try manually: https://github.com/sammcarthur07/CloudCounter/actions"
fi