# ID3 v2.4 → v2.3 Converter (Android)

## Overview

An Android tool that converts ID3 tags in MP3 files from version 2.4 to version 2.3.

This is designed for Sony Walkman (Android-based) music players, which only support ID3v2.3 tags. MP3 files with ID3v2.4 tags may not display metadata correctly on these devices.

## Features

- Recursively scans a selected folder (including subfolders) for MP3 files
- Converts ID3v2.4 tags to ID3v2.3
- Save mode selection:
  - Overwrite original files
  - Save as new files (appends `_v23` to filename)
- **Pause & Resume**: Interrupt conversion at any time and resume from where you left off
- Progress tracking persisted to storage (survives app restart)
- Real-time progress bar and log output
- Optimized for large libraries (tested with 30,000+ files)

## Technical Specs

- **Min SDK**: Android 8.0 (API 26)
- **Target SDK**: Android 14 (API 34)
- **Language**: Kotlin
- **ID3 Tag Library**: JAudioTagger 3.0.1
- **UI**: Material Components

## Build

1. Open the project in Android Studio
2. Run Gradle Sync
3. Build > Make Project
4. Run on device

## Usage

1. Launch the app
2. Tap "Select Folder" and choose the folder containing your MP3 files
3. Select save mode (overwrite or save as new file)
4. Optionally check "Resume from last progress" if continuing a previous session
5. Tap "Start Conversion"
6. Confirm in the dialog
7. To stop midway, tap the "Cancel" button — progress is automatically saved

## Pause & Resume

When dealing with large music libraries, you can interrupt the conversion at any time:

- Press the **Cancel** button during conversion
- Progress is saved immediately (per-file granularity)
- On next launch, select the same folder and check **"Resume from last progress"**
- Already-converted files are skipped automatically
- Use **"Clear Progress"** to start over from scratch

## Notes

- On Android 11+, "All Files Access" permission is required
- In overwrite mode, original files are modified — back up your files first
- MP3 files without ID3v2.4 tags are skipped (no changes made)
- File processing order is sorted by path for deterministic resume behavior

## Project Structure

```
app/
├── src/main/
│   ├── AndroidManifest.xml
│   ├── java/com/example/id3converter/
│   │   ├── MainActivity.kt       # Main UI, permissions, folder picker
│   │   ├── Id3Converter.kt       # ID3 tag conversion logic
│   │   └── ProgressTracker.kt    # Pause/resume progress persistence
│   └── res/
│       ├── layout/activity_main.xml
│       └── values/
│           ├── strings.xml
│           ├── colors.xml
│           └── themes.xml
├── build.gradle.kts
└── proguard-rules.pro
```

## License

MIT
