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

import com.vmware.photon.controller.api.ResourceList;
import com.vmware.photon.controller.api.Task;
import com.vmware.photon.controller.api.common.exceptions.external.ExternalException;
import com.vmware.photon.controller.apife.clients.TaskFeClient;
import com.vmware.photon.controller.apife.resources.routes.FlavorsResourceRoutes;
import com.vmware.photon.controller.apife.resources.routes.TaskResourceRoutes;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import org.mockito.Mock;
import org.testng.annotations.Test;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.when;

import javax.ws.rs.core.GenericType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriBuilder;

import java.net.URI;

/**
 * Test {@link FlavorTasksResource}.
 */
public class FlavorTasksResourceTest extends ResourceTest {

  private String flavorId = "flavor1";
  private String taskId = "task1";

  private String flavorTaskRoute =
      UriBuilder.fromPath(FlavorsResourceRoutes.FLAVOR_TASKS_PATH).build(flavorId).toString();
  private String taskRoutePath =
      UriBuilder.fromPath(TaskResourceRoutes.TASK_PATH).build(taskId).toString();

  @Mock
  private TaskFeClient client;

  @Override
  protected void setUpResources() {
    addResource(new FlavorTasksResource(client));
  }

  @Test
  public void testGetFlavorTasks() throws Exception {
    Task task = new Task();
    task.setId(taskId);

    when(client.getFlavorTasks(flavorId, Optional.<String>absent()))
        .thenReturn(new ResourceList<Task>(ImmutableList.of(task)));

    Response response = client()
        .target(flavorTaskRoute)
        .request("application/json")
        .get();
    assertThat(response.getStatus(), is(200));

    ResourceList<Task> tasks = response.readEntity(
        new GenericType<ResourceList<Task>>() {
        }
    );

    assertThat(tasks.getItems().size(), is(1));
    assertThat(tasks.getItems().get(0), is(task));

    for (Task t : tasks.getItems()) {
      assertThat(new URI(t.getSelfLink()).isAbsolute(), is(true));
      assertThat(t.getSelfLink().endsWith(taskRoutePath), is(true));
    }
  }

  @Test
  public void testGetFlavorTasksWithInvalidId() throws Exception {
    Task task = new Task();
    task.setId(taskId);

    when(client.getFlavorTasks(flavorId, Optional.<String>absent()))
        .thenThrow(new ExternalException("Invalid flavor Id."));

    Response response = client()
        .target(flavorTaskRoute)
        .request("application/json")
        .get();
    assertThat(response.getStatus(), is(500));
  }
}
