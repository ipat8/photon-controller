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

package com.vmware.photon.controller.cloudstore.dcp.entity;

import com.vmware.dcp.common.Operation;
import com.vmware.dcp.common.ServiceDocument;
import com.vmware.dcp.common.StatefulService;
import com.vmware.dcp.services.common.QueryTask;
import com.vmware.photon.controller.api.NetworkState;
import com.vmware.photon.controller.common.dcp.InitializationUtils;
import com.vmware.photon.controller.common.dcp.OperationUtils;
import com.vmware.photon.controller.common.dcp.PatchUtils;
import com.vmware.photon.controller.common.dcp.ServiceUtils;
import com.vmware.photon.controller.common.dcp.ValidationUtils;
import com.vmware.photon.controller.common.dcp.validation.Immutable;
import com.vmware.photon.controller.common.dcp.validation.NotBlank;
import com.vmware.photon.controller.common.dcp.validation.NotNull;

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.List;

/**
 * Class NetworkService is used for data persistence of network information.
 */
public class NetworkService extends StatefulService {

  public static final String PORT_GROUPS_KEY =
      QueryTask.QuerySpecification.buildCollectionItemName(State.FIELD_NAME_PORT_GROUPS);

  public NetworkService() {
    super(State.class);
    super.toggleOption(ServiceOption.PERSISTENCE, true);
    super.toggleOption(ServiceOption.REPLICATION, true);
    super.toggleOption(ServiceOption.OWNER_SELECTION, true);
    super.toggleOption(ServiceOption.INSTRUMENTATION, true);
  }

  @Override
  public void handleStart(Operation startOperation) {
    ServiceUtils.logInfo(this, "Starting service %s", getSelfLink());
    try {
      State startState = startOperation.getBody(State.class);
      InitializationUtils.initialize(startState);
      validateState(startState);
      startOperation.complete();

    } catch (Throwable t) {
      ServiceUtils.logSevere(this, t);
      if (!OperationUtils.isCompleted(startOperation)) {
        startOperation.fail(t);
      }
    }
  }

  @Override
  public void handlePatch(Operation patchOperation) {
    ServiceUtils.logInfo(this, "Patching service %s", getSelfLink());

    try {
      State startState = getState(patchOperation);

      State patchState = patchOperation.getBody(State.class);
      validatePatchState(startState, patchState);

      PatchUtils.patchState(startState, patchState);
      patchOperation.complete();
    } catch (Throwable t) {
      ServiceUtils.logSevere(this, t);
      if (!OperationUtils.isCompleted(patchOperation)) {
        patchOperation.fail(t);
      }
    }
  }

  @Override
  public ServiceDocument getDocumentTemplate() {
    return ServiceUtils.getDocumentTemplateWithIndexedFields(
        super.getDocumentTemplate(), State.FIELD_NAME_PORT_GROUPS);
  }

  /**
   * Validate the service state for coherence.
   *
   * @param currentState
   */
  protected void validateState(State currentState) {
    ValidationUtils.validateState(currentState);
  }

  private void validatePatchState(State startState, State patchState) {
    checkNotNull(patchState, "patch can not be null");
    ValidationUtils.validatePatch(startState, patchState);
  }

  /**
   * Durable service state data. Class encapsulating the data for network.
   */
  public static class State extends ServiceDocument {

    public static final String FIELD_NAME_PORT_GROUPS = "portGroups";

    @NotBlank
    @Immutable
    public String name;

    public String description;

    /**
     * The timestamp indicating when network is marked as PENDING_DELETE.
     */
    public Long deleteRequestTime;

    @NotNull
    public NetworkState state;

    @NotNull
    public List<String> portGroups;
  }
}
