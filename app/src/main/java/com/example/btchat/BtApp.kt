package com.example.btchat

import android.app.Application
import com.example.btchat.core.Engine

class BtApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Engine.init(this)
    }
}
