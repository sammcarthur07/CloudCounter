#!/bin/bash

# Quick sync check - shows only the critical sync points
echo "🔍 Quick Font/Color Sync Check"
echo "=============================="
echo ""
echo "Watch these key events:"
echo "1. [SAVE] MainActivity saves before launching Giant"
echo "2. [LOAD] GiantCounter loads on startup" 
echo "3. [SAVE] GiantCounter saves on changes"
echo "4. [LOAD] MainActivity loads when Giant closes"
echo ""

adb logcat -c
adb logcat | grep -E "(Saving spinner|Saving lock states|Spinner state from prefs|Reloaded lock states|Saving display|Reloading font/color from GiantCounter)" | while read -r line; do
    if echo "$line" | grep -q "MainActivity.*Saving spinner"; then
        echo -e "\033[1;32m[1.MAIN SAVE]\033[0m" $(echo "$line" | grep -oE "(Saving spinner.*)")
    elif echo "$line" | grep -q "MainActivity.*Saving lock states"; then
        echo -e "\033[1;33m[1.LOCK SAVE]\033[0m" $(echo "$line" | grep -oE "(randomFontsEnabled: [a-z]+|colorChangingEnabled: [a-z]+)")
    elif echo "$line" | grep -q "GiantCounter.*Spinner state"; then
        echo -e "\033[1;36m[2.GIANT LOAD]\033[0m" $(echo "$line" | grep -oE "(spinnerFontIndex: -?[0-9]+|spinnerColor: -?[0-9]+)")
    elif echo "$line" | grep -q "GiantCounter.*Saving display"; then
        echo -e "\033[1;31m[3.GIANT SAVE]\033[0m" $(echo "$line" | grep -oE "(Saving display.*)")
    elif echo "$line" | grep -q "MainActivity.*Reloading.*from GiantCounter"; then
        echo -e "\033[1;35m[4.MAIN LOAD]\033[0m" $(echo "$line" | grep -oE "(color: -?[0-9]+|fontIndex: -?[0-9]+)")
    elif echo "$line" | grep -q "MainActivity.*Reloaded lock states"; then
        echo -e "\033[1;33m[4.LOCK LOAD]\033[0m" $(echo "$line" | grep -oE "(randomFontsEnabled: [a-z]+|colorChangingEnabled: [a-z]+)")
    fi
done