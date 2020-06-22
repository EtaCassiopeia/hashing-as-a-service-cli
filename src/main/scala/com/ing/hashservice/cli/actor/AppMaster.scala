package com.ing.hashservice.cli.actor

import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import com.ing.hashservice.cli.actor.AppMaster.Protocol.{AppMasterCommand, Completed, Failed}
import com.ing.hashservice.cli.client.HashingService.HashingService
import com.ing.hashservice.cli.extension.ZioFutureOps
import com.ing.hashservice.cli.io.BlockReaderService.BlockReaderService
import com.ing.hashservice.cli.io.BlockWriterService.BlockWriterService
import sttp.client.asynchttpclient.zio.SttpClient
import zio.ZIO
import zio.console._

class AppMaster(jobId: String, outputFile: String)(implicit zioOps: ZioFutureOps[AppMaster.AppEnvironment]) {

  def start(): Behavior[AppMasterCommand] = Behaviors.setup[AppMasterCommand] { context =>
    context.spawn(TaskMaster(jobId, outputFile, context.self), "taskmaster")
    Behaviors.receiveMessage[AppMasterCommand] {
      case Completed =>
        context.log.error(s"Successfully completed: $outputFile")
        Behaviors.stopped
      case Failed(cause) =>
        context.log.error(s"Failed ${cause.getMessage}")
        Behaviors.stopped
    }
  }
}

object AppMaster {
  type AppEnvironment = Console with BlockReaderService with BlockWriterService with HashingService with SttpClient

  def apply[R <: AppEnvironment](jobId: String, outputFile: String)(
    implicit zioOps: ZioFutureOps[R]): ZIO[R, Nothing, Behavior[AppMasterCommand]] =
    putStrLn("Initializing AppMaster") *> ZIO.succeed(new AppMaster(jobId, outputFile).start())

  object Protocol {
    sealed trait AppMasterCommand
    case object Completed extends AppMasterCommand
    case class Failed(cause: Throwable) extends AppMasterCommand
  }
}
