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

package com.vmware.photon.controller.client.resource;

import com.vmware.photon.controller.api.Image;
import com.vmware.photon.controller.api.ResourceList;
import com.vmware.photon.controller.api.Task;
import com.vmware.photon.controller.client.RestClient;

import com.fasterxml.jackson.core.type.TypeReference;
import com.google.common.collect.ImmutableMap;
import com.google.common.util.concurrent.FutureCallback;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;

import java.io.IOException;

/**
 * Images Api.
 */
public class ImagesApi extends ApiBase {
  public ImagesApi(RestClient restClient) {
    super(restClient);
  }

  @Override
  public String getBasePath() {
    return "/images";
  }

  public Task uploadImage(String inputFileName) throws IOException {
    return uploadImage(inputFileName, "EAGER");
  }

  /**
   * Uploads the image pointed to by the inputFileName.
   *
   * @param inputFileName - path of the image to upload
   * @return Tracking {@link Task}
   * @throws IOException
   */
  public Task uploadImage(String inputFileName, String replicationType) throws IOException {
    String path = getBasePath();

    HttpResponse response =
        this.restClient.upload(path, inputFileName, ImmutableMap.of("IMAGEREPLICATION", replicationType));

    this.restClient.checkResponse(response, HttpStatus.SC_CREATED);
    return parseTaskFromHttpResponse(response);
  }

  /**
   * Get details about the specified image.
   *
   * @param imageId
   * @return Image metadata details
   * @throws java.io.IOException
   */
  public Image getImage(String imageId) throws IOException {
    String path = String.format("%s/%s", getBasePath(), imageId);

    HttpResponse httpResponse = this.restClient.perform(RestClient.Method.GET, path, null);
    this.restClient.checkResponse(httpResponse, HttpStatus.SC_OK);

    return this.restClient.parseHttpResponse(
        httpResponse,
        new TypeReference<Image>() {
        }
    );
  }

  /**
   * Get details about the specified image.
   *
   * @param imageId
   * @param responseCallback
   * @throws IOException
   */
  public void getImageAsync(final String imageId, final FutureCallback<Image> responseCallback) throws IOException {
    final String path = String.format("%s/%s", getBasePath(), imageId);

    getObjectByPathAsync(path, responseCallback, new TypeReference<Image>() {
    });
  }

  /**
   * Returns a list of all images.
   *
   * @return {@link ResourceList} of {@link Image}
   * @throws IOException
   */
  public ResourceList<Image> getImages() throws IOException {
    String path = getBasePath();

    HttpResponse httpResponse = this.restClient.perform(RestClient.Method.GET, path, null);
    this.restClient.checkResponse(httpResponse, HttpStatus.SC_OK);

    return this.restClient.parseHttpResponse(
        httpResponse,
        new TypeReference<ResourceList<Image>>() {
        }
    );
  }

  /**
   * Returns the list of all images.
   *
   * @param responseCallback
   * @throws IOException
   */
  public void getImagesAsync(final FutureCallback<ResourceList<Image>> responseCallback) throws IOException {
    getObjectByPathAsync(getBasePath(), responseCallback, new TypeReference<ResourceList<Image>>() {
    });
  }

  /**
   * Delete the specified image.
   *
   * @param id - id of the image to delete
   * @return Tracking {@link Task}
   * @throws IOException
   */
  public Task delete(String id) throws IOException {
    String path = String.format("%s/%s", getBasePath(), id);

    HttpResponse response = this.restClient.perform(RestClient.Method.DELETE, path, null);

    this.restClient.checkResponse(response, HttpStatus.SC_CREATED);
    return parseTaskFromHttpResponse(response);
  }

  /**
   * Delete the specifies image.
   *
   * @param id
   * @param responseCallback
   * @throws IOException
   */
  public void deleteAsync(final String id, final FutureCallback<Task> responseCallback) throws IOException {
    deleteObjectAsync(id, responseCallback);
  }
}
