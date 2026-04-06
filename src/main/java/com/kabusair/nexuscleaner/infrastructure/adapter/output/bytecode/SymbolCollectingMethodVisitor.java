package com.kabusair.nexuscleaner.infrastructure.adapter.output.bytecode;

import com.kabusair.nexuscleaner.core.domain.model.SymbolReference;
import org.objectweb.asm.ConstantDynamic;
import org.objectweb.asm.Handle;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

import java.util.Set;
import java.util.regex.Pattern;

/**
 * Visits the body of one method and harvests every referenced symbol — including
 * string constants passed to reflection sinks and every target reachable through
 * an {@code invokedynamic} bootstrap argument list (lambdas, method references,
 * condy). The reflection detector is a single-slot {@code LDC} look-behind: the
 * most recent string constant is promoted to a {@code REFLECTION_HINT} when the
 * next instruction is a known reflection entry point.
 */
class SymbolCollectingMethodVisitor extends MethodVisitor {

    private static final Pattern FQCN_PATTERN =
            Pattern.compile("^[a-zA-Z_$][\\w$]*(\\.[a-zA-Z_$][\\w$]*)+$");

    private final Set<SymbolReference> sink;
    private String pendingStringConstant;

    SymbolCollectingMethodVisitor(Set<SymbolReference> sink, MethodVisitor downstream) {
        super(Opcodes.ASM9, downstream);
        this.sink = sink;
    }

    @Override
    public void visitTypeInsn(int opcode, String type) {
        sink.add(SymbolReference.type(internalToFqcn(type)));
        super.visitTypeInsn(opcode, type);
    }

    @Override
    public void visitFieldInsn(int opcode, String owner, String name, String descriptor) {
        sink.add(SymbolReference.field(internalToFqcn(owner), name));
        collectTypesFromDescriptor(descriptor);
        super.visitFieldInsn(opcode, owner, name, descriptor);
    }

    @Override
    public void visitMethodInsn(int opcode, String owner, String name, String descriptor, boolean isInterface) {
        sink.add(SymbolReference.method(internalToFqcn(owner), name));
        collectTypesFromDescriptor(descriptor);
        promotePendingStringIfReflectionSink(owner, name, descriptor);
        pendingStringConstant = null;
        super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
    }

    @Override
    public void visitInvokeDynamicInsn(String name, String descriptor, Handle bsm, Object... bsmArgs) {
        collectTypesFromDescriptor(descriptor);
        collectHandle(bsm);
        collectBootstrapArguments(bsmArgs);
        pendingStringConstant = null;
        super.visitInvokeDynamicInsn(name, descriptor, bsm, bsmArgs);
    }

    @Override
    public void visitLdcInsn(Object value) {
        rememberOrConsumeConstant(value);
        super.visitLdcInsn(value);
    }

    @Override
    public void visitInsn(int opcode) {
        if (opcode != Opcodes.NOP) {
            pendingStringConstant = null;
        }
        super.visitInsn(opcode);
    }

    private void promotePendingStringIfReflectionSink(String owner, String name, String descriptor) {
        if (pendingStringConstant == null) return;
        if (!isReflectionSink(owner, name, descriptor)) return;
        if (!looksLikeFqcn(pendingStringConstant)) return;
        sink.add(SymbolReference.reflectionHint(pendingStringConstant));
    }

    private void rememberOrConsumeConstant(Object value) {
        if (value instanceof String s) {
            pendingStringConstant = s;
            return;
        }
        if (value instanceof Type t && t.getSort() == Type.OBJECT) {
            sink.add(SymbolReference.type(t.getClassName()));
        }
        pendingStringConstant = null;
    }

    private void collectBootstrapArguments(Object[] bsmArgs) {
        if (bsmArgs == null) return;
        for (Object arg : bsmArgs) {
            collectBootstrapArgument(arg);
        }
    }

    private void collectBootstrapArgument(Object arg) {
        if (arg == null) return;
        if (arg instanceof Type t) {
            collectTypeBsmArg(t);
            return;
        }
        if (arg instanceof Handle h) {
            collectHandle(h);
            return;
        }
        if (arg instanceof ConstantDynamic condy) {
            collectCondy(condy);
        }
    }

    private void collectTypeBsmArg(Type t) {
        switch (t.getSort()) {
            case Type.OBJECT -> sink.add(SymbolReference.type(t.getClassName()));
            case Type.ARRAY -> addTypeIfObject(t.getElementType());
            case Type.METHOD -> collectMethodTypeSignature(t);
            default -> { }
        }
    }

    private void collectMethodTypeSignature(Type methodType) {
        for (Type arg : methodType.getArgumentTypes()) {
            addTypeIfObject(arg);
        }
        addTypeIfObject(methodType.getReturnType());
    }

    private void collectCondy(ConstantDynamic condy) {
        collectTypesFromDescriptor(condy.getDescriptor());
        collectHandle(condy.getBootstrapMethod());
        for (int i = 0; i < condy.getBootstrapMethodArgumentCount(); i++) {
            collectBootstrapArgument(condy.getBootstrapMethodArgument(i));
        }
    }

    private void collectHandle(Handle handle) {
        if (handle == null) return;
        sink.add(SymbolReference.method(internalToFqcn(handle.getOwner()), handle.getName()));
        collectTypesFromDescriptor(handle.getDesc());
    }

    private boolean isReflectionSink(String owner, String name, String descriptor) {
        return switch (owner) {
            case "java/lang/Class" -> "forName".equals(name);
            case "java/lang/ClassLoader", "jdk/internal/loader/BuiltinClassLoader" ->
                    "loadClass".equals(name) || "findClass".equals(name);
            case "java/util/ServiceLoader" -> "load".equals(name) || "loadInstalled".equals(name);
            case "javax/naming/InitialContext" -> "lookup".equals(name) || "doLookup".equals(name);
            case "java/lang/invoke/MethodHandles$Lookup" ->
                    name.startsWith("find") || name.startsWith("bind") || name.startsWith("unreflect");
            case "java/lang/reflect/Proxy" -> "newProxyInstance".equals(name);
            default -> false;
        };
    }

    private boolean looksLikeFqcn(String s) {
        return s != null && s.length() < 512 && FQCN_PATTERN.matcher(s).matches();
    }

    private void collectTypesFromDescriptor(String descriptor) {
        if (descriptor == null) return;
        try {
            if (descriptor.startsWith("(")) {
                collectMethodDescriptor(descriptor);
            } else {
                addTypeIfObject(Type.getType(descriptor));
            }
        } catch (IllegalArgumentException ignored) {
        }
    }

    private void collectMethodDescriptor(String descriptor) {
        Type methodType = Type.getMethodType(descriptor);
        for (Type arg : methodType.getArgumentTypes()) {
            addTypeIfObject(arg);
        }
        addTypeIfObject(methodType.getReturnType());
    }

    private void addTypeIfObject(Type type) {
        if (type == null) return;
        if (type.getSort() == Type.OBJECT) {
            sink.add(SymbolReference.type(type.getClassName()));
        } else if (type.getSort() == Type.ARRAY) {
            addTypeIfObject(type.getElementType());
        }
    }

    static String internalToFqcn(String internal) {
        if (internal == null) return "";
        if (internal.startsWith("[")) {
            Type t = Type.getType(internal);
            Type el = t.getElementType();
            return el.getSort() == Type.OBJECT ? el.getClassName() : "";
        }
        return internal.replace('/', '.');
    }
}
