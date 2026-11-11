package com.sma.admin.file.internal.persistence;
@lombok.Getter @lombok.Setter @com.baomidou.mybatisplus.annotation.TableName("file_scene")
public class SceneEntity extends com.sma.admin.common.persistence.TenantEntity {
    private String application;private String code;private String name;private String draftJson;
    private Long version;private Long publishedVersion;private Boolean enabled;
}
