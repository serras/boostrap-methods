import java.lang.invoke.CallSite
import java.lang.invoke.ConstantCallSite
import java.lang.invoke.MethodHandle
import java.lang.invoke.MethodHandles
import java.lang.invoke.MethodType
import kotlin.reflect.jvm.javaGetter
import kotlin.reflect.jvm.javaMethod

data class Person(val name: String, val age: Int, val registered: Boolean) {
    companion object {
        @OptIn(ExperimentalStdlibApi::class)
        @JvmStatic
        fun bootstrap(
            lookup: MethodHandles.Lookup,
            name: String,
            type: MethodType,
            vararg modifiedProperties: String,
        ): CallSite {
            require(name == "copy") { "Only copy is supported" }

            val getters = mutableListOf<MethodHandle>()
            val indices = mutableListOf<Int>(0) // always start with 'this'

            fun addProperty(property: String, getter: MethodHandle) {
                when (val index = modifiedProperties.indexOf(property)) {
                    -1 -> {
                        getters += getter
                        indices += 0
                    }
                    else -> {
                        getters += MethodHandles.identity(getter.type().returnType())
                        indices += index + 1
                    }
                }
            }

            addProperty("name", lookup.unreflect(Person::name.javaGetter))
            addProperty("age", lookup.unreflect(Person::age.javaGetter))

            val copyWithGetters = MethodHandles.filterArguments(lookup.unreflect(Person::copy.javaMethod), 1, *getters.toTypedArray())
            val copyReordered = MethodHandles.permuteArguments(copyWithGetters, type, *indices.toIntArray())
            return ConstantCallSite(copyReordered)
        }
    }
}
