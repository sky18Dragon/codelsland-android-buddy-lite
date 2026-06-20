package com.codeisland.buddylite

import android.app.Application
import com.codeisland.buddylite.data.local.BuddyDatabase
import com.codeisland.buddylite.data.local.PreferencesDataStore
import com.codeisland.buddylite.di.ServiceLocator

class BuddyLiteApp : Application() {

    lateinit var locator: ServiceLocator
        private set

    override fun onCreate() {
        super.onCreate()
        val database = BuddyDatabase.build(this)
        val prefs = PreferencesDataStore(this)
        locator = ServiceLocator(applicationContext, database, prefs)
    }
}
