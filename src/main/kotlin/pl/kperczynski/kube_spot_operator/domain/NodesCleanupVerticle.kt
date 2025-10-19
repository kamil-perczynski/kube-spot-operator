package pl.kperczynski.kube_spot_operator.domain

import io.vertx.core.Future
import io.vertx.core.VerticleBase
import pl.kperczynski.kube_spot_operator.domain.ServiceOpIds.CLEANUP_NODES
import pl.kperczynski.kube_spot_operator.kube.KubeClient
import pl.kperczynski.kube_spot_operator.kube.KubeClientProps
import pl.kperczynski.kube_spot_operator.kube.kubeHttpClient
import pl.kperczynski.kube_spot_operator.logging.Slf4j

private const val TWO_MINUTES = 2 * 60 * 1000L

class NodesCleanupVerticle(private val kubeClientProps: KubeClientProps) : VerticleBase() {

  companion object : Slf4j()

  private lateinit var kubeClient: KubeClient

  override fun start(): Future<*>? {
    this.kubeClient = KubeClient(kubeHttpClient(vertx, kubeClientProps), vertx, kubeClientProps)

    val bus = vertx.eventBus()

    vertx.setPeriodic(TWO_MINUTES) {
      kubeClient.listNodes().onSuccess { nodes ->
        val nodesToDelete = nodes.filter { shouldNodeBeDeleted(it) }

        if (nodesToDelete.isNotEmpty()) {
          log.info("Detected tainted nodes needing cleanup: {}", nodesToDelete.map { it.name })
          bus.send(CLEANUP_NODES, CleanupNodesInput(nodes))
        }
      }
    }

    return Future.succeededFuture<Void>()
  }
}
