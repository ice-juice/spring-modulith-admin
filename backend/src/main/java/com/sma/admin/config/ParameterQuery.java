package com.sma.admin.config;
import java.math.BigDecimal;
import java.util.Map;
public interface ParameterQuery {
 String stringValue(String key,String fallback);
 boolean booleanValue(String key,boolean fallback);
 long longValue(String key,long fallback);
 BigDecimal decimalValue(String key,BigDecimal fallback);
 Map<String,String> publicValues();
}
