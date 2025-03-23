package org.autojs.autojs

import android.annotation.SuppressLint
import android.app.Application
import android.app.LocaleManager
import android.content.Intent
import android.os.Build
import android.os.LocaleList
import android.os.Process
import android.util.Log
import android.webkit.WebView
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.core.os.LocaleListCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.work.Configuration
import com.aiselp.autox.engine.NodeScriptEngine.Companion.initModuleResource
import com.aiselp.autox.ui.material3.activity.ErrorReportActivity
import com.stardust.app.GlobalAppContext
import com.stardust.autojs.core.pref.PrefKey
import com.stardust.autojs.servicecomponents.EngineController
import com.stardust.autojs.util.ProcessUtils
import com.stardust.theme.ThemeColor
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.autojs.autojs.autojs.AutoJs
import org.autojs.autojs.autojs.key.GlobalKeyObserver
import org.autojs.autojs.external.receiver.DynamicBroadcastReceivers
import org.autojs.autojs.theme.ThemeColorManagerCompat
import org.autojs.autojs.timing.TimedTaskManager
import org.autojs.autojs.timing.TimedTaskScheduler
import org.autojs.autojs.ui.main.MainActivity
import org.autojs.autoxjs.BuildConfig
import org.autojs.autoxjs.R
import rikka.shizuku.ShizukuProvider
import java.lang.ref.WeakReference

/**
 * Created by Stardust on 2017/1/27.
 */

class App : Application(), Configuration.Provider {
    lateinit var dynamicBroadcastReceivers: DynamicBroadcastReceivers
        private set


    override fun onCreate() {
        super.onCreate()
        GlobalAppContext.set(
            this, com.stardust.app.BuildConfig.generate(BuildConfig::class.java)
        )
        instance = WeakReference(this)
        setUpDebugEnvironment()
        init()
    }


    private fun setUpDebugEnvironment() {
        ErrorReportActivity.install(this, MainActivity::class.java)
    }

    private fun init() {
        initLanguage()
        ThemeColorManagerCompat.init(
            this,
            ThemeColor(
                ContextCompat.getColor(this, R.color.colorPrimary),
                ContextCompat.getColor(this, R.color.colorPrimaryDark),
                ContextCompat.getColor(this, R.color.colorAccent)
            )
        )
        if (ProcessUtils.isScriptProcess(this)) {
            AutoJs.initInstance(this)
            if (Pref.isRunningVolumeControlEnabled()) {
                GlobalKeyObserver.init()
            }
            TimedTaskScheduler.init(this)
            initDynamicBroadcastReceivers()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                WebView.setDataDirectorySuffix(getString(R.string.text_script_process_name))
            };
        } else if (ProcessUtils.isMainProcess(this)) {
            initResource()
            EngineController.scope.launch {
                delay(1000)
                ShizukuProvider.requestBinderForNonProviderProcess(this@App)
            }
        }
        Log.i(
            TAG, "Pid: ${Process.myPid()}, isScriptProcess: ${ProcessUtils.isScriptProcess(this)}"
        )
    }

    private fun initLanguage() {
        fun changeLanguage(language: String?) {
            if (language == null) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    getSystemService(LocaleManager::class.java)
                        .applicationLocales = LocaleList.getEmptyLocaleList()
                } else AppCompatDelegate.setApplicationLocales(LocaleListCompat.getEmptyLocaleList())
            } else
                AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(language))
        }

        val l = Pref.def().getString(PrefKey.KEY_LANGUAGE, null)
        Pref.def().registerOnSharedPreferenceChangeListener { _, key ->
            if (key == PrefKey.KEY_LANGUAGE) {
                changeLanguage(key)
            }
        }
        changeLanguage(l)
    }

    private fun initResource() {
        val appVersionChange =
            Pref.def().getInt(getString(R.string.key_init_resource), 0) != BuildConfig.VERSION_CODE
        Thread {
            initModuleResource(this, appVersionChange)
            if (appVersionChange) {
                Pref.def().edit(commit = true) {
                    putInt(getString(R.string.key_init_resource), BuildConfig.VERSION_CODE)
                }
            }
        }.start()
    }

    @SuppressLint("CheckResult")
    private fun initDynamicBroadcastReceivers() {
        dynamicBroadcastReceivers = DynamicBroadcastReceivers(this)
        val localActions = ArrayList<String>()
        val actions = ArrayList<String>()
        TimedTaskManager.allIntentTasks
            .filter { task -> task.action != null }
            .doOnComplete {
                if (localActions.isNotEmpty()) {
                    dynamicBroadcastReceivers.register(localActions, true)
                }
                if (actions.isNotEmpty()) {
                    dynamicBroadcastReceivers.register(actions, false)
                }
                @Suppress("DEPRECATION")
                LocalBroadcastManager.getInstance(applicationContext).sendBroadcast(
                    Intent(
                        DynamicBroadcastReceivers.ACTION_STARTUP
                    )
                )
            }
            .subscribe({
                if (it.isLocal) {
                    it.action?.let { it1 -> localActions.add(it1) }
                } else {
                    it.action?.let { it1 -> actions.add(it1) }
                }
            }, { it.printStackTrace() })


    }

    companion object {
        private const val TAG = "App"

        private lateinit var instance: WeakReference<App>

        init {
            ShizukuProvider.enableMultiProcessSupport(false)
        }

        val app: App
            get() = instance.get()!!
    }


    override val workManagerConfiguration: Configuration
        get() {
            return Configuration.Builder()
                .setMinimumLoggingLevel(Log.INFO)
                .build()
        }

}
