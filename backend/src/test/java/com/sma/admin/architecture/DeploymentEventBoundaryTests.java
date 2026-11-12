package com.sma.admin.architecture;

import static org.assertj.core.api.Assertions.*;
import java.lang.reflect.Modifier;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import com.tngtech.archunit.core.importer.*;

class DeploymentEventBoundaryTests {
    @Test void productionPersistentListenersKeepReviewedSynchronousTransactionContract(){
        var classes=new ClassFileImporter().withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("com.sma.admin","com.sma.business");
        var platform=new TreeSet<String>();
        for(var type:classes){
            for(var member:type.getMethods()){
                if(!member.isAnnotatedWith(ApplicationModuleListener.class)&&!member.isMetaAnnotatedWith(ApplicationModuleListener.class))continue;
                var method=member.reflect();var tx=AnnotatedElementUtils.findMergedAnnotation(method,Transactional.class);
                var async=AnnotatedElementUtils.findMergedAnnotation(method,org.springframework.scheduling.annotation.Async.class);
                assertThat(async).as(member.getFullName()).isNotNull();assertThat(async.value()).isEqualTo("deploymentEventExecutor");
                assertThat(method.getReturnType()).as(member.getFullName()).isEqualTo(void.class);
                assertThat(method.getParameterCount()).as(member.getFullName()).isEqualTo(1);
                assertThat(Modifier.isPublic(method.getModifiers())&&!Modifier.isFinal(method.getModifiers())&&!Modifier.isStatic(method.getModifiers()))
                        .as(member.getFullName()).isTrue();
                assertThat(Modifier.isFinal(method.getDeclaringClass().getModifiers())).isFalse();
                assertThat(tx).as(member.getFullName()).isNotNull();assertThat(tx.propagation()).isEqualTo(Propagation.REQUIRES_NEW);
                if(type.getPackageName().startsWith("com.sma.admin."))platform.add(type.getSimpleName()+"."+method.getName());
            }
        }
        assertThat(platform).containsExactlyInAnyOrder("OperationLogListener.on","LoginLogListener.on","UserSessionListener.on",
                "SocketEventListener.notice","SocketEventListener.todo","SocketEventListener.privateMessage","SocketEventListener.ended");
    }
}
