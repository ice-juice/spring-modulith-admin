package com.sma.admin.iam;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.json.JsonMapper;
import com.sma.admin.common.security.*;
import com.sma.admin.iam.internal.service.*;
import com.sma.admin.support.*;

@IntegrationTest @AutoConfigureMockMvc
@TestPropertySource(properties={"sma.security.requests.fingerprint-key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
        "sma.security.requests.minimum-write-interval=30s"})
class IamRequestGovernanceHttpTests {
    @Autowired MockMvc mvc;
    @Autowired JsonMapper json;
    @Autowired RoleService roles;
    @Autowired RolePermissionService grants;
    @Autowired UserService users;
    @Autowired ProfileService profiles;
    @Autowired PermissionService permissions;
    @Autowired IamPermissionCatalog catalog;
    private static final Set<String> ROLE_PERMISSIONS=Set.of("iam:role:create","iam:role:update","iam:role:delete","iam:role:grant","iam:role:view");

    @Test void roleWritesRequireKeysReplayMetadataAndKeepDifferentObjectsIndependent() {
        Users.as(actor(79301,79301,ROLE_PERMISSIONS,DataAccess.unrestricted()),()->{
            String body=role("one","One"), key=key();
            assertThat(send(post("/api/iam/roles").contentType("application/json").content(body)).getResponse().getStatus()).isEqualTo(400);
            var first=send(write(post("/api/iam/roles"),key,body)); success(first);
            String id=value(first,"/data/id"), receipt=first.getResponse().getHeader("X-Operation-Id");
            var replay=send(write(post("/api/iam/roles"),key,body));
            assertThat(replay.getResponse().getStatus()).isEqualTo(409);
            assertThat(value(replay,"/data/state")).isEqualTo("SUCCEEDED");
            assertThat(value(replay,"/data/name")).isEmpty();
            assertThat(roles.list()).hasSize(1);
            long other=roles.save(null,new RoleService.Input(0L,"Two","two",0,true,DataScope.SELF,Set.of(),null)).id();
            success(send(write(put("/api/iam/roles/"+id),key(),role("one","Changed one"))));
            success(send(write(put("/api/iam/roles/"+other),key(),role("two","Changed two"))));
            String grantKey=key();
            success(send(write(put("/api/iam/roles/"+id+"/permissions"),grantKey,"{\"permissionIds\":[]}")));
            assertThat(send(write(put("/api/iam/roles/"+id+"/permissions"),grantKey,"{\"permissionIds\":[]}")).getResponse().getStatus()).isEqualTo(409);
            Users.as(actor(79301,79301,Set.of(),DataAccess.unrestricted()),()->{
                assertThat(send(get("/api/common/requests/"+receipt)).getResponse().getStatus()).isEqualTo(403);return null;
            });
            return null;
        });
    }

    @Test void roleGrantCannotCrossTenantOrEscalateExistingAuthority() {
        var owner=new SmaPrincipal(79302,"owner",79302,"admin",null,true,DataAccess.unrestricted(),ROLE_PERMISSIONS,"UTC");
        long[] ids=Users.as(owner,()->{
            permissions.sync(catalog.permissions());
            long permission=permissions.list().stream().filter(row->row.permissionKey().equals("iam:user:create")).findFirst().orElseThrow().id();
            long role=roles.save(null,new RoleService.Input(0L,"Restricted","restricted",0,true,DataScope.SELF,Set.of(),null)).id();
            return new long[]{role,permission};
        });
        Users.as(actor(79303,79303,ROLE_PERMISSIONS,DataAccess.unrestricted()),()->{
            var foreign=send(write(put("/api/iam/roles/"+ids[0]+"/permissions"),key(),"{\"permissionIds\":[]}"));
            assertThat(value(foreign,"/error")).isEqualTo("common.error.data-not-found");return null;
        });
        Users.as(actor(79304,79302,Set.of("iam:role:grant"),DataAccess.unrestricted()),()->{
            var escalation=send(write(put("/api/iam/roles/"+ids[0]+"/permissions"),key(),"{\"permissionIds\":[\""+ids[1]+"\"]}"));
            assertThat(escalation.getResponse().getStatus()).isEqualTo(403);
            assertThat(grants.selected(ids[0])).isEmpty();return null;
        });
    }

    @Test void passwordRetryExecutesOnceAndPreservesExistingPolicyAndDataScope() {
        Users.as(actor(79305,79305,Set.of("iam:user:reset-password"),DataAccess.unrestricted()),()->{
            long id=createUser("governed-password");
            long initial=users.findById(id).orElseThrow().credentialVersion();
            String key=key(), body="{\"password\":\"n8L3w0S5\"}";
            success(send(write(put("/api/iam/users/"+id+"/password"),key,body)));
            var replay=send(write(put("/api/iam/users/"+id+"/password"),key,body));
            assertThat(replay.getResponse().getStatus()).isEqualTo(409);
            assertThat(value(replay,"/data/state")).isEqualTo("SUCCEEDED");
            assertThat(text(replay)).doesNotContain("n8L3w0S5","passwordHash");
            assertThat(users.findById(id).orElseThrow().credentialVersion()).isEqualTo(initial+1);
            assertThat(users.matchesPassword(id,"n8L3w0S5")).isTrue();
            Users.as(actor(79306,79305,Set.of("iam:user:reset-password"),DataAccess.none()),()->{
                var denied=send(write(put("/api/iam/users/"+id+"/password"),key(),"{\"password\":\"p9M4x1T6\"}"));
                assertThat(value(denied,"/error")).isEqualTo("common.error.data-not-found");return null;
            });
            Users.as(actor(79307,79307,Set.of("iam:user:reset-password"),DataAccess.unrestricted()),()->{
                var denied=send(write(put("/api/iam/users/"+id+"/password"),key(),body));
                assertThat(value(denied,"/error")).isEqualTo("common.error.data-not-found");return null;
            });
            Users.as(actor(id,79305,Set.of(),DataAccess.own()),()->{
                String selfKey=key(), change="{\"oldPassword\":\"n8L3w0S5\",\"newPassword\":\"p9M4x1T6\"}";
                success(send(write(put("/api/iam/users/me/password"),selfKey,change)));
                assertThat(send(write(put("/api/iam/users/me/password"),selfKey,change)).getResponse().getStatus()).isEqualTo(409);
                return null;
            });
            assertThat(users.findById(id).orElseThrow().credentialVersion()).isEqualTo(initial+2);
            assertThat(users.matchesPassword(id,"p9M4x1T6")).isTrue();
            // A different actor avoids the 30-second test cooldown, reaching the unchanged domain policy.
            Users.as(actor(79308,79305,Set.of("iam:user:reset-password"),DataAccess.unrestricted()),()->{
                var weak=send(write(put("/api/iam/users/"+id+"/password"),key(),"{\"password\":\"abc\"}"));
                assertThat(value(weak,"/error")).isEqualTo("iam.error.password-format");return null;
            });
            assertThat(users.findById(id).orElseThrow().credentialVersion()).isEqualTo(initial+2);return null;
        });
    }

    @Test void profileReceiptsNeverReturnPrivateFieldsOrShareAnotherUsersIntent() {
        long[] ids=Users.as(actor(79309,79309,Set.of(),DataAccess.unrestricted()),()->new long[]{createUser("profile-a"),createUser("profile-b")});
        String key=key(), body="{\"name\":\"Private name\",\"email\":\"private@example.test\",\"gender\":\"UNSPECIFIED\"}";
        String first=Users.as(actor(ids[0],79309,Set.of(),DataAccess.own()),()->{
            var response=send(write(put("/api/iam/profile"),key,body));success(response);
            var replay=send(write(put("/api/iam/profile"),key,body));
            assertThat(replay.getResponse().getStatus()).isEqualTo(409);
            assertThat(text(replay)).doesNotContain("Private name","private@example.test");
            assertThat(profiles.get().name()).isEqualTo("Private name");
            return response.getResponse().getHeader("X-Operation-Id");
        });
        Users.as(actor(ids[1],79309,Set.of(),DataAccess.own()),()->{
            assertThat(send(get("/api/common/requests/"+first)).getResponse().getStatus()).isEqualTo(404);
            var response=send(write(put("/api/iam/profile"),key,body));success(response);
            assertThat(response.getResponse().getHeader("X-Operation-Id")).isNotEqualTo(first);return null;
        });
    }

    private long createUser(String username) {
        return users.create(new UserService.Create(new UserService.Input(username,username,null,null,null,"UNSPECIFIED",true,Set.of(),Set.of(),null,"UTC"),"m7K2v9R4")).id();
    }
    private static String role(String code,String name){return "{\"name\":\""+name+"\",\"code\":\""+code+"\",\"sort\":0,\"enabled\":true,\"dataScope\":\"SELF\",\"departmentIds\":[]}";}
    private static String key(){return UUID.randomUUID().toString();}
    private static SmaPrincipal actor(long id,long tenant,Set<String> permissions,DataAccess data){return new SmaPrincipal(id,"governance-iam",tenant,"admin",null,false,data,permissions,"UTC");}
    private MockHttpServletRequestBuilder write(MockHttpServletRequestBuilder request,String key,String body){return request.header("Idempotency-Key",key).contentType("application/json").content(body);}
    private MvcResult send(MockHttpServletRequestBuilder request){
        var user=CurrentUser.require();return Users.as(user,()->{try{return mvc.perform(request.with(Users.request(user))).andReturn();}catch(Exception ex){throw new IllegalStateException(ex);}});
    }
    private String text(MvcResult response){try{return response.getResponse().getContentAsString();}catch(Exception ex){throw new IllegalStateException(ex);}}
    private String value(MvcResult response,String pointer){return json.readTree(text(response)).at(pointer).asText();}
    private void success(MvcResult response){assertThat(response.getResponse().getStatus()).as(text(response)).isEqualTo(200);assertThat(value(response,"/code")).as(text(response)).isEqualTo("0");}
}
