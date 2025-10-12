package pl.kperczynski.kube_spot_operator

import io.vertx.launcher.application.VertxApplication

fun main() {
  VertxApplication.main(arrayOf(MainVerticle::class.java.name))
}
