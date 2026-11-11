package com.sma.admin.file.internal.inspection;

import static org.assertj.core.api.Assertions.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Actual JVM process faults. These tests do not assert container/filesystem/network sandboxing. */
class BoundedInspectionProcessTests {
    @TempDir Path directory;
    public static class Probe {
        public static void main(String[] args)throws Exception{
            switch(args[0]){
                case "environment"->System.out.print(Runtime.getRuntime().maxMemory()+":"+System.getenv("JAVA_TOOL_OPTIONS"));
                case "stdout"->System.out.write(new byte[20000]);
                case "stderr"->System.err.write(new byte[20000]);
                case "memory"->{byte[] oversized=new byte[128*1024*1024];System.out.print(oversized.length);}
                case "wait"->{Path path=Path.of(args[1]);Path temporary=path.resolveSibling("pid.tmp");Files.writeString(temporary,Long.toString(ProcessHandle.current().pid()));Files.move(temporary,path);Thread.sleep(30000);}
                default->throw new IllegalArgumentException();
            }
        }
    }
    private List<String> command(String action){return List.of(Path.of(System.getProperty("java.home"),"bin",System.getProperty("os.name").startsWith("Windows")?"java.exe":"java").toString(),
            "-Xmx32m","-XX:MaxDirectMemorySize=8m","-XX:ActiveProcessorCount=1","-cp",System.getProperty("java.class.path"),Probe.class.getName(),action,directory.resolve("pid").toString());}
    @Test void realProcessHasExplicitHeapAndDoesNotInheritJavaOptions()throws Exception{
        try(var runner=new BoundedInspectionProcess(1)){
            for(int i=0;i<3;i++){
                var result=runner.execute(command("environment"),directory,new byte[0],Duration.ofSeconds(10));assertThat(result.exitCode()).isZero();
                String output=new String(result.output(),StandardCharsets.UTF_8);String[] values=output.split(":");assertThat(Long.parseLong(values[0])).isLessThanOrEqualTo(32L*1024*1024);assertThat(values[1]).isEqualTo("null");
            }
            var memory=runner.execute(command("memory"),directory,new byte[0],Duration.ofSeconds(10));assertThat(memory.exitCode()).isNotZero();
        }
    }
    @Test void oversizedStdoutOrStderrCannotBecomeAValidReport(){
        try(var runner=new BoundedInspectionProcess(1)){
            for(String action:List.of("stdout","stderr"))assertThatThrownBy(()->runner.execute(command(action),directory,new byte[0],Duration.ofSeconds(10)))
                    .isInstanceOfSatisfying(BoundedInspectionProcess.Failure.class,e->assertThat(e.reason()).isEqualTo(BoundedInspectionProcess.Reason.OUTPUT_LIMIT));
        }
    }
    @Test void timeoutKillsActualChildAndConcurrencyNeverStartsASecondOne()throws Exception{
        try(var runner=new BoundedInspectionProcess(1);var thread=Executors.newSingleThreadExecutor()){
            var active=thread.submit(()->runner.execute(command("wait"),directory,new byte[0],Duration.ofSeconds(3)));
            long stop=System.nanoTime()+TimeUnit.SECONDS.toNanos(2);while(!Files.exists(directory.resolve("pid")) && System.nanoTime()<stop)Thread.sleep(10);
            assertThat(Files.exists(directory.resolve("pid"))).isTrue();long pid=Long.parseLong(Files.readString(directory.resolve("pid")));
            assertThatThrownBy(()->runner.execute(command("environment"),directory,new byte[0],Duration.ofSeconds(10)))
                    .isInstanceOfSatisfying(BoundedInspectionProcess.Failure.class,e->assertThat(e.reason()).isEqualTo(BoundedInspectionProcess.Reason.BUSY));
            assertThatThrownBy(()->active.get(8,TimeUnit.SECONDS)).isInstanceOf(ExecutionException.class).hasCauseInstanceOf(BoundedInspectionProcess.Failure.class);
            assertThat(ProcessHandle.of(pid).map(ProcessHandle::isAlive).orElse(false)).isFalse();
            assertThat(runner.execute(command("environment"),directory,new byte[0],Duration.ofSeconds(10)).exitCode()).isZero();
        }
    }
}
