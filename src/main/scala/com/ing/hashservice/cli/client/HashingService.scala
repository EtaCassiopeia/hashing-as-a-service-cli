package com.ing.hashservice.cli.client

import com.ing.hashservice.cli.config.ServiceConfig
import io.circe.generic.auto._
import sttp.client.asynchttpclient.zio.SttpClient
import sttp.client.circe._
import sttp.client.{basicRequest, _}
import zio.{Has, ZIO, ZLayer}
import scala.concurrent.duration._

object HashingService {
  final case class HashRequest(id: String, lines: List[String])

  final case class HashResponse(id: String, lines: List[String])

  type HashingService = Has[HashingService.Service]

  trait Service {
    def send(request: HashRequest): ZIO[SttpClient, Throwable, HashResponse]
  }

  final class Live(config: ServiceConfig) extends Service {
    private val baseUrl = s"${config.scheme}://${config.host}:${config.port}"

    override def send(hashRequest: HashRequest): ZIO[SttpClient, Throwable, HashResponse] = {
      val request = basicRequest
        .post(uri"$baseUrl/api/service")
        .body(hashRequest)
        .response(asJson[HashResponse])
        .readTimeout(10.seconds)
      SttpClient
        .send(request)
        .foldM(
          e => ZIO.fail(e),
          _.body.fold(ZIO.fail(_), ZIO.succeed(_))
        )
    }
  }

  def live: ZLayer[Has[ServiceConfig] with SttpClient, Throwable, HashingService] =
    ZLayer.fromFunction { env =>
      val config = env.get
      new Live(config)
    }

  def send(hashRequest: HashRequest): ZIO[HashingService with SttpClient, Throwable, HashResponse] =
    ZIO.accessM(_.get.send(hashRequest))
}
