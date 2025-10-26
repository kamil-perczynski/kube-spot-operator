package pl.kperczynski.kube_spot_operator.ec2

/**
 * https://docs.aws.amazon.com/autoscaling/ec2/userguide/ec2-auto-scaling-lifecycle.html
 */
enum class AsgLifecycleState {
  PENDING,
  IN_SERVICE,
  ENTERING_STANDBY,
  STANDBY,
  TERMINATING,
  TERMINATED,
  DETACHING,
  DETACHED,
  PENDING_WAIT,
  PENDING_PROCEED,
  TERMINATING_WAIT,
  TERMINATING_PROCEED,
  UNKNOWN_ASG_LIFECYCLE_STATE
}
