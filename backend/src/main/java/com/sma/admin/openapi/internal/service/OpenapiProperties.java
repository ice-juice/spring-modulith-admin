package com.sma.admin.openapi.internal.service;
import org.springframework.boot.context.properties.ConfigurationProperties;
@ConfigurationProperties("sma.openapi")
public record OpenapiProperties(String masterKey,String keyFile){public OpenapiProperties{keyFile=keyFile==null||keyFile.isBlank()?"data/keys/openapi.key":keyFile;}}
