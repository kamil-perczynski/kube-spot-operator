package pl.kperczynski.kube_spot_operator

import io.netty.handler.codec.http.HttpResponseStatus
import io.vertx.core.Future
import io.vertx.core.VerticleBase
import io.vertx.core.http.HttpHeaders.CONTENT_TYPE
import io.vertx.core.http.HttpServer
import io.vertx.core.json.JsonArray
import io.vertx.core.json.JsonObject
import io.vertx.ext.web.Router
import pl.kperczynski.kube_spot_operator.http.HttpServerProps
import pl.kperczynski.kube_spot_operator.kube.KubeClient
import pl.kperczynski.kube_spot_operator.kube.KubeClientProps
import pl.kperczynski.kube_spot_operator.kube.kubeHttpClient
import pl.kperczynski.kube_spot_operator.logging.Slf4j

private const val APPLICATION_JSON = "application/json"

class HttpServerVerticle(
  private val httpProps: HttpServerProps,
  private val kubeClientProps: KubeClientProps
) : VerticleBase() {

  companion object : Slf4j()

  private lateinit var kubeClient: KubeClient
  private lateinit var router: Router
  private lateinit var server: HttpServer

  override fun start(): Future<*> {
    this.server = vertx.createHttpServer()
    this.router = Router.router(vertx)
    this.kubeClient = KubeClient(kubeHttpClient(vertx, kubeClientProps, log), vertx, kubeClientProps)

    router.get("/").handler { ctx ->
      ctx.response()
        .setStatusCode(HttpResponseStatus.OK.code())
        .putHeader(CONTENT_TYPE, APPLICATION_JSON)
        .end(
          JsonObject()
            .put("service", "kube-spot-operator")
            .put("description", "Kubernetes operator for managing spot instances")
            .put(
              "routes", JsonArray()
                .plus("/actuator/health")
                .plus("/openid/v1/jwks")
            )
            .toString()
        )
    }

    router.get("/actuator/health").handler { ctx ->
      ctx.response()
        .setStatusCode(HttpResponseStatus.OK.code())
        .putHeader(CONTENT_TYPE, APPLICATION_JSON)
        .end(
          JsonObject()
            .put("ok", true)
            .toString()
        )
    }

    router.get("/openid/v1/jwks").handler {
      kubeClient.fetchJwks()
        .onSuccess { jwks ->
          it.response()
            .setStatusCode(HttpResponseStatus.OK.code())
            .putHeader(CONTENT_TYPE, APPLICATION_JSON)
            .end(jwks)
        }
        .onFailure { err ->
          log.error("Failed to fetch: {}", err.message, err)
          it.response()
            .setStatusCode(500)
            .putHeader(CONTENT_TYPE, APPLICATION_JSON)
            .end(
              JsonObject()
                .put("error", "Failed to fetch JWKS")
                .put("details", err.message)
                .toString()
            )
        }
    }

    router.get("/.well-known/openid-configuration").handler {
      kubeClient.fetchOpenIdConfiguration()
        .onSuccess { openidConfiguration ->
          openidConfiguration.put("jwks_uri", kubeClientProps.externalJwksUri)
          openidConfiguration.put(
            "claims_supported",
            JsonArray().add("sub").add("iss").add("aud").add("exp").add("iat")
          )

          it.response()
            .setStatusCode(HttpResponseStatus.OK.code())
            .putHeader(CONTENT_TYPE, APPLICATION_JSON)
            .end(openidConfiguration.toString())
        }
        .onFailure { err ->
          log.error("Failed to fetch: {}", err.message, err)

          it.response()
            .setStatusCode(500)
            .putHeader(CONTENT_TYPE, APPLICATION_JSON)
            .end(
              JsonObject()
                .put("error", "Failed to fetch JWKS")
                .put("details", err.message)
                .toString()
            )
        }
    }

    return server
      .requestHandler(router)
      .listen(httpProps.port)
      .onSuccess { log.info("HTTP server started on port {}", httpProps.port) }
      .onFailure { log.error("HTTP server failed to start", it) }
  }

  override fun stop(): Future<*> {
    log.debug("Stopping HTTP server")
    return server.shutdown()
  }
}
