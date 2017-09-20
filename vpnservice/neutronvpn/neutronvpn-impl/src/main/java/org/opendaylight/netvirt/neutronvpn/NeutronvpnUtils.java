/*
 * Copyright (c) 2016, 2017 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.neutronvpn;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableBiMap;
import com.google.common.collect.Sets;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import org.apache.commons.lang3.StringUtils;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.WriteTransaction;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.md.sal.common.api.data.ReadFailedException;
import org.opendaylight.genius.datastoreutils.SingleTransactionDataBroker;
import org.opendaylight.genius.mdsalutil.MDSALUtil;
import org.opendaylight.netvirt.neutronvpn.api.enums.IpVersionChoice;
import org.opendaylight.netvirt.neutronvpn.api.utils.NeutronUtils;
import org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.VpnInstances;
import org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.VpnInterfaces;
import org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.vpn.instances.VpnInstance;
import org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.vpn.interfaces.VpnInterface;
import org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.vpn.interfaces.VpnInterfaceKey;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.IpAddress;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Ipv6Address;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.Interfaces;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.Interface;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.InterfaceKey;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.MacAddress;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.PhysAddress;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.Uuid;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.idmanager.rev160406.AllocateIdInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.idmanager.rev160406.AllocateIdInputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.idmanager.rev160406.AllocateIdOutput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.idmanager.rev160406.IdManagerService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.idmanager.rev160406.ReleaseIdInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.idmanager.rev160406.ReleaseIdInputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.aclservice.rev160608.InterfaceAclBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.aclservice.rev160608.IpPrefixOrAddress;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.aclservice.rev160608.PortsSubnetIpPrefixes;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.aclservice.rev160608.interfaces._interface.AllowedAddressPairs;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.aclservice.rev160608.interfaces._interface.AllowedAddressPairsBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.aclservice.rev160608.ports.subnet.ip.prefixes.PortSubnetIpPrefixes;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.aclservice.rev160608.ports.subnet.ip.prefixes.PortSubnetIpPrefixesBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.aclservice.rev160608.ports.subnet.ip.prefixes.PortSubnetIpPrefixesKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.SegmentTypeBase;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.SegmentTypeFlat;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.SegmentTypeGre;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.SegmentTypeVlan;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.SegmentTypeVxlan;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.interfaces.elan._interface.StaticMacEntries;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.interfaces.elan._interface.StaticMacEntriesBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.LearntVpnVipToPortData;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.NeutronRouterDpns;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.learnt.vpn.vip.to.port.data.LearntVpnVipToPort;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.learnt.vpn.vip.to.port.data.LearntVpnVipToPortKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.neutron.router.dpns.RouterDpnList;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.neutron.router.dpns.router.dpn.list.DpnVpninterfacesList;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.ExtRouters;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.ExternalSubnets;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.FloatingIpPortInfo;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.ProviderTypes;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.ext.routers.RoutersKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.external.subnets.SubnetsKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.floating.ip.port.info.FloatingIpIdToPortMapping;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.floating.ip.port.info.FloatingIpIdToPortMappingKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.NetworkMaps;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.NeutronVpnPortipPortData;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.Subnetmaps;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.VpnMaps;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.networkmaps.NetworkMap;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.networkmaps.NetworkMapKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.neutron.vpn.portip.port.data.VpnPortipToPort;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.neutron.vpn.portip.port.data.VpnPortipToPortBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.neutron.vpn.portip.port.data.VpnPortipToPortKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.subnetmaps.Subnetmap;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.subnetmaps.SubnetmapKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.vpnmaps.VpnMap;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.vpnmaps.VpnMapKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.l3.ext.rev150712.NetworkL3Extension;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.l3.rev150712.routers.attributes.Routers;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.l3.rev150712.routers.attributes.routers.Router;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.l3.rev150712.routers.attributes.routers.RouterKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.networks.rev150712.NetworkTypeBase;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.networks.rev150712.NetworkTypeFlat;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.networks.rev150712.NetworkTypeGre;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.networks.rev150712.NetworkTypeVlan;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.networks.rev150712.NetworkTypeVxlan;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.networks.rev150712.networks.attributes.Networks;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.networks.rev150712.networks.attributes.networks.Network;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.networks.rev150712.networks.attributes.networks.NetworkKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.ports.rev150712.port.attributes.FixedIps;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.ports.rev150712.ports.attributes.Ports;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.ports.rev150712.ports.attributes.ports.Port;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.ports.rev150712.ports.attributes.ports.PortKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.portsecurity.rev150712.PortSecurityExtension;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.provider.ext.rev150712.NetworkProviderExtension;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.qos.rev160613.qos.attributes.qos.policies.QosPolicy;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.rev150712.Neutron;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.subnets.rev150712.subnets.attributes.Subnets;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.subnets.rev150712.subnets.attributes.subnets.Subnet;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.subnets.rev150712.subnets.attributes.subnets.SubnetKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.netvirt.inter.vpn.link.rev160311.InterVpnLinkStates;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.netvirt.inter.vpn.link.rev160311.InterVpnLinks;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.netvirt.inter.vpn.link.rev160311.inter.vpn.link.states.InterVpnLinkState;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.netvirt.inter.vpn.link.rev160311.inter.vpn.link.states.InterVpnLinkStateKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.netvirt.inter.vpn.link.rev160311.inter.vpn.links.InterVpnLink;
import org.opendaylight.yangtools.yang.binding.DataObject;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.yangtools.yang.common.RpcResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NeutronvpnUtils {

    private static final Logger LOG = LoggerFactory.getLogger(NeutronvpnUtils.class);
    private static final ImmutableBiMap<Class<? extends NetworkTypeBase>, Class<? extends SegmentTypeBase>>
        NETWORK_MAP =
        new ImmutableBiMap.Builder<Class<? extends NetworkTypeBase>, Class<? extends SegmentTypeBase>>()
            .put(NetworkTypeFlat.class, SegmentTypeFlat.class)
            .put(NetworkTypeGre.class, SegmentTypeGre.class)
            .put(NetworkTypeVlan.class, SegmentTypeVlan.class)
            .put(NetworkTypeVxlan.class, SegmentTypeVxlan.class)
            .build();

    public static ConcurrentHashMap<Uuid, Network> networkMap = new ConcurrentHashMap<>();
    public static ConcurrentHashMap<Uuid, Router> routerMap = new ConcurrentHashMap<>();
    public static ConcurrentHashMap<Uuid, Port> portMap = new ConcurrentHashMap<>();
    public static ConcurrentHashMap<Uuid, Subnet> subnetMap = new ConcurrentHashMap<>();
    public static Map<IpAddress, Set<Uuid>> subnetGwIpMap = new ConcurrentHashMap<>();
    public static ConcurrentHashMap<Uuid, QosPolicy> qosPolicyMap = new ConcurrentHashMap<>();
    public static ConcurrentHashMap<Uuid, HashMap<Uuid, Port>> qosPortsMap = new ConcurrentHashMap<>();
    public static ConcurrentHashMap<Uuid, HashMap<Uuid, Network>> qosNetworksMap = new ConcurrentHashMap<>();

    private static final Set<Class<? extends NetworkTypeBase>> SUPPORTED_NETWORK_TYPES = Sets.newConcurrentHashSet();

    static {
        registerSupportedNetworkType(NetworkTypeFlat.class);
        registerSupportedNetworkType(NetworkTypeVlan.class);
        registerSupportedNetworkType(NetworkTypeVxlan.class);
        registerSupportedNetworkType(NetworkTypeGre.class);
    }

    private NeutronvpnUtils() {
        throw new UnsupportedOperationException("Utility class should not be instantiated");
    }



    public static void registerSupportedNetworkType(Class<? extends NetworkTypeBase> netType) {
        SUPPORTED_NETWORK_TYPES.add(netType);
    }

    public static void unregisterSupportedNetworkType(Class<? extends NetworkTypeBase> netType) {
        SUPPORTED_NETWORK_TYPES.remove(netType);
    }

    protected static Subnetmap getSubnetmap(DataBroker broker, Uuid subnetId) {
        InstanceIdentifier id = buildSubnetMapIdentifier(subnetId);
        Optional<Subnetmap> sn = read(broker, LogicalDatastoreType.CONFIGURATION, id);

        if (sn.isPresent()) {
            return sn.get();
        }
        LOG.error("getSubnetmap failed, subnet {} is not present", subnetId.getValue());
        return null;
    }

    public static VpnMap getVpnMap(DataBroker broker, Uuid id) {
        InstanceIdentifier<VpnMap> vpnMapIdentifier = InstanceIdentifier.builder(VpnMaps.class).child(VpnMap.class,
                new VpnMapKey(id)).build();
        Optional<VpnMap> optionalVpnMap = read(broker, LogicalDatastoreType.CONFIGURATION, vpnMapIdentifier);
        if (optionalVpnMap.isPresent()) {
            return optionalVpnMap.get();
        }
        LOG.error("getVpnMap failed, VPN {} not present", id.getValue());
        return null;
    }

    protected static Uuid getVpnForNetwork(DataBroker broker, Uuid network) {
        InstanceIdentifier<VpnMaps> vpnMapsIdentifier = InstanceIdentifier.builder(VpnMaps.class).build();
        Optional<VpnMaps> optionalVpnMaps = read(broker, LogicalDatastoreType.CONFIGURATION, vpnMapsIdentifier);
        if (optionalVpnMaps.isPresent() && optionalVpnMaps.get().getVpnMap() != null) {
            List<VpnMap> allMaps = optionalVpnMaps.get().getVpnMap();
            for (VpnMap vpnMap : allMaps) {
                List<Uuid> netIds = vpnMap.getNetworkIds();
                if (netIds != null && netIds.contains(network)) {
                    return vpnMap.getVpnId();
                }
            }
        }
        LOG.debug("getVpnForNetwork: Failed for network {} as no VPN present in VPNMaps DS", network.getValue());
        return null;
    }

    protected static Uuid getVpnForSubnet(DataBroker broker, Uuid subnetId) {
        InstanceIdentifier<Subnetmap> subnetmapIdentifier = buildSubnetMapIdentifier(subnetId);
        Optional<Subnetmap> optionalSubnetMap = read(broker, LogicalDatastoreType.CONFIGURATION, subnetmapIdentifier);
        if (optionalSubnetMap.isPresent()) {
            return optionalSubnetMap.get().getVpnId();
        }
        LOG.error("getVpnForSubnet: Failed as subnetMap DS is absent for subnet {}", subnetId.getValue());
        return null;
    }

    protected static Uuid getNetworkForSubnet(DataBroker broker, Uuid subnetId) {
        InstanceIdentifier<Subnetmap> subnetmapIdentifier = buildSubnetMapIdentifier(subnetId);
        Optional<Subnetmap> optionalSubnetMap = read(broker, LogicalDatastoreType.CONFIGURATION, subnetmapIdentifier);
        if (optionalSubnetMap.isPresent()) {
            return optionalSubnetMap.get().getNetworkId();
        }
        LOG.error("getNetworkForSubnet: Failed as subnetMap DS is absent for subnet {}", subnetId.getValue());
        return null;
    }

    // @param external vpn - true if external vpn being fetched, false for internal vpn
    protected static Uuid getVpnForRouter(DataBroker broker, Uuid routerId, Boolean externalVpn) {
        InstanceIdentifier<VpnMaps> vpnMapsIdentifier = InstanceIdentifier.builder(VpnMaps.class).build();
        Optional<VpnMaps> optionalVpnMaps = read(broker, LogicalDatastoreType.CONFIGURATION, vpnMapsIdentifier);
        if (optionalVpnMaps.isPresent() && optionalVpnMaps.get().getVpnMap() != null) {
            List<VpnMap> allMaps = optionalVpnMaps.get().getVpnMap();
            if (routerId != null) {
                for (VpnMap vpnMap : allMaps) {
                    if (routerId.equals(vpnMap.getRouterId())) {
                        if (externalVpn) {
                            if (!routerId.equals(vpnMap.getVpnId())) {
                                return vpnMap.getVpnId();
                            }
                        } else {
                            if (routerId.equals(vpnMap.getVpnId())) {
                                return vpnMap.getVpnId();
                            }
                        }
                    }
                }
            }
        }
        LOG.debug("getVpnForRouter: Failed for router {} as no VPN present in VPNMaps DS", routerId.getValue());
        return null;
    }

    protected static Uuid getRouterforVpn(DataBroker broker, Uuid vpnId) {
        InstanceIdentifier<VpnMap> vpnMapIdentifier = InstanceIdentifier.builder(VpnMaps.class).child(VpnMap.class,
                new VpnMapKey(vpnId)).build();
        Optional<VpnMap> optionalVpnMap = read(broker, LogicalDatastoreType.CONFIGURATION, vpnMapIdentifier);
        if (optionalVpnMap.isPresent()) {
            VpnMap vpnMap = optionalVpnMap.get();
            return vpnMap.getRouterId();
        }
        LOG.error("getRouterforVpn: Failed as VPNMaps DS is absent for VPN {}", vpnId.getValue());
        return null;
    }

    protected static List<Uuid> getNetworksforVpn(DataBroker broker, Uuid vpnId) {
        InstanceIdentifier<VpnMap> vpnMapIdentifier = InstanceIdentifier.builder(VpnMaps.class).child(VpnMap.class,
                new VpnMapKey(vpnId)).build();
        Optional<VpnMap> optionalVpnMap = read(broker, LogicalDatastoreType.CONFIGURATION, vpnMapIdentifier);
        if (optionalVpnMap.isPresent()) {
            VpnMap vpnMap = optionalVpnMap.get();
            return vpnMap.getNetworkIds();
        }
        LOG.error("getNetworksforVpn: Failed as VPNMaps DS is absent for VPN {}", vpnId.getValue());
        return null;
    }

    protected static List<Uuid> getSubnetsforVpn(DataBroker broker, Uuid vpnid) {
        List<Uuid> subnets = new ArrayList<>();
        // read subnetmaps
        InstanceIdentifier<Subnetmaps> subnetmapsid = InstanceIdentifier.builder(Subnetmaps.class).build();
        Optional<Subnetmaps> subnetmaps = read(broker, LogicalDatastoreType.CONFIGURATION, subnetmapsid);
        if (subnetmaps.isPresent() && subnetmaps.get().getSubnetmap() != null) {
            List<Subnetmap> subnetMapList = subnetmaps.get().getSubnetmap();
            for (Subnetmap subnetMap : subnetMapList) {
                if (subnetMap.getVpnId() != null && subnetMap.getVpnId().equals(vpnid)) {
                    subnets.add(subnetMap.getId());
                }
            }
        }
        return subnets;
    }

    protected static String getNeutronPortNameFromVpnPortFixedIp(DataBroker broker, String vpnName, String fixedIp) {
        InstanceIdentifier id = buildVpnPortipToPortIdentifier(vpnName, fixedIp);
        Optional<VpnPortipToPort> vpnPortipToPortData = read(broker, LogicalDatastoreType.CONFIGURATION, id);
        if (vpnPortipToPortData.isPresent()) {
            return vpnPortipToPortData.get().getPortName();
        }
        LOG.error("getNeutronPortNameFromVpnPortFixedIp: Failed as vpnPortipToPortData DS is absent for VPN {} and"
                + " fixed IP {}", vpnName, fixedIp);
        return null;
    }

    protected static List<Uuid> getSubnetIdsFromNetworkId(DataBroker broker, Uuid networkId) {
        InstanceIdentifier id = buildNetworkMapIdentifier(networkId);
        Optional<NetworkMap> optionalNetworkMap = read(broker, LogicalDatastoreType.CONFIGURATION, id);
        if (optionalNetworkMap.isPresent()) {
            return optionalNetworkMap.get().getSubnetIdList();
        }
        LOG.error("getSubnetIdsFromNetworkId: Failed as networkmap DS is absent for network {}", networkId.getValue());
        return null;
    }

    protected static List<Uuid> getPortIdsFromSubnetId(DataBroker broker, Uuid subnetId) {
        InstanceIdentifier id = buildSubnetMapIdentifier(subnetId);
        Optional<Subnetmap> optionalSubnetmap = read(broker, LogicalDatastoreType.CONFIGURATION, id);
        if (optionalSubnetmap.isPresent()) {
            return optionalSubnetmap.get().getPortList();
        }
        return null;
    }

    protected static Router getNeutronRouter(DataBroker broker, Uuid routerId) {
        Router router = null;
        router = routerMap.get(routerId);
        if (router != null) {
            return router;
        }
        InstanceIdentifier<Router> inst = InstanceIdentifier.create(Neutron.class).child(Routers.class).child(Router
                .class, new RouterKey(routerId));
        Optional<Router> rtr = read(broker, LogicalDatastoreType.CONFIGURATION, inst);
        if (rtr.isPresent()) {
            router = rtr.get();
        }
        return router;
    }

    protected static Network getNeutronNetwork(DataBroker broker, Uuid networkId) {
        Network network = null;
        network = networkMap.get(networkId);
        if (network != null) {
            return network;
        }
        LOG.debug("getNeutronNetwork for {}", networkId.getValue());
        InstanceIdentifier<Network> inst = InstanceIdentifier.create(Neutron.class).child(Networks.class)
            .child(Network.class, new NetworkKey(networkId));
        Optional<Network> net = read(broker, LogicalDatastoreType.CONFIGURATION, inst);
        if (net.isPresent()) {
            network = net.get();
        }
        return network;
    }

    protected static Port getNeutronPort(DataBroker broker, Uuid portId) {
        Port prt = null;
        prt = portMap.get(portId);
        if (prt != null) {
            return prt;
        }
        LOG.debug("getNeutronPort for {}", portId.getValue());
        InstanceIdentifier<Port> inst = InstanceIdentifier.create(Neutron.class).child(Ports.class).child(Port.class,
                new PortKey(portId));
        Optional<Port> port = read(broker, LogicalDatastoreType.CONFIGURATION, inst);
        if (port.isPresent()) {
            prt = port.get();
        }
        return prt;
    }

    /**
     * Returns port_security_enabled status with the port.
     *
     * @param port the port
     * @return port_security_enabled status
     */
    protected static boolean getPortSecurityEnabled(Port port) {
        String deviceOwner = port.getDeviceOwner();
        if (deviceOwner != null && deviceOwner.startsWith("network:")) {
            // port with device owner of network:xxx is created by
            // neutorn for its internal use. So security group doesn't apply.
            // router interface, dhcp port and floating ip.
            return false;
        }
        PortSecurityExtension portSecurity = port.getAugmentation(PortSecurityExtension.class);
        if (portSecurity != null) {
            return portSecurity.isPortSecurityEnabled();
        }
        return false;
    }

    /**
     * Gets security group UUIDs delta   .
     *
     * @param port1SecurityGroups the port 1 security groups
     * @param port2SecurityGroups the port 2 security groups
     * @return the security groups delta
     */
    protected static List<Uuid> getSecurityGroupsDelta(List<Uuid> port1SecurityGroups,
            List<Uuid> port2SecurityGroups) {
        if (port1SecurityGroups == null) {
            return null;
        }

        if (port2SecurityGroups == null) {
            return port1SecurityGroups;
        }

        List<Uuid> list1 = new ArrayList<>(port1SecurityGroups);
        List<Uuid> list2 = new ArrayList<>(port2SecurityGroups);
        for (Iterator<Uuid> iterator = list1.iterator(); iterator.hasNext();) {
            Uuid securityGroup1 = iterator.next();
            for (Uuid securityGroup2 : list2) {
                if (securityGroup1.getValue().equals(securityGroup2.getValue())) {
                    iterator.remove();
                    break;
                }
            }
        }
        return list1;
    }

    /**
     * Gets the fixed ips delta.
     *
     * @param port1FixedIps the port 1 fixed ips
     * @param port2FixedIps the port 2 fixed ips
     * @return the fixed ips delta
     */
    protected static List<FixedIps> getFixedIpsDelta(List<FixedIps> port1FixedIps, List<FixedIps> port2FixedIps) {
        if (port1FixedIps == null) {
            return null;
        }

        if (port2FixedIps == null) {
            return port1FixedIps;
        }

        List<FixedIps> list1 = new ArrayList<>(port1FixedIps);
        List<FixedIps> list2 = new ArrayList<>(port2FixedIps);
        for (Iterator<FixedIps> iterator = list1.iterator(); iterator.hasNext();) {
            FixedIps fixedIps1 = iterator.next();
            for (FixedIps fixedIps2 : list2) {
                if (fixedIps1.getIpAddress().equals(fixedIps2.getIpAddress())) {
                    iterator.remove();
                    break;
                }
            }
        }
        return list1;
    }

    /**
     * Gets the allowed address pairs delta.
     *
     * @param port1AllowedAddressPairs the port 1 allowed address pairs
     * @param port2AllowedAddressPairs the port 2 allowed address pairs
     * @return the allowed address pairs delta
     */
    protected static List<AllowedAddressPairs> getAllowedAddressPairsDelta(
        List<org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.ports.rev150712.port.attributes
            .AllowedAddressPairs> port1AllowedAddressPairs,
        List<org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.ports.rev150712.port.attributes
            .AllowedAddressPairs> port2AllowedAddressPairs) {
        if (port1AllowedAddressPairs == null) {
            return null;
        }

        if (port2AllowedAddressPairs == null) {
            return getAllowedAddressPairsForAclService(port1AllowedAddressPairs);
        }

        List<org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.ports.rev150712.port.attributes
            .AllowedAddressPairs> list1 =
                new ArrayList<>(port1AllowedAddressPairs);
        List<org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.ports.rev150712.port.attributes
            .AllowedAddressPairs> list2 =
                new ArrayList<>(port2AllowedAddressPairs);
        for (Iterator<org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.ports.rev150712.port.attributes
            .AllowedAddressPairs> iterator =
             list1.iterator(); iterator.hasNext();) {
            org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.ports.rev150712.port.attributes
                .AllowedAddressPairs allowedAddressPair1 = iterator.next();
            for (org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.ports.rev150712.port.attributes
                     .AllowedAddressPairs allowedAddressPair2 : list2) {
                if (allowedAddressPair1.getKey().equals(allowedAddressPair2.getKey())) {
                    iterator.remove();
                    break;
                }
            }
        }
        return getAllowedAddressPairsForAclService(list1);
    }

    /**
     * Gets the acl allowed address pairs.
     *
     * @param macAddress the mac address
     * @param ipAddress the ip address
     * @return the acl allowed address pairs
     */
    protected static AllowedAddressPairs getAclAllowedAddressPairs(MacAddress macAddress,
            org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.types.rev160517.IpPrefixOrAddress ipAddress) {
        AllowedAddressPairsBuilder aclAllowedAdressPairBuilder = new AllowedAddressPairsBuilder();
        aclAllowedAdressPairBuilder.setMacAddress(macAddress);
        if (ipAddress != null && ipAddress.getValue() != null) {
            if (ipAddress.getIpPrefix() != null) {
                aclAllowedAdressPairBuilder.setIpAddress(new IpPrefixOrAddress(ipAddress.getIpPrefix()));
            } else {
                aclAllowedAdressPairBuilder.setIpAddress(new IpPrefixOrAddress(ipAddress.getIpAddress()));
            }
        }
        return aclAllowedAdressPairBuilder.build();
    }

    /**
     * Gets the allowed address pairs for acl service.
     *
     * @param macAddress the mac address
     * @param fixedIps the fixed ips
     * @return the allowed address pairs for acl service
     */
    protected static List<AllowedAddressPairs> getAllowedAddressPairsForAclService(MacAddress macAddress,
            List<FixedIps> fixedIps) {
        List<AllowedAddressPairs> aclAllowedAddressPairs = new ArrayList<>();
        for (FixedIps fixedIp : fixedIps) {
            aclAllowedAddressPairs.add(getAclAllowedAddressPairs(macAddress,
                    new org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.types.rev160517.IpPrefixOrAddress(
                            fixedIp.getIpAddress().getValue())));
        }
        return aclAllowedAddressPairs;
    }

    /**
     * Gets the allowed address pairs for acl service.
     *
     * @param portAllowedAddressPairs the port allowed address pairs
     * @return the allowed address pairs for acl service
     */
    protected static List<AllowedAddressPairs> getAllowedAddressPairsForAclService(
        List<org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.ports.rev150712.port.attributes
            .AllowedAddressPairs> portAllowedAddressPairs) {
        List<AllowedAddressPairs> aclAllowedAddressPairs = new ArrayList<>();
        for (org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.ports.rev150712.port.attributes.AllowedAddressPairs
                 portAllowedAddressPair : portAllowedAddressPairs) {
            aclAllowedAddressPairs.add(getAclAllowedAddressPairs(portAllowedAddressPair.getMacAddress(),
                portAllowedAddressPair.getIpAddress()));
        }
        return aclAllowedAddressPairs;
    }

    /**
     * Gets the IPv6 Link Local Address corresponding to the MAC Address.
     *
     * @param macAddress the mac address
     * @return the allowed address pairs for acl service which includes the MAC + IPv6LLA
     */
    protected static AllowedAddressPairs updateIPv6LinkLocalAddressForAclService(MacAddress macAddress) {
        IpAddress ipv6LinkLocalAddress = getIpv6LinkLocalAddressFromMac(macAddress);
        return getAclAllowedAddressPairs(macAddress,
                new org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.types.rev160517.IpPrefixOrAddress(
                        ipv6LinkLocalAddress.getValue()));
    }

    /**
     * Gets the updated security groups.
     *
     * @param aclInterfaceSecurityGroups the acl interface security groups
     * @param origSecurityGroups the orig security groups
     * @param newSecurityGroups the new security groups
     * @return the updated security groups
     */
    protected static List<Uuid> getUpdatedSecurityGroups(List<Uuid> aclInterfaceSecurityGroups,
            List<Uuid> origSecurityGroups, List<Uuid> newSecurityGroups) {
        List<Uuid> addedGroups = getSecurityGroupsDelta(newSecurityGroups, origSecurityGroups);
        List<Uuid> deletedGroups = getSecurityGroupsDelta(origSecurityGroups, newSecurityGroups);
        List<Uuid> updatedSecurityGroups =
                aclInterfaceSecurityGroups != null ? new ArrayList<>(aclInterfaceSecurityGroups) : new ArrayList<>();
        if (addedGroups != null) {
            updatedSecurityGroups.addAll(addedGroups);
        }
        if (deletedGroups != null) {
            updatedSecurityGroups.removeAll(deletedGroups);
        }
        return updatedSecurityGroups;
    }

    /**
     * Gets the allowed address pairs for fixed ips.
     *
     * @param aclInterfaceAllowedAddressPairs the acl interface allowed address pairs
     * @param portMacAddress the port mac address
     * @param origFixedIps the orig fixed ips
     * @param newFixedIps the new fixed ips
     * @return the allowed address pairs for fixed ips
     */
    protected static List<AllowedAddressPairs> getAllowedAddressPairsForFixedIps(
            List<AllowedAddressPairs> aclInterfaceAllowedAddressPairs, MacAddress portMacAddress,
            List<FixedIps> origFixedIps, List<FixedIps> newFixedIps) {
        List<FixedIps> addedFixedIps = getFixedIpsDelta(newFixedIps, origFixedIps);
        List<FixedIps> deletedFixedIps = getFixedIpsDelta(origFixedIps, newFixedIps);
        List<AllowedAddressPairs> updatedAllowedAddressPairs =
            aclInterfaceAllowedAddressPairs != null
                ? new ArrayList<>(aclInterfaceAllowedAddressPairs) : new ArrayList<>();
        if (deletedFixedIps != null) {
            updatedAllowedAddressPairs.removeAll(getAllowedAddressPairsForAclService(portMacAddress, deletedFixedIps));
        }
        if (addedFixedIps != null) {
            updatedAllowedAddressPairs.addAll(getAllowedAddressPairsForAclService(portMacAddress, addedFixedIps));
        }
        return updatedAllowedAddressPairs;
    }

    /**
     * Gets the updated allowed address pairs.
     *
     * @param aclInterfaceAllowedAddressPairs the acl interface allowed address pairs
     * @param origAllowedAddressPairs the orig allowed address pairs
     * @param newAllowedAddressPairs the new allowed address pairs
     * @return the updated allowed address pairs
     */
    protected static List<AllowedAddressPairs> getUpdatedAllowedAddressPairs(
            List<AllowedAddressPairs> aclInterfaceAllowedAddressPairs,
            List<org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.ports.rev150712.port.attributes
                .AllowedAddressPairs> origAllowedAddressPairs,
            List<org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.ports.rev150712.port.attributes
                .AllowedAddressPairs> newAllowedAddressPairs) {
        List<AllowedAddressPairs> addedAllowedAddressPairs =
            getAllowedAddressPairsDelta(newAllowedAddressPairs,origAllowedAddressPairs);
        List<AllowedAddressPairs> deletedAllowedAddressPairs =
            getAllowedAddressPairsDelta(origAllowedAddressPairs, newAllowedAddressPairs);
        List<AllowedAddressPairs> updatedAllowedAddressPairs =
            aclInterfaceAllowedAddressPairs != null
                ? new ArrayList<>(aclInterfaceAllowedAddressPairs) : new ArrayList<>();
        if (addedAllowedAddressPairs != null) {
            updatedAllowedAddressPairs.addAll(addedAllowedAddressPairs);
        }
        if (deletedAllowedAddressPairs != null) {
            updatedAllowedAddressPairs.removeAll(deletedAllowedAddressPairs);
        }
        return updatedAllowedAddressPairs;
    }

    /**
     * Populate interface acl builder.
     *
     * @param interfaceAclBuilder the interface acl builder
     * @param port the port
     */
    protected static void populateInterfaceAclBuilder(InterfaceAclBuilder interfaceAclBuilder, Port port) {
        // Handle security group enabled
        List<Uuid> securityGroups = port.getSecurityGroups();
        if (securityGroups != null) {
            interfaceAclBuilder.setSecurityGroups(securityGroups);
        }
        List<AllowedAddressPairs> aclAllowedAddressPairs = NeutronvpnUtils.getAllowedAddressPairsForAclService(
                port.getMacAddress(), port.getFixedIps());
        // Update the allowed address pair with the IPv6 LLA that is auto configured on the port.
        aclAllowedAddressPairs.add(NeutronvpnUtils.updateIPv6LinkLocalAddressForAclService(port.getMacAddress()));
        List<org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.ports.rev150712.port.attributes.AllowedAddressPairs>
            portAllowedAddressPairs = port.getAllowedAddressPairs();
        if (portAllowedAddressPairs != null) {
            aclAllowedAddressPairs.addAll(NeutronvpnUtils.getAllowedAddressPairsForAclService(portAllowedAddressPairs));
        }
        interfaceAclBuilder.setAllowedAddressPairs(aclAllowedAddressPairs);
    }

    protected static void populateSubnetIpPrefixes(DataBroker broker, Port port) {
        List<IpPrefixOrAddress> subnetIpPrefixes = NeutronvpnUtils.getSubnetIpPrefixes(broker, port);
        if (subnetIpPrefixes != null) {
            String portId = port.getUuid().getValue();
            InstanceIdentifier portSubnetIpPrefixIdentifier =
                NeutronvpnUtils.buildPortSubnetIpPrefixIdentifier(portId);
            PortSubnetIpPrefixesBuilder subnetIpPrefixesBuilder = new PortSubnetIpPrefixesBuilder()
                    .setKey(new PortSubnetIpPrefixesKey(portId)).setPortId(portId)
                    .setSubnetIpPrefixes(subnetIpPrefixes);
            MDSALUtil.syncWrite(broker, LogicalDatastoreType.OPERATIONAL, portSubnetIpPrefixIdentifier,
                    subnetIpPrefixesBuilder.build());
            LOG.debug("Created Subnet IP Prefixes for port {}", port.getUuid().getValue());
        }
    }

    protected static List<IpPrefixOrAddress> getSubnetIpPrefixes(DataBroker broker, Port port) {
        List<Uuid> subnetIds = getSubnetIdsFromNetworkId(broker, port.getNetworkId());
        if (subnetIds == null) {
            LOG.error("Failed to get Subnet Ids for the Network {}", port.getNetworkId());
            return null;
        }
        List<IpPrefixOrAddress> subnetIpPrefixes = new ArrayList<>();
        for (Uuid subnetId : subnetIds) {
            Subnet subnet = getNeutronSubnet(broker, subnetId);
            if (subnet != null) {
                subnetIpPrefixes.add(new IpPrefixOrAddress(subnet.getCidr()));
            }
        }
        return subnetIpPrefixes;
    }

    protected static Subnet getNeutronSubnet(DataBroker broker, Uuid subnetId) {
        Subnet subnet = null;
        subnet = subnetMap.get(subnetId);
        if (subnet != null) {
            return subnet;
        }
        InstanceIdentifier<Subnet> inst = InstanceIdentifier.create(Neutron.class).child(Subnets.class).child(Subnet
                .class, new SubnetKey(subnetId));
        Optional<Subnet> sn = read(broker, LogicalDatastoreType.CONFIGURATION, inst);

        if (sn.isPresent()) {
            subnet = sn.get();
        }
        return subnet;
    }

    protected static List<Uuid> getNeutronRouterSubnetIds(DataBroker broker, Uuid routerId) {
        LOG.debug("getNeutronRouterSubnetIds for {}", routerId.getValue());
        List<Uuid> subnetIdList = new ArrayList<>();
        Optional<Subnetmaps> subnetMaps = read(broker, LogicalDatastoreType.CONFIGURATION,
            InstanceIdentifier.builder(Subnetmaps.class).build());
        if (subnetMaps.isPresent() && subnetMaps.get().getSubnetmap() != null) {
            for (Subnetmap subnetmap : subnetMaps.get().getSubnetmap()) {
                if (routerId.equals(subnetmap.getRouterId())) {
                    subnetIdList.add(subnetmap.getId());
                }
            }
        }
        LOG.debug("returning from getNeutronRouterSubnetIds for {}", routerId.getValue());
        return subnetIdList;
    }

    // TODO Clean up the exception handling and the console output
    @SuppressWarnings({"checkstyle:IllegalCatch", "checkstyle:RegexpSinglelineJava"})
    protected static Short getIPPrefixFromPort(DataBroker broker, Port port) {
        try {
            Uuid subnetUUID = port.getFixedIps().get(0).getSubnetId();
            SubnetKey subnetkey = new SubnetKey(subnetUUID);
            InstanceIdentifier<Subnet> subnetidentifier = InstanceIdentifier.create(Neutron.class).child(Subnets
                    .class).child(Subnet.class, subnetkey);
            Optional<Subnet> subnet = read(broker, LogicalDatastoreType.CONFIGURATION, subnetidentifier);
            if (subnet.isPresent()) {
                String cidr = String.valueOf(subnet.get().getCidr().getValue());
                // Extract the prefix length from cidr
                String[] parts = cidr.split("/");
                if (parts.length == 2) {
                    return Short.valueOf(parts[1]);
                } else {
                    LOG.trace("Could not retrieve prefix from subnet CIDR");
                    System.out.println("Could not retrieve prefix from subnet CIDR");
                }
            } else {
                LOG.trace("Unable to read on subnet datastore");
            }
        } catch (Exception e) {
            LOG.error("Failed to retrieve IP prefix from port for port {}", port.getUuid().getValue(), e);
            System.out.println("Failed to retrieve IP prefix from port : " + e.getMessage());
        }
        LOG.error("Failed for port {}", port.getUuid().getValue());
        return null;
    }

    // TODO Clean up the exception handling
    @SuppressWarnings("checkstyle:IllegalCatch")
    protected static void createVpnPortFixedIpToPort(DataBroker broker,String vpnName, String fixedIp,
                                                     String portName, String macAddress, boolean isSubnetIp,
                                                     WriteTransaction writeConfigTxn) {
        InstanceIdentifier<VpnPortipToPort> id = NeutronvpnUtils.buildVpnPortipToPortIdentifier(vpnName, fixedIp);
        VpnPortipToPortBuilder builder = new VpnPortipToPortBuilder()
            .setKey(new VpnPortipToPortKey(fixedIp, vpnName))
            .setVpnName(vpnName).setPortFixedip(fixedIp)
            .setPortName(portName).setMacAddress(macAddress).setSubnetIp(isSubnetIp);
        try {
            if (writeConfigTxn != null) {
                writeConfigTxn.put(LogicalDatastoreType.CONFIGURATION, id, builder.build());
            } else {
                MDSALUtil.syncWrite(broker, LogicalDatastoreType.CONFIGURATION, id, builder.build());
            }
            LOG.trace("Neutron port with fixedIp: {}, vpn {}, interface {}, mac {}, isSubnetIp {} added to "
                + "VpnPortipToPort DS", fixedIp, vpnName, portName, macAddress, isSubnetIp);
        } catch (Exception e) {
            LOG.error("Failure while creating VPNPortFixedIpToPort map for vpn {} - fixedIP {}", vpnName, fixedIp,
                    e);
        }
    }

    // TODO Clean up the exception handling
    @SuppressWarnings("checkstyle:IllegalCatch")
    protected static void removeVpnPortFixedIpToPort(DataBroker broker, String vpnName,
                                                     String fixedIp, WriteTransaction writeConfigTxn) {
        InstanceIdentifier<VpnPortipToPort> id = NeutronvpnUtils.buildVpnPortipToPortIdentifier(vpnName, fixedIp);
        try {
            if (writeConfigTxn != null) {
                writeConfigTxn.delete(LogicalDatastoreType.CONFIGURATION, id);
            } else {
                MDSALUtil.syncDelete(broker, LogicalDatastoreType.CONFIGURATION, id);
            }
            LOG.trace("Neutron router port with fixedIp: {}, vpn {} removed from LearntVpnPortipToPort DS", fixedIp,
                    vpnName);
        } catch (Exception e) {
            LOG.error("Failure while removing VPNPortFixedIpToPort map for vpn {} - fixedIP {}", vpnName, fixedIp,
                    e);
        }
    }

    // TODO Clean up the exception handling
    @SuppressWarnings("checkstyle:IllegalCatch")
    protected static void removeLearntVpnVipToPort(DataBroker broker, String vpnName, String fixedIp) {
        InstanceIdentifier<LearntVpnVipToPort> id = NeutronvpnUtils.buildLearntVpnVipToPortIdentifier(vpnName, fixedIp);
        try {
            synchronized ((vpnName + fixedIp).intern()) {
                MDSALUtil.syncDelete(broker, LogicalDatastoreType.OPERATIONAL, id);
            }
            LOG.trace("Neutron router port with fixedIp: {}, vpn {} removed from LearntVpnPortipToPort DS", fixedIp,
                    vpnName);
        } catch (Exception e) {
            LOG.error("Failure while removing LearntVpnPortFixedIpToPort map for vpn {} - fixedIP {}",
                vpnName, fixedIp, e);
        }
    }

    public static void addToNetworkCache(Network network) {
        networkMap.put(network.getUuid(), network);
    }

    public static void removeFromNetworkCache(Network network) {
        networkMap.remove(network.getUuid());
    }

    public static void addToRouterCache(Router router) {
        routerMap.put(router.getUuid(), router);
    }

    public static void removeFromRouterCache(Router router) {
        routerMap.remove(router.getUuid());
    }

    public static void addToPortCache(Port port) {
        portMap.put(port.getUuid(), port);
    }

    public static void removeFromPortCache(Port port) {
        portMap.remove(port.getUuid());
    }

    public static void addToSubnetCache(Subnet subnet) {
        subnetMap.put(subnet.getUuid(), subnet);
        IpAddress gatewayIp = subnet.getGatewayIp();
        if (gatewayIp != null) {
            subnetGwIpMap.computeIfAbsent(gatewayIp, k -> Sets.newConcurrentHashSet()).add(subnet.getUuid());
        }
    }

    public static void removeFromSubnetCache(Subnet subnet) {
        subnetMap.remove(subnet.getUuid());
        IpAddress gatewayIp = subnet.getGatewayIp();
        if (gatewayIp != null) {
            Set<Uuid> gwIps = subnetGwIpMap.get(gatewayIp);
            if (gwIps != null) {
                gwIps.remove(subnet.getUuid());
            }
        }
    }

    public static String getSegmentationIdFromNeutronNetwork(Network network) {
        String segmentationId = null;
        NetworkProviderExtension providerExtension = network.getAugmentation(NetworkProviderExtension.class);
        if (providerExtension != null) {
            Class<? extends NetworkTypeBase> networkType = providerExtension.getNetworkType();
            segmentationId = NeutronUtils.getSegmentationIdFromNeutronNetwork(network, networkType);
        }

        return segmentationId;
    }

    public static Class<? extends SegmentTypeBase> getSegmentTypeFromNeutronNetwork(Network network) {
        NetworkProviderExtension providerExtension = network.getAugmentation(NetworkProviderExtension.class);
        return providerExtension != null ? NETWORK_MAP.get(providerExtension.getNetworkType()) : null;
    }

    public static String getPhysicalNetworkName(Network network) {
        NetworkProviderExtension providerExtension = network.getAugmentation(NetworkProviderExtension.class);
        return providerExtension != null ? providerExtension.getPhysicalNetwork() : null;
    }

    public static Collection<Uuid> getSubnetIdsForGatewayIp(IpAddress ipAddress) {
        return subnetGwIpMap.getOrDefault(ipAddress, Collections.emptySet());
    }

    static InstanceIdentifier<VpnPortipToPort> buildVpnPortipToPortIdentifier(String vpnName, String fixedIp) {
        InstanceIdentifier<VpnPortipToPort> id =
            InstanceIdentifier.builder(NeutronVpnPortipPortData.class)
                .child(VpnPortipToPort.class, new VpnPortipToPortKey(fixedIp, vpnName)).build();
        return id;
    }

    static InstanceIdentifier<LearntVpnVipToPort> buildLearntVpnVipToPortIdentifier(String vpnName, String fixedIp) {
        InstanceIdentifier<LearntVpnVipToPort> id =
            InstanceIdentifier.builder(LearntVpnVipToPortData.class)
                .child(LearntVpnVipToPort.class, new LearntVpnVipToPortKey(fixedIp, vpnName)).build();
        return id;
    }

    static Boolean getIsExternal(Network network) {
        return network.getAugmentation(NetworkL3Extension.class) != null
                && network.getAugmentation(NetworkL3Extension.class).isExternal();
    }

    public static void addToQosPolicyCache(QosPolicy qosPolicy) {
        qosPolicyMap.put(qosPolicy.getUuid(),qosPolicy);
    }

    public static void removeFromQosPolicyCache(QosPolicy qosPolicy) {
        qosPolicyMap.remove(qosPolicy.getUuid());
    }

    public static void addToQosPortsCache(Uuid qosUuid, Port port) {
        if (qosPortsMap.containsKey(qosUuid)) {
            if (!qosPortsMap.get(qosUuid).containsKey(port.getUuid())) {
                qosPortsMap.get(qosUuid).put(port.getUuid(), port);
            }
        } else {
            HashMap<Uuid, Port> portMap = new HashMap<>();
            portMap.put(port.getUuid(), port);
            qosPortsMap.put(qosUuid, portMap);
        }
    }

    public static void removeFromQosPortsCache(Uuid qosUuid, Port port) {
        if (qosPortsMap.containsKey(qosUuid) && qosPortsMap.get(qosUuid).containsKey(port.getUuid())) {
            qosPortsMap.get(qosUuid).remove(port.getUuid(), port);
        }
    }

    public static void addToQosNetworksCache(Uuid qosUuid, Network network) {
        if (qosNetworksMap.containsKey(qosUuid)) {
            if (!qosNetworksMap.get(qosUuid).containsKey(network.getUuid())) {
                qosNetworksMap.get(qosUuid).put(network.getUuid(), network);
            }
        } else {
            HashMap<Uuid, Network> networkMap = new HashMap<>();
            networkMap.put(network.getUuid(), network);
            qosNetworksMap.put(qosUuid, networkMap);
        }
    }

    public static void removeFromQosNetworksCache(Uuid qosUuid, Network network) {
        if (qosNetworksMap.containsKey(qosUuid) && qosNetworksMap.get(qosUuid).containsKey(network.getUuid())) {
            qosNetworksMap.get(qosUuid).remove(network.getUuid(), network);
        }
    }

    static InstanceIdentifier<NetworkMap> buildNetworkMapIdentifier(Uuid networkId) {
        InstanceIdentifier<NetworkMap> id = InstanceIdentifier.builder(NetworkMaps.class).child(NetworkMap.class, new
                NetworkMapKey(networkId)).build();
        return id;
    }

    static InstanceIdentifier<VpnInterface> buildVpnInterfaceIdentifier(String ifName) {
        InstanceIdentifier<VpnInterface> id = InstanceIdentifier.builder(VpnInterfaces.class).child(VpnInterface
                .class, new VpnInterfaceKey(ifName)).build();
        return id;
    }

    static InstanceIdentifier<Subnetmap> buildSubnetMapIdentifier(Uuid subnetId) {
        InstanceIdentifier<Subnetmap> id = InstanceIdentifier.builder(Subnetmaps.class).child(Subnetmap.class, new
                SubnetmapKey(subnetId)).build();
        return id;
    }

    static InstanceIdentifier<Interface> buildVlanInterfaceIdentifier(String interfaceName) {
        InstanceIdentifier<Interface> id = InstanceIdentifier.builder(Interfaces.class).child(Interface.class, new
                InterfaceKey(interfaceName)).build();
        return id;
    }

    static InstanceIdentifier<org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.ext
            .routers.Routers> buildExtRoutersIdentifier(Uuid routerId) {
        InstanceIdentifier<org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.ext.routers
                .Routers> id = InstanceIdentifier.builder(ExtRouters.class).child(org.opendaylight.yang.gen.v1.urn
                .opendaylight.netvirt.natservice.rev160111.ext.routers.Routers.class, new RoutersKey(routerId
                .getValue())).build();
        return id;
    }

    static InstanceIdentifier<FloatingIpIdToPortMapping> buildfloatingIpIdToPortMappingIdentifier(Uuid floatingIpId) {
        return InstanceIdentifier.builder(FloatingIpPortInfo.class).child(FloatingIpIdToPortMapping.class, new
                FloatingIpIdToPortMappingKey(floatingIpId)).build();
    }

    static InstanceIdentifier<PortSubnetIpPrefixes> buildPortSubnetIpPrefixIdentifier(String portId) {
        InstanceIdentifier<PortSubnetIpPrefixes> id = InstanceIdentifier.builder(PortsSubnetIpPrefixes.class)
            .child(PortSubnetIpPrefixes.class, new PortSubnetIpPrefixesKey(portId)).build();
        return id;
    }

    // TODO Remove this method entirely
    @SuppressWarnings("checkstyle:IllegalCatch")
    private static <T extends DataObject> Optional<T> read(DataBroker broker, LogicalDatastoreType datastoreType,
                                                   InstanceIdentifier<T> path) {
        try {
            return SingleTransactionDataBroker.syncReadOptional(broker, datastoreType, path);
        } catch (ReadFailedException e) {
            throw new RuntimeException(e);
        }
    }

    public static Class<? extends NetworkTypeBase> getNetworkType(Network network) {
        NetworkProviderExtension providerExtension = network.getAugmentation(NetworkProviderExtension.class);
        return providerExtension != null ? providerExtension.getNetworkType() : null;
    }

    static ProviderTypes getProviderNetworkType(Network network) {
        if (network == null) {
            LOG.error("Error in getting provider network type since network is null");
            return null;
        }
        NetworkProviderExtension npe = network.getAugmentation(NetworkProviderExtension.class);
        if (npe != null) {
            Class<? extends NetworkTypeBase> networkTypeBase = npe.getNetworkType();
            if (networkTypeBase != null) {
                if (networkTypeBase.isAssignableFrom(NetworkTypeFlat.class)) {
                    return ProviderTypes.FLAT;
                } else if (networkTypeBase.isAssignableFrom(NetworkTypeVlan.class)) {
                    return ProviderTypes.VLAN;
                } else if (networkTypeBase.isAssignableFrom(NetworkTypeVxlan.class)) {
                    return ProviderTypes.VXLAN;
                } else if (networkTypeBase.isAssignableFrom(NetworkTypeGre.class)) {
                    return ProviderTypes.GRE;
                }
            }
        }
        LOG.error("Error in getting provider network type since network provider extension is null for network "
                + "{}", network.getUuid().getValue());
        return null;
    }

    static boolean isNetworkTypeSupported(Network network) {
        NetworkProviderExtension npe = network.getAugmentation(NetworkProviderExtension.class);
        return npe != null && npe.getNetworkType() != null && SUPPORTED_NETWORK_TYPES.contains(npe.getNetworkType());
    }

    static boolean isNetworkOfType(Network network, Class<? extends NetworkTypeBase> type) {
        NetworkProviderExtension npe = network.getAugmentation(NetworkProviderExtension.class);
        if (npe != null && npe.getNetworkType() != null) {
            return type.isAssignableFrom(npe.getNetworkType());
        }
        return false;
    }

    static boolean isFlatOrVlanNetwork(Network network) {
        return network != null
                && (isNetworkOfType(network, NetworkTypeVlan.class) || isNetworkOfType(network, NetworkTypeFlat.class));
    }

    static boolean isVlanOrVxlanNetwork(Class<? extends NetworkTypeBase> type) {
        return type.isAssignableFrom(NetworkTypeVxlan.class) || type.isAssignableFrom(NetworkTypeVlan.class);
    }

    /**
     * Get inter-VPN link state.
     *
     * @param broker data broker
     * @param vpnLinkName VPN link name
     * @return Optional of InterVpnLinkState
     */
    public static Optional<InterVpnLinkState> getInterVpnLinkState(DataBroker broker, String vpnLinkName) {
        InstanceIdentifier<InterVpnLinkState> vpnLinkStateIid = InstanceIdentifier.builder(InterVpnLinkStates.class)
                .child(InterVpnLinkState.class, new InterVpnLinkStateKey(vpnLinkName)).build();
        return read(broker, LogicalDatastoreType.CONFIGURATION, vpnLinkStateIid);
    }

    /**
     * Returns an InterVpnLink by searching by one of its endpoint's IP.
     *
     * @param broker The Databroker
     * @param endpointIp IP to search for
     * @return a InterVpnLink
     */
    public static Optional<InterVpnLink> getInterVpnLinkByEndpointIp(DataBroker broker, String endpointIp) {
        InstanceIdentifier<InterVpnLinks> interVpnLinksIid = InstanceIdentifier.builder(InterVpnLinks.class).build();
        Optional<InterVpnLinks> interVpnLinksOpData = MDSALUtil.read(broker, LogicalDatastoreType.CONFIGURATION,
                interVpnLinksIid);
        if (interVpnLinksOpData.isPresent()) {
            List<InterVpnLink> allInterVpnLinks = interVpnLinksOpData.get().getInterVpnLink();
            for (InterVpnLink interVpnLink : allInterVpnLinks) {
                if (interVpnLink.getFirstEndpoint().getIpAddress().getValue().equals(endpointIp)
                        || interVpnLink.getSecondEndpoint().getIpAddress().getValue().equals(endpointIp)) {
                    return Optional.of(interVpnLink);
                }
            }
        }
        return Optional.absent();
    }


    public static Set<RouterDpnList> getAllRouterDpnList(DataBroker broker, BigInteger dpid) {
        Set<RouterDpnList> ret = new HashSet<>();
        InstanceIdentifier<NeutronRouterDpns> routerDpnId =
                InstanceIdentifier.create(NeutronRouterDpns.class);
        Optional<NeutronRouterDpns> neutronRouterDpnsOpt =
            MDSALUtil.read(broker, LogicalDatastoreType.OPERATIONAL, routerDpnId);
        if (neutronRouterDpnsOpt.isPresent()) {
            NeutronRouterDpns neutronRouterDpns = neutronRouterDpnsOpt.get();
            List<RouterDpnList> routerDpnLists = neutronRouterDpns.getRouterDpnList();
            for (RouterDpnList routerDpnList : routerDpnLists) {
                if (routerDpnList.getDpnVpninterfacesList() != null) {
                    for (DpnVpninterfacesList dpnInterfaceList : routerDpnList.getDpnVpninterfacesList()) {
                        if (dpnInterfaceList.getDpnId().equals(dpid)) {
                            ret.add(routerDpnList);
                        }
                    }
                }
            }
        }
        return ret;
    }

    protected static Integer getUniqueRDId(IdManagerService idManager, String poolName, String idKey) {
        AllocateIdInput getIdInput = new AllocateIdInputBuilder().setPoolName(poolName).setIdKey(idKey).build();
        try {
            Future<RpcResult<AllocateIdOutput>> result = idManager.allocateId(getIdInput);
            RpcResult<AllocateIdOutput> rpcResult = result.get();
            if (rpcResult.isSuccessful()) {
                return rpcResult.getResult().getIdValue().intValue();
            } else {
                LOG.error("RPC call to get unique ID for pool name {} with ID key {} returned with errors {}",
                        poolName, idKey, rpcResult.getErrors());
            }
        } catch (InterruptedException | ExecutionException e) {
            LOG.error("Exception when getting Unique Id for poolname {} and ID Key {}", poolName, idKey, e);
        }
        LOG.error("getUniqueRdId: Failed to return ID for poolname {} and ID Key {}", poolName, idKey);
        return null;
    }

    protected static void releaseRDId(IdManagerService idManager, String poolName, String idKey) {
        ReleaseIdInput idInput = new ReleaseIdInputBuilder().setPoolName(poolName).setIdKey(idKey).build();
        try {
            Future<RpcResult<Void>> result = idManager.releaseId(idInput);
            RpcResult<Void> rpcResult = result.get();
            if (!rpcResult.isSuccessful()) {
                LOG.error("RPC Call to Get Unique Id returned with errors for poolname {} and ID Key {}",
                        poolName, idKey, rpcResult.getErrors());
            } else {
                LOG.info("ID {} for RD released successfully", idKey);
            }
        } catch (InterruptedException | ExecutionException e) {
            LOG.error("Exception when trying to release ID for poolname {} and ID Key {}", poolName, idKey, e);
        }
    }

    protected static IpAddress getIpv6LinkLocalAddressFromMac(MacAddress mac) {
        byte[] octets = bytesFromHexString(mac.getValue());

        /* As per the RFC2373, steps involved to generate a LLA include
           1. Convert the 48 bit MAC address to 64 bit value by inserting 0xFFFE
              between OUI and NIC Specific part.
           2. Invert the Universal/Local flag in the OUI portion of the address.
           3. Use the prefix "FE80::/10" along with the above 64 bit Interface
              identifier to generate the IPv6 LLA. */

        StringBuffer interfaceID = new StringBuffer();
        short u8byte = (short) (octets[0] & 0xff);
        u8byte ^= 1 << 1;
        interfaceID.append(Integer.toHexString(0xFF & u8byte));
        interfaceID.append(StringUtils.leftPad(Integer.toHexString(0xFF & octets[1]), 2, "0"));
        interfaceID.append(":");
        interfaceID.append(Integer.toHexString(0xFF & octets[2]));
        interfaceID.append("ff:fe");
        interfaceID.append(StringUtils.leftPad(Integer.toHexString(0xFF & octets[3]), 2, "0"));
        interfaceID.append(":");
        interfaceID.append(Integer.toHexString(0xFF & octets[4]));
        interfaceID.append(StringUtils.leftPad(Integer.toHexString(0xFF & octets[5]), 2, "0"));

        Ipv6Address ipv6LLA = new Ipv6Address("fe80:0:0:0:" + interfaceID.toString());
        IpAddress ipAddress = new IpAddress(ipv6LLA.getValue().toCharArray());
        return ipAddress;
    }

    protected static byte[] bytesFromHexString(String values) {
        String target = "";
        if (values != null) {
            target = values;
        }
        String[] octets = target.split(":");

        byte[] ret = new byte[octets.length];
        for (int i = 0; i < octets.length; i++) {
            ret[i] = Integer.valueOf(octets[i], 16).byteValue();
        }
        return ret;
    }

    public static List<String> getExistingRDs(DataBroker broker) {
        List<String> existingRDs = new ArrayList<>();
        InstanceIdentifier<VpnInstances> path = InstanceIdentifier.builder(VpnInstances.class).build();
        Optional<VpnInstances> vpnInstancesOptional = read(broker, LogicalDatastoreType.CONFIGURATION, path);
        if (vpnInstancesOptional.isPresent() && vpnInstancesOptional.get().getVpnInstance() != null) {
            for (VpnInstance vpnInstance : vpnInstancesOptional.get().getVpnInstance()) {
                if (vpnInstance.getIpv4Family() == null) {
                    continue;
                }
                List<String> rds = vpnInstance.getIpv4Family().getRouteDistinguisher();
                if (rds != null) {
                    existingRDs.addAll(rds);
                }
            }
        }
        return existingRDs;
    }

    protected static boolean doesVpnExist(DataBroker broker, Uuid vpnId) {
        InstanceIdentifier<VpnMap> vpnMapIdentifier = InstanceIdentifier.builder(VpnMaps.class).child(VpnMap.class,
                new VpnMapKey(vpnId)).build();
        return read(broker, LogicalDatastoreType.CONFIGURATION, vpnMapIdentifier).isPresent();
    }

    protected static Optional<org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.external
        .subnets.Subnets> getOptionalExternalSubnets(DataBroker dataBroker, Uuid subnetId) {
        InstanceIdentifier<org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice
            .rev160111.external.subnets.Subnets> subnetsIdentifier =
                InstanceIdentifier.builder(ExternalSubnets.class)
                .child(org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice
                        .rev160111.external.subnets.Subnets.class, new SubnetsKey(subnetId)).build();
        return read(dataBroker, LogicalDatastoreType.CONFIGURATION, subnetsIdentifier);
    }

    public static List<StaticMacEntries> buildStaticMacEntry(Port port) {
        PhysAddress physAddress = new PhysAddress(port.getMacAddress().getValue());
        List<FixedIps> fixedIps = port.getFixedIps();
        IpAddress ipAddress = null;
        if (isNotEmpty(fixedIps)) {
            ipAddress = port.getFixedIps().get(0).getIpAddress();
        }
        StaticMacEntriesBuilder staticMacEntriesBuilder = new StaticMacEntriesBuilder();
        List<StaticMacEntries> staticMacEntries = new ArrayList<>();
        if (ipAddress != null) {
            staticMacEntries.add(staticMacEntriesBuilder.setMacAddress(physAddress).setIpPrefix(ipAddress).build());
        } else {
            staticMacEntries.add(staticMacEntriesBuilder.setMacAddress(physAddress).build());
        }
        return staticMacEntries;
    }

    public static boolean isEmpty(Collection collection) {
        return collection == null || collection.isEmpty();
    }

    public static boolean isNotEmpty(Collection collection) {
        return !isEmpty(collection);
    }

    /**Method to get an ipVersionChosen as IPV4 and/or IPV6 or undefined from the subnetmaps of the router.
     * @param dataBroker to get informations from data store
     * @param routerUuid the Uuid for which find out the IP version associated
     * @return an IpVersionChoice used by the router from its attached subnetmaps. IpVersionChoice.UNDEFINED if any
     */
    public static IpVersionChoice getIpVersionChoicesFromRouterUuid(DataBroker dataBroker, Uuid routerUuid) {
        IpVersionChoice rep = IpVersionChoice.UNDEFINED;
        if (routerUuid == null) {
            return rep;
        }
        List<Subnetmap> subnetmapList = getNeutronRouterSubnetMaps(dataBroker, routerUuid);
        if (subnetmapList.isEmpty()) {
            return rep;
        }
        for (Subnetmap sn : subnetmapList) {
            if (sn.getSubnetIp() != null) {
                IpVersionChoice ipVers = NeutronUtils.getIpVersion(sn.getSubnetIp());
                if (rep.choice != ipVers.choice) {
                    rep.addVersion(ipVers);
                }
                if (rep.choice == rep.IPV4AND6.choice) {
                    return rep;
                }
            }
        }
        return rep;
    }

    /**This method return the list of Subnetmap associated to the router or a empty list if any.
     * @param broker the data broker to get information
     * @param routerId the Uuid of router for which subnetmap is find out
     * @return a list of Subnetmap associated to the router. it could be empty if any
     */
    protected static List<Subnetmap> getNeutronRouterSubnetMaps(DataBroker broker, Uuid routerId) {
        List<Subnetmap> subnetIdList = new ArrayList<>();
        Optional<Subnetmaps> subnetMaps = read(broker, LogicalDatastoreType.CONFIGURATION,
            InstanceIdentifier.builder(Subnetmaps.class).build());
        if (subnetMaps.isPresent() && subnetMaps.get().getSubnetmap() != null) {
            for (Subnetmap subnetmap : subnetMaps.get().getSubnetmap()) {
                if (routerId.equals(subnetmap.getRouterId())) {
                    subnetIdList.add(subnetmap);
                }
            }
        }
        return subnetIdList;
    }
}
