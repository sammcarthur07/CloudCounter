#!/data/data/com.termux/files/usr/bin/bash

# Simple Mobile Workflow for Cloud Counter
# Colors
GREEN='\033[0;32m'
BLUE='\033[0;34m'
RED='\033[0;31m'
NC='\033[0m'

echo -e "${GREEN}Cloud Counter Mobile Workflow${NC}"
echo "=================================="
echo "1) Setup Termux (first time only)"
echo "2) Clone/Update Repository" 
echo "3) Build Debug APK (GitHub Actions)"
echo "4) Build Release APK (GitHub Actions)"
echo "5) Check Build Status"
echo "6) Open Downloads Folder"
echo "0) Exit"
echo "=================================="

read -p "Choose option: " choice

case $choice in
    1)
        echo -e "${BLUE}Setting up Termux...${NC}"
        pkg update -y
        pkg install -y git curl wget nodejs-lts
        echo -e "${GREEN}Setup complete!${NC}"
        ;;
    2)
        echo -e "${BLUE}Cloning/Updating repository...${NC}"
        cd ~
        if [ -d "CloudCounter" ]; then
            cd CloudCounter && git pull origin main
        else
            git clone https://github.com/sammcarthur07/CloudCounter.git
        fi
        echo -e "${GREEN}Repository ready!${NC}"
        ;;
    3)
        echo -e "${BLUE}Triggering Debug Build...${NC}"
        echo "Go to: https://github.com/sammcarthur07/CloudCounter/actions"
        echo "Click 'Build APK and Upload to Firebase'"
        echo "Click 'Run workflow'"
        echo "Select 'Debug' build type"
        echo "Click 'Run workflow' button"
        echo -e "${GREEN}Check your email in 5-10 minutes!${NC}"
        ;;
    4)
        echo -e "${BLUE}Triggering Release Build...${NC}"
        echo "Go to: https://github.com/sammcarthur07/CloudCounter/actions"
        echo "Click 'Build Signed APK for Play Store'"
        echo "Click 'Run workflow'"
        echo "Choose version bump type (patch/minor/major)"
        echo "Add release notes"
        echo "Click 'Run workflow' button"
        echo -e "${GREEN}Signed APK will be ready in 5-10 minutes!${NC}"
        ;;
    5)
        echo -e "${BLUE}Opening GitHub Actions...${NC}"
        echo "Check status at: https://github.com/sammcarthur07/CloudCounter/actions"
        ;;
    6)
        echo -e "${BLUE}Opening Downloads folder...${NC}"
        am start -a android.intent.action.VIEW -d "file:///storage/emulated/0/Download"
        ;;
    0)
        exit 0
        ;;
    *)
        echo -e "${RED}Invalid option!${NC}"
        ;;
esac