/*
 * Copyright (c) 2016, 2017 Ericsson India Global Services Pvt Ltd. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.neutronvpn;

import com.google.common.base.Optional;
import java.util.ArrayList;
import java.util.List;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.NotificationPublishService;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.genius.datastoreutils.AsyncDataTreeChangeListenerBase;
import org.opendaylight.genius.datastoreutils.SingleTransactionDataBroker;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.Uuid;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.ElanInstances;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.instances.ElanInstance;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.instances.ElanInstanceKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.PortAddedToSubnetBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.PortRemovedFromSubnetBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.SubnetAddedToVpnBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.SubnetDeletedFromVpnBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.SubnetUpdatedInVpnBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.Subnetmaps;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.subnetmaps.Subnetmap;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class SubnetmapChangeListener extends AsyncDataTreeChangeListenerBase<Subnetmap, SubnetmapChangeListener> {
    private static final Logger LOG = LoggerFactory.getLogger(SubnetmapChangeListener.class);
    private final DataBroker dataBroker;
    private NotificationPublishService notificationPublishService;

    public SubnetmapChangeListener(final DataBroker dataBroker, final NotificationPublishService notiPublishService) {
        super(Subnetmap.class, SubnetmapChangeListener.class);
        this.dataBroker = dataBroker;
        this.notificationPublishService = notiPublishService;
    }

    public void start() {
        LOG.info("{} start", getClass().getSimpleName());
        registerListener(dataBroker);
    }

    @Override
    protected InstanceIdentifier<Subnetmap> getWildCardPath() {
        return InstanceIdentifier.create(Subnetmaps.class).child(Subnetmap.class);
    }

    // TODO Clean up the exception handling
    @SuppressWarnings("checkstyle:IllegalCatch")
    private void registerListener(final DataBroker db) {
        try {
            registerListener(LogicalDatastoreType.CONFIGURATION, db);
        } catch (final Exception e) {
            LOG.error("NeutronVpn subnetMap config DataChange listener registration fail!", e);
            throw new IllegalStateException("NeutronVpn subnetMap config DataChange listener registration failed.", e);
        }
    }

    @Override
    protected void add(InstanceIdentifier<Subnetmap> identifier, Subnetmap subnetmap) {
        LOG.trace("add:SubnetmapChangeListener add subnetmap method - key: " + identifier + ", value=" + subnetmap);
        Uuid subnetId = subnetmap.getId();
        Uuid vpnId = subnetmap.getVpnId();
        if (subnetmap.getVpnId() != null) {
            boolean isBgpVpn = !vpnId.equals(subnetmap.getRouterId());
            String elanInstanceName = subnetmap.getNetworkId().getValue();
            Long elanTag = getElanTag(elanInstanceName);
            if (elanTag.equals(0L)) {
                LOG.error("add:Unable to fetch elantag from ElanInstance {} and hence not proceeding with "
                        + "subnetmapListener add for subnet {}", elanInstanceName, subnetId.getValue());
                return;
            }
            try {
                // subnet added to VPN case upon config DS replay after reboot
                // ports added to subnet upon config DS replay after reboot are handled implicitly by the above
                // notification in SubnetRouteHandler
                checkAndPublishSubnetAddedToVpnNotification(subnetmap, isBgpVpn, elanTag);
                LOG.debug("add:Subnet added to VPN notification sent for subnet {} on VPN {}", subnetId
                                .getValue(), vpnId.getValue());
            } catch (InterruptedException e) {
                LOG.error("add:Subnet added to VPN notification failed for subnet {} on VPN {}", subnetId
                                .getValue(), vpnId.getValue(), e);
            }
            return;
        }
    }

    @Override
    protected void remove(InstanceIdentifier<Subnetmap> identifier, Subnetmap subnetmap) {
        LOG.trace("remove:SubnetmapChangeListener remove subnetmap method - key: " + identifier + ", value"
                + subnetmap);
    }

    @Override
    // TODO Clean up the exception handling
    @SuppressWarnings("checkstyle:IllegalCatch")
    protected void update(InstanceIdentifier<Subnetmap> identifier, Subnetmap subnetmapOriginal, Subnetmap
            subnetmapUpdate) {
        LOG.trace("update:SubnetmapListener update subnetmap method - key: {}, original: {}, update: {}",
                    identifier, subnetmapOriginal, subnetmapUpdate);
        Uuid vpnIdNew = subnetmapUpdate.getVpnId();
        Uuid vpnIdOld = subnetmapOriginal.getVpnId();
        Uuid subnetId = subnetmapUpdate.getId();
        String elanInstanceName = subnetmapUpdate.getNetworkId().getValue();
        String subnetIp = subnetmapUpdate.getSubnetIp();
        Long elanTag = getElanTag(elanInstanceName);
        if (elanTag.equals(0L)) {
            LOG.error("update:Unable to fetch elantag from ElanInstance {} and hence not proceeding with "
                + "subnetmapListener update for subnet {}", elanInstanceName, subnetId.getValue());
            return;
        }
        // subnet added to VPN case
        if (vpnIdNew != null && vpnIdOld == null) {
            boolean isBgpVpn = !vpnIdNew.equals(subnetmapUpdate.getRouterId());
            try {
                checkAndPublishSubnetAddedToVpnNotification(subnetmapUpdate, isBgpVpn, elanTag);
                LOG.debug("update:Subnet added to VPN notification sent for subnet {} on VPN {}", subnetId.getValue(),
                        vpnIdNew.getValue());
            } catch (Exception e) {
                LOG.error("update:Subnet added to VPN notification failed for subnet {} on VPN {}", subnetId.getValue(),
                        vpnIdNew.getValue(), e);
            }
            return;
        }
        // subnet removed from VPN case
        if (vpnIdOld != null && vpnIdNew == null) {
            Boolean isBgpVpn = vpnIdOld.equals(subnetmapOriginal.getRouterId()) ? false : true;
            try {
                checkAndPublishSubnetDeletedFromVpnNotification(subnetId, subnetIp,
                        vpnIdOld.getValue(), isBgpVpn, elanTag);
                LOG.debug("update:Subnet removed from VPN notification sent for subnet {} on VPN {}",
                            subnetId.getValue(), vpnIdOld.getValue());
            } catch (Exception e) {
                LOG.error("update:Subnet removed from VPN notification failed for subnet {} on VPN {}",
                            subnetId.getValue(), vpnIdOld.getValue(), e);
            }
            return;
        }
        // subnet updated in VPN case
        if (vpnIdOld != null && vpnIdNew != null && (!vpnIdNew.equals(vpnIdOld))) {
            boolean isBeingAssociated = subnetmapUpdate.getVpnId().equals(subnetmapUpdate.getRouterId()) ? false : true;
            try {
                checkAndPublishSubnetUpdatedInVpnNotification(subnetId, subnetIp,
                        subnetmapUpdate.getVpnId().getValue(), isBeingAssociated, elanTag);
                LOG.debug("VPN updated for subnet notification sent for subnet {} on VPN {}", subnetId.getValue(),
                        vpnIdNew.getValue());
            } catch (Exception e) {
                LOG.error("VPN updated for subnet notification failed for subnet {} on VPN {}", subnetId.getValue(),
                        vpnIdNew.getValue(), e);
            }
            return;
        }
        // port added/removed to/from subnet case
        List<Uuid> oldPortList;
        List<Uuid> newPortList;
        newPortList = subnetmapUpdate.getPortList() != null ? subnetmapUpdate.getPortList() : new ArrayList<>();
        oldPortList = subnetmapOriginal.getPortList() != null ? subnetmapOriginal.getPortList() : new ArrayList<>();
        if (newPortList.size() == oldPortList.size()) {
            return;
        }
        if (newPortList.size() > oldPortList.size()) {
            for (Uuid port : newPortList) {
                if (! oldPortList.contains(port)) {
                    try {
                        checkAndPublishPortAddedToSubnetNotification(subnetIp, subnetId, port, elanTag);
                        LOG.debug("Port added to subnet notification sent for port {} in subnet {}", port.getValue(),
                                subnetId.getValue());
                    } catch (Exception e) {
                        LOG.error("Port added to subnet notification failed for port {} in subnet {}",
                            port.getValue(), subnetId.getValue(), e);
                    }
                    return;
                }
            }
        } else {
            for (Uuid port : oldPortList) {
                if (! newPortList.contains(port)) {
                    try {
                        checkAndPublishPortRemovedFromSubnetNotification(subnetIp, subnetId, port, elanTag);
                        LOG.debug("Port removed from subnet notification sent for port {} in subnet {}",
                            port.getValue(), subnetId.getValue());
                    } catch (Exception e) {
                        LOG.error("Port removed from subnet notification failed for port {} in subnet {}",
                            port.getValue(), subnetId.getValue(), e);
                    }
                    return;
                }
            }
        }
    }

    @Override
    protected SubnetmapChangeListener getDataTreeChangeListener() {
        return this;
    }

    // TODO Clean up the exception handling
    @SuppressWarnings("checkstyle:IllegalCatch")
    protected long getElanTag(String elanInstanceName) {
        InstanceIdentifier<ElanInstance> elanIdentifierId = InstanceIdentifier.builder(ElanInstances.class)
                .child(ElanInstance.class, new ElanInstanceKey(elanInstanceName)).build();
        long elanTag = 0L;
        try {
            Optional<ElanInstance> elanInstance = SingleTransactionDataBroker.syncReadOptional(dataBroker,
                    LogicalDatastoreType.CONFIGURATION, elanIdentifierId);
            if (elanInstance.isPresent()) {
                elanTag = elanInstance.get().getElanTag();
            } else {
                LOG.error("Notification failed because of failure in reading ELANInstance {}", elanInstanceName);
            }
        } catch (Exception e) {
            LOG.error("Notification failed because of failure in fetching elanTag from ElanInstance {} config DS",
                elanInstanceName, e);
        }
        return elanTag;
    }

    private void checkAndPublishSubnetAddedToVpnNotification(Subnetmap subnetmap, Boolean isBgpVpn, Long elanTag)
            throws InterruptedException {
        SubnetAddedToVpnBuilder builder = new SubnetAddedToVpnBuilder();
        builder.setSubnetId(subnetmap.getId());
        builder.setSubnetIp(subnetmap.getSubnetIp());
        builder.setVpnName(subnetmap.getVpnId().getValue());
        builder.setBgpVpn(isBgpVpn);
        builder.setElanTag(elanTag);
        builder.setNetworkId(subnetmap.getNetworkId());
        builder.setNetworkType(subnetmap.getNetworkType());
        builder.setSegmentationId(subnetmap.getSegmentationId());

        notificationPublishService.putNotification(builder.build());
        LOG.trace("publish notification called from SubnetAddedToVpnNotification: {}", builder);
    }

    private void checkAndPublishSubnetDeletedFromVpnNotification(Uuid subnetId, String subnetIp, String vpnName,
                                                                 Boolean isBgpVpn, Long elanTag)
                                                                 throws InterruptedException {
        SubnetDeletedFromVpnBuilder builder = new SubnetDeletedFromVpnBuilder();
        builder.setSubnetId(subnetId);
        builder.setSubnetIp(subnetIp);
        builder.setVpnName(vpnName);
        builder.setBgpVpn(isBgpVpn);
        builder.setElanTag(elanTag);

        notificationPublishService.putNotification(builder.build());
        LOG.trace("publish notification called SubnetDeletedFromVpnNotification: {}", builder);
    }

    private void checkAndPublishSubnetUpdatedInVpnNotification(Uuid subnetId, String subnetIp, String vpnName,
                                                               Boolean isBgpVpn, Long elanTag) throws
                                                               InterruptedException {
        SubnetUpdatedInVpnBuilder builder = new SubnetUpdatedInVpnBuilder();
        builder.setSubnetId(subnetId);
        builder.setSubnetIp(subnetIp);
        builder.setVpnName(vpnName);
        builder.setBgpVpn(isBgpVpn);
        builder.setElanTag(elanTag);

        notificationPublishService.putNotification(builder.build());
        LOG.trace("publish notification called SubnetUpdatedInVpnNotification: {}", builder);
    }

    private void checkAndPublishPortAddedToSubnetNotification(String subnetIp, Uuid subnetId, Uuid portId,
                                                              Long elanTag) throws InterruptedException {
        PortAddedToSubnetBuilder builder = new PortAddedToSubnetBuilder();
        builder.setSubnetIp(subnetIp);
        builder.setSubnetId(subnetId);
        builder.setPortId(portId);
        builder.setElanTag(elanTag);

        notificationPublishService.putNotification(builder.build());
        LOG.trace("publish notification called PortAddedToSubnetNotification: {}", builder);
    }

    private void checkAndPublishPortRemovedFromSubnetNotification(String subnetIp, Uuid subnetId, Uuid portId,
                                                                  Long elanTag) throws InterruptedException {
        PortRemovedFromSubnetBuilder builder = new PortRemovedFromSubnetBuilder();
        builder.setPortId(portId);
        builder.setSubnetIp(subnetIp);
        builder.setSubnetId(subnetId);
        builder.setElanTag(elanTag);

        notificationPublishService.putNotification(builder.build());
        LOG.trace("publish notification called PortRemovedFromSubnetNotification: {}", builder);
    }
}
