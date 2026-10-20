# Contributing

Thanks for helping! The project is small on purpose.

## Ground rules

* **No new libraries.** The launcher runs on TVs with ~1.4 GB of RAM; `androidx.core` is the only dependency. If a feature seems to need a library, open an issue first.
* **Views in code, sizes in 1080p units.** Use `px(n)` for dimensions and `TextView.sizePx(n)` for text (see `Ui.kt`). Do not use `dp`/`sp` directly: on a density-320 TV they double.
* **No blur, no per-frame drawing.** Animate `translation*`, `alpha`, `scale*` of views (GPU layers).
* **Keep both themes working** (`Look.clean` true/false) and **work without a hub** (`HUB_ON`).
* **Never commit secrets, addresses, photos or wallpapers** you do not own. `local.properties` is git-ignored; the screenshots in `docs/` come from demo mode.

## Build and test

```bash
cd launcher && ./gradlew assembleDebug
cd .. && tools/install.sh <tv-ip>                      # stock launcher stays Home while you test
adb -s <ip>:5555 shell am start -S -n cl.appbyte.tv/.HomeActivity --ez demo true   # made-up data
python3 tools/mock-hub/server.py                       # fake hub for the home features
tools/screenshot.sh <tv-ip> shot.png
```

There are no unit tests yet (UI is verified on a real TV with screenshots and the D-pad via `adb shell input keyevent KEYCODE_DPAD_*`). Adding tests for `Sky`, `TvFeed` parsing and the hub client would be a great contribution.

**Always test with the stock launcher enabled.** Disable it only for a build you have used for a while: a crash at start with no other launcher leaves a grey screen (recover with `tools/debloat.sh <ip> launcher-on`).

## Good first contributions

* Move the Spanish strings to `res/values/strings.xml` and add English.
* A mini player for apps other than Spotify.
* Support for TVs other than the Philips one (tell us the model, Android version and what broke).
* A reference hub for Home Assistant.

## Pull requests

* One change per PR, with before/after screenshots when the UI changes (demo mode, please).
* Say which TV you tested on.
* Keep the code style of the surrounding file (short lines of code with a comment on the *why*, not the what).
