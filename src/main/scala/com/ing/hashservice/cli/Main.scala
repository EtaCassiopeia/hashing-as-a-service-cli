package com.ing.hashservice.cli

import java.nio.file.{Files, Paths}
import java.util.UUID

import com.ing.hashservice.cli.actor.AppMaster
import com.ing.hashservice.cli.actor.AppMaster.AppEnvironment
import com.ing.hashservice.cli.actor.AppMaster.Protocol.AppMasterCommand
import com.ing.hashservice.cli.client.HashingService
import com.ing.hashservice.cli.config.ServiceConfig
import com.ing.hashservice.cli.extension.{ManagedActorSystem, ZioFutureOps}
import com.ing.hashservice.cli.io.{BlockReaderService, BlockWriterService}
import sttp.client.asynchttpclient.zio._
import zio._
import zio.console._

import scala.util.Try

object Main extends App {

  override def run(args: List[String]): ZIO[zio.ZEnv, Nothing, ExitCode] =
    (validateArgs(args) *> (for {
      _ <- putStrLn("Starting up...")
      config <- ZIO.fromTry(Try(ServiceConfig.load))
      jobId = UUID.randomUUID().toString
      hashingServiceLayer = (ZLayer.succeed(config) ++ AsyncHttpClientZioBackend.layer()) >>> HashingService.live
      program = startAppMaster[AppMaster.AppEnvironment](jobId, args(1))
      _ <- program.provideCustomLayer(
        BlockReaderService
          .live(args.head) ++ BlockWriterService.live() ++ AsyncHttpClientZioBackend
          .layer() ++ hashingServiceLayer ++ Console.live)
    } yield ())).catchAll(e => putStrLn(e.getMessage)).exitCode

  private def validateArgs(args: List[String]): ZIO[Any, Throwable, Boolean] = ZIO.fromTry {
    Try {
      if (args.length < 2)
        throw new Exception("Input or output file is missing!")
      else if (!Files.exists(Paths.get(args.head)))
        throw new Exception("Input file doesn't exist!")
      else
        true
    }
  }

  private def startAppMaster[R <: AppEnvironment](
    jobId: String,
    outputFile: String): ZIO[R with Console, Throwable, Unit] =
    ZIO.runtime[R].flatMap { runtime =>
      implicit val in: ZioFutureOps[R] = ZioFutureOps(runtime)

      for {
        behavior <- AppMaster[R](jobId, outputFile)
        _ <- ManagedActorSystem[AppMasterCommand](behavior).use { resource =>
          ZIO.fromFuture(_ => resource.actorSystem.whenTerminated)
        }
      } yield ()
    }
}
