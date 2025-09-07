#!/bin/bash

# Simple font/color log command
echo "🎨 Font/Color Debug for Giant Counter"
echo "====================================="
echo ""
echo "Filter: 🔤 font | 🎨 color | 🔒 lock | 👆 tap | 💾 save"
echo ""

adb logcat -c
adb logcat | grep -E "GiantCounter.*(\[GIANT\]|🔤|🎨|🔒|🔓|👆|💾|spinnerFontIndex|spinnerColor|colorChangingEnabled|randomFontsEnabled|Rotation:|Tap:|Using)" --color=always