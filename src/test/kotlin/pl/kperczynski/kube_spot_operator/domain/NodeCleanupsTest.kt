package pl.kperczynski.kube_spot_operator.domain

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import pl.kperczynski.kube_spot_operator.domain.model.KubeNode

class NodeCleanupsTest {

  @Test
  fun `should find node to delete`() {
    // given
    val nodes = listOf(
        KubeNode(
            name = "node-1",
            conditions = listOf(),
            taints = listOf("node.kubernetes.io/unschedulable")
        ),
        KubeNode(
            name = "node-2",
            conditions = listOf("Ready"),
            taints = listOf()
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
        nodeName = "node-1",
        executionerNode = "node-2"
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

  @Test
  fun `should find executioner node moving backwards`() {
    val nodes = listOf(
        KubeNode(
            name = "ip-10-46-101-99.eu-north-1.compute.internal",
            conditions = listOf("Ready"),
            taints = emptyList()
        ),
        KubeNode(
            name = "ip-10-46-102-40.eu-north-1.compute.internal",
            conditions = listOf("Ready"),
            taints = emptyList()
        ),
        KubeNode(
            name = "ip-10-46-103-173.eu-north-1.compute.internal",
            conditions = listOf("Ready"),
            taints = emptyList()
        ),
        KubeNode(
            name = "ip-10-46-103-44.eu-north-1.compute.internal",
            conditions = emptyList(),
            taints = listOf(
                "node.kubernetes.io/unschedulable",
                "node.kubernetes.io/unreachable",
                "node.kubernetes.io/unreachable"
            )
        )
    )

    // when
    val result = findNodesToDelete(nodes)

    // then
    assertThat(result).containsExactly(
      ScheduledNodeDelete(
        nodeName = "ip-10-46-103-44.eu-north-1.compute.internal",
        executionerNode = "ip-10-46-103-173.eu-north-1.compute.internal"
      )
    )
  }

}
