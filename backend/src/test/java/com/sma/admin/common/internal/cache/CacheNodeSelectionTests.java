package com.sma.admin.common.internal.cache;
import static org.assertj.core.api.Assertions.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import com.sma.admin.common.cache.*;
import com.sma.admin.common.security.*;
import com.sma.admin.support.Users;

class CacheNodeSelectionTests {
    private final CacheNodeIdentity identity=new CacheNodeIdentity(new CacheNodeProperties("test","node-a",List.of("node-a","node-b"),true));
    private final CacheNodeSelection selection=new CacheNodeSelectionService(identity);
    private SmaPrincipal viewer(){return new SmaPrincipal(100L,"viewer",1,"admin",null,false,DataAccess.unrestricted(),Set.of("monitor:cache:view"),"UTC");}
    private void rejected(String node,String process,CacheNodeSelectionRejected.Reason reason){
        assertThatThrownBy(()->selection.select(node,process)).isInstanceOfSatisfying(CacheNodeSelectionRejected.class,error->assertThat(error.reason()).isEqualTo(reason));
    }
    @Test void legacySelectionAndExplicitLocalSelectionReferToTheSameReceivingProcess(){
        Users.as(viewer(),()->{var local=selection.select(null,null);assertThat(local).isEqualTo(new CacheNodeSelection.Selected("node-a",identity.processId()));
            assertThat(selection.select(local.nodeId(),local.processId())).isEqualTo(local);return null;});
    }
    @Test void wrongNodeAndReplacedProcessAreDifferentAndNeitherFallsBack(){
        Users.as(viewer(),()->{rejected("node-b",UUID.randomUUID().toString(),CacheNodeSelectionRejected.Reason.WRONG_NODE);
            rejected("node-a",UUID.randomUUID().toString(),CacheNodeSelectionRejected.Reason.REPLACED_PROCESS);return null;});
    }
    @Test void bothHeadersAreRequiredAndNoUnknownNodeUrlOrNonCanonicalProcessIsAccepted(){
        Users.as(viewer(),()->{
            for(String node:List.of("", "node-unknown", "http://localhost:8080", "node-a,node-b", "NODE-A"))rejected(node,identity.processId(),CacheNodeSelectionRejected.Reason.INVALID);
            rejected(null,identity.processId(),CacheNodeSelectionRejected.Reason.INVALID);rejected("node-a",null,CacheNodeSelectionRejected.Reason.INVALID);
            rejected("node-a","1-1-1-1-1",CacheNodeSelectionRejected.Reason.INVALID);return null;
        });
    }
    @Test void selectingKnownIdentifiersNeverGrantsViewPermission(){
        Users.as(new SmaPrincipal(100L,"operator",1,"admin",null,false,DataAccess.unrestricted(),Set.of("monitor:cache:clear"),"UTC"),()->{
            assertThatThrownBy(()->selection.select("node-a",identity.processId())).isInstanceOf(AccessDeniedException.class);return null;
        });
        assertThatThrownBy(()->selection.select(null,null)).isInstanceOf(AccessDeniedException.class);
    }
}
