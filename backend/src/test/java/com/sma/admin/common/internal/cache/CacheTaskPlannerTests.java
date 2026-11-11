package com.sma.admin.common.internal.cache;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import com.sma.admin.common.cache.*;
import com.sma.admin.common.internal.service.CacheRevisionStore;
import com.sma.admin.common.security.*;
import com.sma.admin.support.Users;

class CacheTaskPlannerTests {
    private final CacheRevisionStore revisions=mock(CacheRevisionStore.class);
    private final CacheProperties properties=new CacheProperties(null,null,null,null,null,null,null);
    private final List<CacheDescriptor> catalog=List.of(
            descriptor("shop.items","catalog","display",CacheDescriptor.Kind.DATA,"shop:cache:manage",Set.of(CacheDescriptor.Operation.CLEAR)),
            descriptor("shop.prices","catalog","pricing",CacheDescriptor.Kind.DATA,"shop:price:manage",Set.of(CacheDescriptor.Operation.CLEAR)),
            descriptor("shop.readonly","catalog","display",CacheDescriptor.Kind.DATA,"shop:cache:manage",Set.of()),
            descriptor("shop.sessions","security","session",CacheDescriptor.Kind.PROTECTED,"shop:cache:manage",Set.of()),
            descriptor("shop.compiler","runtime","compile",CacheDescriptor.Kind.RUNTIME,"shop:cache:manage",Set.of(CacheDescriptor.Operation.CLEAR)));
    private final CacheTaskPlanner planner=planner(catalog);
    @Test void previewFreezesOnlyPermittedTargetsAndExplainsEveryExplicitExclusion(){
        when(revisions.state("shop.items")).thenReturn(CacheRevisionStore.State.initial());
        Users.as(actor("monitor:cache:view","monitor:cache:clear","shop:cache:manage"),()->{
            var result=planner.preview(CacheTasks.Action.CLEAR,new CacheTasks.Selection(Set.of("shop.items","shop.prices","shop.readonly","shop.sessions","shop.compiler","shop.missing"),null,null,null));
            assertThat(result.targets()).extracting(CacheTasks.Target::region).containsExactly("shop.items");
            assertThat(result.skipped()).containsExactly(
                    new CacheTasks.Skipped("shop.compiler",CacheTasks.SkipReason.ADAPTER_REQUIRED),
                    new CacheTasks.Skipped("shop.missing",CacheTasks.SkipReason.UNKNOWN_REGION),
                    new CacheTasks.Skipped("shop.prices",CacheTasks.SkipReason.FORBIDDEN),
                    new CacheTasks.Skipped("shop.readonly",CacheTasks.SkipReason.UNSUPPORTED),
                    new CacheTasks.Skipped("shop.sessions",CacheTasks.SkipReason.PROTECTED));
            assertThat(result.targets().getFirst().registrationHash()).matches("[0-9a-f]{64}");
            assertThatThrownBy(()->result.targets().clear()).isInstanceOf(UnsupportedOperationException.class);return null;
        });verify(revisions).state("shop.items");verifyNoMoreInteractions(revisions);
    }
    @Test void groupAndScenarioIntersectAndDoNotGrantPermissions(){
        when(revisions.state("shop.items")).thenReturn(CacheRevisionStore.State.initial());
        Users.as(actor("monitor:cache:view","monitor:cache:clear","shop:cache:manage"),()->{
            var result=planner.preview(CacheTasks.Action.CLEAR,new CacheTasks.Selection(Set.of(),"shop","catalog","display"));
            assertThat(result.targets()).extracting(CacheTasks.Target::region).containsExactly("shop.items");
            assertThat(result.skipped()).containsExactly(new CacheTasks.Skipped("shop.readonly",CacheTasks.SkipReason.UNSUPPORTED));
            var explicit=planner.preview(CacheTasks.Action.CLEAR,new CacheTasks.Selection(Set.of("shop.items","shop.prices"),"shop","catalog","display"));
            assertThat(explicit.skipped()).containsExactly(new CacheTasks.Skipped("shop.prices",CacheTasks.SkipReason.FILTERED_OUT));return null;
        });
        Users.as(actor("monitor:cache:view","shop:cache:manage"),()->{
            assertThatThrownBy(()->planner.preview(CacheTasks.Action.CLEAR,new CacheTasks.Selection(Set.of(),null,null,null))).isInstanceOf(AccessDeniedException.class);return null;
        });
    }
    @Test void metadataAndControlChangesInvalidateFirstSubmissionButDataRevisionDoesNot(){
        when(revisions.state("shop.items")).thenReturn(CacheRevisionStore.State.initial());
        when(revisions.lockedState("shop.items")).thenReturn(new CacheRevisionStore.State(99,0,0,false,null));
        Users.as(actor("monitor:cache:view","monitor:cache:clear","shop:cache:manage"),()->{
            var targets=planner.preview(CacheTasks.Action.CLEAR,new CacheTasks.Selection(Set.of("shop.items"),null,null,null)).targets();
            planner.validateFrozen(targets);
            var changed=planner(List.of(descriptor("shop.items","new-group","display",CacheDescriptor.Kind.DATA,"shop:cache:manage",Set.of(CacheDescriptor.Operation.CLEAR))));
            assertThatThrownBy(()->changed.validateFrozen(targets)).isInstanceOf(CacheTaskRejected.class);
            var newNamespace=new CacheTaskPlanner(new CacheRegistry(List.of(()->catalog)),new CacheProperties(null,"new-deployment",null,null,null,null,null),revisions);
            assertThatThrownBy(()->newNamespace.validateFrozen(targets)).isInstanceOf(CacheTaskRejected.class);
            assertThat(targets.toString()).doesNotContain("new-deployment","namespace");
            when(revisions.lockedState("shop.items")).thenReturn(new CacheRevisionStore.State(100,1,0,true,null));
            assertThatThrownBy(()->planner.validateFrozen(targets)).isInstanceOf(CacheTaskRejected.class);
            // Replaying an accepted task rechecks authorization, not the old preview's version.
            planner.authorize(CacheTasks.Action.CLEAR,targets);return null;
        });
    }
    @Test void filteringOccursBeforeTheBoundAndLargeSelectionsNeverSilentlyTruncate(){
        var descriptors=new ArrayList<CacheDescriptor>();
        for(int i=0;i<129;i++)descriptors.add(descriptor("shop.item-"+i,i==0?"selected":"other","display",CacheDescriptor.Kind.DATA,"shop:cache:manage",Set.of(CacheDescriptor.Operation.CLEAR)));
        when(revisions.state("shop.item-0")).thenReturn(CacheRevisionStore.State.initial());
        var many=planner(descriptors);
        Users.as(actor("monitor:cache:view","monitor:cache:clear","shop:cache:manage"),()->{
            assertThat(many.preview(CacheTasks.Action.CLEAR,new CacheTasks.Selection(Set.of(),null,"selected",null)).targets()).hasSize(1);
            assertThatThrownBy(()->many.preview(CacheTasks.Action.CLEAR,new CacheTasks.Selection(Set.of(),null,null,null))).isInstanceOf(CacheTaskRejected.class);return null;
        });
    }
    @Test void nativePreviewBindsVersionAndDisabledDeploymentCannotAcceptItButStillAllowsAuthorizedReplay(){
        var resources=mock(CacheResourceRegistry.class);
        var identity=new CacheTasks.ResourceTarget(UUID.randomUUID().toString(),"node-a",1);
        when(resources.enabled()).thenReturn(true);
        when(resources.operationVersion("shop.compiler")).thenReturn(1);
        when(resources.target("shop.compiler")).thenReturn(identity);
        when(resources.capability("shop.compiler",CacheDescriptor.Operation.CLEAR)).thenReturn(new CacheResources.Capability(true,"READY"));
        var nativePlanner=new CacheTaskPlanner(new CacheRegistry(List.of(()->catalog)),properties,revisions,null,resources);
        Users.as(actor("monitor:cache:view","monitor:cache:clear","shop:cache:manage"),()->{
            var targets=nativePlanner.preview(CacheTasks.Action.CLEAR,new CacheTasks.Selection(Set.of("shop.compiler"),null,null,null)).targets();
            assertThat(targets.getFirst().resource()).isEqualTo(identity);nativePlanner.validateFrozen(targets);
            when(resources.operationVersion("shop.compiler")).thenReturn(2);
            assertThatThrownBy(()->nativePlanner.validateFrozen(targets)).isInstanceOf(CacheTaskRejected.class);
            when(resources.operationVersion("shop.compiler")).thenReturn(1);when(resources.enabled()).thenReturn(false);
            assertThatThrownBy(()->nativePlanner.validateFrozen(targets)).isInstanceOf(CacheTaskRejected.class);
            nativePlanner.authorize(CacheTasks.Action.CLEAR,targets);nativePlanner.authorizeCancellation(CacheTasks.Action.CLEAR,targets);
            return null;
        });verifyNoInteractions(revisions);
    }
    private CacheTaskPlanner planner(List<CacheDescriptor> rows){return new CacheTaskPlanner(new CacheRegistry(List.of(()->rows)),properties,revisions);}
    private CacheDescriptor descriptor(String id,String group,String scenario,CacheDescriptor.Kind kind,String permission,Set<CacheDescriptor.Operation> operations){
        return new CacheDescriptor(id,"shop","shop.cache.items",group,scenario,kind,CacheDescriptor.Scope.TENANT,1,
                kind==CacheDescriptor.Kind.DATA?CachePolicy.defaults():null,operations,Set.of("save"),permission);
    }
    private SmaPrincipal actor(String... permissions){return new SmaPrincipal(101,"manager",9700,"admin",null,false,DataAccess.none(),Set.of(permissions),"UTC");}
}
