package com.vidgrab.app

import android.app.Application
import com.vidgrab.app.core.DownloadRepository
import com.vidgrab.app.core.Engine
import com.vidgrab.app.core.UpdateManager
import com.vidgrab.app.service.DownloadService
import com.vidgrab.app.service.UpdateService
import kotlinx.coroutines.launch

class VidGrabApp : Application() {

    override fun onCreate() {
        super.onCreate()
        // Notification channels pehle
        DownloadService.createChannel(this)
        UpdateService.createChannel(this)
        // Repository (queue/history restore)
        DownloadRepository.initOnce(this)
        // Engine init + silent update check — background thread
        DownloadRepository.appScope.launch {
            Engine.init(this@VidGrabApp)
            UpdateManager.checkOnLaunch(this@VidGrabApp)
        }
    }
}
