package com.sma.admin.identity.internal.service;
import java.util.List;
import com.sma.admin.common.security.*;
import org.springframework.stereotype.Service;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

@Service
public class ApplicationClientResolver {
    public record Client(IdentityAuthority.Snapshot authority,String handle,String name,List<String> origins,String sameSite,boolean googleEnabled,String googleClientId,boolean registrationEnabled){
        public boolean web(){return authority.platform().equals("WEB");}
    }
    private final IdentityAuthority authority;private final JsonMapper json;
    public ApplicationClientResolver(IdentityAuthority authority,JsonMapper json){this.authority=authority;this.json=json;}
    public Client resolve(String handle){
        var entry=TenantEntryScope.require();
        return ActorContext.withinJob(entry.tenantId(),"identity-http-entry",()->{
            var loaded=authority.entry(handle);var configuration=loaded.configuration();
            return new Client(loaded.snapshot(),handle,loaded.client().getName(),json.readValue(configuration.getOriginsJson(),new TypeReference<List<String>>(){}),
                    switch(configuration.getCookieSameSite()){case "LAX"->"Lax";case "NONE"->"None";default->throw new IllegalStateException("Unsupported cookie policy");},
                    configuration.getGoogleEnabled(),configuration.getGoogleClientId(),loaded.realm().getRegistrationEnabled());
        });
    }
}
