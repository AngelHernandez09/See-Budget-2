package com.seebudget.app.di

import org.koin.core.context.startKoin
import org.koin.dsl.KoinAppDeclaration

/**
 * Inicializador único de Koin, llamado desde cada plataforma
 * (SeeBudgetApplication en Android, main() en Desktop/Web, doInitKoinIos()
 * en iOS). Mantiene la configuración de módulos centralizada en commonMain.
 */
fun initKoin(config: KoinAppDeclaration? = null) {
    startKoin {
        config?.invoke(this)
        modules(commonModule, platformModule())
    }
}
