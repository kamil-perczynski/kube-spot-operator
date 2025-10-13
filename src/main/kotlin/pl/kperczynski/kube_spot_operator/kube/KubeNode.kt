package pl.kperczynski.kube_spot_operator.kube

data class KubeNode(
  val name: String,
  val taints: List<String>,
  val conditions: List<String>,
)
