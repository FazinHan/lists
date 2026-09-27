package com.fazinhan.lists

import android.app.Application
import com.fazinhan.lists.work.Maintenance

class ListsApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Maintenance.createChannel(this)
        Maintenance.schedule(this)
    }
}
