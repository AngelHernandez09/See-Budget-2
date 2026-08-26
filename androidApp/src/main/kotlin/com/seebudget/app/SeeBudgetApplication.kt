package com.seebudget.app

import android.app.Application
import com.seebudget.app.di.initKoin
import org.koin.android.ext.koin.androidContext

class SeeBudgetApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        initKoin {
            androidContext(this@SeeBudgetApplication)
        }
    }
}
