package com.sma.release.catalog;

import java.util.List;
import org.springframework.stereotype.Service;
import com.sma.extension.protocol.*;
import com.sma.release.auth.*;
import com.sma.release.catalog.CatalogRecords.*;
import com.sma.release.deployment.DeploymentRecords.*;
import com.sma.release.store.ReleaseDatabase;

/** Safe console projections; never exposes credentials, session identifiers, private keys or worker tokens. */
@Service
public class ConsoleQueries {
    private final ReleaseDatabase database;
    private final IdentityService identities;
    public ConsoleQueries(ReleaseDatabase database,IdentityService identities){this.database=database;this.identities=identities;}
    public record Access(String userId,String username,boolean securityAdmin,List<SecurityRecords.ScopeGrant> scopes,List<String> environments) {}
    public record Page<T>(List<T> items,long total,int offset,int limit) {}
    public record Usage(String groupId,long bytes,long limit) {}
    public record Storage(List<Usage> groups,Long repositoryBytes,Long repositoryLimit) {}
    public Access access(String session){return database.transaction(tx -> {
        var p=identities.principal(tx,session);var scopes=tx.list("scope",SecurityRecords.ScopeGrant.class).stream().filter(g -> g.userId().equals(p.userId())).toList();
        var environments=p.securityAdmin()?tx.list("machine",MachineService.Machine.class).stream().flatMap(m -> m.environments().stream()).distinct().sorted().toList():scopes.stream().map(SecurityRecords.ScopeGrant::environment).distinct().sorted().toList();
        return new Access(p.userId(),p.username(),p.securityAdmin(),scopes,environments);
    });}
    public Page<Artifact> artifacts(String session,String pluginId,String environment,int offset,int limit) {
        bounds(offset,limit);return database.transaction(tx -> {var p=identities.principal(tx,session);var plugin=plugin(tx,pluginId);read(tx,p,plugin.groupId(),environment);
            var rows=tx.list("artifact",Artifact.class).stream().filter(a -> a.pluginId().equals(pluginId) && a.environment().equals(environment))
                    .sorted(java.util.Comparator.comparing(Artifact::createdAt).reversed().thenComparing(Artifact::id)).toList();
            return new Page<>(rows.stream().skip(offset).limit(limit).toList(),rows.size(),offset,limit);
        });
    }
    public List<ValidationJob> jobs(String session,String pluginId,String environment){return database.transaction(tx -> {
        var p=identities.principal(tx,session);read(tx,p,plugin(tx,pluginId).groupId(),environment);
        var ids=tx.list("artifact",Artifact.class).stream().filter(a -> a.pluginId().equals(pluginId) && a.environment().equals(environment)).map(Artifact::id).collect(java.util.stream.Collectors.toSet());
        return tx.list("job",ValidationJob.class).stream().filter(j -> j.environment().equals(environment) && ids.contains(j.artifactId()))
                .sorted(java.util.Comparator.comparing(ValidationJob::createdAt).reversed()).limit(200).toList();
    });}
    public ActionReceipt byKey(String session,String key){Checks.id(key);return database.transaction(tx -> {
        var p=identities.principal(tx,session);String id="a-"+Hashes.sha256((p.userId()+":"+key).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        return tx.get("action",id,ActionReceipt.class).orElseThrow(() -> new ProtocolException("action-not-found"));
    });}
    public Storage storage(String session){return database.transaction(tx -> {
        var p=identities.principal(tx,session);var policy=tx.get("policy","resources",ResourcePolicy.class).orElse(ResourcePolicy.defaults());
        // Group totals span environments. Only administrators may see aggregate group/repository totals.
        Checks.require(p.securityAdmin(),"forbidden");
        return new Storage(tx.list("group",Group.class).stream().map(g -> new Usage(g.id(),StorageUsage.group(tx,g.id()),policy.groupBytes())).toList(),StorageUsage.repository(tx),policy.repositoryBytes());
    });}
    public List<SecurityRecords.ScopeGrant> grants(String session,String group,String environment){return database.transaction(tx -> {
        Checks.require(identities.principal(tx,session).securityAdmin(),"forbidden");return tx.list("scope",SecurityRecords.ScopeGrant.class).stream().filter(g -> g.groupId().equals(group) && g.environment().equals(environment)).toList();
    });}
    private static Plugin plugin(ReleaseDatabase.Tx tx,String id){Checks.id(id);return tx.get("plugin",id,Plugin.class).orElseThrow(() -> new ProtocolException("plugin-not-found"));}
    private static void read(ReleaseDatabase.Tx tx,SecurityRecords.Principal p,String group,String environment){Checks.id(environment);Checks.require(p.securityAdmin() || tx.list("scope",SecurityRecords.ScopeGrant.class).stream().anyMatch(g -> g.userId().equals(p.userId()) && g.groupId().equals(group) && g.environment().equals(environment) && g.read()),"forbidden");}
    private static void bounds(int offset,int limit){Checks.require(offset>=0 && offset<=100000 && limit>0 && limit<=200,"invalid-page");}
}
