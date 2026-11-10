package com.sma.admin.file.internal.service;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import javax.imageio.ImageIO;
import org.slf4j.*;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.*;
import org.springframework.web.multipart.MultipartFile;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sma.admin.common.*;
import com.sma.admin.common.security.*;
import com.sma.admin.file.*;
import com.sma.admin.file.internal.persistence.*;

@Service @EnableConfigurationProperties(FileProperties.class) @Transactional(readOnly=true)
public class FileService implements FileQuery {
 private static final Logger log=LoggerFactory.getLogger(FileService.class);
 private static final Set<String> RASTER=Set.of("image/png","image/jpeg","image/gif");
 private final FileMapper mapper;private final FileLookupMapper lookup;private final StorageAdapter storage;private final FileProperties properties;private final List<FileAccessPolicy> policies;
 public FileService(FileMapper mapper,FileLookupMapper lookup,StorageAdapter storage,FileProperties properties,List<FileAccessPolicy> policies){
  this.mapper=mapper;this.lookup=lookup;this.storage=storage;this.properties=properties;this.policies=policies;
 }
 public PageResult<FileView> page(PageQuery page,String keyword,String kind){
  var query=new LambdaQueryWrapper<FileEntity>().eq(FileEntity::getPortal,CurrentUser.portal()).orderByDesc(FileEntity::getId);
  if(keyword!=null&&!keyword.isBlank())query.like(FileEntity::getOriginalName,keyword);if(kind!=null&&!kind.isBlank())query.eq(FileEntity::getKind,kind);
  return PageResult.of(mapper.selectPage(page.toPage(Map.of("name","original_name","size","size_bytes","createdAt","created_at")),query),this::view);
 }
 @Transactional public FileView upload(MultipartFile file){
  var user=CurrentUser.require();
  if(file.isEmpty())throw new BizException(CommonErrorCode.BAD_REQUEST);
  if(file.getSize()>properties.maxBytes())throw new BizException(FileErrorCode.TOO_LARGE,properties.maxBytes());
  final StorageAdapter.Stored object;
  try(var input=file.getInputStream()){object=storage.put(input,properties.maxBytes());}
  catch(LocalStorage.TooLarge tooLarge){throw new BizException(FileErrorCode.TOO_LARGE,properties.maxBytes());}
  catch(IOException failure){log.error("Could not write uploaded asset",failure);throw new BizException(FileErrorCode.STORAGE);}
  TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization(){
   @Override public void afterCompletion(int status){if(status!=STATUS_COMMITTED)try{storage.remove(object.key());}catch(IOException failure){log.error("Could not remove rolled-back asset {}",object.key(),failure);}}
  });
  String type=detect(storage.path(object.key()),file.getContentType());
  var row=new FileEntity();row.setPortal(user.portal());row.setDeptId(user.departmentId());row.setOriginalName(name(file.getOriginalFilename()));
  row.setStorageKey(object.key());row.setContentType(type);row.setKind(RASTER.contains(type)?"IMAGE":type.startsWith("video/")?"VIDEO":"DOCUMENT");
  row.setSizeBytes(object.sizeBytes());row.setChecksum(object.checksum());row.setUploader(user.username());mapper.insert(row);return view(row);
 }
 private String name(String input){
  String value=input==null?"file":input.replace('\\','/');value=value.substring(value.lastIndexOf('/')+1).replaceAll("[\\p{Cntrl}]","_");
  if(value.isBlank())value="file";return value.codePointCount(0,value.length())>255?value.substring(0,value.offsetByCodePoints(0,255)):value;
 }
 private String detect(Path path,String provided){
  try(var image=ImageIO.createImageInputStream(path.toFile())){
   if(image!=null){
    var readers=ImageIO.getImageReaders(image);
    if(readers.hasNext()){var reader=readers.next();try{
     reader.setInput(image);if((long)reader.getWidth(0)*reader.getHeight(0)>25000000L)throw new BizException(FileErrorCode.INVALID_IMAGE);
     if(reader.read(0)==null)throw new BizException(FileErrorCode.INVALID_IMAGE);
     String format=reader.getFormatName().toLowerCase(Locale.ROOT);
     return switch(format){case "png"->"image/png";case "jpg","jpeg"->"image/jpeg";case "gif"->"image/gif";default->"application/octet-stream";};
    }finally{reader.dispose();}}
   }
  }catch(IOException failure){throw new BizException(FileErrorCode.INVALID_IMAGE);}
  if(provided!=null&&RASTER.contains(provided.toLowerCase(Locale.ROOT)))throw new BizException(FileErrorCode.INVALID_IMAGE);
  return provided!=null&&provided.length()<=128&&provided.matches("[a-zA-Z0-9.+-]+/[a-zA-Z0-9.+-]+")?provided.toLowerCase(Locale.ROOT):"application/octet-stream";
 }
 private FileEntity require(long id){
  var row=lookup.selectOne(new LambdaQueryWrapper<FileEntity>().eq(FileEntity::getId,id).eq(FileEntity::getPortal,CurrentUser.portal()));
  if(row==null)throw new BizException(CommonErrorCode.DATA_NOT_FOUND);return row;
 }
 private void readable(FileEntity row){
  var user=CurrentUser.require();
  if(row.getCreatedBy()==user.userId())return;
  if((user.superAdmin()||user.permissions().contains("file:file:view"))&&mapper.selectById(row.getId())!=null)return;
  if(policies.stream().anyMatch(policy->policy.canRead(row.getId(),user)))return;
  throw new AccessDeniedException("Asset is outside the current access grant");
 }
 @Override public FileView metadata(long id){var row=require(id);readable(row);return view(row);}
 @Override @Transactional public FileView requireOwned(long id){var row=lookup.selectOne(new LambdaQueryWrapper<FileEntity>().eq(FileEntity::getId,id).eq(FileEntity::getPortal,CurrentUser.portal()).last("FOR UPDATE"));if(row==null)throw new BizException(CommonErrorCode.DATA_NOT_FOUND);if(row.getCreatedBy()!=CurrentUser.require().userId())throw new AccessDeniedException("Not your asset");return view(row);}
 @Transactional public FileView uploadImage(MultipartFile file){var result=upload(file);if(!RASTER.contains(result.contentType()))throw new BizException(FileErrorCode.INVALID_IMAGE);return result;}
 @Override @Transactional public FileView requireOwnedImage(long id){var result=requireOwned(id);if(!RASTER.contains(result.contentType()))throw new BizException(FileErrorCode.INVALID_IMAGE);return result;}
 public record Download(Path path,String name,String contentType,long sizeBytes,boolean previewable){}
 public Download content(long id)throws NoSuchFileException{
  FileEntity row;
  try{row=require(id);}catch(BizException missing){throw new NoSuchFileException(Long.toString(id));}
  readable(row);Path path=storage.path(row.getStorageKey());if(!Files.isRegularFile(path))throw new NoSuchFileException(Long.toString(id));
  return new Download(path,row.getOriginalName(),row.getContentType(),row.getSizeBytes(),RASTER.contains(row.getContentType()));
 }
 @Transactional public void delete(long id){
  var row=mapper.selectOne(new LambdaQueryWrapper<FileEntity>().eq(FileEntity::getId,id).eq(FileEntity::getPortal,CurrentUser.portal()).last("FOR UPDATE"));
  if(row==null)throw new BizException(CommonErrorCode.DATA_NOT_FOUND);
  if(policies.stream().anyMatch(policy->policy.inUse(id)))throw new BizException(FileErrorCode.IN_USE);
  mapper.deleteById(id); // Retain the binary so deletion remains recoverable; downloads cannot access deleted metadata.
 }
 private FileView view(FileEntity row){return new FileView(row.getId(),row.getOriginalName(),row.getContentType(),row.getKind(),row.getSizeBytes(),row.getChecksum(),
  row.getCreatedBy(),row.getUploader(),row.getCreatedAt(),RASTER.contains(row.getContentType()),"/api/file/files/"+row.getId()+"/content");}
}
