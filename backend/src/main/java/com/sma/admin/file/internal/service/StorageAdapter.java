package com.sma.admin.file.internal.service;
import java.io.*;
import java.nio.file.Path;
public interface StorageAdapter {
 record Stored(String key,long sizeBytes,String checksum){}
 Stored put(InputStream input,long maxBytes) throws IOException;
 Path path(String key);
 void remove(String key)throws IOException;
}
