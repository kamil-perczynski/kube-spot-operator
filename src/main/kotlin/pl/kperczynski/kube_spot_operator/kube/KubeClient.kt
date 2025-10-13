package pl.kperczynski.kube_spot_operator.kube

import io.kubernetes.client.openapi.models.V1Node
import io.kubernetes.client.openapi.models.V1NodeList
import io.netty.handler.codec.http.HttpStatusClass
import io.vertx.core.Future
import io.vertx.core.Vertx
import io.vertx.core.buffer.Buffer
import io.vertx.core.http.HttpClient
import io.vertx.core.http.HttpClientOptions
import io.vertx.core.http.HttpClientResponse
import io.vertx.core.http.HttpHeaders
import io.vertx.core.http.HttpMethod
import io.vertx.core.json.Json
import io.vertx.core.json.JsonObject
import io.vertx.core.net.PemTrustOptions
import org.slf4j.LoggerFactory
import java.net.URI

private val log = LoggerFactory.getLogger(KubeClient::class.java)

class KubeClient(
  private val httpClient: HttpClient,
  private val vertx: Vertx,
  private val props: KubeClientProps
) {

  fun fetchJwks(): Future<String> {
    log.debug("Fetching GET {}", props.jwksEndpoint)

    return readToken()
      .compose { token ->
        httpClient
          .request(HttpMethod.GET, props.jwksEndpoint)
          .compose { req ->
            req.putHeader(HttpHeaders.AUTHORIZATION, "Bearer $token")
            req.idleTimeout(5000L)
            req.send()
          }
      }
      .compose { res ->
        if (HttpStatusClass.valueOf(res.statusCode()) == HttpStatusClass.SUCCESS) {
          return@compose res.body()
        }

        res.body().compose {
          Future.failedFuture(
            KubeClientException(
              "Failed to fetch ${res.request().uri}. Status: ${res.statusCode()}, Body: ${it.toString(Charsets.UTF_8)}"
            )
          )
        }
      }
      .compose { body ->
        Future.succeededFuture(body.toString(Charsets.UTF_8))
      }
  }

  fun fetchOpenIdConfiguration(): Future<JsonObject> {
    log.debug("Fetching GET {}", props.openIdConfigurationEndpoint)

    return readToken()
      .compose { token ->
        httpClient
          .request(HttpMethod.GET, props.openIdConfigurationEndpoint)
          .compose { req ->
            req.putHeader(HttpHeaders.AUTHORIZATION, "Bearer $token")
            req.idleTimeout(5000L)
            req.send()
          }
      }
      .compose { handleResponseErrors(it) }
      .compose { body -> Future.succeededFuture(JsonObject(body.toString(Charsets.UTF_8))) }
  }


  private fun readToken(): Future<String> {
    return vertx.fileSystem().readFile(props.tokenPath)
      .map { it.toString(Charsets.UTF_8).trim() }
  }

  fun listNodes(): Future<List<KubeNode>> {
    log.debug("Listing nodes from the cluster")

    return readToken()
      .compose { token ->
        httpClient
          .request(HttpMethod.GET, "/api/v1/nodes")
          .compose { req ->
            req.putHeader(HttpHeaders.AUTHORIZATION, "Bearer $token")
            req.idleTimeout(5000L)
            req.send()
          }
          .compose { handleResponseErrors(it) }
          .map { body ->
            val listNode = Json.decodeValue(body, V1NodeList::class.java)
            toNodesList(listNode)
          }
      }
  }

  fun cordonNode(nodeId: String): Future<CordonResult> {
    log.debug("Draining node {}", nodeId)

    return readToken()
      .compose { token ->
        httpClient
          .request(HttpMethod.PATCH, "/api/v1/nodes/$nodeId")
          .compose { req ->
            req.putHeader(HttpHeaders.AUTHORIZATION, "Bearer $token")
            req.putHeader(HttpHeaders.CONTENT_TYPE, "application/strategic-merge-patch+json")
            req.idleTimeout(5000L)
            req.send(Buffer.buffer("{\"spec\":{\"unschedulable\":true}}"))
          }
          .compose { handleResponseErrors(it) }
          .map { body ->
            val node = Json.decodeValue(body, V1Node::class.java)
            val cordonTaint = node.spec?.taints?.find { taint -> taint.key == "node.kubernetes.io/unschedulable" }

            if (cordonTaint == null) {
              CordonResult.CORDONED
            } else {
              CordonResult.ALREADY_CORDONED
            }
          }
      }
  }
}

private fun toNodesList(listNode: V1NodeList): List<KubeNode> {
  return listNode.items
    .filter { node -> node.metadata?.name != null }
    .map { node ->
      val conditions = node.status?.conditions ?: emptyList()
      val taints = node.spec?.taints?.map { it.key } ?: emptyList()
      val activeConditions = conditions.filter { it.status == "True" }.map { it.type }

      KubeNode(
        name = node.metadata?.name ?: "unknown",
        conditions = activeConditions,
        taints = taints
      )
    }
}

private fun handleResponseErrors(res: HttpClientResponse): Future<Buffer> {
  if (HttpStatusClass.valueOf(res.statusCode()) == HttpStatusClass.SUCCESS) {
    return res.body()
  }

  return res.body().compose {
    Future.failedFuture(
      KubeClientException(
        "Failed to fetch ${res.request().uri}. Status: ${res.statusCode()}, Body: ${it.toString(Charsets.UTF_8)}"
      )
    )
  }
}

fun kubeHttpClient(vertx: Vertx, kubeClientProps: KubeClientProps): HttpClient {
  val origin = URI.create(kubeClientProps.apiOrigin)

  val schemePort = when (origin.scheme) {
    "https" -> 443
    "http" -> 80
    else -> null
  }

  val port = origin.port.takeIf { it != -1 } ?: schemePort ?: 443

  log.debug(
    "Creating kube openid client for origin={} with port={}, caPath={}",
    origin,
    port,
    kubeClientProps.caCertPath
  )

  val opts = HttpClientOptions()
    .setSsl(origin.scheme == "https")
    .setDefaultHost(origin.host)
    .setDefaultPort(port)
    .setKeepAlive(true)

  if (kubeClientProps.sslTrustAll) {
    log.warn("SSL Trust All is enabled")
    opts.setTrustAll(true)
    opts.setVerifyHost(false)
  } else {
    opts.setTrustOptions(
      PemTrustOptions()
        .addCertPath(kubeClientProps.caCertPath)
    )
  }

  return vertx.createHttpClient(opts)
}

enum class CordonResult {
  CORDONED,
  ALREADY_CORDONED,
}
