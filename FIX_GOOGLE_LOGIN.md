# Fix Google Sign-In for Debug Builds

## The Problem
Google Sign-In fails with "Developer error" because Firebase doesn't recognize the SHA-1 fingerprint of the APK. Different build environments (local vs GitHub Actions) use different keystores, resulting in different SHA-1s.

## The Solution
Add ALL SHA-1 fingerprints to Firebase Console. Firebase allows multiple SHA-1s per app!

## Step 1: Get Your Local SHA-1

Run this on your computer:
```bash
./get-local-sha1.sh
```

Or manually:
```bash
keytool -list -v -keystore ~/.android/debug.keystore \
  -alias androiddebugkey -storepass android -keypass android \
  | grep SHA1
```

Copy the SHA1 value (e.g., `12:34:56:78:90:AB:CD:EF...`)

## Step 2: Get GitHub Actions SHA-1

1. Go to: https://github.com/sammcarthur07/CloudCounter/actions
2. Click "Get Debug SHA-1 Fingerprint"
3. Run workflow from main branch
4. Once complete, click the workflow run
5. Check the logs for the SHA-1 value
6. Copy it

## Step 3: Add Both SHA-1s to Firebase

1. Go to Firebase Console:
   https://console.firebase.google.com/project/cloudcounter-sam/settings/general

2. Scroll down to "Your apps" section

3. Find your Android app (com.sam.cloudcounter)

4. In the "SHA certificate fingerprints" section:
   - Click "Add fingerprint"
   - Paste your LOCAL SHA-1 (from Step 1)
   - Click Save
   
5. Click "Add fingerprint" again:
   - Paste the GITHUB ACTIONS SHA-1 (from Step 2)
   - Click Save

## Step 4: Download Updated google-services.json

1. In the same Firebase Console page
2. Click "Download google-services.json"
3. Replace the file in: `CloudCounter/app/google-services.json`
4. Commit and push:
```bash
git add CloudCounter/app/google-services.json
git commit -m "Update google-services.json with new SHA-1 fingerprints"
git push origin main
```

## Step 5: Test

1. Trigger a new build from GitHub Actions
2. Download and install the APK
3. Google Sign-In should now work!

## Important Notes

- You can add MULTIPLE SHA-1s to Firebase (no limit)
- Each build environment can have its own SHA-1
- Common SHA-1s to add:
  - Local debug keystore (Android Studio builds)
  - GitHub Actions debug keystore (CI builds)
  - Release keystore (production builds)
  
## Troubleshooting

If login still fails:
1. Make sure you downloaded the NEW google-services.json after adding SHA-1s
2. Clean and rebuild the app
3. Check that the package name matches: com.sam.cloudcounter
4. Verify Firebase Auth is enabled in Firebase Console

## Quick Commands

Get local SHA-1:
```bash
./get-local-sha1.sh
```

Get GitHub SHA-1 (run workflow):
```bash
gh workflow run get-sha1.yml
```

Check current SHA-1s in Firebase:
https://console.firebase.google.com/project/cloudcounter-sam/settings/general