/*
 * Copyright (c) 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.natservice.internal;

import com.google.common.base.Optional;
import javax.annotation.PostConstruct;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.genius.datastoreutils.AsyncDataTreeChangeListenerBase;
import org.opendaylight.genius.datastoreutils.SingleTransactionDataBroker;
import org.opendaylight.genius.mdsalutil.MDSALUtil;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.Uuid;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.FloatingIpInfo;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.floating.ip.info.RouterPorts;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.floating.ip.info.RouterPortsBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.floating.ip.info.RouterPortsKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.router.to.vpn.mapping.Routermapping;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.router.to.vpn.mapping.RoutermappingBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.router.to.vpn.mapping.RoutermappingKey;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class RouterPortsListener
    extends AsyncDataTreeChangeListenerBase<RouterPorts, RouterPortsListener> {

    private static final Logger LOG = LoggerFactory.getLogger(RouterPortsListener.class);
    private final DataBroker dataBroker;

    @Inject
    public RouterPortsListener(final DataBroker dataBroker) {
        super(RouterPorts.class, RouterPortsListener.class);
        this.dataBroker = dataBroker;
    }

    @Override
    @PostConstruct
    public void init() {
        LOG.info("{} init", getClass().getSimpleName());
        registerListener(LogicalDatastoreType.CONFIGURATION, dataBroker);
    }

    @Override
    protected InstanceIdentifier<RouterPorts> getWildCardPath() {
        return InstanceIdentifier.create(FloatingIpInfo.class).child(RouterPorts.class);
    }

    @Override
    protected RouterPortsListener getDataTreeChangeListener() {
        return RouterPortsListener.this;
    }

    @Override
    protected void add(final InstanceIdentifier<RouterPorts> identifier, final RouterPorts routerPorts) {
        LOG.trace("add : key:{}  value:{}",routerPorts.getKey(), routerPorts);
        Optional<RouterPorts> optRouterPorts =
                SingleTransactionDataBroker.syncReadOptionalAndTreatReadFailedExceptionAsAbsentOptional(dataBroker,
                        LogicalDatastoreType.OPERATIONAL, identifier);
        if (optRouterPorts.isPresent()) {
            RouterPorts ports = optRouterPorts.get();
            String routerName = ports.getRouterId();
            MDSALUtil.syncUpdate(dataBroker, LogicalDatastoreType.OPERATIONAL, identifier,
                new RouterPortsBuilder().setKey(new RouterPortsKey(routerName)).setRouterId(routerName)
                    .setExternalNetworkId(routerPorts.getExternalNetworkId()).build());
        } else {
            String routerName = routerPorts.getRouterId();
            MDSALUtil.syncWrite(dataBroker, LogicalDatastoreType.OPERATIONAL, identifier,
                new RouterPortsBuilder().setKey(new RouterPortsKey(routerName)).setRouterId(routerName)
                    .setExternalNetworkId(routerPorts.getExternalNetworkId()).build());
        }
        //Check if the router is associated with any BGP VPN and update the association
        String routerName = routerPorts.getRouterId();
        Uuid vpnName = NatUtil.getVpnForRouter(dataBroker, routerName);
        if (vpnName != null) {
            InstanceIdentifier<Routermapping> routerMappingId = NatUtil.getRouterVpnMappingId(routerName);
            Optional<Routermapping> optRouterMapping =
                    SingleTransactionDataBroker.syncReadOptionalAndTreatReadFailedExceptionAsAbsentOptional(dataBroker,
                            LogicalDatastoreType.OPERATIONAL, routerMappingId);
            if (!optRouterMapping.isPresent()) {
                Long vpnId = NatUtil.getVpnId(dataBroker, vpnName.getValue());
                LOG.debug("add : Updating router {} to VPN {} association with Id {}", routerName, vpnName, vpnId);
                Routermapping routerMapping = new RoutermappingBuilder().setKey(new RoutermappingKey(routerName))
                    .setRouterName(routerName).setVpnName(vpnName.getValue()).setVpnId(vpnId).build();
                MDSALUtil.syncWrite(dataBroker, LogicalDatastoreType.OPERATIONAL, routerMappingId, routerMapping);
            }
        }
    }

    @Override
    protected void remove(InstanceIdentifier<RouterPorts> identifier, RouterPorts routerPorts) {
        LOG.trace("remove : key:{}  value:{}",routerPorts.getKey(), routerPorts);
        //MDSALUtil.syncDelete(dataBroker, LogicalDatastoreType.OPERATIONAL, identifier);
        //Remove the router to vpn association mapping entry if at all present
        String routerName = routerPorts.getRouterId();
        Uuid vpnName = NatUtil.getVpnForRouter(dataBroker, routerName);
        if (vpnName != null) {
            MDSALUtil.syncDelete(dataBroker, LogicalDatastoreType.OPERATIONAL,
                NatUtil.getRouterVpnMappingId(routerName));
        }
    }

    @Override
    protected void update(InstanceIdentifier<RouterPorts> identifier, RouterPorts original, RouterPorts update) {
        LOG.trace("Update : key: {}, original:{}, update:{}",update.getKey(), original, update);
    }
}
