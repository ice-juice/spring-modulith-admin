package com.sma.admin.message.internal.persistence;
import java.time.Instant;
import com.baomidou.mybatisplus.annotation.TableName;
import com.sma.admin.common.persistence.TenantEntity;
import lombok.Getter;
import lombok.Setter;
@Getter @Setter @TableName("message_socket_ticket")
public class SocketTicketEntity extends TenantEntity {
    private String portal;
    private Long userId;
    private Long sessionId;
    private String tokenHash;
    private Instant expiresAt;
}
