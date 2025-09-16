# CloudCounter User Manual

## Getting Started

### First Time Opening the App

1. **Welcome Screen** appears with three setup options:
   - **Setup Stash**: Configure inventory tracking
   - **Setup Ratios**: Set measurement ratios for calculations  
   - **Create Goal**: Make your first achievement goal
   
2. **Choose your path**:
   - Tap checkboxes next to options you want to set up
   - Tap **"Next"** to go through selected setups
   - Tap **"Skip"** to go directly to main screen

### Quick Start Guide

1. **Add Your First Participant**:
   - Look at top-left corner of main screen
   - Find the text field showing "Add smoker..."
   - Tap on this text field
   - A dropdown menu appears with three options:
     - **Add Smoker**: Tap to add new participant
     - **Delete All**: Remove all participants
     - **Reorder**: Change participant order
   - Tap "Add Smoker" to open the Add Smoker dialog

2. **Start Tracking Activities**:
   - Find the three main activity buttons in center of screen:
     - **ADD JOINT** button
     - **ADD CONE** button
     - **ADD BOWL** button
   - Tap any button to add one count
   - Number appears showing total count

3. **Start a Session**:
   - Find "Start Sesh" button at bottom of screen
   - Tap it to begin timing your session
   - Button changes to "End Sesh"
   - Timer starts counting up at top of screen

## Main Screen Layout (Top to Bottom)

### Top Section

#### Version Info (Very Top)
- Small green text showing "VERSION : AUG 25th v16"

#### Participant Dropdown (Top-Left Corner)
**Location**: Top-left corner, large text field
**Default Text**: "Add smoker..." when no participants

**How to Use**:
1. **Tap** the text field showing participant name or "Add smoker..."
2. Dropdown menu appears with:
   - Current participants (if any)
   - **Add Smoker** option
   - **Delete All** option
   - **Reorder** option

**Long-Press Features** (Progressive timing - each level adds more!):

- **Hold for 1.5 seconds**:
  - Vibrates once
  - Locks/unlocks COLOR ONLY
  - Shows toast: "Color locked" or "Color unlocked"
  - Dropdown won't open when you release
  
- **Hold for 3 seconds**:
  - Vibrates again
  - Locks/unlocks FONT ONLY (color stays as previous setting)
  - Shows toast: "Font locked" or "Font unlocked"
  - Keep holding to reach next level
  
- **Hold for 5 seconds**:
  - Vibrates again
  - Locks/unlocks BOTH font AND color together
  - Shows toast: "Font & color locked" or "Font & color unlocked"
  - Keep holding to reach cycling mode
  
- **Hold for 7+ seconds**:
  - Starts AUTO-CYCLING through all fonts
  - Changes to new font every 2 seconds
  - Small vibration with each font change
  - Shows toast: "Font cycling started (every 2s)"
  - Release when you see the font you want
  - When released: Locks both font & color with your selection
  - Shows toast: "Font & color locked"

**Important Notes**:
- Each timing level builds on the previous
- You can release at any level to apply that action
- Locked settings apply to ALL participants globally
- Settings persist even after app restart

#### Mode Selection (Top-Center)
Two radio buttons:
- **Sticky**: Keeps same participant selected
- **Auto**: Rotates through participants after each activity

#### Top Button Bar (Top-Right)
Five small icon buttons in a row:

1. **Layout Rotation** (🔄 icon):
   - Toggles activity buttons between top and bottom of screen
   
2. **Add Custom Activity** (➕ icon):
   - Opens dialog to create custom activity button
   
3. **Giant Counter** (Weed leaf icon):
   - Opens full-screen counting mode
   - Large button for easy tapping
   
4. **Vibration Toggle** (Vibration icon):
   - Enable/disable haptic feedback
   - Icon highlighted when on, grayed when off
   
5. **Notification Toggle** (Bell icon):
   - Enable/disable notifications
   - Icon highlighted when on, grayed when off

### Activity Buttons Section

#### Main Activity Buttons
Three or four buttons in a row:
- **ADD JOINT** - Tap to add joint count
- **ADD CONE** - Tap to add cone count
- **ADD BOWL** - Tap to add bowl count
- **ADD [CUSTOM]** - If custom activity created

**Button Interactions**:
- **Single Tap**: Adds one count for current participant
- **Long Press**: Opens activity-specific popup:
  
  **Joint Popup Shows**:
  - Total joints for current participant
  - Session joint count
  - Time since last joint
  - Options may include quick add multiple
  
  **Cone Popup Shows**:
  - Total cones for current participant
  - Session cone count
  - Time since last cone
  - Cone-specific statistics
  
  **Bowl Popup Shows**:
  - Total bowls for current participant
  - Session bowl count
  - Time since last bowl
  - Bowl-specific options

### Timer Section (Hidden by Default)

#### Advanced Button
**Location**: Below activity buttons
**Text**: Shows "Advanced" when collapsed, "See Less" when expanded

**When Tapped**:
- Expands to show timer controls
- Activity buttons become taller
- Auto-add checkboxes appear below each activity button

#### Timer Display (When Expanded)
Three timers in a row:

1. **Left Timer - Time Since Last**:
   - Shows time since last activity
   - Format: "X:XX since last"

2. **Middle Timer - Countdown/Count-up**:
   - Starts at time interval you set
   - Counts DOWN to 0:00
   - At 0:00: Notification sound plays (if enabled)
   - Continues counting into negative (-X:XX)
   - Shows how overdue the next activity is
   - **Sound Toggle**: Icon button to enable/disable notification sound at zero

3. **Right Timer - Session Time**:
   - Shows total session duration
   - Format: "X:XX:XX this sesh"

#### Auto-Add Controls (When Expanded)
Below each activity button:
- **Checkbox**: "Auto" - Enable auto-adding
- **Timer Display**: Shows countdown when active

### Rounds Section
**Location**: Below timers (when expanded)

**Components**:
- **Text Display**: "X Rounds Left"
- **Minus Button** (➖): Decrease rounds
- **Plus Button** (➕): Increase rounds

**Purpose**: 
- Track rotation rounds between participants in a session
- Helps ensure everyone gets equal turns
- Countdown for group sessions

### Session Control Buttons (Bottom Section)

#### Main Session Buttons
- **Start Sesh**: Begin new session (changes to "End Sesh" when active)
- **End Sesh**: Stop and save current session
- **Rewind**: Go back 10 seconds in time (adjusts timer, may affect recent activities)
- **Skip**: Skip current participant's turn in rotation
- **Undo**: Remove last added activity

## Navigation Tabs (Bottom)

### 1️⃣ Main Tab
Current screen - activity tracking and session management

### 2️⃣ Sesh Tab
**What You See**:
- Session timer at top
- Room name and share code (if in room)
- List of participants with their counts
- "Resume Sesh" floating button (bottom-right)

### 3️⃣ Stats Tab

**Time Period Chips**:
- **All Time**: Complete history
- **Today**: Current day only
- **This Week**: Current week
- **This Month**: Current month
- **Custom**: Special selection

**Custom Chip Features**:
1. Tap "Custom" chip
2. Session selection dialog opens
3. Shows list of all sessions with:
   - Session date and time
   - Checkboxes for selection
4. Select multiple sessions to include
5. Tap "Apply" to filter stats to only selected sessions
6. Tap "Cancel" to keep previous selection
7. Custom chip shows number of sessions selected

**Statistics Display**:
- Participant filter chips below time chips
- Statistics cards showing totals
- Per-activity breakdown
- Per-participant breakdown

### 4️⃣ Goals Tab
**What You See**:
- List of goal cards
- Each card shows:
  - Goal name
  - Progress bar
  - Percentage complete
  - Pause/Delete buttons
- "+" button (bottom-right) to add new goal

### 5️⃣ History Tab
**What You See**:
- Chronological list of all activities
- Session summaries with timestamps
- Each entry shows:
  - Activity type
  - Participant name
  - Time
  - Delete option

### 6️⃣ Graph Tab
**What You See**:
- Chart display (line or bar)
- Time period selection chips
- Activity type filters
- Participant filters
- Toggle between chart types

### 7️⃣ Stash Tab

**Top Bar (Left to Right)**:
- **Stash Amount Display**: "X.XXg" shows current stash
- **"Their Stash" Text**: Label for comparison
- **"+" Button**: Add to stash
- **"X" Button**: Sign out of Google account

**When Tapping "+" Button**:
Opens popup with two tabs:
1. **My Stash Tab**:
   - Amount input field
   - Cost input field
   - Ratio selector (Eighths, Quarters, etc.)
   - "Add" button
   - "Remove" button

2. **Their Stash Tab**:
   - View friend's stash amount
   - Comparison statistics

**Main Display Area**:
- Distribution chart
- Usage statistics
- Cost breakdown
- Per-session consumption

### 8️⃣ Chat Tab

**Main Menu** (First Screen):
1. **Text or Video Chat** - Opens submenu:
   - **Sesh Chat**: Private room chat
   - **Public Chat**: Global chat
2. **Sesh Logs**: View session activity logs
3. **Public Logs**: View global activity feed

**Sesh Chat Screen**:
- Message list in center
- Input field at bottom
- Send button
- Each message has heart icon

**Heart/Like Feature**:
- Tap heart on any message to like
- **Your own message**: Heart becomes outlined when you like it
- **Others' messages**: Heart fills with red color
- Like count shows next to heart
- Sender gets notification when their message is liked

### 9️⃣ About Tab

**420 Countdown Feature**:
- **Location**: Bottom section of About tab
- **What it does**: Shows countdown to 4:20 (AM/PM) in cities worldwide
- **How it works**:
  1. Displays city name and countdown timer
  2. Every 5 seconds, rotates to next city
  3. Shows cities approaching 4:20
  4. Prioritizes cities closest to 4:20
  5. Includes your current location
- **City Examples**: Los Angeles, New York, London, Amsterdam, Tokyo, Sydney
- **Visual**: City name with countdown "X:XX until 4:20"
- **Notification**: Optional alert when it's 4:20 in any city

**App Information**:
- Version number display
- Developer credits

## Adding Participants - 5 Methods

When you tap "Add Smoker" from the dropdown, a dialog appears with 5 options:

### 1. Add New (Local)
**Icon**: ➕ "Add New"
**Description**: "Create new local profile"
**Steps**:
1. Tap this option
2. Enter name in text field
3. Optional: Set password
4. Tap "OK" to create

### 2. Google Sign-In  
**Icon**: 🔐 "Login with Cloud Profile"
**Description**: "Sign in with Google account"
**Steps**:
1. Tap this option
2. Google sign-in screen appears
3. Select your Google account
4. Profile syncs automatically
5. Enter display name if prompted

### 3. Search by Name
**Icon**: 🔍 "Search by Name"
**Description**: "Find smoker by name"
**Steps**:
1. Tap this option
2. Type participant name to search
3. Select from search results
4. Confirm to add

### 4. Search by Code
**Icon**: 🔢 "Search by Code"
**Description**: "Enter 6-character code"
**Steps**:
1. Tap this option
2. Enter the 6-character participant code
3. Participant profile loads
4. Confirm to add

### 5. Password Login
**Icon**: 🔑 "Password Login"
**Description**: "Login with password"
**Steps**:
1. Available if participant has password
2. Enter participant password
3. Profile unlocks and loads

## Deleting Participants

When you select delete for a participant, a confirmation dialog appears:

**Delete Options**:
1. **Delete** - Removes participant and all their data
2. **Cancel** - Keeps participant, returns to list

**What Gets Deleted**:
- Participant profile
- All activity history for that participant
- Goals associated with participant
- Statistics data

**Warning**: This cannot be undone!

## Participant Icons (Next to Name in Dropdown)

When viewing participants in the expanded dropdown, up to 7 icons may appear:

1. **Sync Dot** (Green/Yellow/Red):
   - Green: Fully synced with cloud
   - Yellow: Syncing in progress
   - Red: Sync error or offline

2. **Lock Icon** (🔒):
   - Shows if participant is password protected
   - Tap to enter password

3. **Pause Icon** (⏸️):
   - Shows if participant is paused
   - Paused participants don't count in rotations

4. **Edit Icon** (✏️):
   - Tap to edit participant name or settings

5. **Cloud Icon** (☁️):
   - Shows if participant uses cloud sync
   - Enables cross-device access

6. **Delete Icon** (🗑️):
   - Tap to remove participant
   - Confirmation required

7. **Activity Indicator** (🟢):
   - Shows recent activity
   - Pulses when actively tracking

## Custom Activities

### How to Create Custom Activity

1. **Access Methods**:
   - Tap "+" icon in top button bar, OR
   - Tap "ADD CUSTOM ACTIVITY" button if visible

2. **Dialog Opens** showing:
   - Current activities list with management options
   - Name input field
   - Icon selection grid

3. **Rules**:
   - Maximum 4 total activities allowed
   - Name limit: 8 characters for text display
   - Names over 8 characters automatically use icon
   - Must remove an activity if 4 already exist

4. **Icon Options** (6 choices):
   - Pills icon
   - Bong icon
   - Cough icon
   - Stretch icon
   - Cigarette icon
   - Water glass icon

5. **Managing Activities**:
   - **Core activities**: Can temporarily "Remove" to make space
   - **Custom activities**: Can permanently "Delete"
   - Use ⬆️⬇️ arrows to reorder
   - "Reset" button restores all core activities

## Room Sessions

### Creating a Room

1. **From Main Screen**:
   - Start a session first (tap "Start Sesh")
   - Go to Sesh tab
   - Look for room creation option
   - Enter room name or use suggested name
   - System generates share code (4-6 characters)
   - Share code with friends

### Joining a Room

1. **From Main Screen**:
   - Get share code from friend
   - Go to appropriate screen
   - Enter share code in input field
   - Tap "Join"
   - You're now synced with room

### Room Features
- Real-time activity sync
- See all participants
- Chat with room members
- Video call option

## Tips for Effective Use

1. **Quick Counting**: Use Giant Counter mode for rapid counting
2. **Session Tracking**: Always start session to track time
3. **Turn Rotation**: Use rounds counter for fair rotation
4. **Auto-Add**: Set timers for regular intervals
5. **Rewind Feature**: Use to adjust time (goes back 10 seconds)
6. **Custom Activities**: Create for specific tracking needs
7. **Goals**: Set daily goals for motivation
8. **Stats Review**: Check stats tab regularly for insights
9. **Font Control**: Long-press participant name to lock font
10. **420 Tracking**: Check About tab for worldwide 4:20 times

## Troubleshooting

### Can't Add Custom Activity
- Check if you have 4 activities already
- Remove or delete an existing activity first

### Notifications Not Working
- Check notification toggle is on (bell icon)
- Verify app has notification permissions in phone settings

### Timer Not Showing
- Tap "Advanced" button to expand timer section

### Can't Join Room
- Verify share code is correct
- Check internet connection
- Ensure room is still active

### Font Keeps Changing
- Hold participant name for 5 seconds to lock font
- Hold for 7+ seconds to manually cycle through fonts

### Cloud Session Connection Issues
**Problem**: No internet during cloud session
**Symptoms**: 
- Sync dot shows red
- Activities not syncing to other devices
- "Offline" indicator may appear

**Solutions**:
1. **Check WiFi/Mobile Data**: Ensure internet is connected
2. **Continue Offline**: App saves activities locally
3. **When Internet Returns**: 
   - App automatically syncs pending activities
   - Green sync dot returns
   - All activities upload to cloud
4. **Force Sync**: Pull down to refresh in Sesh tab
5. **If Still Not Working**:
   - Sign out and sign back in (Stash tab → X button)
   - Restart the app
   - Check if Google services are working

---

*CloudCounter User Manual - Version 16.0*