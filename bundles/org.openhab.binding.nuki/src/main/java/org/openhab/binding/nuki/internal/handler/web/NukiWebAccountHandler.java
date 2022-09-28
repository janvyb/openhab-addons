package org.openhab.binding.nuki.internal.handler.web;

import java.util.Collection;
import java.util.Collections;
import java.util.function.Consumer;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jetty.client.HttpClient;
import org.openhab.binding.nuki.internal.configuration.NukiWebAccountBridgeConfiguration;
import org.openhab.binding.nuki.internal.dataexchange.NukiWebHttpClient;
import org.openhab.binding.nuki.internal.discovery.NukiWebDeviceDiscoveryService;
import org.openhab.core.thing.Bridge;
import org.openhab.core.thing.ChannelUID;
import org.openhab.core.thing.ThingStatus;
import org.openhab.core.thing.ThingStatusDetail;
import org.openhab.core.thing.binding.BaseBridgeHandler;
import org.openhab.core.thing.binding.ThingHandlerService;
import org.openhab.core.types.Command;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@NonNullByDefault
public class NukiWebAccountHandler extends BaseBridgeHandler {

    private final Logger logger = LoggerFactory.getLogger(NukiWebAccountHandler.class);
    private final HttpClient httpClient;
    @Nullable
    private NukiWebHttpClient nukiWebClient = null;
    private NukiWebAccountBridgeConfiguration config = new NukiWebAccountBridgeConfiguration();

    public NukiWebAccountHandler(Bridge bridge, HttpClient httpClient) {
        super(bridge);
        this.httpClient = httpClient;
    }

    @Override
    public void initialize() {
        this.config = getConfigAs(NukiWebAccountBridgeConfiguration.class);
        String accessToken = this.config.accessToken;
        if (accessToken == null) {
            logger.debug("Cannot initialize Web Account Bridge - missing accessToken");
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR,
                    "Missing accessToken configuration property");
        } else {
            this.nukiWebClient = new NukiWebHttpClient(this.httpClient, accessToken);
            scheduler.execute(() -> {
                try {
                    nukiWebClient.getSmartlocks();
                    logger.debug("Bridge initialized");
                    updateStatus(ThingStatus.ONLINE);
                } catch (Exception e) {
                    logger.warn("Failed to initialize Nuki Web Account - invalid access token: {}", e.getMessage());
                    updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR, e.getMessage());
                }
            });
        }
    }

    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {
        logger.trace("handleCommand({}, {})", channelUID, command);
    }

    @Nullable
    public NukiWebHttpClient getNukiWebClient() {
        return nukiWebClient;
    }

    @Override
    public Collection<Class<? extends ThingHandlerService>> getServices() {
        return Collections.singleton(NukiWebDeviceDiscoveryService.class);
    }

    public void withClient(Consumer<NukiWebHttpClient> handler) {
        NukiWebHttpClient client = this.nukiWebClient;
        if (client != null) {
            handler.accept(client);
        }
    }
}
