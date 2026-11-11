package com.sma.admin.monitor;
import static org.assertj.core.api.Assertions.*;
import org.junit.jupiter.api.Test;
import jakarta.validation.Validation;
import tools.jackson.databind.json.JsonMapper;
import com.sma.admin.monitor.internal.service.CacheControlService;

class CacheControlInputTests {
    @Test void missingVersionRemainsNullAfterJsonBindingAndFailsValidationInsteadOfBecomingVersionZero(){
        try(var factory=Validation.buildDefaultValidatorFactory()){
            var json=JsonMapper.builder().build();var validator=factory.getValidator();
            var missing=json.readValue("{\"reason\":\"pause\"}",CacheControlService.Version.class);
            assertThat(missing.expectedVersion()).isNull();
            assertThat(validator.validate(missing)).extracting(value->value.getPropertyPath().toString()).contains("expectedVersion");
            var explicit=json.readValue("{\"expectedVersion\":\"0\",\"reason\":\"pause\"}",CacheControlService.Version.class);
            assertThat(validator.validate(explicit)).isEmpty();
            var policy=json.readValue("{\"reason\":\"policy\",\"ttlMillis\":1000,\"negativeTtlMillis\":0,\"maxEntries\":1,\"maxBytes\":1024,\"maxEntryBytes\":128}",CacheControlService.PolicyInput.class);
            assertThat(validator.validate(policy)).extracting(value->value.getPropertyPath().toString()).containsExactly("expectedVersion");
        }
    }
}
