package com.sma.admin.message.internal.persistence;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;
import com.baomidou.mybatisplus.annotation.TableName;
import com.sma.admin.common.persistence.TenantEntity;
@Getter @Setter @TableName("message_socket_signal")
public class SocketSignalEntity extends TenantEntity {
    private String portal;
    private Long userId;
    private String signalType;
    private Long objectId;
    private Long revision;
    private String reason;
    private Instant expiresAt;
}
