# Play Store Release Setup

## 🎯 Release SHA-1 for Firebase

Add this SHA-1 to Firebase Console for signed release APKs:
**`c75f659869a8525471d9be72964a579df424e791`**

1. Go to: https://console.firebase.google.com/project/cloudcounter-sam/settings/general
2. Find your Android app
3. Click "Add fingerprint"
4. Paste: `c75f659869a8525471d9be72964a579df424e791`
5. Click Save
6. Download new google-services.json

## 🔐 GitHub Secrets Setup

You need to add these secrets to GitHub for the signed APK workflow to work.

### Step 1: Encode your keystore

Run this command on your computer:
```bash
base64 -w 0 CloudCounter/keystore/release.jks > keystore_base64.txt
```

### Step 2: Add GitHub Secrets

Go to: https://github.com/sammcarthur07/CloudCounter/settings/secrets/actions

Add these 4 secrets:

| Secret Name | Value |
|------------|-------|
| `KEYSTORE_BASE64` | Contents of keystore_base64.txt (from Step 1) |
| `KEYSTORE_PASSWORD` | `Sam$03` |
| `KEY_ALIAS` | `cloudcounter` |
| `KEY_PASSWORD` | `Sam$03` |

### Step 3: (Optional) Add Firebase Token

For automatic Firebase App Distribution:
```bash
firebase login:ci
```
Add the token as `FIREBASE_TOKEN` secret.

## 🚀 Using the Workflow

### To Build a Signed APK for Play Store:

1. Go to: https://github.com/sammcarthur07/CloudCounter/actions
2. Click "Build Signed APK for Play Store"
3. Choose version bump type:
   - **patch**: 16.0.0 → 16.0.1 (bug fixes)
   - **minor**: 16.0.0 → 16.1.0 (new features)
   - **major**: 16.0.0 → 17.0.0 (breaking changes)
4. Add release notes
5. Run workflow

### What the Workflow Does:

✅ Automatically increments version code and name
✅ Builds a signed, optimized APK
✅ Creates a GitHub release with the APK
✅ Optionally uploads to Firebase App Distribution
✅ Commits the version bump back to the repo

## 📦 APK Details

The signed APK will be:
- ✅ Signed with your release keystore
- ✅ Optimized with R8/ProGuard
- ✅ Ready for Play Store upload
- ✅ Working Google Sign-In (with release SHA-1)

## 🔍 Version Information

Current version in build.gradle.kts:
- Version Code: 16
- Version Name: "16.0"

The workflow will automatically increment these.

## 📱 Play Store Upload

After the workflow completes:
1. Download the signed APK from GitHub Actions artifacts
2. Go to Google Play Console
3. Create a new release
4. Upload the APK
5. Fill in release notes
6. Submit for review

## ⚠️ Important Security Note

**NEVER** commit your keystore or passwords to Git!
Always use GitHub Secrets for sensitive information.

## 🆘 Troubleshooting

If the build fails:
- Check that all 4 GitHub Secrets are set correctly
- Verify the keystore base64 encoding
- Check the workflow logs for specific errors

If Google Sign-In doesn't work:
- Make sure you added the release SHA-1 to Firebase
- Download and update google-services.json
- Rebuild the APK