package com.sma.admin.identity.internal.persistence;
@org.apache.ibatis.annotations.Mapper
public interface IdentityExternalMapper extends com.baomidou.mybatisplus.core.mapper.BaseMapper<IdentityExternalEntity> {
    /** Explicit history lookup: keep tenant interception, but include the logical tombstone for ownership protection. */
    @org.apache.ibatis.annotations.Select("SELECT id,tenant_id,realm_id,subject_id,issuer,external_subject,created_at,created_by,updated_at,updated_by,deleted FROM identity_external WHERE realm_id=#{realm} AND issuer=#{issuer} AND external_subject=#{subject} FOR UPDATE")
    IdentityExternalEntity history(long realm,String issuer,String subject);
    @org.apache.ibatis.annotations.Update("UPDATE identity_external SET deleted=0,updated_at=#{now},updated_by=0 WHERE id=#{id} AND subject_id=#{subject} AND deleted=1")
    int restore(long id,long subject,java.time.Instant now);
    @org.apache.ibatis.annotations.Select("SELECT id FROM identity_external WHERE subject_id=#{subject} LIMIT 1001 FOR UPDATE")
    java.util.List<Long> remembered(long subject);
}
