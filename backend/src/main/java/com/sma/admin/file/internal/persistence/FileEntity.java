package com.sma.admin.file.internal.persistence;
import com.baomidou.mybatisplus.annotation.TableName;
import com.sma.admin.common.persistence.TenantEntity;
import lombok.Getter;
import lombok.Setter;
@Getter @Setter @TableName("file_asset")
public class FileEntity extends TenantEntity {
 private String portal;private Long deptId;private String originalName;private String storageKey;private String contentType;private String kind;
 private Long sizeBytes;private String checksum;private String uploader;
}
