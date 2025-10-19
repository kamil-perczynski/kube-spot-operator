package pl.kperczynski.kube_spot_operator.libs

import io.vertx.core.Future
import io.vertx.core.Vertx
import io.vertx.junit5.VertxExtension
import io.vertx.junit5.VertxTestContext
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import pl.kperczynski.kube_spot_operator.logging.Slf4j

@ExtendWith(VertxExtension::class)
class RetryDecoratorTest {

  companion object : Slf4j()

  @Test
  fun testRetryDecorator(vertx: Vertx, ctx: VertxTestContext) {
    // given:
    val decor = retryDecorator<Void>(
      opName = "TestOp",
      vertx = vertx,
      log = log,
      times = arrayOf(20, 50, 100)
    )

    // when & then:
    decor {
      Future.failedFuture(IllegalArgumentException("Sth bad happened"))
    }.onComplete(
      { ctx.failNow("Should fail") },
      { ctx.completeNow() }
    )
  }
}
