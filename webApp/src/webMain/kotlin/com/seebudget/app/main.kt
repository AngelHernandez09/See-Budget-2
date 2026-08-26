package com.seebudget.app

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import com.seebudget.app.di.initKoin

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    initKoin() // Fase 0 — setup

    ComposeViewport {
        App()
    }
}
