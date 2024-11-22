import java.lang.invoke.MethodHandles
import java.lang.invoke.MethodType

data class Foo(val bar: String, val baz: Int)

fun main() {
    val changeName = DataCopy.bootstrap(
        MethodHandles.lookup(),
        "copy",
        MethodType.methodType(Person::class.java, Person::class.java, String::class.java),
        Person::class.java,
        "name"
    )
    requireNotNull(changeName) { "Method not found" }
    val changeRegisteredAndName = DataCopy.bootstrap(
        MethodHandles.lookup(),
        "copy",
        MethodType.methodType(Person::class.java, Person::class.java, Boolean::class.java, String::class.java),
        Person::class.java,
        "registered", "name"
    )
    requireNotNull(changeRegisteredAndName) { "Method not found" }

    println(changeName.type())
    val original = Person("Pepe", 123, false)

    val result1 = changeName.dynamicInvoker().invoke(original, "Ambrosio")
    println(result1)

    val result2 = changeRegisteredAndName.dynamicInvoker().invoke(original, true, "Luis")
    println(result2)

    val changeValue = DataCopy.bootstrap(
        MethodHandles.lookup(),
        "copy",
        // be careful! if something is generic it should be 'Any'
        MethodType.methodType(Result::class.java, Result::class.java, Any::class.java),
        Result::class.java,
        "value"
    )
    requireNotNull(changeValue) { "Method not found" }
    val originalR = Result(success = false, value = "foo")
    val resultR = changeValue.dynamicInvoker().invoke(originalR, "bar")
    println(resultR)
}