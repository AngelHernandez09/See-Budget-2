package com.seebudget.app

import androidx.compose.ui.window.ComposeUIViewController
import com.seebudget.app.di.initKoin

/**
 * Llamado una sola vez desde Swift (iOSApp.swift) antes de crear la primera
 * vista, para inicializar Koin del lado iOS. Fase 0: setup del proyecto.
 */
fun doInitKoinIos() {
    initKoin()
}

fun MainViewController() = ComposeUIViewController { App() }
