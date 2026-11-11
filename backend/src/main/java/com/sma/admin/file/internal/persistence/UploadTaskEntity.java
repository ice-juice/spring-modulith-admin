package com.sma.admin.file.internal.persistence;
@lombok.Getter @lombok.Setter @com.baomidou.mybatisplus.annotation.TableName("file_upload_task")
public class UploadTaskEntity extends com.sma.admin.common.persistence.TenantEntity {
    private String publicId;private Long fileId;private String ownerHash;private String ownerJson;private String portal;
    private String keyHash;private String inputFingerprint;private String application;private String sceneCode;private String snapshotJson;
    private String originalName;private String format;private String mode;private Long expectedSize;private String expectedChecksum;private Long partBytes;private Integer partCount;
    private String state;private Long version;private Integer receivedParts;private Long receivedBytes;private Long reservedBytes;private Boolean reservationReleased;
    private java.time.Instant expiresAt;private java.time.Instant retainUntil;
    @com.baomidou.mybatisplus.annotation.TableField(updateStrategy=com.baomidou.mybatisplus.annotation.FieldStrategy.ALWAYS) private String freezeClaim;
    private String frozenReceipt;private String frozenChecksum;
}
