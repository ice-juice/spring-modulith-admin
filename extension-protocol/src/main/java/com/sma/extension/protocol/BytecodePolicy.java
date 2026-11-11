package com.sma.extension.protocol;

import java.util.List;
import java.util.Set;
import org.objectweb.asm.*;

/** Admission checks for common forbidden APIs, never advertised as a malicious-code sandbox. */
public final class BytecodePolicy {
    private BytecodePolicy() {}
    private static final List<String> FORBIDDEN=List.of("java/lang/Thread","java/lang/ClassLoader","java/lang/Process",
            "java/lang/instrument/","java/lang/reflect/","java/lang/foreign/","java/net/","java/nio/file/","java/io/File",
            "java/io/RandomAccessFile","java/io/ObjectInputStream","java/util/concurrent/Executors","java/util/concurrent/ThreadPoolExecutor",
            "java/util/concurrent/ScheduledThreadPoolExecutor","java/util/Timer","javax/script/","sun/","jdk/internal/");
    public static void inspect(byte[] bytes,String expectedName,boolean pluginOwned) {
        try {
            var reader=new ClassReader(bytes);Checks.require(reader.getClassName().equals(expectedName),"class-name-mismatch");
            reader.accept(new ClassVisitor(Opcodes.ASM9) {
                @Override public void visit(int version,int access,String name,String signature,String parent,String[] interfaces) {
                    if(pluginOwned){type(parent);if(interfaces!=null)for(String value:interfaces)type(value);}
                }
                @Override public FieldVisitor visitField(int access,String name,String descriptor,String signature,Object value) {
                    if(pluginOwned)descriptor(descriptor);return null;
                }
                @Override public MethodVisitor visitMethod(int access,String name,String descriptor,String signature,String[] exceptions) {
                    Checks.require((access&Opcodes.ACC_NATIVE)==0,"native-method-forbidden");
                    if(!pluginOwned)return null;
                    descriptor(descriptor);if(exceptions!=null)for(String exception:exceptions)type(exception);
                    return new MethodVisitor(Opcodes.ASM9) {
                        @Override public void visitTypeInsn(int opcode,String owner){type(owner);}
                        @Override public void visitFieldInsn(int opcode,String owner,String name,String desc){type(owner);descriptor(desc);}
                        @Override public void visitMethodInsn(int opcode,String owner,String method,String desc,boolean itf){call(owner,method);descriptor(desc);}
                        @Override public void visitInvokeDynamicInsn(String name,String descriptor,Handle bootstrap,Object... arguments) {
                            Checks.require(Set.of("java/lang/invoke/LambdaMetafactory","java/lang/invoke/StringConcatFactory","java/lang/runtime/ObjectMethods").contains(bootstrap.getOwner()),"unsupported-bootstrap-method");
                            descriptor(descriptor);
                            for(Object argument:arguments)if(argument instanceof Handle handle)call(handle.getOwner(),handle.getName());
                        }
                        @Override public void visitLdcInsn(Object value){Checks.require(!(value instanceof ConstantDynamic),"unsupported-constant-bootstrap");if(value instanceof Type constant)asmType(constant);if(value instanceof Handle handle)call(handle.getOwner(),handle.getName());}
                        @Override public void visitMultiANewArrayInsn(String desc,int dimensions){descriptor(desc);}
                    };
                }
            },ClassReader.SKIP_DEBUG|ClassReader.SKIP_FRAMES);
        } catch(ProtocolException denied){throw denied;}
        catch(RuntimeException malformed){throw new ProtocolException("invalid-class-format",malformed);}
    }
    private static void call(String owner,String method) {
        type(owner);
        Checks.require(!owner.startsWith("java/lang/invoke/"),"forbidden-plugin-api");
        if(owner.equals("java/lang/System"))Checks.require(!Set.of("exit","setSecurityManager","setProperties","setProperty","getProperties","getProperty","getenv","load","loadLibrary","setOut","setErr","setIn").contains(method),"forbidden-plugin-api");
        if(owner.equals("java/lang/Class"))Checks.require(!method.startsWith("getDeclared") && !Set.of("forName","getClassLoader","getMethod","getMethods","getField","getFields","getConstructor","getConstructors","newInstance","getResource","getResourceAsStream","getProtectionDomain").contains(method),"forbidden-plugin-api");
        if(owner.equals("java/util/concurrent/ForkJoinPool") || owner.equals("java/util/concurrent/CompletableFuture"))Checks.require(false,"unmanaged-async-forbidden");
    }
    private static void type(String name) {
        if(name==null)return;if(name.startsWith("[")){descriptor(name);return;}
        Checks.require(!name.equals("java/lang/Runtime"),"forbidden-plugin-api");
        Checks.require(FORBIDDEN.stream().noneMatch(name::startsWith),"forbidden-plugin-api");
        Checks.require(!name.startsWith("com/sma/") || name.startsWith("com/sma/extension/api/"),"host-internal-reference");
    }
    private static void descriptor(String value){asmType(Type.getType(value));}
    private static void asmType(Type value) {
        switch(value.getSort()) {
            case Type.OBJECT -> type(value.getInternalName());
            case Type.ARRAY -> asmType(value.getElementType());
            case Type.METHOD -> {asmType(value.getReturnType());for(Type argument:value.getArgumentTypes())asmType(argument);}
            default -> { }
        }
    }
}
