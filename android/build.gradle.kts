// Top-level build file. No wrapper (gradlew) is checked in -- CI installs a
// pinned Gradle via gradle/actions/setup-gradle instead (see
// .github/workflows/build-android.yml) since generating a correct binary
// gradle-wrapper.jar blind, with no local Gradle install to run `gradle
// wrapper` in this dev sandbox, risks shipping a corrupt one. Opening this
// project in Android Studio will offer to generate the wrapper for you,
// which is the normal local-dev path -- see android/README.md.
plugins {
    id("com.android.application") version "8.4.0" apply false
    id("org.jetbrains.kotlin.android") version "1.9.24" apply false
}
