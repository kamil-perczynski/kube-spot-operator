package pl.kperczynski.kube_spot_operator.domain

import io.vertx.core.Future
import io.vertx.core.json.JsonObject
import pl.kperczynski.kube_spot_operator.domain.model.KubeNode
import pl.kperczynski.kube_spot_operator.domain.model.KubePod
import pl.kperczynski.kube_spot_operator.kube.CordonResult

interface KubeClient {

  fun fetchJwks(): Future<JsonObject>
  fun fetchOpenIdConfiguration(): Future<JsonObject>
  fun listNodes(): Future<List<KubeNode>>
  fun cordonNode(nodeName: String): Future<CordonResult>
  fun evictPod(podName: String, namespace: String): Future<Void>
  fun listNodePods(nodeId: String): Future<List<KubePod>>
  fun deleteNode(nodeName: String): Future<Void>

}
