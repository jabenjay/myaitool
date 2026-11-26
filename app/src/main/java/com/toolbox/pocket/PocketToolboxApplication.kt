package com.toolbox.pocket

import android.app.Application
import com.toolbox.pocket.data.AppLogManager

class PocketToolboxApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        AppLogManager.init(this)
    }
}
