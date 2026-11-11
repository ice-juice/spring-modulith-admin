package com.sma.admin.file.internal.service;

import static org.assertj.core.api.Assertions.*;
import java.util.*;
import com.sma.admin.common.BizException;
import com.sma.admin.file.StorageProvider;
import org.junit.jupiter.api.Test;

class StorageProfileRulesTests {
    private final StorageProfileRules rules=new StorageProfileRules(new StorageSettings(Map.of("default","data/managed-files")));
    @Test void providerLocationsAreExplicitAndNeverAcceptArbitraryEgressEndpoints(){
        assertThat(rules.location(StorageProvider.S3,null,"us-east-1",null,"tenant-bucket","assets").endpoint()).isEqualTo("https://s3.us-east-1.amazonaws.com");
        assertThat(rules.location(StorageProvider.S3,null,"cn-north-1",null,"tenant-bucket",null).endpoint()).isEqualTo("https://s3.cn-north-1.amazonaws.com.cn");
        assertThat(rules.location(StorageProvider.OSS,null,"cn-hangzhou",null,"tenant-bucket",null).endpoint()).isEqualTo("https://oss-cn-hangzhou.aliyuncs.com");
        String r2="https://"+"a".repeat(32)+".r2.cloudflarestorage.com";assertThat(rules.location(StorageProvider.R2,null,null,r2,"tenant-bucket",null).region()).isEqualTo("auto");
        for(String endpoint:List.of("http://s3.us-east-1.amazonaws.com","https://169.254.169.254","https://evil.example","https://s3.us-east-1.amazonaws.com.evil.example",
                "https://user@s3.us-east-1.amazonaws.com","https://s3.us-east-1.amazonaws.com:443","https://s3.us-east-1.amazonaws.com/path","https://s3.us-east-1.amazonaws.com?x=1"))
            assertThatThrownBy(()->rules.location(StorageProvider.S3,null,"us-east-1",endpoint,"tenant-bucket",null)).isInstanceOf(BizException.class);
        assertThatThrownBy(()->rules.location(StorageProvider.R2,null,"us-east-1",r2,"tenant-bucket",null)).isInstanceOf(BizException.class);
        assertThatThrownBy(()->rules.location(StorageProvider.OSS,null,"cn-hangzhou",null,"bucket.with.dots",null)).isInstanceOf(BizException.class);
    }
    @Test void localPathsPrefixesAndProviderNamespacesCannotEscapeDeploymentRoots(){
        assertThat(rules.location(StorageProvider.LOCAL,"default",null,null,null,"images/public").localRoot()).isEqualTo("default");
        for(String root:List.of("../outside","C:\\Windows","/etc","unregistered"))assertThatThrownBy(()->rules.location(StorageProvider.LOCAL,root,null,null,null,null)).isInstanceOf(BizException.class);
        for(String prefix:List.of("../x","a//b","a\\b","/root","CON","folder/NUL.txt","folder.","a/%2e%2e","a:stream"))
            assertThatThrownBy(()->rules.location(StorageProvider.LOCAL,"default",null,null,null,prefix)).isInstanceOf(BizException.class);
        assertThatThrownBy(()->rules.location(StorageProvider.LOCAL,"default","us-east-1",null,null,null)).isInstanceOf(BizException.class);
        assertThatThrownBy(()->rules.location(StorageProvider.S3,"default","us-east-1",null,"tenant-bucket",null)).isInstanceOf(BizException.class);
        assertThatThrownBy(()->rules.location(StorageProvider.S3,null,"us-east-1",null,"bucket--x-s3",null)).isInstanceOf(BizException.class);
        assertThat(List.of(StorageProvider.values())).containsExactly(StorageProvider.LOCAL,StorageProvider.OSS,StorageProvider.S3,StorageProvider.R2);
    }
}
