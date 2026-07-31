package com.stardust.autojs.core.image.capture

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.annotation.RequiresApi
import com.stardust.app.OnActivityResultDelegate
import com.stardust.util.IntentExtras

@RequiresApi(Build.VERSION_CODES.LOLLIPOP)
class ScreenCaptureRequestActivity : Activity() {

    private val mOnActivityResultDelegateMediator = OnActivityResultDelegate.Mediator()
    private var mScreenCaptureRequester: ScreenCaptureRequester.ActivityScreenCaptureRequester? = null
    private var mCallback: ScreenCaptureRequester.Callback? = null

    companion object {
        fun request(context: Context, callback: ScreenCaptureRequester.Callback) {
            val intent = Intent(context, ScreenCaptureRequestActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

            val extras = IntentExtras.newExtras()
                .put("callback", callback)
            extras.putInIntent(intent)

            context.startActivity(intent)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        try {
            super.onCreate(savedInstanceState)

            val extras = IntentExtras.fromIntentAndRelease(intent)
            if (extras == null) {
                finish()
                return
            }

            val callback = extras.get<ScreenCaptureRequester.Callback>("callback")
            if (callback == null) {
                finish()
                return
            }

            mCallback = callback

            mScreenCaptureRequester = ScreenCaptureRequester.ActivityScreenCaptureRequester(
                mOnActivityResultDelegateMediator, this
            )
        } catch (e: Exception) {
            Log.e("SCREEN_LEGACY", "创建失败: ${e.message}")
        }
        mScreenCaptureRequester?.setOnActivityResultCallback(object : ScreenCaptureRequester.Callback {
            override fun onRequestResult(result: Int, data: Intent?) {
                mCallback?.onRequestResult(result, data)
            }
        })
        mScreenCaptureRequester?.requestLegacy()
    }

    override fun onDestroy() {
        super.onDestroy()
        mCallback = null
        mScreenCaptureRequester?.cancel()
        mScreenCaptureRequester = null
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        mOnActivityResultDelegateMediator.onActivityResult(requestCode, resultCode, data)
        finish()
    }
}