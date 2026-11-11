package com.sma.admin.tenant.internal.service;

import java.net.InetAddress;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import javax.naming.NamingException;

/** Internal bounded DNS transport. Production uses deployment DNS, tests can supply a controlled authoritative fixture. */
public interface DomainDns {
    Set<String> txt(String name,Duration timeout) throws NamingException;
    List<InetAddress> addresses(String name,Duration timeout) throws NamingException;
}
