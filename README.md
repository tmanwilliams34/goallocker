# GoalLock

Locks social media, YouTube and streaming apps every day until you finish your study goals.

## How it works
- A goal is an app and a number of minutes, for example 10 min in Gospel Library or 10 min in AnkiDroid.
- GoalLock counts time only while that app is on screen, the screen is on, and the phone is unlocked.
- After the time is reached, you answer your goal's journal question, such as "What chapter did you read?". The answer is saved in your **Journal**.
- When every goal is done, blocked apps unlock until the daily reset (4 AM by default).
- **Emergency unlock**: one tap on the lock screen gives 15 minutes of access. It's logged in the journal.
- Calls, texts, maps, settings and your goal apps are never blocked.

Default goals: Book of Mormon chapter (Gospel Library) and 15 Japanese words (AnkiDroid). You can add Takoboto or any other app with **＋ Add a goal**.

## Get the APK (one-time setup, about 5 minutes)
1. Create a new **empty** repo on GitHub, for example `goallock`. It can be private.
2. Upload this folder from your laptop:
   ```bash
   cd GoalLock
   git init -b main
   git add .
   git commit -m "GoalLock v1"
   git remote add origin https://github.com/<you>/goallock.git
   git push -u origin main
   ```
3. Open the repo's **Actions** tab and wait for the "Build APK" run to turn green. This takes about 4 minutes.
4. Open the repo's **Releases** page. There, the latest build has `GoalLock.apk`.

Each push to `main` builds a new APK. The same signing key is used every time, so a new APK installs as an update and keeps your goals and journal.

## Install on your Samsung / Android phone
1. On the phone, open the Releases page in Chrome and download `GoalLock.apk`. This is easiest if the repo is public. For a private repo, sign in to GitHub first.
2. Open the downloaded file. If prompted, allow **Install unknown apps** for Chrome.
3. Open GoalLock and complete the **Finish setup** card:
   - **Turn on app locking**: Accessibility → Installed apps → GoalLock → On.
     If the switch is greyed out, go to Settings → Apps → GoalLock → ⋮ → **Allow restricted settings**, then try again. Android does this for every app installed outside the Play Store.
   - **Allow background running**: this stops Samsung from putting GoalLock to sleep.
   - **Allow notifications**: GoalLock notifies you when a goal's time is reached.

## Limitations
- GoalLock can't see inside Gospel Library or Anki, so progress is based on time spent in the app plus your journal answer.
- It blocks apps, not websites. YouTube in a web browser still works. You can add your browser to the blocked list if that becomes a loophole.
- Anyone can turn off the lock by disabling the Accessibility switch or uninstalling the app. The app is built to keep you on track, not to be impossible to escape.

## Project layout
`app/src/main/java/com/tman/goallock/`
- `BlockerService.kt`: the Accessibility service that detects the current app, blocks it, and counts goal time
- `Store.kt`: goals, progress, journal and settings (SharedPreferences, kept on the phone only)
- `MainActivity.kt`: home screen · `GoalEditActivity.kt`: goal editor · `BlockedAppsActivity.kt`: list of blocked apps
- `JournalActivity.kt`: write and read entries · `BlockActivity.kt`: "this app is locked" screen

`keystore/goallock.jks` is the signing key for your personal builds. Keep it in the repo, or updates won't install over the old version.
