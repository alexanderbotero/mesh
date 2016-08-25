package com.gentics.mesh.search;

import static com.gentics.mesh.util.MeshAssert.assertSuccess;
import static com.gentics.mesh.util.MeshAssert.latchFor;
import static org.junit.Assert.assertEquals;

import java.util.ArrayList;
import java.util.List;

import org.codehaus.jettison.json.JSONException;
import org.junit.Test;

import com.gentics.mesh.core.rest.project.ProjectListResponse;
import com.gentics.mesh.core.rest.project.ProjectResponse;
import com.gentics.mesh.graphdb.NoTx;
import com.gentics.mesh.parameter.impl.PagingParameters;
import com.gentics.mesh.rest.client.MeshResponse;
import com.gentics.mesh.util.MeshAssert;

import io.vertx.core.AbstractVerticle;

public class ProjectSearchVerticleTest extends AbstractSearchVerticleTest implements BasicSearchCrudTestcases {

	@Override
	public List<AbstractVerticle> getAdditionalVertices() {
		List<AbstractVerticle> list = new ArrayList<>();
		list.add(meshDagger.searchVerticle());
		list.add(meshDagger.projectVerticle());
		return list;
	}
	
//	@BeforeClass
//	public static void setupOnce() {
//		new RxDebugger().start();
//	}

	@Test
	public void testSearchProject() throws Exception {
		try (NoTx noTx = db.noTx()) {
			fullIndex();
		}

		MeshResponse<ProjectListResponse> future = getClient().searchProjects(getSimpleQuery("dummy"), new PagingParameters().setPage(1).setPerPage(2)).invoke();
		latchFor(future);
		assertSuccess(future);
		ProjectListResponse response = future.result();
		assertEquals(1, response.getData().size());

		future = getClient().searchProjects(getSimpleQuery("blub"), new PagingParameters().setPage(1).setPerPage(2)).invoke();
		latchFor(future);
		assertSuccess(future);
		response = future.result();
		assertEquals(0, response.getData().size());

		future = getClient().searchProjects(getSimpleTermQuery("name", "dummy"), new PagingParameters().setPage(1).setPerPage(2)).invoke();
		latchFor(future);
		assertSuccess(future);
		response = future.result();
		assertEquals(1, response.getData().size());
	}

	@Test
	@Override
	public void testDocumentCreation() throws Exception {

		final String newName = "newproject";
		ProjectResponse project = createProject(newName);
		try (NoTx noTx = db.noTx()) {
			MeshAssert.assertElement(boot.projectRoot(), project.getUuid(), true);
		}
		MeshResponse<ProjectListResponse> future = getClient().searchProjects(getSimpleTermQuery("name", newName),
				new PagingParameters().setPage(1).setPerPage(2)).invoke();
		latchFor(future);
		assertSuccess(future);
		ProjectListResponse response = future.result();
		assertEquals(1, response.getData().size());
	}

	@Test
	@Override
	public void testDocumentDeletion() throws InterruptedException, JSONException {
		final String projectName = "newproject";
		ProjectResponse project = createProject(projectName);

		MeshResponse<ProjectListResponse> future = getClient().searchProjects(getSimpleTermQuery("name", projectName),
				new PagingParameters().setPage(1).setPerPage(2)).invoke();
		latchFor(future);
		assertSuccess(future);
		assertEquals(1, future.result().getData().size());

		deleteProject(project.getUuid());
		future = getClient().searchProjects(getSimpleTermQuery("name", projectName), new PagingParameters().setPage(1).setPerPage(2)).invoke();
		latchFor(future);
		assertSuccess(future);
		assertEquals(0, future.result().getData().size());
	}

	@Test
	@Override
	public void testDocumentUpdate() throws InterruptedException, JSONException {
		final String projectName = "newproject";
		ProjectResponse project = createProject(projectName);

		String newProjectName = "updatedprojectname";
		updateProject(project.getUuid(), newProjectName);

		MeshResponse<ProjectListResponse> future = getClient().searchProjects(getSimpleTermQuery("name", projectName),
				new PagingParameters().setPage(1).setPerPage(2)).invoke();
		latchFor(future);
		assertSuccess(future);
		assertEquals(0, future.result().getData().size());

		future = getClient().searchProjects(getSimpleTermQuery("name", newProjectName), new PagingParameters().setPage(1).setPerPage(2)).invoke();
		latchFor(future);
		assertSuccess(future);
		assertEquals(1, future.result().getData().size());
	}

}
