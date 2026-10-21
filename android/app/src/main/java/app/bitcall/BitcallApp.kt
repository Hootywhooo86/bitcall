package app.bitcall

import android.app.Application
import app.bitcall.data.NotesStore
import app.bitcall.data.Settings
import app.bitcall.telecom.CallNotifications

class BitcallApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Settings.init(this)
        NotesStore.init(this)
        CallNotifications.createChannels(this)
    }
}
