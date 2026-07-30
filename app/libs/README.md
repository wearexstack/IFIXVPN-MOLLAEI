# libbox.aar (sing-box for Android)

IFIX VPN needs the official **libbox** Android library from [SagerNet/sing-box](https://github.com/SagerNet/sing-box).

## Quick build (Linux / macOS / CI)

```bash
# from repo root
./scripts/build-libbox.sh
# produces: app/libs/libbox.aar
```

Requirements: Go 1.22+, Android NDK, `ANDROID_HOME` / `ANDROID_NDK_HOME`.

## Gradle

`app/build.gradle.kts` loads any `*.aar` under this folder:

```kotlin
implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.aar"))))
```

Without the AAR the app still builds; connect will report that the core is missing.

## License

libbox / sing-box is GPL. Distribute source obligations if you ship a modified core.
