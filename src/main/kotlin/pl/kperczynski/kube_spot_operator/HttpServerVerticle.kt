package pl.kperczynski.kube_spot_operator

import io.netty.handler.codec.http.HttpResponseStatus
import io.netty.handler.codec.http.HttpResponseStatus.*
import io.vertx.core.Future
import io.vertx.core.VerticleBase
import io.vertx.core.http.HttpHeaders.CONTENT_TYPE
import io.vertx.core.http.HttpServer
import io.vertx.core.http.HttpServerResponse
import io.vertx.core.json.Json
import io.vertx.core.json.JsonArray
import io.vertx.core.json.JsonObject
import io.vertx.ext.web.Router
import io.vertx.ext.web.RoutingContext
import org.slf4j.Logger
import pl.kperczynski.kube_spot_operator.domain.ServiceOpIds.GET_JWKS
import pl.kperczynski.kube_spot_operator.domain.ServiceOpIds.GET_OPENID_CONFIG
import pl.kperczynski.kube_spot_operator.domain.ServiceOpIds.LIST_KUBE_NODES
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

    this.kubeClient = KubeClient(kubeHttpClient(vertx, kubeClientProps), vertx, kubeClientProps)

    router.get("/").handler {
      jsonResponse(
        response = it.response(),
        body = JsonObject()
          .put("service", "kube-spot-operator")
          .put("description", "Kubernetes operator for managing spot instances")
          .put(
            "routes", JsonArray()
              .plus("/actuator/health")
              .plus("/.well-known/openid-configuration")
              .plus("/openid/v1/jwks")
              .plus("/api/nodes")
          )
      )
    }

    router.get("/actuator/health").handler {
      jsonResponse(
        response = it.response(),
        body = JsonObject().put("ok", true)
      )
    }

    router.get("/openid/v1/jwks").handler {
      vertx.eventBus()
        .request<JsonObject>(GET_JWKS, null)
        .onSuccess { msg -> jsonResponse(it.response(), msg.body()) }
        .onFailure { err -> handleError(it, err, log) }
    }

    router.get("/api/nodes").handler {
      vertx.eventBus()
        .request<JsonObject>(LIST_KUBE_NODES, null)
        .onSuccess { msg -> jsonResponse(it.response(), msg.body()) }
        .onFailure { err -> handleError(it, err, log) }
    }

    router.get("/.well-known/openid-configuration").handler {
      vertx.eventBus()
        .request<JsonObject>(GET_OPENID_CONFIG, null)
        .onSuccess { msg -> jsonResponse(it.response(), msg.body()) }
        .onFailure { err -> handleError(it, err, log) }
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

private fun handleError(ctx: RoutingContext, err: Throwable, logger: Logger) {
  logger.error("Route {} failed: {}", ctx.request().path(), err.message, err)
  ctx.response()
    .setStatusCode(500)
    .putHeader(CONTENT_TYPE, APPLICATION_JSON)
    .end(
      JsonObject()
        .put("status", 500)
        .put("route", ctx.request().path())
        .put("error", err.javaClass.simpleName)
        .put("message", err.message)
        .toString()
    )
}

private fun jsonResponse(
  response: HttpServerResponse,
  body: Any,
  status: HttpResponseStatus = OK
) {
  response
    .setStatusCode(status.code())
    .putHeader(CONTENT_TYPE, APPLICATION_JSON)
    .end(Json.encode(body))
}
