package pl.kperczynski.kube_spot_operator.ec2

import io.vertx.core.Future
import io.vertx.core.VerticleBase
import pl.kperczynski.kube_spot_operator.config.KubeNodeProps
import pl.kperczynski.kube_spot_operator.domain.EventIds.NODE_TERMINATION_SCHEDULED
import pl.kperczynski.kube_spot_operator.domain.model.NodeTerminationScheduledInput
import pl.kperczynski.kube_spot_operator.ec2.AsgLifecycleState.*
import pl.kperczynski.kube_spot_operator.logging.Slf4j
import java.time.Duration

class EC2EventsVerticle(
  private val ec2MetadataProps: EC2MetadataProps,
  private val kubeNodeProps: KubeNodeProps
) : VerticleBase() {

  companion object : Slf4j()

  private lateinit var ec2MetadataClient: EC2MetadataClient

  private var terminationScheduled: Boolean = false

  override fun start(): Future<*> {
    this.ec2MetadataClient = monitoredEC2MetadataClient(vertx, ec2MetadataProps)

    if (ec2MetadataProps.enabled) {
      val interval = Duration.ofMillis(ec2MetadataProps.timerInterval)

      log.info("Starting EC2 metadata checking for termination every: {}s", interval.toSeconds())
      vertx.setPeriodic(ec2MetadataProps.timerInterval) {
        observeSpotTermination()
      }
    }

    return Future.succeededFuture<Void>()
  }

  private fun observeSpotTermination() {
    if (terminationScheduled) {
      log.trace("Termination already scheduled for current ec2 instance node={}", kubeNodeProps.currentNodeName)
      return
    }

    ec2MetadataClient.fetchInstanceAction().onSuccess { instanceAction ->
      if (instanceAction == null) {
        log.trace("No scheduled actions for current ec2 instance node={}", kubeNodeProps.currentNodeName)
        return@onSuccess
      }

      if (instanceAction.action == "terminate" || instanceAction.action == "stop") {
        log.info(
          "Current node={} is scheduled to {}: {}",
          kubeNodeProps.currentNodeName,
          instanceAction.action,
          instanceAction
        )

        vertx.eventBus().send(
          NODE_TERMINATION_SCHEDULED,
          NodeTerminationScheduledInput(nodeId = kubeNodeProps.currentNodeName)
        )
        terminationScheduled = true
      }
    }

    ec2MetadataClient.fetchAsgTargetLifecycleState().onSuccess { lifecycleState ->
      log.trace(
        "Fetched requested ASG lifecycle state={} for node={}",
        kubeNodeProps.currentNodeName,
        lifecycleState
      )

      when (lifecycleState) {
        TERMINATING_WAIT, TERMINATING, TERMINATING_PROCEED, TERMINATED -> {
          log.info(
            "ASG requested node={} to transition into state={}",
            kubeNodeProps.currentNodeName,
            lifecycleState
          )

          vertx.eventBus().send(
            NODE_TERMINATION_SCHEDULED,
            NodeTerminationScheduledInput(nodeId = kubeNodeProps.currentNodeName)
          )
          terminationScheduled = true
        }

        else -> {
          // no-op
        }
      }
    }
  }
}
