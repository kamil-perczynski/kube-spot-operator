package pl.kperczynski.kube_spot_operator.kube

import io.vertx.core.Vertx
import io.vertx.junit5.VertxExtension
import io.vertx.junit5.VertxTestContext
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.tuple
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import pl.kperczynski.kube_spot_operator.bootstrapConfig
import pl.kperczynski.kube_spot_operator.config.ConfigMap

@ExtendWith(VertxExtension::class)
class KubeClientTest {

  companion object {

    private lateinit var configmap: ConfigMap
    private lateinit var kubeStubs: KubeApiStubs
    private lateinit var kubeClient: KubeClient

    @BeforeAll
    @JvmStatic
    fun setUp(vertx: Vertx, ctx: VertxTestContext) {
      val kubeWiremock = WiremockVerticle("Kube", 29024)

      bootstrapConfig(vertx)
        .onSuccess { configmap = it }
        .compose { vertx.deployVerticle(kubeWiremock) }
        .onSuccess {
          kubeStubs = KubeApiStubs(vertx, kubeWiremock.wiremock)
        }
        .onSuccess {
          kubeClient = KubeClient(
            kubeHttpClient(vertx, configmap.kubeClient),
            vertx,
            configmap.kubeClient
          )
        }
        .onComplete(ctx.succeedingThenComplete())
    }
  }

  @BeforeEach
  fun setUp() {
    kubeStubs.resetAll()
  }

  @Test
  fun testListNodes(ctx: VertxTestContext) {
    // given:
    val stubFuture = kubeStubs.stubListNodes()

    // when:
    val listNodes = stubFuture.compose { kubeClient.listNodes() }

    // then:
    listNodes.map { nodes ->
      assertThat(nodes)
        .extracting({ it.name }, { it.conditions }, { it.taints })
        .containsExactly(
          tuple(
            "ip-10-46-101-78.eu-north-1.compute.internal",
            listOf("Ready"),
            emptyList<String>()
          ),
          tuple(
            "ip-10-46-102-33.eu-north-1.compute.internal",
            listOf("Ready"),
            listOf("node.kubernetes.io/unschedulable")
          ),
          tuple(
            "ip-10-46-103-245.eu-north-1.compute.internal",
            listOf("Ready"),
            emptyList<String>()
          )
        )
    }.onComplete({ ctx.completeNow() }, ctx::failNow)
  }

  @Test
  fun testDrainNode(ctx: VertxTestContext) {
    val nodeId = "ip-10-46-102-33.eu-north-1.compute.internal"

    val stubCordonNode = kubeStubs.stubCordonNode(nodeId)

    val cordonNode = stubCordonNode.compose { kubeClient.cordonNode(nodeId) }

    cordonNode
      .onSuccess { json ->
        assertThat(json).isEqualTo(CordonResult.CORDONED)
      }
      .onComplete({ ctx.completeNow() }, ctx::failNow)
  }
}
