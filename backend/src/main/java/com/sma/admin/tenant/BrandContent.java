package com.sma.admin.tenant;

import java.util.Map;

/** Versioned brand data only: no HTML, CSS, script, JDBC credentials or authentication policy. */
public record BrandContent(int schemaVersion, Identity identity, Login login, Legal legal, Links links,
        Appearance appearance, Map<AssetSlot,Long> assets, Map<Field,OverrideMode> overrides) {
    public enum AssetSlot { LOGO_LIGHT, LOGO_DARK, LOGO_COMPACT, FAVICON, LOGIN_BACKGROUND }
    public enum OverrideMode { INHERIT, OVERRIDE, HIDE }
    public enum Field { SHORT_NAME, ENGLISH_NAME, WELCOME_TITLE, WELCOME_DESCRIPTION, COPYRIGHT, ICP_RECORD, POLICE_RECORD,
        WEBSITE, HELP, SUPPORT, PRIVACY, AGREEMENT, LOGO_LIGHT, LOGO_DARK, LOGO_COMPACT, FAVICON, LOGIN_BACKGROUND, PRIMARY_COLOR }
    public record Text(String zhCN,String enUS) {}
    public record Identity(String systemName,String shortName,String englishName) {}
    public record Login(String templateId,Text title,Text description,boolean backgroundBlur) {}
    public record Legal(String copyrightOwner,Integer copyrightStartYear,String copyrightUrl,
            String icpRecord,String icpUrl,String policeRecord,String policeUrl) {}
    public record Links(String website,String help,String support,String privacy,String agreement) {}
    public record Appearance(String primaryColor,String defaultSkin,boolean allowPersonalSkin) {}
    public BrandContent {
        assets=assets==null?Map.of():Map.copyOf(assets);
        overrides=overrides==null?Map.of():Map.copyOf(overrides);
    }
    public static BrandContent defaults(String systemName) {
        return new BrandContent(1,new Identity(systemName,"",""),new Login("login1",new Text("",""),new Text("",""),false),
                new Legal("",null,"","","","",""),new Links("","","","",""),new Appearance("","iris",true),Map.of(),Map.of());
    }
}
