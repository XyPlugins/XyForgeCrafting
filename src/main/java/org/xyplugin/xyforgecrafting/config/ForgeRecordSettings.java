package org.xyplugin.xyforgecrafting.config;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Immutable formatting settings for provenance added to successful forge results. */
public final class ForgeRecordSettings {
    private final boolean enabled;
    private final ZoneId zoneId;
    private final DateTimeFormatter formatter;
    private final boolean replaceLastSeparator;
    private final List<String> lore;

    public ForgeRecordSettings(boolean enabled, String zoneId, String timeFormat,
                               boolean replaceLastSeparator, List<String> lore) {
        this.enabled = enabled;
        try {
            this.zoneId = ZoneId.of(zoneId == null ? "Asia/Shanghai" : zoneId.trim());
        } catch (RuntimeException failure) {
            throw new IllegalArgumentException("forge-record.timezone 无效: " + zoneId);
        }
        try {
            this.formatter = DateTimeFormatter.ofPattern(
                    timeFormat == null ? "yyyy-MM-dd HH:mm:ss" : timeFormat.trim()).withZone(this.zoneId);
        } catch (RuntimeException failure) {
            throw new IllegalArgumentException("forge-record.time-format 无效: " + timeFormat);
        }
        this.replaceLastSeparator = replaceLastSeparator;
        this.lore = Collections.unmodifiableList(new ArrayList<String>(lore));
    }

    public boolean isEnabled() { return enabled; }
    public boolean shouldReplaceLastSeparator() { return replaceLastSeparator; }
    public List<String> getLore() { return lore; }
    public String format(long epochMillis) { return formatter.format(Instant.ofEpochMilli(epochMillis)); }
}
