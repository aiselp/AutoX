package com.stardust.autojs

import android.app.Application

class TestAutojs(application: Application) : AutoJs(application) {

    override fun ensureAccessibilityServiceEnabled() {
        TODO("Not yet implemented")
    }

    override fun waitForAccessibilityServiceEnabled() {
        TODO("Not yet implemented")
    }

    companion object {
        fun init(application: Application) {
            instance = TestAutojs(application)
        }
    }
}