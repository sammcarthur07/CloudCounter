# Testing "Continue with Last Bowl" Feature - Version 2

## What Was Fixed

1. **Bowl with Associated Cones**: The bowl now stores the number of cones from the previous session using the `associatedConesCount` field
2. **Stats Persistence**: Added carried-over stats tracking in SessionStatsViewModel to prevent stats from being reset during refresh
3. **Proper Cone Counting**: The cones since last bowl now correctly counts only cones after the bowl, not including carried-over cones
4. **Rounds Calculation**: Rounds from the last bowl are properly added to the current session rounds

## How to Test

### Logcat Filter
Use this filter in Android Studio Logcat:
```
tag:MainActivity (CONTINUE_BOWL|carried-over|Associated)
```

Or for more detailed logs:
```
tag:MainActivity|tag:SessionStatsVM
```

## Test Scenario

### Setup Phase (Previous Session)
1. Start a session
2. Add 1 bowl
3. Add 8 cones (preferably rotating between smokers to create 4 rounds)
4. End the session
5. Note the stats: Total cones: 8, Rounds: 4

### Test Phase (New Session with Continue)
1. Start a new session
2. Try to add a cone (should trigger the popup)
3. Tap "Continue with last bowl"

### Expected Results
After tapping "Continue with last bowl":

1. **Immediate Stats** (before refresh):
   - Total Cones: 8 (from previous session)
   - Total Bowls: 1
   - Rounds: 4 (from previous session after last bowl)
   - Cones since last bowl: 0 (just added the bowl)

2. **After Adding Cone**:
   - Total Cones: 9 (8 carried + 1 new)
   - Total Bowls: 1
   - Rounds: 4 or 5 (depending on smoker rotation)
   - Cones since last bowl: 1

3. **Database Check**:
   - The bowl should have `associatedConesCount = 8`
   - This preserves the history of cones from the previous session

## Expected Log Output

```
🔄 CONTINUE_BOWL: Starting continue with last bowl for [name]
🔄 CONTINUE_BOWL: Last bowl found: true
🔄 CONTINUE_BOWL: Cones since last bowl: 8
🔄 CONTINUE_BOWL: Rounds from last bowl: 4
🔄 CONTINUE_BOWL: Updated stats - Cones: 8, Bowls: 1, Rounds: 4
🔄 CONTINUE_BOWL: Bowl added with ID: [id], associated cones: 8
📦 Setting carried-over stats - Cones: 8, Rounds: 4
🔄 CONTINUE_BOWL: Performing delayed stats refresh
🔍📦 Carried-over stats - Cones: 8, Rounds: 4
🔍🍶 Found 1 bowls with total associated cones: 8
🔍🎡 Added carried-over rounds: 4
```

## Verification Steps

1. **Check the Bowl Record**:
   - In your database viewer, check the latest bowl entry
   - It should have `associatedConesCount = 8` (or whatever was carried over)

2. **Stats Display**:
   - The UI should show the correct total cones (previous + new)
   - Rounds should include rounds from the last bowl only
   - "Cones since last bowl" should show only new cones after the continued bowl

3. **Add More Activities**:
   - Add another cone
   - Stats should continue incrementing correctly
   - The carried-over values should persist

## Troubleshooting

If stats reset to lower values:
- Check for "Carried-over stats" log entries
- Verify the delayed refresh is happening (500ms after bowl add)
- Ensure the bowl has associatedConesCount set

If rounds don't calculate correctly:
- Check "Rounds from last bowl" in the logs
- Verify the activities list includes only post-bowl activities

If cones since bowl is wrong:
- Should be 0 immediately after adding bowl
- Should increment with each new cone
- Check for "Cones since last bowl" in the current session calculation