package com.sma.release.catalog;

import com.sma.release.store.ReleaseDatabase;
import com.sma.release.catalog.CatalogRecords.*;
import com.sma.release.deployment.DeploymentRecords.BuildReservation;
import com.sma.extension.protocol.*;

/** Control DB lock must be held while checking and creating reservations. */
public final class StorageUsage {
    private StorageUsage() {}
    public static long reserved(ReleaseDatabase.Tx tx) {
        return tx.list("upload",Upload.class).stream().filter(u -> u.state().equals("RECEIVING")).mapToLong(Upload::reservedBytes).sum()
                + tx.list("build-reservation",BuildReservation.class).stream().mapToLong(BuildReservation::bytes).sum();
    }
    public static long plugin(ReleaseDatabase.Tx tx,String plugin) {
        return tx.list("artifact",Artifact.class).stream().filter(a -> a.pluginId().equals(plugin) && !a.state().equals("DELETED")).mapToLong(Artifact::bytes).sum()
                +tx.list("upload",Upload.class).stream().filter(u -> u.pluginId().equals(plugin) && u.state().equals("RECEIVING")).mapToLong(Upload::reservedBytes).sum()
                +tx.list("build-reservation",BuildReservation.class).stream().filter(b -> b.pluginId().equals(plugin)).mapToLong(BuildReservation::bytes).sum();
    }
    public static long group(ReleaseDatabase.Tx tx,String group) {
        return tx.list("plugin",Plugin.class).stream().filter(p -> p.groupId().equals(group)).mapToLong(p -> plugin(tx,p.id())).sum();
    }
    public static long repository(ReleaseDatabase.Tx tx) {return tx.list("artifact",Artifact.class).stream().filter(a -> !a.state().equals("DELETED")).mapToLong(Artifact::bytes).sum()+reserved(tx);}
    public static void reserve(ReleaseDatabase.Tx tx,ResourcePolicy policy,String plugin,String group,long incoming) {
        Checks.require(incoming>=0 && incoming<=policy.pluginBytes()-plugin(tx,plugin),"plugin-quota-exceeded");
        Checks.require(incoming<=policy.groupBytes()-group(tx,group),"group-quota-exceeded");
        Checks.require(incoming<=policy.repositoryBytes()-repository(tx),"repository-quota-exceeded");
    }
}
