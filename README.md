# MockRoute — build & install

This is a complete Android Studio project. It moves your device's GPS from a
start address to an end address in a straight line at a chosen speed.

## Fastest path to an APK

### Option A — Android Studio (recommended)
1. Install Android Studio (free).
2. File → Open → select this `MockRoute` folder.
3. Let it sync (downloads Gradle + SDK automatically).
4. Build → Build App Bundle(s)/APK(s) → **Build APK(s)**.
5. Click "locate" → APK is at `app/build/outputs/apk/debug/app-debug.apk`.
6. Copy to phone, tap to install (allow "install from unknown sources").

### Option B — Command line
Requires the Android SDK + JDK 17 installed, then from this folder:
```
gradle wrapper        # regenerates the wrapper jar once
./gradlew assembleDebug
```
APK: `app/build/outputs/apk/debug/app-debug.apk`

## Using the app
1. On the phone: Settings → About phone → tap "Build number" 7x (enables
   Developer Options).
2. Settings → System → Developer Options → **Select mock location app** →
   MockRoute.
3. Open MockRoute, grant location permission.
4. Type start + end address, set speed, tap **Start moving**.

## Notes
- Movement is straight-line (great for testing). To follow real roads, plug a
  routing API (OSRM/Mapbox/Google Directions) into MockService.drive() — it just
  needs a list of waypoints instead of two points.
- Banking / anti-cheat / driver apps often detect mock locations and reject them.
