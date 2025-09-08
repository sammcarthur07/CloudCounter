#!/bin/bash

# Debug script to track view state updates
echo "🔍 View State Debug"
echo "==================="
echo ""
echo "Tracking view updates in MainActivity"
echo ""

adb logcat -c
adb logcat | grep -E "MainActivity" | while read -r line; do
    # Track SmokerManager updates
    if echo "$line" | grep -q "Updated SmokerManager"; then
        echo -e "\033[1;32m[MANAGER UPDATE]\033[0m" $(echo "$line" | grep -oE "Updated SmokerManager.*")
    
    # Track view updates
    elif echo "$line" | grep -q "applied.*directly to current view"; then
        echo -e "\033[1;33m[VIEW UPDATE]\033[0m" $(echo "$line" | grep -oE "applied.*directly to current view")
    
    # Track spinner refresh
    elif echo "$line" | grep -q "Forcing spinner refresh"; then
        echo -e "\033[1;36m[SPINNER REFRESH]\033[0m Adapter notifyDataSetChanged called"
    
    # Track null views
    elif echo "$line" | grep -q "⚠️"; then
        echo -e "\033[1;31m[WARNING]\033[0m" $(echo "$line" | grep -oE "⚠️.*")
    
    # Track what's being saved
    elif echo "$line" | grep -q "Saving spinner.*from"; then
        echo -e "\033[1;35m[SAVE SOURCE]\033[0m" $(echo "$line" | grep -oE "from (view|manager)")
    fi
done