package com.sma.admin.iam;
import java.util.Optional;

public interface AccountQuery {
    Optional<AccountView> authenticate(String username,String password,String portal);
    Optional<AccountView> findById(long id);
    boolean matchesPassword(long id,String password);
    java.util.Set<Long> searchIds(String keyword);
}
