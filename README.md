This is a Kotlin Multiplatform project targeting Android, iOS, Web, Desktop (JVM).

* [/iosApp](./iosApp/iosApp) contains an iOS application. Even if you’re sharing your UI with Compose Multiplatform,
  you need this entry point for your iOS app. This is also where you should add SwiftUI code for your project.

* [/shared](./shared/src) is for code that will be shared across your Compose Multiplatform applications.
  It contains several subfolders:
  - [commonMain](./shared/src/commonMain/kotlin) is for code that’s common for all targets.
  - Other folders are for Kotlin code that will be compiled for only the platform indicated in the folder name.
    For example, if you want to use Apple’s CoreCrypto for the iOS part of your Kotlin app,
    the [iosMain](./shared/src/iosMain/kotlin) folder would be the right place for such calls.
    Similarly, if you want to edit the Desktop (JVM) specific part, the [jvmMain](./shared/src/jvmMain/kotlin)
    folder is the appropriate location.

### Running the apps

Use the run configurations provided by the run widget in your IDE's toolbar. You can also use these commands and options:

- Android app: `./gradlew :androidApp:assembleDebug`
- Desktop app:
  - Hot reload: `./gradlew :desktopApp:hotRun --auto`
  - Standard run: `./gradlew :desktopApp:run`
- Web app:
  - Wasm target (faster, modern browsers): `./gradlew :webApp:wasmJsBrowserDevelopmentRun`
  - JS target (slower, supports older browsers): `./gradlew :webApp:jsBrowserDevelopmentRun`
- iOS app: open the [/iosApp](./iosApp) directory in Xcode and run it from there.

### Running tests

Use the run button in your IDE's editor gutter, or run tests using Gradle tasks:

- Android tests: `./gradlew :shared:testAndroidHostTest`
- Desktop tests: `./gradlew :shared:jvmTest`
- Web tests:
  - Wasm target: `./gradlew :shared:wasmJsTest`
  - JS target: `./gradlew :shared:jsTest`
- iOS tests: `./gradlew :shared:iosSimulatorArm64Test`

---

## Fase 0 — Setup (roadmap, sección 9)

Sobre este scaffold generado por el wizard de Kotlin Multiplatform, se agregó lo necesario para arrancar el roadmap de `requerimientos-app-gastos-kmp.md`:

- **Version catalog** (`gradle/libs.versions.toml`): Koin, Ktor, kotlinx.serialization, kotlinx-datetime, SQLDelight y supabase-kt, con sus plugins correspondientes.
- **`shared/build.gradle.kts`**: dependencias agregadas por source set (`commonMain`, `androidMain`, `jvmMain`, `iosMain`, `jsMain`, `wasmJsMain`) y un esquema SQLDelight vacío (`AppDatabase`, paquete `com.seebudget.app.db`) listo para que Fase 1 agregue los `.sq` de `Expense`/`Category`/`User`/`Budget`/`PlannedItem`/`ProjectionSnapshot` (sección 7 del documento).
- **Koin**: `commonModule` vacío + `expect fun platformModule()` (`shared/src/commonMain/.../di/AppModule.kt`, `Koin.kt`), con `actual` por plataforma. Inicializado desde `SeeBudgetApplication` (Android), `main()` (Desktop/Web) y `doInitKoinIos()` (iOS, llamado desde `iOSApp.swift`).
- **Supabase**: `SupabaseClientProvider` + `SupabaseConfig` (`expect`/`actual` por plataforma) listos para Fase 2.
- **CI** (`.github/workflows/ci.yml`): build de shared/desktop/web y android en `ubuntu-latest`, tests + build de iOS en `macos-14`.

### ⚠️ Nota sobre las versiones agregadas en Fase 0

Las versiones de Koin/Ktor/kotlinx.serialization/kotlinx-datetime/SQLDelight/supabase-kt que agregué en `libs.versions.toml` son las últimas que conozco de forma confiable — el entorno donde las armé no tenía acceso a Maven Central para confirmar si hay versiones más nuevas (a diferencia de las que ya traía el wizard, que sí están verificadas). Al sincronizar por primera vez en Android Studio, si algo no resuelve, es probablemente una de esas versiones — decime el error y lo ajustamos.

### Supabase — credenciales

El proyecto real ya existe en supabase.com y sus credenciales (`url`/`anonKey`) están hardcodeadas directo en `SupabaseConfig.*.kt` de cada plataforma (`shared/src/<platform>Main/.../data/remote/`), commiteadas en git.

Decisión explícita: es la práctica estándar de Supabase para la **anon key** (a diferencia de la `service_role` key, que nunca debe estar en un cliente) — la seguridad real la da **Row Level Security (RLS)** en las tablas, no el secreto de esta key.

**⚠️ Pendiente antes de guardar datos reales de usuarios: activar RLS en todas las tablas de Supabase.** Sin eso, cualquiera con la anon key (visible en el binario de la app) puede leer/escribir toda la base. Esto se define como parte de Fase 2 (Auth + sync), junto con las políticas de RLS por usuario.

### Próximo paso sugerido

Fase 2 — Backend y sync: Supabase Auth (registro/login), sincronización de gastos/categorías vía Supabase Realtime, modo offline-first robusto con `SyncManager`, y políticas de RLS en las tablas.

---

Learn more about [Kotlin Multiplatform](https://www.jetbrains.com/help/kotlin-multiplatform-dev/get-started.html),
[Compose Multiplatform](https://github.com/JetBrains/compose-multiplatform/#compose-multiplatform),
[Kotlin/Wasm](https://kotl.in/wasm/)…

We would appreciate your feedback on Compose/Web and Kotlin/Wasm in the public Slack channel [#compose-web](https://slack-chats.kotlinlang.org/c/compose-web).
If you face any issues, please report them on [YouTrack](https://youtrack.jetbrains.com/newIssue?project=CMP).
