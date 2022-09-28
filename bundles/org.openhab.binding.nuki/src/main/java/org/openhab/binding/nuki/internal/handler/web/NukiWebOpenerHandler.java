package org.openhab.binding.nuki.internal.handler.web;

import java.util.Collection;

import org.openhab.binding.nuki.api.model.Smartlock;
import org.openhab.binding.nuki.api.model.SmartlockAction;
import org.openhab.binding.nuki.internal.configuration.NukiWebDeviceConfiguration;
import org.openhab.binding.nuki.internal.constants.NukiBindingConstants;
import org.openhab.core.library.types.DecimalType;
import org.openhab.core.library.types.OnOffType;
import org.openhab.core.thing.ChannelUID;
import org.openhab.core.thing.Thing;
import org.openhab.core.thing.binding.ThingHandlerService;
import org.openhab.core.types.Command;

public class NukiWebOpenerHandler extends AbstractNukiWebDeviceHandler<NukiWebDeviceConfiguration> {

    public NukiWebOpenerHandler(Thing thing) {
        super(thing);
    }

    @Override
    void handleStatusUpdate(Smartlock status) {
        updateState(NukiBindingConstants.CHANNEL_OPENER_LOW_BATTERY, status.getState().isBatteryCritical(),
                OnOffType::from);
        updateState(NukiBindingConstants.CHANNEL_OPENER_STATE, status.getState().getState(), DecimalType::new);
        updateState(NukiBindingConstants.CHANNEL_OPENER_MODE, status.getState().getMode(), DecimalType::new);
    }

    @Override
    protected boolean doHandleCommand(ChannelUID channelUID, Command command, NukiWebAccountHandler bridge,
            String smartlockId) {
        switch (channelUID.getId()) {
            case NukiBindingConstants.CHANNEL_OPENER_STATE:
                if (command instanceof DecimalType) {
                    bridge.withClient(client -> {
                        SmartlockAction actionData = new SmartlockAction();
                        actionData.setAction(((DecimalType) command).intValue());
                        actionData.setOption(0);
                        try {
                            client.sendAction(smartlockId, actionData);
                            runRefreshJob();
                        } catch (Exception e) {
                            logger.warn("Failed to send command {} for opener {} - {}: {}", command,
                                    configuration.smartLockId, e.getClass(), e.getMessage());
                        }
                    });
                    return true;
                }
        }
        return false;
    }

    @Override
    protected Class<NukiWebDeviceConfiguration> getConfigurationClass() {
        return NukiWebDeviceConfiguration.class;
    }

    @Override
    public Collection<Class<? extends ThingHandlerService>> getServices() {
        return super.getServices();
    }
}
