# Utho — alarm you can only stop by solving a sum

Expo SDK 57 (React Native 0.86) + a local native Android module (Kotlin). **Android only. Expo Go will not work**, it needs a dev build or APK.

## Run / build
```bash
npm install
npx expo prebuild --platform android      # generates android/ and links modules/alarm-native
npx expo run:android                       # phone connected with USB debugging
# or cloud APK:
eas build -p android --profile preview
```

## First launch
Tap "Allow" on each row (exact alarms, unrestricted battery, display over other apps, full-screen alerts). On Xiaomi/Oppo/Vivo/Realme/Samsung also enable Autostart and set battery to "No restrictions" in app settings.

## How it works
- `AlarmManager.setAlarmClock` fires exactly, works with the app closed, and is rebuilt after reboot (`AlarmReceiver`).
- `AlarmService` (foreground) plays your audio on the alarm stream, forces alarm volume to max every second, and reopens the puzzle if it is not on screen.
- `AlarmActivity` shows over the lock screen. Volume, back and media keys are swallowed. Only a correct answer stops the ring.

## Known limits
Android does not let any app block the power menu, force-stop or switch-off. Pressing the power button just turns the screen off; the service wakes it again within seconds.
