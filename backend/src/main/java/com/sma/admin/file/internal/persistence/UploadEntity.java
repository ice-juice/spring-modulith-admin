package com.sma.admin.file.internal.persistence;

import com.baomidou.mybatisplus.annotation.TableName;
import com.sma.admin.common.persistence.TenantEntity;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter @TableName("file_upload_request")
public class UploadEntity extends TenantEntity {
    private String portal;
    private String publicId;
    private String keyHash;
    private Long fileId;
    private Long securityGeneration;
    private Boolean imageOnly;
    private Long expectedSize;
    private String expectedChecksum;
    private String state;
}
