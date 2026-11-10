package com.sma.admin.auth.internal.persistence;
import com.baomidou.mybatisplus.annotation.TableName;
import com.sma.admin.common.persistence.TenantEntity;
@TableName("auth_session_guard")
public class SessionGuardEntity extends TenantEntity {}
