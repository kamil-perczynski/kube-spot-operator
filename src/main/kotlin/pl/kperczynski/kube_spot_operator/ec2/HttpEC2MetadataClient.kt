package pl.kperczynski.kube_spot_operator.ec2

import io.netty.handler.codec.http.HttpStatusClass
import io.vertx.core.Future
import io.vertx.core.http.HttpClient
import io.vertx.core.http.HttpMethod
import io.vertx.core.json.JsonObject
import org.slf4j.LoggerFactory
import org.slf4j.event.Level
import pl.kperczynski.kube_spot_operator.libs.handleResponseErrors
import pl.kperczynski.kube_spot_operator.libs.preconfigureRequest
import java.time.Duration
import java.time.Instant

private val log = LoggerFactory.getLogger(HttpEC2MetadataClient::class.java)

private const val X_AWS_EC2_METADATA_TOKEN = "X-aws-ec2-metadata-token"

class HttpEC2MetadataClient(
  private val httpClient: HttpClient,
  private val ec2MetadataProps: EC2MetadataProps
) : EC2MetadataClient {

  private lateinit var validTo: Instant
  private lateinit var cachedToken: String

  override fun fetchInstanceAction(): Future<InstanceAction> {
    return acquireToken()
      .compose { token ->
        httpClient.request(HttpMethod.GET, "/latest/meta-data/spot/instance-action")
          .onSuccess(preconfigureRequest(log, Level.TRACE))
          .compose { req ->
            req.putHeader(X_AWS_EC2_METADATA_TOKEN, token)
            req.send()
          }
      }
      .compose { res ->
        if (res.statusCode() == 404) {
          return@compose Future.succeededFuture(null)
        }
        handleResponseErrors(res, log)
      }
      .map { body ->
        if (body == null) {
          return@map null
        }

        val json = JsonObject(body.toString(Charsets.UTF_8))

        InstanceAction(
          action = json.getString("action"),
          time = json.getString("time")
        )
      }
  }

  private fun acquireToken(): Future<String> {
    if (!this::validTo.isInitialized) {
      log.info("EC2 metadata token is missing, fetching a new one")
      return fetchToken()
    }

    val tokenMinutesTtl = Duration.between(Instant.now(), validTo).toMinutes()

    if (tokenMinutesTtl < 3) {
      log.info("EC2 metadata token is expiring soon (in {} minutes), fetching a new one", tokenMinutesTtl)
      return fetchToken()
    }

    return Future.succeededFuture(cachedToken)
  }

  private fun fetchToken(): Future<String> {
    return httpClient.request(HttpMethod.PUT, "/latest/api/token")
      .onSuccess(preconfigureRequest(log, Level.TRACE))
      .compose { req ->
        req.putHeader("X-aws-ec2-metadata-token-ttl-seconds", ec2MetadataProps.ttlSeconds.toString())
        req.send()
      }
      .compose { res ->
        if (HttpStatusClass.valueOf(res.statusCode()) != HttpStatusClass.SUCCESS) {
          return@compose Future.failedFuture(IllegalStateException("Failed to fetch EC2 metadata token"))
        }

        res.body().map { it.toString(Charsets.UTF_8) }
      }
      .onSuccess { token ->
        this.cachedToken = token
        this.validTo = Instant.now().plusSeconds(ec2MetadataProps.ttlSeconds)
      }
      .onFailure {
        this.cachedToken = ""
        this.validTo = Instant.EPOCH
      }
  }
}
