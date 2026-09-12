package com.example.wavemuzix

import android.app.Application
import com.example.wavemuzix.network.MyDownloader
import org.schabi.newpipe.extractor.NewPipe

class WaveMuzixApp : Application() {
    override fun onCreate() {
        super.onCreate()
        NewPipe.init(MyDownloader.getInstance())
    }
}
