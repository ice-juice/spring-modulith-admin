package com.sma.admin.defense;
/** Host authority SPI; a tenant administrator is not automatically a platform operator. */
public interface DefensePlatformAccess { void requireViewer(); }
