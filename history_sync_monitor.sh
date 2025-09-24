#!/bin/bash

echo "========================================="
echo "HISTORY CLOUD SYNC MONITOR"
echo "========================================="
echo ""
echo "This monitor shows:"
echo "  📤 Activity uploads to cloud"
echo "  📥 Activity downloads from cloud"
echo "  🔄 Sync operations and pagination"
echo "  ✅ Success messages"
echo "  ❌ Error messages"
echo ""
echo "Press Ctrl+C to stop"
echo "========================================="
echo ""

# Clear previous logs
adb logcat -c

# Monitor with color coding and emojis
adb logcat -v time \
    ActivityRepository:D \
    HistoryCloudSync:D \
    MainActivity:D \
    StashCloudSync:D \
    STASH_VM:D \
    *:S | while read line; do
        # Color code based on content
        if echo "$line" | grep -qE "(ERROR|Failed|failed|Exception|❌)"; then
            echo -e "\033[31m$line\033[0m"  # Red for errors
        elif echo "$line" | grep -qE "(✅|Success|successful|complete|Complete)"; then
            echo -e "\033[32m$line\033[0m"  # Green for success
        elif echo "$line" | grep -qE "(📤|Uploading|upload)"; then
            echo -e "\033[33m$line\033[0m"  # Yellow for uploads
        elif echo "$line" | grep -qE "(📥|Downloading|download)"; then
            echo -e "\033[36m$line\033[0m"  # Cyan for downloads
        elif echo "$line" | grep -qE "(🌐|sync|Sync|History)"; then
            echo -e "\033[35m$line\033[0m"  # Magenta for sync operations
        elif echo "$line" | grep -qE "(hasMore|page|Page|pagination)"; then
            echo -e "\033[34m$line\033[0m"  # Blue for pagination
        else
            echo "$line"
        fi
    done