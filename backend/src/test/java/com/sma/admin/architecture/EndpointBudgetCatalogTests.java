package com.sma.admin.architecture;

import static org.assertj.core.api.Assertions.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.Semaphore;
import com.sma.admin.common.internal.security.*;
import com.sma.admin.common.request.GovernedApi;
import com.sma.admin.file.internal.service.*;
import com.sma.admin.file.internal.web.UploadTaskRequestFilter;
import com.sma.admin.identity.internal.service.*;
import com.sma.admin.tenant.internal.service.BrandWorkBudget;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class EndpointBudgetCatalogTests {
    @Test void compiledStorageOwnersAndDefaultNodeCapacityMatchTheCatalog()throws Exception{
        EndpointBudgetCatalog.validateCompiled();
        assertThat(EndpointBudgetCatalog.byId("identity.http").nodeDefault()).isEqualTo(new IdentityHttpProperties(null,null).maximumConcurrent().longValue());
        assertThat(EndpointBudgetCatalog.byId("common.admission").nodeDefault()).isEqualTo(new RequestGovernanceProperties(null,null,null,null,null).maxConcurrent().longValue());
        var browser=new BrowserRequestProperties(null);
        assertThat(EndpointBudgetCatalog.byId("common.browser-json").nodeDefault()).isEqualTo(browser.maximumConcurrentJson().longValue());
        assertThat(EndpointBudgetCatalog.byId("common.anonymous-json").nodeDefault()).isEqualTo(browser.maximumConcurrentAnonymousJson().longValue());
        assertThat(EndpointBudgetCatalog.byId("identity.password").nodeDefault()).isEqualTo(new IdentityPasswordWork.Settings(null,null).maximumConcurrent().longValue());
        assertThat(EndpointBudgetCatalog.byId("file.local-transfer").nodeDefault()).isEqualTo(new LocalTransferProperties(null,null,null).maximumConcurrent().longValue());
        var settings=new UploadTaskProperties(null,null,null,null,null,null,null,null,null,null);
        var upload=new UploadTaskRequestFilter((request,response,error)->{},settings);
        assertThat(EndpointBudgetCatalog.byId("file.http").nodeDefault()).isEqualTo((long)((Semaphore)ReflectionTestUtils.getField(upload,"slots")).availablePermits());
        assertThat(EndpointBudgetCatalog.byId("tenant.brand-work").nodeDefault()).isEqualTo((long)((Semaphore)ReflectionTestUtils.getField(new BrandWorkBudget(),"permits")).availablePermits());
    }
    @Test void sharedCountersAreNotMultipliedAndNestedNodeGatesAreNotAdded(){
        for(var budget:EndpointBudgetCatalog.ALL){
            for(int nodes:List.of(1,2,5))assertThat(budget.aggregate(17,nodes)).isEqualTo(budget.scope()==EndpointBudgetCatalog.Scope.shared?17:17L*nodes);
            assertThatThrownBy(()->budget.aggregate(1,0)).isInstanceOf(IllegalArgumentException.class);
        }
        assertThat(EndpointBudgetCatalog.byId("identity.http").aggregate(64,3)).isEqualTo(192);
        // Same request traverses both gates: the smaller available gate bounds concurrent upload handling.
        assertThat(Math.min(EndpointBudgetCatalog.byId("identity.http").aggregate(64,3),EndpointBudgetCatalog.byId("file.http").aggregate(16,3))).isEqualTo(48);
        assertThat(EndpointBudgetCatalog.byId("common.requests").aggregate(1500,3)).isEqualTo(1500);
    }
    @Test void newScopeFieldsCannotBeEmptyAndSharedCannotClaimNodeStorage(){
        var valid=EndpointBudgetCatalog.byId("auth.security");
        assertThatThrownBy(()->new EndpointBudgetCatalog.Budget(valid.id(),null,valid.module(),valid.storage(),valid.implementation(),valid.tables(),valid.key(),valid.limits(),valid.lifecycle(),null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->new EndpointBudgetCatalog.Budget(valid.id(),valid.scope(),valid.module(),"process-memory",valid.implementation(),valid.tables(),valid.key(),valid.limits(),valid.lifecycle(),null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->new EndpointBudgetCatalog.Budget(valid.id(),valid.scope(),valid.module(),valid.storage(),valid.implementation(),Map.of(),valid.key(),valid.limits(),valid.lifecycle(),null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->EndpointBudgetCatalog.contracts(Set.of("missing.registration"))).isInstanceOf(IllegalArgumentException.class);
    }
    @GovernedApi(group="test.scope") static class Governed {}
    @Test void endpointBindingsDistinguishReadsAnonymousApplicationAndBrandWork(){
        var governed=Governed.class.getAnnotation(GovernedApi.class);
        assertThat(EndpointBudgetCatalog.bindings("/api/iam/users","GET","UserController#page",governed,"RequiresPermission")).containsExactly("common.requests");
        assertThat(EndpointBudgetCatalog.bindings("/api/auth/login","POST","AuthController#login",governed,"PermitAnonymous"))
                .containsExactly("auth.security","common.anonymous-json","common.browser-json");
        assertThat(EndpointBudgetCatalog.bindings("/app/file/upload-tasks/{id}/parts/{number}","PUT","ApplicationUploadTaskController#putPart",null,"PermitApplicationSelf"))
                .containsExactly("file.http","file.local-transfer","file.tasks","identity.http");
        assertThat(EndpointBudgetCatalog.bindings("/api/tenant/brands/publications","POST","BrandController#publish",governed,"RequiresPermission"))
                .contains("tenant.brand-work","common.requests","common.admission");
        assertThat(EndpointBudgetCatalog.bindings("/api/tenant/brand-images/{revisionId}/{slot}","GET","BrandEntryController#image",null,"PermitAnonymous")).isEmpty();
        assertThat(EndpointBudgetCatalog.bindings("/open/token","POST","OpenController#token",null,"PermitAnonymous")).containsExactly("openapi.login");
        assertThat(EndpointBudgetCatalog.bindings("/api/auth/security-passwords","POST","SecurityPasswordController#set",governed,"PermitLoggedIn")).contains("auth.security");
    }
    @Test void deploymentCapacityTableIsGeneratedFromTheSameReviewedScopes()throws Exception{
        Path operations=Path.of("../docs/security-defense-operations.md");
        String text=Files.readString(operations);String begin="<!-- budget-capacity:start -->",end="<!-- budget-capacity:end -->";
        int start=text.indexOf(begin),finish=text.indexOf(end);assertThat(start).isNotNegative();assertThat(finish).isGreaterThan(start);
        String expected=capacityTable();
        if(Boolean.getBoolean("sma.security.review-policy-baseline")){
            text=text.substring(0,start+begin.length())+"\n"+expected+text.substring(finish);Files.writeString(operations,text);
        }
        String actual=text.substring(text.indexOf(begin)+begin.length(),text.indexOf(end)).strip();assertThat(actual).isEqualTo(expected.strip());
    }
    static String capacityTable(){
        var table=new StringBuilder("| 预算ID | scope | 所属模块/存储 | 单实例默认 | 3实例同配置总上限 | N实例 |\n| --- | --- | --- | --- | --- | --- |\n");
        EndpointBudgetCatalog.ALL.stream().sorted(Comparator.comparing(EndpointBudgetCatalog.Budget::id)).forEach(b->{
            String one=b.nodeDefault()==null?"L（同键/同窗口）":b.nodeDefault().toString();
            String three=b.nodeDefault()==null?"L（仍共享）":Long.toString(b.aggregate(b.nodeDefault(),3));
            table.append("| ").append(b.id()).append(" | ").append(b.scope()).append(" | ").append(b.module()).append("/").append(b.storage()).append(" | ")
                    .append(one).append(" | ").append(three).append(" | ").append(b.scale()).append(" |\n");
        });return table.toString();
    }
}
