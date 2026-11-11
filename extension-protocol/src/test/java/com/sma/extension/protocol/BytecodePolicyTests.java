package com.sma.extension.protocol;

import static org.assertj.core.api.Assertions.*;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.*;

class BytecodePolicyTests {
    @Test void directProcessExitAndNativeMethodsAreRejectedWithoutInitializingTheClass() {
        var dangerous=new ClassWriter(0);dangerous.visit(Opcodes.V21,Opcodes.ACC_PUBLIC,"example/Dangerous",null,"java/lang/Object",null);
        var method=dangerous.visitMethod(Opcodes.ACC_STATIC,"<clinit>","()V",null,null);method.visitCode();method.visitInsn(Opcodes.ICONST_0);
        method.visitMethodInsn(Opcodes.INVOKESTATIC,"java/lang/System","exit","(I)V",false);method.visitInsn(Opcodes.RETURN);method.visitMaxs(1,0);method.visitEnd();dangerous.visitEnd();
        assertThatThrownBy(() -> BytecodePolicy.inspect(dangerous.toByteArray(),"example/Dangerous",true)).hasMessage("forbidden-plugin-api");
        var nativeClass=new ClassWriter(0);nativeClass.visit(Opcodes.V21,Opcodes.ACC_PUBLIC,"example/Native",null,"java/lang/Object",null);
        nativeClass.visitMethod(Opcodes.ACC_PUBLIC|Opcodes.ACC_NATIVE,"run","()V",null,null).visitEnd();nativeClass.visitEnd();
        assertThatThrownBy(() -> BytecodePolicy.inspect(nativeClass.toByteArray(),"example/Native",false)).hasMessage("native-method-forbidden");
    }
    @Test void normalExceptionsAreAllowedButHiddenHostReferencesAreRejected() {
        var safe=new ClassWriter(0);safe.visit(Opcodes.V21,Opcodes.ACC_PUBLIC,"example/Safe",null,"java/lang/RuntimeException",null);safe.visitEnd();
        assertThatCode(() -> BytecodePolicy.inspect(safe.toByteArray(),"example/Safe",true)).doesNotThrowAnyException();
        var invalid=new ClassWriter(0);invalid.visit(Opcodes.V21,Opcodes.ACC_PUBLIC,"example/Invalid",null,"java/lang/Object",null);
        invalid.visitField(Opcodes.ACC_PRIVATE,"host","Lcom/sma/admin/Secret;",null,null).visitEnd();invalid.visitEnd();
        assertThatThrownBy(() -> BytecodePolicy.inspect(invalid.toByteArray(),"example/Invalid",true)).hasMessage("host-internal-reference");
    }
}
