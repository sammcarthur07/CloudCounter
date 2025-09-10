# Testing "Continue with Last Bowl" Feature

## How to Test

1. **Start Android Studio** and run the app
2. **Open Logcat** in Android Studio
3. **Apply the filter** to see only relevant logs:
   ```
   tag:MainActivity CONTINUE_BOWL
   ```
   Or use this broader filter to see all related logs:
   ```
   tag:MainActivity (CONTINUE_BOWL|proceedWithLogHit|refreshLocal|updateGroup)
   ```

## Test Steps

### Test 1: Basic Flow
1. Start a session
2. Add a bowl activity
3. Add some cones (2-3)
4. End the session
5. Start a new session
6. Try to add a cone (should trigger the popup)
7. Select "Continue with last bowl"
8. Check the logs and verify:
   - Bowl is added to the current session
   - Stats are updated correctly
   - Cones since last bowl is correct
   - Rounds calculation is correct

### Test 2: No Previous Bowl
1. Clear all data or use a fresh install
2. Start a session
3. Try to add a cone (should trigger the popup)
4. Select "Continue with last bowl"
5. Should handle gracefully and add a new bowl

## Expected Log Output

When you tap "Continue with last bowl", you should see logs like:

```
🔄 CONTINUE_BOWL: Starting continue with last bowl for [smoker_name]
🔄 CONTINUE_BOWL: Current session active: true
🔄 CONTINUE_BOWL: Last bowl found: true
🔄 CONTINUE_BOWL: Last bowl timestamp: [timestamp]
🔄 CONTINUE_BOWL: Last bowl smoker ID: [id]
🔄 CONTINUE_BOWL: Last bowl session ID: [session_id]
🔄 CONTINUE_BOWL: Cones since last bowl: [count]
🔄 CONTINUE_BOWL: Activities since last bowl: [count]
🔄 CONTINUE_BOWL: Rounds from last bowl: [rounds]
🔄 CONTINUE_BOWL: Last bowl smoker name: [name]
🔄 CONTINUE_BOWL: Current stats - Cones: X, Bowls: Y, Rounds: Z
🔄 CONTINUE_BOWL: Updated stats - Cones: X+cones, Bowls: Y+1, Rounds: Z+rounds
🔄 CONTINUE_BOWL: Stats updated in ViewModel
🔄 CONTINUE_BOWL: Adding bowl at timestamp: [timestamp]
🔄 CONTINUE_BOWL: Bowl added successfully
🔄 CONTINUE_BOWL: Adding cone at timestamp: [timestamp]
🔄 CONTINUE_BOWL: Cone added successfully
🔄 CONTINUE_BOWL: Restored auto mode to: [true/false]
🔄 CONTINUE_BOWL: Refreshing local session stats
🔄 CONTINUE_BOWL: UI refreshed
🔄 CONTINUE_BOWL: Process completed successfully
```

## Troubleshooting

If the bowl is not being added, check for:

1. **Error logs**: Look for any logs starting with `🔄 CONTINUE_BOWL: Error`
2. **Session state**: Ensure `Current session active: true`
3. **Database queries**: Check if `Last bowl found: true`
4. **Stats updates**: Verify the stats are being updated in the logs

## Additional Debug Commands

To see ALL MainActivity logs during testing:
```
tag:MainActivity
```

To see database operations:
```
tag:ActivityRepository|tag:ActivityLogDao
```

To see session stats updates:
```
tag:SessionStatsVM
```

## Send Me The Logs

After testing, please copy the filtered logs and share them. Focus on:
1. The logs from when you tap "Continue with last bowl"
2. Any error messages
3. The final state of the stats after the operation