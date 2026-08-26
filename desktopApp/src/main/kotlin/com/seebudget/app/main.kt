package com.seebudget.app

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.seebudget.app.di.initKoin

fun main() {
    initKoin() // Fase 0 — setup

    application {
        Window(
            onCloseRequest = ::exitApplication,
            title = "SeeBudget2",
        ) {
            App()
        }
    }
}
