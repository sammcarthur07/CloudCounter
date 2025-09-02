# GitHub Actions Setup Instructions

## Required GitHub Secrets

To enable Firebase App Distribution in GitHub Actions, you need to set up the following secrets in your GitHub repository:

### 1. Get Firebase Token (One-time setup)

Run this command on your computer (not mobile):

```bash
# Install Firebase CLI if not installed
npm install -g firebase-tools

# Login and get token
firebase login:ci
```

This will open a browser, let you login, and give you a token that looks like:
`1//0abcdef...`

### 2. Add Token to GitHub Secrets

1. Go to: https://github.com/sammcarthur07/CloudCounter/settings/secrets/actions
2. Click "New repository secret"
3. Name: `FIREBASE_TOKEN`
4. Value: (paste the token from step 1)
5. Click "Add secret"

## Testing the Workflows

### Simple Build (No Firebase)

Use this to test if the build works at all:

1. Go to: https://github.com/sammcarthur07/CloudCounter/actions
2. Click "Simple APK Build"
3. Click "Run workflow"
4. Select build type (debug/release)
5. Click "Run workflow" button

This will build the APK and save it as an artifact you can download from the Actions page.

### Firebase Build & Distribution

Once the simple build works and you've added the FIREBASE_TOKEN:

1. Go to: https://github.com/sammcarthur07/CloudCounter/actions
2. Click "Firebase Cloud Build & Distribution"
3. Click "Run workflow"
4. Select build type and add release notes
5. Click "Run workflow" button

The APK will be emailed to: mcarthur.sp@gmail.com

## Troubleshooting

### Build Fails Immediately

- Check the logs for "gradlew: not found" - fixed by chmod +x
- Check for missing google-services.json - needs to be in CloudCounter/app/
- Check for SDK version issues - workflow uses JDK 17

### Firebase Distribution Fails

- Make sure FIREBASE_TOKEN is set in GitHub secrets
- Check Firebase console for App ID: 1:778271181918:android:2225b29f4fe7cea4d338cf
- Verify email: mcarthur.sp@gmail.com is added as a tester in Firebase Console

### From Mobile (Termux)

To trigger from mobile, you can:

1. Use the mobile-workflow.sh script (options 7 or 8)
2. Or manually trigger from browser:
   - Open Chrome on your phone
   - Go to: https://github.com/sammcarthur07/CloudCounter/actions
   - Request desktop site
   - Click workflow and run it

## Direct Links

- [GitHub Actions](https://github.com/sammcarthur07/CloudCounter/actions)
- [Add Secrets](https://github.com/sammcarthur07/CloudCounter/settings/secrets/actions)
- [Firebase Console](https://console.firebase.google.com/project/cloudcounter-sam/appdistribution)