package com.gentics.mesh.rest.client.method;

import com.gentics.mesh.core.rest.common.GenericMessageResponse;
import com.gentics.mesh.core.rest.schema.Microschema;
import com.gentics.mesh.core.rest.schema.MicroschemaListResponse;
import com.gentics.mesh.core.rest.schema.Schema;
import com.gentics.mesh.core.rest.schema.SchemaListResponse;
import com.gentics.mesh.core.rest.schema.change.impl.SchemaChangesListModel;
import com.gentics.mesh.parameter.ParameterProvider;
import com.gentics.mesh.rest.client.MeshRequest;

public interface SchemaClientMethods {

	/**
	 * Create a new schema using the given request.
	 * 
	 * @param request
	 * @return
	 */
	MeshRequest<Schema> createSchema(Schema request);

	/**
	 * Load the schema with the given uuid.
	 * 
	 * @param uuid
	 * @param parameters
	 * @return
	 */
	MeshRequest<Schema> findSchemaByUuid(String uuid, ParameterProvider... parameters);

	/**
	 * Update the schema with the given request.
	 * 
	 * @param uuid
	 *            Schema uuid
	 * @param request
	 *            Update request
	 * @return
	 */
	MeshRequest<GenericMessageResponse> updateSchema(String uuid, Schema request);

	/**
	 * Compare the given schema with the currently stored one and return a list of schema changes.
	 * 
	 * @param uuid
	 * @return
	 */
	MeshRequest<SchemaChangesListModel> diffSchema(String uuid, Schema request);

	/**
	 * Delete the given schema
	 * 
	 * @param uuid
	 *            Schema uuid
	 * @return
	 */
	MeshRequest<Void> deleteSchema(String uuid);

	/**
	 * Load multiple schemas.
	 * 
	 * @param parameters
	 * @return
	 */
	MeshRequest<SchemaListResponse> findSchemas(ParameterProvider... parameters);

	/**
	 * Load multiple microschemas.
	 * 
	 * @param parameters
	 * @return
	 */
	MeshRequest<MicroschemaListResponse> findMicroschemas(ParameterProvider... parameters);

	/**
	 * Apply the given list of changes to the schema which is identified by the given uuid.
	 * 
	 * @param uuid
	 *            Schema uuid
	 * @param changes
	 *            List of changes
	 * @return
	 */
	MeshRequest<GenericMessageResponse> applyChangesToSchema(String uuid, SchemaChangesListModel changes);


	/**
	 * Assign a schema to the project
	 *
	 * @param projectName project name
	 * @param schemaUuid schema uuid
	 * @return
	 */
	MeshRequest<Schema> assignSchemaToProject(String projectName, String schemaUuid);

	/**
	 * Unassign a schema from the project
	 *
	 * @param projectName project name
	 * @param schemaUuid schema uuid
	 * @return
	 */
	MeshRequest<Schema> unassignSchemaFromProject(String projectName, String schemaUuid);

	/**
	 * Find all schemas assigned to the project
	 *
	 * @param projectName project name
	 * @param parameters
	 * @return
	 */
	MeshRequest<SchemaListResponse> findSchemas(String projectName, ParameterProvider... parameters);

	/**
	 * Assign a microschema to the project
	 *
	 * @param projectName project name
	 * @param microschemaUuid microschema uuid
	 * @return
	 */
	MeshRequest<Microschema> assignMicroschemaToProject(String projectName, String microschemaUuid);

	/**
	 * Unassign a microschema from the project
	 * @param projectName project name
	 * @param microschemaUuid microschema uuid
	 * @return
	 */
	MeshRequest<Microschema> unassignMicroschemaFromProject(String projectName, String microschemaUuid);

	/**
	 * Find all microschemas assigned to the project
	 *
	 * @param projectName project name
	 * @param parameters
	 * @return
	 */
	MeshRequest<MicroschemaListResponse> findMicroschemas(String projectName, ParameterProvider... parameters);
}
