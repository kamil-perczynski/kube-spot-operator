package pl.kperczynski.kube_spot_operator.domain

import io.vertx.core.Future
import io.vertx.core.VerticleBase
import io.vertx.core.Vertx
import pl.kperczynski.kube_spot_operator.config.KubeNodeProps
import pl.kperczynski.kube_spot_operator.domain.ServiceOpIds.CLEANUP_NODES
import pl.kperczynski.kube_spot_operator.domain.model.CleanupNodesInput
import pl.kperczynski.kube_spot_operator.kube.KubeClientProps
import pl.kperczynski.kube_spot_operator.logging.Slf4j

private const val TWO_MINUTES = 2 * 60 * 1000L

class KubeNodesCleanupVerticle(
  private val kubeClientProps: KubeClientProps,
  private val kubeNodeProps: KubeNodeProps
) : VerticleBase() {

  companion object : Slf4j()

  private lateinit var kubeClient: KubeClient

  override fun start(): Future<*>? {
    this.kubeClient = monitoredKubeClient(vertx, kubeClientProps)

    if (kubeNodeProps.enableAutomaticNodeCleanup) {
      log.info("Starting automatic node cleanup every {} ms", TWO_MINUTES)

      vertx.setPeriodic(TWO_MINUTES) {
        automaticNodeCleanup(vertx)
      }
    }

    return Future.succeededFuture<Void>()
  }

  fun automaticNodeCleanup(vertx: Vertx) {
    val bus = vertx.eventBus()

    kubeClient.listNodes().onSuccess { nodes ->
      val nodesToDelete = nodes.filter { isUnschedulableAndUnreachable(it) }

      if (nodesToDelete.isNotEmpty()) {
        log.info("Detected tainted nodes needing cleanup: {}", nodesToDelete.map { it.name })
        bus.send(CLEANUP_NODES, CleanupNodesInput(nodes))
      }
    }
  }
}
