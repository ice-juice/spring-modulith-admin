package com.sma.admin.audit.internal.persistence;
import java.time.Instant;
import com.baomidou.mybatisplus.annotation.TableName;
import com.sma.admin.common.persistence.TenantEntity;
import lombok.Getter;
import lombok.Setter;
@Getter @Setter @TableName("audit_login_log")
public class LoginLogEntity extends TenantEntity {
 private String eventId;private Long userId;private Long deptId;private String username;private String portal;private String status;
 private String ip;private String userAgent;private String browser;private String operatingSystem;
 private String country;private String province;private String city;private String countryCode;private Boolean localAddress;private Boolean ipChanged;private Boolean unusualLocation;private Instant occurredAt;
}
