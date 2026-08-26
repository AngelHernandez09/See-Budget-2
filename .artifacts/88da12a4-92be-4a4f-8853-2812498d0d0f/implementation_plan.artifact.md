# Fix Activity client record null crash

The crash `java.lang.IllegalArgumentException: Activity client record must not be null to execute transaction item: TopResumedActivityChangeItem{onTop=true}` is a known issue in modern Android versions (14+) when using `enableEdgeToEdge()` in a way that conflicts with the Activity lifecycle or when using a legacy theme.

## Proposed Changes

### [androidApp]

#### [MODIFY] [MainActivity.kt](file:///C:/Users/Angel/Desktop/Angel/Proyectos/Programación/Desarrollo Multiplataforma/See Budget/androidApp/src/main/kotlin/com/seebudget/app/MainActivity.kt)
- Move `enableEdgeToEdge()` after `super.onCreate(savedInstanceState)` to ensure the Activity is properly initialized before configuring window insets.

#### [MODIFY] [AndroidManifest.xml](file:///C:/Users/Angel/Desktop/Angel/Proyectos/Programación/Desarrollo Multiplataforma/See Budget/androidApp/src/main/AndroidManifest.xml)
- Update the activity theme to a custom modern theme.
- Add `android:configChanges` to prevent unnecessary activity recreations on orientation or screen size changes, which can trigger lifecycle-related crashes.

#### [NEW] [themes.xml](file:///C:/Users/Angel/Desktop/Angel/Proyectos/Programación/Desarrollo Multiplataforma/See Budget/androidApp/src/main/res/values/themes.xml)
- Define `Theme.SeeBudget` inheriting from `Theme.Material3.DayNight.NoActionBar`.

### [shared]

#### [MODIFY] [build.gradle.kts](file:///C:/Users/Angel/Desktop/Angel/Proyectos/Programación/Desarrollo Multiplataforma/See Budget/shared/build.gradle.kts)
- Change `compose.material3` dependency to `api` to ensure its resources (like themes) are available to the `androidApp` module.

## Verification Plan

### Automated Tests
- Build the project using `./gradlew :androidApp:assembleDebug` to ensure there are no resource or compilation errors.

### Manual Verification
- Deploy the app to an Android device/emulator (Target SDK 36 environment) and verify it no longer crashes on startup.
- Verify that the Edge-to-Edge display still works as expected.
