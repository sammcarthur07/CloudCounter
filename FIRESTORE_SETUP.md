# Firestore Security Rules Setup

## Problem
The stash sync feature is getting PERMISSION_DENIED errors because Firestore security rules aren't configured to allow users to access their stash data.

## Solution
Update your Firestore security rules in the Firebase Console:

### Steps:
1. Go to [Firebase Console](https://console.firebase.google.com)
2. Select your CloudCounter project
3. Navigate to **Firestore Database** → **Rules**
4. Replace the existing rules with the following:

```javascript
rules_version = '2';
service cloud.firestore {
  match /databases/{database}/documents {
    // Allow users to read/write their own stash data
    match /user_stash/{userId} {
      allow read, write: if request.auth != null && request.auth.uid == userId;
      
      // Allow access to subcollections (like settings)
      match /{subcollection}/{document} {
        allow read, write: if request.auth != null && request.auth.uid == userId;
      }
    }
    
    // Existing rules for rooms (if any)
    match /rooms/{roomId} {
      allow read, write: if request.auth != null;
    }
    
    // Default deny all other access
    match /{document=**} {
      allow read, write: if false;
    }
  }
}
```

5. Click **Publish** to deploy the rules

### What these rules do:
- **`/user_stash/{userId}`**: Each user can only read/write their own stash data
- **Authentication required**: User must be signed in with Google
- **User isolation**: User A cannot access User B's stash data
- **Subcollections**: Allows access to settings like ratios under the user's stash document

### Testing:
After publishing the rules:
1. Clear app data (Settings → Apps → CloudCounter → Clear Data)
2. Open the app and sign in with Google
3. Add some stash data (e.g., 7g at $15/g)
4. Close the app completely
5. Clear app data again
6. Sign in with the same Google account
7. Your stash data should now load from the cloud!

### Monitoring:
Use the monitoring script to verify sync is working:
```bash
cd /home/sam/AndroidStudioProjects/CloudCounter
./monitor_stash.sh
```

Look for these success messages:
- `📤 Uploading stash for user`
- `✅ Stash uploaded successfully`
- `📥 Downloading stash for user`
- `✅ Stash downloaded`

### Alternative: Temporary Testing Rules
If you want to test quickly without authentication restrictions (NOT for production):
```javascript
rules_version = '2';
service cloud.firestore {
  match /databases/{database}/documents {
    match /{document=**} {
      allow read, write: if true;
    }
  }
}
```
⚠️ **WARNING**: These rules allow anyone to read/write all data. Only use for testing!