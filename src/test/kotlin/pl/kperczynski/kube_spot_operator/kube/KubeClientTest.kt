package pl.kperczynski.kube_spot_operator.kube

import com.github.tomakehurst.wiremock.stubbing.Scenario
import io.vertx.core.Future
import io.vertx.core.Promise
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
import pl.kperczynski.kube_spot_operator.domain.DrainNodeService
import pl.kperczynski.kube_spot_operator.domain.KubePod

@ExtendWith(VertxExtension::class)
class KubeClientTest {

  companion object {

    private lateinit var drainNodeService: DrainNodeService
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
          drainNodeService = DrainNodeService(kubeClient, vertx)
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
  fun testListNodePods(ctx: VertxTestContext) {
    // given:
    val nodeName = "ip-10-46-102-33.eu-north-1.compute.internal"
    val stubFuture = kubeStubs.stubListPodsOnNode(nodeName)

    // when:
    val listNodePods = stubFuture.compose { kubeClient.listNodePods(nodeName) }

    // then:
    listNodePods.onSuccess { pods ->
      assertThat(pods).containsExactly(
        KubePod(
          name = "argocd-application-controller-0",
          namespace = "argocd",
          phase = "Running",
          ownerKind = "StatefulSet",
          ownerName = "argocd-application-controller",
          hasEmptyDirVolume = true
        ),
        KubePod(
          name = "argocd-applicationset-controller-549cbdb686-8h59z",
          namespace = "argocd",
          phase = "Running",
          ownerKind = "ReplicaSet",
          ownerName = "argocd-applicationset-controller-549cbdb686",
          hasEmptyDirVolume = true
        ),
        KubePod(
          name = "argocd-dex-server-78c775dd69-gg4hd",
          namespace = "argocd",
          phase = "Running",
          ownerKind = "ReplicaSet",
          ownerName = "argocd-dex-server-78c775dd69",
          hasEmptyDirVolume = true
        ),
        KubePod(
          name = "argocd-notifications-controller-7d87d96cc4-f7mpd",
          namespace = "argocd",
          phase = "Running",
          ownerKind = "ReplicaSet",
          ownerName = "argocd-notifications-controller-7d87d96cc4",
          hasEmptyDirVolume = false
        ),
        KubePod(
          name = "argocd-repo-server-5c4b778556-xjrm5",
          namespace = "argocd",
          phase = "Running",
          ownerKind = "ReplicaSet",
          ownerName = "argocd-repo-server-5c4b778556",
          hasEmptyDirVolume = true
        ),
        KubePod(
          name = "argocd-server-57897b89bd-h4gjv",
          namespace = "argocd",
          phase = "Running",
          ownerKind = "ReplicaSet",
          ownerName = "argocd-server-57897b89bd",
          hasEmptyDirVolume = true
        ),
        KubePod(
          name = "cert-mgr-dev-cert-manager-59664d9678-x8kbt",
          namespace = "infra-dev",
          phase = "Running",
          ownerKind = "ReplicaSet",
          ownerName = "cert-mgr-dev-cert-manager-59664d9678",
          hasEmptyDirVolume = false
        ),
        KubePod(
          name = "cert-mgr-dev-cert-manager-webhook-589c8f79d7-b7sv2",
          namespace = "infra-dev",
          phase = "Running",
          ownerKind = "ReplicaSet",
          ownerName = "cert-mgr-dev-cert-manager-webhook-589c8f79d7",
          hasEmptyDirVolume = false
        ),
        KubePod(
          name = "grafana-monitoring-dev-alloy-logs-tmc5k",
          namespace = "infra-dev",
          phase = "Running",
          ownerKind = "DaemonSet",
          ownerName = "grafana-monitoring-dev-alloy-logs",
          hasEmptyDirVolume = false
        ),
        KubePod(
          name = "grafana-monitoring-dev-alloy-metrics-0",
          namespace = "infra-dev",
          phase = "Running",
          ownerKind = "StatefulSet",
          ownerName = "grafana-monitoring-dev-alloy-metrics",
          hasEmptyDirVolume = false
        ),
        KubePod(
          name = "grafana-monitoring-dev-alloy-receiver-p4w2l",
          namespace = "infra-dev",
          phase = "Running",
          ownerKind = "DaemonSet",
          ownerName = "grafana-monitoring-dev-alloy-receiver",
          hasEmptyDirVolume = false
        ),
        KubePod(
          name = "grafana-monitoring-dev-alloy-singleton-55658b9c6b-b6nzl",
          namespace = "infra-dev",
          phase = "Running",
          ownerKind = "ReplicaSet",
          ownerName = "grafana-monitoring-dev-alloy-singleton-55658b9c6b",
          hasEmptyDirVolume = false
        ),
        KubePod(
          name = "grafana-monitoring-dev-kube-state-metrics-67f8bbc785-tw8jg",
          namespace = "infra-dev",
          phase = "Running",
          ownerKind = "ReplicaSet",
          ownerName = "grafana-monitoring-dev-kube-state-metrics-67f8bbc785",
          hasEmptyDirVolume = false
        ),
        KubePod(
          name = "grafana-monitoring-dev-node-exporter-rdr8k",
          namespace = "infra-dev",
          phase = "Running",
          ownerKind = "DaemonSet",
          ownerName = "grafana-monitoring-dev-node-exporter",
          hasEmptyDirVolume = false
        ),
        KubePod(
          name = "kube-spot-operator-6gds2",
          namespace = "infra-dev",
          phase = "Running",
          ownerKind = "DaemonSet",
          ownerName = "kube-spot-operator",
          hasEmptyDirVolume = false
        ),
        KubePod(
          name = "coredns-64fd4b4794-5hwdm",
          namespace = "kube-system",
          phase = "Running",
          ownerKind = "ReplicaSet",
          ownerName = "coredns-64fd4b4794",
          hasEmptyDirVolume = false
        ),
        KubePod(
          name = "local-path-provisioner-774c6665dc-4c8zr",
          namespace = "kube-system",
          phase = "Running",
          ownerKind = "ReplicaSet",
          ownerName = "local-path-provisioner-774c6665dc",
          hasEmptyDirVolume = false
        ),
        KubePod(
          name = "metrics-server-7bfffcd44-6nn6v",
          namespace = "kube-system",
          phase = "Running",
          ownerKind = "ReplicaSet",
          ownerName = "metrics-server-7bfffcd44",
          hasEmptyDirVolume = true
        ),
        KubePod(
          name = "svclb-traefik-7aa1c6d0-rfdl8",
          namespace = "kube-system",
          phase = "Running",
          ownerKind = "DaemonSet",
          ownerName = "svclb-traefik-7aa1c6d0",
          hasEmptyDirVolume = false
        )
      )
    }.onComplete({ ctx.completeNow() }, ctx::failNow)
  }

  @Test
  fun testListNodesError(ctx: VertxTestContext) {
    // given:
    val stubFuture = kubeStubs.stubListNodesError()

    // when:
    val listNodes = stubFuture.compose { kubeClient.listNodes() }

    // then:
    listNodes
      .onSuccess {
        ctx.failNow(IllegalStateException("Expected failure but got success with $it"))
      }
      .recover { err ->
        assertThat(err)
          .isInstanceOf(KubeClientException::class.java)
          .hasMessageContaining("Call GET /api/v1/nodes returned status=401")

        Future.succeededFuture()
      }
      .onComplete({ ctx.completeNow() }, ctx::failNow)
  }

  @Test
  fun testCordonNode(ctx: VertxTestContext) {
    val nodeName = "ip-10-46-102-33.eu-north-1.compute.internal"

    val stubCordonNode = kubeStubs.stubCordonNode(nodeName)

    val cordonNode = stubCordonNode.compose { kubeClient.cordonNode(nodeName) }

    cordonNode
      .onSuccess { json ->
        assertThat(json).isEqualTo(CordonResult.CORDONED)
      }
      .onComplete({ ctx.completeNow() }, ctx::failNow)
  }

  @Test
  fun testEvictPod(ctx: VertxTestContext) {
    // given:
    val podName = "kroplowa-fe-lp-847dbc77f6-gwvgc"
    val namespace = "apps-prod"

    val stubEvictPod = kubeStubs.stubEvictPod(podName, namespace)

    // when:
    val evictPod = stubEvictPod.compose { kubeClient.evictPod(podName, namespace) }

    // then:
    evictPod.onComplete({ ctx.completeNow() }, ctx::failNow)
  }

  @Test
  fun testDrainNode(ctx: VertxTestContext) {
    // given:
    val nodeName = "ip-10-46-103-245.eu-north-1.compute.internal"

    val stubs = kubeStubs
      .stubListPodsOnNode(nodeName) {
        it.inScenario("Drain node")
          .whenScenarioStateIs(Scenario.STARTED)
          .willSetStateTo("All pods evicted")
      }
      .compose { kubeStubs.stubCordonNode(nodeName) }
      .compose { kubeStubs.stubEvictPod("argocd-application-controller-0", "argocd") }
      .compose { kubeStubs.stubEvictPod("argocd-applicationset-controller-549cbdb686-8h59z", "argocd") }
      .compose { kubeStubs.stubEvictPod("argocd-dex-server-78c775dd69-gg4hd", "argocd") }
      .compose { kubeStubs.stubEvictPod("argocd-notifications-controller-7d87d96cc4-f7mpd", "argocd") }
      .compose { kubeStubs.stubEvictPod("argocd-repo-server-5c4b778556-xjrm5", "argocd") }
      .compose { kubeStubs.stubEvictPod("argocd-server-57897b89bd-h4gjv", "argocd") }
      .compose { kubeStubs.stubEvictPod("cert-mgr-dev-cert-manager-59664d9678-x8kbt", "infra-dev") }
      .compose { kubeStubs.stubEvictPod("cert-mgr-dev-cert-manager-webhook-589c8f79d7-b7sv2", "infra-dev") }
      .compose { kubeStubs.stubEvictPod("grafana-monitoring-dev-alloy-metrics-0", "infra-dev") }
      .compose { kubeStubs.stubEvictPod("grafana-monitoring-dev-alloy-singleton-55658b9c6b-b6nzl", "infra-dev") }
      .compose { kubeStubs.stubEvictPod("grafana-monitoring-dev-kube-state-metrics-67f8bbc785-tw8jg", "infra-dev") }
      .compose { kubeStubs.stubEvictPod("coredns-64fd4b4794-5hwdm", "kube-system") }
      .compose { kubeStubs.stubEvictPod("local-path-provisioner-774c6665dc-4c8zr", "kube-system") }
      .compose { kubeStubs.stubEvictPod("metrics-server-7bfffcd44-6nn6v", "kube-system") }
      .compose {
        kubeStubs.stubListAllEvictedPodsOnNode(nodeName) {
          it.inScenario("Drain node")
            .whenScenarioStateIs("All pods evicted")
        }
      }

    // when & then:
    stubs
      .compose { drainNodeService.drainNode(nodeName) }
      .onComplete({ ctx.completeNow() }, ctx::failNow)
  }

  @Test
  fun testRetryDecorator(vertx: Vertx, ctx: VertxTestContext) {
    // given:
    val decor = retryDecorator<Void>("TestOp", vertx, arrayOf(20, 50, 100))

    // when & then:
    decor {
      Future.failedFuture(IllegalArgumentException("Sth bad happened"))
    }.onComplete(
      { ctx.failNow("Should fail") },
      { ctx.completeNow() }
    )
  }
}

fun <T> retryDecorator(
  opName: String,
  vertx: Vertx,
  times: Array<Long> = arrayOf(3000L, 5000L, 10000L),
): (() -> Future<T>) -> Future<T> {
  return { fn ->
    var res = fn()

    for ((index, lng) in times.withIndex()) {
      res = res.recover { err ->
        println("$opName fail #${index + 1}: ${err.message}, ${lng / 1000}s to retry...")
        setTimeout(vertx, lng).compose { fn() }
      }
    }

    res
  }
}

fun setTimeout(vertx: Vertx, delayMs: Long): Future<Void> {
  val promise = Promise.promise<Void>()
  vertx.setTimer(delayMs) { promise.complete() }
  return promise.future()
}
