package org.openhab.binding.nuki.internal.handler.web;

import java.util.Map;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.nuki.api.model.Smartlock;
import org.openhab.binding.nuki.internal.configuration.NukiWebDeviceConfiguration;
import org.openhab.binding.nuki.internal.dataexchange.NukiWebHttpClient;
import org.openhab.binding.nuki.internal.handler.AbstractNukiThingHandler;
import org.openhab.core.thing.Bridge;
import org.openhab.core.thing.ChannelUID;
import org.openhab.core.thing.Thing;
import org.openhab.core.thing.ThingStatus;
import org.openhab.core.thing.ThingStatusDetail;
import org.openhab.core.thing.ThingStatusInfo;
import org.openhab.core.types.Command;
import org.openhab.core.types.RefreshType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class AbstractNukiWebDeviceHandler<T extends NukiWebDeviceConfiguration>
        extends AbstractNukiThingHandler {

    protected final Logger logger = LoggerFactory.getLogger(getClass());

    public AbstractNukiWebDeviceHandler(Thing thing) {
        super(thing);
        this.configuration = getConfigAs(getConfigurationClass());
    }

    protected T configuration;
    private ScheduledFuture<?> statusjob = null;
    private ScheduledFuture<?> pollJob = null;

    @Override
    public void initialize() {
        logger.trace("initialize()");
        this.configuration = getConfigAs(getConfigurationClass());
        runUpdateStateJob(0);
    }

    @Override
    public void dispose() {
        logger.trace("dispose()");
        this.cancelStatusJob();
        this.cancelPollJob();
    }

    private void cancelStatusJob() {
        ScheduledFuture<?> statusJob = this.statusjob;
        if (statusJob != null) {
            statusJob.cancel(true);
            this.statusjob = null;
        }
    }

    private void schedulePollJob() {
        Integer pollInterval = configuration.pollInterval;
        if (pollInterval != null && pollInterval > 0) {
            logger.debug("Scheduling poll job to run every {}s", pollInterval);
            this.pollJob = scheduler.scheduleAtFixedRate(this::updateDeviceState, pollInterval, pollInterval,
                    TimeUnit.SECONDS);
        }
    }

    private void cancelPollJob() {
        ScheduledFuture<?> pollJob = this.pollJob;
        if (pollJob != null) {
            if (pollJob.cancel(true)) {
                logger.debug("Poll job cancelled");
            }
            this.pollJob = null;
        }
    }

    protected void runUpdateStateJob(long delaySeconds) {
        this.cancelStatusJob();
        this.statusjob = this.scheduler.schedule(this::updateDeviceState, delaySeconds, TimeUnit.SECONDS);
    }

    protected void runRefreshJob() {
        T config = configuration;
        if (config != null && config.refreshDelay != null && config.refreshDelay > 0) {
            runUpdateStateJob(config.refreshDelay);
        }
    }

    private void updateDeviceState() {
        Bridge bridge = getBridge();
        String smartLockId = configuration == null ? null : configuration.smartLockId;
        if (bridge == null || bridge.getStatus() != ThingStatus.ONLINE) {
            logger.debug("Bridge is offline");
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.BRIDGE_OFFLINE);
        } else if (smartLockId == null) {
            logger.debug("Missing smartLockId configuration property");
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR,
                    "Missing smartLockId configuration property");
        } else {
            getWebBridge(bridge).withClient(client -> {
                try {
                    handleStatusUpdate(client.getSmartlock(smartLockId));
                    updateStatus(ThingStatus.ONLINE);
                    logger.debug("Thing {} is online", getThing());
                } catch (NukiWebHttpClient.CommunicationErrorException e) {
                    logger.debug("Communication error", e);
                    updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR, e.getMessage());
                    runUpdateStateJob(5);
                } catch (NukiWebHttpClient.UnauthorizedException e) {
                    logger.debug("Unauthorized - " + e.getMessage());
                    updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR,
                            "Unauthorized - check access token");
                    runUpdateStateJob(5);
                }
            });
        }
    }

    protected NukiWebAccountHandler getWebBridge(Bridge bridge) {
        return (NukiWebAccountHandler) bridge.getHandler();
    }

    @Override
    public void bridgeStatusChanged(ThingStatusInfo bridgeStatusInfo) {
        logger.trace("bridgeStatusChanged({})", bridgeStatusInfo);
        runUpdateStateJob(0);
    }

    @Override
    protected void updateStatus(ThingStatus status, ThingStatusDetail statusDetail, @Nullable String description) {
        if (getThing().getStatus() != status) {
            cancelPollJob();
            if (status == ThingStatus.ONLINE) {
                schedulePollJob();
            }
        }
        super.updateStatus(status, statusDetail, description);
    }

    @Override
    public void handleConfigurationUpdate(Map<String, Object> configurationParameters) {
        super.handleConfigurationUpdate(configurationParameters);
        cancelPollJob();
        schedulePollJob();
    }

    abstract void handleStatusUpdate(Smartlock status);

    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {
        logger.trace("handleComment({}, {})", channelUID, command);

        Bridge bridge = getBridge();
        if (bridge == null) {
            return;
        }

        String smartlockId = configuration.smartLockId;
        if (smartlockId == null) {
            return;
        }

        if (command instanceof RefreshType) {
            runUpdateStateJob(0);
        } else {
            if (!doHandleCommand(channelUID, command, getWebBridge(bridge), smartlockId)) {
                logger.debug("Command {} for channel {} not implemented", command, channelUID);
            }
        }
    }

    protected abstract boolean doHandleCommand(ChannelUID channelUID, Command command, NukiWebAccountHandler bridge,
            String smartlockId);

    protected abstract Class<T> getConfigurationClass();
}
