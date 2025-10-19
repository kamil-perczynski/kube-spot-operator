package pl.kperczynski.kube_spot_operator.domain

import io.vertx.core.Future
import pl.kperczynski.kube_spot_operator.config.KubeNodeProps
import pl.kperczynski.kube_spot_operator.kube.KubeClient
import pl.kperczynski.kube_spot_operator.logging.Slf4j

class DeleteNodeService(private val kubeClient: KubeClient, private val kubeNodeProps: KubeNodeProps) {

  companion object : Slf4j()

  fun cleanupNodes(): Future<Void> {
    return kubeClient.listNodes().compose { allNodes ->
      val deletesToExecute = findNodesToDelete(allNodes)
        .filter { scheduledDelete -> scheduledDelete.executionerNode == kubeNodeProps.currentNodeName }

      val nodesToDelete = deletesToExecute.map { it.nodeName }
      log.info("Found nodes {} to be deleted node={}", nodesToDelete, kubeNodeProps.currentNodeName)

      executeDeletes(nodesToDelete)
    }
  }

  private fun executeDeletes(nodesToDelete: List<String>): Future<Void> {
    val futures = mutableListOf<Future<*>>()

    for (nodeToDelete in nodesToDelete) {
      log.info("Deleting node={}", nodeToDelete)

      val deleteNodeFuture = kubeClient.deleteNode(nodeToDelete)
        .onSuccess { log.info("Node {} deleted", nodeToDelete) }
        .recover {
          log.error("Failed to delete node {}", nodeToDelete, it)
          Future.succeededFuture()
        }

      futures.add(deleteNodeFuture)
    }

    return Future.all<Any>(futures).mapEmpty()
  }
}

fun findNodesToDelete(nodes: List<KubeNode>): List<ScheduledNodeDelete> {
  val nodesToDelete = nodes.filter { shouldNodeBeDeleted(it) }

  val result = mutableListOf<ScheduledNodeDelete>()

  for (node in nodesToDelete) {
    val indexOf = nodes.indexOf(node)

    if (indexOf < nodes.size) {
      val scheduledNodeDelete = findExecutingNodeFromIndex(indexOf, nodes, node)

      if (scheduledNodeDelete != null) {
        result.add(scheduledNodeDelete)
        continue
      }
    }

    if (indexOf > 0) {
      val scheduledNodeDelete = findExecutingNodeUpToIndex(indexOf, nodes, node)
      if (scheduledNodeDelete != null) {
        result.add(scheduledNodeDelete)
      }
    }
  }

  return result
}

private fun findExecutingNodeUpToIndex(indexOf: Int, nodes: List<KubeNode>, node: KubeNode): ScheduledNodeDelete? {
  for (i in (indexOf - 1)..0) {
    val candidateNode = nodes[i]

    if (isNodeReady(candidateNode)) {
      return ScheduledNodeDelete(nodeName = node.name, executionerNode = candidateNode.name)
    }
  }

  return null
}

private fun findExecutingNodeFromIndex(
  indexOf: Int,
  nodes: List<KubeNode>,
  node: KubeNode
): ScheduledNodeDelete? {
  for (i in (indexOf + 1) until nodes.size) {
    val candidateNode = nodes[i]

    if (isNodeReady(candidateNode)) {
      return ScheduledNodeDelete(
        nodeName = node.name,
        executionerNode = candidateNode.name
      )
    }
  }

  return null
}

private fun shouldNodeBeDeleted(node: KubeNode): Boolean {
  return !isNodeReady(node) && node.taints.contains("node.kubernetes.io/unschedulable")
}

private fun isNodeReady(candidateNode: KubeNode): Boolean = candidateNode.conditions.contains("Ready")

data class ScheduledNodeDelete(val nodeName: String, val executionerNode: String)

