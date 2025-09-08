#!/bin/bash

# Font/Color Sync Debug Script
# Shows both MainActivity and GiantCounter font/color states

echo "🔍 Font/Color Sync Debugger"
echo "==========================="
echo ""
echo "Monitoring sync between MainActivity and GiantCounter"
echo "Press Ctrl+C to stop..."
echo ""

# Clear existing logs
adb logcat -c

# Monitor with detailed filtering
adb logcat | grep -E "(MainActivity|GiantCounter)" | while read -r line; do
    # MainActivity saving states before launching GiantCounter
    if echo "$line" | grep -q "MainActivity.*Saving.*spinner"; then
        echo -e "\033[1;32m[MAIN→GIANT SAVE]\033[0m $line"
    
    # MainActivity reading states after returning from GiantCounter
    elif echo "$line" | grep -q "MainActivity.*Reload.*from GiantCounter"; then
        echo -e "\033[1;35m[MAIN←GIANT LOAD]\033[0m $line"
    
    # Lock state saves/loads
    elif echo "$line" | grep -q "Saving lock states"; then
        echo -e "\033[1;33m[LOCK SAVE]\033[0m $line"
    elif echo "$line" | grep -q "Reloaded lock states"; then
        echo -e "\033[1;33m[LOCK LOAD]\033[0m $line"
    
    # GiantCounter initialization
    elif echo "$line" | grep -q "GiantCounter.*Spinner state from prefs"; then
        echo -e "\033[1;36m[GIANT INIT]\033[0m $line"
    elif echo "$line" | grep -q "GiantCounter.*spinnerFontIndex\\|spinnerColor"; then
        echo -e "\033[1;36m[GIANT STATE]\033[0m $line"
    
    # Display state saves
    elif echo "$line" | grep -q "Saving display"; then
        echo -e "\033[1;31m[DISPLAY SAVE]\033[0m $line"
    
    # Font/color changes
    elif echo "$line" | grep -q "current_spinner_color\\|current_spinner_font"; then
        echo -e "\033[1;34m[SPINNER UPDATE]\033[0m $line"
    elif echo "$line" | grep -q "giant_counter_color\\|giant_counter_font"; then
        echo -e "\033[1;34m[GIANT UPDATE]\033[0m $line"
    
    # Lock state changes
    elif echo "$line" | grep -q "randomFontsEnabled\\|colorChangingEnabled"; then
        echo -e "\033[1;33m[LOCK STATE]\033[0m $line"
    
    # Errors
    elif echo "$line" | grep -q "ERROR\\|Error"; then
        echo -e "\033[1;31m[ERROR]\033[0m $line"
    fi
done