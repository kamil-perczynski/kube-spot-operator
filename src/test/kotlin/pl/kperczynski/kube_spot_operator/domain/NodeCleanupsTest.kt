package pl.kperczynski.kube_spot_operator.domain

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class NodeCleanupsTest {

  @Test
  fun `should find node to delete`() {
    // given
    val nodes = listOf(
      KubeNode(
        name = "node-1",
        conditions = listOf("Ready"),
        taints = emptyList()
      ),
      KubeNode(
        name = "node-2",
        conditions = emptyList(),
        taints = listOf("node.kubernetes.io/unschedulable")
      ),
      KubeNode(
        name = "node-3",
        conditions = listOf("Ready"),
        taints = emptyList()
      ),
      KubeNode(
        name = "node-4",
        conditions = listOf("Ready"),
        taints = emptyList()
      )
    )

    // when
    val result = findNodesToDelete(nodes)

    // then
    assertThat(result).containsExactly(
      ScheduledNodeDelete(
        nodeName = "node-2",
        executionerNode = "node-3"
      )
    )
  }

  @Test
  fun `find nodes to delete when multiple are not ready`() {
    // given
    val nodes = listOf(
      KubeNode(
        name = "node-1",
        conditions = listOf("Ready"),
        taints = emptyList()
      ),
      KubeNode(
        name = "node-2",
        conditions = emptyList(),
        taints = listOf("node.kubernetes.io/unschedulable")
      ),
      KubeNode(
        name = "node-3",
        conditions = emptyList(),
        taints = listOf("node.kubernetes.io/unschedulable")
      ),
      KubeNode(
        name = "node-4",
        conditions = listOf("Ready"),
        taints = emptyList()
      )
    )

    // when
    val result = findNodesToDelete(nodes)

    // then
    assertThat(result).containsExactly(
      ScheduledNodeDelete(
        nodeName = "node-2",
        executionerNode = "node-4"
      ),
      ScheduledNodeDelete(
        nodeName = "node-3",
        executionerNode = "node-4"
      )
    )
  }

  @Test
  fun `should not find nodes to delete when all nodes are ready`() {
    // given
    val nodes = listOf(
      KubeNode(
        name = "node-1",
        conditions = listOf("Ready"),
        taints = emptyList()
      ),
      KubeNode(
        name = "node-2",
        conditions = listOf("Ready"),
        taints = emptyList()
      )
    )

    // when
    val result = findNodesToDelete(nodes)

    // then
    assertThat(result).isEmpty()
  }

  @Test
  fun `should find executioner node from beginning when no ready nodes after unschedulable node`() {
    // given
    val nodes = listOf(
      KubeNode(
        name = "node-1",
        conditions = listOf("Ready"),
        taints = emptyList()
      ),
      KubeNode(
        name = "node-2",
        conditions = emptyList(),
        taints = listOf("node.kubernetes.io/unschedulable")
      ),
      KubeNode(
        name = "node-3",
        conditions = emptyList(),
        taints = emptyList()
      )
    )

    // when
    val result = findNodesToDelete(nodes)

    // then
    assertThat(result).containsExactly(
      ScheduledNodeDelete(
        nodeName = "node-2",
        executionerNode = "node-1"
      )
    )
  }

  @Test
  fun `should not find nodes to delete when node is not ready but not unschedulable`() {
    // given
    val nodes = listOf(
      KubeNode(
        name = "node-1",
        conditions = listOf("Ready"),
        taints = emptyList()
      ),
      KubeNode(
        name = "node-2",
        conditions = emptyList(),
        taints = emptyList()
      )
    )

    // when
    val result = findNodesToDelete(nodes)

    // then
    assertThat(result).isEmpty()
  }
}
