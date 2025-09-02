#!/data/data/com.termux/files/usr/bin/bash

# Termux Debug Script for Cloud Counter
# This works directly on your phone in Termux

echo "========================================="
echo "Termux Debug for Cloud Counter"
echo "========================================="
echo ""

# Check if we have logcat access
echo "Testing logcat access..."
timeout 2 logcat -d -s "ActivityManager" | head -3

if [ $? -eq 0 ]; then
    echo "✅ Logcat access works!"
    echo ""
    echo "Choose debug option:"
    echo "1) Live Google Sign-In logs"
    echo "2) Live app crash logs" 
    echo "3) All app logs"
    echo "4) Test specific pattern"
    echo "5) Show last 50 app logs"
    echo ""
    read -p "Select: " opt
    
    case $opt in
        1)
            echo "🔍 Monitoring Google Sign-In (press Ctrl+C to stop)..."
            logcat -v time | grep -i -E "googleauth|firebase.*auth|sign.*in|oauth|developer.error"
            ;;
        2) 
            echo "🔍 Monitoring crashes (press Ctrl+C to stop)..."
            logcat -v time *:E *:F | grep -E "com.sam.cloudcounter|AndroidRuntime"
            ;;
        3)
            echo "🔍 Monitoring all app logs (press Ctrl+C to stop)..."
            logcat -v time | grep "com.sam.cloudcounter"
            ;;
        4)
            read -p "Enter pattern to search for: " pattern
            echo "🔍 Monitoring for: $pattern"
            logcat -v time | grep -i "$pattern"
            ;;
        5)
            echo "📋 Last 50 app-related logs:"
            logcat -d | grep -E "com.sam.cloudcounter|GoogleAuth|Firebase" | tail -50
            ;;
    esac
else
    echo "❌ Limited logcat access"
    echo ""
    echo "Alternative debugging methods:"
    echo "1) Use computer with USB debugging"
    echo "2) Check if Termux has READ_LOGS permission"
    echo "3) Try with root access (if rooted)"
    echo ""
    echo "To enable USB debugging:"
    echo "- Settings → Developer Options → USB Debugging"
    echo "- Connect to computer and use: adb logcat"
    echo ""
    echo "Quick test - try this command:"
    echo "su -c 'logcat | grep com.sam.cloudcounter'"
fi

echo ""
echo "========================================="
echo "Debugging Tips:"
echo "- Test sign-in AFTER starting monitoring"
echo "- Look for 'Developer error' or 'SHA' in logs"
echo "- If no logs appear, try USB debugging"
echo "========================================="