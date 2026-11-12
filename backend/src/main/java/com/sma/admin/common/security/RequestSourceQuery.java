package com.sma.admin.common.security;

import java.util.Optional;

/** Read the current synchronous HTTP observation. Outside that request thread no source is inferred. */
public interface RequestSourceQuery {
    Optional<RequestSource> current();
}
