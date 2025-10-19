package pl.kperczynski.kube_spot_operator.kube

import io.kubernetes.client.openapi.models.V1Node
import io.kubernetes.client.openapi.models.V1NodeList
import io.kubernetes.client.openapi.models.V1PodList
import io.vertx.core.Future
import io.vertx.core.Vertx
import io.vertx.core.buffer.Buffer
import io.vertx.core.http.HttpClient
import io.vertx.core.http.HttpClientOptions
import io.vertx.core.http.HttpHeaders
import io.vertx.core.http.HttpMethod.*
import io.vertx.core.json.Json
import io.vertx.core.json.JsonObject
import io.vertx.core.net.PemTrustOptions
import io.vertx.uritemplate.UriTemplate
import io.vertx.uritemplate.Variables
import org.slf4j.LoggerFactory
import org.slf4j.event.Level
import pl.kperczynski.kube_spot_operator.domain.KubeNode
import pl.kperczynski.kube_spot_operator.domain.KubePod
import pl.kperczynski.kube_spot_operator.libs.handleResponseErrors
import pl.kperczynski.kube_spot_operator.libs.preconfigureRequest
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
          .onSuccess(preconfigureRequest(log))
          .compose { req ->
            req.putHeader(HttpHeaders.AUTHORIZATION, "Bearer $token")
            req.send()
          }
      }
      .compose { handleResponseErrors(it, log) }
      .compose { body ->
        Future.succeededFuture(JsonObject(body.toString(Charsets.UTF_8)))
      }
  }

  fun fetchOpenIdConfiguration(): Future<JsonObject> {
    return readToken()
      .compose { token ->
        httpClient
          .request(GET, props.openIdConfigurationEndpoint)
          .onSuccess(preconfigureRequest(log))
          .compose { req ->
            req.putHeader(HttpHeaders.AUTHORIZATION, "Bearer $token")
            req.send()
          }
      }
      .compose { handleResponseErrors(it, log) }
      .compose { body -> Future.succeededFuture(JsonObject(body.toString(Charsets.UTF_8))) }
  }


  fun listNodes(): Future<List<KubeNode>> {
    return readToken()
      .compose { token ->
        httpClient
          .request(GET, "/api/v1/nodes")
          .onSuccess(preconfigureRequest(log, Level.TRACE))
          .compose { req ->
            req.putHeader(HttpHeaders.AUTHORIZATION, "Bearer $token")
            req.send()
          }
          .compose { handleResponseErrors(it, log) }
          .map { body ->
            val listNode = Json.decodeValue(body, V1NodeList::class.java)
            toNodesList(listNode).sortedBy { it.name }
          }
      }
  }

  fun cordonNode(nodeName: String): Future<CordonResult> {
    return readToken().compose { token ->
      httpClient
        .request(PATCH, "/api/v1/nodes/$nodeName")
        .onSuccess(preconfigureRequest(log))
        .compose { req ->
          req.putHeader(HttpHeaders.AUTHORIZATION, "Bearer $token")
          req.putHeader(HttpHeaders.CONTENT_TYPE, "application/strategic-merge-patch+json")
          req.send(Buffer.buffer("{\"spec\":{\"unschedulable\":true}}"))
        }
        .compose { handleResponseErrors(it, log) }
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

  fun evictPod(podName: String, namespace: String): Future<Void> {
    return readToken()
      .compose { token ->
        val uri = UriTemplate.of("/api/v1/namespaces/{namespace}/pods/{pod}/eviction").expandToString(
          Variables.variables()
            .set("namespace", namespace)
            .set("pod", podName)
        )

        httpClient
          .request(POST, uri)
          .onSuccess(preconfigureRequest(log))
          .compose { req ->
            req.putHeader(HttpHeaders.AUTHORIZATION, "Bearer $token")
            req.putHeader(HttpHeaders.CONTENT_TYPE, "application/json")
            req.send(
              JsonObject()
                .put("apiVersion", "policy/v1")
                .put("kind", "Eviction")
                .put(
                  "metadata", JsonObject()
                    .put("name", podName)
                    .put("namespace", namespace)
                )
                .encode()
            )
          }
          .compose { handleResponseErrors(it, log) }
          .mapEmpty()
      }
  }

  fun listNodePods(nodeId: String): Future<List<KubePod>> {
    return readToken().compose { token ->
      val uri = UriTemplate.of("/api/v1/pods?fieldSelector={fieldSelector}").expandToString(
        Variables.variables().set("fieldSelector", "spec.nodeName=$nodeId")
      )

      httpClient
        .request(GET, uri)
        .onSuccess(preconfigureRequest(log))
        .compose { req ->
          req.putHeader(HttpHeaders.AUTHORIZATION, "Bearer $token")
          req.send()
        }
        .compose { handleResponseErrors(it, log) }
        .map { body ->
          val podList = Json.decodeValue(body, V1PodList::class.java)
          toPodsList(podList)
        }
    }
  }

  fun deleteNode(nodeName: String): Future<Void> {
    return readToken().compose { token ->
      httpClient
        .request(DELETE, "/api/v1/nodes/$nodeName")
        .onSuccess(preconfigureRequest(log))
        .compose { req ->
          req.putHeader(HttpHeaders.AUTHORIZATION, "Bearer $token")
          req.send()
        }
        .compose { handleResponseErrors(it, log) }
        .mapEmpty()
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
