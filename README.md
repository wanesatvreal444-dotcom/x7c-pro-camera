# X7c Pro Camera — enhanced CameraX prototype

An Android 14 / MagicOS 8-oriented native camera app project for HONOR X7c. It is a separate app (`com.x7cpro.camera`) and does not replace the HONOR camera app.

## Included in this source
- Rear/front camera preview and switching.
- Photo capture requesting CameraX maximum-quality mode and JPEG quality 100. Actual pixel resolution depends on the phone's camera HAL; 108MP output is not guaranteed.
- Video recording with HD / FHD / UHD quality requests and CameraX lower-quality fallback when a requested quality is unavailable.
- Tap-on-preview autofocus and auto-exposure metering (useful for close-up/macro subjects).
- Zoom and exposure-compensation sliders, limited by what the camera exposes to Android.
- Torch toggle.
- Natural / DSLR / Macro / Night shooting presets in the interface. These are capture guidance/settings labels, not AI image filters; Macro uses tap-to-focus and Night adjusts exposure compensation where supported.
- Cinematic recording selection and separate save folder. This prototype does not yet bake a cinematic LUT, crop, or guaranteed 24 fps into the recorded video.
- Photos and videos are saved into the app's folders in shared media storage.

## Important hardware and software limits
- The app cannot reproduce iPhone 13 Pro image quality by software alone. Sensor size, optics, stabilization and HONOR camera HAL capabilities matter.
- CameraX quality requests are best-effort. UHD/4K may fall back to a lower supported quality. 108MP still capture, RAW, true multi-frame Night mode, AI portrait segmentation, focus stacking, and guaranteed 24 fps cinematic export are not implemented in this prototype.
- “DSLR”, “Natural”, “Macro”, and “Night” are interface presets, not claims of a finished computational photography pipeline.
- Must be built and tested on the actual HONOR X7c. This environment has not run an Android build or device test.

## Build
Create a new GitHub repository named `x7c-pro-camera`, upload the contents of this folder to the repository root on branch `main`, then open Actions and run **Build X7c Pro Camera APK**. Download the `X7c-Pro-Camera-debug-apk` artifact and install `app-debug.apk`.

The included GitHub Actions workflow builds a debug APK. A debug APK is for testing, not Play Store release.
