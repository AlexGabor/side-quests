package com.alexgabor.bookplate

import android.app.Application
import android.content.Context
import com.alexgabor.bookplate.di.initKoin
import org.koin.android.ext.koin.androidContext

class BookplateApplication : Application() {

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
