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

package com.vmware.photon.controller.common.dcp;

import com.vmware.dcp.common.Operation;
import com.vmware.dcp.common.ServiceDocument;
import com.vmware.dcp.common.Utils;
import com.vmware.dcp.services.common.ExampleFactoryService;
import com.vmware.dcp.services.common.ExampleService;
import com.vmware.dcp.services.common.QueryTask;
import com.vmware.photon.controller.common.dcp.exceptions.DcpRuntimeException;
import com.vmware.photon.controller.common.dcp.exceptions.DocumentNotFoundException;
import com.vmware.photon.controller.common.thrift.StaticServerSet;

import com.google.common.collect.ImmutableMap;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;

import java.net.InetSocketAddress;
import java.net.ProtocolException;
import java.net.URI;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Tests {@link DcpRestClient}.
 */
public class DcpRestClientTest {

  private static final Integer MAX_ITERATIONS = 10;

  private BasicServiceHost host;
  private DcpRestClient dcpRestClient;

  /**
   * Dummy test case to make Intellij recognize this as a test class.
   */
  @Test
  private void dummy() {
  }

  private void setUpHostAndClient() throws Throwable {
    host = BasicServiceHost.create();
    ExampleFactoryService exampleFactoryService = new ExampleFactoryService();
    host.startServiceSynchronously(exampleFactoryService, null, ExampleFactoryService.SELF_LINK);
    assertThat(host.checkServiceAvailable(exampleFactoryService.getSelfLink()), is(true));

    StaticServerSet serverSet = new StaticServerSet(
        new InetSocketAddress(host.getPreferredAddress(), host.getPort()));

    dcpRestClient = spy(new DcpRestClient(serverSet, Executors.newFixedThreadPool(1)));
  }

  private String createDocument(ExampleService.ExampleServiceState exampleServiceState) throws Throwable {
    return createDocument(dcpRestClient, exampleServiceState);
  }

  private String createDocument(DcpRestClient dcpRestClient, ExampleService.ExampleServiceState exampleServiceState)
      throws Throwable {
    Operation result = dcpRestClient.postAndWait(ExampleFactoryService.SELF_LINK, exampleServiceState);

    assertThat(result.getStatusCode(), is(200));
    ExampleService.ExampleServiceState createdState = result.getBody(ExampleService.ExampleServiceState.class);
    assertThat(createdState.name, is(equalTo(exampleServiceState.name)));
    return createdState.documentSelfLink;
  }

  private String[] createDocuments(Integer documentCount) throws Throwable {
    String[] documentSelfLinks = new String[documentCount];
    for (Integer i = 0; i < documentCount; i++) {
      ExampleService.ExampleServiceState exampleServiceState = new ExampleService.ExampleServiceState();
      exampleServiceState.name = UUID.randomUUID().toString();
      documentSelfLinks[i] = createDocument(exampleServiceState);
    }
    return documentSelfLinks;
  }

  private BasicServiceHost[] setUpMultipleHosts(Integer hostCount) throws Throwable {

    BasicServiceHost[] hosts = new BasicServiceHost[hostCount];
    InetSocketAddress[] servers = new InetSocketAddress[hostCount];
    for (Integer i = 0; i < hostCount; i++) {
      hosts[i] = BasicServiceHost.create();
      hosts[i].setMaintenanceIntervalMicros(TimeUnit.MILLISECONDS.toMicros(100));
      hosts[i].startServiceSynchronously(new ExampleFactoryService(), null, ExampleFactoryService.SELF_LINK);

      servers[i] = new InetSocketAddress(hosts[i].getPreferredAddress(), hosts[i].getPort());
    }

    if (hostCount > 1) {
      // join peer node group
      BasicServiceHost host = hosts[0];
      for (int i = 1; i < hosts.length; i++) {
        BasicServiceHost peerHost = hosts[i];
        ServiceHostUtils.joinNodeGroup(peerHost, host.getUri().getHost(), host.getPort());
      }

      ServiceHostUtils.waitForNodeGroupConvergence(
          hosts,
          com.vmware.dcp.services.common.ServiceUriPaths.DEFAULT_NODE_GROUP,
          ServiceHostUtils.DEFAULT_NODE_GROUP_CONVERGENCE_MAX_RETRIES,
          ServiceHostUtils.DEFAULT_NODE_GROUP_CONVERGENCE_SLEEP);
    }

    StaticServerSet serverSet = new StaticServerSet(servers);
    dcpRestClient = spy(new DcpRestClient(serverSet, Executors.newFixedThreadPool(1)));
    return hosts;
  }

  private DcpRestClient[] setupDcpRestClients(BasicServiceHost[] hosts) {
    DcpRestClient[] dcpRestClients = new DcpRestClient[hosts.length];

    for (Integer i = 0; i < hosts.length; i++) {
      StaticServerSet serverSet = new StaticServerSet(
          new InetSocketAddress(hosts[i].getPreferredAddress(), hosts[i].getPort()));
      dcpRestClients[i] = spy(new DcpRestClient(serverSet, Executors.newFixedThreadPool(1)));
      dcpRestClients[i].start();
    }

    return dcpRestClients;
  }

  /**
   * Tests for the postAndWait operation.
   */
  public class PostAndWaitTest {
    @BeforeMethod
    public void setUp() throws Throwable {
      setUpHostAndClient();
    }

    @AfterMethod
    public void tearDown() throws Throwable {
      if (host != null) {
        host.destroy();
      }

      if (dcpRestClient != null) {
        dcpRestClient.stop();
      }
    }

    @Test
    public void testWithStartedClient() throws Throwable {
      dcpRestClient.start();
      ExampleService.ExampleServiceState exampleServiceState = new ExampleService.ExampleServiceState();
      exampleServiceState.name = UUID.randomUUID().toString();

      Operation result = dcpRestClient.postAndWait(ExampleFactoryService.SELF_LINK, exampleServiceState);

      assertThat(result.getStatusCode(), is(200));
      ExampleService.ExampleServiceState createdState = result.getBody(ExampleService.ExampleServiceState.class);
      assertThat(createdState.name, is(equalTo(exampleServiceState.name)));
      ExampleService.ExampleServiceState savedState = host.getServiceState(ExampleService.ExampleServiceState.class,
          createdState.documentSelfLink);
      assertThat(savedState.name, is(equalTo(exampleServiceState.name)));
    }

    @Test(expectedExceptions = DcpRuntimeException.class)
    public void testWithoutStartingClient() throws Throwable {
      ExampleService.ExampleServiceState exampleServiceState = new ExampleService.ExampleServiceState();
      exampleServiceState.name = UUID.randomUUID().toString();

      dcpRestClient.postAndWait(ExampleFactoryService.SELF_LINK, exampleServiceState);
    }

    @Test(expectedExceptions = DcpRuntimeException.class)
    public void testWithStoppedClient() throws Throwable {
      dcpRestClient.start();
      dcpRestClient.stop();
      ExampleService.ExampleServiceState exampleServiceState = new ExampleService.ExampleServiceState();
      exampleServiceState.name = UUID.randomUUID().toString();

      dcpRestClient.postAndWait(ExampleFactoryService.SELF_LINK, exampleServiceState);
    }
  }

  /**
   * Tests for the getAndWait operation.
   */
  public class GetAndWaitTest {

    @BeforeMethod
    public void setUp() throws Throwable {
      setUpHostAndClient();
      dcpRestClient.start();
    }

    @AfterMethod
    public void tearDown() throws Throwable {
      if (host != null) {
        host.destroy();
      }

      if (dcpRestClient != null) {
        dcpRestClient.stop();
      }
    }

    @Test
    public void testGetOfCreatedDocument() throws Throwable {
      ExampleService.ExampleServiceState exampleServiceState = new ExampleService.ExampleServiceState();
      exampleServiceState.name = UUID.randomUUID().toString();

      String documentSelfLink = createDocument(exampleServiceState);

      Operation result = dcpRestClient.getAndWait(documentSelfLink);
      assertThat(result.getStatusCode(), is(200));

      ExampleService.ExampleServiceState savedState = result.getBody(ExampleService.ExampleServiceState.class);
      assertThat(savedState.name, is(equalTo(exampleServiceState.name)));
    }

    @Test(expectedExceptions = DocumentNotFoundException.class)
    public void testGetOfNonExistingDocument() throws Throwable {
      dcpRestClient.getAndWait(ExampleFactoryService.SELF_LINK + "/" + UUID.randomUUID().toString());
    }

    @Test
    public void testGetOfMultipleCreatedDocument() throws Throwable {
      Map<String, ExampleService.ExampleServiceState> exampleServiceStateMap = new HashMap<>();

      for (int i = 0; i < 5; i++) {
        ExampleService.ExampleServiceState exampleServiceState = new ExampleService.ExampleServiceState();
        exampleServiceState.name = UUID.randomUUID().toString();
        String documentSelfLink = createDocument(exampleServiceState);

        exampleServiceStateMap.put(documentSelfLink, exampleServiceState);
      }

      Collection<Operation> results = dcpRestClient.getAndWait(exampleServiceStateMap.keySet());
      for (Operation result : results) {
        ExampleService.ExampleServiceState savedState = result.getBody(ExampleService.ExampleServiceState.class);
        ExampleService.ExampleServiceState expectedState = exampleServiceStateMap.get(savedState.documentSelfLink);

        assertThat(savedState.name, is(equalTo(expectedState.name)));
      }
    }
  }

  /**
   * Tests for the sendAndWait operation.
   */
  public class SendAndWaitTest {

    @BeforeMethod
    public void setUp() throws Throwable {
      setUpHostAndClient();
      dcpRestClient.start();
    }

    @AfterMethod
    public void tearDown() throws Throwable {
      if (host != null) {
        host.destroy();
      }

      if (dcpRestClient != null) {
        dcpRestClient.stop();
      }
    }

    @Test
    public void testTimeoutOfOperation() throws Throwable {
      String documentSelfLink = ExampleFactoryService.SELF_LINK + "/" + UUID.randomUUID().toString();
      URI uri = dcpRestClient.createUriUsingRandomAddress(documentSelfLink);
      Operation getOperation = Operation
          .createGet(uri)
          .setUri(uri)
          .setExpiration(1)
          .setReferer(OperationUtils.getLocalAddress())
          .setStatusCode(Operation.STATUS_CODE_TIMEOUT);

      OperationLatch.OperationResult operationResult = new OperationLatch.OperationResult();
      operationResult.completedOperation = getOperation;
      String exceptionMessage = UUID.randomUUID().toString();
      operationResult.operationFailure = new ProtocolException(exceptionMessage);

      OperationLatch operationLatch = spy(new OperationLatch(getOperation));
      doReturn(operationResult).when(operationLatch).getOperationResult();

      doReturn(operationLatch).when(dcpRestClient).createOperationLatch(any(Operation.class));

      try {
        dcpRestClient.sendAndWait(getOperation);
        Assert.fail("sendAndWait should have thrown TimeoutException");
      } catch (TimeoutException e) {
        assertThat(e.getMessage(), containsString(exceptionMessage));
      }
    }
  }

  /**
   * Tests for the queryAndWait operation.
   */
  public class QueryAndWaitTest {

    @BeforeMethod
    public void setUp() throws Throwable {
      setUpHostAndClient();
      dcpRestClient.start();
    }

    @AfterMethod
    public void tearDown() throws Throwable {
      if (host != null) {
        host.destroy();
      }

      if (dcpRestClient != null) {
        dcpRestClient.stop();
      }
    }

    @Test
    public void testQueryOfCreatedDocument() throws Throwable {
      ExampleService.ExampleServiceState exampleServiceState = new ExampleService.ExampleServiceState();
      exampleServiceState.name = UUID.randomUUID().toString();

      String documentSelfLink = createDocument(exampleServiceState);

      QueryTask.Query kindClause = new QueryTask.Query()
          .setTermPropertyName(ServiceDocument.FIELD_NAME_KIND)
          .setTermMatchValue(Utils.buildKind(ExampleService.ExampleServiceState.class));

      QueryTask.Query nameClause = new QueryTask.Query()
          .setTermPropertyName("name")
          .setTermMatchValue(exampleServiceState.name);

      QueryTask.QuerySpecification spec = new QueryTask.QuerySpecification();
      spec.query.addBooleanClause(kindClause);
      spec.query.addBooleanClause(nameClause);
      spec.options = EnumSet.of(QueryTask.QuerySpecification.QueryOption.EXPAND_CONTENT);

      Operation result = dcpRestClient.queryAndWait(spec);
      assertThat(result.getStatusCode(), is(200));

      Collection<String> documentLinks = QueryTaskUtils.getQueryResultDocumentLinks(result);
      assertThat(documentLinks.size(), is(1));
      assertThat(documentLinks.iterator().next(), is(equalTo(documentSelfLink)));

      List<ExampleService.ExampleServiceState> results =
          QueryTaskUtils.getQueryResultDocuments(
              ExampleService.ExampleServiceState.class, result);
      assertThat(results.size(), is(1));
      assertThat(results.get(0).documentSelfLink, is(equalTo(documentSelfLink)));
      assertThat(results.get(0).name, is(equalTo(exampleServiceState.name)));
    }

    @Test
    public void testQueryWhenNoDocumentsExist() throws Throwable {
      QueryTask.Query kindClause = new QueryTask.Query()
          .setTermPropertyName(ServiceDocument.FIELD_NAME_KIND)
          .setTermMatchValue(Utils.buildKind(ExampleService.ExampleServiceState.class));

      QueryTask.QuerySpecification spec = new QueryTask.QuerySpecification();
      spec.query = kindClause;
      spec.options = EnumSet.of(QueryTask.QuerySpecification.QueryOption.EXPAND_CONTENT);

      Operation result = dcpRestClient.queryAndWait(spec);
      assertThat(result.getStatusCode(), is(200));

      Collection<String> documentLinks = QueryTaskUtils.getQueryResultDocumentLinks(result);
      assertThat(documentLinks.size(), is(0));
    }
  }

  /**
   * Tests for the patchAndWait operation.
   */
  public class PatchAndWaitTest {

    @BeforeMethod
    public void setUp() throws Throwable {
      setUpHostAndClient();
      dcpRestClient.start();
    }

    @AfterMethod
    public void tearDown() throws Throwable {
      if (host != null) {
        host.destroy();
      }

      if (dcpRestClient != null) {
        dcpRestClient.stop();
      }
    }

    @Test
    public void testPatchOfCreatedDocument() throws Throwable {
      ExampleService.ExampleServiceState exampleServiceState = new ExampleService.ExampleServiceState();
      exampleServiceState.name = UUID.randomUUID().toString();
      exampleServiceState.counter = 0L;

      String documentSelfLink = createDocument(exampleServiceState);

      //patch name only
      ExampleService.ExampleServiceState patchExampleServiceState = new ExampleService.ExampleServiceState();
      patchExampleServiceState.name = UUID.randomUUID().toString();

      Operation result = dcpRestClient.patchAndWait(documentSelfLink, patchExampleServiceState);

      assertThat(result.getStatusCode(), is(200));

      ExampleService.ExampleServiceState savedState = result.getBody(ExampleService.ExampleServiceState.class);
      assertThat(savedState.name, is(equalTo(patchExampleServiceState.name)));
      assertThat(savedState.counter, is(exampleServiceState.counter));

      result = dcpRestClient.getAndWait(documentSelfLink);
      assertThat(result.getStatusCode(), is(200));

      savedState = result.getBody(ExampleService.ExampleServiceState.class);
      assertThat(savedState.name, is(equalTo(patchExampleServiceState.name)));
      assertThat(savedState.counter, is(exampleServiceState.counter));

      //patch counter only
      ExampleService.ExampleServiceState patchExampleServiceState2 = new ExampleService.ExampleServiceState();
      patchExampleServiceState2.counter = 1L;

      result = dcpRestClient.patchAndWait(documentSelfLink, patchExampleServiceState2);

      assertThat(result.getStatusCode(), is(200));

      savedState = result.getBody(ExampleService.ExampleServiceState.class);
      assertThat(savedState.name, is(equalTo(patchExampleServiceState.name)));
      assertThat(savedState.counter, is(patchExampleServiceState2.counter));

      result = dcpRestClient.getAndWait(documentSelfLink);
      assertThat(result.getStatusCode(), is(200));

      savedState = result.getBody(ExampleService.ExampleServiceState.class);
      assertThat(savedState.name, is(equalTo(patchExampleServiceState.name)));
      assertThat(savedState.counter, is(patchExampleServiceState2.counter));
    }

    @Test(expectedExceptions = DocumentNotFoundException.class)
    public void testPatchOfNonExistingDocument() throws Throwable {
      ExampleService.ExampleServiceState exampleServiceState = new ExampleService.ExampleServiceState();
      exampleServiceState.name = UUID.randomUUID().toString();
      exampleServiceState.counter = 0L;

      dcpRestClient.patchAndWait(
          ExampleFactoryService.SELF_LINK + "/" + UUID.randomUUID().toString(),
          exampleServiceState);
    }
  }

  /**
   * Tests for the queryDocuments operation.
   */
  public class QueryDocumentsTest {
    private BasicServiceHost host;
    private DcpRestClient dcpRestClient;

    @BeforeMethod
    public void setUp() throws Throwable {
      setUpHostAndClient();
      dcpRestClient.start();
    }

    @AfterMethod
    public void tearDown() throws Throwable {
      if (host != null) {
        host.destroy();
      }

      if (dcpRestClient != null) {
        dcpRestClient.stop();
      }
    }

    @Test
    public void testQueryOfCreatedDocument() throws Throwable {
      ExampleService.ExampleServiceState exampleServiceState = new ExampleService.ExampleServiceState();
      exampleServiceState.name = UUID.randomUUID().toString();

      String documentSelfLink = createDocument(exampleServiceState);

      List<ExampleService.ExampleServiceState> documentList = dcpRestClient.queryDocuments(
          ExampleService.ExampleServiceState.class, null);

      assertThat(documentList.size(), is(1));
      assertThat(documentList.get(0).name, is(equalTo(exampleServiceState.name)));
      assertThat(documentList.get(0).documentSelfLink, is(equalTo(documentSelfLink)));

      List<String> documentLinks = dcpRestClient.queryDocumentsForLinks(
          ExampleService.ExampleServiceState.class, null);

      assertThat(documentLinks.size(), is(1));
      assertThat(documentLinks.get(0), is(equalTo(documentSelfLink)));

      ImmutableMap.Builder<String, String> termsBuilder = new ImmutableMap.Builder<String, String>();

      documentList = dcpRestClient.queryDocuments(
          ExampleService.ExampleServiceState.class, termsBuilder.build());

      assertThat(documentList.size(), is(1));
      assertThat(documentList.get(0).name, is(equalTo(exampleServiceState.name)));
      assertThat(documentList.get(0).documentSelfLink, is(equalTo(documentSelfLink)));

      dcpRestClient.queryDocumentsForLinks(
          ExampleService.ExampleServiceState.class, null);

      assertThat(documentLinks.size(), is(1));
      assertThat(documentLinks.get(0), is(equalTo(documentSelfLink)));

      termsBuilder.put("name", exampleServiceState.name);

      documentList = dcpRestClient.queryDocuments(
          ExampleService.ExampleServiceState.class, termsBuilder.build());

      assertThat(documentList.size(), is(1));
      assertThat(documentList.get(0).name, is(equalTo(exampleServiceState.name)));
      assertThat(documentList.get(0).documentSelfLink, is(equalTo(documentSelfLink)));

      dcpRestClient.queryDocumentsForLinks(
          ExampleService.ExampleServiceState.class, null);

      assertThat(documentLinks.size(), is(1));
      assertThat(documentLinks.get(0), is(equalTo(documentSelfLink)));
    }

    @Test
    public void testQueryOfMultipleCreatedDocuments() throws Throwable {
      Map<String, ExampleService.ExampleServiceState> exampleServiceStateMap = new HashMap<>();
      for (int i = 0; i < 5; i++) {
        ExampleService.ExampleServiceState exampleServiceState = new ExampleService.ExampleServiceState();
        exampleServiceState.name = UUID.randomUUID().toString();
        String documentSelfLink = createDocument(exampleServiceState);

        exampleServiceStateMap.put(documentSelfLink, exampleServiceState);
      }

      List<ExampleService.ExampleServiceState> documentList = dcpRestClient.queryDocuments(
          ExampleService.ExampleServiceState.class, null);

      assertThat(documentList.size(), is(5));

      List<String> documentLinks = dcpRestClient.queryDocumentsForLinks(
          ExampleService.ExampleServiceState.class, null);

      assertThat(documentLinks.size(), is(5));

      ImmutableMap.Builder<String, String> termsBuilder = new ImmutableMap.Builder<String, String>();

      documentList = dcpRestClient.queryDocuments(
          ExampleService.ExampleServiceState.class, termsBuilder.build());

      assertThat(documentList.size(), is(5));

      documentLinks = dcpRestClient.queryDocumentsForLinks(
          ExampleService.ExampleServiceState.class, termsBuilder.build());

      assertThat(documentLinks.size(), is(5));

      for (Map.Entry<String, ExampleService.ExampleServiceState> entry : exampleServiceStateMap.entrySet()) {
        termsBuilder = new ImmutableMap.Builder<String, String>();
        termsBuilder.put("name", entry.getValue().name);

        documentList = dcpRestClient.queryDocuments(
            ExampleService.ExampleServiceState.class, termsBuilder.build());

        assertThat(documentList.size(), is(1));
        assertThat(documentList.get(0).name, is(equalTo(entry.getValue().name)));
        assertThat(documentList.get(0).documentSelfLink, is(equalTo(entry.getKey())));

        documentLinks = dcpRestClient.queryDocumentsForLinks(
            ExampleService.ExampleServiceState.class, termsBuilder.build());

        assertThat(documentLinks.size(), is(1));
        assertThat(documentLinks.get(0), is(equalTo(entry.getKey())));
      }
    }

    @Test
    public void testQueryWhenNoDocumentsExist() throws Throwable {
      List<ExampleService.ExampleServiceState> documentList = dcpRestClient.queryDocuments(
          ExampleService.ExampleServiceState.class, null);
      assertThat(documentList.size(), is(0));
      Collection<String> documentLinks = dcpRestClient.queryDocumentsForLinks(
          ExampleService.ExampleServiceState.class, null);
      assertThat(documentLinks.size(), is(0));
    }

    private void setUpHostAndClient() throws Throwable {
      host = BasicServiceHost.create();
      host.startServiceSynchronously(new ExampleFactoryService(), null, ExampleFactoryService.SELF_LINK);

      StaticServerSet serverSet = new StaticServerSet(
          new InetSocketAddress(host.getPreferredAddress(), host.getPort()));
      dcpRestClient = spy(new DcpRestClient(serverSet, Executors.newFixedThreadPool(1)));
    }

    private String createDocument(ExampleService.ExampleServiceState exampleServiceState) throws Throwable {
      Operation result = dcpRestClient.postAndWait(ExampleFactoryService.SELF_LINK, exampleServiceState);

      assertThat(result.getStatusCode(), is(200));
      ExampleService.ExampleServiceState createdState = result.getBody(ExampleService.ExampleServiceState.class);
      assertThat(createdState.name, is(equalTo(exampleServiceState.name)));
      return createdState.documentSelfLink;
    }
  }


  /**
   * Tests with multiple hosts.
   */
  public class MultiHostTest {

    private BasicServiceHost[] hosts;
    private DcpRestClient[] dcpRestClients;

    @BeforeMethod
    public void setUp() throws Throwable {
      hosts = setUpMultipleHosts(5);
      dcpRestClients = setupDcpRestClients(hosts);
      doReturn(TimeUnit.SECONDS.toMicros(5)).when(dcpRestClient).getGetOperationExpirationMicros();
      doReturn(TimeUnit.SECONDS.toMicros(5)).when(dcpRestClient).getQueryOperationExpirationMicros();
      dcpRestClient.start();
    }

    @AfterMethod
    public void tearDown() throws Throwable {
      if (hosts != null) {
        for (BasicServiceHost host : hosts) {
          host.destroy();
        }
      }

      if (dcpRestClient != null) {
        dcpRestClient.stop();
      }

      if (dcpRestClients != null) {
        for (DcpRestClient dcpRestClient : dcpRestClients) {
          dcpRestClient.stop();
        }
      }
    }

    @Test
    public void testGetOfNonExistingDocument() throws Throwable {
      String documentSelfLink = null;
      for (int i = 0; i < MAX_ITERATIONS; i++) {
        for (DcpRestClient dcpRestClient : dcpRestClients) {
          try {
            documentSelfLink = ExampleFactoryService.SELF_LINK + "/" + UUID.randomUUID().toString();
            dcpRestClient.getAndWait(documentSelfLink);
            Assert.fail("getAndWait for a non-existing document should have failed");
          } catch (DocumentNotFoundException e) {
            assertThat(e.getMessage(), containsString(documentSelfLink));
          }
        }
      }
    }

    @Test
    public void testPatchOfNonExistingDocument() throws Throwable {
      ExampleService.ExampleServiceState exampleServiceState = new ExampleService.ExampleServiceState();
      String documentSelfLink = null;
      for (int i = 0; i < MAX_ITERATIONS; i++) {
        for (DcpRestClient dcpRestClient : dcpRestClients) {
          try {
            exampleServiceState.name = UUID.randomUUID().toString();
            documentSelfLink = ExampleFactoryService.SELF_LINK + "/" + UUID.randomUUID().toString();
            dcpRestClient.patchAndWait(documentSelfLink, exampleServiceState);
            Assert.fail("patchAndWait for a non-existing document should have failed");
          } catch (DocumentNotFoundException e) {
            assertThat(e.getMessage(), containsString(documentSelfLink));
          }
        }
      }
    }

    @Test
    public void testDeleteOfNonExistingDocument() throws Throwable {
      for (int i = 0; i < MAX_ITERATIONS; i++) {
        for (DcpRestClient dcpRestClient : dcpRestClients) {
          String documentSelfLink = ExampleFactoryService.SELF_LINK + "/" + UUID.randomUUID().toString();
          dcpRestClient.deleteAndWait(documentSelfLink, null);
        }
      }
    }

    @Test
    public void testGetOfCreatedDocument() throws Throwable {
      ExampleService.ExampleServiceState[] exampleServiceStates =
          new ExampleService.ExampleServiceState[dcpRestClients.length];
      String[] documentSelfLinks = new String[dcpRestClients.length];

      for (int j = 0; j < dcpRestClients.length; j++) {
        exampleServiceStates[j] = new ExampleService.ExampleServiceState();
        exampleServiceStates[j].name = UUID.randomUUID().toString();
        documentSelfLinks[j] = createDocument(dcpRestClients[j], exampleServiceStates[j]);
      }

      for (int i = 0; i < MAX_ITERATIONS; i++) {
        for (int j = 0; j < dcpRestClients.length; j++) {
          for (int k = 0; k < dcpRestClients.length; k++) {
            Operation result = dcpRestClients[k].getAndWait(documentSelfLinks[j]);
            ExampleService.ExampleServiceState savedState = result.getBody(ExampleService.ExampleServiceState.class);
            assertThat(savedState.name, is(equalTo(exampleServiceStates[j].name)));
          }
        }
      }
    }

    @Test
    public void testQueryOfCreatedDocument() throws Throwable {
      ExampleService.ExampleServiceState exampleServiceState = new ExampleService.ExampleServiceState();
      exampleServiceState.name = UUID.randomUUID().toString();

      String documentSelfLink = createDocument(exampleServiceState);

      QueryTask.Query kindClause = new QueryTask.Query()
          .setTermPropertyName(ServiceDocument.FIELD_NAME_KIND)
          .setTermMatchValue(Utils.buildKind(ExampleService.ExampleServiceState.class));

      QueryTask.Query nameClause = new QueryTask.Query()
          .setTermPropertyName("name")
          .setTermMatchValue(exampleServiceState.name);

      QueryTask.QuerySpecification spec = new QueryTask.QuerySpecification();
      spec.query.addBooleanClause(kindClause);
      spec.query.addBooleanClause(nameClause);

      spec.options = EnumSet.of(QueryTask.QuerySpecification.QueryOption.EXPAND_CONTENT);

      Operation result = dcpRestClient.queryAndWait(spec);
      assertThat(result.getStatusCode(), is(200));

      Collection<String> documentLinks = QueryTaskUtils.getQueryResultDocumentLinks(result);
      assertThat(documentLinks.size(), is(1));
      assertThat(documentLinks.iterator().next(), is(equalTo(documentSelfLink)));

      List<ExampleService.ExampleServiceState> results =
          QueryTaskUtils.getQueryResultDocuments(
              ExampleService.ExampleServiceState.class, result);
      assertThat(results.size(), is(1));
      assertThat(results.get(0).documentSelfLink, is(equalTo(documentSelfLink)));
      assertThat(results.get(0).name, is(equalTo(exampleServiceState.name)));

      result = dcpRestClient.getAndWait(results.get(0).documentSelfLink);
      ExampleService.ExampleServiceState document = result.getBody(ExampleService.ExampleServiceState.class);
      assertThat(results.get(0).documentSelfLink, is(equalTo(document.documentSelfLink)));
    }

    @Test
    public void testQueryOfCreatedDocumentWithDifferentHosts() throws Throwable {

      ExampleService.ExampleServiceState exampleServiceState = new ExampleService.ExampleServiceState();
      exampleServiceState.name = UUID.randomUUID().toString();

      String documentSelfLink = createDocument(dcpRestClients[0], exampleServiceState);

      QueryTask.Query kindClause = new QueryTask.Query()
          .setTermPropertyName(ServiceDocument.FIELD_NAME_KIND)
          .setTermMatchValue(Utils.buildKind(ExampleService.ExampleServiceState.class));

      QueryTask.Query nameClause = new QueryTask.Query()
          .setTermPropertyName("name")
          .setTermMatchValue(exampleServiceState.name);

      QueryTask.QuerySpecification spec = new QueryTask.QuerySpecification();
      spec.query.addBooleanClause(kindClause);
      spec.query.addBooleanClause(nameClause);

      Operation result = dcpRestClients[1].queryAndWait(spec);
      assertThat(result.getStatusCode(), is(200));

      Collection<String> documentLinks = QueryTaskUtils.getQueryResultDocumentLinks(result);
      assertThat(documentLinks.size(), is(1));
      assertThat(documentLinks.iterator().next(), is(equalTo(documentSelfLink)));
    }

    @Test
    public void testQueryWhenNoDocumentsExist() throws Throwable {
      QueryTask.Query kindClause = new QueryTask.Query()
          .setTermPropertyName(ServiceDocument.FIELD_NAME_KIND)
          .setTermMatchValue(Utils.buildKind(ExampleService.ExampleServiceState.class));

      QueryTask.QuerySpecification spec = new QueryTask.QuerySpecification();
      spec.query = kindClause;
      spec.options = EnumSet.of(QueryTask.QuerySpecification.QueryOption.EXPAND_CONTENT);

      Operation result = dcpRestClient.queryAndWait(spec);
      assertThat(result.getStatusCode(), is(200));

      QueryTask queryResult = result.getBody(QueryTask.class);
      assertThat(queryResult, is(notNullValue()));
      assertThat(queryResult.results, is(nullValue()));
    }
  }
}
