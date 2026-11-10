package com.sma.admin.file;
public interface FileQuery {
 FileView metadata(long id);
 FileView requireOwned(long id);
 FileView requireOwnedImage(long id);
}
