package com.sma.admin.audit;
import static org.assertj.core.api.Assertions.*;
import java.time.Duration;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.context.i18n.LocaleContextHolder;
import com.sma.admin.audit.internal.service.OperationLogService;
import com.sma.admin.common.*;
import com.sma.admin.common.security.*;
import com.sma.admin.support.*;

@IntegrationTest @AutoConfigureMockMvc
class OperationLogTests {
 @Autowired MockMvcTester mvc;@Autowired OperationLogService service;
 @Test void successfulAndRejectedMutationsAreRecordedWithoutRequestBodies(){
  var actor=new SmaPrincipal(101,"operation-tester",211,"admin",null,true,DataAccess.unrestricted(),Set.of("iam:post:create"),"UTC");
  Users.as(actor,()->{
   Users.as(actor,()->{
    assertThat(mvc.post().uri("/api/iam/posts").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Sales\",\"code\":\"sales\",\"sort\":0,\"enabled\":true}")).hasStatusOk();
    return null;
   });
   Users.as(actor,()->{
    assertThat(mvc.post().uri("/api/iam/posts").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"SecretMustNotBeLogged\",\"code\":\"sales\",\"sort\":0,\"enabled\":true}"))
     .hasStatusOk().bodyJson().hasPathSatisfying("$.error",value->value.assertThat().isEqualTo("iam.error.duplicate-code"));
    return null;
   });
   org.awaitility.Awaitility.await().pollInSameThread().atMost(Duration.ofSeconds(8)).untilAsserted(()->{
    var page=service.page(new PageQuery(1,20,null,null),"operation-tester","iam:post:create",null,null,null);assertThat(page.total()).isEqualTo(2);
    assertThat(page.items()).extracting(OperationLogService.View::resultCode).containsExactlyInAnyOrder(0,400);
    assertThat(page.items()).filteredOn(row->row.resultCode()==0).allSatisfy(row->{assertThat(row.objectId()).isNotBlank();assertThat(row.clientPath()).startsWith("/iam/posts?focus=");});
    assertThat(page.items()).allSatisfy(row->assertThat(row.toString()).doesNotContain("SecretMustNotBeLogged"));
   });
   try{LocaleContextHolder.setLocale(Locale.US);assertThat(service.page(new PageQuery(1,20,null,null),"operation-tester",null,true,null,null).items().getFirst().message()).startsWith("Create post");}
   finally{LocaleContextHolder.resetLocaleContext();}
   return null;
  });
 }
}
