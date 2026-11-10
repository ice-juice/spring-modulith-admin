package com.sma.admin.iam;
import java.util.*;
/** Expands a message audience within the caller's tenant, portal and data scope. */
public interface AudienceQuery {
 Map<Long,String> resolve(String type,Set<Long> ids);
 List<com.sma.admin.common.OptionItem> options(String type,String keyword);
}
