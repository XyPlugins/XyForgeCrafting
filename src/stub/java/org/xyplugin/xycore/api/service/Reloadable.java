package org.xyplugin.xycore.api.service;

public interface Reloadable {
    String getId();
    void reload() throws Exception;
}
