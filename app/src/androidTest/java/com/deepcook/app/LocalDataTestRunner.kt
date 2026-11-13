package com.deepcook.app

import android.app.Application
import androidx.test.runner.AndroidJUnitRunner

/** Historical UI tests exercise local repositories; never create accounts on the production API. */
class LocalDataTestRunner : AndroidJUnitRunner() {
    override fun callApplicationOnCreate(app: Application) {
        (app as DeepCookApplication).cloudEnabled = false
        super.callApplicationOnCreate(app)
    }
}
