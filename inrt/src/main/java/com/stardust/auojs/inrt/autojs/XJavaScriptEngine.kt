package com.stardust.auojs.inrt.autojs

import android.content.Context
import com.stardust.autojs.engine.LoopBasedJavaScriptEngine
import com.stardust.autojs.engine.encryption.ScriptEncryption.decrypt
import com.stardust.autojs.script.EncryptedScriptFileHeader
import com.stardust.autojs.script.EncryptedScriptFileHeader.isValidFile
import com.stardust.autojs.script.JavaScriptFileSource
import com.stardust.autojs.script.ScriptSource
import com.stardust.autojs.script.StringScriptSource

class XJavaScriptEngine(context: Context) : LoopBasedJavaScriptEngine(context) {


    override fun execute(source: ScriptSource, callback: ExecuteCallback?) {
        if (source is JavaScriptFileSource) {
            val data = source.file.readBytes()
            if (isValidFile(data)) {
                val script = decrypt(data, EncryptedScriptFileHeader.BLOCK_SIZE)
                    .toString(Charsets.UTF_8)
                super.execute(
                    StringScriptSource(source.file.name, script), callback
                )
                return
            }
        }
        super.execute(source, callback)
    }

}