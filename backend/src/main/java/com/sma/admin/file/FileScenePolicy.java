package com.sma.admin.file;

import com.sma.admin.common.security.ActorRef;

/** One policy per owning application. Default is deny; references and application strings alone never authorize. */
public interface FileScenePolicy {
    String application();
    boolean canUse(ActorRef actor,FileSceneAccess request);
}
