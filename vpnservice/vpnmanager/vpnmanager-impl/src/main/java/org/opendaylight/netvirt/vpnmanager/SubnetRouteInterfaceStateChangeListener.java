/*
 * Copyright (c) 2015, 2017 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.vpnmanager;

import com.google.common.base.Optional;
import com.google.common.util.concurrent.ListenableFuture;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.genius.datastoreutils.AsyncDataTreeChangeListenerBase;
import org.opendaylight.genius.datastoreutils.DataStoreJobCoordinator;
import org.opendaylight.netvirt.neutronvpn.interfaces.INeutronVpnManager;
import org.opendaylight.netvirt.vpnmanager.utilities.InterfaceUtils;
import org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.vpn.interfaces.VpnInterface;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana._if.type.rev140508.L2vlan;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.InterfacesState;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.state.Interface;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.Uuid;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.port.op.data.PortOpDataEntry;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.ports.rev150712.ports.attributes.ports.Port;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SubnetRouteInterfaceStateChangeListener extends AsyncDataTreeChangeListenerBase<Interface,
    SubnetRouteInterfaceStateChangeListener> implements AutoCloseable {
    private static final Logger LOG = LoggerFactory.getLogger(SubnetRouteInterfaceStateChangeListener.class);
    private static final String LOGGING_PREFIX = "SUBNETROUTE:";
    private final DataBroker dataBroker;
    private final VpnInterfaceManager vpnInterfaceManager;
    private final VpnSubnetRouteHandler vpnSubnetRouteHandler;
    private final SubnetOpDpnManager subOpDpnManager;
    private final INeutronVpnManager neutronVpnManager;

    public SubnetRouteInterfaceStateChangeListener(final DataBroker dataBroker,
        final VpnInterfaceManager vpnInterfaceManager,
        final VpnSubnetRouteHandler vpnSubnetRouteHandler,
        final SubnetOpDpnManager subnetOpDpnManager,
        final INeutronVpnManager neutronVpnService) {
        super(Interface.class, SubnetRouteInterfaceStateChangeListener.class);
        this.dataBroker = dataBroker;
        this.vpnInterfaceManager = vpnInterfaceManager;
        this.vpnSubnetRouteHandler = vpnSubnetRouteHandler;
        this.subOpDpnManager = subnetOpDpnManager;
        this.neutronVpnManager = neutronVpnService;
    }

    public void start() {
        LOG.info("{} start", getClass().getSimpleName());
        registerListener(LogicalDatastoreType.OPERATIONAL, dataBroker);
    }

    @Override
    protected InstanceIdentifier<Interface> getWildCardPath() {
        return InstanceIdentifier.create(InterfacesState.class).child(Interface.class);
    }

    @Override
    protected SubnetRouteInterfaceStateChangeListener getDataTreeChangeListener() {
        return SubnetRouteInterfaceStateChangeListener.this;
    }

    // TODO Clean up the exception handling
    @SuppressWarnings("checkstyle:IllegalCatch")
    @Override
    protected void add(InstanceIdentifier<Interface> identifier, Interface intrf) {
        LOG.trace("{} add: Received interface {} up event", LOGGING_PREFIX, intrf);
        final Uuid subnetId;
        try {
            if (L2vlan.class.equals(intrf.getType())) {
                LOG.trace("SubnetRouteInterfaceListener add: Received interface {} up event", intrf);
                if (intrf.getOperStatus().equals(Interface.OperStatus.Up)) {
                    subnetId = getSubnetId(intrf);
                    if (subnetId == null) {
                        LOG.error("SubnetRouteInterfaceListener add: Port {} doesnt exist in configDS",
                                intrf.getName());
                        return;
                    }
                    DataStoreJobCoordinator dataStoreCoordinator = DataStoreJobCoordinator.getInstance();
                    dataStoreCoordinator.enqueueJob("SUBNETROUTE-" + subnetId,
                        () -> {
                            List<ListenableFuture<Void>> futures = new ArrayList<>();
                            String interfaceName = intrf.getName();
                            LOG.info("{} add: Received port UP event for interface {} subnetId {}",
                                    LOGGING_PREFIX, interfaceName, subnetId.getValue());
                            try {
                                BigInteger dpnId = InterfaceUtils.getDpIdFromInterface(intrf);
                                vpnSubnetRouteHandler.onInterfaceUp(dpnId, intrf.getName(), subnetId);
                            } catch (Exception e) {
                                LOG.error("{} add: Unable to obtain dpnId for interface {} in subnet {},"
                                        + " subnetroute inclusion for this interface failed with exception {}",
                                        LOGGING_PREFIX, interfaceName, subnetId.getValue(), e);
                            }
                            return futures;
                        });
                }
            }
            LOG.info("{} add: Processed interface {} up event", LOGGING_PREFIX, intrf.getName());
        } catch (Exception e) {
            LOG.error("{} add: Exception observed in handling addition for VPN Interface {}.", LOGGING_PREFIX,
                intrf.getName(), e);
        }
    }

    // TODO Clean up the exception handling
    @SuppressWarnings("checkstyle:IllegalCatch")
    @Override
    protected void remove(InstanceIdentifier<Interface> identifier, Interface intrf) {
        final Uuid subnetId;
        try {
            if (L2vlan.class.equals(intrf.getType())) {
                LOG.trace("SubnetRouteInterfaceListener remove: Received interface {} down event", intrf);
                subnetId = getSubnetId(intrf);
                if (subnetId == null) {
                    LOG.error("SubnetRouteInterfaceListener add: Port {} doesnt exist in configDS",
                            intrf.getName());
                    return;
                }
                DataStoreJobCoordinator dataStoreCoordinator = DataStoreJobCoordinator.getInstance();
                dataStoreCoordinator.enqueueJob("SUBNETROUTE-" + subnetId,
                    () -> {
                        String interfaceName = intrf.getName();
                        BigInteger dpnId = BigInteger.ZERO;
                        LOG.info("{} remove: Received port DOWN event for interface {} in subnet {} ",
                                LOGGING_PREFIX, interfaceName, subnetId.getValue());
                        try {
                            dpnId = InterfaceUtils.getDpIdFromInterface(intrf);
                        } catch (Exception e) {
                            LOG.error("{} remove: Unable to retrieve dpnId for interface {} in subnet {}. "
                                            + "Fetching from vpn interface itself due to exception {}",
                                    LOGGING_PREFIX, intrf.getName(), subnetId.getValue(), e);
                            InstanceIdentifier<VpnInterface> id = VpnUtil
                                    .getVpnInterfaceIdentifier(interfaceName);
                            Optional<VpnInterface> optVpnInterface = VpnUtil.read(dataBroker,
                                    LogicalDatastoreType.OPERATIONAL, id);
                            if (optVpnInterface.isPresent()) {
                                dpnId = optVpnInterface.get().getDpnId();
                            }
                        }
                        if (!dpnId.equals(BigInteger.ZERO)) {
                            vpnSubnetRouteHandler.onInterfaceDown(dpnId, intrf.getName(), subnetId);
                        }
                        List<ListenableFuture<Void>> futures = new ArrayList<>();
                        return futures;
                    });
            }
            LOG.info("{} remove: Processed interface {} down event in ", LOGGING_PREFIX, intrf.getName());
        } catch (Exception e) {
            LOG.error("{} remove: Exception observed in handling deletion of VPN Interface {}.", LOGGING_PREFIX,
                intrf.getName(), e);
        }
    }

    // TODO Clean up the exception handling
    @SuppressWarnings("checkstyle:IllegalCatch")
    @Override
    protected void update(InstanceIdentifier<Interface> identifier,
        Interface original, Interface update) {
        final Uuid subnetId;
        try {
            String interfaceName = update.getName();
            if (L2vlan.class.equals(update.getType())) {
                LOG.trace("{} update: Operation Interface update event - Old: {}, New: {}", LOGGING_PREFIX,
                    original, update);
                subnetId = getSubnetId(update);
                if (subnetId == null) {
                    LOG.error("SubnetRouteInterfaceListener update: Port {} doesnt exist in configDS",
                            update.getName());
                    return;
                }
                DataStoreJobCoordinator dataStoreCoordinator = DataStoreJobCoordinator.getInstance();
                dataStoreCoordinator.enqueueJob("SUBNETROUTE-" + subnetId,
                    () -> {
                        List<ListenableFuture<Void>> futures = new ArrayList<>();
                        BigInteger dpnId = BigInteger.ZERO;
                        try {
                            dpnId = InterfaceUtils.getDpIdFromInterface(update);
                        } catch (Exception e) {
                            LOG.error("{} remove: Unable to retrieve dpnId for interface {} in subnet  {}. "
                                    + "Fetching from vpn interface itself due to exception {}", LOGGING_PREFIX,
                                    update.getName(), subnetId.getValue(), e);
                            InstanceIdentifier<VpnInterface> id = VpnUtil
                                    .getVpnInterfaceIdentifier(interfaceName);
                            Optional<VpnInterface> optVpnInterface = VpnUtil.read(dataBroker,
                                    LogicalDatastoreType.OPERATIONAL, id);
                            if (optVpnInterface.isPresent()) {
                                dpnId = optVpnInterface.get().getDpnId();
                            }
                        }
                        if (!dpnId.equals(BigInteger.ZERO)) {
                            if (update.getOperStatus().equals(Interface.OperStatus.Up)) {
                                LOG.info("{} update: Received port UP event for interface {} in subnet {}",
                                        LOGGING_PREFIX, update.getName(), subnetId.getValue());
                                vpnSubnetRouteHandler.onInterfaceUp(dpnId, update.getName(), subnetId);
                            } else if (update.getOperStatus().equals(Interface.OperStatus.Down)
                                    || update.getOperStatus().equals(Interface.OperStatus.Unknown)) {
                                /*
                                 * If the interface went down voluntarily (or) if the interface is not
                                 * reachable from control-path involuntarily, trigger subnetRoute election
                                 */
                                LOG.info("{} update: Received port {} event for interface {} in subnet {} ",
                                        LOGGING_PREFIX, update.getOperStatus().equals(Interface.OperStatus.Unknown)
                                                ? "UNKNOWN" : "DOWN", update.getName(), subnetId.getValue());
                                vpnSubnetRouteHandler.onInterfaceDown(dpnId, update.getName(), subnetId);
                            }
                        }
                        return futures;
                    });
            }
            LOG.info("{} update: Processed Interface {} update event", LOGGING_PREFIX, update.getName());
        } catch (Exception e) {
            LOG.error("{} update: Exception observed in handling deletion of VPNInterface {}", LOGGING_PREFIX,
                    update.getName(), e);
        }
    }

    protected Uuid getSubnetId(Interface intrf) {

        if (!VpnUtil.isUuidValidPattern(intrf.getName())) {
            LOG.error("SubnetRouteInterfaceListener: Interface {} doesnt have valid uuid pattern", intrf.getName());
            return null;
        }

        PortOpDataEntry portOpEntry = subOpDpnManager.getPortOpDataEntry(intrf.getName());
        if (portOpEntry != null) {
            return portOpEntry.getSubnetId();
        }
        LOG.trace("SubnetRouteInterfaceListener : Received Port {} event for {} that is not part of subnetRoute",
                intrf.getOperStatus(), intrf.getName());
        Port port = neutronVpnManager.getNeutronPort(intrf.getName());
        if (port != null && port.getFixedIps() != null && port.getFixedIps().size() > 0) {
            return port.getFixedIps().get(0).getSubnetId();
        } else {
            return null;
        }
    }
}
