package com.gentics.mesh.core.node;

import static com.gentics.mesh.assertj.MeshAssertions.assertThat;
import static com.gentics.mesh.core.data.relationship.GraphPermission.CREATE_PERM;
import static com.gentics.mesh.core.data.relationship.GraphPermission.DELETE_PERM;
import static com.gentics.mesh.core.data.relationship.GraphPermission.READ_PERM;
import static com.gentics.mesh.core.data.relationship.GraphPermission.UPDATE_PERM;
import static com.gentics.mesh.demo.TestDataProvider.PROJECT_NAME;
import static com.gentics.mesh.util.MeshAssert.assertElement;
import static com.gentics.mesh.util.MeshAssert.assertSuccess;
import static com.gentics.mesh.util.MeshAssert.failingLatch;
import static com.gentics.mesh.util.MeshAssert.latchFor;
import static io.netty.handler.codec.http.HttpResponseStatus.BAD_REQUEST;
import static io.netty.handler.codec.http.HttpResponseStatus.CONFLICT;
import static io.netty.handler.codec.http.HttpResponseStatus.FORBIDDEN;
import static io.netty.handler.codec.http.HttpResponseStatus.METHOD_NOT_ALLOWED;
import static io.netty.handler.codec.http.HttpResponseStatus.NOT_FOUND;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.stream.Collectors;

import org.junit.Ignore;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.gentics.mesh.core.AbstractSpringVerticle;
import com.gentics.mesh.core.data.Language;
import com.gentics.mesh.core.data.NodeGraphFieldContainer;
import com.gentics.mesh.core.data.node.Node;
import com.gentics.mesh.core.data.schema.SchemaContainer;
import com.gentics.mesh.core.data.schema.SchemaContainerVersion;
import com.gentics.mesh.core.data.search.SearchQueue;
import com.gentics.mesh.core.data.search.SearchQueueBatch;
import com.gentics.mesh.core.data.service.ServerSchemaStorage;
import com.gentics.mesh.core.rest.common.GenericMessageResponse;
import com.gentics.mesh.core.rest.error.HttpStatusCodeErrorException;
import com.gentics.mesh.core.rest.node.NodeCreateRequest;
import com.gentics.mesh.core.rest.node.NodeListResponse;
import com.gentics.mesh.core.rest.node.NodeResponse;
import com.gentics.mesh.core.rest.node.NodeUpdateRequest;
import com.gentics.mesh.core.rest.node.field.StringField;
import com.gentics.mesh.core.rest.schema.Schema;
import com.gentics.mesh.core.rest.schema.SchemaReference;
import com.gentics.mesh.core.verticle.node.NodeVerticle;
import com.gentics.mesh.query.impl.NodeRequestParameter;
import com.gentics.mesh.query.impl.NodeRequestParameter.LinkType;
import com.gentics.mesh.query.impl.PagingParameter;
import com.gentics.mesh.query.impl.RolePermissionParameter;
import com.gentics.mesh.test.AbstractBasicCrudVerticleTest;
import com.gentics.mesh.util.FieldUtil;

import io.vertx.core.Future;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

public class NodeVerticleTest extends AbstractBasicCrudVerticleTest {

	private static final Logger log = LoggerFactory.getLogger(NodeVerticleTest.class);

	@Autowired
	private NodeVerticle verticle;

	@Override
	public List<AbstractSpringVerticle> getAdditionalVertices() {
		List<AbstractSpringVerticle> list = new ArrayList<>();
		list.add(verticle);
		return list;
	}

	// Create tests

	@Test
	public void testCreateNodeWithNoLanguageCode() {
		NodeCreateRequest request = new NodeCreateRequest();
		SchemaReference schemaReference = new SchemaReference();
		schemaReference.setName("content");
		schemaReference.setUuid(schemaContainer("content").getUuid());
		// No language code set
		request.getFields().put("name", FieldUtil.createStringField("some name"));
		request.getFields().put("filename", FieldUtil.createStringField("new-page.html"));
		request.getFields().put("content", FieldUtil.createStringField("Blessed mealtime again!"));
		request.setSchema(schemaReference);
		request.setParentNodeUuid(folder("news").getUuid());

		assertThat(searchProvider).recordedStoreEvents(0);
		Future<NodeResponse> future = getClient().createNode(PROJECT_NAME, request);
		latchFor(future);
		expectException(future, BAD_REQUEST, "node_no_languagecode_specified");
		assertThat(searchProvider).recordedStoreEvents(0);
	}

	@Test
	public void testCreateNodeWithBogusLanguageCode() throws HttpStatusCodeErrorException, Exception {
		NodeCreateRequest request = new NodeCreateRequest();
		SchemaReference schemaReference = new SchemaReference();
		schemaReference.setName("content");
		schemaReference.setUuid(schemaContainer("content").getUuid());
		schemaReference.setVersion(1);
		request.setLanguage("BOGUS");
		request.getFields().put("name", FieldUtil.createStringField("some name"));
		request.getFields().put("filename", FieldUtil.createStringField("new-page.html"));
		request.getFields().put("content", FieldUtil.createStringField("Blessed mealtime again!"));
		request.setSchema(schemaReference);
		request.setParentNodeUuid(folder("news").getUuid());

		assertThat(searchProvider).recordedStoreEvents(0);
		Future<NodeResponse> future = getClient().createNode(PROJECT_NAME, request);
		latchFor(future);
		expectException(future, BAD_REQUEST, "language_not_found", "BOGUS");
		assertThat(searchProvider).recordedStoreEvents(0);
	}

	@Test
	public void testCreateNodeInBaseNode() {
		NodeCreateRequest request = new NodeCreateRequest();
		request.setSchema(new SchemaReference().setVersion(1).setName("content").setUuid(schemaContainer("content").getUuid()));
		request.setLanguage("en");
		request.getFields().put("title", FieldUtil.createStringField("some title"));
		request.getFields().put("name", FieldUtil.createStringField("some name"));
		request.getFields().put("filename", FieldUtil.createStringField("new-page.html"));
		request.getFields().put("content", FieldUtil.createStringField("Blessed mealtime again!"));
		request.setParentNodeUuid(project().getBaseNode().getUuid());

		assertThat(searchProvider).recordedStoreEvents(0);
		Future<NodeResponse> future = getClient().createNode(PROJECT_NAME, request);
		latchFor(future);
		assertSuccess(future);
		NodeResponse restNode = future.result();
		test.assertMeshNode(request, restNode);
		assertThat(searchProvider).recordedStoreEvents(1);
	}

	@Test
	public void testCreateFolder() {
		Node parentNode = folder("news");
		String uuid = parentNode.getUuid();
		assertNotNull(parentNode);
		assertNotNull(parentNode.getUuid());

		NodeCreateRequest request = new NodeCreateRequest();
		request.setSchema(new SchemaReference().setName("folder").setVersion(1).setUuid(schemaContainer("folder").getUuid()));
		request.setLanguage("en");
		request.getFields().put("name", FieldUtil.createStringField("some name"));
		request.setPublished(true);
		request.setParentNodeUuid(uuid);

		assertThat(searchProvider).recordedStoreEvents(0);
		Future<NodeResponse> future = getClient().createNode(PROJECT_NAME, request);
		latchFor(future);
		assertSuccess(future);
		NodeResponse restNode = future.result();
		test.assertMeshNode(request, restNode);
		assertThat(searchProvider).recordedStoreEvents(1);
	}

	@Test
	@Ignore
	public void testCreateMultiple() {
		Node parentNode = folder("news");
		String uuid = parentNode.getUuid();
		assertNotNull(parentNode);
		assertNotNull(parentNode.getUuid());

		long start = System.currentTimeMillis();
		for (int i = 1; i < 100; i++) {
			searchProvider.reset();
			NodeCreateRequest request = new NodeCreateRequest();
			request.setSchema(new SchemaReference().setName("content").setUuid(schemaContainer("content").getUuid()));
			request.setLanguage("en");
			request.getFields().put("title", FieldUtil.createStringField("some title " + i));
			request.getFields().put("name", FieldUtil.createStringField("some name " + i));
			request.getFields().put("filename", FieldUtil.createStringField("new-page_" + i + ".html"));
			request.getFields().put("content", FieldUtil.createStringField("Blessed mealtime again!"));
			request.setPublished(true);
			request.setParentNodeUuid(uuid);

			Future<NodeResponse> future = getClient().createNode(PROJECT_NAME, request);
			latchFor(future);
			assertSuccess(future);
			long duration = System.currentTimeMillis() - start;
			System.out.println("Duration:" + i + " " + (duration / i));
		}

	}

	@Test
	@Override
	public void testCreate() throws Exception {
		Node parentNode = folder("news");
		String uuid = parentNode.getUuid();
		assertNotNull(parentNode);
		assertNotNull(parentNode.getUuid());

		NodeCreateRequest request = new NodeCreateRequest();
		request.setSchema(new SchemaReference().setName("content").setUuid(schemaContainer("content").getUuid()));
		request.setLanguage("en");
		request.getFields().put("title", FieldUtil.createStringField("some title"));
		request.getFields().put("name", FieldUtil.createStringField("some name"));
		request.getFields().put("filename", FieldUtil.createStringField("new-page.html"));
		request.getFields().put("content", FieldUtil.createStringField("Blessed mealtime again!"));
		request.setPublished(true);
		request.setParentNodeUuid(uuid);

		assertThat(searchProvider).recordedStoreEvents(0);
		Future<NodeResponse> future = getClient().createNode(PROJECT_NAME, request);
		latchFor(future);
		assertSuccess(future);
		NodeResponse restNode = future.result();
		test.assertMeshNode(request, restNode);
		assertThat(searchProvider).recordedStoreEvents(1);

		// We created the node. The searchqueue batch should have been processed
		assertThat(meshRoot().getSearchQueue()).hasEntries(0);
		// SearchQueueBatch batch = searchQueue.take();
		// assertEquals(1, batch.getEntries().size());
		// SearchQueueEntry entry = batch.getEntries().get(0);
		// assertEquals(restNode.getUuid(), entry.getElementUuid());
		// assertEquals(Node.TYPE, entry.getElementType());
		// assertEquals(SearchQueueEntryAction.CREATE_ACTION, entry.getElementAction());
	}

	@Test
	@Ignore
	public void testCreateNodeAndCheckReadOnlyRole() {
		fail("Not yet implemented");
	}

	@Test
	@Override
	public void testCreateReadDelete() throws Exception {
		NodeResponse restNode2;

		NodeCreateRequest request = new NodeCreateRequest();
		SchemaReference schemaReference = new SchemaReference();
		schemaReference.setName("content");
		schemaReference.setUuid(schemaContainer("content").getUuid());
		request.setSchema(schemaReference);

		request.setLanguage("de");
		request.getFields().put("name", FieldUtil.createStringField("some name"));
		request.getFields().put("title", FieldUtil.createStringField("Title"));
		request.getFields().put("filename", FieldUtil.createStringField("new-page.html"));
		request.getFields().put("content", FieldUtil.createStringField("Blessed mealtime again!"));
		request.setParentNodeUuid(folder("news").getUuid());

		NodeRequestParameter parameters = new NodeRequestParameter();
		parameters.setLanguages("de");

		assertThat(searchProvider).recordedStoreEvents(0);
		Future<NodeResponse> future = getClient().createNode(PROJECT_NAME, request, parameters);
		latchFor(future);
		assertSuccess(future);
		assertThat(searchProvider).recordedStoreEvents(1);
		NodeResponse restNode = future.result();
		test.assertMeshNode(request, restNode);

		Node node = meshRoot().getNodeRoot().findByUuid(restNode.getUuid()).toBlocking().single();
		assertNotNull(node);
		test.assertMeshNode(request, node);

		// Load the node again
		Future<NodeResponse> future2 = getClient().findNodeByUuid(PROJECT_NAME, restNode.getUuid(), parameters);
		latchFor(future2);
		assertSuccess(future2);
		restNode2 = future2.result();

		// Delete the node
		Future<GenericMessageResponse> deleteFut = getClient().deleteNode(PROJECT_NAME, restNode2.getUuid());
		latchFor(deleteFut);
		assertSuccess(deleteFut);
		expectResponseMessage(deleteFut, "node_deleted", restNode2.getUuid());

		meshRoot().getNodeRoot().reload();
		Node deletedNode = meshRoot().getNodeRoot().findByUuid(restNode2.getUuid()).toBlocking().single();
		assertNull("The node should have been deleted.", deletedNode);
	}

	@Test
	public void testCreateNodeWithMissingParentNodeUuid() throws Exception {

		NodeCreateRequest request = new NodeCreateRequest();
		SchemaReference schemaReference = new SchemaReference();
		schemaReference.setName("node");
		request.setSchema(schemaReference);
		request.getFields().put("name", FieldUtil.createStringField("some name"));
		request.getFields().put("filename", FieldUtil.createStringField("new-page.html"));
		request.getFields().put("content", FieldUtil.createStringField("Blessed mealtime again!"));
		request.setSchema(new SchemaReference().setName("content").setUuid(schemaContainer("content").getUuid()));

		Future<NodeResponse> future = getClient().createNode(PROJECT_NAME, request);
		latchFor(future);
		expectException(future, BAD_REQUEST, "node_missing_parentnode_field");
	}

	@Test
	public void testCreateNodeWithMissingSchemaPermission() {
		Node node = folder("news");
		String uuid = node.getUuid();
		role().revokePermissions(schemaContainer("content"), READ_PERM);

		NodeCreateRequest request = new NodeCreateRequest();
		SchemaReference schemaReference = new SchemaReference().setName("content").setUuid(schemaContainer("content").getUuid());
		request.setSchema(schemaReference);
		request.getFields().put("name", FieldUtil.createStringField("some name"));
		request.getFields().put("filename", FieldUtil.createStringField("new-page.html"));
		request.getFields().put("content", FieldUtil.createStringField("Blessed mealtime again!"));
		request.setSchema(new SchemaReference().setName("content").setUuid(schemaContainer("content").getUuid()));
		request.setLanguage("en");
		request.setParentNodeUuid(uuid);

		Future<NodeResponse> future = getClient().createNode(PROJECT_NAME, request);
		latchFor(future);
		expectException(future, FORBIDDEN, "error_missing_perm", schemaContainer("content").getUuid());
	}

	@Test
	public void testCreateNodeWithMissingPermission() throws Exception {
		// Revoke create perm
		Node node = folder("news");
		String uuid = node.getUuid();
		role().revokePermissions(node, CREATE_PERM);

		NodeCreateRequest request = new NodeCreateRequest();
		SchemaReference schemaReference = new SchemaReference().setName("content").setUuid(schemaContainer("content").getUuid());
		request.setSchema(schemaReference);
		request.getFields().put("name", FieldUtil.createStringField("some name"));
		request.getFields().put("filename", FieldUtil.createStringField("new-page.html"));
		request.getFields().put("content", FieldUtil.createStringField("Blessed mealtime again!"));
		request.setSchema(new SchemaReference().setName("content").setUuid(schemaContainer("content").getUuid()));
		request.setLanguage("en");
		request.setParentNodeUuid(uuid);

		assertThat(searchProvider).recordedStoreEvents(0);
		Future<NodeResponse> future = getClient().createNode(PROJECT_NAME, request);
		latchFor(future);
		expectException(future, FORBIDDEN, "error_missing_perm", uuid);
		assertThat(searchProvider).recordedStoreEvents(0);
	}

	// Read tests

	/**
	 * Test default paging parameters
	 * 
	 * @throws Exception
	 */
	@Test
	public void testReadNodesDefaultPaging() throws Exception {
		Future<NodeListResponse> future = getClient().findNodes(PROJECT_NAME);
		latchFor(future);
		assertSuccess(future);

		NodeListResponse restResponse = future.result();
		assertNotNull(restResponse);
		assertEquals(25, restResponse.getMetainfo().getPerPage());
		assertEquals(1, restResponse.getMetainfo().getCurrentPage());
		assertEquals(getNodeCount(), restResponse.getData().size());
	}

	@Test
	public void testReadMultipleAndAssertOrder() {
		Node parentNode = folder("2015");
		int nNodes = 20;
		for (int i = 0; i < nNodes; i++) {
			Node node = parentNode.create(user(), schemaContainer("content").getLatestVersion(), project());
			assertNotNull(node);
			role().grantPermissions(node, READ_PERM);
		}

		String firstUuid = null;
		for (int i = 0; i < 10; i++) {
			Future<NodeListResponse> future = getClient().findNodes(PROJECT_NAME, new PagingParameter(1, 100));
			latchFor(future);
			assertSuccess(future);
			if (firstUuid == null) {
				firstUuid = future.result().getData().get(0).getUuid();
			}
			assertEquals("The first element in the page should not change but it changed in run {" + i + "}", firstUuid,
					future.result().getData().get(0).getUuid());
		}

	}

	@Test
	@Override
	public void testReadMultiple() throws Exception {

		Node parentNode = folder("2015");
		// Don't grant permissions to the no perm node. We want to make sure that this one will not be listed.
		Node noPermNode = parentNode.create(user(), schemaContainer("content").getLatestVersion(), project());
		String noPermNodeUUID = noPermNode.getUuid();

		int nNodes = 20;
		for (int i = 0; i < nNodes; i++) {
			Node node = parentNode.create(user(), schemaContainer("content").getLatestVersion(), project());
			assertNotNull(node);
			role().grantPermissions(node, READ_PERM);
		}

		assertNotNull(noPermNode.getUuid());
		int perPage = 11;
		Future<NodeListResponse> future = getClient().findNodes(PROJECT_NAME, new PagingParameter(3, perPage));
		latchFor(future);
		assertSuccess(future);
		NodeListResponse restResponse = future.result();
		assertEquals(perPage, restResponse.getData().size());

		// Extra Nodes + permitted nodes
		int totalNodes = getNodeCount() + nNodes;
		int totalPages = (int) Math.ceil(totalNodes / (double) perPage);
		assertEquals("The response did not contain the correct amount of items", perPage, restResponse.getData().size());
		assertEquals(3, restResponse.getMetainfo().getCurrentPage());
		assertEquals(totalNodes, restResponse.getMetainfo().getTotalCount());
		assertEquals(totalPages, restResponse.getMetainfo().getPageCount());
		assertEquals(perPage, restResponse.getMetainfo().getPerPage());

		List<NodeResponse> allNodes = new ArrayList<>();
		for (int page = 1; page <= totalPages; page++) {
			Future<NodeListResponse> pageFuture = getClient().findNodes(PROJECT_NAME, new PagingParameter(page, perPage));
			latchFor(pageFuture);
			assertSuccess(pageFuture);
			restResponse = pageFuture.result();
			allNodes.addAll(restResponse.getData());
		}
		assertEquals("Somehow not all nodes were loaded when loading all pages.", totalNodes, allNodes.size());

		// Verify that the no_perm_node is not part of the response
		List<NodeResponse> filteredUserList = allNodes.parallelStream().filter(restNode -> restNode.getUuid().equals(noPermNodeUUID))
				.collect(Collectors.toList());
		assertTrue("The no perm node should not be part of the list since no permissions were added.", filteredUserList.size() == 0);

		Future<NodeListResponse> pageFuture = getClient().findNodes(PROJECT_NAME, new PagingParameter(-1, 25));
		latchFor(pageFuture);
		expectException(pageFuture, BAD_REQUEST, "error_page_parameter_must_be_positive", "-1");

		pageFuture = getClient().findNodes(PROJECT_NAME, new PagingParameter(0, 25));
		latchFor(pageFuture);
		expectException(pageFuture, BAD_REQUEST, "error_page_parameter_must_be_positive", "0");

		pageFuture = getClient().findNodes(PROJECT_NAME, new PagingParameter(1, -1));
		latchFor(pageFuture);
		expectException(pageFuture, BAD_REQUEST, "error_pagesize_parameter", "-1");

		pageFuture = getClient().findNodes(PROJECT_NAME, new PagingParameter(4242, 25));
		latchFor(pageFuture);
		assertSuccess(pageFuture);
		NodeListResponse list = pageFuture.result();
		assertEquals(4242, list.getMetainfo().getCurrentPage());
		assertEquals(0, list.getData().size());
		assertEquals(25, list.getMetainfo().getPerPage());
		assertEquals(2, list.getMetainfo().getPageCount());
		assertEquals(getNodeCount() + nNodes, list.getMetainfo().getTotalCount());

	}

	@Test
	public void testReadMultipleOnlyMetadata() {
		Future<NodeListResponse> pageFuture = getClient().findNodes(PROJECT_NAME, new PagingParameter(1, 0));
		latchFor(pageFuture);
		assertSuccess(pageFuture);
		assertEquals(0, pageFuture.result().getData().size());
	}

	@Test
	public void testReadNodesWithoutPermissions() throws Exception {

		// TODO add node that has no perms and check the response
		Future<NodeListResponse> future = getClient().findNodes(PROJECT_NAME, new PagingParameter(1, 10));
		latchFor(future);
		assertSuccess(future);
		NodeListResponse restResponse = future.result();

		int nElements = restResponse.getData().size();
		assertEquals("The amount of elements in the list did not match the expected count", 10, nElements);
		assertEquals(1, restResponse.getMetainfo().getCurrentPage());
		assertEquals(2, restResponse.getMetainfo().getPageCount());
		assertEquals(10, restResponse.getMetainfo().getPerPage());
		assertEquals(getNodeCount(), restResponse.getMetainfo().getTotalCount());
	}

	@Test
	@Ignore("Disabled until custom 404 handler has been added")
	public void testReadNodeWithBogusProject() {
		Future<NodeResponse> future = getClient().findNodeByUuid("BOGUS", "someUuuid");
		latchFor(future);
		expectException(future, BAD_REQUEST, "project_not_found", "BOGUS");
	}

	@Test
	@Ignore("Disabled since test is unstable - CL-246")
	public void testCreateUpdateReadDeleteMultithreaded() throws Exception {

		int nJobs = 200;
		CountDownLatch latch = new CountDownLatch(nJobs);

		Node parentNode = folder("news");
		String uuid = parentNode.getUuid();

		long nNodesFound = meshRoot().getNodeRoot().findAll().size();

		NodeCreateRequest createRequest = new NodeCreateRequest();
		createRequest.setSchema(new SchemaReference().setName("content").setUuid(schemaContainer("content").getUuid()));
		createRequest.setLanguage("en");
		createRequest.getFields().put("title", FieldUtil.createStringField("some title"));
		createRequest.getFields().put("name", FieldUtil.createStringField("some name"));
		createRequest.getFields().put("filename", FieldUtil.createStringField("new-page.html"));
		createRequest.getFields().put("content", FieldUtil.createStringField("Blessed mealtime again!"));
		createRequest.setPublished(true);
		createRequest.setParentNodeUuid(uuid);

		NodeUpdateRequest updateRequest = new NodeUpdateRequest();
		SchemaReference schemaReference = new SchemaReference();
		schemaReference.setName("folder");
		schemaReference.setUuid(schemaContainer("folder").getUuid());
		updateRequest.setSchema(schemaReference);
		updateRequest.setLanguage("en");
		updateRequest.setPublished(true);
		updateRequest.getFields().put("name", FieldUtil.createStringField("UPDATED"));

		// Create various nodes and update them directly after creation. Ensure that update was successful.
		for (int i = 0; i < nJobs; i++) {
			log.info("Invoking createNode REST call for job {" + i + "}");
			Future<NodeResponse> createFuture = getClient().createNode(PROJECT_NAME, createRequest);

			createFuture.setHandler(rh -> {
				if (rh.failed()) {
					fail(rh.cause().getMessage());
				} else {
					log.info("Created {" + rh.result().getUuid() + "}");
					NodeResponse response = rh.result();
					Future<NodeResponse> updateFuture = getClient().updateNode(PROJECT_NAME, response.getUuid(), updateRequest);
					updateFuture.setHandler(uh -> {
						if (uh.failed()) {
							fail(uh.cause().getMessage());
						} else {
							log.info("Updated {" + uh.result().getUuid() + "}");
							Future<NodeResponse> readFuture = getClient().findNodeByUuid(PROJECT_NAME, uh.result().getUuid());
							readFuture.setHandler(rf -> {
								if (rh.failed()) {
									fail(rh.cause().getMessage());
								} else {
									log.info("Read {" + rf.result().getUuid() + "}");
									Future<GenericMessageResponse> deleteFuture = getClient().deleteNode(PROJECT_NAME, rf.result().getUuid());
									deleteFuture.setHandler(df -> {
										if (df.failed()) {
											fail(df.cause().getMessage());
										} else {
											log.info("Deleted {" + rf.result().getUuid() + "} " + df.result().getMessage());
											latch.countDown();
										}
									});
								}

							});

						}
					});
				}
			});
			Thread.sleep(250);
			log.info("Invoked call create requests.");
		}

		failingLatch(latch);

		long nNodesFoundAfterRest = meshRoot().getNodeRoot().findAll().size();
		assertEquals("All created nodes should have been created.", nNodesFound, nNodesFoundAfterRest);
		// for (Future<NodeResponse> future : set) {
		// latchFor(future);
		// assertSuccess(future);
		// }
	}

	@Test
	@Override
	@Ignore("Disabled since test is unstable - CL-246")
	public void testCreateMultithreaded() throws InterruptedException {
		Node parentNode = folder("news");
		String uuid = parentNode.getUuid();
		assertNotNull(parentNode);
		assertNotNull(parentNode.getUuid());

		NodeCreateRequest request = new NodeCreateRequest();
		request.setSchema(new SchemaReference().setName("content").setUuid(schemaContainer("content").getUuid()));
		request.setLanguage("en");
		request.getFields().put("title", FieldUtil.createStringField("some title"));
		request.getFields().put("name", FieldUtil.createStringField("some name"));
		request.getFields().put("filename", FieldUtil.createStringField("new-page.html"));
		request.getFields().put("content", FieldUtil.createStringField("Blessed mealtime again!"));
		request.setPublished(true);
		request.setParentNodeUuid(uuid);

		int nJobs = 50;
		// CyclicBarrier barrier = new CyclicBarrier(nJobs);
		// Trx.enableDebug();
		// Trx.setBarrier(barrier);
		Set<Future<NodeResponse>> set = new HashSet<>();
		for (int i = 0; i < nJobs; i++) {
			log.debug("Invoking createNode REST call");
			set.add(getClient().createNode(PROJECT_NAME, request));
		}

		// Check each call response
		Set<String> uuids = new HashSet<>();
		for (Future<NodeResponse> future : set) {
			latchFor(future);
			assertSuccess(future);
			String currentUuid = future.result().getUuid();
			assertFalse("The rest api returned a node response with a uuid that was returned before. Each create request must always be atomic.",
					uuids.contains(currentUuid));
			uuids.add(currentUuid);
		}
		// Trx.disableDebug();
		// assertFalse("The barrier should not break. Somehow not all threads reached the barrier point.", barrier.isBroken());
	}

	@Test
	@Override
	@Ignore("Disabled since test is unstable - CL-246")
	public void testUpdateMultithreaded() throws InterruptedException {

		final String newName = "english renamed name";
		Node node = folder("2015");
		String uuid = node.getUuid();
		assertEquals("2015", node.getGraphFieldContainer(english()).getString("name").getString());

		NodeUpdateRequest request = new NodeUpdateRequest();
		SchemaReference schemaReference = new SchemaReference();
		schemaReference.setName("folder");
		schemaReference.setUuid(schemaContainer("folder").getUuid());
		request.setSchema(schemaReference);
		request.setLanguage("en");
		request.setPublished(true);
		request.getFields().put("name", FieldUtil.createStringField(newName));

		NodeRequestParameter parameters = new NodeRequestParameter();
		parameters.setLanguages("en", "de");

		int nJobs = 115;
		// CyclicBarrier barrier = new CyclicBarrier(nJobs);
		// Trx.enableDebug();
		// Trx.setBarrier(barrier);
		Set<Future<NodeResponse>> set = new HashSet<>();
		for (int i = 0; i < nJobs; i++) {
			log.debug("Invoking updateNode REST call");
			set.add(getClient().updateNode(PROJECT_NAME, uuid, request, parameters));
		}

		for (Future<NodeResponse> future : set) {
			latchFor(future);
			assertSuccess(future);
		}
		// Trx.disableDebug();
		// assertFalse("The barrier should not break. Somehow not all threads reached the barrier point.", barrier.isBroken());

	}

	@Test
	@Override
	public void testDeleteByUUIDMultithreaded() {

		int nJobs = 3;
		String uuid = folder("2015").getUuid();
		// CyclicBarrier barrier = new CyclicBarrier(nJobs);
		// Trx.enableDebug();
		// Trx.setBarrier(barrier);
		Set<Future<GenericMessageResponse>> set = new HashSet<>();
		for (int i = 0; i < nJobs; i++) {
			log.debug("Invoking deleteNode REST call");
			set.add(getClient().deleteNode(PROJECT_NAME, uuid));
		}

		validateDeletion(set, null);

	}

	@Test
	@Override
	public void testReadByUuidMultithreaded() throws InterruptedException {
		int nJobs = 50;
		Set<Future<NodeResponse>> set = new HashSet<>();
		for (int i = 0; i < nJobs; i++) {
			log.debug("Invoking findNodeByUuid REST call");
			set.add(getClient().findNodeByUuid(PROJECT_NAME, folder("2015").getUuid()));
		}
		for (Future<NodeResponse> future : set) {
			latchFor(future);
			assertSuccess(future);
		}
	}

	@Test
	@Override
	public void testReadByUuidMultithreadedNonBlocking() throws InterruptedException {
		int nJobs = 200;
		Set<Future<NodeResponse>> set = new HashSet<>();
		for (int i = 0; i < nJobs; i++) {
			log.debug("Invoking findNodeByUuid REST call");
			set.add(getClient().findNodeByUuid(PROJECT_NAME, folder("2015").getUuid()));
		}
		for (Future<NodeResponse> future : set) {
			latchFor(future);
			assertSuccess(future);
		}
		// Trx.disableDebug();
	}

	@Test
	public void testReadByUuidWithRolePerms() {

		Node node = folder("2015");
		String uuid = node.getUuid();

		Future<NodeResponse> future = getClient().findNodeByUuid(PROJECT_NAME, uuid, new RolePermissionParameter().setRoleUuid(role().getUuid()));
		latchFor(future);
		assertSuccess(future);
		assertNotNull(future.result().getRolePerms());
		assertEquals(4, future.result().getRolePerms().length);
	}

	@Test
	public void testReadByUUID() throws Exception {
		Node node = folder("2015");
		String uuid = node.getUuid();
		assertNotNull(node);
		assertNotNull(node.getUuid());

		Future<NodeResponse> future = getClient().findNodeByUuid(PROJECT_NAME, uuid);
		latchFor(future);
		assertSuccess(future);
		test.assertMeshNode(folder("2015"), future.result());
		NodeResponse response = future.result();
		//		assertEquals("name", response.getDisplayField());
		assertNotNull(response.getParentNode());
		assertEquals(folder("2015").getParentNode().getUuid(), response.getParentNode().getUuid());
		assertEquals("News", response.getParentNode().getDisplayName());
		assertEquals("en", response.getLanguage());
	}

	/**
	 * Test reading a node with link resolving enabled. Ensure that the schema segment field of the node is not set.
	 */
	@Test
	public void testReadByUUIDWithLinkPathsAndNoSegmentFieldRef() {
		Node node = folder("news");
		// Update the schema
		Schema schema = node.getSchemaContainer().getLatestVersion().getSchema();
		schema.setSegmentField(null);
		node.getSchemaContainer().getLatestVersion().setSchema(schema);
		ServerSchemaStorage.getInstance().clear();

		Future<NodeResponse> future = getClient().findNodeByUuid(PROJECT_NAME, node.getUuid(),
				new NodeRequestParameter().setResolveLinks(LinkType.FULL));
		latchFor(future);
		assertSuccess(future);
		NodeResponse response = future.result();
		assertEquals("/api/v1/dummy/webroot/error/404", response.getPath());
		assertThat(response.getLanguagePaths()).containsEntry("en", "/api/v1/dummy/webroot/error/404");
		assertThat(response.getLanguagePaths()).containsEntry("de", "/api/v1/dummy/webroot/error/404");
	}

	@Test
	public void testReadByUUIDWithLinkPaths() {
		Node node = folder("news");
		Future<NodeResponse> future = getClient().findNodeByUuid(PROJECT_NAME, node.getUuid(),
				new NodeRequestParameter().setResolveLinks(LinkType.FULL));
		latchFor(future);
		assertSuccess(future);
		NodeResponse response = future.result();
		assertThat(response.getAvailableLanguages()).containsExactly("de", "en");
		assertThat(response.getLanguagePaths()).containsEntry("en", "/api/v1/dummy/webroot/News");
		assertThat(response.getLanguagePaths()).containsEntry("de", "/api/v1/dummy/webroot/Neuigkeiten");
	}

	@Test
	public void testReadBreadcrumbWithLangfallback() {
		Node baseNode = project().getBaseNode();

		// level 0
		NodeCreateRequest request = new NodeCreateRequest();
		request.setSchema(new SchemaReference().setName("folder"));
		request.setLanguage("en");
		request.getFields().put("name", FieldUtil.createStringField("english folder-0"));
		request.setPublished(true);
		request.setParentNodeUuid(baseNode.getUuid());
		Future<NodeResponse> future = getClient().createNode(PROJECT_NAME, request);
		latchFor(future);
		assertSuccess(future);

		// level 1
		request.setParentNodeUuid(future.result().getUuid());
		request.getFields().put("name", FieldUtil.createStringField("english folder-1"));
		future = getClient().createNode(PROJECT_NAME, request);
		latchFor(future);
		assertSuccess(future);

		// level 2
		request.setLanguage("de");
		request.setParentNodeUuid(future.result().getUuid());
		request.getFields().put("name", FieldUtil.createStringField("german folder-2"));
		future = getClient().createNode(PROJECT_NAME, request);
		latchFor(future);
		assertSuccess(future);

		// Load the german folder
		Future<NodeResponse> readFuture = getClient().findNodeByUuid(PROJECT_NAME, future.result().getUuid(),
				new NodeRequestParameter().setResolveLinks(LinkType.FULL).setLanguages("de", "en"));
		latchFor(readFuture);
		assertSuccess(readFuture);
		NodeResponse response = readFuture.result();
		assertEquals("/api/v1/dummy/webroot/english%20folder-0/english%20folder-1/german%20folder-2", response.getPath());
		assertEquals("/api/v1/dummy/webroot/english%20folder-0/english%20folder-1", response.getBreadcrumb().get(0).getPath());
		assertEquals("/api/v1/dummy/webroot/english%20folder-0", response.getBreadcrumb().get(1).getPath());

	}

	@Test
	public void testReadByUUIDBreadcrumb() {
		Node node = content("news_2014");
		Future<NodeResponse> future = getClient().findNodeByUuid(PROJECT_NAME, node.getUuid(),
				new NodeRequestParameter().setResolveLinks(LinkType.FULL));
		latchFor(future);
		assertSuccess(future);
		NodeResponse response = future.result();
		assertTrue(response.getBreadcrumb().get(0).getUuid().equals(folder("2014").getUuid()));
		assertTrue(response.getBreadcrumb().get(0).getDisplayName().equals("2014"));
		assertTrue(response.getBreadcrumb().get(1).getUuid().equals(folder("news").getUuid()));
		assertTrue(response.getBreadcrumb().get(1).getDisplayName().equals("News"));
		assertEquals("/api/v1/dummy/webroot/News/2014", response.getBreadcrumb().get(0).getPath());
		assertEquals("/api/v1/dummy/webroot/News", response.getBreadcrumb().get(1).getPath());
		assertEquals("Only two items should be listed in the breadcrumb", 2, response.getBreadcrumb().size());

		future = getClient().findNodeByUuid(PROJECT_NAME, node.getUuid());
		latchFor(future);
		assertSuccess(future);
		response = future.result();
		assertTrue(response.getBreadcrumb().get(0).getUuid().equals(folder("2014").getUuid()));
		assertTrue(response.getBreadcrumb().get(0).getDisplayName().equals("2014"));
		assertTrue(response.getBreadcrumb().get(1).getUuid().equals(folder("news").getUuid()));
		assertTrue(response.getBreadcrumb().get(1).getDisplayName().equals("News"));
		assertNull("No path should be rendered since by default the linkType is OFF", response.getBreadcrumb().get(0).getPath());
		assertNull("No path should be rendered since by default the linkType is OFF", response.getBreadcrumb().get(1).getPath());
		assertEquals("Only two items should be listed in the breadcrumb", 2, response.getBreadcrumb().size());

	}

	@Test
	public void testReadNodeByUUIDLanguageFallback() {
		Node node = folder("products");
		SearchQueueBatch batch = createBatch();
		node.getGraphFieldContainer(english()).delete(batch);
		String uuid = node.getUuid();

		// Request the node with various language parameter values. Fallback to "de"
		NodeRequestParameter parameters = new NodeRequestParameter();
		parameters.setLanguages("dv,nl,de,en");
		Future<NodeResponse> future = getClient().findNodeByUuid(PROJECT_NAME, uuid, parameters);
		latchFor(future);
		assertSuccess(future);
		NodeResponse restNode = future.result();
		test.assertMeshNode(folder("products"), restNode);

		// Ensure "de" version was returned
		StringField field = restNode.getFields().getStringField("name");
		String nameText = field.getString();
		assertEquals("Produkte", nameText);

	}

	@Test
	public void testReadNodeByUUIDSingleLanguage() throws Exception {
		Node node = folder("products");
		String uuid = node.getUuid();

		NodeRequestParameter parameters = new NodeRequestParameter();
		parameters.setLanguages("de");
		Future<NodeResponse> future = getClient().findNodeByUuid(PROJECT_NAME, uuid, parameters);
		latchFor(future);
		assertSuccess(future);
		NodeResponse restNode = future.result();
		test.assertMeshNode(folder("products"), restNode);

		StringField field = restNode.getFields().getStringField("name");
		String nameText = field.getString();
		assertEquals("Produkte", nameText);
	}

	@Test
	public void testReadNodeByUUIDNoLanguage() throws Exception {
		// Create node with nl language
		Node parentNode = folder("products");
		Language languageNl = meshRoot().getLanguageRoot().findByLanguageTag("nl");
		SchemaContainerVersion version = schemaContainer("content").getLatestVersion();
		Node node = parentNode.create(user(), version, project());
		NodeGraphFieldContainer englishContainer = node.createGraphFieldContainer(languageNl, version);
		englishContainer.createString("name").setString("name");
		englishContainer.createString("title").setString("title");
		englishContainer.createString("displayName").setString("displayName");
		englishContainer.createString("filename").setString("filename.nl.html");
		englishContainer.createHTML("content").setHtml("nl content");
		role().grantPermissions(node, READ_PERM);

		// Request the node in english en
		NodeRequestParameter parameters = new NodeRequestParameter();
		parameters.setLanguages("en");
		Future<NodeResponse> future = getClient().findNodeByUuid(PROJECT_NAME, node.getUuid(), parameters);
		latchFor(future);
		assertSuccess(future);
		assertThat(future.result().getLanguage()).as("Node language").isNull();
		assertThat(future.result().getAvailableLanguages()).as("Available languages").containsOnly("nl");
		assertThat(future.result().getFields()).as("Node Fields").isEmpty();
	}

	@Test
	public void testReadNodeWithBogusLanguageCode() throws Exception {

		Node node = folder("2015");
		String uuid = node.getUuid();
		assertNotNull(node);
		assertNotNull(node.getUuid());

		NodeRequestParameter parameters = new NodeRequestParameter();
		parameters.setLanguages("blabla", "edgsdg");

		assertThat(searchProvider).recordedStoreEvents(0);
		Future<NodeResponse> future = getClient().findNodeByUuid(PROJECT_NAME, uuid, parameters);
		latchFor(future);
		expectException(future, BAD_REQUEST, "error_language_not_found", "blabla");
		assertThat(searchProvider).recordedStoreEvents(0);

	}

	@Test
	@Override
	public void testReadByUUIDWithMissingPermission() throws Exception {
		Node node = folder("2015");
		String uuid = node.getUuid();
		role().revokePermissions(node, READ_PERM);
		Future<NodeResponse> future = getClient().findNodeByUuid(PROJECT_NAME, uuid);
		latchFor(future);
		expectException(future, FORBIDDEN, "error_missing_perm", uuid);
	}

	@Test
	public void testReadNodeByBogusUUID() throws Exception {
		Future<NodeResponse> future = getClient().findNodeByUuid(PROJECT_NAME, "bogusUUID");
		latchFor(future);
		expectException(future, NOT_FOUND, "object_not_found_for_uuid", "bogusUUID");
	}

	@Test
	public void testReadNodeByInvalidUUID() throws Exception {
		String uuid = "dde8ba06bb7211e4897631a9ce2772f5";
		Future<NodeResponse> future = getClient().findNodeByUuid(PROJECT_NAME, uuid);
		latchFor(future);
		expectException(future, NOT_FOUND, "object_not_found_for_uuid", uuid);
	}

	// Update

	@Test
	@Override
	public void testUpdate() throws HttpStatusCodeErrorException, Exception {
		final String newName = "english renamed name";
		Node node = folder("2015");
		String uuid = node.getUuid();
		assertEquals("2015", node.getGraphFieldContainer(english()).getString("name").getString());

		// Prepare the request
		NodeUpdateRequest request = new NodeUpdateRequest();
		SchemaReference schemaReference = new SchemaReference();
		schemaReference.setName("folder");
		schemaReference.setUuid(schemaContainer("folder").getUuid());
		request.setSchema(schemaReference);
		request.setLanguage("en");
		request.setPublished(true);
		request.getFields().put("name", FieldUtil.createStringField(newName));
		NodeRequestParameter parameters = new NodeRequestParameter();
		parameters.setLanguages("en", "de");

		// Update the node
		Future<NodeResponse> future = getClient().updateNode(PROJECT_NAME, uuid, request, parameters);
		latchFor(future);
		assertSuccess(future);

		NodeResponse restNode = future.result();
		assertEquals("en", restNode.getLanguage());
		StringField field = restNode.getFields().getStringField("name");
		assertEquals(newName, field.getString());
		assertNotNull(restNode);
		assertTrue(restNode.isPublished());
		NodeGraphFieldContainer container = node.getGraphFieldContainer(english());
		container.reload();
		assertEquals(newName, container.getString("name").getString());

		assertEquals(1, searchProvider.getStoreEvents().size());

		SearchQueue searchQueue = meshRoot().getSearchQueue();
		assertEquals("We updated the node. The search queue batch should have been processed.", 0, searchQueue.getSize());
		// SearchQueueBatch batch = searchQueue.take();
		// assertEquals(1, batch.getEntries().size());
		// SearchQueueEntry entry = batch.getEntries().get(0);
		//
		// assertEquals(restNode.getUuid(), entry.getElementUuid());
		// assertEquals(Node.TYPE, entry.getElementType());
		// assertEquals(SearchQueueEntryAction.UPDATE_ACTION, entry.getElementAction());
	}

	@Test
	@Override
	public void testUpdateByUUIDWithoutPerm() throws Exception {
		Node node = folder("2015");
		role().revokePermissions(node, UPDATE_PERM);
		String uuid = node.getUuid();
		NodeUpdateRequest request = new NodeUpdateRequest();
		SchemaReference reference = new SchemaReference();
		reference.setName("content");
		request.setSchema(reference);
		request.setLanguage("en");

		Future<NodeResponse> future = getClient().updateNode(PROJECT_NAME, uuid, request);
		latchFor(future);
		expectException(future, FORBIDDEN, "error_missing_perm", uuid);

	}

	@Test
	@Override
	public void testUpdateWithBogusUuid() throws HttpStatusCodeErrorException, Exception {

		NodeUpdateRequest request = new NodeUpdateRequest();
		SchemaReference schemaReference = new SchemaReference();
		schemaReference.setName("folder");
		schemaReference.setUuid(schemaContainer("folder").getUuid());
		request.setSchema(schemaReference);
		request.setLanguage("en");
		request.setPublished(true);

		NodeRequestParameter parameters = new NodeRequestParameter();
		parameters.setLanguages("en", "de");

		Future<NodeResponse> future = getClient().updateNode(PROJECT_NAME, "bogus", request, parameters);
		latchFor(future);
		expectException(future, NOT_FOUND, "object_not_found_for_uuid", "bogus");
	}

	@Test
	public void testUpdateNodeWithExtraField() throws UnknownHostException, InterruptedException {
		Node parentNode = folder("news");
		String uuid = parentNode.getUuid();
		assertNotNull(parentNode);
		assertNotNull(parentNode.getUuid());

		NodeCreateRequest request = new NodeCreateRequest();
		request.setSchema(new SchemaReference().setName("content").setUuid(schemaContainer("content").getUuid()));
		request.setLanguage("en");
		request.getFields().put("title", FieldUtil.createStringField("some title"));
		request.getFields().put("extrafield", FieldUtil.createStringField("some extra field value"));
		request.getFields().put("name", FieldUtil.createStringField("some name"));
		request.getFields().put("filename", FieldUtil.createStringField("new-page.html"));
		request.getFields().put("content", FieldUtil.createStringField("Blessed mealtime again!"));

		request.setParentNodeUuid(uuid);

		Future<NodeResponse> future = getClient().createNode(PROJECT_NAME, request);
		latchFor(future);
		expectException(future, BAD_REQUEST, "node_unhandled_fields", "content", "[extrafield]");
		assertNull(future.result());

	}

	@Test
	public void testCreateNodeWithMissingRequiredField() {
		Node parentNode = folder("news");
		String uuid = parentNode.getUuid();
		assertNotNull(parentNode);
		assertNotNull(parentNode.getUuid());

		NodeCreateRequest request = new NodeCreateRequest();
		request.setSchema(new SchemaReference().setName("content").setUuid(schemaContainer("content").getUuid()));
		request.setLanguage("en");
		// non required title field is missing
		// required name field is missing
		request.getFields().put("filename", FieldUtil.createStringField("new-page.html"));
		request.getFields().put("content", FieldUtil.createStringField("Blessed mealtime again!"));

		request.setParentNodeUuid(uuid);

		Future<NodeResponse> future = getClient().createNode(PROJECT_NAME, request);
		latchFor(future);
		expectException(future, BAD_REQUEST, "node_error_missing_mandatory_field_value", "name", "content");
		assertNull(future.result());

	}

	@Test
	public void testCreateNodeWithMissingField() throws UnknownHostException, InterruptedException {
		Node parentNode = folder("news");
		String uuid = parentNode.getUuid();
		assertNotNull(parentNode);
		assertNotNull(parentNode.getUuid());

		NodeCreateRequest request = new NodeCreateRequest();
		request.setSchema(new SchemaReference().setName("content").setUuid(schemaContainer("content").getUuid()));
		request.setLanguage("en");
		// title field is missing
		request.getFields().put("name", FieldUtil.createStringField("some name"));
		request.getFields().put("filename", FieldUtil.createStringField("new-page.html"));
		request.getFields().put("content", FieldUtil.createStringField("Blessed mealtime again!"));

		request.setParentNodeUuid(uuid);

		Future<NodeResponse> future = getClient().createNode(PROJECT_NAME, request);
		latchFor(future);
		assertSuccess(future);
		assertNotNull(future.result());
	}

	@Test
	public void testUpdateNodeWithExtraField2() throws HttpStatusCodeErrorException, Exception {
		Node node = folder("2015");
		String uuid = node.getUuid();
		NodeUpdateRequest request = new NodeUpdateRequest();
		SchemaReference schemaReference = new SchemaReference();
		schemaReference.setName("content");
		request.setSchema(schemaReference);
		request.setLanguage("en");
		final String newName = "english renamed name";
		final String newDisplayName = "display name changed";

		request.getFields().put("name", FieldUtil.createStringField(newName));
		request.getFields().put("displayName", FieldUtil.createStringField(newDisplayName));

		NodeRequestParameter parameters = new NodeRequestParameter();
		parameters.setLanguages("de", "en");
		Future<NodeResponse> future = getClient().updateNode(PROJECT_NAME, uuid, request, parameters);
		latchFor(future);
		expectException(future, BAD_REQUEST, "node_unhandled_fields", "folder", "[displayName]");

		assertNull(future.result());

		NodeGraphFieldContainer englishContainer = folder("2015").createGraphFieldContainer(english(),
				folder("2015").getSchemaContainer().getLatestVersion());
		assertNotEquals(newName, englishContainer.getString("name").getString());

	}

	// Delete

	@Test
	public void testDeleteBaseNode() throws Exception {
		Node node = project().getBaseNode();
		String uuid = node.getUuid();

		Future<GenericMessageResponse> future = getClient().deleteNode(PROJECT_NAME, uuid);
		latchFor(future);
		expectException(future, METHOD_NOT_ALLOWED, "node_basenode_not_deletable");

		Node foundNode = meshRoot().getNodeRoot().findByUuid(uuid).toBlocking().single();
		assertNotNull("The node should still exist.", foundNode);
	}

	@Test
	@Override
	public void testDeleteByUUID() throws Exception {
		Node node = content("concorde");
		String uuid = node.getUuid();
		Future<GenericMessageResponse> future = getClient().deleteNode(PROJECT_NAME, uuid);
		latchFor(future);
		assertSuccess(future);

		expectResponseMessage(future, "node_deleted", uuid);
		assertElement(meshRoot().getNodeRoot(), uuid, false);
		assertThat(searchProvider).recordedDeleteEvents(2);
		SearchQueue searchQueue = meshRoot().getSearchQueue();
		assertThat(searchQueue).hasEntries(0);
		// SearchQueueBatch batch = searchQueue.take();
		// assertEquals(1, batch.getEntries().size());
		// SearchQueueEntry entry = batch.getEntries().get(0);
		// assertEquals(uuid, entry.getElementUuid());
		// assertEquals(Node.TYPE, entry.getElementType());
		// assertEquals(SearchQueueEntryAction.DELETE_ACTION, entry.getElementAction());
	}

	@Test
	@Override
	public void testDeleteByUUIDWithNoPermission() throws Exception {
		Node node = folder("2015");
		String uuid = node.getUuid();
		role().revokePermissions(node, DELETE_PERM);

		Future<GenericMessageResponse> future = getClient().deleteNode(PROJECT_NAME, uuid);
		latchFor(future);
		expectException(future, FORBIDDEN, "error_missing_perm", uuid);

		assertNotNull(meshRoot().getNodeRoot().findByUuid(uuid).toBlocking().first());
	}

	// Webroot Path Uniqueness Tests

	@Test
	public void testCreateDuplicateWebrootPath() {
		String conflictingName = "filename.html";
		Node parent = folder("2015");
		SchemaContainer contentSchema = schemaContainer("content");

		// create the initial content
		NodeCreateRequest create = new NodeCreateRequest();
		create.setParentNodeUuid(parent.getUuid());
		create.setLanguage("en");
		create.setSchema(new SchemaReference().setName(contentSchema.getName()).setUuid(contentSchema.getUuid()));
		create.getFields().put("title", FieldUtil.createStringField("some title"));
		create.getFields().put("name", FieldUtil.createStringField("some name"));
		create.getFields().put("filename", FieldUtil.createStringField(conflictingName));
		create.getFields().put("content", FieldUtil.createStringField("Blessed mealtime!"));
		Future<NodeResponse> future = getClient().createNode(PROJECT_NAME, create);
		latchFor(future);
		assertSuccess(future);

		// try to create the new content with same filename
		create = new NodeCreateRequest();
		create.setParentNodeUuid(parent.getUuid());
		create.setLanguage("en");
		create.setSchema(new SchemaReference().setName(contentSchema.getName()).setUuid(contentSchema.getUuid()));
		create.getFields().put("title", FieldUtil.createStringField("some other title"));
		create.getFields().put("name", FieldUtil.createStringField("some other name"));
		create.getFields().put("filename", FieldUtil.createStringField(conflictingName));
		create.getFields().put("content", FieldUtil.createStringField("Blessed mealtime again!"));
		future = getClient().createNode(PROJECT_NAME, create);
		latchFor(future);
		expectException(future, CONFLICT, "node_conflicting_segmentfield_update", "filename", conflictingName);
	}

	@Test
	public void testUpdateDuplicateWebrootPath() {
		String conflictingName = "filename.html";
		String nonConflictingName = "otherfilename.html";
		Node parent = folder("2015");
		SchemaContainer contentSchema = schemaContainer("content");

		// create the initial content
		NodeCreateRequest create = new NodeCreateRequest();
		create.setParentNodeUuid(parent.getUuid());
		create.setLanguage("en");
		create.setSchema(new SchemaReference().setName(contentSchema.getName()).setUuid(contentSchema.getUuid()));
		create.getFields().put("title", FieldUtil.createStringField("some title"));
		create.getFields().put("name", FieldUtil.createStringField("some name"));
		create.getFields().put("filename", FieldUtil.createStringField(conflictingName));
		create.getFields().put("content", FieldUtil.createStringField("Blessed mealtime!"));
		Future<NodeResponse> future = getClient().createNode(PROJECT_NAME, create);
		latchFor(future);
		assertSuccess(future);

		// create a new content
		create = new NodeCreateRequest();
		create.setParentNodeUuid(parent.getUuid());
		create.setLanguage("en");
		create.setSchema(new SchemaReference().setName(contentSchema.getName()).setUuid(contentSchema.getUuid()));
		create.getFields().put("title", FieldUtil.createStringField("some title"));
		create.getFields().put("name", FieldUtil.createStringField("some name"));
		create.getFields().put("filename", FieldUtil.createStringField(nonConflictingName));
		create.getFields().put("content", FieldUtil.createStringField("Blessed mealtime!"));
		future = getClient().createNode(PROJECT_NAME, create);
		latchFor(future);
		assertSuccess(future);
		String uuid = future.result().getUuid();

		// try to update with conflict
		NodeUpdateRequest update = new NodeUpdateRequest();
		update.setLanguage("en");
		update.setSchema(new SchemaReference().setName(contentSchema.getName()).setUuid(contentSchema.getUuid()));
		update.getFields().put("filename", FieldUtil.createStringField(conflictingName));
		future = getClient().updateNode(PROJECT_NAME, uuid, update);
		latchFor(future);
		expectException(future, CONFLICT, "node_conflicting_segmentfield_update", "filename", conflictingName);
	}

	@Test
	public void testTranslateDuplicateWebrootPath() {
		String conflictingName = "filename.html";
		Node parent = folder("2015");
		SchemaContainer contentSchema = schemaContainer("content");

		// create the initial content
		NodeCreateRequest create = new NodeCreateRequest();
		create.setParentNodeUuid(parent.getUuid());
		create.setLanguage("en");
		create.setSchema(new SchemaReference().setName(contentSchema.getName()).setUuid(contentSchema.getUuid()));
		create.getFields().put("title", FieldUtil.createStringField("some title"));
		create.getFields().put("name", FieldUtil.createStringField("some name"));
		create.getFields().put("filename", FieldUtil.createStringField(conflictingName));
		create.getFields().put("content", FieldUtil.createStringField("Blessed mealtime!"));
		Future<NodeResponse> future = getClient().createNode(PROJECT_NAME, create);
		latchFor(future);
		assertSuccess(future);
		String uuid = future.result().getUuid();

		// translate the content
		NodeUpdateRequest update = new NodeUpdateRequest();
		update.setLanguage("de");
		update.setSchema(new SchemaReference().setName(contentSchema.getName()).setUuid(contentSchema.getUuid()));
		update.getFields().put("title", FieldUtil.createStringField("Irgendein Titel"));
		update.getFields().put("name", FieldUtil.createStringField("Irgendein Name"));
		update.getFields().put("filename", FieldUtil.createStringField(conflictingName));
		update.getFields().put("content", FieldUtil.createStringField("Gesegnete Mahlzeit!"));
		future = getClient().updateNode(PROJECT_NAME, uuid, update);
		latchFor(future);
		expectException(future, CONFLICT, "node_conflicting_segmentfield_update", "filename", conflictingName);
	}

	@Test
	public void testMoveDuplicateWebrootPath() {
		String conflictingName = "filename.html";
		Node parent = folder("2015");
		Node otherParent = folder("news");
		SchemaContainer contentSchema = schemaContainer("content");

		// create the initial content
		NodeCreateRequest create = new NodeCreateRequest();
		create.setParentNodeUuid(parent.getUuid());
		create.setLanguage("en");
		create.setSchema(new SchemaReference().setName(contentSchema.getName()).setUuid(contentSchema.getUuid()));
		create.getFields().put("title", FieldUtil.createStringField("some title"));
		create.getFields().put("name", FieldUtil.createStringField("some name"));
		create.getFields().put("filename", FieldUtil.createStringField(conflictingName));
		create.getFields().put("content", FieldUtil.createStringField("Blessed mealtime!"));
		Future<NodeResponse> future = getClient().createNode(PROJECT_NAME, create);
		latchFor(future);
		assertSuccess(future);
		String uuid = future.result().getUuid();

		// create a "conflicting" content in another folder
		create = new NodeCreateRequest();
		create.setParentNodeUuid(otherParent.getUuid());
		create.setLanguage("en");
		create.setSchema(new SchemaReference().setName(contentSchema.getName()).setUuid(contentSchema.getUuid()));
		create.getFields().put("title", FieldUtil.createStringField("some other title"));
		create.getFields().put("name", FieldUtil.createStringField("some other name"));
		create.getFields().put("filename", FieldUtil.createStringField(conflictingName));
		create.getFields().put("content", FieldUtil.createStringField("Blessed mealtime again!"));
		future = getClient().createNode(PROJECT_NAME, create);
		latchFor(future);
		assertSuccess(future);

		// try to move the original node
		Future<GenericMessageResponse> moveFuture = getClient().moveNode(PROJECT_NAME, uuid, otherParent.getUuid());
		latchFor(moveFuture);
		expectException(moveFuture, CONFLICT, "node_conflicting_segmentfield_move", "filename", conflictingName);
	}
}