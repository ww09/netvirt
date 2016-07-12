/*
 * Copyright (c) 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.neutronvpn;


import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.DataChangeListener;
import org.opendaylight.controller.md.sal.common.api.data.AsyncDataBroker.DataChangeScope;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.genius.mdsalutil.AbstractDataChangeListener;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.Uuid;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.bgpvpns.rev150903.bgpvpns.attributes.Bgpvpns;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.bgpvpns.rev150903.bgpvpns.attributes.bgpvpns.Bgpvpn;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.bgpvpns.rev150903.BgpvpnTypeBase;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.bgpvpns.rev150903.BgpvpnTypeL3;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.idmanager.rev160406.CreateIdPoolInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.idmanager.rev160406.CreateIdPoolInputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.idmanager.rev160406.IdManagerService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.vpnmaps.VpnMap;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.rev150712.Neutron;
import org.opendaylight.yangtools.concepts.ListenerRegistration;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.yangtools.yang.common.RpcResult;
import org.osgi.framework.BundleContext;
import org.osgi.framework.FrameworkUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;


public class NeutronBgpvpnChangeListener extends AbstractDataChangeListener<Bgpvpn> implements AutoCloseable {
    private static final Logger LOG = LoggerFactory.getLogger(NeutronBgpvpnChangeListener.class);

    private ListenerRegistration<DataChangeListener> listenerRegistration;
    private NeutronvpnManager nvpnManager;
    private IdManagerService idManager;
    private String adminRDValue;
    private DataBroker dbroker;


    public NeutronBgpvpnChangeListener(final DataBroker db, NeutronvpnManager nVpnMgr) {
        super(Bgpvpn.class);
        nvpnManager = nVpnMgr;
        dbroker = db;
        registerListener(db);
        BundleContext bundleContext=FrameworkUtil.getBundle(NeutronBgpvpnChangeListener.class).getBundleContext();
        adminRDValue = bundleContext.getProperty(NeutronConstants.RD_PROPERTY_KEY);
    }

    @Override
    public void close() throws Exception {
        if (listenerRegistration != null) {
            try {
                listenerRegistration.close();
            } catch (final Exception e) {
                LOG.error("Error when cleaning up DataChangeListener.", e);
            }
            listenerRegistration = null;
        }
        LOG.info("N_Bgpvpn listener Closed");
    }


    private void registerListener(final DataBroker db) {
        try {
            listenerRegistration = db.registerDataChangeListener(LogicalDatastoreType.CONFIGURATION,
                    InstanceIdentifier.create(Neutron.class).child(Bgpvpns.class).child(Bgpvpn.class),
                    NeutronBgpvpnChangeListener.this, DataChangeScope.SUBTREE);
        } catch (final Exception e) {
            LOG.error("Neutron Manager Bgpvpn DataChange listener registration fail!", e);
            throw new IllegalStateException("Neutron Manager Bgpvpn DataChange listener registration failed.", e);
        }
    }

    public void setIdManager(IdManagerService idManager) {
        this.idManager = idManager;
        createIdPool();
    }

    private boolean isBgpvpnTypeL3(Class<? extends BgpvpnTypeBase> bgpvpnType) {
        if (BgpvpnTypeL3.class.equals(bgpvpnType)) {
            return true;
        } else {
            LOG.warn("CRUD operations supported only for L3 type Bgpvpn");
            return false;
        }
    }

    @Override
    protected void add(InstanceIdentifier<Bgpvpn> identifier, Bgpvpn input) {
        if (LOG.isTraceEnabled()) {
            LOG.trace("Adding Bgpvpn : key: " + identifier + ", value=" + input);
        }
        if (isBgpvpnTypeL3(input.getType())) {
            // Create internal VPN
            // handle route-target
            List<String> importRouteTargets = new ArrayList<>();
            List<String> exportRouteTargets = new ArrayList<>();
            List<String> inputRouteList = input.getRouteTargets();
            List<String> inputImportRouteList = input.getImportTargets();
            List<String> inputExportRouteList = input.getExportTargets();
            if (inputRouteList != null && !inputRouteList.isEmpty()) {
                importRouteTargets.addAll(inputRouteList);
                exportRouteTargets.addAll(inputRouteList);
            }
            if (inputImportRouteList != null && !inputImportRouteList.isEmpty()) {
                importRouteTargets.addAll(inputImportRouteList);
            }
            if (inputExportRouteList != null && !inputExportRouteList.isEmpty()) {
                exportRouteTargets.addAll(inputExportRouteList);
            }
            List<String> rd = input.getRouteDistinguishers();

            if (rd == null || rd.isEmpty()) {
                // generate new RD
                rd = generateNewRD(input.getUuid());
            }else {
                String[] rdParams = rd.get(0).split(":");
                if (rdParams[0].trim().equals(adminRDValue)) {
                    LOG.error("AS specific part of RD should not be same as that defined by DC Admin");
                    return;
                }
            }
            Uuid router = null;
            if (input.getRouters() != null && !input.getRouters().isEmpty()) {
                // currently only one router
                router = input.getRouters().get(0);
            }
            if (rd != null) {
                nvpnManager.createL3Vpn(input.getUuid(), input.getName(), input.getTenantId(), rd,
                        importRouteTargets, exportRouteTargets, router, input.getNetworks());
            }else {
                LOG.error("Create BgpVPN with id " + input.getUuid() + " failed due to missing/invalid RD value.");
            }
        }

    }

    private List<String> generateNewRD(Uuid vpn) {
        List<String> rd = null;
        if (adminRDValue != null) {
            Integer rdId = NeutronvpnUtils.getUniqueRDId(idManager, NeutronConstants.RD_IDPOOL_NAME, vpn.toString());
            if (rdId != null) {
                rd = new ArrayList<>(Arrays.asList(adminRDValue + ":" + rdId));
            }
            LOG.debug("Generated RD " + rd.get(0) + " for L3VPN " + vpn);
        }
        return rd;
    }

    @Override
    protected void remove(InstanceIdentifier<Bgpvpn> identifier, Bgpvpn input) {
        if (LOG.isTraceEnabled()) {
            LOG.trace("Removing Bgpvpn : key: " + identifier + ", value=" + input);
        }
        if (isBgpvpnTypeL3(input.getType())) {
            nvpnManager.removeL3Vpn(input.getUuid());
            NeutronvpnUtils.releaseRDId(idManager, NeutronConstants.RD_IDPOOL_NAME, input.getUuid().toString());
        }


    }

    @Override
    protected void update(InstanceIdentifier<Bgpvpn> identifier, Bgpvpn original, Bgpvpn update) {
        if (LOG.isTraceEnabled()) {
            LOG.trace("Update Bgpvpn : key: " + identifier + ", value=" + update);
        }
        if (isBgpvpnTypeL3(update.getType())) {
            List<Uuid> oldNetworks = original.getNetworks();
            List<Uuid> newNetworks = update.getNetworks();
            List<Uuid> oldRouters = original.getRouters();
            List<Uuid> newRouters = update.getRouters();
            Uuid vpnId = update.getUuid();
            handleNetworksUpdate(vpnId, oldNetworks, newNetworks);
            handleRoutersUpdate(vpnId, oldRouters, newRouters);

        }
    }

    protected void handleNetworksUpdate(Uuid vpnId, List<Uuid> oldNetworks, List<Uuid> newNetworks) {
        if (newNetworks != null && !newNetworks.isEmpty()) {
            if (oldNetworks != null && !oldNetworks.isEmpty()) {
                if (oldNetworks != newNetworks) {
                    Iterator<Uuid> iter = newNetworks.iterator();
                    while (iter.hasNext()) {
                        Object net = iter.next();
                        if (oldNetworks.contains(net)) {
                            oldNetworks.remove(net);
                            iter.remove();
                        }
                    }
                    //clear removed networks
                    if (!oldNetworks.isEmpty()) {
                        LOG.trace("Removing old networks {} ", oldNetworks);
                        nvpnManager.dissociateNetworksFromVpn(vpnId, oldNetworks);
                    }

                    //add new (Delta) Networks
                    if (!newNetworks.isEmpty()) {
                        LOG.trace("Adding delta New networks {} ", newNetworks);
                        nvpnManager.associateNetworksToVpn(vpnId, newNetworks);
                    }
                }
            } else {
                //add new Networks
                LOG.trace("Adding New networks {} ", newNetworks);
                nvpnManager.associateNetworksToVpn(vpnId, newNetworks);
            }
        } else if (oldNetworks != null && !oldNetworks.isEmpty()) {
            LOG.trace("Removing old networks {} ", oldNetworks);
            nvpnManager.dissociateNetworksFromVpn(vpnId, oldNetworks);

        }
    }

    protected void handleRoutersUpdate(Uuid vpnId, List<Uuid> oldRouters, List<Uuid> newRouters) {
        if (newRouters != null && !newRouters.isEmpty()) {
            if (oldRouters != null && !oldRouters.isEmpty()) {
                if (oldRouters.size() > 1 || newRouters.size() > 1) {
                    VpnMap vpnMap = NeutronvpnUtils.getVpnMap(dbroker, vpnId);
                    if (vpnMap.getRouterId() != null) {
                        LOG.warn("Only Single Router association  to a given bgpvpn is allowed .Kindly de-associate " +
                                "router " + vpnMap.getRouterId().getValue() + " from vpn " + vpnId + " before " +
                                "proceeding with associate");
                    }
                    return;
                }
            } else if (validateRouteInfo(newRouters.get(0))) {
                nvpnManager.associateRouterToVpn(vpnId, newRouters.get(0));
            }

        } else if (oldRouters != null && !oldRouters.isEmpty()) {
                /* dissociate old router */
            Uuid oldRouter = oldRouters.get(0);
            nvpnManager.dissociateRouterFromVpn(vpnId, oldRouter);
        }
    }

    private void createIdPool() {
        CreateIdPoolInput createPool = new CreateIdPoolInputBuilder().setPoolName(NeutronConstants.RD_IDPOOL_NAME)
                .setLow(NeutronConstants.RD_IDPOOL_START)
                .setHigh(new BigInteger(NeutronConstants.RD_IDPOOL_SIZE).longValue()).build();
        try {
            Future<RpcResult<Void>> result = idManager.createIdPool(createPool);
            if ((result != null) && (result.get().isSuccessful())) {
                LOG.info("Created IdPool for Bgpvpn RD");
            }
        } catch (InterruptedException | ExecutionException e) {
            LOG.error("Failed to create idPool for Bgpvpn RD", e);
        }
    }

    private boolean validateRouteInfo(Uuid routerID) {
        Uuid assocVPNId;
        if ((assocVPNId = NeutronvpnUtils.getVpnForRouter(dbroker, routerID, true)) != null) {
            LOG.warn("VPN router association failed  due to router " + routerID.getValue()
                    + " already associated to another VPN " + assocVPNId.getValue());
            return false;
        }
        return true;
    }

}
