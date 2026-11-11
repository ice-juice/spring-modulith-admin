package com.sma.admin.identity.internal.service;

import static org.assertj.core.api.Assertions.*;
import java.util.List;
import com.sma.admin.common.security.ActorRef;
import com.sma.admin.identity.*;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

class SubjectAccessPoliciesTests {
    @Test void missingAndDuplicatePoliciesFailClosedAndContactNeedsItsOwnDecision(){
        var actor=new ActorRef(ActorRef.Kind.SYSTEM_JOB,1,"notice:1",null,null);var request=new SubjectReadRequest("shop",2,3,"notice");
        assertThatThrownBy(()->new SubjectAccessPolicies(List.of()).require(actor,request,SubjectAccessPolicy.Content.BASIC)).isInstanceOf(AccessDeniedException.class);
        SubjectAccessPolicy basic=new SubjectAccessPolicy(){public String application(){return "shop";}
            public boolean canRead(ActorRef original,SubjectReadRequest value,Content content){return original.equals(actor) && value.equals(request) && content==Content.BASIC;}};
        assertThatThrownBy(()->new SubjectAccessPolicies(List.of(basic,basic))).isInstanceOf(IllegalStateException.class);
        var registry=new SubjectAccessPolicies(List.of(basic));registry.require(actor,request,SubjectAccessPolicy.Content.BASIC);
        assertThatThrownBy(()->registry.require(actor,request,SubjectAccessPolicy.Content.VERIFIED_EMAIL)).isInstanceOf(AccessDeniedException.class);
    }
}
