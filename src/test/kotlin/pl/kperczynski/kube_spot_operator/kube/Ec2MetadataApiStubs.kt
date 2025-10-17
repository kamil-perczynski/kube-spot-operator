package pl.kperczynski.kube_spot_operator.kube

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.*
import com.github.tomakehurst.wiremock.stubbing.StubMapping
import io.vertx.core.Future
import io.vertx.core.http.HttpHeaders.CONTENT_TYPE

class Ec2MetadataApiStubs(private val wiremock: WireMockServer) {

  fun stubIssueToken(): Future<StubMapping> {
    return Future.succeededFuture(
      wiremock.stubFor(
        put("/latest/api/token")
          .withHeader("X-aws-ec2-metadata-token-ttl-seconds", matching(".*"))
          .willReturn(
            aResponse()
              .withStatus(200)
              .withHeader(CONTENT_TYPE.toString(), "text/plain")
              .withBody("Fake-Metadata-Token")
          )
      )
    )
  }

  fun stubInstanceActionNotFound(): Future<StubMapping> {
    return Future.succeededFuture(
      wiremock.stubFor(
        get("/latest/meta-data/spot/instance-action")
          .willReturn(
            aResponse()
              .withStatus(404)
              .withHeader(CONTENT_TYPE.toString(), "application/plain")
              .withBody(
                """
                <html>
                  <head><title>404 Not Found</title></head>
                  <body>
                    <h1>Not Found</h1>
                    The requested resource /latest/meta-data/spot/instance-action was not found on this server.
                  </body>
                </html>
              """.trimIndent()
              )
          )
      )
    )
  }

  fun stubInstanceActionTerminate(): Future<StubMapping> {
    return Future.succeededFuture(
      wiremock.stubFor(
        get("/latest/meta-data/spot/instance-action")
          .willReturn(
            aResponse()
              .withStatus(200)
              .withHeader(CONTENT_TYPE.toString(), "text/plain")
              .withBody("{\"action\":\"terminate\",\"time\":\"2025-10-17T14:51:09Z\"}")
          )
      )
    )
  }

  fun resetAll() {
    wiremock.resetAll()
  }

}

