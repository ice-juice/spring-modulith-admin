package com.sma.admin.iam;
import static org.assertj.core.api.Assertions.*;
import org.junit.jupiter.api.Test;
import com.sma.admin.common.BizException;
import com.sma.admin.iam.internal.service.LoginPasswordPolicy;

class LoginPasswordPolicyTests {
    @org.junit.jupiter.api.Test void sharedLoginWritePolicyUsesTheSameRulesWithoutExposingIamErrors(){
        com.sma.admin.common.security.PasswordStrengthPolicy shared=new LoginPasswordPolicy();
        assertThat(shared.evaluate("operator","Q7d4e10594")).isEqualTo(com.sma.admin.common.security.PasswordStrengthPolicy.Decision.ACCEPTED);
        assertThat(shared.evaluate("operator","password1")).isEqualTo(com.sma.admin.common.security.PasswordStrengthPolicy.Decision.WEAK);
        assertThat(shared.evaluate("operator","short")).isEqualTo(com.sma.admin.common.security.PasswordStrengthPolicy.Decision.INVALID_FORMAT);
    }
    private final LoginPasswordPolicy policy=new LoginPasswordPolicy();
    @Test void acceptsEverySupportedLengthWithoutRequiringMixedCaseOrSymbols(){
        for(String password:java.util.List.of("m7K2v9R4","m7K2v9R4N","m7K2v9R4Np","m7K2v9R4NpQ","m7K2v9R4NpQr","m7k2v9r4","M7K2V9R4"))
            assertThatCode(()->policy.validate("unrelated-user",password)).doesNotThrowAnyException();
    }
    @Test void rejectsShortLongPureAndNonAsciiOrWhitespaceValues(){
        for(String password:java.util.List.of("123456","aaa","m7K2v9R","m7K2v9R4NpQrs","12345678","abcdefgh","m7K2v9R4\n","m7K2v9R4 ","m7K2v9R!","ｍ7K2v9R4"))
            assertThatThrownBy(()->policy.validate("unrelated-user",password)).isInstanceOfSatisfying(BizException.class,e->assertThat(e.getErrorCode()).isEqualTo(IamErrorCode.PASSWORD_FORMAT));
    }
    @Test void rejectsDictionaryCaseVariantsPatternsAndAccountDerivedValues(){
        for(String password:java.util.List.of("password1","Password123","admin123","qwerty12","abc12345","123456ab","ab12ab12","a1a1a1a1","aaaa1111","1111aaaa","operator1","12operator"))
            assertThatThrownBy(()->policy.validate("operator",password)).isInstanceOfSatisfying(BizException.class,e->assertThat(e.getErrorCode()).isEqualTo(IamErrorCode.WEAK_PASSWORD));
        assertThatCode(()->policy.validate("operator","aa1b2c3d")).doesNotThrowAnyException();
    }
}
