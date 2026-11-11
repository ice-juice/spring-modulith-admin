package com.sma.admin.iam;
import static org.assertj.core.api.Assertions.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;
import com.sma.admin.common.*;
import com.sma.admin.common.security.*;
import com.sma.admin.file.internal.service.FileService;
import com.sma.admin.iam.internal.service.*;
import com.sma.admin.support.*;

@IntegrationTest
class ProfileTests {
 @Autowired UserService users;@Autowired ProfileService profiles;@Autowired FileService files;
 @Test void profileSupportsNullableFieldsAndOwnedAvatarsWithoutInvalidatingCredentials(){
  Users.as(Users.principal(101,224,DataAccess.unrestricted()),()->{
   var account=users.create(new UserService.Create(new UserService.Input("profile","Profile",null,"test@example.test","123","UNSPECIFIED",true,Set.of(),Set.of(),null,null),"Q70a005368"));
   Users.as(Users.principal(account.id(),224,DataAccess.own()),()->{
    long version=users.findById(account.id()).orElseThrow().credentialVersion();
    byte[] png=Base64.getDecoder().decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+aX1EAAAAASUVORK5CYII=");
    var image=files.uploadImage(new MockMultipartFile("file","avatar.png","application/octet-stream",png));
    profiles.update(new ProfileService.Input("Changed",null,null,null,image.id()));
    assertThat(profiles.get().avatarFileId()).isEqualTo(image.id());assertThat(profiles.get().email()).isNull();
    assertThat(users.findById(account.id()).orElseThrow().credentialVersion()).isEqualTo(version);
    assertThatThrownBy(()->files.delete(image.id())).isInstanceOf(BizException.class);
    Users.as(Users.principal(303,224,DataAccess.own()),()->{
     assertThat(files.metadata(image.id()).previewable()).isTrue();
     assertThatThrownBy(()->files.requireOwnedImage(image.id())).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);return null;
    });
    profiles.update(new ProfileService.Input("Changed",null,null,null,null));
    assertThat(profiles.get().avatarFileId()).isNull();files.delete(image.id());
    return null;
   });return null;
  });
 }
}
