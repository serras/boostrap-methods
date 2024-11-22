import java.lang.invoke.CallSite
import java.lang.invoke.ConstantCallSite
import java.lang.invoke.MethodHandle
import java.lang.invoke.MethodHandles
import java.lang.invoke.MethodType
import kotlin.reflect.KFunction
import kotlin.reflect.javaType

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
        val copyHandle = klass.declaredMethods.singleOrNull { it.name == "copy" }
        val copyInfo = kotlinKlass.members.singleOrNull { it.name == "copy" }
        require(copyHandle != null && copyInfo is KFunction<*>) { "No copy method found" }

        // create list of arguments
        val getters = mutableListOf<MethodHandle>()
        val indices = mutableListOf<Int>(0) // always start with 'this'
        for (parameter in copyInfo.parameters.drop(1)) {
            val parameterName = requireNotNull(parameter.name)
            when (val index = givenProperties.indexOf(parameterName)) {
                -1 -> {
                    val getterName = "get${parameterName.replaceFirstChar { it.uppercaseChar() }}"
                    val getter = requireNotNull(klass.getDeclaredMethod(getterName)) {
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

        val copyWithGetters = MethodHandles.filterArguments(lookup.unreflect(copyHandle), 1, *getters.toTypedArray())
        val copyReordered = MethodHandles.permuteArguments(copyWithGetters, type, *indices.toIntArray())
        return ConstantCallSite(copyReordered)
    }
}