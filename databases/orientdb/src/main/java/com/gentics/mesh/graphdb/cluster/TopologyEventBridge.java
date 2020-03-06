package com.gentics.mesh.graphdb.cluster;

import static com.gentics.mesh.core.rest.MeshEvent.CLUSTER_DATABASE_CHANGE_STATUS;
import static com.gentics.mesh.core.rest.MeshEvent.CLUSTER_NODE_JOINED;
import static com.gentics.mesh.core.rest.MeshEvent.CLUSTER_NODE_JOINING;
import static com.gentics.mesh.core.rest.MeshEvent.CLUSTER_NODE_LEFT;

import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import com.gentics.mesh.cli.BootstrapInitializer;
import com.gentics.mesh.core.verticle.handler.HandlerUtilities;
import com.orientechnologies.orient.server.distributed.ODistributedLifecycleListener;
import com.orientechnologies.orient.server.distributed.ODistributedServerManager.DB_STATUS;

import dagger.Lazy;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

/**
 * Listener for OrientDB cluster specific events. The listener relays the events via messages to the eventbus.
 */
public class TopologyEventBridge implements ODistributedLifecycleListener {

	private static final Logger log = LoggerFactory.getLogger(TopologyEventBridge.class);

	private final Lazy<Vertx> vertx;

	private OrientDBClusterManager manager;

	private final Lazy<BootstrapInitializer> boot;

	private Map<String, DB_STATUS> statusMap = new HashMap<>();

	private CountDownLatch nodeJoinLatch = new CountDownLatch(1);

	private Lazy<HandlerUtilities> handlerUtilities;

	public TopologyEventBridge(Lazy<Vertx> vertx, Lazy<BootstrapInitializer> boot, OrientDBClusterManager manager, Lazy<HandlerUtilities> handlerUtilities) {
		this.vertx = vertx;
		this.boot = boot;
		this.manager = manager;
		this.handlerUtilities = handlerUtilities;
	}

	EventBus getEventBus() {
		return vertx.get().eventBus();
	}

	@Override
	public boolean onNodeJoining(String nodeName) {
		if (log.isDebugEnabled()) {
			log.debug("Node {" + nodeName + "} is joining the cluster.");
		}
		if (isVertxReady()) {
			getEventBus().publish(CLUSTER_NODE_JOINING.address, nodeName);
		}
		return true;
	}

	@Override
	public void onNodeJoined(String iNode) {
		if (log.isDebugEnabled()) {
			log.debug("Node {" + iNode + "} joined the cluster.");
		}
		if (isVertxReady()) {
			getEventBus().publish(CLUSTER_NODE_JOINED.address, iNode);
		}
	}

	@Override
	public void onNodeLeft(String iNode) {
		if (log.isDebugEnabled()) {
			log.debug("Node {" + iNode + "} left the cluster");
		}
		statusMap.remove(iNode);
		// db.removeNode(iNode);
		if (isVertxReady()) {
			getEventBus().publish(CLUSTER_NODE_LEFT.address, iNode);
		}
	}

	@Override
	public void onDatabaseChangeStatus(String iNode, String iDatabaseName, DB_STATUS iNewStatus) {
		log.info("Node {" + iNode + "} Database {" + iDatabaseName + "} changed status {" + iNewStatus.name() + "}");
		statusMap.put(iNode, iNewStatus);

		if (isVertxReady()) {
			JsonObject statusInfo = new JsonObject();
			statusInfo.put("node", iNode);
			statusInfo.put("database", iDatabaseName);
			statusInfo.put("status", iNewStatus.name());
			getEventBus().publish(CLUSTER_DATABASE_CHANGE_STATUS.address, statusInfo);
		}
		if ("storage".equals(iDatabaseName) && iNewStatus == DB_STATUS.ONLINE && iNode.equals(manager.getNodeName())) {
			nodeJoinLatch.countDown();
		}
	}

	/**
	 * Block until another node joined the cluster and the database is ready to use.
	 * 
	 * @param timeout
	 *            the maximum time to wait
	 * @param unit
	 *            the time unit of the {@code timeout} argument
	 * @return {@code true} if the count reached zero and {@code false} if the waiting time elapsed before the count reached zero
	 * @throws InterruptedException
	 *             if the current thread is interrupted while waiting
	 */
	public boolean waitForMainGraphDB(int timeout, TimeUnit unit) throws InterruptedException {
		return nodeJoinLatch.await(timeout, unit);
	}

	public boolean isVertxReady() {
		return boot.get().isVertxReady();
	}

	public Map<String, DB_STATUS> getStatusMap() {
		return statusMap;
	}

	/**
	 * Check whether any instance in the cluster is in a blocking state (e.g. BACKUP, SYNCHRONIZING).
	 * 
	 * @return
	 */
	public boolean hasBlockingState() {
		for (Entry<String, DB_STATUS> entry : getStatusMap().entrySet()) {
			DB_STATUS status = entry.getValue();
			switch (status) {
			case BACKUP:
			case SYNCHRONIZING:
				return true;
			default:
				continue;
			}
		}
		return false;
	}

}
