package pl.kperczynski.kube_spot_operator.ec2

import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Tag
import io.micrometer.core.instrument.Timer.start
import io.vertx.core.Future

private const val METRIC_NAME = "ec2.metadata.client.ops"

class MonitoredEC2MetadataClient(
  private val delegate: HttpEC2MetadataClient,
  private val meterRegistry: MeterRegistry
) : EC2MetadataClient {

  override fun fetchInstanceAction(): Future<InstanceAction> {
    val sample = start(meterRegistry)

    return delegate.fetchInstanceAction().onComplete(
      { instanceAction ->
        val timer = meterRegistry.timer(
          METRIC_NAME,
          listOf(
            Tag.of("operation", "fetchInstanceAction"),
            Tag.of("action", instanceAction?.action ?: "none"),
            Tag.of("status", "success"),
            Tag.of("exception", "none")
          )
        )
        sample.stop(timer)
      },
      { ex ->
        val timer = meterRegistry.timer(
          METRIC_NAME,
          listOf(
            Tag.of("operation", "fetchInstanceAction"),
            Tag.of("action", "none"),
            Tag.of("status", "failure"),
            Tag.of("exception", ex.javaClass.simpleName)
          )
        )
        sample.stop(timer)
      }
    )
  }

  override fun fetchAsgTargetLifecycleState(): Future<AsgLifecycleState> {
    val sample = start(meterRegistry)

    return delegate.fetchAsgTargetLifecycleState().onComplete(
      { state ->
        val timer = meterRegistry.timer(
          METRIC_NAME,
          listOf(
            Tag.of("operation", "fetchAsgTargetLifecycleState"),
            Tag.of("lifecycleState", state.name),
            Tag.of("status", "success"),
            Tag.of("exception", "none")
          )
        )
        sample.stop(timer)
      },
      { ex ->
        val timer = meterRegistry.timer(
          METRIC_NAME,
          listOf(
            Tag.of("operation", "fetchAsgTargetLifecycleState"),
            Tag.of("lifecycleState", "none"),
            Tag.of("status", "failure"),
            Tag.of("exception", ex.javaClass.simpleName)
          )
        )
        sample.stop(timer)
      }
    )
  }

}
