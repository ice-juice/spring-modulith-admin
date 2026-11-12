package com.sma.admin.architecture;

import static org.assertj.core.api.Assertions.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.scheduling.annotation.*;
import com.tngtech.archunit.core.domain.*;
import com.tngtech.archunit.core.importer.*;

/** Discovery/review gate, not proof that a registered activity has already passed drain acceptance. */
class DeploymentActivityInventoryTests {
    @Test void platformActivitySourcesMatchExplicitlyReviewedInventory()throws Exception{
        var classes=new ClassFileImporter().withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("com.sma.admin","com.sma.business");
        var found=discover(classes);
        var lines=found.entrySet().stream().map(entry->entry.getKey()+" | count="+entry.getValue()).toList();
        Path output=Path.of(System.getProperty("sma.verify.directory","target"),"deployment-activity-actual.txt");
        Files.createDirectories(output.getParent());Files.write(output,lines,StandardCharsets.UTF_8);
        List<String> expected;
        try(var input=getClass().getResourceAsStream("/deployment-activity-inventory.txt")){
            assertThat(input).as("Reviewed activity inventory is required; never auto-approve newly discovered execution sources").isNotNull();
            expected=new String(input.readAllBytes(),StandardCharsets.UTF_8).lines().filter(line->!line.isBlank()&&!line.startsWith("#")).toList();
        }
        assertThat(lines).as("Review new/removed execution sources and their real lifetime; actual inventory: %s",output)
                .containsExactlyElementsOf(expected);
    }

    static SortedMap<String,Integer> discover(JavaClasses classes){
        var found=new TreeMap<String,Integer>();
        for(var type:classes){
            // A subclass may inherit all methods and introduce Async only on the class itself.
            var typeAsync=AnnotatedElementUtils.findMergedAnnotation(type.reflect(),Async.class);
            if(typeAsync!=null)add(found,"ASYNC_TYPE "+type.getName()+" executor="+typeAsync.value());
            for(var method:type.getMethods()){
                for(var scheduled:AnnotatedElementUtils.getMergedRepeatableAnnotations(method.reflect(),Scheduled.class,Schedules.class))
                    add(found,"SCHEDULED "+method.getFullName()+" delay="+scheduled.fixedDelay()+"/"+scheduled.fixedDelayString()
                            +" rate="+scheduled.fixedRate()+"/"+scheduled.fixedRateString()+" initial="+scheduled.initialDelay()+"/"+scheduled.initialDelayString()
                            +" cron="+scheduled.cron()+" zone="+scheduled.zone()+" scheduler="+scheduled.scheduler()+" unit="+scheduled.timeUnit());
                var async=AnnotatedElementUtils.findMergedAnnotation(method.reflect(),Async.class);
                if(async==null)async=AnnotatedElementUtils.findMergedAnnotation(type.reflect(),Async.class);
                if(async!=null)add(found,"ASYNC "+method.getFullName()+" executor="+async.value());
                String returned=method.getRawReturnType().getName();
                if(Set.of("java.util.concurrent.Callable","java.util.concurrent.CompletionStage","java.util.concurrent.CompletableFuture",
                        "org.springframework.web.context.request.async.DeferredResult","org.springframework.web.context.request.async.WebAsyncTask",
                        "org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody","org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter",
                        "org.springframework.web.servlet.mvc.method.annotation.SseEmitter").contains(returned))
                    add(found,"ASYNC_RETURN "+method.getFullName()+" -> "+returned);
            }
            for(var call:type.getMethodCallsFromSelf()){
                String owner=call.getTargetOwner().getName(),name=call.getName();
                String category=category(owner,name);
                if(category==null&&call.getTargetOwner().isAssignableTo(java.util.concurrent.Executor.class)
                        &&Set.of("execute","submit","invokeAll","invokeAny","schedule","scheduleAtFixedRate","scheduleWithFixedDelay").contains(name))category="SUBMIT";
                if(category!=null)add(found,category+" "+call.getOrigin().getFullName()+" -> "+call.getTarget().getFullName());
            }
            for(var call:type.getConstructorCallsFromSelf()){
                String owner=call.getTargetOwner().getName();
                if(owner.equals("java.lang.Thread")||owner.equals("java.util.Timer")
                        ||call.getTargetOwner().isAssignableTo(java.util.concurrent.Executor.class)
                        ||owner.startsWith("org.springframework.scheduling.concurrent.")
                        ||owner.equals("com.yomahub.liteflow.core.FlowExecutor"))
                    add(found,"EXECUTOR "+call.getOrigin().getFullName()+" -> "+call.getTarget().getFullName());
                if(owner.equals("com.zaxxer.hikari.HikariDataSource")||owner.equals("org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory"))
                    add(found,"RESOURCE "+call.getOrigin().getFullName()+" -> "+call.getTarget().getFullName());
            }
        }
        return found;
    }
    private static void add(Map<String,Integer> found,String key){found.merge(key,1,Integer::sum);}
    static String category(String owner,String name){
        if(owner.equals("com.sma.admin.common.deployment.DeploymentTaskTracker")&&name.equals("submit"))return "SUBMIT";
        if(owner.equals("java.lang.ProcessBuilder")&&Set.of("start","startPipeline").contains(name)||owner.equals("java.lang.Runtime")&&name.equals("exec"))return "PROCESS";
        if(owner.equals("java.lang.Runtime")&&name.equals("addShutdownHook"))return "SHUTDOWN_HOOK";
        if(owner.equals("java.lang.Thread")&&Set.of("start","startVirtualThread").contains(name)
                ||owner.startsWith("java.lang.Thread$Builder")&&name.equals("start"))return "THREAD";
        if(owner.equals("java.util.Timer")&&name.startsWith("schedule"))return "SUBMIT";
        if((owner.startsWith("java.util.concurrent.")||owner.startsWith("org.springframework.core.task.")||owner.startsWith("org.springframework.scheduling."))
                &&(Set.of("execute","submit","invokeAll","invokeAny","fork","commonPool","schedule","scheduleAtFixedRate","scheduleWithFixedDelay").contains(name)
                    ||name.endsWith("Async")||owner.equals("java.util.concurrent.Executors")&&name.startsWith("new")))return "SUBMIT";
        if(owner.startsWith("java.util.stream.")&&Set.of("parallel","parallelStream").contains(name)
                ||owner.startsWith("java.util.")&&name.equals("parallelStream"))return "SUBMIT";
        if(owner.equals("java.net.http.HttpClient")&&Set.of("send","sendAsync","newWebSocketBuilder").contains(name))return "HTTP";
        if(owner.equals("com.yomahub.liteflow.core.FlowExecutor")&&name.startsWith("execute"))return "LIBRARY";
        if(owner.equals("com.github.benmanes.caffeine.cache.Caffeine")&&Set.of("build","buildAsync").contains(name)
                ||owner.equals("java.net.http.HttpClient$Builder")&&name.equals("build"))return "RESOURCE";
        if(owner.startsWith("reactor.core.publisher.")&&Set.of("subscribe","publishOn","subscribeOn","parallel").contains(name))return "SUBMIT";
        if(owner.startsWith("jakarta.servlet.")&&name.equals("startAsync"))return "SERVLET_ASYNC";
        return null;
    }

    @Test void realBytecodeDiscoveryCatchesComposedAnnotationsSubclassExecutorsAndImplicitAsync(){
        // Import fixture bytecode, never run its methods or start its thread/process.
        var found=discover(new ClassFileImporter().importClasses(ActivityFixture.class,ActivityFixture.OwnExecutor.class,
                ActivityFixture.Base.class,ActivityFixture.Inherited.class));
        var keys=found.keySet();
        assertThat(keys.stream().filter(key->key.startsWith("SCHEDULED "))).hasSize(2);
        assertThat(keys).anyMatch(key->key.startsWith("ASYNC ")&&key.contains(".composed() executor=fixture"));
        assertThat(keys).anyMatch(key->key.startsWith("ASYNC_TYPE ")&&key.contains("Inherited executor=inherited"));
        assertThat(keys).anyMatch(key->key.startsWith("SUBMIT ")&&key.contains("OwnExecutor.execute"));
        assertThat(keys).anyMatch(key->key.startsWith("SUBMIT ")&&key.contains("thenRunAsync"));
        assertThat(keys).anyMatch(key->key.startsWith("SUBMIT ")&&key.contains("parallelStream"));
        assertThat(keys).anyMatch(key->key.startsWith("THREAD ")&&key.contains("OfVirtual.start"));
        assertThat(keys).anyMatch(key->key.startsWith("PROCESS ")&&key.contains("ProcessBuilder.start"));
        assertThat(keys).anyMatch(key->key.startsWith("HTTP ")&&key.contains("sendAsync"));
        assertThat(keys).anyMatch(key->key.startsWith("SERVLET_ASYNC "));
        assertThat(keys).noneMatch(key->key.contains("CompletableFuture.complete("));
    }
    @java.lang.annotation.Retention(java.lang.annotation.RetentionPolicy.RUNTIME)
    @java.lang.annotation.Target(java.lang.annotation.ElementType.METHOD)
    @Async("fixture") @interface FixtureAsync {}
    static class ActivityFixture {
        static class OwnExecutor implements java.util.concurrent.Executor {public void execute(Runnable action){}}
        static class Base {public void inherited(){}}
        @Async("inherited") static class Inherited extends Base {}
        @Scheduled(fixedDelay=1) @Scheduled(fixedDelay=2) public void repeat(){}
        @FixtureAsync public void composed(){}
        void alternatives(OwnExecutor executor,java.util.concurrent.CompletableFuture<Void> future,List<String> input,
                          java.net.http.HttpClient http,java.net.http.HttpRequest request,jakarta.servlet.ServletRequest servlet)throws Exception{
            executor.execute(()->{});future.thenRunAsync(()->{});future.complete(null);input.parallelStream().count();
            Thread.ofVirtual().start(()->{});new ProcessBuilder("never-run").start();
            http.sendAsync(request,java.net.http.HttpResponse.BodyHandlers.discarding());servlet.startAsync();
        }
    }
}
