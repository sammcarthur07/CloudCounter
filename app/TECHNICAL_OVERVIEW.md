# CloudCounter Technical Overview

## Table of Contents
1. [Architecture](#architecture)
2. [Project Structure](#project-structure)
3. [Key Technologies](#key-technologies--libraries)
4. [Data Models](#data-models)
5. [Feature Implementations](#key-features-implementation)
6. [Security & Privacy](#security--privacy)
7. [Performance](#performance-optimizations)
8. [Development Guidelines](#development-guidelines)

## Architecture
- **Language**: Kotlin
- **Platform**: Android Native
- **Min SDK**: 21 (Android 5.0 Lollipop)
- **Target SDK**: 35 (Android 15)
- **Architecture Pattern**: MVVM (Model-View-ViewModel) with Repository pattern
- **Build System**: Gradle with Kotlin DSL

## Project Structure
```
CloudCounter/
├── app/
│   └── src/main/java/com/sam/cloudcounter/
│       ├── Activities/
│       │   ├── MainActivity.kt (Main navigation hub)
│       │   └── GiantCounterActivity.kt (Full-screen counter)
│       ├── Fragments/
│       │   ├── SeshFragment.kt (Session tracking)
│       │   ├── StatsFragment.kt (Statistics display)
│       │   ├── GoalFragment.kt (Goal management)
│       │   ├── ChatFragment.kt (Chat & video)
│       │   ├── StashFragment.kt (Inventory tracking)
│       │   ├── GraphFragment.kt (Data visualization)
│       │   ├── HistoryFragment.kt (Session history)
│       │   ├── AboutFragment.kt (App info)
│       │   └── InboxFragment.kt (Support messages)
│       ├── ViewModels/
│       │   ├── StatsViewModel.kt
│       │   ├── GoalViewModel.kt
│       │   ├── ChatViewModel.kt
│       │   ├── StashViewModel.kt
│       │   ├── SessionStatsViewModel.kt
│       │   └── GraphViewModel.kt
│       ├── Data/
│       │   ├── AppDatabase.kt (Room database)
│       │   ├── DAOs/ (Data Access Objects)
│       │   └── Entities/ (Data models)
│       ├── Services/
│       │   ├── ChatListenerService.kt
│       │   ├── VideoCallService.kt
│       │   ├── CloudSyncService.kt
│       │   └── SessionSyncService.kt
│       └── Utils/
│           ├── NotificationHelper.kt
│           ├── ConfettiHelper.kt
│           └── WebRTCManager.kt
```

## Key Technologies & Libraries

### Core Android
- **AndroidX**: Core KTX, AppCompat, ConstraintLayout
- **Material Design 3**: Latest Material components
- **View Binding**: Type-safe view references
- **Lifecycle Components**: ViewModel, LiveData, Coroutines

### Data Persistence
- **Room Database**: Local SQLite abstraction
- **SharedPreferences**: User settings storage
- **Type Converters**: Complex data type storage

### Networking & Cloud
- **Firebase Realtime Database**: Real-time sync
- **Firebase Auth**: User authentication
- **Firebase Firestore**: Cloud data storage
- **WebRTC**: Video calling (Stream WebRTC Android)

### UI/UX Libraries
- **MPAndroidChart**: Data visualization
- **Konfetti**: Celebration animations
- **CardView**: Material card layouts
- **SwipeRefreshLayout**: Pull-to-refresh

### Async Operations
- **Kotlin Coroutines**: Async programming
- **Flow**: Reactive data streams
- **LiveData**: Observable data holders

## Data Models

### Core Entities
- **ActivityLog**: Records of tracked activities
- **Smoker**: User/participant profiles
- **SessionSummary**: Aggregated session data
- **Goal**: User-defined achievement targets
- **ChatMessage**: Chat communication records
- **StashEntry**: Inventory tracking records
- **CustomActivity**: User-defined activity types

### Database Schema
- **Primary Database**: Room with 10+ tables
- **Relationships**: One-to-many, many-to-many
- **Migrations**: Handled via Room migrations
- **Backup**: Auto-backup rules configured

## Key Features Implementation

### Real-time Collaboration
- WebRTC for peer-to-peer video
- Firebase Realtime Database for signaling
- Room-based session sharing with codes
- Presence detection and user status

### Activity Tracking System
- Foreground service for persistent tracking
- Notification controls for quick actions
- Turn-based rotation management
- Auto-add functionality with timers

### Statistics Engine
- Complex aggregation queries
- Time-period filtering
- Multi-dimensional data analysis
- Cached calculations for performance

## Security & Privacy

### Authentication
- Firebase Auth integration
- Google Sign-In support
- Anonymous usage option
- Password protection for sensitive features

### Data Protection
- Local storage encryption
- Secure cloud transmission
- User-controlled data sharing
- Private session options

## Performance Optimizations

### Caching Strategy
- LiveData caching for UI updates
- Room database query optimization
- Lazy loading for large datasets
- View recycling in lists

### Background Processing
- Coroutines for async operations
- Foreground services for persistence
- Work Manager for scheduled tasks
- Efficient battery usage

## UI/UX Implementation

### Material Design 3
- Dynamic theming
- Responsive layouts
- Adaptive UI components
- Gesture navigation support

### Animations & Effects
- Konfetti celebration animations
- Custom progress animations
- Shimmer loading effects
- Smooth transitions

### Accessibility
- Screen reader support
- Large touch targets
- High contrast options
- Keyboard navigation

## Testing & Quality

### Build Configuration
- Debug and Release variants
- ProGuard optimization
- Resource shrinking
- APK size optimization

### Continuous Integration
- GitHub Actions workflow
- Firebase App Distribution
- Automated testing
- Version management

## API Integration

### Firebase Services
- Realtime Database for sync
- Firestore for cloud storage
- Authentication services
- Cloud Functions support

### WebRTC Implementation
- Stream WebRTC Android SDK
- Peer-to-peer connections
- STUN/TURN server support
- Audio/Video codec handling

## Data Flow Architecture

### MVVM Pattern
```
View (Fragment/Activity)
    ↓↑ Data Binding
ViewModel
    ↓↑ LiveData/Flow
Repository
    ↓↑ Coroutines
Data Sources (Room/Firebase)
```

### State Management
- ViewModel persistence
- SavedStateHandle for process death
- SharedPreferences for settings
- Room for structured data

## Feature Modules

### Activity Tracking Module
- Real-time counter management
- Session state persistence
- Auto-add timer logic
- Turn rotation system

### Statistics Module
- Data aggregation engine
- Time-period calculations
- Per-user analytics
- Performance metrics

### Goals Module
- Goal definition system
- Progress tracking
- Achievement detection
- Notification triggers

### Social Module
- Chat messaging system
- Video calling infrastructure
- Room management
- User presence tracking

### Inventory Module
- Stash tracking system
- Cost calculations
- Distribution analytics
- Ratio-based operations

## Development Guidelines

### Code Organization
- Feature-based packaging
- Clear separation of concerns
- Dependency injection ready
- Testable architecture

### Naming Conventions
- Kotlin naming standards
- Descriptive variable names
- Consistent prefixing
- Clear function purposes

### Documentation
- KDoc comments for public APIs
- README files for modules
- Architecture decision records
- Change logs maintained

## Deployment & Distribution

### Build Process
```bash
# Debug build
./gradlew assembleDebug

# Release build
./gradlew assembleRelease

# Run tests
./gradlew test

# Generate signed APK
./gradlew assembleRelease -Pkeystore=path/to/keystore
```

### Release Configuration
- Version code: 16
- Version name: "16.0"
- Min SDK: 21 (Android 5.0)
- Target SDK: 35 (Android 15)
- ProGuard enabled for release
- Resource shrinking enabled

### Firebase App Distribution
- Automated distribution via GitHub Actions
- Test builds for internal testing
- Production releases via Google Play

## Database Schema Summary

### Core Tables
- `activity_logs` - Individual activity entries
- `smokers` - User/participant profiles
- `session_summaries` - Aggregated session data
- `goals` - User-defined targets
- `chat_messages` - Communication records
- `stash_entries` - Inventory records
- `custom_activities` - User-created activity types

### Relationships
- ActivityLog → Smoker (many-to-one)
- SessionSummary → Smoker (many-to-many)
- Goal → Smoker (many-to-many)
- ChatMessage → User (many-to-one)

## Future Enhancements

### Planned Features
- Data export functionality
- Advanced analytics dashboard
- Machine learning predictions
- Wearable device integration
- Cross-platform support
- API for third-party integrations

### Technical Improvements
- Migration to Compose UI
- Dependency injection with Hilt
- Modularization for features
- Enhanced offline capabilities
- Performance monitoring
- A/B testing framework

## Troubleshooting Guide

### Common Issues
1. **Build failures**: Clean and rebuild project
2. **Sync issues**: Check internet connection
3. **Database migration**: Verify schema versions
4. **WebRTC issues**: Check permissions and firewall

### Debug Tools
- Android Studio Profiler
- Firebase Crashlytics
- Network inspection with Stetho
- Database inspection tools

## Contributing

### Setup Instructions
1. Clone repository
2. Open in Android Studio
3. Sync Gradle files
4. Configure Firebase (google-services.json)
5. Run on device/emulator

### Code Standards
- Follow Kotlin style guide
- Write unit tests for new features
- Document public APIs
- Create pull requests for review

---

*Last Updated: 2025*
*Version: 16.0*
*Author: CloudCounter Development Team*

---
*Last updated: Analysis in progress*