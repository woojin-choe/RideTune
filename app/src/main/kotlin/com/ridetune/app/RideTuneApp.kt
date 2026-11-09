package com.ridetune.app

import android.app.Application
import com.ridetune.app.data.ProfileRepository
import com.ridetune.app.data.vehicle.CabinController
import com.ridetune.app.data.vehicle.PleosCabinController

/** 앱 전체에서 하나씩만 쓰는 객체를 둔다. */
class RideTuneApp : Application() {
    lateinit var profiles: ProfileRepository
        private set
    lateinit var cabin: CabinController
        private set

    override fun onCreate() {
        super.onCreate()
        profiles = ProfileRepository(this)
        cabin = PleosCabinController(this).also { it.start() }
    }
}
