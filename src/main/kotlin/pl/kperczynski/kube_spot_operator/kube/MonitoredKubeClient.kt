package pl.kperczynski.kube_spot_operator.kube

import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Tag
import io.micrometer.core.instrument.Timer
import io.vertx.core.Future
import io.vertx.core.json.JsonObject
import pl.kperczynski.kube_spot_operator.domain.KubeClient
import pl.kperczynski.kube_spot_operator.domain.model.KubeNode
import pl.kperczynski.kube_spot_operator.domain.model.KubePod

class MonitoredKubeClient(
  private val delegate: KubeClient,
  private val meterRegistry: MeterRegistry
) : KubeClient {

  override fun fetchJwks(): Future<JsonObject> {
    return captureTimer("fetchJwks") { delegate.fetchJwks() }
  }

  override fun fetchOpenIdConfiguration(): Future<JsonObject> {
    return captureTimer("fetchOpenIdConfiguration") { delegate.fetchOpenIdConfiguration() }
  }

  override fun listNodes(): Future<List<KubeNode>> {
    return captureTimer("listNodes") { delegate.listNodes() }
  }

  override fun cordonNode(nodeName: String): Future<CordonResult> {
    return captureTimer("cordonNode") { delegate.cordonNode(nodeName) }
  }

  override fun evictPod(podName: String, namespace: String): Future<Void> {
    return captureTimer("evictPod") { delegate.evictPod(podName, namespace) }
  }

  override fun listNodePods(nodeId: String): Future<List<KubePod>> {
    return captureTimer("listNodePods") { delegate.listNodePods(nodeId) }
  }

  override fun deleteNode(nodeName: String): Future<Void> {
    return captureTimer("deleteNode") { delegate.deleteNode(nodeName) }
  }

  fun <T> captureTimer(operation: String, fn: () -> Future<T>): Future<T> {
    val sample = Timer.start(meterRegistry)

    return fn().onComplete(
      {
        val timer = meterRegistry.timer(
          "kube.client.ops",
          listOf<Tag>(Tag.of("operation", operation), Tag.of("status", "success"), Tag.of("exception", "none"))
        )
        sample.stop(timer)
      },
      { ex ->
        val timer = meterRegistry.timer(
          "kube.client.ops",
          listOf<Tag>(
            Tag.of("operation", operation),
            Tag.of("status", "failure"),
            Tag.of("exception", ex.javaClass.simpleName)
          )
        )

        sample.stop(timer)
      }
    )
  }
}
