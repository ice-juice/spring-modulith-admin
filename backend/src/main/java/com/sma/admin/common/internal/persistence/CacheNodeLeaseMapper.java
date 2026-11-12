package com.sma.admin.common.internal.persistence;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.*;
@Mapper
public interface CacheNodeLeaseMapper extends BaseMapper<CacheNodeLeaseEntity> {
    @Update("""
        UPDATE common_cache_node_lease SET evidence_nonce=#{nonce},evidence_process_id=#{process},evidence_epoch=#{epoch},
        evidence_digest=#{digest},evidence_state=#{state},evidence_until=#{until},updated_at=UTC_TIMESTAMP(3),updated_by=0
        WHERE id=#{id} AND process_id=#{process} AND epoch=#{epoch} AND deleted=0
        """) int reportEvidence(long id,String process,long epoch,String nonce,String digest,String state,java.time.Instant until);
    @Update("""
        UPDATE common_cache_node_lease SET storage_report_json=#{report},updated_at=UTC_TIMESTAMP(3),updated_by=0
        WHERE id=#{id} AND process_id=#{process} AND epoch=#{epoch} AND deleted=0
        """) int reportStorage(long id,String process,long epoch,String report);
}
