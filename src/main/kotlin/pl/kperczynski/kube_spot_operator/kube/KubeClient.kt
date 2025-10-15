package pl.kperczynski.kube_spot_operator.kube

import io.kubernetes.client.openapi.models.V1Node
import io.kubernetes.client.openapi.models.V1NodeList
import io.kubernetes.client.openapi.models.V1PodList
import io.netty.handler.codec.http.HttpStatusClass
import io.vertx.core.Future
import io.vertx.core.Handler
import io.vertx.core.Vertx
import io.vertx.core.buffer.Buffer
import io.vertx.core.http.*
import io.vertx.core.http.HttpMethod.GET
import io.vertx.core.http.HttpMethod.PATCH
import io.vertx.core.json.Json
import io.vertx.core.json.JsonObject
import io.vertx.core.net.PemTrustOptions
import io.vertx.uritemplate.UriTemplate
import io.vertx.uritemplate.Variables
import org.slf4j.LoggerFactory
import pl.kperczynski.kube_spot_operator.domain.KubeNode
import pl.kperczynski.kube_spot_operator.domain.KubePod
import java.net.URI

private val log = LoggerFactory.getLogger(KubeClient::class.java)

private const val UNKNOWN = "unknown"

class KubeClient(
  private val httpClient: HttpClient,
  private val vertx: Vertx,
  private val props: KubeClientProps
) {

  fun fetchJwks(): Future<JsonObject> {
    return readToken()
      .compose { token ->
        httpClient
          .request(GET, props.jwksEndpoint)
          .onSuccess(preconfigureRequest())
          .compose { req ->
            req.putHeader(HttpHeaders.AUTHORIZATION, "Bearer $token")
            req.send()
          }
      }
      .compose { handleResponseErrors(it) }
      .compose { body ->
        Future.succeededFuture(JsonObject(body.toString(Charsets.UTF_8)))
      }
  }

  fun fetchOpenIdConfiguration(): Future<JsonObject> {
    return readToken()
      .compose { token ->
        httpClient
          .request(GET, props.openIdConfigurationEndpoint)
          .onSuccess(preconfigureRequest())
          .compose { req ->
            req.putHeader(HttpHeaders.AUTHORIZATION, "Bearer $token")
            req.send()
          }
      }
      .compose { handleResponseErrors(it) }
      .compose { body -> Future.succeededFuture(JsonObject(body.toString(Charsets.UTF_8))) }
  }


  fun listNodes(): Future<List<KubeNode>> {
    return readToken()
      .compose { token ->
        httpClient
          .request(GET, "/api/v1/nodes")
          .onSuccess(preconfigureRequest())
          .compose { req ->
            req.putHeader(HttpHeaders.AUTHORIZATION, "Bearer $token")
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
    return readToken()
      .compose { token ->
        httpClient
          .request(PATCH, "/api/v1/nodes/$nodeId")
          .onSuccess(preconfigureRequest())
          .compose { req ->
            req.putHeader(HttpHeaders.AUTHORIZATION, "Bearer $token")
            req.putHeader(HttpHeaders.CONTENT_TYPE, "application/strategic-merge-patch+json")
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

  fun listNodePods(nodeId: String): Future<List<KubePod>> {
    return readToken().compose { token ->
      val uri = UriTemplate.of("/api/v1/pods?fieldSelector={fieldSelector}").expandToString(
        Variables.variables().set("fieldSelector", "spec.nodeName=$nodeId")
      )

      httpClient
        .request(GET, uri)
        .onSuccess(preconfigureRequest())
        .compose { req ->
          req.putHeader(HttpHeaders.AUTHORIZATION, "Bearer $token")
          req.send()
        }
        .compose { handleResponseErrors(it) }
        .map { body ->
          val podList = Json.decodeValue(body, V1PodList::class.java)
          toPodsList(podList)
        }
    }
  }

  private fun readToken(): Future<String> {
    return vertx.fileSystem().readFile(props.tokenPath)
      .map { it.toString(Charsets.UTF_8).trim() }
  }

}

fun toPodsList(podList: V1PodList): List<KubePod> {
  return podList.items.map { item ->
    val hasEmptyDirVolume = item.spec?.volumes?.any { vol -> vol.emptyDir != null } ?: false

    KubePod(
      name = item.metadata?.name ?: UNKNOWN,
      namespace = item.metadata?.namespace ?: UNKNOWN,
      phase = item.status?.phase ?: UNKNOWN,
      ownerKind = item.metadata?.ownerReferences?.firstOrNull()?.kind ?: UNKNOWN,
      ownerName = item.metadata?.ownerReferences?.firstOrNull()?.name ?: UNKNOWN,
      hasEmptyDirVolume = hasEmptyDirVolume
    )
  }
}

private fun preconfigureRequest(): Handler<in HttpClientRequest> {
  return Handler {
    log.info("Calling ${it.method} ${it.uri}")
    it.idleTimeout(5000L)
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
        name = node.metadata?.name ?: UNKNOWN,
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
    log.info(
      "Call {} {} returned status={} body={}",
      res.request().method,
      res.request().uri,
      res.statusCode(),
      it.toString(Charsets.UTF_8)
    )

    Future.failedFuture(
      KubeClientException("Call ${res.request().method} ${res.request().uri} returned status=${res.statusCode()}")
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
