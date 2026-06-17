# BT-300 head pose mouse bridge

This folder contains a small Android sender for Epson Moverio BT-300 and a
Debian receiver.

The sender uses Android's standard `SensorManager` motion APIs. It prefers
`TYPE_ROTATION_VECTOR` and falls back to `TYPE_GAME_ROTATION_VECTOR` if needed.
It sends yaw/pitch/roll over UDP to Debian.

## Build the APK

```bash
cd bt300
./build.sh
```

The output APK is:

```text
bt300/build/bt300-headmouse-debug.apk
```

Install it on the BT-300:

```bash
adb install -r build/bt300-headmouse-debug.apk
```

## Run the Debian receiver

On Debian, listening on all interfaces:

```bash
cd bt300
DISPLAY=:0 ./receiver.py --host 0.0.0.0 --port 39500
```

The default mode maps head yaw/pitch to an absolute mouse position around the
screen center. Start the Android app while looking at the neutral center point.
Tap "Recenter" in the app, or restart the receiver, to set a new neutral pose.

The app displays both derived `turn`/`tilt` values and Android raw orientation
axes. On BT-300 the physical head axes may not line up with raw
`yaw`/`pitch`/`roll`, because Android's device coordinate system does not
necessarily match the glasses' head coordinate system. The receiver defaults to
the derived vector-based axes: `--x-axis turn --y-axis tilt`.

Useful receiver options:

```bash
DISPLAY=:0 ./receiver.py --yaw-gain 38 --pitch-gain 30
DISPLAY=:0 ./receiver.py --x-axis turn --y-axis tilt --invert-y
DISPLAY=:0 ./receiver.py --x-axis yaw --y-axis roll
DISPLAY=:0 ./receiver.py --mode scroll --scroll-threshold 8
```

## Android app settings

Default target:

```text
192.168.0.12:39500
```

Change it in the app UI if Debian has a different IP.

## Notes

Android documents rotation vector and gyroscope motion sensors through the
standard sensor API. `SensorManager.getRotationMatrixFromVector()` plus
`SensorManager.getOrientation()` converts the rotation vector into yaw, pitch,
and roll. BT-300 should expose its head motion sensors through the same Android
API path.
