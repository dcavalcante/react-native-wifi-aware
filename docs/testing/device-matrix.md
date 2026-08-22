# Device matrix

| Device | Android/API | `android.hardware.wifi.aware` | Generated example | Evidence |
|---|---|---|---|---|
| Two user-previously verified Android devices | not freshly observed | advertised support | not freshly observed | User-supplied prior verification |
| Fresh adb observation, 2026-08-22 | — | — | — | `adb devices -l` returned no attached devices |

Fresh recheck when devices are connected:

```powershell
adb devices -l
adb -s <serial> shell getprop ro.product.model
adb -s <serial> shell getprop ro.build.version.release
adb -s <serial> shell getprop ro.build.version.sdk
adb -s <serial> shell pm list features | findstr android.hardware.wifi.aware
```

The generated arm64 debug APK compiled successfully; installation/run is deferred solely because no device was attached. This is not evidence of Wi-Fi Aware runtime behavior.
