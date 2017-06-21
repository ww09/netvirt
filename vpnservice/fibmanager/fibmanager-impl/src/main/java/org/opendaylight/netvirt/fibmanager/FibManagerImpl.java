/*
 * Copyright (c) 2015 - 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.fibmanager;

import com.google.common.base.Optional;
import com.google.common.util.concurrent.FutureCallback;
import io.netty.util.concurrent.GlobalEventExecutor;
import java.math.BigInteger;
import java.util.List;
import javax.annotation.PostConstruct;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.opendaylight.controller.config.api.osgi.WaitingServiceTracker;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.WriteTransaction;
import org.opendaylight.netvirt.fibmanager.api.IFibManager;
import org.opendaylight.netvirt.fibmanager.api.RouteOrigin;
import org.opendaylight.netvirt.vpnmanager.api.IVpnManager;
import org.opendaylight.netvirt.vpnmanager.api.intervpnlink.InterVpnLinkCache;
import org.opendaylight.netvirt.vpnmanager.api.intervpnlink.InterVpnLinkDataComposite;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.RouterInterface;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.vrfentries.VrfEntry;
import org.osgi.framework.BundleContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class FibManagerImpl implements IFibManager {
    private static final Logger LOG = LoggerFactory.getLogger(FibManagerImpl.class);
    private final NexthopManager nexthopManager;
    private final VrfEntryListener vrfEntryListener;
    private IVpnManager vpnmanager;
    private final DataBroker dataBroker;

    @Inject
    public FibManagerImpl(final DataBroker dataBroker,
                          final NexthopManager nexthopManager,
                          final VrfEntryListener vrfEntryListener,
                          final BundleContext bundleContext) {
        this.dataBroker = dataBroker;
        this.nexthopManager = nexthopManager;
        this.vrfEntryListener = vrfEntryListener;

        GlobalEventExecutor.INSTANCE.execute(() -> {
            final WaitingServiceTracker<IVpnManager> tracker = WaitingServiceTracker.create(
                IVpnManager.class, bundleContext);
            vpnmanager = tracker.waitForService(WaitingServiceTracker.FIVE_MINUTES);
            LOG.info("FibManagerImpl initialized. IVpnManager={}", vpnmanager);
        });
    }

    @PostConstruct
    public void init() {
        InterVpnLinkCache.createInterVpnLinkCaches(this.dataBroker);  // Idempotent creation
    }

    @Override
    public void populateFibOnNewDpn(BigInteger dpnId, long vpnId, String rd,
                                    FutureCallback<List<Void>> callback) {
        vrfEntryListener.populateFibOnNewDpn(dpnId, vpnId, rd, callback);
    }

    @Override
    public void populateExternalRoutesOnDpn(BigInteger localDpnId, long vpnId,
                                            String rd, String localNextHopIp,
                                            String remoteNextHopIp) {
        vrfEntryListener.populateExternalRoutesOnDpn(localDpnId, vpnId, rd,
            localNextHopIp, remoteNextHopIp);
    }

    @Override
    public void cleanUpExternalRoutesOnDpn(BigInteger dpnId, long vpnId,
                                           String rd, String localNextHopIp,
                                           String remoteNextHopIp) {
        vrfEntryListener.cleanUpExternalRoutesOnDpn(dpnId, vpnId, rd,
            localNextHopIp, remoteNextHopIp);
    }

    @Override
    public void cleanUpDpnForVpn(BigInteger dpnId, long vpnId, String rd,
                                 FutureCallback<List<Void>> callback) {
        vrfEntryListener.cleanUpDpnForVpn(dpnId, vpnId, rd, callback);
    }

    @Override
    public void setConfTransType(String service, String transportType) {
        nexthopManager.setConfTransType(service, transportType);
    }

    @Override
    public void writeConfTransTypeConfigDS() {
        nexthopManager.writeConfTransTypeConfigDS();
    }

    @Override
    public String getConfTransType() {
        return nexthopManager.getConfiguredTransportTypeL3VPN().toString();
    }

    @Override
    public String getReqTransType() {
        return nexthopManager.getReqTransType();
    }

    @Override
    public String getTransportTypeStr(String tunType) {
        return nexthopManager.getTransportTypeStr(tunType);
    }

    @Override
    public void manageRemoteRouteOnDPN(boolean action,
                                       BigInteger dpnId,
                                       long vpnId,
                                       String rd,
                                       String destPrefix,
                                       String destTepIp,
                                       long label) {
        vrfEntryListener.manageRemoteRouteOnDPN(action, dpnId, vpnId, rd, destPrefix, destTepIp, label);
    }

    @Override
    public void addOrUpdateFibEntry(DataBroker broker, String rd, String macAddress, String prefix,
                                    List<String> nextHopList, VrfEntry.EncapType encapType, long label,
                                    long l3vni, String gwMacAddress, String parentVpnRd, RouteOrigin origin,
                                    WriteTransaction writeConfigTxn) {
        FibUtil.addOrUpdateFibEntry(broker, rd, macAddress, prefix , nextHopList, encapType, label, l3vni,
                gwMacAddress, parentVpnRd, origin, writeConfigTxn);
    }

    @Override
    public void addFibEntryForRouterInterface(DataBroker broker, String rd, String prefix,
                                              RouterInterface routerInterface, long label,
                                              WriteTransaction writeConfigTxn) {
        FibUtil.addFibEntryForRouterInterface(broker, rd, prefix, routerInterface, label, writeConfigTxn);
    }

    @Override
    public void removeOrUpdateFibEntry(DataBroker broker, String rd, String prefix,
                                       String nextHopToRemove, WriteTransaction writeConfigTxn) {
        FibUtil.removeOrUpdateFibEntry(broker, rd, prefix, nextHopToRemove, writeConfigTxn);
    }

    @Override
    public void removeFibEntry(DataBroker broker, String rd, String prefix, WriteTransaction writeConfigTxn) {
        FibUtil.removeFibEntry(broker, rd, prefix, writeConfigTxn);
    }

    @Override
    public void updateRoutePathForFibEntry(DataBroker broker, String rd, String prefix, String nextHop,
                               long label, boolean nextHopAdd, WriteTransaction writeConfigTxn) {
        FibUtil.updateRoutePathForFibEntry(broker, rd, prefix, nextHop, label, nextHopAdd, writeConfigTxn);
    }

    @Override
    public void removeVrfTable(DataBroker broker, String rd, WriteTransaction writeConfigTxn) {
        FibUtil.removeVrfTable(broker, rd, writeConfigTxn);
    }

    @Override
    public boolean isVPNConfigured() {
        return this.vpnmanager.isVPNConfigured();
    }

    @Override
    public void removeInterVPNLinkRouteFlows(final String interVpnLinkName,
                                             final boolean isVpnFirstEndPoint,
                                             final VrfEntry vrfEntry) {
        Optional<InterVpnLinkDataComposite> optInterVpnLink = InterVpnLinkCache.getInterVpnLinkByName(interVpnLinkName);
        if (!optInterVpnLink.isPresent()) {
            LOG.warn("Could not find InterVpnLink with name {}. InterVpnLink route flows wont be removed",
                     interVpnLinkName);
            return;
        }
        InterVpnLinkDataComposite interVpnLink = optInterVpnLink.get();
        String vpnName = (isVpnFirstEndPoint) ? interVpnLink.getFirstEndpointVpnUuid().get()
                                              : interVpnLink.getSecondEndpointVpnUuid().get();

        vrfEntryListener.removeInterVPNLinkRouteFlows(interVpnLink, vpnName, vrfEntry);
    }

    @Override
    public void programDcGwLoadBalancingGroup(List<String> availableDcGws, BigInteger dpnId,
            String destinationIp, int addRemoveOrUpdate, boolean isTunnelUp) {
        nexthopManager.programDcGwLoadBalancingGroup(availableDcGws, dpnId, destinationIp,
            addRemoveOrUpdate, isTunnelUp);
    }

    @Override
    public void refreshVrfEntry(String rd, String prefix) {
        vrfEntryListener.refreshFibTables(rd, prefix);
    }
}
