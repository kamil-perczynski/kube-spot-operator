package pl.kperczynski.kube_spot_operator

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import io.vertx.core.Deployable
import io.vertx.core.DeploymentOptions
import io.vertx.core.Future
import io.vertx.core.VerticleBase
import io.vertx.core.Vertx
import io.vertx.core.buffer.Buffer
import io.vertx.core.eventbus.MessageCodec
import io.vertx.core.json.JsonObject
import io.vertx.core.json.jackson.DatabindCodec
import pl.kperczynski.kube_spot_operator.config.ConfigMap
import pl.kperczynski.kube_spot_operator.config.parseConfigMap
import pl.kperczynski.kube_spot_operator.domain.KubeClientVerticle
import pl.kperczynski.kube_spot_operator.logging.Slf4j
import java.util.function.Supplier

class MainVerticle() : VerticleBase() {

  lateinit var configmap: ConfigMap

  companion object : Slf4j()

  override fun start(): Future<*> {
    log.info("Starting Kube-Spot-Operator application")

    val configmapFuture = bootstrapConfig(vertx)
      .onSuccess { cm -> this.configmap = cm }

    return configmapFuture
      .flatMap { configMap ->
        vertx
          .deployVerticle(
            Supplier<Deployable> { KubeClientVerticle(configMap.kubeClient) },
            DeploymentOptions().setInstances(2)
          )
          .flatMap {
            vertx.deployVerticle(
              Supplier<Deployable> { HttpServerVerticle(configMap.httpServer, configMap.kubeClient) },
              DeploymentOptions().setInstances(1)
            )
          }
      }
      .onFailure {
        log.error("Failed to start MainVerticle", it)
      }
  }
}

fun bootstrapConfig(vertx: Vertx): Future<ConfigMap> {
  vertx.eventBus().registerDefaultCodec(Object::class.java, DirectMessageCodec())
  vertx.eventBus().codecSelector { DirectMessageCodec.DIRECT_CODEC_NAME }

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

class DirectMessageCodec : MessageCodec<Object, Object> {

  companion object {
    const val DIRECT_CODEC_NAME = "Direct"
  }

  override fun encodeToWire(buffer: Buffer?, s: Object?) {
    throw UnsupportedOperationException("Not implemented")
  }

  override fun decodeFromWire(pos: Int, buffer: Buffer?): Object {
    throw UnsupportedOperationException("Not implemented")
  }

  override fun transform(s: Object?): Object {
    return s as Object
  }

  override fun name(): String {
    return DIRECT_CODEC_NAME
  }

  override fun systemCodecID(): Byte {
    return -1
  }
}
