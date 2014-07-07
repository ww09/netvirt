/*
 * Copyright (C) 2013 Red Hat, Inc.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 *
 * Authors : Madhu Venugopal, Brent Salisbury, Sam Hague, Dave Tucker
 */
package org.opendaylight.ovsdb.neutron;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;

import org.opendaylight.controller.networkconfig.neutron.NeutronNetwork;
import org.opendaylight.controller.sal.core.Node;
import org.opendaylight.controller.sal.core.NodeConnector;
import org.opendaylight.controller.sal.core.Property;
import org.opendaylight.controller.sal.core.UpdateType;
import org.opendaylight.controller.switchmanager.IInventoryListener;
import org.opendaylight.ovsdb.lib.notation.Row;
import org.opendaylight.ovsdb.lib.notation.UUID;
import org.opendaylight.ovsdb.neutron.provider.IProviderNetworkManager;
import org.opendaylight.ovsdb.plugin.OVSDBInventoryListener;
import org.opendaylight.ovsdb.schema.openvswitch.Interface;
import org.opendaylight.ovsdb.schema.openvswitch.OpenVSwitch;
import org.opendaylight.ovsdb.schema.openvswitch.Port;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SouthboundHandler extends BaseHandler implements OVSDBInventoryListener, IInventoryListener {
    static final Logger logger = LoggerFactory.getLogger(SouthboundHandler.class);
    //private Thread eventThread;
    private ExecutorService eventHandler;
    private BlockingQueue<SouthboundEvent> events;
    List<Node> nodeCache;

    // The implementation for each of these services is resolved by the OSGi Service Manager
    private volatile IAdminConfigManager adminConfigManager;
    private volatile IInternalNetworkManager internalNetworkManager;
    private volatile ITenantNetworkManager tenantNetworkManager;
    private volatile IProviderNetworkManager providerNetworkManager;

    void init() {
        eventHandler = Executors.newSingleThreadExecutor();
        this.events = new LinkedBlockingQueue<SouthboundEvent>();
        nodeCache = new ArrayList<>();
    }

    void start() {
        eventHandler.submit(new Runnable()  {
            @Override
            public void run() {
                while (true) {
                    SouthboundEvent ev;
                    try {
                        ev = events.take();
                    } catch (InterruptedException e) {
                        logger.info("The event handler thread was interrupted, shutting down", e);
                        return;
                    }
                    switch (ev.getType()) {
                    case NODE:
                        try {
                            processNodeUpdate(ev.getNode(), ev.getAction());
                        } catch (Exception e) {
                            logger.error("Exception caught in ProcessNodeUpdate for node " + ev.getNode(), e);
                        }
                        break;
                    case ROW:
                        try {
                            processRowUpdate(ev.getNode(), ev.getTableName(), ev.getUuid(), ev.getRow(),
                                             ev.getContext(),ev.getAction());
                        } catch (Exception e) {
                            logger.error("Exception caught in ProcessRowUpdate for node " + ev.getNode(), e);
                        }
                        break;
                    default:
                        logger.warn("Unable to process action " + ev.getAction() + " for node " + ev.getNode());
                    }
                }
            }
        });
        this.triggerUpdates();
    }

    void stop() {
        eventHandler.shutdownNow();
    }

    @Override
    public void nodeAdded(Node node) {
        this.enqueueEvent(new SouthboundEvent(node, SouthboundEvent.Action.ADD));
    }

    @Override
    public void nodeRemoved(Node node) {
        this.enqueueEvent(new SouthboundEvent(node, SouthboundEvent.Action.DELETE));
    }

    @Override
    public void rowAdded(Node node, String tableName, String uuid, Row row) {
        this.enqueueEvent(new SouthboundEvent(node, tableName, uuid, row, SouthboundEvent.Action.ADD));
    }

    @Override
    public void rowUpdated(Node node, String tableName, String uuid, Row oldRow, Row newRow) {
        if (this.isUpdateOfInterest(node, oldRow, newRow)) {
            this.enqueueEvent(new SouthboundEvent(node, tableName, uuid, newRow, SouthboundEvent.Action.UPDATE));
        }
    }

    /*
     * Ignore unneccesary updates to be even considered for processing.
     * (Especially stats update are fast and furious).
     */

    private boolean isUpdateOfInterest(Node node, Row oldRow, Row newRow) {
        if (oldRow == null) return true;
        if (newRow.getTableSchema().getName().equals(ovsdbConfigService.getTableName(node, Interface.class))) {
            // We are NOT interested in Stats only updates
            Interface oldIntf = ovsdbConfigService.getTypedRow(node, Interface.class, oldRow);
            if (oldIntf.getName() == null && oldIntf.getExternalIdsColumn() == null && oldIntf.getMacColumn() == null &&
                oldIntf.getOpenFlowPortColumn() == null && oldIntf.getOptionsColumn() == null && oldIntf.getOtherConfigColumn() == null &&
                oldIntf.getTypeColumn() == null) {
                logger.trace("IGNORING Interface Update: node {}, row: {}", node, newRow);
                return false;
            }
        } else if (newRow.getTableSchema().getName().equals(ovsdbConfigService.getTableName(node, Port.class))) {
            // We are NOT interested in Stats only updates
            Port oldPort = ovsdbConfigService.getTypedRow(node, Port.class, oldRow);
            if (oldPort.getName() == null && oldPort.getExternalIdsColumn() == null && oldPort.getMacColumn() == null &&
                oldPort.getInterfacesColumn() == null && oldPort.getTagColumn() == null && oldPort.getTrunksColumn() == null) {
                logger.trace("IGNORING Port Update: node {}, row: {}", node, newRow);
                return false;
            }
        } else if (newRow.getTableSchema().getName().equals(ovsdbConfigService.getTableName(node, OpenVSwitch.class))) {
            OpenVSwitch oldOpenvSwitch = ovsdbConfigService.getTypedRow(node, OpenVSwitch.class, oldRow);
            if (oldOpenvSwitch.getOtherConfigColumn()== null) {
                /* we are only interested in other_config field change */
                return false;
            }
        }
        return true;
    }

    @Override
    public void rowRemoved(Node node, String tableName, String uuid, Row row, Object context) {
        this.enqueueEvent(new SouthboundEvent(node, tableName, uuid, row, context, SouthboundEvent.Action.DELETE));
    }

    private void enqueueEvent (SouthboundEvent event) {
        try {
            events.put(event);
        } catch (InterruptedException e) {
            logger.error("Thread was interrupted while trying to enqueue event ", e);
        }
    }

    public void processNodeUpdate(Node node, SouthboundEvent.Action action) {
        if (action == SouthboundEvent.Action.DELETE) return;
        logger.trace("Process Node added {}", node);
        internalNetworkManager.prepareInternalNetwork(node);
    }

    private void processRowUpdate(Node node, String tableName, String uuid, Row row,
                                  Object context, SouthboundEvent.Action action) {
        if (action == SouthboundEvent.Action.DELETE) {
            if (ovsdbConfigService.getTableName(node, Interface.class).equalsIgnoreCase(tableName)) {
                logger.debug("processRowUpdate: {} Deleted node: {}, uuid: {}, row: {}", tableName, node, uuid, row);
                Interface deletedIntf = ovsdbConfigService.getTypedRow(node, Interface.class, row);
                NeutronNetwork network = null;
                if (context == null) {
                    network = tenantNetworkManager.getTenantNetworkForInterface(deletedIntf);
                } else {
                    network = (NeutronNetwork)context;
                }
                List<String> phyIfName = adminConfigManager.getAllPhysicalInterfaceNames(node);
                logger.info("Delete interface " + deletedIntf.getName());

                if (deletedIntf.getTypeColumn().getData().equalsIgnoreCase(NetworkHandler.NETWORK_TYPE_VXLAN) ||
                    deletedIntf.getTypeColumn().getData().equalsIgnoreCase(NetworkHandler.NETWORK_TYPE_GRE) ||
                    phyIfName.contains(deletedIntf.getName())) {
                    /* delete tunnel interfaces or physical interfaces */
                    this.handleInterfaceDelete(node, uuid, deletedIntf, false, null);
                } else if (network != null && !network.getRouterExternal()) {
                    try {
                        ConcurrentMap<String, Row> interfaces = this.ovsdbConfigService.getRows(node, ovsdbConfigService.getTableName(node, Interface.class));
                        if (interfaces != null) {
                            boolean isLastInstanceOnNode = true;
                            for (String intfUUID : interfaces.keySet()) {
                                if (intfUUID.equals(uuid)) continue;
                                Interface intf = this.ovsdbConfigService.getTypedRow(node, Interface.class, interfaces.get(intfUUID));
                                NeutronNetwork neutronNetwork = tenantNetworkManager.getTenantNetworkForInterface(intf);
                                if (neutronNetwork != null && neutronNetwork.equals(network)) isLastInstanceOnNode = false;
                            }
                            this.handleInterfaceDelete(node, uuid, deletedIntf, isLastInstanceOnNode, network);
                        }
                    } catch (Exception e) {
                        logger.error("Error fetching Interface Rows for node " + node, e);
                    }
                }
            }
        }
        else if (ovsdbConfigService.getTableName(node, Interface.class).equalsIgnoreCase(tableName)) {
            logger.debug("processRowUpdate: {} Added / Updated node: {}, uuid: {}, row: {}", tableName, node, uuid, row);
            Interface intf = this.ovsdbConfigService.getTypedRow(node, Interface.class, row);
            NeutronNetwork network = tenantNetworkManager.getTenantNetworkForInterface(intf);
            if (network != null && !network.getRouterExternal()) {
                if (providerNetworkManager.getProvider().hasPerTenantTunneling()) {
                    int vlan = tenantNetworkManager.networkCreated(node, network.getID());
                    logger.trace("Neutron Network {}:{} Created with Internal Vlan: {}", network.getNetworkUUID(), network.getNetworkName(), vlan);

                    String portUUID = this.getPortIdForInterface(node, uuid, intf);
                    if (portUUID != null) {
                        tenantNetworkManager.programTenantNetworkInternalVlan(node, portUUID, network);
                    }
                }
                this.handleInterfaceUpdate(node, uuid, intf);
            }
        } else if (ovsdbConfigService.getTableName(node, Port.class).equalsIgnoreCase(tableName)) {
            logger.debug("processRowUpdate: {} Added / Updated node: {}, uuid: {}, row: {}", tableName, node, uuid, row);
            Port port = this.ovsdbConfigService.getTypedRow(node, Port.class, row);
            Set<UUID> interfaceUUIDs = port.getInterfacesColumn().getData();
            for (UUID intfUUID : interfaceUUIDs) {
                logger.trace("Scanning interface "+intfUUID);
                try {
                    Row intfRow = this.ovsdbConfigService.getRow(node, ovsdbConfigService.getTableName(node, Interface.class), intfUUID.toString());
                    Interface intf = this.ovsdbConfigService.getTypedRow(node, Interface.class, intfRow);
                    NeutronNetwork network = tenantNetworkManager.getTenantNetworkForInterface(intf);
                    if (network != null && !network.getRouterExternal()) {
                        tenantNetworkManager.programTenantNetworkInternalVlan(node, uuid, network);
                        this.handleInterfaceUpdate(node, intfUUID.toString(), intf);
                    } else {
                        logger.trace("ignore update because there is not a neutron network.");
                    }
                } catch (Exception e) {
                    logger.error("Failed to process row update", e);
                }
            }
        } else if (ovsdbConfigService.getTableName(node, OpenVSwitch.class).equalsIgnoreCase(tableName)) {
            logger.debug("processRowUpdate: {} Added / Updated node: {}, uuid: {}, row: {}", tableName, node, uuid, row);
            try {
                ConcurrentMap<String, Row> interfaces = this.ovsdbConfigService.getRows(node, ovsdbConfigService.getTableName(node, Interface.class));
                if (interfaces != null) {
                    for (String intfUUID : interfaces.keySet()) {
                        Interface intf = ovsdbConfigService.getTypedRow(node, Interface.class, interfaces.get(intfUUID));
                        this.handleInterfaceUpdate(node, intfUUID, intf);
                    }
                }
            } catch (Exception e) {
                logger.error("Error fetching Interface Rows for node " + node, e);
            }
        }
    }

    private void handleInterfaceUpdate (Node node, String uuid, Interface intf) {
        logger.debug("handleInterfaceUpdate: node: {}, uuid: {}", node, uuid);
        NeutronNetwork network = tenantNetworkManager.getTenantNetworkForInterface(intf);
        if (network != null) {
            if (internalNetworkManager.checkAndCreateNetwork(node, network)) {
                providerNetworkManager.getProvider().handleInterfaceUpdate(network, node, intf);
            }
        }
    }

    private void handleInterfaceDelete (Node node, String uuid, Interface intf, boolean isLastInstanceOnNode,
                                        NeutronNetwork network) {
        logger.debug("handleInterfaceDelete: node: {}, uuid: {}, isLastInstanceOnNode: {}, interface: {}",
                node, uuid, isLastInstanceOnNode, intf);

        List<String> phyIfName = adminConfigManager.getAllPhysicalInterfaceNames(node);
        if (intf.getTypeColumn().getData().equalsIgnoreCase(NetworkHandler.NETWORK_TYPE_VXLAN) ||
            intf.getTypeColumn().getData().equalsIgnoreCase(NetworkHandler.NETWORK_TYPE_GRE) ||
            phyIfName.contains(intf.getName())) {
            /* delete tunnel or physical interfaces */
            providerNetworkManager.getProvider().handleInterfaceDelete(intf.getTypeColumn().getData(), null, node, intf, isLastInstanceOnNode);
        } else if (network != null) {
            if (!network.getProviderNetworkType().equalsIgnoreCase(NetworkHandler.NETWORK_TYPE_VLAN)) { /* vlan doesn't need a tunnel endpoint */
                if (adminConfigManager.getTunnelEndPoint(node) == null) {
                    logger.error("Tunnel end-point configuration missing. Please configure it in OpenVSwitch Table");
                    return;
                }
            }
            if (isLastInstanceOnNode & providerNetworkManager.getProvider().hasPerTenantTunneling()) {
                tenantNetworkManager.reclaimTenantNetworkInternalVlan(node, uuid, network);
            }
            providerNetworkManager.getProvider().handleInterfaceDelete(network.getProviderNetworkType(), network, node, intf, isLastInstanceOnNode);
        }
    }

    private String getPortIdForInterface (Node node, String uuid, Interface intf) {
        try {
            Map<String, Row> ports = this.ovsdbConfigService.getRows(node, ovsdbConfigService.getTableName(node, Port.class));
            if (ports == null) return null;
            for (String portUUID : ports.keySet()) {
                Port port = ovsdbConfigService.getTypedRow(node, Port.class, ports.get(portUUID));
                Set<UUID> interfaceUUIDs = port.getInterfacesColumn().getData();
                logger.trace("Scanning Port {} to identify interface : {} ",port, uuid);
                for (UUID intfUUID : interfaceUUIDs) {
                    if (intfUUID.toString().equalsIgnoreCase(uuid)) {
                        logger.trace("Found Interface {} -> {}", uuid, portUUID);
                        return portUUID;
                    }
                }
            }
        } catch (Exception e) {
            logger.debug("Failed to add Port tag for for Intf {}",intf, e);
        }
        return null;
    }

    @Override
    public void notifyNode(Node node, UpdateType type, Map<String, Property> propMap) {
        logger.debug("notifyNode: Node {} update {} from Controller's inventory Service", node, type);

        // Add the Node Type check back once the Consistency issue is resolved between MD-SAL and AD-SAL
        if (!type.equals(UpdateType.REMOVED) && !nodeCache.contains(node)) {
            nodeCache.add(node);
            providerNetworkManager.getProvider().initializeOFFlowRules(node);
        } else if (type.equals(UpdateType.REMOVED)){
            nodeCache.remove(node);
        }
    }

    @Override
    public void notifyNodeConnector(NodeConnector nodeConnector, UpdateType type, Map<String, Property> propMap) {
        //We are not interested in the nodeConnectors at this moment
    }

    private void triggerUpdates() {
        List<Node> nodes = this.getConnectionService().getNodes();
        if (nodes == null) return;
        for (Node node : nodes) {
            try {
                List<String> tableNames = this.getOVSDBConfigService().getTables(node);
                if (tableNames == null) continue;
                for (String tableName : tableNames) {
                    Map<String, Row> rows = this.getOVSDBConfigService().getRows(node, tableName);
                    if (rows == null) continue;
                    for (String uuid : rows.keySet()) {
                        Row row = rows.get(uuid);
                        this.rowAdded(node, tableName, uuid, row);
                    }
                }
            } catch (Exception e) {
                logger.error("Exception during OVSDB Southbound update trigger", e);
            }
        }
    }
}
