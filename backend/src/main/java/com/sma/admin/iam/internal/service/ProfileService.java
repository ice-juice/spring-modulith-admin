package com.sma.admin.iam.internal.service;
import jakarta.validation.constraints.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.sma.admin.common.*;
import com.sma.admin.common.security.*;
import com.sma.admin.file.FileQuery;
import com.sma.admin.iam.internal.persistence.*;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;

@Service @Transactional(readOnly=true)
public class ProfileService {
 private final AccountMapper accounts;private final FileQuery files;
 public ProfileService(AccountMapper accounts,FileQuery files){this.accounts=accounts;this.files=files;}
 public record Input(@NotBlank @Size(max=128) String name,@Email @Size(max=254) String email,
  @Size(max=32) String mobile,@Pattern(regexp="MALE|FEMALE|UNSPECIFIED") String gender,Long avatarFileId){}
 public record View(Long id,String username,String name,String email,String mobile,String gender,Long avatarFileId){}
 private UserEntity own(){var row=accounts.selectById(CurrentUser.require().userId());if(row==null||!row.getPortal().equals(CurrentUser.portal()))throw new BizException(CommonErrorCode.DATA_NOT_FOUND);return row;}
 public View get(){return view(own());}
 @Transactional public View update(Input input){
  var row=own();
  if(input.avatarFileId()!=null&&!input.avatarFileId().equals(row.getAvatarFileId()))files.requireOwnedImage(input.avatarFileId());
  row.setName(input.name().trim());row.setEmail(input.email());row.setMobile(input.mobile());row.setGender(input.gender());row.setAvatarFileId(input.avatarFileId());
  accounts.update(null,new LambdaUpdateWrapper<UserEntity>().eq(UserEntity::getId,row.getId()).eq(UserEntity::getPortal,CurrentUser.portal())
   .set(UserEntity::getName,row.getName()).set(UserEntity::getEmail,row.getEmail()).set(UserEntity::getMobile,row.getMobile())
   .set(UserEntity::getGender,row.getGender()).set(UserEntity::getAvatarFileId,row.getAvatarFileId())
   .set(UserEntity::getUpdatedAt,java.time.Instant.now()).set(UserEntity::getUpdatedBy,CurrentUser.userId()));return view(row);
 }
 private View view(UserEntity row){return new View(row.getId(),row.getUsername(),row.getName(),row.getEmail(),row.getMobile(),row.getGender(),row.getAvatarFileId());}
}
