package pl.kperczynski.kube_spot_operator.domain

import io.vertx.core.Future
import io.vertx.core.Vertx
import org.slf4j.LoggerFactory
import pl.kperczynski.kube_spot_operator.kube.KubeClient
import pl.kperczynski.kube_spot_operator.libs.retryDecorator

private val log = LoggerFactory.getLogger(DrainNodeService::class.java)

class DrainNodeService(private val kubeClient: KubeClient, private val vertx: Vertx) {

  fun drainNode(nodeName: String): Future<Void> {
    return kubeClient.cordonNode(nodeName).compose {
      log.info("Node $nodeName cordoned")

      val podsEvictedDecor = retryDecorator<Void>(
        opName = "Await all pods evicted",
        vertx = vertx,
        log = log,
        times = arrayOf(3000L, 5000L, 10000L, 20000L, 20000L)
      )

      kubeClient.listNodePods(nodeName)
        .compose { pods ->
          val list = mutableListOf<Future<*>>()
          for (pod in pods.filter { isEvictionCandidate(it) }) {
            log.info("Evicting pod ${pod.namespace}/${pod.name}")
            val decor = retryDecorator<Void>(
              opName = "Evict ${pod.namespace}/${pod.name}",
              vertx = vertx,
              log = log
            )

            list.add(
              decor { kubeClient.evictPod(pod.name, pod.namespace) }
                .onSuccess { log.info("Pod ${pod.namespace}/${pod.name} evicted") }
                .recover {
                  log.info("Failed to evict pod ${pod.namespace}/${pod.name}: ${it.message}")
                  Future.succeededFuture()
                }
            )
          }

          Future.all<Any>(list)
        }
        .compose { podsEvictedDecor { awaitAllPodsEvicted(nodeName) } }
        .compose { kubeClient.listNodePods(nodeName) }
        .mapEmpty()
    }
  }

  fun awaitAllPodsEvicted(nodeName: String): Future<Void> {
    return kubeClient.listNodePods(nodeName)
      .compose { pods ->
        val evictablePods = pods.filter { isEvictionCandidate(it) }

        if (evictablePods.isNotEmpty()) {
          log.info(
            "Some pods were not evicted: {}",
            evictablePods.map { pod -> formatPodName(pod) }
          )
          Future.failedFuture(IllegalStateException("There are still ${evictablePods.size} pod(s) on node $nodeName"))
        } else {
          log.info("No evictable pods are running on node $nodeName")
          Future.succeededFuture()
        }
      }
  }

}

private fun formatPodName(pod: KubePod): String {
  return "${pod.namespace}/${pod.name}"
}

private fun isEvictionCandidate(pod: KubePod): Boolean {
  return pod.ownerKind != "DaemonSet"
}
