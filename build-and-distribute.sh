#!/bin/bash

# Cloud Counter Build and Distribution Script
# For Firebase App Distribution & Local Testing

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Configuration
PROJECT_DIR="CloudCounter"
APK_OUTPUT_DIR="CloudCounter/app/build/outputs/apk"
EMAIL="mcarthur.sp@gmail.com"
FIREBASE_PROJECT="cloudcounter-sam"

# Function to print colored output
print_status() {
    echo -e "${GREEN}[BUILD]${NC} $1"
}

print_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

print_warning() {
    echo -e "${YELLOW}[WARNING]${NC} $1"
}

# Function to build APK
build_apk() {
    local build_type=$1
    print_status "Building $build_type APK..."
    
    cd $PROJECT_DIR || exit 1
    
    if [ "$build_type" == "debug" ]; then
        ./gradlew assembleDebug
    else
        ./gradlew assembleRelease
    fi
    
    if [ $? -eq 0 ]; then
        print_status "Build successful!"
        return 0
    else
        print_error "Build failed!"
        return 1
    fi
}

# Function to distribute via Firebase App Distribution
distribute_firebase() {
    local build_type=$1
    print_status "Distributing $build_type APK via Firebase App Distribution..."
    
    cd $PROJECT_DIR || exit 1
    
    if [ "$build_type" == "debug" ]; then
        ./gradlew appDistributionUploadDebug
    else
        ./gradlew appDistributionUploadRelease
    fi
    
    if [ $? -eq 0 ]; then
        print_status "APK distributed successfully to $EMAIL"
        return 0
    else
        print_error "Distribution failed!"
        return 1
    fi
}

# Function to copy APK to Downloads (for mobile testing)
copy_to_downloads() {
    local build_type=$1
    print_status "Copying APK to Downloads folder..."
    
    if [ "$build_type" == "debug" ]; then
        APK_FILE="$APK_OUTPUT_DIR/debug/app-debug.apk"
    else
        APK_FILE="$APK_OUTPUT_DIR/release/app-release.apk"
    fi
    
    if [ -f "$APK_FILE" ]; then
        cp "$APK_FILE" ~/Downloads/CloudCounter-$build_type-$(date +%Y%m%d-%H%M%S).apk
        print_status "APK copied to Downloads folder"
        return 0
    else
        print_error "APK file not found!"
        return 1
    fi
}

# Function to install APK directly (when connected via ADB)
install_apk() {
    local build_type=$1
    print_status "Installing APK via ADB..."
    
    if [ "$build_type" == "debug" ]; then
        APK_FILE="$APK_OUTPUT_DIR/debug/app-debug.apk"
    else
        APK_FILE="$APK_OUTPUT_DIR/release/app-release.apk"
    fi
    
    if [ -f "$APK_FILE" ]; then
        adb install -r "$APK_FILE"
        if [ $? -eq 0 ]; then
            print_status "APK installed successfully!"
            return 0
        else
            print_error "Installation failed!"
            return 1
        fi
    else
        print_error "APK file not found!"
        return 1
    fi
}

# Main menu
show_menu() {
    echo ""
    echo "========================================="
    echo "   Cloud Counter Build & Distribution"
    echo "========================================="
    echo "1) Build Debug APK"
    echo "2) Build Release APK"
    echo "3) Build Debug & Distribute via Firebase"
    echo "4) Build Release & Distribute via Firebase"
    echo "5) Build Debug & Copy to Downloads"
    echo "6) Build Debug & Install via ADB"
    echo "7) Quick Build & Email (Debug)"
    echo "8) Quick Build & Email (Release)"
    echo "9) Exit"
    echo "========================================="
}

# Main script
main() {
    while true; do
        show_menu
        read -p "Select option: " choice
        
        case $choice in
            1)
                build_apk "debug"
                ;;
            2)
                build_apk "release"
                ;;
            3)
                build_apk "debug" && distribute_firebase "debug"
                ;;
            4)
                build_apk "release" && distribute_firebase "release"
                ;;
            5)
                build_apk "debug" && copy_to_downloads "debug"
                ;;
            6)
                build_apk "debug" && install_apk "debug"
                ;;
            7)
                print_status "Quick build and email (Debug)..."
                build_apk "debug" && distribute_firebase "debug"
                ;;
            8)
                print_status "Quick build and email (Release)..."
                build_apk "release" && distribute_firebase "release"
                ;;
            9)
                print_status "Exiting..."
                exit 0
                ;;
            *)
                print_error "Invalid option!"
                ;;
        esac
        
        echo ""
        read -p "Press Enter to continue..."
    done
}

# Check if we're in the right directory
if [ ! -d "$PROJECT_DIR" ]; then
    print_error "CloudCounter directory not found!"
    print_warning "Please run this script from the AndroidStudioProjects/CloudCounter directory"
    exit 1
fi

# Run main function
main