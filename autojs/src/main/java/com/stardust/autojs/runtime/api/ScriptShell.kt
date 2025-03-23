package com.stardust.autojs.runtime.api

import com.stardust.autojs.annotation.ScriptInterface
import com.stardust.autojs.core.shizuku.ShizukuClient
import com.stardust.autojs.core.util.Shell2
import java.util.concurrent.CopyOnWriteArraySet

class ScriptShell {
    private val shellList = CopyOnWriteArraySet<Shell2>()
    private var suShell: Shell2? = null
    private var shShell: Shell2? = null

    @Synchronized
    private fun openShell(root: Boolean): Shell2 {
        if (root) {
            if (suShell == null) {
                suShell = Shell2("su")
            }
            return suShell!!
        } else {
            if (shShell == null) {
                shShell = Shell2("sh")
            }
            return shShell!!
        }
    }

    @ScriptInterface
    fun exec(cmd: String, root: Boolean): AbstractShell.Result {
        return openShell(root).execAndWaitFor(cmd)
    }


    @ScriptInterface
    fun createShell(root: Boolean): Shell2 {
        val shell = if (root) {
            Shell2("su")
        } else Shell2("sh")
        shellList.add(shell)
        return shell
    }


    @ScriptInterface
    fun isShizukuAlive(): Boolean = ShizukuClient.instance.shizukuConnection.service != null

    fun recycle(console: Console) {
        suShell?.exit()
        shShell?.exit()
        var num = 0
        shellList.forEach {
            if (it.isAlive()) {
                num++
                it.exit()
            } else it.close()
        }
        shellList.clear()
        if (num > 0) {
            console.warn("$num shell not recovered")
        }
    }

    companion object {
        private const val TAG = "ScriptShell"
    }
}