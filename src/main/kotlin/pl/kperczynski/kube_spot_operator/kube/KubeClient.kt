package pl.kperczynski.kube_spot_operator.kube

import io.netty.handler.codec.http.HttpStatusClass
import io.vertx.core.Future
import io.vertx.core.Vertx
import io.vertx.core.http.HttpClient
import io.vertx.core.http.HttpClientOptions
import io.vertx.core.http.HttpHeaders
import io.vertx.core.http.HttpMethod
import io.vertx.core.json.JsonObject
import io.vertx.core.net.PemTrustOptions
import org.slf4j.Logger
import pl.kperczynski.kube_spot_operator.logging.Slf4j
import java.net.URI

class KubeClient(
  private val httpClient: HttpClient,
  private val vertx: Vertx,
  private val props: KubeClientProps
) {

  companion object : Slf4j()

  fun fetchJwks(): Future<String> {
    log.debug("Fetching JWKS from Kubernetes API at {}", props.jwksEndpoint)

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

  private fun readToken(): Future<String> {
    return vertx.fileSystem().readFile(props.tokenPath)
      .map { it.toString(Charsets.UTF_8) }
  }

}

fun kubeHttpClient(vertx: Vertx, kubeClientProps: KubeClientProps, log: Logger): HttpClient {
  val origin = URI.create(kubeClientProps.apiOrigin)

  val schemePort = when (origin.scheme) {
    "https" -> 443
    "http" -> 80
    else -> null
  }

  val port = origin.port.takeIf { it != -1 } ?: schemePort ?: 443

  log.debug("Creating Kube HTTP client for origin={} with port={}, caPath={}", origin, port, kubeClientProps.caCertPath)

  val opts = HttpClientOptions()
    .setSsl(origin.scheme == "https")
    .setDefaultHost(origin.host)
    .setDefaultPort(port)
    .setKeepAlive(true)

  if (kubeClientProps.sslTrustAll) {
    log.warn("SSL Trust All is enabled")
    opts.setTrustAll(true)
  } else {
    opts.setTrustOptions(
      PemTrustOptions()
        .addCertPath(kubeClientProps.caCertPath)
    )
  }

  return vertx.createHttpClient(opts)
}

class KubeClientException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)
