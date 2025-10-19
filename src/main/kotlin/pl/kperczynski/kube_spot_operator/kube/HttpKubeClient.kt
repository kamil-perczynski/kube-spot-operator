package pl.kperczynski.kube_spot_operator.kube

import io.vertx.core.Future
import io.vertx.core.Vertx
import io.vertx.core.buffer.Buffer
import io.vertx.core.http.HttpClient
import io.vertx.core.http.HttpHeaders
import io.vertx.core.http.HttpMethod.*
import io.vertx.core.json.Json
import io.vertx.core.json.JsonObject
import io.vertx.uritemplate.UriTemplate
import io.vertx.uritemplate.Variables
import org.slf4j.LoggerFactory
import org.slf4j.event.Level
import pl.kperczynski.kube_spot_operator.domain.KubeClient
import pl.kperczynski.kube_spot_operator.domain.model.KubeNode
import pl.kperczynski.kube_spot_operator.domain.model.KubePod
import pl.kperczynski.kube_spot_operator.kube.model.KubeNodeDto
import pl.kperczynski.kube_spot_operator.kube.model.KubeNodeListingDto
import pl.kperczynski.kube_spot_operator.kube.model.KubePodListingDto
import pl.kperczynski.kube_spot_operator.libs.handleResponseErrors
import pl.kperczynski.kube_spot_operator.libs.preconfigureRequest

private val log = LoggerFactory.getLogger(HttpKubeClient::class.java)

private const val UNKNOWN = "unknown"

class HttpKubeClient(
  private val httpClient: HttpClient,
  private val vertx: Vertx,
  private val props: KubeClientProps
) : KubeClient {

  override fun fetchJwks(): Future<JsonObject> {
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

  override fun fetchOpenIdConfiguration(): Future<JsonObject> {
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


  override fun listNodes(): Future<List<KubeNode>> {
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
            val listNode = Json.decodeValue(body, KubeNodeListingDto::class.java)
            toNodesList(listNode).sortedBy { it.name }
          }
      }
  }

  override fun cordonNode(nodeName: String): Future<CordonResult> {
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
          val node = Json.decodeValue(body, KubeNodeDto::class.java)
          val cordonTaint = node.spec.taints.find { taint -> taint.key == "node.kubernetes.io/unschedulable" }

          if (cordonTaint == null) {
            CordonResult.CORDONED
          } else {
            CordonResult.ALREADY_CORDONED
          }
        }
    }
  }

  override fun evictPod(podName: String, namespace: String): Future<Void> {
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

  override fun listNodePods(nodeId: String): Future<List<KubePod>> {
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
          val podList = Json.decodeValue(body, KubePodListingDto::class.java)
          toPodsList(podList)
        }
    }
  }

  override fun deleteNode(nodeName: String): Future<Void> {
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

fun toPodsList(podList: KubePodListingDto): List<KubePod> {
  return podList.items.map { item ->
    val hasEmptyDirVolume = item.spec.volumes.any { vol -> vol.emptyDir != null }

    KubePod(
      name = item.metadata.name,
      namespace = item.metadata.namespace,
      phase = item.status.phase,
      ownerKind = item.metadata.ownerReferences?.firstOrNull()?.kind ?: UNKNOWN,
      ownerName = item.metadata.ownerReferences?.firstOrNull()?.name ?: UNKNOWN,
      hasEmptyDirVolume = hasEmptyDirVolume
    )
  }
}

private fun toNodesList(listNode: KubeNodeListingDto): List<KubeNode> {
  return listNode.items
    .map { node ->
      val conditions = node.status.conditions
      val taints = node.spec.taints.map { it.key }
      val activeConditions = conditions.filter { it.status == "True" }.map { it.type }

      KubeNode(
        name = node.metadata.name,
        conditions = activeConditions,
        taints = taints
      )
    }
}

enum class CordonResult {
  CORDONED,
  ALREADY_CORDONED,
}
