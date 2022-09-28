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

public class NukiWebSmartLockHandler extends AbstractNukiWebDeviceHandler<NukiWebDeviceConfiguration> {

    public NukiWebSmartLockHandler(Thing thing) {
        super(thing);
    }

    @Override
    void handleStatusUpdate(Smartlock status) {
        updateState(NukiBindingConstants.CHANNEL_SMARTLOCK_BATTERY_LEVEL, status.getState().getBatteryCharge(),
                DecimalType::new);
        updateState(NukiBindingConstants.CHANNEL_SMARTLOCK_LOW_BATTERY, status.getState().isBatteryCritical(),
                OnOffType::from);
        updateState(NukiBindingConstants.CHANNEL_SMARTLOCK_KEYPAD_LOW_BATTERY,
                status.getState().isKeypadBatteryCritical(), OnOffType::from);
        updateState(NukiBindingConstants.CHANNEL_SMARTLOCK_BATTERY_CHARGING, status.getState().isBatteryCharging(),
                OnOffType::from);
        updateState(NukiBindingConstants.CHANNEL_SMARTLOCK_STATE, status.getState().getState(), DecimalType::new);
        updateState(NukiBindingConstants.CHANNEL_SMARTLOCK_DOOR_STATE, status.getState().getDoorState(),
                DecimalType::new);
    }

    @Override
    protected Class<NukiWebDeviceConfiguration> getConfigurationClass() {
        return NukiWebDeviceConfiguration.class;
    }

    @Override
    protected boolean doHandleCommand(ChannelUID channelUID, Command command, NukiWebAccountHandler bridge,
            String smartlockId) {
        switch (channelUID.getId()) {
            case NukiBindingConstants.CHANNEL_SMARTLOCK_STATE:
                if (command instanceof DecimalType) {
                    DecimalType cmd = (DecimalType) command;
                    SmartlockAction action = new SmartlockAction();
                    action.setAction(cmd.intValue());
                    action.setOption(0);

                    bridge.withClient(client -> {
                        try {
                            client.sendAction(smartlockId, action);
                            runRefreshJob();
                        } catch (Exception e) {
                            logger.error("Failed to send command {} for lock {} - {}: {}", command,
                                    configuration.smartLockId, e.getClass(), e.getMessage());
                        }
                    });
                    return true;
                }
                break;
        }

        return false;
    }

    @Override
    public Collection<Class<? extends ThingHandlerService>> getServices() {
        return super.getServices();
    }
}
