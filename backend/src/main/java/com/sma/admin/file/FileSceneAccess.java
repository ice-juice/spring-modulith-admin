package com.sma.admin.file;

/** Resource references are data, not grants. Policies must check real business records for the trusted actor. */
public record FileSceneAccess(String application,String scene,Action action,String objectType,String objectId,Long fileId) {
    public enum Action { UPLOAD, READ, BIND, UNBIND, DELETE }
    public FileSceneAccess {
        if(application==null || !application.matches("[a-z][a-z0-9.-]{0,63}") || scene==null || !scene.startsWith(application+".") || scene.length()>96 || action==null
                || fileId!=null && fileId<=0 || (objectType==null)!=(objectId==null)
                || objectType!=null && (!objectType.matches("[a-z][a-z0-9.-]{0,63}") || !objectId.matches("[A-Za-z0-9_.:-]{1,128}"))
                || (action==Action.BIND || action==Action.UNBIND) && (objectId==null || fileId==null))throw new IllegalArgumentException("Invalid file scene access request");
    }
}
