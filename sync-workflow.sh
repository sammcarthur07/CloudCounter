#!/bin/bash

# Sync Workflow Helper
# For seamless switching between computer and mobile development

GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

print_status() {
    echo -e "${GREEN}[SYNC]${NC} $1"
}

print_warning() {
    echo -e "${YELLOW}[INFO]${NC} $1"
}

# Auto-detect environment
detect_environment() {
    if [ -d "/data/data/com.termux" ]; then
        echo "mobile"
    else
        echo "computer"
    fi
}

ENV=$(detect_environment)

# Sync from remote
sync_from_remote() {
    print_status "Pulling latest changes from GitHub..."
    git fetch origin
    git pull origin main --no-edit
    
    if [ $? -eq 0 ]; then
        print_status "Successfully synced from GitHub!"
        git log --oneline -5
    else
        print_warning "Sync failed. You may need to resolve conflicts."
    fi
}

# Save and push current work
save_current_work() {
    print_status "Saving current work to GitHub..."
    
    # Check if there are changes
    if [ -z "$(git status --porcelain)" ]; then
        print_warning "No changes to save."
        return
    fi
    
    # Add all changes
    git add -A
    
    # Commit with environment-specific message
    if [ "$ENV" == "mobile" ]; then
        git commit -m "Mobile update - $(date '+%Y-%m-%d %H:%M')"
    else
        git commit -m "Computer update - $(date '+%Y-%m-%d %H:%M')"
    fi
    
    # Push to GitHub
    git push origin main
    
    if [ $? -eq 0 ]; then
        print_status "Work saved and pushed to GitHub!"
    else
        print_warning "Push failed. Try 'git pull' first."
    fi
}

# Quick switch workflow
quick_switch() {
    print_status "Preparing for environment switch..."
    print_status "Current environment: $ENV"
    
    # Save current work
    save_current_work
    
    print_status "Ready to switch!"
    print_warning "On the other device, run: ./sync-workflow.sh pull"
}

# Main menu
case "$1" in
    pull|sync)
        sync_from_remote
        ;;
    push|save)
        save_current_work
        ;;
    switch)
        quick_switch
        ;;
    *)
        echo "Usage: ./sync-workflow.sh [pull|push|switch]"
        echo ""
        echo "Commands:"
        echo "  pull   - Pull latest changes from GitHub"
        echo "  push   - Save and push current work to GitHub"
        echo "  switch - Prepare to switch to other device"
        echo ""
        echo "Current environment: $ENV"
        ;;
esac