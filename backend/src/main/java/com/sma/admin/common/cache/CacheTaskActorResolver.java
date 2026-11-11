package com.sma.admin.common.cache;

import java.util.Optional;
import com.sma.admin.common.security.SmaPrincipal;

/** Host SPI for a persisted cache-task SYSTEM_JOB only. Never expose it as an HTTP identity switch. */
public interface CacheTaskActorResolver {
    Optional<SmaPrincipal> resolve(long userId,String portal);
}
