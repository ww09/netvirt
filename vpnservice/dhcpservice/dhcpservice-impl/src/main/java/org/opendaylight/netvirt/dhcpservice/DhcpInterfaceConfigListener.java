/*
 * Copyright (c) 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.dhcpservice;

import java.util.Collections;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.WriteTransaction;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.genius.datastoreutils.AsyncDataTreeChangeListenerBase;
import org.opendaylight.genius.mdsalutil.NwConstants;
import org.opendaylight.infrautils.jobcoordinator.JobCoordinator;
import org.opendaylight.netvirt.dhcpservice.api.DhcpMConstants;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.IpAddress;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.Interfaces;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.Interface;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rev160406.IfL2vlan;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rev160406.IfTunnel;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rev160406.ParentRefs;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.ports.rev150712.ports.attributes.ports.Port;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.subnets.rev150712.subnets.attributes.subnets.Subnet;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DhcpInterfaceConfigListener
        extends AsyncDataTreeChangeListenerBase<Interface, DhcpInterfaceConfigListener> {

    private static final Logger LOG = LoggerFactory.getLogger(DhcpInterfaceConfigListener.class);

    private final DataBroker dataBroker;
    private final DhcpExternalTunnelManager dhcpExternalTunnelManager;
    private final DhcpManager dhcpManager;
    private final JobCoordinator jobCoordinator;

    public DhcpInterfaceConfigListener(DataBroker dataBroker,
            DhcpExternalTunnelManager dhcpExternalTunnelManager, DhcpManager dhcpManager,
            JobCoordinator jobCoordinator) {
        super(Interface.class, DhcpInterfaceConfigListener.class);
        this.dataBroker = dataBroker;
        this.dhcpExternalTunnelManager = dhcpExternalTunnelManager;
        this.dhcpManager = dhcpManager;
        this.jobCoordinator = jobCoordinator;
        registerListener(LogicalDatastoreType.CONFIGURATION, dataBroker);
    }

    @Override
    public void close() {
        super.close();
        LOG.info("DhcpInterfaceConfigListener Closed");
    }

    @Override
    protected void remove(InstanceIdentifier<Interface> identifier, Interface del) {
        jobCoordinator.enqueueJob(DhcpServiceUtils.getJobKey(del.getName()), () -> {
            IfTunnel tunnelInterface = del.getAugmentation(IfTunnel.class);
            IfL2vlan vlanInterface = del.getAugmentation(IfL2vlan.class);
            String interfaceName = del.getName();
            if (tunnelInterface != null && !tunnelInterface.isInternal()) {
                IpAddress tunnelIp = tunnelInterface.getTunnelDestination();
                ParentRefs interfce = del.getAugmentation(ParentRefs.class);
                if (interfce != null) {
                    if (LOG.isTraceEnabled()) {
                        LOG.trace("Calling handleTunnelStateDown for tunnelIp {} and interface {}",
                                tunnelIp, interfaceName);
                    }
                    return dhcpExternalTunnelManager.handleTunnelStateDown(tunnelIp,
                            interfce.getDatapathNodeIdentifier());
                }
            }
            if (vlanInterface != null) {
                WriteTransaction unbindTx = dataBroker.newWriteOnlyTransaction();
                DhcpServiceUtils.unbindDhcpService(interfaceName, unbindTx);
                return Collections.singletonList(unbindTx.submit());
            }
            return Collections.emptyList();
        }, DhcpMConstants.RETRY_COUNT);
    }

    @Override
    protected void update(InstanceIdentifier<Interface> identifier, Interface original, Interface update) {
        // Handled in update () DhcpInterfaceEventListener
    }

    @Override
    protected void add(InstanceIdentifier<Interface> identifier, Interface add) {
        jobCoordinator.enqueueJob(DhcpServiceUtils.getJobKey(add.getName()), () -> {
            String interfaceName = add.getName();
            IfL2vlan vlanInterface = add.getAugmentation(IfL2vlan.class);
            if (vlanInterface == null) {
                return Collections.emptyList();
            }
            Port port = dhcpManager.getNeutronPort(interfaceName);
            Subnet subnet = dhcpManager.getNeutronSubnet(port);
            if (null != subnet && subnet.isEnableDhcp()) {
                WriteTransaction bindServiceTx = dataBroker.newWriteOnlyTransaction();
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Binding DHCP service for interface {}", interfaceName);
                }
                DhcpServiceUtils.bindDhcpService(interfaceName, NwConstants.DHCP_TABLE, bindServiceTx);
                return Collections.singletonList(bindServiceTx.submit());
            }
            return Collections.emptyList();
        }, DhcpMConstants.RETRY_COUNT);
    }

    @Override
    protected InstanceIdentifier<Interface> getWildCardPath() {
        return InstanceIdentifier.create(Interfaces.class).child(Interface.class);
    }

    @Override
    protected DhcpInterfaceConfigListener getDataTreeChangeListener() {
        return DhcpInterfaceConfigListener.this;
    }
}
