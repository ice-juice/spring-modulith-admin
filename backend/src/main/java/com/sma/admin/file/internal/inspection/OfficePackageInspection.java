package com.sma.admin.file.internal.inspection;

import java.io.*;
import java.net.URI;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Pattern;
import javax.xml.XMLConstants;
import javax.xml.parsers.SAXParserFactory;
import org.xml.sax.*;
import org.xml.sax.helpers.DefaultHandler;
import com.sma.admin.file.FileFormat;

/** Worker-only OPC/OOXML structure and active-content checks. Returned media still needs full decoding and malware scanning. */
final class OfficePackageInspection {
    private static final String TYPES="http://schemas.openxmlformats.org/package/2006/content-types",RELS="http://schemas.openxmlformats.org/package/2006/relationships";
    private static final String OFFICE="http://schemas.openxmlformats.org/officeDocument/2006/relationships/";
    private static final Map<FileFormat,String> MAIN=Map.of(FileFormat.DOCX,"word/document.xml",FileFormat.XLSX,"xl/workbook.xml",FileFormat.PPTX,"ppt/presentation.xml");
    private static final Map<FileFormat,String> MAIN_TYPE=Map.of(FileFormat.DOCX,"application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml",
            FileFormat.XLSX,"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml",FileFormat.PPTX,"application/vnd.openxmlformats-officedocument.presentationml.presentation.main+xml");
    private static final Map<FileFormat,String> MAIN_NAMESPACE=Map.of(FileFormat.DOCX,"http://schemas.openxmlformats.org/wordprocessingml/2006/main",
            FileFormat.XLSX,"http://schemas.openxmlformats.org/spreadsheetml/2006/main",FileFormat.PPTX,"http://schemas.openxmlformats.org/presentationml/2006/main");
    private static final Map<FileFormat,String> MAIN_ROOT=Map.of(FileFormat.DOCX,"document",FileFormat.XLSX,"workbook",FileFormat.PPTX,"presentation");
    private static final Set<String> RELATIONSHIPS=Set.of("officeDocument","styles","settings","webSettings","fontTable","theme","numbering","header","footer","footnotes","endnotes","comments",
            "image","hyperlink","worksheet","sharedStrings","calcChain","drawing","chart","chartStyle","chartColorStyle","table","pivotTable","pivotCacheDefinition","pivotCacheRecords",
            "slide","slideLayout","slideMaster","notesSlide","notesMaster","handoutMaster","presProps","viewProps","tableStyles","extended-properties","custom-properties");
    private static final Set<String> ACTIVE_ELEMENTS=Set.of("oleobject","object","control","controls","altchunk","externaldata","webextension","ddelink","olelink","connection","externalreference","script","macro");
    private static final Set<String> SAFE_FUNCTIONS=Set.of("SUM","AVERAGE","MIN","MAX","COUNT","COUNTA","IF","IFERROR","AND","OR","NOT","ROUND","ROUNDUP","ROUNDDOWN","ABS","INT","MOD","SQRT",
            "SUMIF","SUMIFS","COUNTIF","COUNTIFS","AVERAGEIF","AVERAGEIFS","VLOOKUP","HLOOKUP","XLOOKUP","INDEX","MATCH","LEN","LEFT","RIGHT","MID","TRIM","CONCAT","CONCATENATE","TEXT","VALUE","DATE","YEAR","MONTH","DAY","TODAY","NOW","TRUE","FALSE");
    private static final Pattern FUNCTION=Pattern.compile("([A-Za-z_][A-Za-z0-9_.]*)\\s*\\(");
    record Package(List<ArchiveInspection.Entry> media){Package{media=List.copyOf(media);}}
    private OfficePackageInspection(){}
    static Package inspect(Path source,FileFormat format,ArchiveInspection.Manifest manifest)throws IOException{
        if(!MAIN.containsKey(format))throw rejected();var parts=new HashMap<String,ArchiveInspection.Entry>();
        for(var entry:manifest.entries())if(!entry.directory()){
            String lower=entry.name().toLowerCase(Locale.ROOT);
            if(lower.contains("vbaproject") || lower.contains("/activex/") || lower.contains("/embeddings/") || lower.contains("/externallinks/")
                    || lower.contains("/macrosheets/") || lower.contains("/querytables/") || lower.endsWith("connections.xml") || lower.startsWith("customui/"))throw rejected();
            parts.put(entry.name(),entry);
        }
        if(!parts.keySet().containsAll(Set.of("[Content_Types].xml","_rels/.rels",MAIN.get(format))))throw rejected();
        var defaults=new HashMap<String,String>();var overrides=new HashMap<String,String>();var media=new ArrayList<ArchiveInspection.Entry>();
        try(var zip=org.apache.commons.compress.archivers.zip.ZipFile.builder().setPath(source).get()){
            parse(zip,parts.get("[Content_Types].xml"),new Guard(){
                @Override void element(String namespace,String name,Attributes attrs)throws SAXException{
                    if(depth==1){require(namespace.equals(TYPES)&&name.equals("Types"));return;}
                    require(depth==2 && namespace.equals(TYPES));
                    if(name.equals("Default")){
                        String extension=value(attrs,"Extension"),type=value(attrs,"ContentType");require(extension.matches("[A-Za-z0-9]{1,16}") && defaults.putIfAbsent(extension.toLowerCase(Locale.ROOT),type)==null);
                    }else if(name.equals("Override")){
                        String part=value(attrs,"PartName"),type=value(attrs,"ContentType");require(part.startsWith("/") && !part.startsWith("//"));
                        String resolved=resolve("_rels/.rels",part);require(parts.containsKey(resolved) && overrides.putIfAbsent(resolved,type)==null);
                    }else throw invalid();
                }
            });
            for(var part:parts.values()){
                if(part.name().equals("[Content_Types].xml"))continue;String extension=extension(part.name());String type=overrides.getOrDefault(part.name(),defaults.get(extension));
                if(type==null || type.length()>256 || type.toLowerCase(Locale.ROOT).matches(".*(?:macro|vba|activex|oleobject|msdownload).*"))throw rejected();
                if(part.name().equals(MAIN.get(format)) && !type.equals(MAIN_TYPE.get(format)))throw rejected();
                if(extension.equals("rels")){
                    if(!type.equals("application/vnd.openxmlformats-package.relationships+xml"))throw rejected();relationships(zip,part,parts,MAIN.get(format));
                }else if(extension.equals("xml")){
                    if(!type.equals("application/xml") && !(type.startsWith("application/vnd.openxmlformats-") && type.endsWith("+xml")))throw rejected();
                    parse(zip,part,new Guard(){
                        private StringBuilder instructions;private int instructionDepth;
                        @Override void element(String namespace,String name,Attributes attrs)throws SAXException{
                            require(!ACTIVE_ELEMENTS.contains(name.toLowerCase(Locale.ROOT)));
                            for(int i=0;i<attrs.getLength();i++)if(Set.of("action","macro","script").contains(attrs.getLocalName(i).toLowerCase(Locale.ROOT)))require(attrs.getValue(i).isBlank());
                            if(depth==1 && part.name().equals(MAIN.get(format)))require(namespace.equals(MAIN_NAMESPACE.get(format)) && name.equals(MAIN_ROOT.get(format)));
                            if(name.equals("instrText") || name.equals("f") || name.equals("definedName")){require(instructions==null);instructions=new StringBuilder();instructionDepth=depth;}
                            if(name.equals("fldSimple"))for(int i=0;i<attrs.getLength();i++)if(attrs.getLocalName(i).equals("instr"))field(attrs.getValue(i));
                        }
                        @Override public void characters(char[] chars,int start,int length)throws SAXException{
                            super.characters(chars,start,length);if(instructions!=null){require(instructions.length()+length<=65536);instructions.append(chars,start,length);}
                        }
                        @Override public void endElement(String namespace,String name,String qName)throws SAXException{
                            if(instructions!=null && depth==instructionDepth){if(name.equals("instrText"))field(instructions.toString());else formula(instructions.toString());instructions=null;}
                            super.endElement(namespace,name,qName);
                        }
                    });
                }else{
                    String expected=switch(extension){case "png"->"image/png";case "jpg","jpeg"->"image/jpeg";case "gif"->"image/gif";case "webp"->"image/webp";default->throw rejected();};
                    if(!type.equals(expected))throw rejected();media.add(part);
                }
            }
        }return new Package(media);
    }
    private static void relationships(org.apache.commons.compress.archivers.zip.ZipFile zip,ArchiveInspection.Entry part,Map<String,ArchiveInspection.Entry> parts,String main)throws IOException{
        String name=part.name();boolean root=name.equals("_rels/.rels");
        if(!root){int marker=name.lastIndexOf("/_rels/");if(marker<0 || !name.endsWith(".rels") || !parts.containsKey(name.substring(0,marker+1)+name.substring(marker+7,name.length()-5)))throw rejected();}
        var ids=new HashSet<String>();int[] mainRelationships={0};
        parse(zip,part,new Guard(){
            @Override void element(String namespace,String local,Attributes attrs)throws SAXException{
                if(depth==1){require(namespace.equals(RELS)&&local.equals("Relationships"));return;}
                require(depth==2 && namespace.equals(RELS) && local.equals("Relationship"));String id=value(attrs,"Id"),type=value(attrs,"Type"),target=value(attrs,"Target");
                String mode=attrs.getValue("TargetMode");require(mode==null || mode.equals("Internal"));require(ids.add(id));
                boolean office=type.startsWith(OFFICE)&&RELATIONSHIPS.contains(type.substring(OFFICE.length()));
                require(office || type.equals("http://schemas.openxmlformats.org/package/2006/relationships/metadata/core-properties")
                        || type.equals("http://schemas.openxmlformats.org/package/2006/relationships/metadata/thumbnail"));
                String resolved=resolve(name,target);require(parts.containsKey(resolved));
                if(type.equals(OFFICE+"officeDocument")){require(root && resolved.equals(main));mainRelationships[0]++;}
            }
        });
        if(root && mainRelationships[0]!=1)throw rejected();
    }
    private static String resolve(String relationship,String target)throws SAXException{
        try{
            var uri=new URI(target);if(uri.isAbsolute() || uri.getRawAuthority()!=null || uri.getRawQuery()!=null || uri.getRawFragment()!=null)throw invalid();
            String path=uri.getPath();if(path==null || path.isBlank() || path.contains("\\") || path.contains("%") || path.contains(":"))throw invalid();
            var segments=new ArrayDeque<String>();
            if(!path.startsWith("/") && !relationship.equals("_rels/.rels")){
                int marker=relationship.lastIndexOf("/_rels/");if(marker<0)throw invalid();segments.addAll(Arrays.asList(relationship.substring(0,marker).split("/")));
            }
            for(String segment:path.split("/",-1)){
                if(segment.isEmpty() || segment.equals("."))continue;
                if(segment.equals("..")){if(segments.isEmpty())throw invalid();segments.removeLast();}
                else{if(segment.codePoints().anyMatch(c->Character.isISOControl(c)||Character.getType(c)==Character.FORMAT))throw invalid();segments.addLast(segment);}
            }
            return String.join("/",segments);
        }catch(java.net.URISyntaxException invalid){throw invalid();}
    }
    private static void field(String value)throws SAXException{
        String normalized=value.strip().toUpperCase(Locale.ROOT);String command=normalized.split("\\s+",2)[0];
        require(Set.of("PAGE","NUMPAGES","SECTION","SECTIONPAGES","DATE","TIME","AUTHOR","TITLE","SUBJECT","TOC","REF","PAGEREF","SEQ").contains(command));
        require(!normalized.contains("\\\\") && !normalized.contains("://"));
    }
    private static void formula(String value)throws SAXException{
        require(value.length()<=8192 && !value.matches("(?s).*[\\[\\]\\\\|@].*") && !value.contains("://") && value.codePoints().noneMatch(Character::isISOControl));
        var match=FUNCTION.matcher(value);while(match.find())require(SAFE_FUNCTIONS.contains(match.group(1).toUpperCase(Locale.ROOT)));
    }
    private static void parse(org.apache.commons.compress.archivers.zip.ZipFile zip,ArchiveInspection.Entry entry,Guard handler)throws IOException{
        if(entry==null || entry.size()>8L*1024*1024)throw new ArchiveInspection.Capacity();
        try(var input=zip.getInputStream(zip.getEntry(entry.name()))){
            var factory=SAXParserFactory.newInstance();factory.setNamespaceAware(true);factory.setXIncludeAware(false);factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING,true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl",true);factory.setFeature("http://xml.org/sax/features/external-general-entities",false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities",false);var parser=factory.newSAXParser();
            parser.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD,"");parser.setProperty(XMLConstants.ACCESS_EXTERNAL_SCHEMA,"");
            parser.parse(input,handler);
        }catch(javax.xml.parsers.ParserConfigurationException | SAXException | RuntimeException invalid){throw rejected();}
    }
    private abstract static class Guard extends DefaultHandler {
        int depth,nodes;long characters;
        @Override public final void startElement(String namespace,String name,String qName,Attributes attrs)throws SAXException{
            require(++depth<=64 && ++nodes<=100000 && attrs.getLength()<=64);
            for(int i=0;i<attrs.getLength();i++)require(attrs.getValue(i).length()<=8192);element(namespace,name,attrs);
        }
        abstract void element(String namespace,String name,Attributes attrs)throws SAXException;
        @Override public void endElement(String namespace,String name,String qName)throws SAXException{depth--;}
        @Override public void characters(char[] text,int start,int length)throws SAXException{characters+=length;require(characters<=8L*1024*1024);}
        @Override public void processingInstruction(String target,String data)throws SAXException{throw invalid();}
        @Override public InputSource resolveEntity(String publicId,String systemId)throws SAXException{throw invalid();}
        @Override public void error(SAXParseException exception)throws SAXException{throw invalid();}
        @Override public void fatalError(SAXParseException exception)throws SAXException{throw invalid();}
    }
    private static String value(Attributes attrs,String name)throws SAXException{String value=attrs.getValue(name);require(value!=null && !value.isBlank() && value.length()<=2048);return value;}
    private static String extension(String name){int dot=name.lastIndexOf('.');return dot<0?"":name.substring(dot+1).toLowerCase(Locale.ROOT);}
    private static void require(boolean allowed)throws SAXException{if(!allowed)throw invalid();}
    private static SAXException invalid(){return new SAXException("Office package rejected");}
    private static IOException rejected(){return new IOException("Office package rejected");}
}
