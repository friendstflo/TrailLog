# TrailLog

**Trail Maintenance Reporting App**  
for **The Mountaineers – Everett Lookout Trail Maintenance Committee**

https://www.mountaineers.org/locations-lodges/everett-branch/committees/everett-lookout-trail-maintenance-committee

### Features
- **Offline-first**: long-press to add a pin, select type (Log / Brush / Treadwork / Other), severity (Low / Medium / High), landowner/team, diameter (inches) or length (feet), take photo — all works fully offline
- **Real-time Firebase sync**: pins, photos, and status updates sync across crew when online
- **Completed toggle**: green check icon on map, "COMPLETE" tag in reports list, moves to bottom
- **Reports tab**: photo thumbnails, date, quantity (inches for logs), landowner, colored severity tag, clickable to jump to pin on map
- **Stats dashboard**: live totals (reports, cleared, pending, logs removed, brushing ft, treadwork ft) — filtered by selected landowner
- **Settings**: set crew name, select default landowner/team filter (persists across app restarts), force sync, last sync time + total reports count
- **Map**: My Location (max zoom), Pin Here (drops pin at current GPS), startup centering on user location or Three Fingers fallback
- **Pull-to-refresh** on Reports tab

### Installation
1. Download **TrailLog-v1.0.apk** from Releases
2. Install on Android 8.0+ (allow unknown sources)
3. Grant camera & location permissions
4. Pre-cache maps on WiFi before heading out (pan/zoom over Everett Lookout area)

### Development
- Android Studio (latest stable)
- Kotlin + Firebase (Firestore + Storage) + OSMdroid + Room + WorkManager
- Clone: `git clone https://github.com/friendstflo/TrailLog.git`

### Credits
Built with ❤️ for trail safety and efficiency by Dan Renfrow (@renfnut)  
Feedback / bugs → Issues tab

Made possible by The Mountaineers Everett Lookout Trail Maintenance Committee.