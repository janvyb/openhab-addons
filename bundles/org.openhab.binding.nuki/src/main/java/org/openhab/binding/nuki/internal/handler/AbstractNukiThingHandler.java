package org.openhab.binding.nuki.internal.handler;

import java.util.function.Function;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.openhab.core.thing.Channel;
import org.openhab.core.thing.ChannelUID;
import org.openhab.core.thing.Thing;
import org.openhab.core.thing.binding.BaseThingHandler;
import org.openhab.core.types.State;
import org.openhab.core.types.UnDefType;

@NonNullByDefault
public abstract class AbstractNukiThingHandler extends BaseThingHandler {

    public AbstractNukiThingHandler(Thing thing) {
        super(thing);
    }

    protected <U> void updateState(String channelId, U state, Function<U, State> transform) {
        Channel channel = thing.getChannel(channelId);
        if (channel != null) {
            updateState(channel.getUID(), state, transform);
        }
    }

    protected void triggerChannel(String channelId, String event) {
        Channel channel = thing.getChannel(channelId);
        if (channel != null) {
            triggerChannel(channel.getUID(), event);
        }
    }

    protected <U> void updateState(ChannelUID channel, U state, Function<U, State> transform) {
        updateState(channel, state == null ? UnDefType.NULL : transform.apply(state));
    }
}
