package com.sma.admin.file;

/** Authenticated, same-tenant image export for module-owned immutable public snapshots. Never grants anonymous file access. */
public interface ImagePublication {
    Image prepare(long fileId);
    record Image(byte[] bytes,String contentType,String checksum,int width,int height) {
        public Image { bytes=bytes.clone(); }
        @Override public byte[] bytes() { return bytes.clone(); }
        @Override public String toString() { return "Image[contentType="+contentType+", bytes="+bytes.length+"]"; }
    }
}
