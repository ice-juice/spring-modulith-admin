package com.sma.admin.file.internal.persistence;
@lombok.Getter @lombok.Setter @com.baomidou.mybatisplus.annotation.TableName("file_scene_revision")
public class SceneRevisionEntity extends com.sma.admin.common.persistence.TenantEntity {
    private Long sceneId;private Long version;private String configurationJson;private String profilesJson;
}
