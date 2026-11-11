package com.sma.admin.file.internal.service;

import java.text.Normalizer;
import java.util.*;
import com.sma.admin.common.BizException;
import com.sma.admin.file.*;

/** Display names only. Object paths always use server-generated identifiers. Content verification remains mandatory. */
public final class FileNameRules {
    private FileNameRules(){}
    private static final Set<String> BLOCKED=Set.of("exe","dll","so","jar","class","jsp","jspx","php","php3","php4","php5","phtml","asp","aspx","ashx","sh","bash","bat","cmd","ps1","psm1","vbs","js","mjs","html","htm","xhtml","svg","hta","com","scr","msi","lnk","url","reg","config","htaccess","docm","xlsm","pptm");
    public static String normalize(String raw,FileFormat format){
        if(raw==null || format==null)throw invalid();String name=Normalizer.normalize(raw,Normalizer.Form.NFC);
        if(name.isBlank() || name.codePointCount(0,name.length())>255 || name.length()>510 || !name.equals(name.strip()) || name.endsWith(".") || name.startsWith(".")
                || name.chars().anyMatch(c->Character.isISOControl(c) || Character.getType(c)==Character.FORMAT || "/\\:*?\"<>|".indexOf(c)>=0)
                || name.matches("(?i)(?:CON|PRN|AUX|NUL|COM[1-9]|LPT[1-9])(?:\\..*)?"))throw invalid();
        String[] parts=name.toLowerCase(Locale.ROOT).split("\\.",-1);if(parts.length<2 || Arrays.stream(parts).anyMatch(String::isEmpty))throw invalid();
        for(String part:parts)if(BLOCKED.contains(part))throw invalid();String extension=parts[parts.length-1];
        boolean allowed=switch(format){
            case PNG->extension.equals("png");case JPEG->Set.of("jpg","jpeg").contains(extension);case GIF->extension.equals("gif");case WEBP->extension.equals("webp");
            case MP4->extension.equals("mp4");case PDF->extension.equals("pdf");case DOCX->extension.equals("docx");case XLSX->extension.equals("xlsx");case PPTX->extension.equals("pptx");
            case ZIP->extension.equals("zip");case TEXT->extension.equals("txt");case CSV->extension.equals("csv");case JSON->extension.equals("json");
        };
        if(!allowed)throw invalid();return name;
    }
    private static BizException invalid(){return new BizException(FileErrorCode.INVALID_UPLOAD);}
}
