package com.sma.admin.file.internal.service;

import org.springframework.stereotype.Component;
import org.springframework.security.access.AccessDeniedException;
import com.sma.admin.common.cache.CacheResourceAdapter;
import com.sma.admin.common.security.CurrentUser;

@Component
public class FileStorageCacheAdapter implements CacheResourceAdapter {
    public String region(){return "file.storage";}
    public Snapshot inspect(){
        if(!CurrentUser.require().permissions().contains("file:file:view"))throw new AccessDeniedException("File resource view denied");
        // Persistent business assets are not cache entries; no directory scan or connection/path disclosure.
        return new Snapshot(-1,-1,-1,-1,"file.cache.lifecycle.storage");
    }
}
