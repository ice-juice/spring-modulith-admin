package com.sma.admin.common.internal.persistence;
import java.time.Instant;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.*;
@Mapper
public interface CacheTaskMapper extends BaseMapper<CacheTaskEntity> {
    @Select("SELECT UTC_TIMESTAMP(3)") Instant now();
    /** Only never-submitted previews; accepted work and request receipts are never evicted for capacity. */
    @Delete("DELETE FROM common_cache_task WHERE tenant_id=#{tenant} AND state='PREVIEW' AND expires_at<UTC_TIMESTAMP(3) LIMIT 100")
    int purgeExpiredPreviews(long tenant);
}
