package pl.kperczynski.kube_spot_operator.kube

import io.vertx.core.Future
import io.vertx.core.json.JsonObject
import pl.kperczynski.kube_spot_operator.domain.KubeNode
import pl.kperczynski.kube_spot_operator.domain.KubePod

interface KubeClient {

  fun fetchJwks(): Future<JsonObject>
  fun fetchOpenIdConfiguration(): Future<JsonObject>
  fun listNodes(): Future<List<KubeNode>>
  fun cordonNode(nodeName: String): Future<CordonResult>
  fun evictPod(podName: String, namespace: String): Future<Void>
  fun listNodePods(nodeId: String): Future<List<KubePod>>
  fun deleteNode(nodeName: String): Future<Void>

}
