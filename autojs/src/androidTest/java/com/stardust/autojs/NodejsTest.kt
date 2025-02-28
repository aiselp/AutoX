package com.stardust.autojs

import androidx.test.runner.AndroidJUnit4
import com.caoccao.javet.interop.NodeRuntime
import com.caoccao.javet.interop.V8Host
import com.caoccao.javet.values.primitive.V8ValueInteger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NodejsTest {
    val scope: CoroutineScope = CoroutineScope(Dispatchers.Default)

    @Test
    fun test1() {
        println(12345)
    }

    @Test
    fun base_test() {
        V8Host.getNodeInstance().createV8Runtime<NodeRuntime>().use { runtime ->
            val r = runtime.getExecutor("123").execute<V8ValueInteger>().toPrimitive()
            assert(r == 123)
        }
    }
}