Place Termux bootstrap zip here for your device ABI to enable full offline environment.

Supported subfolders or filenames (first found will be used):

- bootstrap/aarch64/bootstrap-aarch64.zip or bootstrap/aarch64/aarch64.zip or bootstrap/aarch64.zip (for arm64-v8a)
- bootstrap/arm/bootstrap-arm.zip or bootstrap/arm/arm.zip or bootstrap/arm.zip (for armeabi-v7a)
- bootstrap/x86_64/bootstrap-x86_64.zip or bootstrap/x86_64/x86_64.zip or bootstrap/x86_64.zip
- bootstrap/i686/bootstrap-i686.zip or bootstrap/i686/i686.zip or bootstrap/i686.zip (for x86)

The zip content should include the `usr/` directory at its root, as provided by official Termux bootstrap archives.
