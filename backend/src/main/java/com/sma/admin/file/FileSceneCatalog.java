package com.sma.admin.file;

import java.util.Collection;
import java.util.Set;

/** Static registrations belong to their real platform/business module; tenant configuration may only narrow them. */
public interface FileSceneCatalog {
    Collection<Definition> scenes();
    record Definition(String application,String code,String nameKey,long maximumBytes,Set<FileFormat> formats,Set<UploadMode> modes){
        public Definition{
            if(application==null || !application.matches("[a-z][a-z0-9.-]{0,63}") || code==null || !code.matches("[a-z][a-z0-9.-]{0,95}")
                    || !code.startsWith(application+".") || nameKey==null || !nameKey.startsWith(application+".") || nameKey.length()>128 || maximumBytes<1
                    || formats==null || formats.isEmpty() || modes==null || modes.isEmpty())throw new IllegalArgumentException("Invalid file scene registration");
            formats=Set.copyOf(formats);modes=Set.copyOf(modes);
        }
    }
}
