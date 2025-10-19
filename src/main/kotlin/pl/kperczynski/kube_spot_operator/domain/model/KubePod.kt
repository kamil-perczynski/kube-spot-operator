package pl.kperczynski.kube_spot_operator.domain.model

data class KubePod(
  val name: String,
  val namespace: String,
  val phase: String,
  val ownerKind: String,
  val ownerName: String,
  val hasEmptyDirVolume: Boolean,
)
