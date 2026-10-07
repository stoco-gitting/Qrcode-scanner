# Sensor QR Scanner

An Android app for batch-scanning LoRaWAN sensor QR codes in the format `PN;DEVEUI;APPEUI;APPKEY`.

- Continuous scanning. Each new sensor gives a beep, a vibration and a green banner.
- Duplicates (same DEVEUI) are flagged in yellow and are not stored again.
- Codes that don't match the format are rejected in red.
- The list is stored on the device, so it survives restarts. You can search it and swipe to delete (with undo).
- Export to CSV (`PN,DEVEUI,APPEUI,APPKEY,ScannedAt`) through the share sheet or straight to Downloads.

## Install
Download the APK from [Releases](../../releases) and install it. You may need to allow "install unknown apps".

## Release
Push a tag `vX.Y.Z` and GitHub Actions builds and publishes the APK.
For stable signing (so updates install over each other), add these repo secrets:
`KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`. If they are missing, the APK is signed with the CI debug key.
