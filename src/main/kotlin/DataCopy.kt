import java.lang.invoke.CallSite
import java.lang.invoke.ConstantCallSite
import java.lang.invoke.MethodHandle
import java.lang.invoke.MethodHandles
import java.lang.invoke.MethodType
import kotlin.reflect.KFunction
import kotlin.reflect.KProperty1
import kotlin.reflect.javaType
import kotlin.reflect.jvm.javaGetter
import kotlin.reflect.jvm.javaMethod

object DataCopy {
    @OptIn(ExperimentalStdlibApi::class)
    @JvmStatic fun bootstrap(
        lookup: MethodHandles.Lookup,
        name: String,
        type: MethodType,
        // these will eventually be given from the constant pool
        // https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-4.html#jvms-4.4
        klass: Class<*>,
        vararg givenProperties: String,
    ): CallSite? {
        // requirements
        val kotlinKlass = klass.kotlin
        require(kotlinKlass.isData) { "Only data classes are supported" }
        require(name == "copy") { "Only copy is supported" }
        val copyInfo = kotlinKlass.members.singleOrNull() { it.name == "copy" }
        requireNotNull(copyInfo as? KFunction<*>) { "No copy method found" }

        // create list of arguments
        val getters = mutableListOf<MethodHandle>()
        val indices = mutableListOf<Int>(0) // always start with 'this'
        for (parameter in copyInfo.parameters.drop(1)) {
            val parameterName = parameter.name
            when (val index = givenProperties.indexOf(parameterName)) {
                -1 -> {
                    val property = requireNotNull(kotlinKlass.members.singleOrNull { it.name == parameterName }) {
                        "No property '$parameterName' found"
                    }
                    val getter = requireNotNull((property as? KProperty1<*, *>)?.javaGetter) {
                        "No getter for '$parameterName' found"
                    }
                    getters += lookup.unreflect(getter)
                    indices += 0
                }
                else -> {
                    getters += MethodHandles.identity(parameter.type.javaType as? Class<*> ?: Any::class.java)
                    indices += index + 1
                }
            }
        }

        val copyWithGetters = MethodHandles.filterArguments(lookup.unreflect(copyInfo.javaMethod), 1, *getters.toTypedArray())
        val copyReordered = MethodHandles.permuteArguments(copyWithGetters, type, *indices.toIntArray())
        return ConstantCallSite(copyReordered)
    }
}