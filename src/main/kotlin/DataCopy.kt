import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Opcodes.*
import org.objectweb.asm.Type
import java.lang.invoke.CallSite
import java.lang.invoke.ConstantCallSite
import java.lang.invoke.MethodHandles
import java.lang.invoke.MethodType
import kotlin.reflect.KType
import kotlin.reflect.full.memberProperties
import kotlin.reflect.full.primaryConstructor
import kotlin.reflect.javaType
import kotlin.reflect.jvm.javaConstructor
import kotlin.reflect.jvm.javaGetter

object DataCopy {
    @JvmStatic fun boostrap(
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
        val constructor = requireNotNull(kotlinKlass.primaryConstructor) { "No primary constructor" }
        val copyParameters = givenProperties.map { givenProperty ->
            val parameter = requireNotNull(constructor.parameters.find { it.name == givenProperty }) { "No parameter with name $givenProperty" }
            parameter.type.javaOrObject
        }
        val expectedMethodType = MethodType.methodType(klass, klass, *copyParameters.toTypedArray())
        require(type == expectedMethodType) { "Expected $expectedMethodType but got $type" }

        // construction of the class and method
        val asmKlass = Type.getType(klass)
        val copyMethodName = "copy\$${givenProperties.joinToString(separator = "\$")}"

        val cw = ClassWriter(ClassWriter.COMPUTE_MAXS + ClassWriter.COMPUTE_FRAMES)
        cw.visit(
            CLASSFILE_VERSION,
            ACC_PUBLIC + ACC_FINAL + ACC_SYNTHETIC + ACC_STATIC,
            "${klass.name.replace('.', '/')}\$\$${copyMethodName}",
            null,
            "java/lang/Object",
            null
        )

        val mv = cw.visitMethod(
            ACC_PUBLIC + ACC_STATIC + ACC_FINAL,
            copyMethodName,
            expectedMethodType.toMethodDescriptorString(),
            null,
            null
        )

        mv.visitCode()

        // step 1 of initialization of the class: allocate memory
        mv.visitTypeInsn(NEW, asmKlass.internalName)
        mv.visitInsn(DUP)

        // step 2 of initialization of the class: call constructor
        // push arguments to the constructor to the stack
        constructor.parameters.map { parameter ->
            val parameterName = parameter.name
            when (val index = givenProperties.indexOf(parameterName)) {
                -1 -> { // not found => we need to call the getter
                    mv.visitVarInsn(ALOAD, 0)
                    val getter = requireNotNull(kotlinKlass.memberProperties.find { it.name == parameterName }?.javaGetter) {
                        "No getter for $parameterName"
                    }
                    val getterType = MethodType.methodType(getter.returnType)
                    mv.visitMethodInsn(INVOKEVIRTUAL, asmKlass.internalName, getter.name, getterType.toMethodDescriptorString(), false)
                }
                else -> { // found => we need to get it from the parameters to 'copy'
                    val loadOpcode = Type.getType(parameter.type.javaOrObject).getOpcode(ILOAD)
                    mv.visitVarInsn(loadOpcode, index + 1)
                }
            }
        }
        // call the constructor itself
        val constructorType = lookup.unreflectConstructor(constructor.javaConstructor!!).type()
        mv.visitMethodInsn(
            INVOKESPECIAL,
            asmKlass.internalName,
            "<init>",
            constructorType.changeReturnType(Void.TYPE).toMethodDescriptorString(),
            false
        )

        mv.visitInsn(ARETURN)
        mv.visitMaxs(givenProperties.size + 1, -1)
        mv.visitEnd()

        cw.visitEnd()

        val classBytes = cw.toByteArray()
        val copyLookup = lookup.defineClass(classBytes)
        return ConstantCallSite(lookup.findStatic(copyLookup, copyMethodName, expectedMethodType))
    }

    @OptIn(ExperimentalStdlibApi::class)
    @JvmStatic val KType.javaOrObject: Class<*> get() = javaType as? Class<*> ?: Object::class.java

    const val CLASSFILE_VERSION: Int = 52
}