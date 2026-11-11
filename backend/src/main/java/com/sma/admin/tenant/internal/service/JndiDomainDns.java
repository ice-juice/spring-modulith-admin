package com.sma.admin.tenant.internal.service;

import java.net.InetAddress;
import java.time.Duration;
import java.util.*;
import javax.naming.*;
import javax.naming.directory.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import com.sma.admin.common.security.TenantHost;

@Component
public class JndiDomainDns implements DomainDns {
    private final String providerUrl;
    @Autowired public JndiDomainDns() { this(null); }
    /** Test transport selection; production always uses the deployment's system DNS servers. */
    public JndiDomainDns(String providerUrl) { this.providerUrl=providerUrl; }
    @Override public Set<String> txt(String name,Duration timeout) throws NamingException {
        return Set.copyOf(resolve(name,new String[]{"TXT","CNAME"},timeout).getOrDefault("TXT",List.of()));
    }
    @Override public List<InetAddress> addresses(String name,Duration timeout) throws NamingException {
        var records=resolve(name,new String[]{"A","AAAA","CNAME"},timeout);
        var result=new LinkedHashMap<String,InetAddress>();
        for(String type:List.of("A","AAAA")) for(String value:records.getOrDefault(type,List.of())) {
            try { var address=DomainAddresses.literal(value);result.put(address.getHostAddress(),address); }
            catch(IllegalArgumentException invalid) { throw new NamingException("DNS did not return an IP literal"); }
        }
        if(result.size()>8) throw new NamingException("Too many domain addresses");
        return List.copyOf(result.values());
    }
    private Map<String,List<String>> resolve(String name,String[] types,Duration timeout) throws NamingException {
        long deadline=System.nanoTime()+timeout.toNanos();Set<String> visited=new HashSet<>();
        for(int depth=0;depth<5;depth++) {
            if(!visited.add(name)) throw new NamingException("DNS alias cycle");
            long millis=Math.max(0,(deadline-System.nanoTime())/1_000_000);
            if(millis<2) throw new NamingException("DNS deadline exceeded");
            var environment=new Hashtable<String,String>();
            environment.put(Context.INITIAL_CONTEXT_FACTORY,"com.sun.jndi.dns.DnsContextFactory");
            environment.put("com.sun.jndi.dns.timeout.initial",Long.toString(Math.max(1,millis/2)));
            environment.put("com.sun.jndi.dns.timeout.retries","1");
            if(providerUrl!=null) environment.put(Context.PROVIDER_URL,providerUrl);
            var context=new InitialDirContext(environment);
            Map<String,List<String>> records=new HashMap<>();
            try {
                var attributes=context.getAttributes(name,types);
                for(String type:types) {
                    var attribute=attributes.get(type);if(attribute==null) continue;
                    if(attribute.size()>32) throw new NamingException("Too many DNS records");
                    List<String> values=new ArrayList<>();
                    for(int i=0;i<attribute.size();i++) {
                        var value=attribute.get(i);if(!(value instanceof String text) || text.length()>1024) throw new NamingException("Invalid DNS record");
                        values.add(text);
                    }
                    records.put(type,List.copyOf(values));
                }
            } finally { context.close(); }
            if(System.nanoTime()>deadline) throw new NamingException("DNS deadline exceeded");
            if(records.containsKey("A") || records.containsKey("AAAA") || records.containsKey("TXT")) return records;
            var aliases=records.getOrDefault("CNAME",List.of());
            if(aliases.isEmpty()) return records;
            if(aliases.size()!=1) throw new NamingException("Ambiguous DNS alias");
            try { name=TenantHost.domain(aliases.getFirst()); }
            catch(IllegalArgumentException invalid) { throw new NamingException("Invalid DNS alias"); }
        }
        throw new NamingException("DNS alias limit exceeded");
    }
}
