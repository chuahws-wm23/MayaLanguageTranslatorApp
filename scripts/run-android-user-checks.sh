#!/usr/bin/env bash
set -euo pipefail
# Keep screenshots even when a user-journey assertion fails.
collect_screenshots() {
    adb pull /sdcard/Android/data/com.chuahws.mayalanguageapp/files/screenshots app/build/outputs/glyph-screenshots || true
}
trap collect_screenshots EXIT
gradle connectedDebugAndroidTest \
  -Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true \
  -Pandroid.testInstrumentationRunnerArguments.class=com.chuahws.mayalanguageapp.presentation.translation.GlyphReferenceWorkflowTest,com.chuahws.mayalanguageapp.presentation.translation.CameraCaptureWorkflowTest,com.chuahws.mayalanguageapp.presentation.translation.UserJourneyTest \
  --stacktrace -Dorg.gradle.jvmargs="-Xmx4g -Dfile.encoding=UTF-8" --max-workers=2
