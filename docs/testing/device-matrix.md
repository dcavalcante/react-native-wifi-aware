# Device matrix

| Device | Android/API | `android.hardware.wifi.aware` | Generated example | Evidence |
|---|---|---|---|---|
| Two user-previously verified Android devices | not freshly observed | advertised support | not freshly observed | User-supplied prior verification |
| Fresh adb observation, 2026-08-22 | — | — | — | `adb devices -l` returned no attached devices |
| Samsung SM-M515F (`RQ8NB08QJ1B`) | Android 12 / API 31 | supported | installed and foreground-launched | Fresh adb observation, 2026-08-22 |

Fresh recheck when devices are connected:

```powershell
adb devices -l
adb -s <serial> shell getprop ro.product.model
adb -s <serial> shell getprop ro.build.version.release
adb -s <serial> shell getprop ro.build.version.sdk
adb -s <serial> shell pm list features | findstr android.hardware.wifi.aware
```

The generated arm64 debug APK compiled successfully and was installed/foreground-launched on the Samsung device. This is not evidence of Wi-Fi Aware runtime behavior.
