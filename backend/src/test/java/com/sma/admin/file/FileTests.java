package com.sma.admin.file;
import static org.assertj.core.api.Assertions.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import com.sma.admin.common.*;
import com.sma.admin.common.security.*;
import com.sma.admin.file.internal.service.*;
import com.sma.admin.support.*;

@IntegrationTest @AutoConfigureMockMvc
class FileTests {
 @Autowired FileService service;@Autowired LocalStorage storage;@Autowired MockMvcTester mvc;@Autowired FileProperties properties;
 @Test void generatedStorageKeysPreventTraversalAndAssetsAreTenantAndOwnerScoped(){
  var actor=new SmaPrincipal(101,"uploader",221,"admin",10L,false,DataAccess.own(),Set.of("file:file:view","file:file:upload","file:file:delete"),"UTC");
  Users.as(actor,()->{
   var file=service.upload(new MockMultipartFile("file","../../escape.txt","text/plain","private-data".getBytes(StandardCharsets.UTF_8)));
   assertThat(file.name()).isEqualTo("escape.txt");assertThat(file.sizeBytes()).isEqualTo(12);assertThat(file.checksum()).hasSize(64);
   assertThat(service.page(new PageQuery(1,20,null,null),null,null).total()).isEqualTo(1);
   Users.as(Users.principal(202,221,DataAccess.own()),()->{assertThatThrownBy(()->service.metadata(file.id())).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);return null;});
   Users.as(Users.principal(101,222,DataAccess.unrestricted()),()->{assertThatThrownBy(()->service.metadata(file.id())).isInstanceOf(BizException.class);return null;});
   Users.as(actor,()->{
    assertThat(mvc.get().uri("/api/file/files/"+file.id()+"/content")).hasStatusOk().hasHeader("X-Content-Type-Options","nosniff");return null;
   });
   service.delete(file.id());assertThatThrownBy(()->service.metadata(file.id())).isInstanceOf(BizException.class);
   return null;
  });
  assertThatThrownBy(()->storage.path("../escape")).isInstanceOf(IllegalArgumentException.class);
 }
 @Test void oversizedAndEmptyUploadsAreRejected(){
  Users.as(Users.principal(101,223,DataAccess.unrestricted()),()->{
   assertThatThrownBy(()->service.upload(new MockMultipartFile("file","empty.txt","text/plain",new byte[0]))).isInstanceOf(BizException.class);
   assertThatThrownBy(()->service.upload(new MockMultipartFile("file","large.bin","application/octet-stream",new byte[20*1024*1024+1]))).isInstanceOf(BizException.class);
   return null;
  });
 }
 @Test void forgedAndTruncatedRasterUploadsRollbackMetadataAndBinary()throws Exception{
  java.nio.file.Path root=java.nio.file.Path.of(properties.directory());
  long before;try(var paths=java.nio.file.Files.list(root)){before=paths.count();}
  Users.as(Users.principal(101,225,DataAccess.unrestricted()),()->{
   assertThatThrownBy(()->service.upload(new MockMultipartFile("file","fake.png","image/png","not an image".getBytes(StandardCharsets.UTF_8))))
    .isInstanceOf(BizException.class);
   byte[] partial=Base64.getDecoder().decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwC");
   assertThatThrownBy(()->service.upload(new MockMultipartFile("file","partial.png","image/png",partial))).isInstanceOf(BizException.class);
   assertThatThrownBy(()->service.uploadImage(new MockMultipartFile("file","avatar.txt","text/plain",new byte[]{1,2,3}))).isInstanceOf(BizException.class);
   assertThat(service.page(new PageQuery(1,20,null,null),null,null).total()).isZero();return null;
  });
  try(var paths=java.nio.file.Files.list(root)){assertThat(paths.count()).isEqualTo(before);}
 }
}
