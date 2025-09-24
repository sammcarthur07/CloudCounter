#!/bin/bash

# Stash Cloud Sync Monitor Script
# This script monitors the stash synchronization process with Firebase

echo "========================================="
echo "STASH CLOUD SYNC MONITOR"
echo "========================================="
echo ""
echo "Monitoring for:"
echo "  - Firebase authentication events"
echo "  - Stash data loading and syncing"
echo "  - Cloud upload/download operations"
echo "  - Error messages"
echo ""
echo "Press Ctrl+C to stop monitoring"
echo "========================================="
echo ""

# Monitor relevant tags with color coding
adb logcat -v time \
    STASH_VM:* \
    StashFragment:* \
    StashCloudSync:* \
    FirebaseAuthManager:* \
    *:S | grep -E --color=always "(User ID set|getCurrentUserId|loadCurrentStash|syncWithCloud|syncRatios|Uploading|Downloading|Sync successful|Sync failed|📤|📥|🔄|☁️|🔐|📦|⚙️|✅|❌)"