# BT Chat – offline text chat between Android phones over Bluetooth

Send text between two Android phones with **no internet and no Wi-Fi**, using
Bluetooth Classic (RFCOMM). Turn on **car mode** and the receiving phone reads
each message aloud; if that phone is connected to the car's Bluetooth audio, the
message plays through the car speakers.

## How to get the APK

1. Install **Android Studio** (free): https://developer.android.com/studio
2. `File > Open` and choose this `BluetoothChat` folder. Let Gradle sync
   (it downloads what it needs the first time; needs internet once).
3. Debug APK: `Build > Build Bundle(s) / APK(s) > Build APK(s)`.
   The file appears in `app/build/outputs/apk/debug/app-debug.apk`.
4. Copy the APK to both phones and install it (allow "install unknown apps").

You can also run it straight onto a phone with USB debugging and the green Run button.

## How to use it

1. Install the app on **both** phones and open it. Allow the Bluetooth permission.
2. Tap **Pair** on one phone, pair the two phones in the Android Bluetooth screen
   (tap **Be visible** on the other phone if it isn't found).
3. On one phone tap **Connect** and choose the other phone. The other phone just
   needs the app open.
4. Type a message and tap **Send**.
5. Optional: switch on **Read incoming messages aloud** on the phone in the car.

## Limits you should know about

- The app must be open (in the foreground) on both phones. Android may close the
  connection when the app is in the background; a foreground service would fix that.
- Range is roughly 10 meters.
- The car itself cannot receive custom text. Cars only accept calls, music and the
  phone's own SMS. That is why car mode uses text-to-speech through the phone.
- Works between Android phones only (iPhones don't allow this kind of Bluetooth link).
- Chat is one-to-one and not encrypted beyond normal Bluetooth pairing.
