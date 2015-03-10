/*
 * Copyright (C) 2015 Red Hat, Inc.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 *
 * Authors : Sam Hague
 */
package org.opendaylight.ovsdb.openstack.netvirt.providers.openflow13;

import com.google.common.collect.Lists;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.DataChangeListener;
import org.opendaylight.controller.md.sal.common.api.data.AsyncDataBroker;
import org.opendaylight.controller.md.sal.common.api.data.AsyncDataChangeEvent;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
//import org.opendaylight.ovsdb.openstack.netvirt.NodeUtils;
import org.opendaylight.ovsdb.openstack.netvirt.api.NetworkingProviderManager;
import org.opendaylight.ovsdb.utils.servicehelper.ServiceHelper;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.FlowCapableNode;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.Nodes;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.nodes.Node;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.nodes.NodeKey;
import org.opendaylight.yangtools.concepts.ListenerRegistration;
import org.opendaylight.yangtools.yang.binding.DataObject;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FlowCapableNodeDataChangeListener implements DataChangeListener, AutoCloseable {
    private static final Logger LOG = LoggerFactory.getLogger(FlowCapableNodeDataChangeListener.class);
    private ListenerRegistration<DataChangeListener> registration;
    private List<Node> nodeCache = Lists.newArrayList();

    public static final InstanceIdentifier<FlowCapableNode> createFlowCapableNodePath () {
        return InstanceIdentifier.builder(Nodes.class)
                .child(Node.class)
                .augmentation(FlowCapableNode.class)
                .build();
    }

    public FlowCapableNodeDataChangeListener (DataBroker dataBroker) {
        LOG.info("Registering FlowCapableNodeChangeListener");
        registration = dataBroker.registerDataChangeListener(LogicalDatastoreType.CONFIGURATION,
                createFlowCapableNodePath(), this, AsyncDataBroker.DataChangeScope.ONE);
    }

    @Override
    public void close () throws Exception {
        registration.close();
    }

    @Override
    public void onDataChanged (AsyncDataChangeEvent<InstanceIdentifier<?>,
            DataObject> changes) {

        LOG.debug("onDataChanged: {}", changes);
        for( Map.Entry<InstanceIdentifier<?>, DataObject> created : changes.getCreatedData().entrySet()) {
            InstanceIdentifier<?> iID = created.getKey();
            String openflowId = iID.firstKeyOf(Node.class, NodeKey.class).getId().getValue();
            LOG.debug(">>>>> created iiD: {} - first: {} - NodeKey: {}",
                    iID,
                    iID.firstIdentifierOf(Node.class),
                    openflowId);

            PipelineOrchestrator pipelineOrchestrator =
                    (PipelineOrchestrator) ServiceHelper.getGlobalInstance(PipelineOrchestrator.class, this);
            pipelineOrchestrator.enqueue(openflowId);

            notifyNodeUpdated(NodeUtils.getOpenFlowNode(openflowId));
        }

        //TODO: how to get the removed node id
        Map<InstanceIdentifier<?>, DataObject> originalDataObject = changes.getOriginalData();
        Set<InstanceIdentifier<?>> iID = changes.getRemovedPaths();
        for (InstanceIdentifier instanceIdentifier : iID) {
            notifyNodeUpdated(NodeUtils.getOpenFlowNode(null));
        }
    }

    public void notifyNodeUpdated (Node openFlowNode) {
        LOG.debug("notifyNodeUpdated: Node {} update from Controller's inventory Service",
                openFlowNode.getId().getValue());

        // Add the Node Type check back once the Consistency issue is resolved between MD-SAL and AD-SAL
        if (!nodeCache.contains(openFlowNode)) {
            nodeCache.add(openFlowNode);
            NetworkingProviderManager networkingProviderManager =
                    (NetworkingProviderManager) ServiceHelper.getGlobalInstance(NetworkingProviderManager.class,
                            this);
            networkingProviderManager.getProvider(openFlowNode).initializeOFFlowRules(openFlowNode);
        }
    }

    public void notifyNodeRemoved (Node openFlowNode) {
        LOG.debug("notifyNodeRemoved: Node {} update from Controller's inventory Service",
                openFlowNode.getId().getValue());

        nodeCache.remove(null);//openFlowNode);
    }
}
