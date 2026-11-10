package com.sma.admin.file.internal.service;
import org.springframework.boot.context.properties.ConfigurationProperties;
@ConfigurationProperties("sma.file")
public record FileProperties(String directory,long maxBytes) {
 public FileProperties {directory=directory==null||directory.isBlank()?"data/uploads":directory;maxBytes=maxBytes<=0?20L*1024*1024:maxBytes;}
}
