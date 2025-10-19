package pl.kperczynski.kube_spot_operator.kube

import io.vertx.core.Vertx
import io.vertx.junit5.VertxExtension
import io.vertx.junit5.VertxTestContext
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import pl.kperczynski.kube_spot_operator.bootstrapConfig
import pl.kperczynski.kube_spot_operator.config.ConfigMap
import pl.kperczynski.kube_spot_operator.ec2.HttpEC2MetadataClient
import pl.kperczynski.kube_spot_operator.ec2.InstanceAction
import pl.kperczynski.kube_spot_operator.ec2.ec2MetadataHttpClient

@ExtendWith(VertxExtension::class)
class Ec2MetadataClientTest {

  companion object {

    private lateinit var configmap: ConfigMap
    private lateinit var ec2MetadataStubs: Ec2MetadataApiStubs
    private lateinit var ec2MetadataClient: HttpEC2MetadataClient

    @BeforeAll
    @JvmStatic
    fun setUp(vertx: Vertx, ctx: VertxTestContext) {
      val kubeWiremock = WiremockVerticle("EC2-Metadata", 29014)

      bootstrapConfig(vertx)
        .onSuccess { configmap = it }
        .compose { vertx.deployVerticle(kubeWiremock) }
        .onSuccess {
          ec2MetadataStubs = Ec2MetadataApiStubs(kubeWiremock.wiremock)
        }
        .onSuccess {
          ec2MetadataClient = HttpEC2MetadataClient(
            ec2MetadataHttpClient(vertx, configmap.ec2),
            configmap.ec2
          )
        }
        .onComplete(ctx.succeedingThenComplete())
    }
  }

  @BeforeEach
  fun setUp() {
    ec2MetadataStubs.resetAll()
  }

  @Test
  fun testFetchInstanceAction404(ctx: VertxTestContext) {
    val stubs = ec2MetadataStubs.stubIssueToken().compose {
      ec2MetadataStubs.stubInstanceActionNotFound()
    }

    val cordonNode = stubs.compose { ec2MetadataClient.fetchInstanceAction() }

    cordonNode
      .onSuccess { instanceAction ->
        assertThat(instanceAction).isNull()
      }
      .onComplete({ ctx.completeNow() }, ctx::failNow)
  }

  @Test
  fun testFetchInstanceAction200(ctx: VertxTestContext) {
    val stubs = ec2MetadataStubs.stubIssueToken().compose {
      ec2MetadataStubs.stubInstanceActionTerminate()
    }

    val cordonNode = stubs.compose { ec2MetadataClient.fetchInstanceAction() }

    cordonNode
      .onSuccess { instanceAction ->
        assertThat(instanceAction).isEqualTo(
          InstanceAction(
            action = "terminate",
            time = "2025-10-17T14:51:09Z"
          )
        )
      }
      .onComplete({ ctx.completeNow() }, ctx::failNow)
  }

}
