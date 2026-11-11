package com.sma.extension.runtime;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import com.sma.extension.api.BusinessPlugin;
import com.sma.extension.protocol.ArchiveSnapshot;

/** Class namespace isolation only, not an adversarial-code sandbox. No URLs, root-container or mutable JAR handles. */
final class SnapshotClassLoader extends ClassLoader {
    static { registerAsParallelCapable(); }
    private final ArchiveSnapshot snapshot;
    SnapshotClassLoader(ArchiveSnapshot snapshot) { super(ClassLoader.getPlatformClassLoader()); this.snapshot = snapshot; }
    @Override protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        synchronized (getClassLoadingLock(name)) {
            Class<?> value = findLoadedClass(name);
            if (value == null) {
                if (name.startsWith("com.sma.extension.api.")) value = BusinessPlugin.class.getClassLoader().loadClass(name);
                else if (name.startsWith("com.sma.")) throw new ClassNotFoundException("Host implementation is not a plugin dependency");
                else {
                    byte[] bytes = snapshot.resource(name.replace('.', '/') + ".class");
                    if (bytes != null) value = defineClass(name, bytes, 0, bytes.length);
                    else value = getParent().loadClass(name);
                }
            }
            if (resolve) resolveClass(value);
            return value;
        }
    }
    @Override public InputStream getResourceAsStream(String name) {
        byte[] bytes = snapshot.resource(name);
        return bytes == null ? null : new ByteArrayInputStream(bytes);
    }
}
