package pl.kperczynski.kube_spot_operator.kube

import com.github.tomakehurst.wiremock.client.MappingBuilder

typealias MappingFn = (MappingBuilder) -> MappingBuilder
