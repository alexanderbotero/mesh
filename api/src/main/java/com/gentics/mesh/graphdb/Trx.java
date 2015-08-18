package com.gentics.mesh.graphdb;

import com.gentics.mesh.graphdb.spi.Database;
import com.syncleus.ferma.DelegatingFramedTransactionalGraph;
import com.syncleus.ferma.FramedTransactionalGraph;
import com.tinkerpop.blueprints.TransactionalGraph;

public class Trx implements AutoCloseable {

	private static ThreadLocal<TransactionalGraph> localGraph = new ThreadLocal<>();
	private TransactionalGraph currentGraph;
	private boolean isSuccess = false;
	private TransactionalGraph oldLocalGraph;

	public Trx(Database database) {
		currentGraph = database.getFramedGraph().newTransaction();
		oldLocalGraph = localGraph.get();
		if (oldLocalGraph == null) {
			localGraph.set(currentGraph);
		} else {
			currentGraph = localGraph.get();
		}
	}

	public void success() {
		isSuccess = true;
	}

	public void failure() {
		isSuccess = false;
	}

	public FramedTransactionalGraph getGraph() {
		return new DelegatingFramedTransactionalGraph<>(currentGraph, true, false);
	}

	@Override
	public void close() {
		if (isSuccess) {
			currentGraph.commit();
		} else {
			currentGraph.rollback();
		}
		setLocalGraph(oldLocalGraph);
	}

	public static void setLocalGraph(TransactionalGraph graph) {
		Trx.localGraph.set(graph);
	}

	public static FramedTransactionalGraph getFramedLocalGraph() {
		return new DelegatingFramedTransactionalGraph<>(getLocalGraph(), true, false);
	}

	public static TransactionalGraph getLocalGraph() {
		return Trx.localGraph.get();
	}
}
