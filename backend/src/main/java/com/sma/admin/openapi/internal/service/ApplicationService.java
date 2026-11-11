package com.sma.admin.openapi.internal.service;
import java.time.Instant;
import java.util.*;
import jakarta.validation.constraints.*;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sma.admin.common.*;
import com.sma.admin.common.security.CurrentUser;
import com.sma.admin.openapi.*;
import com.sma.admin.openapi.internal.persistence.*;

@Service @Transactional(readOnly=true)
public class ApplicationService {
 private static final Set<Integer> TYPES=Set.of(101,1,2,3,4,5,6,7,8,9,10,11,12);
 private final ApplicationMapper mapper;private final SecretCipher secrets;private final MessageSource messages;
 public ApplicationService(ApplicationMapper mapper,SecretCipher secrets,MessageSource messages){this.mapper=mapper;this.secrets=secrets;this.messages=messages;}
 public record Input(@NotBlank @Size(max=128) String name,@NotNull Integer type,@Size(max=200) String description,@NotNull Boolean enabled,@NotNull Boolean signatureRequired){}
 public record Target(@NotBlank @Size(max=64) String type,@NotBlank @Size(max=128) String id,@NotBlank @Size(max=128) String name){}
 public record View(Long id,String name,int type,String description,String appId,boolean enabled,boolean signatureRequired,String targetType,String targetId,String targetName,Instant createdAt,Instant updatedAt){}
 public record Credentials(Long id,String appId,String appSecret){}
 public List<OptionItem> types(){return TYPES.stream().sorted().map(type->new OptionItem(type.longValue(),messages.getMessage("openapi.types."+type,null,LocaleContextHolder.getLocale()),false)).toList();}
 public PageResult<View> page(PageQuery page,String keyword,Integer type,Boolean enabled){var query=new LambdaQueryWrapper<ApplicationEntity>().eq(ApplicationEntity::getPortal,CurrentUser.portal()).orderByDesc(ApplicationEntity::getCreatedAt).orderByDesc(ApplicationEntity::getId);
  query.like(keyword!=null&&!keyword.isBlank(),ApplicationEntity::getName,keyword).eq(type!=null,ApplicationEntity::getType,type).eq(enabled!=null,ApplicationEntity::getEnabled,enabled);
  return PageResult.of(mapper.selectPage(page.toPage(Map.of("createdAt","created_at","name","name")),query),this::view);}
 private ApplicationEntity require(long id,boolean lock){var query=new LambdaQueryWrapper<ApplicationEntity>().eq(ApplicationEntity::getId,id).eq(ApplicationEntity::getPortal,CurrentUser.portal());if(lock)query.last("FOR UPDATE");var row=mapper.selectOne(query);if(row==null)throw new BizException(CommonErrorCode.DATA_NOT_FOUND);return row;}
 public View get(long id){return view(require(id,false));}
 private void apply(ApplicationEntity row,Input input){if(!TYPES.contains(input.type()))throw new BizException(CommonErrorCode.BAD_REQUEST);row.setName(input.name().trim());row.setType(input.type());row.setDescription(input.description());row.setEnabled(input.enabled());row.setSignatureRequired(input.signatureRequired());}
 @Transactional public Credentials create(Input input){var row=new ApplicationEntity();apply(row,input);row.setPortal(CurrentUser.portal());row.setDeptId(CurrentUser.require().departmentId());row.setAppId(UUID.randomUUID().toString().replace("-",""));row.setCredentialVersion(0L);String secret=secrets.randomSecret();row.setSecretCipher(secrets.encrypt(secret,row.getAppId()));
  try{mapper.insert(row);}catch(DuplicateKeyException duplicate){throw new BizException(OpenapiErrorCode.DUPLICATE_NAME);}return new Credentials(row.getId(),row.getAppId(),secret);}
 @Transactional public View update(long id,Input input){var row=require(id,true);apply(row,input);row.setCredentialVersion(row.getCredentialVersion()+1);try{mapper.updateById(row);}catch(DuplicateKeyException duplicate){throw new BizException(OpenapiErrorCode.DUPLICATE_NAME);}return view(row);}
 @Transactional public Credentials rotate(long id){var row=require(id,true);String secret=secrets.randomSecret();row.setSecretCipher(secrets.encrypt(secret,row.getAppId()));row.setCredentialVersion(row.getCredentialVersion()+1);mapper.updateById(row);return new Credentials(row.getId(),row.getAppId(),secret);}
 @Transactional public View link(long id,Target target){var row=require(id,true);row.setTargetType(target.type());row.setTargetId(target.id());row.setTargetName(target.name());mapper.updateById(row);return view(row);}
 @Transactional public View unlink(long id){var row=require(id,true);row.setTargetType(null);row.setTargetId(null);row.setTargetName(null);mapper.updateById(row);return view(row);}
 @Transactional public void delete(long id){var row=require(id,true);if(row.getTargetId()!=null)throw new BizException(OpenapiErrorCode.LINKED);mapper.deleteById(id);}
 private View view(ApplicationEntity row){return new View(row.getId(),row.getName(),row.getType(),row.getDescription(),row.getAppId(),row.getEnabled(),row.getSignatureRequired(),row.getTargetType(),row.getTargetId(),row.getTargetName(),row.getCreatedAt(),row.getUpdatedAt());}
}
