# Lists

A small Android checklist app built with Jetpack Compose. You keep several lists (shopping, movies, reading…), tick items off, and the app tidies up after you: checked items move to an archive after a day, and items left unchecked for a week get flagged.

## Features

- **Multiple lists.** Create, rename, reorder (drag) and delete lists. The top list opens by default.
- **Checklist items.** Add items, check them off, drag to reorder, and swipe left to delete.
- **Auto-archive.** A checked item stays at the bottom of its list for 24 hours, then moves to that list's archive. You can browse and delete archived items per list.
- **Overdue reminders.** An item left unchecked for 7 days is highlighted, and you get one notification for it (you can turn this off).
- **History.** A log of every item you've added, which stays around after the item or list is deleted.
- **Settings.** Dark, light or system theme; delete confirmations for items and archived items; overdue notifications on or off.

A fresh install starts with three example lists: Shopping, Movies and Reading.

## Requirements

- Android 8.0 (API 26) or newer to run it
- JDK 17+ and the Android SDK (compile SDK 37) to build it

## Building

Point Gradle at your Android SDK, either with `ANDROID_HOME` or a `local.properties` file in the project root (it's gitignored):

```properties
sdk.dir=/path/to/Android/Sdk
```

Then:

```bash
./gradlew assembleDebug      # build a debug APK into app/build/outputs/apk/debug/
./gradlew installDebug       # build and install on a connected device or emulator
./gradlew assembleRelease    # minified release build (you have to set up signing)
```

You can also open the project in Android Studio and run the `app` configuration.

## Project layout

```
app/src/main/java/com/fazinhan/lists/
├── ListsApp.kt            Application: notification channel, schedules background work
├── MainActivity.kt        Hosts Compose, handles back navigation and foreground/background
├── data/
│   ├── Database.kt        SQLiteOpenHelper: schema (lists, items, history) and seed data
│   ├── Repository.kt      All reads (as Flows) and writes (in transactions)
│   ├── Models.kt          Data classes and timing constants
│   └── Prefs.kt           SharedPreferences-backed settings exposed as StateFlows
├── ui/
│   ├── AppViewModel.kt    App state, back stack, and actions
│   ├── components/        Shared composables (swipe-to-delete, dialogs)
│   ├── screens/           Checklist, Lists, Archive, History, Settings
│   └── theme/             Material 3 theme
└── work/
    └── Maintenance.kt     Archiving and overdue notifications (WorkManager job)
```

## How it works

- **Storage.** Plain SQLite through `SQLiteOpenHelper`, with no ORM. `Repository` is the only thing that touches the database. Each write runs in a transaction and increments a version counter, and every observed query is a `Flow` that re-runs when that counter changes, so the UI updates on its own after any write.
- **Navigation.** `AppViewModel` keeps a simple back stack of `Screen` values instead of using a navigation library.
- **Maintenance.** `Maintenance.run()` archives items that were checked more than a day ago and sends a notification for newly overdue items. It runs every 15 minutes through WorkManager, and every minute while the app is open. Each item's `overdue_notified` flag makes sure it only triggers one notification.
- **Timings.** `ARCHIVE_AFTER_MS` and `OVERDUE_AFTER_MS` are defined in `data/Models.kt`.

## Dependencies

Jetpack Compose (Material 3), AndroidX Lifecycle / Activity, WorkManager, and [`sh.calvin.reorderable`](https://github.com/Calvin-LL/Reorderable) for drag-to-reorder.

## License

[GNU Affero General Public License v3.0](LICENSE)
