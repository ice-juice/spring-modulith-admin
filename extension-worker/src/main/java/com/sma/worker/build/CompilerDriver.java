package com.sma.worker.build;

import java.io.*;
import java.nio.file.*;
import java.util.Arrays;
import java.util.zip.*;
import javax.tools.ToolProvider;

/** JDK-only trusted helper. Executed solely inside the pinned compiler container. */
public final class CompilerDriver extends FilterOutputStream {
    private final long limit;
    private long written;
    private CompilerDriver(OutputStream output,long limit){super(output);this.limit=limit;}
    @Override public void write(int value)throws IOException{if(written>=limit)throw new IOException("compiler-output-limit");out.write(value);written++;}
    @Override public void write(byte[] value,int offset,int count)throws IOException{if(count>limit-written)throw new IOException("compiler-output-limit");out.write(value,offset,count);written+=count;}
    public static void main(String[] args)throws Exception {
        long expanded=Long.parseLong(args[0]),compressed=Long.parseLong(args[1]);int entries=Integer.parseInt(args[2]);
        if(expanded<=0 || expanded>2L*1024*1024*1024 || compressed<=0 || compressed>1024L*1024*1024 || entries<=0 || entries>100000)throw new IOException("invalid-build-limits");
        var compiler=ToolProvider.getSystemJavaCompiler();if(compiler==null)throw new IOException("jdk-compiler-required");
        int result=compiler.run(null,System.err,System.err,Arrays.copyOfRange(args,3,args.length));
        if(result!=0)throw new IOException("compilation-failed");
        export(Path.of("/build/classes"),System.out,expanded,compressed,entries);
    }
    public static void export(Path classes,OutputStream output,long expandedLimit,long archiveLimit,int entries)throws IOException {
        try(var walk=Files.walk(classes);var zip=new ZipOutputStream(new CompilerDriver(output,archiveLimit))) {
            // Bound enumeration as well as bytes before sorting. No container-controlled host path is written.
            var paths=walk.filter(p -> !p.equals(classes)).limit(2L*entries+1).sorted().toList();
            if(paths.size()>2L*entries)throw new IOException("compiler-entry-limit");
            long expanded=0;int count=0;
            for(Path file:paths) {
                if(Files.isSymbolicLink(file))throw new IOException("linked-compiler-output");
                if(Files.isDirectory(file,LinkOption.NOFOLLOW_LINKS))continue;
                if(!Files.isRegularFile(file,LinkOption.NOFOLLOW_LINKS) || !file.toRealPath().startsWith(classes.toRealPath()))throw new IOException("unsafe-compiler-output");
                String name=classes.relativize(file).toString().replace('\\','/');
                long size=Files.size(file);if(!name.endsWith(".class") || size>expandedLimit-expanded || ++count>entries)throw new IOException("compiler-output-limit");
                expanded+=size;var entry=new ZipEntry(name);entry.setTimeLocal(java.time.LocalDateTime.of(1980,1,1,0,0));zip.putNextEntry(entry);Files.copy(file,zip);zip.closeEntry();
            }
        }
    }
}
