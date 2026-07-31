package com.stardust.autojs.core.image.capture

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import com.stardust.app.OnActivityResultDelegate
import com.stardust.autojs.core.util.ScriptPromiseAdapter
import kotlinx.coroutines.CompletableDeferred
import kotlin.coroutines.cancellation.CancellationException

/**
 * Created by Stardust on 2017/5/17.
 */
interface ScreenCaptureRequester {
    var screenCapture: ScreenCapturer?
    suspend fun requestScreenCapture(context: Context, orientation: Int)
    fun requestScreenCaptureLegacy(context: Context, orientation: Int): ScriptPromiseAdapter
    fun recycle()

    interface Callback {
        fun onRequestResult(result: Int, data: Intent?)
    }
    class ActivityScreenCaptureRequester(
        private val mMediator: OnActivityResultDelegate.Mediator,
        private val mActivity: Activity
    ) : OnActivityResultDelegate {
        val result = CompletableDeferred<Intent>()
        private var mCallback: Callback? = null
        init {
            mMediator.addDelegate(REQUEST_CODE_MEDIA_PROJECTION, this)
        }


        override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
            mMediator.removeDelegate(this)
            if (mCallback != null) {
                mCallback!!.onRequestResult(resultCode, data)
            } else if (resultCode == Activity.RESULT_OK) {
                result.complete(data!!)
            } else {
                result.cancel(CancellationException("user cancel"))
            }
        }

        fun cancel() {
            recycle()
            result.cancel()
        }

        suspend fun request(): Intent {
            mActivity.startActivityForResult(
                (mActivity.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager).createScreenCaptureIntent(),
                REQUEST_CODE_MEDIA_PROJECTION
            )
            val intent = result.await()
            recycle()
            return intent
        }

        fun recycle() {
            mMediator.removeDelegate(this)
        }

        fun setOnActivityResultCallback(callback: Callback?) {
            mCallback = callback
        }

        fun requestLegacy() {
            mActivity.startActivityForResult(
                (mActivity.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager)
                    .createScreenCaptureIntent(),
                REQUEST_CODE_MEDIA_PROJECTION
            )
        }

        companion object {
            private const val REQUEST_CODE_MEDIA_PROJECTION = 17777
        }
    }
}
