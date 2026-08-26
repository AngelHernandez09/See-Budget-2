import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.sqldelight)
}

kotlin {
    applyDefaultHierarchyTemplate()

    iosArm64()
    iosSimulatorArm64()

    targets.withType<org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget>().configureEach {
        binaries.framework {
            baseName = "Shared"
            isStatic = true
        }
    }
    
    jvm()
    
    js {
        browser()
    }
    
    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        browser()
    }
    
    android {
       namespace = "com.seebudget.app.shared"
       compileSdk = libs.versions.android.compileSdk.get().toInt()
       minSdk = libs.versions.android.minSdk.get().toInt()
    
       compilerOptions {
           jvmTarget = JvmTarget.JVM_11
       }
       androidResources {
           enable = true
       }
       withHostTest {
           isIncludeAndroidResources = true
       }
       withDeviceTestBuilder {
           sourceSetTreeName = "test"
       }.configure {
           instrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
       }
    }
    
    sourceSets {
        val jvmMain by getting
        // iosMain lo crea automáticamente el "default hierarchy template" de
        // Kotlin (agrupa iosArm64/iosSimulatorArm64), por eso "by getting".
        val iosMain by getting

        androidMain.dependencies {
            implementation(libs.compose.uiToolingPreview)
            implementation(libs.compose.uiTooling)

            // Fase 8 (RF-05) — NotificationCompat/NotificationManagerCompat/ContextCompat (notificaciones/recordatorios).
            implementation(libs.androidx.core.ktx)

            // Fase 0 — api() porque SeeBudgetApplication (androidApp) llama
            // directo a androidContext(); ver comentario análogo con
            // koin-core en commonMain.
            api(libs.koin.android)
            implementation(libs.ktor.client.android)
            implementation(libs.sqldelight.android.driver)
        }
        commonMain.dependencies {
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            api(libs.compose.material3)
            implementation(libs.compose.ui)
            implementation(libs.compose.components.resources)
            implementation(libs.compose.uiToolingPreview)
            implementation(libs.androidx.lifecycle.viewmodelCompose)
            implementation(libs.androidx.lifecycle.runtimeCompose)

            // Fase 9 (Navigation Compose, agregado en el chat) — ver
            // comentario en libs.versions.toml sobre por qué Navigation 2
            // y no Navigation 3.
            implementation(libs.androidx.navigation.compose)

            // Fase 0 — setup: Koin, Ktor, kotlinx.serialization/datetime,
            // SQLDelight y supabase-kt, listos para que Fase 1+ los use.
            // koin-core es api() porque initKoin() (expuesta a androidApp/
            // desktopApp/webApp) tiene KoinAppDeclaration en su firma
            // pública — con implementation() esos módulos no compilan.
            api(libs.koin.core)
            implementation(libs.koin.compose)
            implementation(libs.koin.compose.viewmodel)

            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.datetime)

            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.serialization.kotlinx.json)

            implementation(libs.sqldelight.runtime)
            implementation(libs.sqldelight.coroutines)

            // Fase 4 — gráficos (Vico, RF-03).
            implementation(libs.vico.compose)

            implementation(project.dependencies.platform(libs.supabase.bom))
            implementation(libs.supabase.postgrest)
            implementation(libs.supabase.auth)
            implementation(libs.supabase.realtime)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
        jsMain.dependencies {
            implementation(libs.wrappers.browser)

            // Fase 0
            implementation(libs.ktor.client.js)
            implementation(libs.sqldelight.webworker.driver)
        }
        wasmJsMain.dependencies {
            // Fase 0
            implementation(libs.ktor.client.js)
            implementation(libs.sqldelight.webworker.driver)
        }
        jvmMain.dependencies {
            // Fase 0 (Desktop)
            implementation(libs.ktor.client.okhttp)
            implementation(libs.sqldelight.sqlite.driver)
        }
        iosMain.dependencies {
            // Fase 0
            implementation(libs.ktor.client.darwin)
            implementation(libs.sqldelight.native.driver)
        }
    }
}

dependencies {
    androidRuntimeClasspath(libs.compose.uiTooling)
}

// Fase 0: esquema SQLDelight vacío (Fase 1 agrega los .sq de
// Expense/Category/User/Budget/PlannedItem/ProjectionSnapshot, sección 7
// del documento de requerimientos).
sqldelight {
    databases {
        create("AppDatabase") {
            packageName.set("com.seebudget.app.db")
        }
    }
}

// RNF-07 (selector de idioma, agregado en el chat) — sin esto, el paquete
// del Res generado se deriva de {group}.{module}.generated.resources, y
// este proyecto no fija `group` en ningún build.gradle.kts (quedaría
// ambiguo/frágil). Se fija explícito para que el import en cada pantalla
// sea siempre el mismo, sin depender de esa inferencia.
compose.resources {
    packageOfResClass = "com.seebudget.app.generated.resources"
}
