package com.stardust.autojs

import android.app.Application
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.runner.AndroidJUnit4
import com.aiselp.autox.test.activicy.TestLogActivity
import com.stardust.app.GlobalAppContext
import com.stardust.autojs.script.ScriptSource
import com.stardust.autojs.script.StringScriptSource
import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class V6ScriptTest {
    val application: Application = ApplicationProvider.getApplicationContext()

//    @get:Rule
//    val activityRule = ActivityScenarioRule(TestLogActivity::class.java)

    private fun getScriptEngineService(): ScriptEngineService {
        return AutoJs.instance.scriptEngineService
    }

    private fun openScriptSource(assetName: String): ScriptSource {
        application.assets.open("${v6AccessDir}/$assetName").use {
            return StringScriptSource(it.readBytes().toString(charset = Charsets.UTF_8))
        }
    }

    @Test
    fun base_test() = runBlocking {
        val resultViewer = ScriptResultViewer()
        val execute =
            getScriptEngineService().execute(openScriptSource("base.js"), resultViewer)
        resultViewer.waitForSuccess()

        assert(execute.engine.getGlobalProperty("a") == 5.0)
        assert(execute.engine.getGlobalProperty("b") == "abc")
    }

    @Test
    fun timers_test() = runBlocking {
        val resultViewer = ScriptResultViewer()
        val execute =
            getScriptEngineService().execute(openScriptSource("timers.js"), resultViewer)
        resultViewer.waitForSuccess(6000) { execute.engine.forceStop() }

        assert(execute.engine.getGlobalProperty("a").toDouble() == 5.0)
        assert(execute.engine.getGlobalProperty("b") == null)
        assert(execute.engine.getGlobalProperty("c").toDouble() == 800.0)
    }

    @Test
    fun shell_test(): Unit = runBlocking {
        val resultViewer = ScriptResultViewer()
        val execute =
            getScriptEngineService().execute(openScriptSource("shell.js"), resultViewer)
        resultViewer.waitForSuccess(3000) { execute.engine.forceStop() }
    }

    @Test
    fun threads_test(): Unit = runBlocking {
        val resultViewer = ScriptResultViewer()
        val execute =
            getScriptEngineService().execute(openScriptSource("threads.js"), resultViewer)
        resultViewer.waitForSuccess(5000) { execute.engine.forceStop() }
    }

    @Test
    fun base64_test(): Unit = runBlocking {
        val resultViewer = ScriptResultViewer()
        val execute =
            getScriptEngineService().execute(openScriptSource("base64.js"), resultViewer)
        resultViewer.waitForSuccess(5000) { execute.engine.forceStop() }
    }

    @Test
    fun dialog_test(): Unit = runBlocking {
        ActivityScenario.launch(TestLogActivity::class.java).use {
            val r = Job()
            it.onActivity { activity ->
                AutoJs.instance.appUtils.currentActivity = activity
                r.complete()
            }
            r.join()
            val resultViewer = ScriptResultViewer()
            getScriptEngineService().execute(openScriptSource("dialog.js"), resultViewer)
            resultViewer.waitForSuccess()
        }
    }

    companion object {
        const val v6AccessDir = "v6_test_script"

        init {
            val application: Application = ApplicationProvider.getApplicationContext()
            GlobalAppContext.set(
                application,
                com.stardust.app.BuildConfig(
                    true, "org.autojs.autoxjs", VERSION_CODE = BuildConfig.VERSION_CODE.toLong(),
                )
            )
//            val context = InstrumentationRegistry.getInstrumentation().targetContext
            TestAutojs.init(application)
        }
    }
}