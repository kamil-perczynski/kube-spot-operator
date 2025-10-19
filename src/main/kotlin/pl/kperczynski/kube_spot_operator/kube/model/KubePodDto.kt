package pl.kperczynski.kube_spot_operator.kube.model

data class KubePodDto(
  val metadata: KubePodMetadataDto,
  val status: KubePodStatusDto,
  val spec: KubePodSpecDto
)

data class KubePodMetadataDto(
  val name: String,
  val namespace: String,
  val ownerReferences: List<KubeOwnerReferenceDto>?,
)

data class KubeOwnerReferenceDto(
  val kind: String,
  val name: String
)

data class KubePodStatusDto(
  val phase: String
)

data class KubePodSpecDto(
  val volumes: List<KubeVolumeDto> = emptyList()
)

data class KubeVolumeDto(
  val name: String,
  val emptyDir: Map<String, Any>?
)

