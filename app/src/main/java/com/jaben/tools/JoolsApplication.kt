package com.jaben.tools

import android.app.Application
import com.jaben.tools.data.AppLogManager

class JoolsApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        AppLogManager.init(this)
    }
}
