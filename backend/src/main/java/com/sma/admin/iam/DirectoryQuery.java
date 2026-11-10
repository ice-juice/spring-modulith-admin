package com.sma.admin.iam;
/** Minimal internal messaging directory; does not expose contact details or account administration. */
public interface DirectoryQuery {
 java.util.List<com.sma.admin.common.OptionItem> search(String keyword);
 java.util.Map<Long,String> names(java.util.Set<Long> userIds);
}
