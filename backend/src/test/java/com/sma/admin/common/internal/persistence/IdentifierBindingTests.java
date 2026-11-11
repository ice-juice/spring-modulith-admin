package com.sma.admin.common.internal.persistence;

import static org.assertj.core.api.Assertions.*;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import com.baomidou.mybatisplus.core.incrementer.IdentifierGenerator;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.sma.admin.common.PageQuery;
import com.sma.admin.common.persistence.IdentifierAllocation;
import com.sma.admin.common.security.*;
import com.sma.admin.iam.internal.service.RoleService;
import com.sma.admin.iam.internal.persistence.RoleMapper;
import com.sma.admin.file.internal.service.FileService;
import com.sma.admin.support.*;

@IntegrationTest
class IdentifierBindingTests {
    @Autowired IdentifierGenerator generator;
    @Autowired IdentifierAllocation identifiers;
    @Autowired IdBlockMapper blocks;
    @Autowired RoleService roles;
    @Autowired RoleMapper roleRows;
    @Autowired FileService files;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactions;
    private SmaPrincipal actor(long tenant){return new SmaPrincipal(101,"id-owner",tenant,"admin",null,true,DataAccess.unrestricted(),Set.of(),"UTC");}
    @Test void publicAllocationLegacyToolkitAndEntityAssignmentUseTheSameDurableAuthority(){
        assertThat(generator).isInstanceOf(DatabaseIdentifierGenerator.class);
        Users.as(actor(98471),()->{
            long publicId=identifiers.nextId(),toolkitId=IdWorker.getId();
            long entityId=roles.save(null,new RoleService.Input(0L,"ID binding","id-binding",0,true,DataScope.ALL,Set.of(),null)).id();
            assertThat(List.of(publicId,toolkitId,entityId)).doesNotHaveDuplicates().allSatisfy(id->assertThat(id).isPositive().isLessThan(blocks.selectById(1L).getNextId()));
            assertThat(roleRows.selectById(entityId)).isNotNull();return null;
        });
    }
    @Test void rollingBackAnEntityWriteNeverReturnsItsIdentifierToTheAllocator(){
        Users.as(actor(98472),()->{
            long[] burned={0};new TransactionTemplate(transactions).execute(status->{
                burned[0]=roles.save(null,new RoleService.Input(0L,"Rollback ID","rollback-id",0,true,DataScope.ALL,Set.of(),null)).id();
                status.setRollbackOnly();return null;
            });
            assertThat(roleRows.selectById(burned[0])).isNull();
            assertThat(identifiers.nextId()).isNotEqualTo(burned[0]);assertThat(IdWorker.getId()).isNotEqualTo(burned[0]);return null;
        });
    }
    @Test void defaultRecentOrderingUsesCreationTimeEvenWhenNodeRangesHaveDifferentNumericOrder(){
        Users.as(actor(98473),()->{
            var first=files.upload(new MockMultipartFile("file","first.txt","text/plain","first".getBytes(StandardCharsets.UTF_8)));
            var second=files.upload(new MockMultipartFile("file","second.txt","text/plain","second".getBytes(StandardCharsets.UTF_8)));
            try{
                assertThat(first.id()).isLessThan(second.id());
                jdbc.update("UPDATE file_asset SET created_at=? WHERE id=? AND tenant_id=?",Timestamp.from(Instant.now().plusSeconds(60)),first.id(),98473);
                jdbc.update("UPDATE file_asset SET created_at=? WHERE id=? AND tenant_id=?",Timestamp.from(Instant.now().minusSeconds(60)),second.id(),98473);
                assertThat(files.page(new PageQuery(1,20,null,null),null,null).items()).extracting(item->item.id()).containsExactly(first.id(),second.id());
            }finally{files.delete(first.id());files.delete(second.id());}
            return null;
        });
    }
}
