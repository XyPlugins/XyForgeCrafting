package org.xyplugin.xycore.api;

import org.xyplugin.xycore.api.economy.EconomyService;
import org.xyplugin.xycore.api.item.ItemLibraryService;
import org.xyplugin.xycore.api.item.ItemTagService;
import org.xyplugin.xycore.api.service.ReloadManager;

public interface XyCoreApi {
    EconomyService getEconomy();
    ItemLibraryService getItems();
    ItemTagService getItemTags();
    ReloadManager getReloads();
    String getMessagePrefix();
}
