Smart-Door
Smart-Door is an Android application that simulates a smart door system using CameraX and ML Kit Face Detection.

The app uses the device camera to detect the presence of faces and automatically toggles the "door" state (open/close), showing updates via Toast messages. It’s lightweight, works offline, and demonstrates how to integrate CameraController with ML Kit Analyzer in a modern, recommended way.

Features
📷 Background Camera Monitoring – Runs continuously using CameraX.
🙂 Face Detection (not recognition) – Detects when any face appears in view.
🚪 Automatic Door Simulation
Door opens when a face is detected.
Door closes when no face is detected for ~1.2 seconds.
🛑 Debounce Logic – Prevents spammy/flickering Toasts.
🎛 Manual Toggle – Always shows "Door opened/closed" Toast when toggled.
⚡ Optimized Performance – Uses PERFORMANCE_MODE_FAST for smooth UX.
Tech Stack
Android (Kotlin)
CameraX – v1.4.2 or newer (stable, includes camera-mlkit-vision).
ML Kit Face Detection – On-device API, free to use.
How It Works
Camera runs in the background via CameraController.
Face detected → Door Opens (Toast shown).
Face disappears for ~1.2s → Door Closes (Toast shown).
Debounce ensures smooth detection without flicker.
⚠️ This app performs face detection (presence only), not identity recognition.

📝 Notes & Gotchas
Free API / Offline Use

ML Kit runs fully on-device and is free.
If using the Play Services dependency, the face model downloads once at install time.
For true offline support from first launch, use:
implementation "com.google.mlkit:face-detection"
Performance Tips

Using PERFORMANCE_MODE_FAST for responsive UX.
On older devices, reduce preview lag by setting a target resolution on analysis or letting CameraX choose a lower resolution.
