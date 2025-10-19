package pl.kperczynski.kube_spot_operator.config

data class KubeNodeProps(
  val currentNodeName: String,
  val enableAutomaticNodeCleanup: Boolean
)
