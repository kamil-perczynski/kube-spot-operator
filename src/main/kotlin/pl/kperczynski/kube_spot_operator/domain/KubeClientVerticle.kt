package pl.kperczynski.kube_spot_operator.domain

import io.vertx.core.Future
import io.vertx.core.Handler
import io.vertx.core.VerticleBase
import io.vertx.core.eventbus.Message
import io.vertx.core.json.JsonArray
import io.vertx.core.json.JsonObject
import org.slf4j.Logger
import pl.kperczynski.kube_spot_operator.config.KubeNodeProps
import pl.kperczynski.kube_spot_operator.domain.EventIds.NODE_TERMINATION_SCHEDULED
import pl.kperczynski.kube_spot_operator.domain.ServiceOpIds.CLEANUP_NODES
import pl.kperczynski.kube_spot_operator.domain.ServiceOpIds.DRAIN_KUBE_NODE
import pl.kperczynski.kube_spot_operator.domain.ServiceOpIds.GET_JWKS
import pl.kperczynski.kube_spot_operator.domain.ServiceOpIds.GET_OPENID_CONFIG
import pl.kperczynski.kube_spot_operator.domain.model.CleanupNodesInput
import pl.kperczynski.kube_spot_operator.domain.model.DrainNodeInput
import pl.kperczynski.kube_spot_operator.domain.model.NodeTerminationScheduledInput
import pl.kperczynski.kube_spot_operator.kube.KubeClientProps
import pl.kperczynski.kube_spot_operator.libs.RecipientException
import pl.kperczynski.kube_spot_operator.logging.Slf4j

class KubeClientVerticle(
  private val kubeClientProps: KubeClientProps,
  private val kubeNodeProps: KubeNodeProps
) : VerticleBase() {

  companion object : Slf4j()

  private lateinit var kubeClient: KubeClient
  private lateinit var drainNodeService: DrainNodeService
  private lateinit var deleteNodeService: DeleteNodeService

  override fun start(): Future<*> {
    this.kubeClient = monitoredKubeClient(vertx, kubeClientProps)
    this.drainNodeService = DrainNodeService(kubeClient, vertx)
    this.deleteNodeService = DeleteNodeService(kubeClient, kubeNodeProps)

    val bus = vertx.eventBus()

    val getJwksConsumer = bus.localConsumer(GET_JWKS) { msg ->
      log.debug("Received request for JWKS")

      kubeClient.fetchJwks()
        .onSuccess { jwks -> msg.reply(jwks) }
        .onFailure(consumerErrorHandler(msg, log))
    }

    val getOpenIdConfigurationConsumer = bus.localConsumer(GET_OPENID_CONFIG) { msg ->
      log.debug("Received request for OpenID Configuration")

      kubeClient.fetchOpenIdConfiguration()
        .onSuccess { kubeOpenIdConfig ->
          val openidConfiguration = toOpenIdConfiguration(
            kubeOpenIdConfig = kubeOpenIdConfig,
            externalJwksUri = kubeClientProps.externalJwksUri
          )

          msg.reply(openidConfiguration)
        }
        .onFailure(consumerErrorHandler(msg, log))
    }

    val drainKubeNodeConsumer = bus.localConsumer<DrainNodeInput>(DRAIN_KUBE_NODE) { msg ->
      val input = msg.body()

      log.debug("Received request to drain node: {}", input.nodeId)

      drainNodeService.drainNode(input.nodeId)
        .onSuccess { msg.reply(JsonObject().put("status", "Node ${input.nodeId} drained successfully")) }
        .onFailure(consumerErrorHandler(msg, log))
    }

    val nodeTerminationConsumer = bus.localConsumer<NodeTerminationScheduledInput>(NODE_TERMINATION_SCHEDULED) { msg ->
      val input = msg.body()

      log.debug("Received node termination scheduled event for node: {}. Draining the node...", input.nodeId)

      drainNodeService.drainNode(input.nodeId)
        .onSuccess { log.info("Node {} drained successfully after termination scheduled event", input.nodeId) }
        .onFailure(consumerErrorHandler(msg, log))
    }

    val cleanupNodesConsumer = bus.localConsumer<CleanupNodesInput>(CLEANUP_NODES) { msg ->
      val input = msg.body()
      deleteNodeService.cleanupNodes(nodes = input.nodes)
    }

    return Future.all<Any>(
      listOf(
        getJwksConsumer.completion(),
        getOpenIdConfigurationConsumer.completion(),
        drainKubeNodeConsumer.completion(),
        nodeTerminationConsumer.completion(),
        cleanupNodesConsumer.completion()
      )
    )
  }

}

private fun toOpenIdConfiguration(kubeOpenIdConfig: JsonObject, externalJwksUri: String): JsonObject {
  val openidConfiguration = kubeOpenIdConfig.copy()

  openidConfiguration.put("jwks_uri", externalJwksUri)
  openidConfiguration.put(
    "claims_supported",
    JsonArray().add("sub").add("iss").add("aud").add("exp").add("iat")
  )
  return openidConfiguration
}

private fun <T : Any> consumerErrorHandler(msg: Message<T>, log: Logger): Handler<in Throwable> {
  return Handler { ex ->
    log.error("Operation failed", ex)
    msg.reply(RecipientException("Recipient ${msg.address()} operation failed", ex))
  }
}


