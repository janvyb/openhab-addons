package org.openhab.binding.nuki.internal.configuration;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;

@NonNullByDefault
public class NukiWebDeviceConfiguration {
    @Nullable
    public String smartLockId;
    @Nullable
    public Integer pollInterval;
    @Nullable
    public Integer refreshDelay;
}
