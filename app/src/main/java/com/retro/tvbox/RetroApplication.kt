package com.retro.tvbox

import android.app.Application
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import com.retro.tvbox.input.InputRouter
import com.retro.tvbox.input.phone.PhoneControllerServer
import com.retro.tvbox.repository.CacheManager
import com.retro.tvbox.repository.GameDownloader
import com.retro.tvbox.repository.RepositoryClient
import com.retro.tvbox.save.MemoryCardManager
import com.retro.tvbox.save.SaveManager
import com.retro.tvbox.save.SaveStateService
import com.retro.tvbox.ui.theme.AppThemeManager

class RetroApplication : Application() {

    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    lateinit var cacheManager: CacheManager
        private set

    lateinit var saveManager: SaveManager
        private set

    lateinit var memoryCardManager: MemoryCardManager
        private set

    lateinit var saveStateService: SaveStateService
        private set

    lateinit var repositoryClient: RepositoryClient
        private set

    lateinit var gameDownloader: GameDownloader
        private set

    lateinit var inputRouter: InputRouter
        private set

    lateinit var phoneControllerServer: PhoneControllerServer
        private set

    lateinit var coreManager: com.retro.tvbox.core.CoreManager
        private set

    lateinit var biosManager: com.retro.tvbox.core.BiosManager
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this

        AppThemeManager.init(this)

        inputRouter = InputRouter.getInstance().apply { initialize(this@RetroApplication) }
        phoneControllerServer = PhoneControllerServer.getInstance(this@RetroApplication).apply { start() }

        coreManager = com.retro.tvbox.core.CoreManager(this)
        biosManager = com.retro.tvbox.core.BiosManager(this)
        cacheManager = CacheManager(this)
        saveManager = SaveManager(this)
        memoryCardManager = MemoryCardManager(this)
        saveStateService = SaveStateService(this)
        repositoryClient = RepositoryClient(this)
        gameDownloader = GameDownloader(this, cacheManager)

        // Enforce cache quotas on startup
        cacheManager.enforceLRU()
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= TRIM_MEMORY_RUNNING_CRITICAL) {
            cacheManager.enforceLRU()
        }
    }

    companion object {
        lateinit var instance: RetroApplication
            private set
    }
}
