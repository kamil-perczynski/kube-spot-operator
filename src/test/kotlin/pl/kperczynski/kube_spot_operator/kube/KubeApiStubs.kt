package pl.kperczynski.kube_spot_operator.kube

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.*
import com.github.tomakehurst.wiremock.stubbing.StubMapping
import io.vertx.core.Future
import io.vertx.core.Vertx
import io.vertx.core.http.HttpHeaders.CONTENT_TYPE

class KubeApiStubs(private val vertx: Vertx, private val wiremock: WireMockServer) {

  fun stubListNodes(): Future<StubMapping> {
    return vertx.fileSystem().readFile("./mocks/list-nodes-one-tainted-node.json").map { nodesJson ->
      wiremock.stubFor(
        get("/api/v1/nodes").willReturn(
          aResponse()
            .withStatus(200)
            .withHeader(CONTENT_TYPE.toString(), "application/json")
            .withBody(nodesJson.bytes)
        )
      )
    }
  }

  fun stubListPodsOnNode(nodeName: String, customizer: MappingFn = { it }): Future<StubMapping> {
    return vertx.fileSystem().readFile("./mocks/node-ip-10-46-102-33-eu-north-1-compute-internal-pods.json")
      .map { podsJson ->
        val mapping = get(urlPathEqualTo("/api/v1/pods"))
          .withQueryParam("fieldSelector", equalTo("spec.nodeName=$nodeName"))
          .willReturn(
            aResponse()
              .withStatus(200)
              .withHeader(CONTENT_TYPE.toString(), "application/json")
              .withBody(podsJson.bytes)
          )

        wiremock.stubFor(customizer(mapping))
      }
  }

  fun stubListAllEvictedPodsOnNode(nodeName: String, customizer: MappingFn = { it }): Future<StubMapping> {
    return vertx.fileSystem().readFile("./mocks/node-ip-10-46-102-33-eu-north-1-compute-internal-all-evicted-pods.json")
      .map { podsJson ->
        val mapping = get(urlPathEqualTo("/api/v1/pods"))
          .withQueryParam("fieldSelector", equalTo("spec.nodeName=$nodeName"))
          .willReturn(
            aResponse()
              .withStatus(200)
              .withHeader(CONTENT_TYPE.toString(), "application/json")
              .withBody(podsJson.bytes)
          )

        wiremock.stubFor(customizer(mapping))
      }
  }

  fun stubListNodesError(): Future<StubMapping> {
    return vertx.fileSystem().readFile("./mocks/unauthorized-error.json").map { errorJson ->
      wiremock.stubFor(
        get("/api/v1/nodes").willReturn(
          aResponse()
            .withStatus(401)
            .withHeader(CONTENT_TYPE.toString(), "application/json")
            .withBody(errorJson.bytes)
        )
      )
    }
  }

  fun stubCordonNode(nodeId: String): Future<StubMapping> {
    return vertx.fileSystem().readFile("./mocks/cordon-node-result.json").map { nodesJson ->
      wiremock.stubFor(
        patch("/api/v1/nodes/$nodeId")
          .withHeader("Content-Type", equalTo("application/strategic-merge-patch+json"))
          .willReturn(
            aResponse()
              .withStatus(200)
              .withHeader(CONTENT_TYPE.toString(), "application/json")
              .withBody(nodesJson.bytes)
          )
      )
    }
  }

  fun stubEvictPod(podName: String, namespace: String): Future<StubMapping> {
    return vertx.fileSystem().readFile("./mocks/pod-evicted-result.json").map { nodesJson ->
      wiremock.stubFor(
        post(urlPathEqualTo("/api/v1/namespaces/$namespace/pods/$podName/eviction"))
          .willReturn(
            aResponse()
              .withStatus(201)
              .withHeader(CONTENT_TYPE.toString(), "application/json")
              .withBody(nodesJson.bytes)
          )
      )
    }
  }

  fun stubDeleteNodeSuccess(nodeName: String): Future<StubMapping> {
    return vertx.fileSystem().readFile("./mocks/node-delete-success.json").map { deleteNodeJson ->
      wiremock.stubFor(
        delete("/api/v1/nodes/$nodeName")
          .willReturn(
            aResponse()
              .withStatus(200)
              .withHeader(CONTENT_TYPE.toString(), "application/json")
              .withBody(deleteNodeJson.bytes)
          )
      )
    }
  }

  fun stubDeleteNodeNotFound(nodeName: String): Future<StubMapping> {
    return vertx.fileSystem().readFile("./mocks/node-delete-not-found.json").map { deleteNodeJson ->
      wiremock.stubFor(
        delete("/api/v1/nodes/$nodeName")
          .willReturn(
            aResponse()
              .withStatus(404)
              .withHeader(CONTENT_TYPE.toString(), "application/json")
              .withBody(deleteNodeJson.bytes)
          )
      )
    }
  }

  fun resetAll() {
    wiremock.resetAll()
  }

}
