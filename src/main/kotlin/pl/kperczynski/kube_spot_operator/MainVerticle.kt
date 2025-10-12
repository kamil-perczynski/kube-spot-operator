package pl.kperczynski.kube_spot_operator

import io.vertx.core.Deployable
import io.vertx.core.DeploymentOptions
import io.vertx.core.Future
import io.vertx.core.VerticleBase
import io.vertx.core.Vertx
import io.vertx.core.json.JsonObject
import pl.kperczynski.kube_spot_operator.config.readConfigMap
import pl.kperczynski.kube_spot_operator.logging.Slf4j
import java.util.function.Supplier

class MainVerticle() : VerticleBase() {

  companion object : Slf4j()

  override fun start(): Future<*> {
    log.info("Starting Kube-Spot-Operator application")

    val configmap = readConfigMapJson(vertx).map { readConfigMap(it) }

    return configmap.flatMap {
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

private const val CONFIGMAP_FILE_PATH = "./configmap.json"

fun readConfigMapJson(vertx: Vertx): Future<JsonObject> {
  return vertx.fileSystem().exists(CONFIGMAP_FILE_PATH)
    .compose { configmapExists ->
      if (!configmapExists) {
        return@compose vertx.executeBlocking {
          MainVerticle::class.java.classLoader.getResourceAsStream(CONFIGMAP_FILE_PATH)?.use { inputStream ->
            val configJson = JsonObject(inputStream.readAllBytes().toString(Charsets.UTF_8))
            return@use configJson
          }
        }
      }

      return@compose vertx.fileSystem().readFile(CONFIGMAP_FILE_PATH).map { buffer ->
        JsonObject(buffer.toString(Charsets.UTF_8))
      }
    }
}
