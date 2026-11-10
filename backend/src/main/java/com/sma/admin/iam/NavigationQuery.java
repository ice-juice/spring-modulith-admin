package com.sma.admin.iam;
import java.util.List;
import java.util.Set;
public interface NavigationQuery { List<NavigationView> visible(Set<Long> menuIds); }
