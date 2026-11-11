package com.sma.admin.iam;
import static org.assertj.core.api.Assertions.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import com.sma.admin.common.BizException;
import com.sma.admin.common.security.*;
import com.sma.admin.iam.internal.service.*;
import com.sma.admin.iam.internal.persistence.*;
import com.sma.admin.auth.internal.persistence.*;
import com.sma.admin.auth.internal.service.*;
import com.sma.admin.support.*;

@IntegrationTest
class LoginPasswordWriteTests {
    @Autowired UserService users;
    @Autowired AccountMapper accounts;
    @Autowired PasswordEncoder encoder;
    @Autowired SecurityPasswordMapper securityPasswords;
    @Autowired SecurityPasswordHash securityHash;
    @Autowired SessionIssuer issuer;
    @Autowired SessionService sessions;
    private UserService.Input input(String name){return new UserService.Input(name,name,null,null,null,"UNSPECIFIED",true,Set.of(),Set.of(),null,"UTC");}
    @Test void everyWritePathRejectsWeakValuesWithoutChangingStoredCredential(){
        Users.as(Users.principal(9901,9901,DataAccess.unrestricted()),()->{
            var user=users.create(new UserService.Create(input("policy-user"),"m7K2v9R4"));var original=accounts.selectById(user.id());
            for(String weak:List.of("123456","aaa","password1","admin123","ab12ab12")){
                assertThatThrownBy(()->users.create(new UserService.Create(input("bad"+UUID.randomUUID().toString().substring(0,8)),weak))).isInstanceOf(BizException.class);
                assertThatThrownBy(()->users.resetPassword(user.id(),weak)).isInstanceOf(BizException.class);
                assertThatThrownBy(()->users.bootstrap(weak)).isInstanceOf(BizException.class);
                var self=Users.principal(user.id(),9901,DataAccess.unrestricted());
                Users.as(self,()->{assertThatThrownBy(()->users.changeOwnPassword("m7K2v9R4",weak)).isInstanceOf(BizException.class);return null;});
                var after=accounts.selectById(user.id());assertThat(after.getPasswordHash()).isEqualTo(original.getPasswordHash());assertThat(after.getCredentialVersion()).isEqualTo(original.getCredentialVersion());
            }
            assertThat(accounts.selectCount(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<UserEntity>().eq(UserEntity::getSuperAdmin,true))).isZero();return null;
        });
    }
    @Test void legacyLongSymbolPasswordRemainsUsableUntilReplacedByNewPolicy(){
        Users.as(Users.principal(9902,9902,DataAccess.unrestricted()),()->{
            var created=users.create(new UserService.Create(input("legacy-user"),"m7K2v9R4"));
            // Explicit old-data fixture: production write services must never accept this candidate now.
            var row=accounts.selectById(created.id());row.setPasswordHash(encoder.encode("Historical!Password2025"));accounts.updateById(row);
            var user=users.authenticate("legacy-user","Historical!Password2025","admin").orElseThrow();
            var token=issuer.issue(user,false,Duration.ofHours(8),"legacy","test");assertThat(sessions.resolve(token.token())).isPresent();
            Users.as(Users.principal(user.id(),9902,DataAccess.unrestricted()),()->{users.changeOwnPassword("Historical!Password2025","n8L3w0S5");return null;});
            assertThat(users.authenticate("legacy-user","Historical!Password2025","admin")).isEmpty();assertThat(users.authenticate("legacy-user","n8L3w0S5","admin")).isPresent();assertThat(sessions.resolve(token.token())).isEmpty();return null;
        });
    }
    @Test void loginPasswordCannotBeResetToExistingSecurityPassword(){
        Users.as(Users.principal(9903,9903,DataAccess.unrestricted()),()->{
            var created=users.create(new UserService.Create(input("independent-user"),"m7K2v9R4"));
            var security=new SecurityPasswordEntity();security.setUserId(created.id());security.setPortal("admin");security.setPepperKeyId(securityHash.currentKey());security.setPasswordHash(securityHash.encode("n8L3w0S5",security.getPepperKeyId()));security.setCredentialVersion(1L);security.setChangedAt(Instant.now());securityPasswords.insert(security);
            assertThatThrownBy(()->users.resetPassword(created.id(),"n8L3w0S5")).isInstanceOfSatisfying(BizException.class,e->assertThat(e.getErrorCode()).isEqualTo(IamErrorCode.PASSWORD_CONFLICT));
            Users.as(Users.principal(created.id(),9903,DataAccess.unrestricted()),()->{assertThatThrownBy(()->users.changeOwnPassword("m7K2v9R4","n8L3w0S5")).isInstanceOf(BizException.class);return null;});
            assertThat(users.authenticate("independent-user","m7K2v9R4","admin")).isPresent();return null;
        });
    }
}
