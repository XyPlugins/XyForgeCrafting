package org.xyplugin.xycore.api.service;

public interface ReloadManager {
    void register(Reloadable reloadable);
    void unregister(String id);
}
