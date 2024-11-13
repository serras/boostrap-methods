import java.lang.invoke.MethodHandles
import java.lang.invoke.MethodType

data class Foo(val bar: String, val baz: Int)

fun main() {
    val method = DataCopy.boostrap(
        MethodHandles.lookup(),
        "copy",
        MethodType.methodType(Foo::class.java, Foo::class.java, String::class.java),
        Foo::class.java,
        "bar"
    )
    requireNotNull(method) { "Method not found" }

    println(method.type())
    val original = Foo("hello", 123)
    val result = method.dynamicInvoker().invoke(original, "bye")
    println(result)
}