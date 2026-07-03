# FocusLock 🔒

Your personal screen-time app. It counts how long you spend in the apps you choose, warns you 5 minutes before your limit, then locks the app until you say what you're doing and solve a quick puzzle.

## How to get it on your phone (10–15 min, one time)

1. **Install Android Studio** on your computer (free): https://developer.android.com/studio — accept all the defaults during setup.
2. **Open this project**: Android Studio → "Open" → pick the `FocusLock` folder. Wait for the loading bar at the bottom to finish (first time takes a few minutes — it's downloading pieces it needs).
3. **Plug in your phone** with a USB cable.
4. **Turn on developer mode on your phone** (one time): Settings → About phone → tap "Build number" 7 times. Then Settings → Developer options → turn on "USB debugging". Tap "Allow" on the popup when you connect.
5. In Android Studio, your phone's name appears in the top bar. **Press the green ▶ button.** The app installs and opens on your phone.

That's it — after this you never need the computer again unless you want changes.

## First time you open the app

FocusLock walks you through two permission steps (buttons are right on the home screen):
- **Usage access** — so it can see which app is on screen and count time
- **Display over other apps** — so it can show the lock screen when time's up

Both are normal for screen-time apps. After that:

1. Set your **daily reset time** (e.g. when you wake up)
2. Tap **Add app**, pick an app (Instagram, TikTok, ...)
3. Tap **Edit** on it to set its daily limit and how many minutes each extension adds

## What happens day to day

- 5 minutes before an app's limit → you get a warning notification
- At 0 → lock screen appears over the app
- To continue: pick what you're doing (e.g. "Searching for something specific") → solve a quick puzzle → your extension minutes are added
- Repeat as many times as you honestly need
- Or pick "No limit for this app today" (also puzzle-gated) to lift the limit until the next reset
- **Stats** shows your last 7 days per app

## Good to know

- Notifications from locked apps still come through — only the app itself is locked.
- FocusLock keeps a small "FocusLock is watching your limits" notification. That's Android's way of letting it run in the background — don't swipe the app away from recents or dismiss it via battery settings, or counting stops.
- If your phone offers to "optimize battery" for FocusLock, choose "Don't optimize" / "Unrestricted" so it isn't put to sleep.
