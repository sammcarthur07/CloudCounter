#!/bin/bash

echo "========================================="
echo "STASH CLOUD SYNC MONITOR"
echo "========================================="
echo ""
echo "Monitoring for:"
echo "  - Authentication events"
echo "  - Stash sync operations"
echo "  - Cloud upload/download"
echo "  - Errors and exceptions"
echo ""
echo "Press Ctrl+C to stop"
echo "========================================="
echo ""

# Clear previous logs
adb logcat -c

# Monitor with comprehensive filtering
adb logcat -v time \
    STASH_VM:D \
    StashFragment:D \
    StashCloudSync:D \
    FirebaseAuthManager:D \
    *:S | while read line; do
        # Highlight important messages
        if echo "$line" | grep -qE "(ERROR|Exception|failed|Failed)"; then
            echo -e "\033[31m$line\033[0m"  # Red for errors
        elif echo "$line" | grep -qE "(User ID set|syncWithCloud|uploadStash|downloadStash|Sync successful)"; then
            echo -e "\033[32m$line\033[0m"  # Green for success
        elif echo "$line" | grep -qE "(\ud83d\udd04|\u2601\ufe0f|\ud83d\udce4|\ud83d\udce5|\ud83d\udd04)"; then
            echo -e "\033[33m$line\033[0m"  # Yellow for cloud operations
        else
            echo "$line"
        fi
    done