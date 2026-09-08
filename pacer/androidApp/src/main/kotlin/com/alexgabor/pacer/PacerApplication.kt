package com.alexgabor.pacer

import android.app.Application
import android.content.Context
import com.alexgabor.pacer.di.initKoin
import org.koin.android.ext.koin.androidContext

class PacerApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        setupKoin()
    }
}

private fun Context.setupKoin() {
    initKoin {
        androidContext(this@setupKoin)
    }
}
