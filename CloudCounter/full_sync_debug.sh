#!/bin/bash

# Full Font/Color Sync Debug - Shows exactly what's happening
echo "🔍 Full Font/Color Sync Debug"
echo "============================="
echo ""
echo "Tracking font/color values between views:"
echo ""

adb logcat -c
adb logcat | grep -E "(MainActivity|GiantCounter)" | while read -r line; do
    # MainActivity saving before launch
    if echo "$line" | grep -q "MainActivity.*Saving spinner color:"; then
        color=$(echo "$line" | grep -oE "color: -?[0-9]+" | cut -d' ' -f2)
        echo -e "\033[1;32m[MAIN→GIANT]\033[0m Saving color: $color"
    elif echo "$line" | grep -q "MainActivity.*Saving spinner font index:"; then
        font=$(echo "$line" | grep -oE "index: [0-9]+" | cut -d' ' -f2)
        echo -e "\033[1;32m[MAIN→GIANT]\033[0m Saving font: $font"
    
    # GiantCounter loading values
    elif echo "$line" | grep -q "GiantCounter.*spinnerColor:"; then
        color=$(echo "$line" | grep -oE "spinnerColor: -?[0-9]+" | cut -d' ' -f2)
        echo -e "\033[1;36m[GIANT LOAD]\033[0m Loaded spinner color: $color"
    elif echo "$line" | grep -q "GiantCounter.*spinnerFontIndex:"; then
        font=$(echo "$line" | grep -oE "spinnerFontIndex: -?[0-9]+" | cut -d' ' -f2)
        echo -e "\033[1;36m[GIANT LOAD]\033[0m Loaded spinner font: $font"
    
    # GiantCounter using values
    elif echo "$line" | grep -q "GiantCounter.*Using spinner color:"; then
        color=$(echo "$line" | grep -oE "color: -?[0-9]+" | cut -d' ' -f2)
        echo -e "\033[1;33m[GIANT USE]\033[0m Using spinner color: $color"
    elif echo "$line" | grep -q "GiantCounter.*Using spinner font index:"; then
        font=$(echo "$line" | grep -oE "index: [0-9]+" | cut -d' ' -f2)
        echo -e "\033[1;33m[GIANT USE]\033[0m Using spinner font: $font"
    elif echo "$line" | grep -q "GiantCounter.*Using locked color:"; then
        color=$(echo "$line" | grep -oE "color: -?[0-9]+" | cut -d' ' -f2)
        echo -e "\033[1;33m[GIANT USE]\033[0m Using LOCKED color: $color"
    elif echo "$line" | grep -q "GiantCounter.*Using locked font"; then
        echo -e "\033[1;33m[GIANT USE]\033[0m Using LOCKED font"
    
    # GiantCounter saving for return
    elif echo "$line" | grep -q "GiantCounter.*Saving display color:"; then
        color=$(echo "$line" | grep -oE "color: -?[0-9]+" | cut -d' ' -f2)
        echo -e "\033[1;31m[GIANT SAVE]\033[0m Display color: $color"
    elif echo "$line" | grep -q "GiantCounter.*Saving display font index:"; then
        font=$(echo "$line" | grep -oE "index: [0-9]+" | cut -d' ' -f2)
        echo -e "\033[1;31m[GIANT SAVE]\033[0m Display font: $font"
    
    # MainActivity loading from GiantCounter
    elif echo "$line" | grep -q "MainActivity.*Reloading.*from GiantCounter"; then
        echo -e "\033[1;35m[MAIN←GIANT]\033[0m" $(echo "$line" | grep -oE "color: -?[0-9]+, fontIndex: [0-9]+")
    
    # Lock states
    elif echo "$line" | grep -q "randomFontsEnabled:"; then
        echo -e "\033[1;34m[LOCK STATE]\033[0m" $(echo "$line" | grep -oE "randomFontsEnabled: [a-z]+")
    elif echo "$line" | grep -q "colorChangingEnabled:"; then
        echo -e "\033[1;34m[LOCK STATE]\033[0m" $(echo "$line" | grep -oE "colorChangingEnabled: [a-z]+")
    fi
done