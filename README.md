# TrailLog

**Trail Maintenance Reporting App**  
For **The Mountaineers – Everett Lookout Trail Maintenance Committee**

https://www.mountaineers.org/locations-lodges/everett-branch/committees/everett-lookout-trail-maintenance-committee

### Features
- Offline pin creation with photo, type, severity, quantity (logs or feet)
- Real-time Firebase sync (pins, photos, status updates)
- Completed toggle (green check icon, moves to bottom of list)
- Reports tab with thumbnails, date, quantity, severity
- Stats dashboard with totals
- Settings: crew name + CSV export
- My Location button
- Pull-to-refresh on Reports

### Installation
1. Download **TrailLog-v1.0.apk** from Releases
2. Transfer to Android phone
3. Tap APK → Install (allow unknown sources)
4. Open app → grant camera/location permissions

Pre-cache map tiles: Open Map tab on WiFi, pan/zoom over Everett Lookout area.

### Screenshots

(Add images here – upload to repo and link)

### Development
- Android Studio (Flamingo or later)
- Kotlin + Firebase + OSMdroid + Room + WorkManager
- Clone: `git clone https://github.com/friendstflo/TrailLog.git`

Feedback / bugs → Issues or contact Dan Renfrow

Made with ❤️ for trail maintenance.