#!/bin/bash

# Font and Color Debug Script for Giant Counter
# Usage: ./font_debug.sh

echo "🎨 Font/Color Debug Monitor for Giant Counter"
echo "============================================"
echo ""
echo "Monitoring for:"
echo "  🔤 Font changes and locks"
echo "  🎨 Color changes and locks"
echo "  👆 Touch events on smoker name"
echo "  💾 Save/load operations"
echo "  🔒 Lock state changes"
echo ""
echo "Press Ctrl+C to stop..."
echo ""

# Colors for terminal output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
PURPLE='\033[0;35m'
CYAN='\033[0;36m'
NC='\033[0m' # No Color

# Monitor the log with colored output
adb logcat -c  # Clear existing logs
adb logcat | grep -E "GiantCounter|MainActivity" | while read -r line; do
    # Highlight different types of events with colors
    if echo "$line" | grep -q "🔤.*[Tt]ap.*cycling font"; then
        echo -e "${GREEN}[FONT TAP]${NC} $line"
    elif echo "$line" | grep -q "🎨.*[Tt]ap.*cycling color"; then
        echo -e "${CYAN}[COLOR TAP]${NC} $line"
    elif echo "$line" | grep -q "🔒.*[Ll]ocked"; then
        echo -e "${RED}[LOCKED]${NC} $line"
    elif echo "$line" | grep -q "🔓\|unlocked"; then
        echo -e "${GREEN}[UNLOCKED]${NC} $line"
    elif echo "$line" | grep -q "💾.*[Ss]aving"; then
        echo -e "${YELLOW}[SAVING]${NC} $line"
    elif echo "$line" | grep -q "👆.*touch"; then
        echo -e "${BLUE}[TOUCH]${NC} $line"
    elif echo "$line" | grep -q "spinnerFontIndex\|spinnerColor\|giant_counter"; then
        echo -e "${PURPLE}[SYNC]${NC} $line"
    elif echo "$line" | grep -q "colorChangingEnabled\|randomFontsEnabled"; then
        echo -e "${YELLOW}[STATE]${NC} $line"
    elif echo "$line" | grep -q "ERROR\|Failed"; then
        echo -e "${RED}[ERROR]${NC} $line"
    else
        echo "$line"
    fi
done