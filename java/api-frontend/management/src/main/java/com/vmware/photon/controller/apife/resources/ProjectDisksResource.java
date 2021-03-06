/*
 * Copyright 2015 VMware, Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License.  You may obtain a copy of
 * the License at http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed
 * under the License is distributed on an "AS IS" BASIS, without warranties or
 * conditions of any kind, EITHER EXPRESS OR IMPLIED.  See the License for the
 * specific language governing permissions and limitations under the License.
 */

package com.vmware.photon.controller.apife.resources;

import com.vmware.photon.controller.api.DiskCreateSpec;
import com.vmware.photon.controller.api.LocalitySpec;
import com.vmware.photon.controller.api.PersistentDisk;
import com.vmware.photon.controller.api.ResourceList;
import com.vmware.photon.controller.api.Task;
import com.vmware.photon.controller.api.Vm;
import com.vmware.photon.controller.api.common.exceptions.external.ExternalException;
import com.vmware.photon.controller.apife.clients.DiskFeClient;
import com.vmware.photon.controller.apife.exceptions.external.InvalidLocalitySpecException;
import com.vmware.photon.controller.apife.resources.routes.DiskResourceRoutes;
import com.vmware.photon.controller.apife.resources.routes.ProjectResourceRoutes;
import com.vmware.photon.controller.apife.resources.routes.TaskResourceRoutes;
import static com.vmware.photon.controller.api.common.Responses.generateCustomResponse;
import static com.vmware.photon.controller.api.common.Responses.generateResourceListResponse;

import com.google.common.base.Optional;
import com.google.inject.Inject;
import com.wordnik.swagger.annotations.Api;
import com.wordnik.swagger.annotations.ApiOperation;
import com.wordnik.swagger.annotations.ApiResponse;
import com.wordnik.swagger.annotations.ApiResponses;
import io.dropwizard.validation.Validated;
import org.glassfish.jersey.server.ContainerRequest;

import javax.validation.ConstraintViolation;
import javax.validation.Validation;
import javax.validation.Validator;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Request;
import javax.ws.rs.core.Response;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * This resource is for disk related API under a project.
 */
@Path(ProjectResourceRoutes.PROJECT_DISKS_PATH)
@Api(value = ProjectResourceRoutes.API)
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class ProjectDisksResource {

  private final DiskFeClient feClient;

  @Inject
  public ProjectDisksResource(DiskFeClient diskFeClient) {
    this.feClient = diskFeClient;
  }

  @POST
  @ApiOperation(value = "Create a Disk in a project", response = Task.class)
  @ApiResponses(value = {
      @ApiResponse(code = 201, message = "Task created, Disk creation progress communicated via the task")
  })
  public Response create(
      @Context Request request,
      @PathParam("id") String projectId,
      @Validated DiskCreateSpec spec)
      throws ExternalException {
    validate(spec);
    return generateCustomResponse(
        Response.Status.CREATED,
        feClient.create(projectId, spec),
        (ContainerRequest) request,
        TaskResourceRoutes.TASK_PATH);
  }

  @GET
  @ApiOperation(value = "Find Disks in a project",
      response = PersistentDisk.class, responseContainer = ResourceList.CLASS_NAME)
  @ApiResponses(value = {@ApiResponse(code = 200, message = "List of disks in the project")})
  public Response find(@Context Request request,
                       @PathParam("id") String projectId,
                       @QueryParam("name") Optional<String> name)
      throws ExternalException {
    return generateResourceListResponse(
        Response.Status.OK,
        feClient.find(projectId, name),
        (ContainerRequest) request,
        DiskResourceRoutes.DISK_PATH);
  }

  private void validate(DiskCreateSpec spec) throws InvalidLocalitySpecException {
    List<LocalitySpec> localitySpecList = spec.getAffinities();
    if (localitySpecList.isEmpty()) {
      return;
    }

    Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
    for (LocalitySpec localitySpec : localitySpecList) {
      Set<ConstraintViolation<LocalitySpec>> errors = validator.validate(localitySpec);
      if (!errors.isEmpty()) {
        List<String> errorList = new ArrayList<>();
        for (ConstraintViolation<LocalitySpec> violation : errors) {
          String error = String.format("%s %s (was %s)", violation.getPropertyPath(), violation.getMessage(),
              violation.getInvalidValue());
          errorList.add(error);
        }
        throw new InvalidLocalitySpecException(errorList.toString());
      }

      if (!localitySpec.getKind().equals(Vm.KIND)) {
        throw new InvalidLocalitySpecException(String.format("Create disk can only take locality kind of vm, " +
            "but got %s", localitySpec.getKind()));
      }
    }
  }
}
