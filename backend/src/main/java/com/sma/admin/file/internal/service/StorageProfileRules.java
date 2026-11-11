package com.sma.admin.file.internal.service;

import java.net.URI;
import java.util.*;
import com.sma.admin.common.BizException;
import com.sma.admin.file.*;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

/** Configuration syntax only. Runtime adapters must separately enforce DNS/IP/redirect and transport policies. */
@Component @EnableConfigurationProperties(StorageSettings.class)
public class StorageProfileRules {
    public record Location(String localRoot,String region,String endpoint,String bucket,String prefix){}
    private final StorageSettings settings;
    public StorageProfileRules(StorageSettings settings){this.settings=settings;}
    public List<String> localRoots(){return settings.localRoots().keySet().stream().sorted().toList();}
    public Location location(StorageProvider provider,String root,String region,String endpoint,String bucket,String prefix){
        if(provider==null)throw invalid();String actualPrefix=prefix==null?"":prefix;
        if(actualPrefix.length()>128 || !actualPrefix.isEmpty() && !actualPrefix.matches("[A-Za-z0-9._-]+(?:/[A-Za-z0-9._-]+)*"))throw invalid();
        for(String part:actualPrefix.split("/"))if(part.equals(".") || part.equals("..") || part.endsWith(".")
                || part.matches("(?i)(?:CON|PRN|AUX|NUL|COM[1-9]|LPT[1-9])(?:\\..*)?"))throw invalid();
        if(provider==StorageProvider.LOCAL){
            if(root==null || !settings.localRoots().containsKey(root) || present(region) || present(endpoint) || present(bucket))throw invalid();
            return new Location(root,null,null,null,actualPrefix);
        }
        if(present(root) || bucket==null || !bucket.matches("[a-z0-9][a-z0-9.-]{1,61}[a-z0-9]") || bucket.contains("..") || bucket.contains(".-") || bucket.contains("-.")
                || bucket.matches("[0-9]+(?:\\.[0-9]+){3}"))throw invalid();
        String expected=null;
        switch(provider){
            case S3->{
                if(region==null || !region.matches("[a-z]{2}(?:-[a-z]+){1,2}-[1-9][0-9]?"))throw invalid();
                expected="https://s3."+region+(region.startsWith("cn-")?".amazonaws.com.cn":".amazonaws.com");
                if(bucket.startsWith("xn--") || bucket.startsWith("sthree-") || bucket.startsWith("amzn-s3-demo-")
                        || bucket.endsWith("-s3alias") || bucket.endsWith("--ol-s3") || bucket.endsWith(".mrap") || bucket.endsWith("--x-s3") || bucket.endsWith("--table-s3"))throw invalid();
            }
            case OSS->{
                if(region==null || !region.matches("[a-z]{2}(?:-[a-z0-9]+){1,3}") || bucket.contains("."))throw invalid();
                expected="https://oss-"+region+".aliyuncs.com";
            }
            case R2->{if(region==null)region="auto";if(!region.equals("auto") || !present(endpoint) || bucket.contains("."))throw invalid();}
            default->throw invalid();
        }
        String canonical=canonical(present(endpoint)?endpoint:expected);
        if(provider==StorageProvider.R2){if(!URI.create(canonical).getHost().matches("[a-f0-9]{32}\\.r2\\.cloudflarestorage\\.com"))throw invalid();}
        else if(!canonical.equals(expected))throw invalid();
        return new Location(null,region,canonical,bucket,actualPrefix);
    }
    private String canonical(String value){
        try{
            var uri=URI.create(value);if(!"https".equals(uri.getScheme()) || uri.getHost()==null || uri.getUserInfo()!=null || uri.getPort()!=-1 || uri.getQuery()!=null || uri.getFragment()!=null
                    || !Set.of("","/").contains(uri.getRawPath()))throw invalid();
            return "https://"+uri.getHost().toLowerCase(Locale.ROOT);
        }catch(IllegalArgumentException malformed){throw invalid();}
    }
    private boolean present(String value){return value!=null && !value.isEmpty();}
    static BizException invalid(){return new BizException(FileErrorCode.INVALID_STORAGE_CONFIGURATION);}
}
