import com.aiselp.autox.utils.Members
import org.junit.Test
import kotlin.reflect.KTypeProjection
import kotlin.reflect.full.createType
import kotlin.reflect.full.isSupertypeOf


class MembersTest {
    @Test
    fun test1() {
        println("test1------------------------")
        val p = P<E>()

        println(p::class.typeParameters)
        val type = p::class.createType(listOf(KTypeProjection.STAR))
        println(type)
        E::class.members.find { it.name == "p" }!!.let {
            println(it.parameters[1].type.classifier)
            val r = it.parameters[1].type.isSupertypeOf(type)
            println(r)
        }
    }

    @Test
    fun test2() {
        println("test2------------------------")
        val method = Members.findMethod(E::class, "add", arrayOf(String::class))
        check(method?.name == "add") { "Method not found" }
    }

    @Test
    fun test3() {
        println("test3------------------------")
        val p = P<E>()
        val method = Members.findMethod(E()::class, "p", arrayOf(p::class))
        check(method?.name == "p") { "Method not found" }
    }

    @Test
    fun test4() {
        println("test4------------------------")
        val list = listOf(1, 2, 3)
        val method = Members.findMethod(E::class, "r", arrayOf(list::class))
        check(method?.name == "r") { "Method not found" }
    }

    @Test
    fun test5() {
        println("test5------------------------")
        val method = Members.findMethod(E::class, "add", arrayOf(932::class))
        check(method != null) { "Method not found" }
        check(method.name == "add") { "Method not found" }
        check(method.call(E(), 5) == 9)
    }

    @Test
    fun test6() {
        println("test6------------------------")
        val method = Members.findMethod(E::class, "add", arrayOf(String::class))
        check(method != null) { "Method not found" }
        check(method.name == "add") { "Method not found" }
        check(method.call(E(), "1") == 78)
    }

    @Test
    fun test7() {
        println("test7------------------------")
        val list = listOf(1, 2, 3)
        val method = Members.findMethod(E::class, "m1", arrayOf(list::class))
        check(method != null) { "Method not found" }
        check(method.name == "m1") { "Method not found" }
        check(method.call(E(), list) == 2)
    }

    @Test
    fun test8() {
        println("test8------------------------")
        val map = mapOf(
            1 to "one",
            5 to "five"
        )
        val method = Members.findMethod(E::class, "m2", arrayOf(map::class))
        check(method != null) { "Method not found" }
        check(method.name == "m2") { "Method name Error" }
        check(method.call(E(), map) == "five")
    }
}

open class E {
    fun add() {}
    fun add(n: String) = 78
    fun <T> add(y: Int) = 9
    fun p(m: P<E>) {}
    fun r(t: List<Int>) {}
    fun m1(m: List<Int>) = m[1]
    fun m2(m: Map<Int, String>) = m[5]
}

class P<R : E> {}