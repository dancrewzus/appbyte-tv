# Configuration

## Build-time settings (`launcher/local.properties`)

Compiled into the app as `BuildConfig` fields. `local.properties` is git-ignored; you can also pass any of them as `-Pkey=value` to Gradle (the file wins if both exist). Every key is optional.

| Key | Default | Effect |
|---|---|---|
| `appbyte.hubUrl` | *(empty)* | URL of the optional home hub, e.g. `http://192.168.1.10:8190` (no trailing slash). Empty = no house cards, no light buttons, no Jellyfin row, no status page. |
| `appbyte.userName` | *(empty)* | First letter is shown in the avatar circle. |
| `appbyte.avatarName` | `me` | Name sent to the hub in `/api/tv/avatar?name=…&own=1`. |
| `appbyte.lat`, `appbyte.lon` | `0.0` | Your coordinates. Used for the weather (Open-Meteo) and the sun position. `0/0` = decide day/sunset/night by the clock and show no weather. |
| `appbyte.liveTv` | *(empty)* | `package/Activity` of the TV's live-TV app. Empty hides the "TV en vivo" tab. See [INSTALL.md](INSTALL.md#finding-your-tvs-live-tv-activity). |

`sdk.dir` (your Android SDK path) also lives in that file; Android Studio writes it.

## Permissions

| Permission | Why | How it is granted |
|---|---|---|
| `INTERNET`, `ACCESS_NETWORK_STATE` | hub, weather, network type | install time |
| `QUERY_ALL_PACKAGES` | list the apps to show | install time |
| `READ_TV_LISTINGS` | read "continue watching" from the Android TV provider | install time (automatic) |
| `ACCESS_WIFI_STATE` | band and link speed | install time |
| `ACCESS_FINE_LOCATION` | **only** so Android reveals the Wi-Fi *name* | `adb shell pm grant …` (done by `install.sh`); also needs the TV's location setting on |
| notification-listener access (`MediaListener`) | read the Spotify media session for the mini player | `adb shell cmd notification allow_listener cl.appbyte.tv/cl.appbyte.tv.MediaListener` (done by `install.sh`) |
| `BIND_DREAM_SERVICE` (service) | the screensaver | `settings put secure screensaver_components …` (`install.sh --screensaver`) |

The app does **not** read notifications' content: `MediaListener` is an empty service; enabling it is only what Android requires to open `MediaSessionManager.getActiveSessions()`.

## Preferences stored on the TV

`SharedPreferences` file `launcher` (inspect: `adb shell run-as cl.appbyte.tv cat shared_prefs/launcher.xml`, debug builds only):

| Key | Meaning |
|---|---|
| `clean` | `true` = *Abstracto*, `false` = *Paisaje* |
| `order` | comma-separated package list of the home row when you moved apps by hand (empty = automatic, most-used first) |
| `n_<package>` | how many times the app was opened from the launcher |

Reset the order from the app: hold **OK** on any app → *Orden automático*.

## Wallpapers and logos

Not shipped (licences). Put them in `launcher/app/src/main/assets/` and rebuild.

| File | Format | Notes |
|---|---|---|
| `bg_day_1.jpg`, `bg_sunset_1.jpg`, `bg_night_1.jpg` | JPEG, **1920×1080**, < 1 MB each | More files (`bg_day_2.jpg`, …) rotate by day of the year. 4K images work but cost memory: downscale them. Sunset is used both at dawn and dusk. |
| `logos/<package.name>.png` | square PNG with rounded corners, ≥ 320 px | Replaces the generic banner of that app (e.g. `logos/com.disney.disneyplus.png`). Apps without a file use the banner supplied by the app itself. |

If no `bg_*.jpg` exists, only the *Abstracto* theme is offered.

## Intent extras (testing)

| Command | Effect |
|---|---|
| `adb shell am start -S -n cl.appbyte.tv/.HomeActivity --es period day` | force `day`, `sunset` or `night` |
| `adb shell am start -S -n cl.appbyte.tv/.HomeActivity --ez demo true` | demo mode: made-up house, weather, Wi-Fi, posters; always *Abstracto*; no network |

## Changing the home row apps

`PREFERRED` in `Apps.kt` lists the package names, in default order, of the apps shown first (Netflix, YouTube TV, Jellyfin, Prime Video, Disney+, Spotify, Play Store). Missing ones are skipped. Edit it and rebuild.

Find a package name: `adb shell pm list packages | grep -i <name>`.

## Strings

The UI text is Spanish and lives as literals in the Kotlin sources. To translate: search for the quoted Spanish strings in `HomeActivity.kt`, `SearchActivity.kt`, `Hub.kt` (`esWeather`) and `Ui.kt`. Moving them to `res/values/strings.xml` is welcome as a pull request.
