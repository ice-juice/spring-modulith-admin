package com.sma.admin.auth;

import static org.assertj.core.api.Assertions.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.apache.commons.codec.binary.Base32;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import com.eatthepath.otp.TimeBasedOneTimePasswordGenerator;
import com.sma.admin.auth.internal.service.*;
import com.sma.admin.common.BizException;

class AuthSecurityCryptoTests {
    @TempDir Path directory;
    private SecuritySecrets secrets(String encId, String pepperId, boolean old) throws Exception {
        var file=directory.resolve(UUID.randomUUID()+".properties");
        String first=Base64.getEncoder().encodeToString(new byte[32]);
        byte[] second=new byte[32]; Arrays.fill(second,(byte)1);
        byte[] third=new byte[32]; Arrays.fill(third,(byte)2);
        byte[] fourth=new byte[32]; Arrays.fill(fourth,(byte)3);
        Files.writeString(file,"encryption.active="+encId+"\npepper.active="+pepperId+"\nencryption.new="+Base64.getEncoder().encodeToString(third)
                +"\npepper.new="+Base64.getEncoder().encodeToString(fourth)+(old?"\nencryption.old="+first+"\npepper.old="+Base64.getEncoder().encodeToString(second):""));
        return new SecuritySecrets(new AccountSecurityProperties(file.toString(),"SMA"));
    }
    @Test void authenticatedEncryptionBindsTenantAccountAndPurposeAndRetainsOldKeys() throws Exception {
        var old=secrets("old","old",true); String context="1:42:admin:totp";
        String encrypted=old.encrypt("secret-material",context);
        assertThat(encrypted).doesNotContain("secret-material");
        assertThat(old.encrypt("secret-material",context)).isNotEqualTo(encrypted);
        var rotated=secrets("new","new",true);
        assertThat(rotated.decrypt(encrypted,context)).isEqualTo("secret-material");
        assertThatThrownBy(()->rotated.decrypt(encrypted,"2:42:admin:totp")).isInstanceOf(BizException.class);
        assertThatThrownBy(()->rotated.decrypt(encrypted,"1:43:admin:totp")).isInstanceOf(BizException.class);
        assertThatThrownBy(()->rotated.decrypt(encrypted,"1:42:admin:enrollment")).isInstanceOf(BizException.class);
        String[] parts=encrypted.split("\\."); byte[] cipher=Base64.getUrlDecoder().decode(parts[3]);cipher[0]^=1;
        parts[3]=Base64.getUrlEncoder().withoutPadding().encodeToString(cipher);
        assertThatThrownBy(()->rotated.decrypt(String.join(".",parts),context)).isInstanceOf(BizException.class);
        var retired=secrets("new","new",false);
        assertThatThrownBy(()->retired.decrypt(encrypted,context)).isInstanceOf(BizException.class);
        String migrated=rotated.reencrypt(encrypted,context);
        assertThat(retired.decrypt(migrated,context)).isEqualTo("secret-material");
        assertThat(rotated.reencrypt(migrated,context)).isEqualTo(migrated);
        assertThat(rotated.prehash("A1b2c3","old")).isEqualTo(old.prehash("A1b2c3","old"));
        assertThat(rotated.prehash("A1b2c3","new")).isNotEqualTo(old.prehash("A1b2c3","old"));
    }
    @Test void absentOrInvalidConfigurationFailsClosedWithoutFallbackKeys() throws Exception {
        var unavailable=new SecuritySecrets(new AccountSecurityProperties("","SMA"));
        assertThat(unavailable.configured()).isFalse();
        assertThatThrownBy(()->unavailable.encrypt("anything","context")).isInstanceOf(BizException.class);
        assertThatThrownBy(()->unavailable.prehash("A1b2c3","missing")).isInstanceOf(BizException.class);
        var invalid=directory.resolve("invalid.properties");Files.writeString(invalid,"encryption.active=x\nencryption.x=not-a-key");
        assertThatThrownBy(()->new SecuritySecrets(new AccountSecurityProperties(invalid.toString(),"SMA"))).isInstanceOf(IllegalStateException.class);
    }
    @Test void matchesAllRfc6238VectorsForSupportedAlgorithms() throws Exception {
        long[] times={59,1111111109,1111111111,1234567890,2000000000,20000000000L};
        String[][] expected={{"94287082","07081804","14050471","89005924","69279037","65353130"},
                {"46119246","68084774","67062674","91819424","90698825","77737706"},
                {"90693936","25091201","99943326","93441116","38618901","47863826"}};
        String[] algorithms={"HmacSHA1","HmacSHA256","HmacSHA512"};int[] lengths={20,32,64};
        for(int a=0;a<3;a++) {
            byte[] secret="1234567890".repeat(7).substring(0,lengths[a]).getBytes(StandardCharsets.US_ASCII);
            var generator=new TimeBasedOneTimePasswordGenerator(Duration.ofSeconds(30),8,algorithms[a]);
            for(int i=0;i<times.length;i++) assertThat(generator.generateOneTimePasswordString(new SecretKeySpec(secret,algorithms[a]),Instant.ofEpochSecond(times[i]),Locale.ROOT)).isEqualTo(expected[a][i]);
        }
    }
    @Test void acceptsOnlyWindowedUnusedSixDigitCodes() throws Exception {
        String secret=new Base32().encodeAsString("12345678901234567890".getBytes(StandardCharsets.US_ASCII));
        var codes=new TotpCodes();
        assertThat(codes.acceptedStep(secret,"287082",Instant.ofEpochSecond(59),null)).isEqualTo(1);
        assertThat(codes.acceptedStep(secret,"287082",Instant.ofEpochSecond(59),1L)).isEqualTo(-1);
        assertThat(codes.acceptedStep(secret,"287082",Instant.ofEpochSecond(90),null)).isEqualTo(-1);
        assertThat(codes.acceptedStep(secret,"0287082",Instant.ofEpochSecond(59),null)).isEqualTo(-1);
        assertThat(new Base32().decode(codes.newSecret())).hasSize(20);
    }
    @Test void argon2idUsesIndependentSaltAndPepperAndRecordsLocalCost() throws Exception {
        var keys=secrets("new","new",true);var encoder=new Argon2PasswordEncoder(16,32,1,19456,2);
        String material=keys.prehash("m7K2v9R4",keys.pepperId());long start=System.nanoTime();
        String first=encoder.encode(material),second=encoder.encode(material);
        assertThat(first).startsWith("$argon2id$v=19$m=19456,t=2,p=1$").isNotEqualTo(second);
        assertThat(encoder.matches(material,first)).isTrue();
        assertThat(encoder.matches(keys.prehash("wrong9","new"),first)).isFalse();
        Files.createDirectories(Path.of("target/p07"));
        Files.writeString(Path.of("target/p07/argon2-cost.txt"),"2 encodes + 2 matches milliseconds="+(System.nanoTime()-start)/1_000_000+"\nparameters=m19456,t2,p1\n");
    }
}
