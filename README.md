# StudySync - Student Academic Management System

StudySync is a comprehensive Android application designed to help students manage their academic life efficiently. It provides features for course management, task tracking, resource organization, and more, all while maintaining synchronization between local storage and cloud services.

## Features

### 1. User Management
- User authentication using Firebase Authentication
- Profile management with university and semester information
- Secure login and registration system
- Automatic session management

### 2. Course Management
- Add, edit, and delete courses
- Course details include:
  - Course name and code
  - Instructor information
  - Class schedule (days and times)
  - Room number
  - Semester information
  - Credit hours
  - Custom color coding
- Course filtering by semester
- Course search functionality
- Course detail view with associated tasks and resources

### 3. Task Management
- Create, edit, and delete tasks
- Task features include:
  - Title and description
  - Due date and time
  - Priority levels
  - Task status (Pending, In Progress, Completed)
  - Task type categorization
  - Grade tracking
  - Course association
- Task filtering options:
  - By status
  - By priority
  - By course
  - By due date
- Search functionality
- Task reminders and notifications

### 4. Resource Management
- Support for multiple resource types:
  - Notes
  - Images
  - Documents
  - Links
- Resource features:
  - Title and description
  - Course association
  - Tagging system
  - File attachments
  - Thumbnail generation for images
- Resource organization and search
- File upload and download capabilities

### 5. Dashboard
- Overview of academic progress
- Upcoming tasks display
- Course statistics
- Quick access to important features
- Visual progress indicators

### 6. Notifications System
- Local notifications for:
  - Task reminders
  - Due date alerts
  - Course updates
  - Resource additions
- Customizable notification settings
- Multiple reminder time options:
  - 15 minutes before
  - 30 minutes before
  - 1 hour before
  - 1 day before
- Notification preferences management

### 7. Data Synchronization
- Offline-first architecture
- Automatic sync with Firebase Firestore
- Local SQLite database for offline access
- Conflict resolution
- Background sync service
- Sync status indicators

### 8. Settings
- Theme customization
- Notification preferences
- Default reminder settings
- Data management options
- User profile settings

## Technical Features

### Architecture
- MVVM (Model-View-ViewModel) architecture
- Repository pattern implementation
- Clean architecture principles
- Dependency injection

### Data Management
- Local SQLite database
- Firebase Firestore integration
- Firebase Storage for file management
- Data synchronization service
- Offline data persistence

### UI/UX
- Material Design implementation
- Responsive layouts
- Dark/Light theme support
- Custom animations and transitions
- Intuitive navigation

### Security
- Firebase Authentication
- Secure data storage
- Encrypted local database
- Secure file handling

### Performance
- Efficient data caching
- Optimized image handling
- Background processing
- Memory management
- Battery optimization

## Requirements

### Development
- Android Studio Arctic Fox or newer
- Kotlin 1.6.0 or newer
- Minimum SDK: 21 (Android 5.0)
- Target SDK: Latest stable version
- Gradle 7.0 or newer

### Dependencies
- Firebase (Authentication, Firestore, Storage)
- Retrofit for API calls
- Glide for image loading
- MPAndroidChart for statistics
- OneSignal for push notifications
- Coroutines for asynchronous operations
- Room for local database
- Navigation Component
- Material Design Components

## Setup Instructions

1. Clone the repository
2. Open the project in Android Studio
3. Set up Firebase project and add google-services.json
4. Configure OneSignal for push notifications
5. Build and run the application

## Contributing

1. Fork the repository
2. Create your feature branch
3. Commit your changes
4. Push to the branch
5. Create a Pull Request

## License

This project is licensed under the MIT License - see the LICENSE file for details.

## Acknowledgments

- Firebase for backend services
- Material Design for UI components
- MPAndroidChart for statistics visualization
- OneSignal for push notification services
