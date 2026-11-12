package com.sma.admin.file.internal.inspection;

import static org.assertj.core.api.Assertions.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;
import com.sma.admin.file.FileFormat;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class OfficePackageInspectionTests {
    @TempDir Path directory;
    private static final String RELS="http://schemas.openxmlformats.org/package/2006/relationships",OFFICE="http://schemas.openxmlformats.org/officeDocument/2006/relationships/";
    private Map<String,String> parts(FileFormat format){
        String root=switch(format){case DOCX->"word/document.xml";case XLSX->"xl/workbook.xml";case PPTX->"ppt/presentation.xml";default->throw new IllegalArgumentException();};
        String main=switch(format){case DOCX->"wordprocessingml.document";case XLSX->"spreadsheetml.sheet";case PPTX->"presentationml.presentation";default->throw new IllegalArgumentException();};
        String namespace=switch(format){case DOCX->"wordprocessingml";case XLSX->"spreadsheetml";case PPTX->"presentationml";default->throw new IllegalArgumentException();};
        String element=switch(format){case DOCX->"document";case XLSX->"workbook";case PPTX->"presentation";default->throw new IllegalArgumentException();};
        var parts=new LinkedHashMap<String,String>();parts.put("[Content_Types].xml","<Types xmlns='http://schemas.openxmlformats.org/package/2006/content-types'>"
                +"<Default Extension='rels' ContentType='application/vnd.openxmlformats-package.relationships+xml'/><Default Extension='xml' ContentType='application/xml'/>"
                +"<Override PartName='/"+root+"' ContentType='application/vnd.openxmlformats-officedocument."+main+".main+xml'/></Types>");
        parts.put("_rels/.rels",relations("<Relationship Id='root' Type='"+OFFICE+"officeDocument' Target='"+root+"'/>"));
        parts.put(root,"<"+element+" xmlns='http://schemas.openxmlformats.org/"+namespace+"/2006/main'/>");return parts;
    }
    private String relations(String body){return "<Relationships xmlns='"+RELS+"'>"+body+"</Relationships>";}
    private Path zip(Map<String,String> parts)throws Exception{
        Path path=directory.resolve(UUID.randomUUID()+".zip");try(var out=new ZipOutputStream(Files.newOutputStream(path))){
            for(var part:parts.entrySet()){out.putNextEntry(new ZipEntry(part.getKey()));out.write(part.getValue().getBytes(StandardCharsets.UTF_8));out.closeEntry();}
        }return path;
    }
    private OfficePackageInspection.Package inspect(FileFormat format,Map<String,String> parts)throws Exception{Path path=zip(parts);return OfficePackageInspection.inspect(path,format,ArchiveInspection.inspect(path));}
    @Test void minimalDocxXlsxPptxPackagesAndInternalRelationshipsAreRecognized()throws Exception{
        for(var format:List.of(FileFormat.DOCX,FileFormat.XLSX,FileFormat.PPTX))assertThat(inspect(format,parts(format)).media()).isEmpty();
        var doc=parts(FileFormat.DOCX);doc.put("word/styles.xml","<w:styles xmlns:w='http://schemas.openxmlformats.org/wordprocessingml/2006/main'/>");
        doc.put("word/_rels/document.xml.rels",relations("<Relationship Id='styles' Type='"+OFFICE+"styles' Target='styles.xml'/>"));assertThat(inspect(FileFormat.DOCX,doc).media()).isEmpty();
    }
    @Test void disguisedMacroUnknownPartTypeOrWrongMainDocumentCannotPass()throws Exception{
        var wrongType=parts(FileFormat.DOCX);wrongType.put("[Content_Types].xml",wrongType.get("[Content_Types].xml").replace("wordprocessingml.document.main+xml","ms-word.document.macroEnabled.main+xml"));
        assertThatThrownBy(()->inspect(FileFormat.DOCX,wrongType)).isInstanceOf(IOException.class);
        for(String path:List.of("word/vbaProject.bin","word/embeddings/payload.bin","word/activeX/control.xml","xl/externalLinks/externalLink.xml","arbitrary.exe")){
            var malicious=parts(FileFormat.DOCX);malicious.put(path,"payload");assertThatThrownBy(()->inspect(FileFormat.DOCX,malicious)).isInstanceOf(IOException.class);
        }
        assertThatThrownBy(()->inspect(FileFormat.XLSX,parts(FileFormat.DOCX))).isInstanceOf(IOException.class);
        var root=parts(FileFormat.DOCX);root.put("word/document.xml","<evil/>");assertThatThrownBy(()->inspect(FileFormat.DOCX,root)).isInstanceOf(IOException.class);
    }
    @Test void externalTargetsTraversalDanglingAndUnknownRelationshipsAreRejected()throws Exception{
        for(String target:List.of("https://evil.invalid/payload","file:///etc/passwd","../word/document.xml","%2e%2e/word/document.xml","word/missing.xml")){
            var malicious=parts(FileFormat.DOCX);malicious.put("_rels/.rels",relations("<Relationship Id='root' Type='"+OFFICE+"officeDocument' Target='"+target+"'/>"));
            assertThatThrownBy(()->inspect(FileFormat.DOCX,malicious)).as(target).isInstanceOf(IOException.class);
        }
        var external=parts(FileFormat.DOCX);external.put("_rels/.rels",external.get("_rels/.rels").replace("Target=","TargetMode='External' Target="));
        assertThatThrownBy(()->inspect(FileFormat.DOCX,external)).isInstanceOf(IOException.class);
        var unknown=parts(FileFormat.DOCX);unknown.put("word/_rels/document.xml.rels",relations("<Relationship Id='x' Type='"+OFFICE+"attachedTemplate' Target='document.xml'/>"));
        assertThatThrownBy(()->inspect(FileFormat.DOCX,unknown)).isInstanceOf(IOException.class);
    }
    @Test void xmlCannotResolveEntitiesOrStylesheetsAndResourceDepthIsBounded()throws Exception{
        Path secret=directory.resolve("private-fixture.txt");Files.writeString(secret,"fixture must not be read");
        for(String xml:List.of("<!DOCTYPE document [<!ENTITY x SYSTEM '"+secret.toUri()+"'>]><document>&x;</document>",
                "<?xml-stylesheet href='https://evil.invalid/x' type='text/xsl'?><document/>","<a>".repeat(65)+"x"+"</a>".repeat(65))){
            var malicious=parts(FileFormat.DOCX);malicious.put("word/extra.xml",xml);assertThatThrownBy(()->inspect(FileFormat.DOCX,malicious)).isInstanceOf(IOException.class);
        }
    }
    @Test void activeOfficeElementsDdeAndExternalFormulaFunctionsAreRejected()throws Exception{
        for(String payload:List.of("<w:altChunk/>","<w:object/>","<w:instrText>DDEAUTO cmd \\c calc</w:instrText>","<w:fldSimple w:instr='INCLUDETEXT remote'/>")){
            var malicious=parts(FileFormat.DOCX);malicious.put("word/document.xml","<w:document xmlns:w='http://schemas.openxmlformats.org/wordprocessingml/2006/main'>"+payload+"</w:document>");
            assertThatThrownBy(()->inspect(FileFormat.DOCX,malicious)).isInstanceOf(IOException.class);
        }
        for(String formula:List.of("WEBSERVICE(\"https://evil.invalid\")","DDE(\"cmd\")","[remote.xlsx]Sheet1!A1","cmd|'calc'!A0","EVILUDF(1)")){
            var malicious=parts(FileFormat.XLSX);malicious.put("xl/sheet.xml","<worksheet xmlns='http://schemas.openxmlformats.org/spreadsheetml/2006/main'><f>"+formula+"</f></worksheet>");
            assertThatThrownBy(()->inspect(FileFormat.XLSX,malicious)).isInstanceOf(IOException.class);
        }
        var safe=parts(FileFormat.XLSX);safe.put("xl/sheet.xml","<worksheet xmlns='http://schemas.openxmlformats.org/spreadsheetml/2006/main'><f>SUM(A1:A4)</f><f>IF(B1&gt;0,1,0)</f></worksheet>");
        assertThat(inspect(FileFormat.XLSX,safe).media()).isEmpty();
        var presentation=parts(FileFormat.PPTX);presentation.put("ppt/action.xml","<a:hlinkClick xmlns:a='http://schemas.openxmlformats.org/drawingml/2006/main' action='ppaction://program'/>");
        assertThatThrownBy(()->inspect(FileFormat.PPTX,presentation)).isInstanceOf(IOException.class);
    }
    @Test void uninspectedMediaIsExplicitlyReturnedInsteadOfMistakenForSafeContent()throws Exception{
        var doc=parts(FileFormat.DOCX);doc.put("[Content_Types].xml",doc.get("[Content_Types].xml").replace("</Types>","<Default Extension='png' ContentType='image/png'/></Types>"));
        doc.put("word/media/picture.png","not an actual image");var media=inspect(FileFormat.DOCX,doc).media();assertThat(media).hasSize(1);assertThat(media.getFirst().name()).isEqualTo("word/media/picture.png");
        // Package structure alone is intentionally not wired to worker PASS: the next gate must reject this payload.
    }
}
