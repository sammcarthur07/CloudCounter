# Cloud Counter Hybrid Development Workflow

## Overview
Complete hybrid development workflow for Cloud Counter Android app with seamless switching between:
- Computer development (Claude Code + Android Studio)
- Mobile development (Claude Code in Termux on S23 Ultra)
- Firebase cloud builds
- Firebase App Distribution

## Configuration Details
- **GitHub Username**: sammcarthur07
- **Firebase Project ID**: cloudcounter-sam
- **Package Name**: com.sam.cloudcounter
- **Email for APK**: mcarthur.sp@gmail.com
- **Firebase App ID**: 1:778271181918:android:2225b29f4fe7cea4d338cf

## Quick Start Commands

### 🖥️ On Computer (Linux/Mac)

#### Initial Setup
```bash
# Clone repository
git clone https://github.com/sammcarthur07/CloudCounter.git
cd CloudCounter

# Set up Git credentials
git config user.name "sammcarthur07"
git config user.email "mcarthur.sp@gmail.com"
```

#### Build & Distribution Commands
```bash
# Interactive build menu
./build-and-distribute.sh

# Quick commands
./gradlew assembleDebug                      # Build debug APK
./gradlew assembleRelease                    # Build release APK
./gradlew appDistributionUploadDebug         # Build & email debug APK
./gradlew appDistributionUploadRelease       # Build & email release APK

# One-line quick build & email
cd CloudCounter && ./gradlew clean assembleDebug appDistributionUploadDebug
```

#### Sync Commands
```bash
# Pull latest from GitHub
./sync-workflow.sh pull

# Save and push to GitHub
./sync-workflow.sh push

# Prepare to switch to mobile
./sync-workflow.sh switch
```

### 📱 On Mobile (Termux on S23 Ultra)

#### Initial Termux Setup
```bash
# Install Termux from F-Droid (not Play Store)
# Open Termux and run:

# Update packages
pkg update && pkg upgrade -y

# Install required tools
pkg install -y git nodejs npm openssh

# Install Firebase CLI
npm install -g firebase-tools

# Clone repository
cd ~
git clone https://github.com/sammcarthur07/CloudCounter.git
cd CloudCounter

# Run mobile setup
./mobile-workflow.sh
# Select option 1 for first-time setup
```

#### Mobile Build Commands
```bash
# Interactive mobile menu
./mobile-workflow.sh

# GitHub Actions cloud build (recommended)
# Option 7 or 8 in mobile-workflow.sh menu

# Or manually trigger from browser:
# https://github.com/sammcarthur07/CloudCounter/actions

# Quick sync and build
cd ~/CloudCounter && git pull && ./mobile-workflow.sh
```

#### Mobile Installation
```bash
# After receiving APK via email:
# 1. Download APK from email to Downloads folder
# 2. Run:
./mobile-workflow.sh
# Select option 8 to install from Downloads

# Or manually:
am start -a android.intent.action.VIEW \
  -d "file:///storage/emulated/0/Download/CloudCounter-debug.apk" \
  -t "application/vnd.android.package-archive"
```

## Workflow Scenarios

### Scenario 1: Computer → Mobile Development
```bash
# On Computer:
./sync-workflow.sh switch        # Saves and pushes all changes

# On Mobile (Termux):
cd ~/CloudCounter
./sync-workflow.sh pull          # Gets latest changes
# Make changes with Claude Code in Termux
./sync-workflow.sh push          # Save changes
./mobile-workflow.sh             # Option 5 for cloud build
```

### Scenario 2: Mobile → Computer Development
```bash
# On Mobile (Termux):
./sync-workflow.sh switch        # Saves and pushes all changes

# On Computer:
./sync-workflow.sh pull          # Gets latest changes
# Make changes with Claude Code
./build-and-distribute.sh       # Option 7 for quick build & email
```

### Scenario 3: Quick Firebase Cloud Build
```bash
# From anywhere with internet:
curl -X POST \
  https://firebaseappdistribution.googleapis.com/v1/projects/cloudcounter-sam/apps/1:288437132062:android:d5fd623e97e79e0f9e4e16/releases \
  -H "Authorization: Bearer $(gcloud auth print-access-token)" \
  -H "Content-Type: application/json" \
  -d '{"releaseNotes": "Cloud build", "testerEmails": ["mcarthur.sp@gmail.com"]}'
```

## Firebase App Distribution

### Setup Firebase CLI
```bash
# Install Firebase CLI (if not installed)
npm install -g firebase-tools

# Login to Firebase
firebase login

# Set project
firebase use cloudcounter-sam
```

### Direct APK Distribution Commands
```bash
# Distribute existing APK
firebase appdistribution:distribute CloudCounter/app/build/outputs/apk/debug/app-debug.apk \
  --app 1:778271181918:android:2225b29f4fe7cea4d338cf \
  --release-notes "Manual distribution" \
  --testers mcarthur.sp@gmail.com

# With groups
firebase appdistribution:distribute CloudCounter/app/build/outputs/apk/release/app-release.apk \
  --app 1:778271181918:android:2225b29f4fe7cea4d338cf \
  --release-notes "Release version" \
  --groups "testers"
```

## Git Shortcuts

### Quick Aliases (already configured in .gitconfig)
```bash
git quick-push      # Add all, commit with 'Mobile update', and push
git sync           # Pull and push
git mobile-save    # Save with timestamp from mobile
git computer-save  # Save with timestamp from computer
git status-short   # Compact status view
```

## Troubleshooting

### Build Issues
```bash
# Clean build
cd CloudCounter
./gradlew clean
./gradlew build

# Check dependencies
./gradlew dependencies

# Firebase App Distribution issues
./gradlew appDistributionUploadDebug --debug
```

### Termux Issues
```bash
# Storage permission
termux-setup-storage

# Fix npm permissions
npm config set prefix ~/.npm-global
export PATH=$PATH:~/.npm-global/bin

# Update everything
pkg update && pkg upgrade
npm update -g
```

### Git Sync Issues
```bash
# Force pull (discard local changes)
git fetch origin
git reset --hard origin/main

# Resolve merge conflicts
git status
# Edit conflicted files
git add .
git commit -m "Resolved conflicts"
git push
```

## Testing Workflow

### Local Testing (Computer with Android Studio)
```bash
# USB debugging
adb devices
./gradlew installDebug
adb shell am start -n com.sam.cloudcounter/.MainActivity
```

### Firebase Test Lab (Cloud Testing)
```bash
# Upload to Test Lab
gcloud firebase test android run \
  --type robo \
  --app CloudCounter/app/build/outputs/apk/debug/app-debug.apk \
  --device model=Pixel2,version=28,locale=en,orientation=portrait \
  --timeout 90s
```

## Environment Variables

### Add to ~/.bashrc or ~/.zshrc
```bash
export ANDROID_HOME=$HOME/Android/Sdk
export PATH=$PATH:$ANDROID_HOME/emulator
export PATH=$PATH:$ANDROID_HOME/tools
export PATH=$PATH:$ANDROID_HOME/tools/bin
export PATH=$PATH:$ANDROID_HOME/platform-tools
export FIREBASE_PROJECT=cloudcounter-sam
export GITHUB_USER=sammcarthur07
```

### For Termux (~/.bashrc)
```bash
export PATH=$PATH:~/.npm-global/bin
export GITHUB_USER=sammcarthur07
export FIREBASE_PROJECT=cloudcounter-sam
alias cc="cd ~/CloudCounter"
alias build="./mobile-workflow.sh"
```

## Quick Reference Card

| Task | Computer Command | Mobile (Termux) Command |
|------|-----------------|------------------------|
| Pull latest | `./sync-workflow.sh pull` | `./sync-workflow.sh pull` |
| Save work | `./sync-workflow.sh push` | `./sync-workflow.sh push` |
| Build debug | `./gradlew assembleDebug` | Use Firebase cloud build |
| Email APK | `./gradlew appDistributionUploadDebug` | `./mobile-workflow.sh` → Option 5 |
| Install APK | `adb install app-debug.apk` | Download from email → Install |
| Quick build+email | `./build-and-distribute.sh` → Option 7 | `./mobile-workflow.sh` → Option 9 |

## Support

- Firebase Console: https://console.firebase.google.com/project/cloudcounter-sam
- GitHub Repo: https://github.com/sammcarthur07/CloudCounter
- Firebase App Distribution: Check email at mcarthur.sp@gmail.com