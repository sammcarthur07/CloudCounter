#!/bin/bash

echo "========================================="
echo "COMPLETE CLOUD SYNC MONITOR"
echo "========================================="
echo ""
echo "Monitoring ALL sync operations:"
echo "  📦 Stash sync"
echo "  📜 History sync"
echo "  🔄 Session summaries"
echo "  📤 Uploads"
echo "  📥 Downloads"
echo ""
echo "Filter for specific operations:"
echo "  - Stash: grep -i stash"
echo "  - History: grep -i history"
echo "  - Activities: grep -i activity"
echo "  - Sessions: grep -i session"
echo ""
echo "Press Ctrl+C to stop"
echo "========================================="
echo ""

# Clear logs
adb logcat -c

# Comprehensive monitoring
adb logcat -v time \
    ActivityRepository:* \
    HistoryCloudSync:* \
    StashCloudSync:* \
    STASH_VM:* \
    MainActivity:* \
    *:S | grep -E "(sync|Sync|upload|Upload|download|Download|History|history|Stash|stash|Session|session|📤|📥|🔄|🌐|✅|❌|hasMore|page)"