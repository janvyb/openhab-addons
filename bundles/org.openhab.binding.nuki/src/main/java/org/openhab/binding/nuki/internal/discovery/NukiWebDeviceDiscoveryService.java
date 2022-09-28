package org.openhab.binding.nuki.internal.discovery;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.nuki.api.model.Smartlock;
import org.openhab.binding.nuki.internal.constants.NukiBindingConstants;
import org.openhab.binding.nuki.internal.dataexchange.NukiWebHttpClient;
import org.openhab.binding.nuki.internal.handler.web.NukiWebAccountHandler;
import org.openhab.core.config.discovery.AbstractDiscoveryService;
import org.openhab.core.config.discovery.DiscoveryResult;
import org.openhab.core.config.discovery.DiscoveryResultBuilder;
import org.openhab.core.thing.ThingUID;
import org.openhab.core.thing.binding.ThingHandler;
import org.openhab.core.thing.binding.ThingHandlerService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NukiWebDeviceDiscoveryService extends AbstractDiscoveryService implements ThingHandlerService {

    private final Logger logger = LoggerFactory.getLogger(NukiWebDeviceDiscoveryService.class);

    @Nullable
    private NukiWebAccountHandler bridge;

    public NukiWebDeviceDiscoveryService() {
        super(Set.of(NukiBindingConstants.THING_TYPE_WEB_SMARTLOCK), 5, false);
    }

    @Override
    protected void startScan() {
        NukiWebAccountHandler bridgeHandler = bridge;
        if (bridgeHandler == null) {
            logger.warn("Cannot start Nuki web discovery - no bridge available");
            return;
        }

        scheduler.execute(() -> {
            bridgeHandler.withClient(client -> {
                try {
                    List<Smartlock> smartlocks = client.getSmartlocks();
                    smartlocks.stream().map(smartlock -> createDiscoveryResult(smartlock, bridgeHandler))
                            .flatMap(Optional::stream).forEach(this::thingDiscovered);
                } catch (NukiWebHttpClient.CommunicationErrorException e) {
                    logger.warn("Nuki Web discovery failed - communication error: {}", e.getMessage());
                } catch (NukiWebHttpClient.UnauthorizedException e) {
                    logger.warn("Nuki Web discovery failed - token unauthorized: {}", e.getMessage());
                }
            });
        });
    }

    private Optional<DiscoveryResult> createDiscoveryResult(Smartlock smartlock, NukiWebAccountHandler handler) {
        ThingUID uid = getUid(smartlock.getSmartlockId().toString(), smartlock.getType(), handler);
        if (uid == null) {
            logger.warn("Failed to create UID for device '{}' - deviceType '{}' is not supported", smartlock,
                    smartlock.getType());
            return Optional.empty();
        } else {
            return Optional.of(DiscoveryResultBuilder.create(uid).withBridge(handler.getThing().getUID())
                    .withLabel(smartlock.getName())
                    .withRepresentationProperty(NukiBindingConstants.PROPERTY_SMARTLOCK_ID)
                    .withProperty(NukiBindingConstants.PROPERTY_NAME, smartlock.getName())
                    .withProperty(NukiBindingConstants.PROPERTY_SMARTLOCK_ID, smartlock.getSmartlockId().toString())
                    .withProperty(NukiBindingConstants.PROPERTY_FIRMWARE_VERSION, smartlock.getFirmwareVersion())
                    .withProperty(NukiBindingConstants.PROPERTY_DEVICE_TYPE, getDeviceTypeName(smartlock.getType()))
                    .build());
        }
    }

    @Nullable
    private ThingUID getUid(String id, int deviceType, NukiWebAccountHandler bridgeHandler) {
        switch (deviceType) {
            case NukiBindingConstants.DEVICE_SMART_LOCK:
            case NukiBindingConstants.DEVICE_SMART_DOOR:
            case NukiBindingConstants.DEVICE_SMART_LOCK_3:
                return new ThingUID(NukiBindingConstants.THING_TYPE_WEB_SMARTLOCK, bridgeHandler.getThing().getUID(),
                        id);
            case NukiBindingConstants.DEVICE_OPENER:
                return new ThingUID(NukiBindingConstants.THING_TYPE_WEB_OPENER, bridgeHandler.getThing().getUID(), id);
            default:
                return null;
        }
    }

    private String getDeviceTypeName(int deviceType) {
        switch (deviceType) {
            case NukiBindingConstants.DEVICE_SMART_LOCK:
                return "Smart Lock 1.0/2.0";
            case NukiBindingConstants.DEVICE_OPENER:
                return "Opener";
            case NukiBindingConstants.DEVICE_SMART_DOOR:
                return "Smart Door";
            case NukiBindingConstants.DEVICE_SMART_LOCK_3:
                return "Smart Lock 3.0";
            default:
                return "Unknown Nuki device (" + deviceType + ")";
        }
    }

    @Override
    public void setThingHandler(ThingHandler handler) {
        if (handler instanceof NukiWebAccountHandler) {
            this.bridge = (NukiWebAccountHandler) handler;
        }
    }

    @Override
    public @Nullable ThingHandler getThingHandler() {
        return bridge;
    }

    @Override
    protected synchronized void stopScan() {
        super.stopScan();
        removeOlderResults(getTimestampOfLastScan());
    }

    @Override
    public void activate() {
    }

    @Override
    public void deactivate() {
    }
}
