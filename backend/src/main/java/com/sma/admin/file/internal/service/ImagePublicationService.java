package com.sma.admin.file.internal.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sma.admin.common.*;
import com.sma.admin.common.security.CurrentUser;
import com.sma.admin.file.*;
import com.sma.admin.file.internal.persistence.*;

@Service
public class ImagePublicationService implements ImagePublication {
    private final FileLookupMapper files;
    private final FileService access;
    private final StorageAdapter storage;
    private final PublicImageEncoder encoder;
    public ImagePublicationService(FileLookupMapper files,FileService access,StorageAdapter storage,PublicImageEncoder encoder) {
        this.files=files;this.access=access;this.storage=storage;this.encoder=encoder;
    }
    @Override @Transactional(propagation=Propagation.MANDATORY)
    public Image prepare(long fileId) {
        CurrentUser.require();
        var row=files.selectOne(new LambdaQueryWrapper<FileEntity>().eq(FileEntity::getId,fileId)
                .eq(FileEntity::getPortal,CurrentUser.portal()).last("FOR UPDATE"));
        if(row==null) throw new BizException(CommonErrorCode.DATA_NOT_FOUND);
        access.metadata(fileId); // Original ownership/data grants or an explicit reference policy, never arbitrary same-tenant access.
        if(row.getSizeBytes()>PublicImageEncoder.MAX_BYTES) throw new BizException(FileErrorCode.INVALID_IMAGE);
        return encoder.encode(storage.path(row.getStorageKey()));
    }
}
