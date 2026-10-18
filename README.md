# CodeForge Android

Landscape-only Android application starter.

## Build
GitHub Actions builds a debug APK when changes are pushed to main.
Open Actions, select Build CodeForge APK, and run the workflow if needed.
Download the CodeForge-debug-apk artifact from the completed run.

## Current limitation
This starter currently builds the Android shell only. It does not yet include
or launch a standalone code-server backend. The Linux ARM64 code-server
distribution uses glibc binaries and native dependencies that cannot simply
be copied into an Android APK.
