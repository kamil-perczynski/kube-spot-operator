package pl.kperczynski.kube_spot_operator.kube

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.client.WireMock.*
import com.github.tomakehurst.wiremock.stubbing.StubMapping
import io.vertx.core.Future
import io.vertx.core.Vertx
import io.vertx.core.http.HttpHeaders

class KubeApiStubs(private val vertx: Vertx, private val wiremock: WireMockServer) {

  fun stubListNodes(): Future<StubMapping> {
    return vertx.fileSystem().readFile("./mocks/list-nodes-one-tainted-node.json").map { nodesJson ->
      wiremock.stubFor(
        get("/api/v1/nodes").willReturn(
          aResponse()
            .withStatus(200)
            .withHeader(HttpHeaders.CONTENT_TYPE.toString(), "application/json")
            .withBody(nodesJson.bytes)
        )
      )
    }
  }

  fun stubListPodsOnNode(nodeName: String): Future<StubMapping> {
    return vertx.fileSystem().readFile("./mocks/node-ip-10-46-102-33-eu-north-1-compute-internal-pods.json")
      .map { podsJson ->
        wiremock.stubFor(
          get(urlPathEqualTo("/api/v1/pods"))
            .withQueryParam("fieldSelector", equalTo("spec.nodeName=$nodeName"))
            .willReturn(
              aResponse()
                .withStatus(200)
                .withHeader(HttpHeaders.CONTENT_TYPE.toString(), "application/json")
                .withBody(podsJson.bytes)
            )
        )
      }
  }

  fun stubListNodesError(): Future<StubMapping> {
    return vertx.fileSystem().readFile("./mocks/unauthorized-error.json").map { errorJson ->
      wiremock.stubFor(
        get("/api/v1/nodes").willReturn(
          aResponse()
            .withStatus(401)
            .withHeader(HttpHeaders.CONTENT_TYPE.toString(), "application/json")
            .withBody(errorJson.bytes)
        )
      )
    }
  }

  fun stubCordonNode(nodeId: String): Future<StubMapping> {
    return vertx.fileSystem().readFile("./mocks/cordon-node-result.json").map { nodesJson ->
      wiremock.stubFor(
        WireMock.patch("/api/v1/nodes/$nodeId")
          .withHeader("Content-Type", equalTo("application/strategic-merge-patch+json"))
          .willReturn(
            aResponse()
              .withStatus(200)
              .withHeader(HttpHeaders.CONTENT_TYPE.toString(), "application/json")
              .withBody(nodesJson.bytes)
          )
      )
    }
  }

  fun resetAll() {
    wiremock.resetAll()
  }

}
