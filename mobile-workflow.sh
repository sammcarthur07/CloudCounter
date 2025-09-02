#!/data/data/com.termux/files/usr/bin/bash

# Mobile Workflow Script for Termux on S23 Ultra
# Optimized for ARM64 and Termux environment

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

# Configuration
GITHUB_USER="sammcarthur07"
REPO_NAME="CloudCounter"
EMAIL="mcarthur.sp@gmail.com"

# Function helpers
print_status() {
    echo -e "${GREEN}[MOBILE]${NC} $1"
}

print_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

print_info() {
    echo -e "${BLUE}[INFO]${NC} $1"
}

# Check if we're in Termux
check_termux() {
    if [ ! -d "/data/data/com.termux" ]; then
        print_error "This script is designed for Termux!"
        exit 1
    fi
}

# Setup Termux environment (first time only)
setup_termux() {
    print_status "Setting up Termux environment..."
    
    # Update packages
    pkg update -y
    
    # Install required tools
    pkg install -y git openssh curl wget
    
    # Install Firebase CLI (if not installed)
    if ! command -v firebase &> /dev/null; then
        print_status "Installing Firebase CLI..."
        npm install -g firebase-tools
    fi
    
    print_status "Termux setup complete!"
}

# Clone or update repository
sync_repo() {
    print_status "Syncing repository..."
    
    if [ -d "$REPO_NAME" ]; then
        cd $REPO_NAME
        print_status "Pulling latest changes..."
        git pull origin main
    else
        print_status "Cloning repository..."
        git clone https://github.com/$GITHUB_USER/$REPO_NAME.git
        cd $REPO_NAME
    fi
    
    print_status "Repository synced!"
}

# Trigger Firebase cloud build
trigger_cloud_build() {
    local build_type=$1
    print_status "Triggering Firebase cloud build ($build_type)..."
    
    # First, we need to build the APK locally or download it
    # Since we're on mobile, we'll use a different approach
    
    # Check if we have a pre-built APK or need to download one
    APK_PATH=""
    if [ "$build_type" == "debug" ]; then
        APK_PATH="/storage/emulated/0/Download/app-debug.apk"
        print_info "Looking for debug APK..."
    else
        APK_PATH="/storage/emulated/0/Download/app-release.apk"
        print_info "Looking for release APK..."
    fi
    
    # If APK doesn't exist, provide instructions
    if [ ! -f "$APK_PATH" ]; then
        print_error "APK not found at $APK_PATH"
        print_info "To use Firebase App Distribution from mobile:"
        print_info "1. Build APK on computer and upload to GitHub releases"
        print_info "2. Download APK to /storage/emulated/0/Download/"
        print_info "3. Run this command again"
        print_info ""
        print_info "Alternative: Use GitHub Actions for cloud builds"
        return 1
    fi
    
    # Distribute the APK via Firebase
    firebase appdistribution:distribute "$APK_PATH" \
        --app 1:778271181918:android:2225b29f4fe7cea4d338cf \
        --release-notes "Mobile distribution - $(date '+%Y-%m-%d %H:%M')" \
        --testers "$EMAIL"
    
    if [ $? -eq 0 ]; then
        print_status "APK distributed successfully! Check email at $EMAIL"
    else
        print_error "Distribution failed!"
        print_info "Make sure you're logged in: firebase login"
    fi
}

# Download APK from Firebase
download_apk() {
    print_status "Opening Firebase App Distribution..."
    print_info "Check your email for the download link"
    
    # Open email app
    am start -a android.intent.action.VIEW -d "mailto:$EMAIL"
}

# Install APK from Downloads
install_from_downloads() {
    print_status "Looking for APK in Downloads..."
    
    # Find latest APK in Downloads
    DOWNLOAD_DIR="/storage/emulated/0/Download"
    LATEST_APK=$(ls -t $DOWNLOAD_DIR/CloudCounter*.apk 2>/dev/null | head -1)
    
    if [ -n "$LATEST_APK" ]; then
        print_status "Found APK: $(basename $LATEST_APK)"
        print_info "Opening APK installer..."
        
        # Open APK with package installer
        am start -a android.intent.action.VIEW \
            -d "file://$LATEST_APK" \
            -t "application/vnd.android.package-archive"
    else
        print_error "No CloudCounter APK found in Downloads!"
    fi
}

# Git operations
git_status() {
    print_status "Git status:"
    git status --short
}

git_commit_push() {
    print_status "Committing and pushing changes..."
    
    read -p "Enter commit message: " msg
    git add -A
    git commit -m "$msg"
    git push origin main
    
    print_status "Changes pushed to GitHub!"
}

# Trigger GitHub Actions cloud build
trigger_github_cloud_build() {
    local build_type=$1
    print_status "Triggering GitHub Actions cloud build ($build_type)..."
    
    # First, make sure we're pushed to GitHub
    print_info "Ensuring latest code is on GitHub..."
    git push origin main 2>/dev/null || git push origin HEAD 2>/dev/null
    
    # Trigger GitHub Actions workflow using gh CLI or curl
    if command -v gh &> /dev/null; then
        # Use GitHub CLI if available
        gh workflow run firebase-build.yml \
            -f build_type="$build_type" \
            -f distribution_notes="Mobile trigger - $(date '+%Y-%m-%d %H:%M')"
        
        if [ $? -eq 0 ]; then
            print_status "Cloud build triggered via GitHub Actions!"
            print_info "Check email in 5-10 minutes for the APK"
        else
            print_error "Failed to trigger GitHub Actions"
        fi
    else
        # Alternative: provide manual instructions
        print_info "To trigger cloud build manually:"
        print_info "1. Open: https://github.com/$GITHUB_USER/$REPO_NAME/actions"
        print_info "2. Click 'Firebase Cloud Build & Distribution'"
        print_info "3. Click 'Run workflow'"
        print_info "4. Select build type: $build_type"
        print_info "5. Click 'Run workflow' button"
        print_info ""
        print_info "APK will be emailed to $EMAIL in 5-10 minutes"
    fi
}

# Main menu
show_menu() {
    echo ""
    echo "========================================="
    echo "   Cloud Counter Mobile Workflow"
    echo "   Running on: $(uname -m)"
    echo "========================================="
    echo "1) Setup Termux Environment (first time)"
    echo "2) Sync Repository (pull latest)"
    echo "3) Show Git Status"
    echo "4) Commit & Push Changes"
    echo "5) Trigger Cloud Build (Debug)"
    echo "6) Trigger Cloud Build (Release)"
    echo "7) GitHub Actions Cloud Build (Debug)"
    echo "8) GitHub Actions Cloud Build (Release)"
    echo "9) Check Email for APK"
    echo "10) Install APK from Downloads"
    echo "11) Full Workflow (Sync → Build → Install)"
    echo "0) Exit"
    echo "========================================="
}

# Full workflow
full_workflow() {
    print_status "Running full mobile workflow..."
    sync_repo
    trigger_cloud_build "debug"
    sleep 5
    download_apk
    print_info "Once downloaded, run option 8 to install"
}

# Main
main() {
    check_termux
    
    while true; do
        show_menu
        read -p "Select option: " choice
        
        case $choice in
            1) setup_termux ;;
            2) sync_repo ;;
            3) git_status ;;
            4) git_commit_push ;;
            5) trigger_cloud_build "debug" ;;
            6) trigger_cloud_build "release" ;;
            7) trigger_github_cloud_build "debug" ;;
            8) trigger_github_cloud_build "release" ;;
            9) download_apk ;;
            10) install_from_downloads ;;
            11) full_workflow ;;
            0) exit 0 ;;
            *) print_error "Invalid option!" ;;
        esac
        
        echo ""
        read -p "Press Enter to continue..."
    done
}

# Run
main