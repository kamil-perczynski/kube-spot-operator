package pl.kperczynski.kube_spot_operator.kube

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.get
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
