package com.sma.admin.file.internal.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.nio.file.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.support.*;
import com.sma.admin.common.security.DataAccess;
import com.sma.admin.file.internal.persistence.*;
import com.sma.admin.support.Users;

class FileRollbackCleanupTests {
    @TempDir Path directory;
    @Test void onlyConfirmedRollbackRemovesBytesUnknownCommitMustPreserveThem()throws Exception{
        var properties=new FileProperties(directory.toString(),1024);var storage=new LocalStorage(properties);
        var service=new FileService(mock(FileMapper.class),mock(FileLookupMapper.class),storage,properties,List.of());
        for(int status:List.of(TransactionSynchronization.STATUS_UNKNOWN,TransactionSynchronization.STATUS_COMMITTED,TransactionSynchronization.STATUS_ROLLED_BACK)){
            long before=count();TransactionSynchronizationManager.initSynchronization();
            try{
                Users.as(Users.principal(101,79820,DataAccess.unrestricted()),()->service.upload(new MockMultipartFile("file","a.txt","text/plain",new byte[]{1,2,3})));
                for(var synchronization:TransactionSynchronizationManager.getSynchronizations())synchronization.afterCompletion(status);
            }finally{TransactionSynchronizationManager.clearSynchronization();}
            assertThat(count()).isEqualTo(before+(status==TransactionSynchronization.STATUS_ROLLED_BACK?0:1));
        }
    }
    private long count()throws Exception{try(var paths=Files.list(directory)){return paths.count();}}
}
