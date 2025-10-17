package pl.kperczynski.kube_spot_operator.domain

import io.vertx.core.Future
import io.vertx.core.Handler
import io.vertx.core.VerticleBase
import io.vertx.core.eventbus.Message
import io.vertx.core.json.JsonArray
import io.vertx.core.json.JsonObject
import org.slf4j.Logger
import pl.kperczynski.kube_spot_operator.domain.EventIds.NODE_TERMINATION_SCHEDULED
import pl.kperczynski.kube_spot_operator.domain.ServiceOpIds.DRAIN_KUBE_NODE
import pl.kperczynski.kube_spot_operator.domain.ServiceOpIds.GET_JWKS
import pl.kperczynski.kube_spot_operator.domain.ServiceOpIds.GET_OPENID_CONFIG
import pl.kperczynski.kube_spot_operator.domain.ServiceOpIds.LIST_KUBE_NODES
import pl.kperczynski.kube_spot_operator.domain.ServiceOpIds.LIST_NODE_PODS
import pl.kperczynski.kube_spot_operator.kube.KubeClient
import pl.kperczynski.kube_spot_operator.kube.KubeClientProps
import pl.kperczynski.kube_spot_operator.kube.kubeHttpClient
import pl.kperczynski.kube_spot_operator.libs.RecipientException
import pl.kperczynski.kube_spot_operator.logging.Slf4j

class KubeClientVerticle(private val kubeClientProps: KubeClientProps) : VerticleBase() {

  companion object : Slf4j()

  private lateinit var kubeClient: KubeClient
  private lateinit var drainNodeService: DrainNodeService

  override fun start(): Future<*> {
    this.kubeClient = KubeClient(kubeHttpClient(vertx, kubeClientProps), vertx, kubeClientProps)
    this.drainNodeService = DrainNodeService(kubeClient, vertx)

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

    val listKubernetesNodesConsumer = bus.localConsumer(LIST_KUBE_NODES) { msg ->
      log.debug("Received request for listing Kubernetes nodes")

      kubeClient.listNodes()
        .onSuccess { nodesList -> msg.reply(nodesList) }
        .onFailure(consumerErrorHandler(msg, log))
    }

    val listNodePodsConsumer = bus.localConsumer<ListPodsInput>(LIST_NODE_PODS) { msg ->
      val input = msg.body()

      log.debug("Received request for listing pods on node: {}", input.nodeId)

      kubeClient.listNodePods(input.nodeId)
        .onSuccess { podsList -> msg.reply(podsList) }
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

    return Future.all(
      getJwksConsumer.completion(),
      getOpenIdConfigurationConsumer.completion(),
      listKubernetesNodesConsumer.completion(),
      listNodePodsConsumer.completion(),
      drainKubeNodeConsumer.completion(),
      nodeTerminationConsumer.completion()
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


