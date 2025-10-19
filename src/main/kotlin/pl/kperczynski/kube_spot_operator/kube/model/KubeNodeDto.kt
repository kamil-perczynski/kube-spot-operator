package pl.kperczynski.kube_spot_operator.kube.model

data class KubeNodeDto(
  val metadata: KubeNodeMetadataDto,
  val spec: KubeNodeSpecDto,
  val status: KubeNodeStatusDto,
)

data class KubeNodeMetadataDto(
  val name: String,
)

data class KubeNodeSpecDto(
  val taints: List<KubeTaintDto> = emptyList(),
)

data class KubeTaintDto(
  val key: String,
  val value: String?,
  val effect: String?,
)

data class KubeNodeStatusDto(
  val conditions: List<KubeNodeConditionDto> = emptyList(),
)

data class KubeNodeConditionDto(
  val type: String,
  val status: String,
)
