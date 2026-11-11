package com.sma.admin.file.internal.service;

import java.util.Objects;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sma.admin.common.BizException;
import com.sma.admin.common.security.ActorContext;
import com.sma.admin.file.*;
import com.sma.admin.file.internal.persistence.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

/** Resolve a server-held task profile snapshot before, never during, file I/O. No arbitrary request paths are accepted. */
@Service
public class LocalStorageLocation {
    public record Location(long tenantId,long profileId,String rootAlias,String directory,String prefix){
        @Override public String toString(){return "LocalStorageLocation[REDACTED]";}
    }
    private final StorageRevisionMapper revisions;private final StorageSettings settings;
    public LocalStorageLocation(StorageRevisionMapper revisions,StorageSettings settings){this.revisions=revisions;this.settings=settings;}
    @Transactional(propagation=Propagation.REQUIRES_NEW,readOnly=true)
    public Location resolve(StorageSceneService.Resolved snapshot){
        ActorContext.requireTenant(snapshot.tenantId());if(snapshot.profile().provider()!=StorageProvider.LOCAL)throw new BizException(FileErrorCode.INVALID_STORAGE_CONFIGURATION);
        var row=revisions.selectOne(new LambdaQueryWrapper<StorageRevisionEntity>().eq(StorageRevisionEntity::getProfileId,snapshot.profile().id())
                .eq(StorageRevisionEntity::getConfigurationVersion,snapshot.profile().configurationVersion()));
        if(row==null || !Objects.equals(row.getCredentialVersion(),snapshot.profile().credentialVersion()))throw new BizException(FileErrorCode.STORAGE_PROFILE_NOT_FOUND);
        String directory=settings.localRoots().get(row.getLocalRoot());if(directory==null)throw new BizException(FileErrorCode.STORAGE_PROFILE_NOT_FOUND);
        return new Location(snapshot.tenantId(),snapshot.profile().id(),row.getLocalRoot(),directory,row.getObjectPrefix());
    }
}
