package com.ratig.app

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.ratig.app.core.sync.SyncWorker
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

/**
 * On-demand WorkManager initialization (default initializer removed in
 * AndroidManifest.xml) so [SyncWorker] gets its dependencies through
 * HiltWorkerFactory. A CONNECTED-constrained periodic sync pass is kept
 * scheduled for the whole process lifetime; manual passes and the
 * reconnect trigger use one-shot requests (see [SyncWorker]).
 */
@HiltAndroidApp
class RatigApp : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        SyncWorker.schedulePeriodic(this)
    }
}
