package pl.kperczynski.kube_spot_operator

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import io.vertx.core.Deployable
import io.vertx.core.DeploymentOptions
import io.vertx.core.Future
import io.vertx.core.VerticleBase
import io.vertx.core.Vertx
import io.vertx.core.json.JsonObject
import io.vertx.core.json.jackson.DatabindCodec
import pl.kperczynski.kube_spot_operator.config.ConfigMap
import pl.kperczynski.kube_spot_operator.config.parseConfigMap
import pl.kperczynski.kube_spot_operator.logging.Slf4j
import java.util.function.Supplier

class MainVerticle() : VerticleBase() {

  lateinit var configmap: ConfigMap

  companion object : Slf4j()

  override fun start(): Future<*> {
    log.info("Starting Kube-Spot-Operator application")

    val configmap = bootstrapConfig(vertx)
      .onSuccess { cm -> this.configmap = cm }

    return configmap
      .flatMap {
        vertx.deployVerticle(
          Supplier<Deployable> { HttpServerVerticle(it.httpServer, it.kubeClient) },
          DeploymentOptions().setInstances(1)
        )
      }
      .onFailure {
        log.error("Failed to start MainVerticle", it)
      }
  }
}

fun bootstrapConfig(vertx: Vertx): Future<ConfigMap> {
  val objectMapper = DatabindCodec.mapper()
  objectMapper.registerModule(JavaTimeModule())
  objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
  objectMapper.configure(DeserializationFeature.FAIL_ON_MISSING_CREATOR_PROPERTIES, false)

  return readConfigMapJson(vertx)
    .map { parseConfigMap(it) }
}

private const val CONFIGMAP_FILE_PATH = "./configmap.json"

fun readConfigMapJson(vertx: Vertx): Future<JsonObject> {
  return vertx.fileSystem().readFile(CONFIGMAP_FILE_PATH).map { buffer ->
    JsonObject(buffer.toString(Charsets.UTF_8))
  }
}
