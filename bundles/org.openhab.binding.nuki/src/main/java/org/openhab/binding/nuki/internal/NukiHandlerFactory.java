/**
 * Copyright (c) 2010-2022 Contributors to the openHAB project
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.openhab.binding.nuki.internal;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jetty.client.HttpClient;
import org.openhab.binding.nuki.internal.constants.NukiBindingConstants;
import org.openhab.binding.nuki.internal.constants.NukiLinkBuilder;
import org.openhab.binding.nuki.internal.dataexchange.NukiApiServlet;
import org.openhab.binding.nuki.internal.handler.NukiBridgeHandler;
import org.openhab.binding.nuki.internal.handler.NukiOpenerHandler;
import org.openhab.binding.nuki.internal.handler.NukiSmartLockHandler;
import org.openhab.binding.nuki.internal.handler.web.NukiWebAccountHandler;
import org.openhab.binding.nuki.internal.handler.web.NukiWebOpenerHandler;
import org.openhab.binding.nuki.internal.handler.web.NukiWebSmartLockHandler;
import org.openhab.core.id.InstanceUUID;
import org.openhab.core.io.net.http.HttpClientFactory;
import org.openhab.core.net.HttpServiceUtil;
import org.openhab.core.net.NetworkAddressService;
import org.openhab.core.thing.Bridge;
import org.openhab.core.thing.Thing;
import org.openhab.core.thing.ThingTypeUID;
import org.openhab.core.thing.ThingUID;
import org.openhab.core.thing.binding.BaseThingHandlerFactory;
import org.openhab.core.thing.binding.ThingHandler;
import org.openhab.core.thing.binding.ThingHandlerFactory;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.http.HttpService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link NukiHandlerFactory} is responsible for creating things and thing
 * handlers.
 *
 * @author Markus Katter - Initial contribution
 * @contributer Jan Vybíral - Improved thing id generation
 */
@Component(service = ThingHandlerFactory.class, configurationPid = "binding.nuki")
@NonNullByDefault
public class NukiHandlerFactory extends BaseThingHandlerFactory {

    private final Logger logger = LoggerFactory.getLogger(NukiHandlerFactory.class);

    private final HttpClient httpClient;
    private final NetworkAddressService networkAddressService;
    private NukiApiServlet nukiApiServlet;

    @Activate
    public NukiHandlerFactory(@Reference HttpService httpService, @Reference final HttpClientFactory httpClientFactory,
            @Reference NetworkAddressService networkAddressService) {
        this.httpClient = httpClientFactory.getCommonHttpClient();
        this.networkAddressService = networkAddressService;
        this.nukiApiServlet = new NukiApiServlet(httpService);
    }

    @Override
    public boolean supportsThingType(ThingTypeUID thingTypeUID) {
        return NukiBindingConstants.SUPPORTED_THING_TYPES_UIDS.contains(thingTypeUID);
    }

    @Override
    protected @Nullable ThingHandler createHandler(Thing thing) {
        ThingTypeUID thingTypeUID = thing.getThingTypeUID();

        if (NukiBindingConstants.THING_TYPE_BRIDGE.equals(thingTypeUID)) {
            String callbackUrl = createCallbackUrl(InstanceUUID.get());
            NukiBridgeHandler nukiBridgeHandler = new NukiBridgeHandler((Bridge) thing, httpClient, callbackUrl);
            nukiApiServlet.add(nukiBridgeHandler);
            return nukiBridgeHandler;
        } else if (NukiBindingConstants.THING_TYPE_SMARTLOCK.equals(thingTypeUID)) {
            return new NukiSmartLockHandler(thing);
        } else if (NukiBindingConstants.THING_TYPE_OPENER.equals(thingTypeUID)) {
            return new NukiOpenerHandler(thing);
        } else if (NukiBindingConstants.THING_TYPE_WEB_ACCOUNT.equals(thingTypeUID)) {
            return new NukiWebAccountHandler((Bridge) thing, httpClient);
        } else if (NukiBindingConstants.THING_TYPE_WEB_SMARTLOCK.equals(thingTypeUID)) {
            return new NukiWebSmartLockHandler(thing);
        } else if (NukiBindingConstants.THING_TYPE_WEB_OPENER.equals(thingTypeUID)) {
            return new NukiWebOpenerHandler(thing);
        }
        logger.warn("No valid Handler found for Thing[{}]!", thingTypeUID);
        return null;
    }

    @Override
    public void removeThing(ThingUID thingUID) {
        super.removeThing(thingUID);
    }

    @Override
    public void unregisterHandler(Thing thing) {
        super.unregisterHandler(thing);
        ThingHandler handler = thing.getHandler();
        if (handler instanceof NukiBridgeHandler) {
            nukiApiServlet.remove((NukiBridgeHandler) handler);
        }
    }

    private @Nullable String createCallbackUrl(String id) {
        final String ipAddress = networkAddressService.getPrimaryIpv4HostAddress();
        if (ipAddress == null) {
            logger.warn("No network interface could be found to get callback address");
            return null;
        }
        // we do not use SSL as it can cause certificate validation issues.
        final int port = HttpServiceUtil.getHttpServicePort(bundleContext);
        if (port == -1) {
            logger.warn("Cannot find port of the http service.");
            return null;
        }
        String callbackUrl = NukiLinkBuilder.callbackUri(ipAddress, port, id).toString();
        logger.trace("callbackUrl[{}]", callbackUrl);
        return callbackUrl;
    }
}
