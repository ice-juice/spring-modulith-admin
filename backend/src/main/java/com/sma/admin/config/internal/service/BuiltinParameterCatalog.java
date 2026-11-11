package com.sma.admin.config.internal.service;

import java.util.*;
import org.springframework.stereotype.Component;
import com.sma.admin.config.*;

/** Pure seed metadata; no dependency on the BuiltinParameters bean or its ParameterService. */
@Component
public class BuiltinParameterCatalog implements ConfigurationCatalog {
    @Override public Collection<ParameterDefinition> parameters() {
        return BuiltinParameters.specs().stream().map(seed->{
            String minimum=null,maximum=null;
            switch(seed.key()) {
                case "auth.sessionTtlSeconds","auth.rememberTtlSeconds" -> {minimum="60";maximum="31536000";}
                case "auth.autoLockSeconds" -> {minimum="0";maximum="86400";}
                case "auth.passwordNoticeDays" -> {minimum="0";maximum="3650";}
                default -> { }
            }
            return new ParameterDefinition("config",seed.key(),seed.group(),new ConfigurationLabel(seed.name(),seed.nameKey(),Map.of()),
                    ParameterDefinition.Scope.PLATFORM_SHARED,ParameterDefinition.ValueType.valueOf(seed.type()),ParameterDefinition.OverridePolicy.OVERRIDABLE,
                    seed.value(),new ParameterDefinition.Constraints(false,seed.type().equals("STRING"),4000,minimum,maximum,Set.of()),
                    seed.key().startsWith("auth.") || seed.key().startsWith("login."),
                    seed.key().equals("login.captchaEnabled")?ParameterDefinition.PublicProjection.LOGIN_CAPTCHA:ParameterDefinition.PublicProjection.NONE);
        }).toList();
    }
}
