package com.sma.admin.file.internal.persistence;
@lombok.Getter @lombok.Setter @com.baomidou.mybatisplus.annotation.TableName("file_upload_part")
public class UploadPartEntity extends com.sma.admin.common.persistence.TenantEntity {
    private Long taskId;private Integer partNumber;private Long sizeBytes;private String checksum;private String state;private String claimId;private String storageReceipt;private Integer attempts;
}
