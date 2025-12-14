package com.aiselp.autox.test

import android.app.Application
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.MediumTest
import com.aiselp.autox.test.activicy.TestLogActivity
import com.aiselp.autox.test.utils.getGlobalProperty
import com.aiselp.autox.test.utils.openAssetDir
import com.aiselp.autox.test.utils.openScriptAsset
import com.aiselp.autox.test.utils.toDouble
import com.stardust.autojs.AutoJs
import com.stardust.autojs.R
import com.stardust.autojs.ScriptEngineService
import com.stardust.autojs.ScriptResultViewer
import com.stardust.autojs.TestAutojs
import com.stardust.autojs.execution.ExecutionConfig
import com.stardust.autojs.script.ScriptFile
import com.stardust.autojs.script.ScriptSource
import com.stardust.autojs.script.StringScriptSource
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.io.path.pathString

@RunWith(AndroidJUnit4::class)
@MediumTest
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
    fun app_test(): Unit = runBlocking {
        val resultViewer = ScriptResultViewer()
        val execute =
            getScriptEngineService().execute(openScriptSource("app.js"), resultViewer)
        resultViewer.waitForSuccess(1000) { execute.engine.forceStop() }
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
        runScriptAssetSuccessfully("shell.js", 3000)
    }

    @Test
    fun threads_test() {
        runScriptAssetSuccessfully("threads.js", 5000)
    }

    @Test
    fun threads2_test() {
        val resultViewer = ScriptResultViewer()
        val execute =
            getScriptEngineService().execute(openScriptSource("threads2.js"), resultViewer)
        runBlocking {
            resultViewer.waitForSuccess(5000) { execute.engine.forceStop() }
        }
        assert(execute.engine.getGlobalProperty("a") == 4.0)
        assert(execute.engine.getGlobalProperty("b") == 6.0)
        assert(execute.engine.getGlobalProperty("c") == 7.0)
        assert(execute.engine.getGlobalProperty("d") == 99.0)
    }

    @Test
    fun emitter_test() {
        runScriptAssetSuccessfully("emitter.js", 2500)
    }

    @Test
    fun base64_test() {
        runScriptAssetSuccessfully("base64.js", 2500)
    }

    @Test
    fun dialog_test(): Unit = runBlocking {
        val job = launch {
            delay(1000)
            Espresso.onView(withText(R.string.ok)).perform(click())
            delay(500)
            Espresso.onView(withText(R.string.ok)).perform(click())
            delay(2500)
            Espresso.onView(withText(R.string.ok)).perform(click())
            delay(2500)
            Espresso.onView(withText("选项C")).perform(click())
        }
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
        job.cancel()
    }

    @Test
    fun zips(): Unit = runBlocking {
        val file = openScriptAsset(application, "$v6AccessDir/zips.js")
        val resultViewer = ScriptResultViewer()
        getScriptEngineService().execute(
            ScriptFile(file.toFile()).toSource(),
            resultViewer,
            ExecutionConfig(workingDirectory = file.parent.toString())
        )
        resultViewer.waitForSuccess()
    }

    @Test
    fun java_type() {
        runScriptAssetSuccessfully("java_type.js", 1000)
    }

    @Test
    fun java_adapter() {
        runScriptAssetSuccessfully("java_adapter.js", 1000)
    }

    @Test
    fun images(): Unit = runBlocking {
        val dir = openAssetDir(application, "$v6AccessDir/images")
        val resultViewer = ScriptResultViewer()
        getScriptEngineService().execute(
            ScriptFile(dir.resolve("test1.js").pathString).toSource(),
            resultViewer,
            ExecutionConfig(workingDirectory = dir.pathString)
        )
        resultViewer.waitForSuccess()
    }

    @Test
    fun images_transparentMask(): Unit = runBlocking {
        val dir = openAssetDir(application, "$v6AccessDir/images_transparentMask")
        val resultViewer = ScriptResultViewer()
        getScriptEngineService().execute(
            ScriptFile(dir.resolve("test.js").pathString).toSource(),
            resultViewer,
            ExecutionConfig(workingDirectory = dir.pathString)
        )
        resultViewer.waitForSuccess()
    }

    @Test
    fun modules_import() {
        runScriptAssetSuccessfully("modules_import.js")
    }

    @Test
    fun http() {
        val file = openScriptAsset(application, "utils/http_server.mjs")
        val execute = getScriptEngineService().execute(ScriptFile(file.toFile()).toSource())
        Thread.sleep(200)
        try {
            runScriptAssetSuccessfully("http.js", 5000)
        } finally {
            execute.engine.forceStop()
            Thread.sleep(200)
        }
    }

    fun runScriptAssetSuccessfully(assetName: String, timeout: Long = 60 * 1000): Unit {
        val resultViewer = ScriptResultViewer()
        val execute =
            getScriptEngineService().execute(openScriptSource(assetName), resultViewer)
        runBlocking {
            resultViewer.waitForSuccess(timeout) { execute.engine.forceStop() }
        }
    }

    companion object {
        const val v6AccessDir = "v6_test_script"

        init {
            val application: Application = ApplicationProvider.getApplicationContext()
//            val context = InstrumentationRegistry.getInstrumentation().targetContext
            TestAutojs.init(application)
        }
    }
}