package com.ing.hashservice.cli.actor

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}
import com.ing.hashservice.cli.actor.TaskMaster.Protocol._
import com.ing.hashservice.cli.actor.TaskWorker.Protocol._
import com.ing.hashservice.cli.client.HashingService._
import com.ing.hashservice.cli.extension.ZioFutureOps
import com.ing.hashservice.cli.extension.ZioFutureOps._
import com.ing.hashservice.cli.io.BlockWriterService._

import scala.concurrent.ExecutionContext
import scala.util.Try

class TaskWorker(jobId: String, taskMaster: ActorRef[TaskMasterCommand])(
  implicit zioOps: ZioFutureOps[AppMaster.AppEnvironment]) {

  def behavior(): Behavior[TaskWorkerCommand] = Behaviors.setup { context =>
    context.log.info(s"TasWorker started ${context.self.path.name}")
    taskMaster ! JoinWorker(context.self)

    idle()
  }

  //TODO add task timeout
  def idle(): Behavior[TaskWorkerCommand] =
    Behaviors
      .receive[TaskWorkerCommand] { (context, message) =>
        implicit val ec: ExecutionContext = context.executionContext
        message match {
          case StartToWork =>
            taskMaster ! PullTask
            Behaviors.same
          case DoWork(task) =>
            context.log.info(s"Task is in progress: ${task.taskId}")
            send(HashRequest(task.taskId.toString, task.dataBlock.lines))
              .pipeTo(context.self, ResponseReceived)
            active(task)
          case _ =>
            Behaviors.same
        }
      }

  def active(activeTask: Task): Behavior[TaskWorkerCommand] =
    Behaviors
      .receive[TaskWorkerCommand] { (context, message) =>
        implicit val ec: ExecutionContext = context.executionContext
        message match {
          case ResponseReceived(result) =>
            result.fold(
              e => {
                taskMaster ! TaskFailed(context.self, activeTask.taskId, e)
                taskMaster ! PullTask
                idle()
              },
              response => {
                context.log.info(s"Writing results to file $jobId - ${response.id}")
                (createDirectory(jobId) *> writeToFile(jobId, response.id, response.lines))
                  .pipeTo(context.self, DumpedToFile)
                Behaviors.same
              }
            )
          case DumpedToFile(result) =>
            result.fold(
              e => taskMaster ! TaskFailed(context.self, activeTask.taskId, e),
              _ => taskMaster ! TaskDone(context.self, activeTask.taskId))
            taskMaster ! PullTask
            idle()
          case _ =>
            Behaviors.same
        }
      }
}

object TaskWorker {

  def apply(jobId: String, taskMaster: ActorRef[TaskMasterCommand])(
    implicit zioOps: ZioFutureOps[AppMaster.AppEnvironment]): Behavior[TaskWorkerCommand] =
    new TaskWorker(jobId, taskMaster).behavior()

  object Protocol {
    trait WorkerState

    case object Created extends WorkerState

    case object Idle extends WorkerState

    case object Working extends WorkerState

    sealed trait TaskWorkerCommand

    case object StartToWork extends TaskWorkerCommand

    case class DoWork(task: Task) extends TaskWorkerCommand

    case class ResponseReceived(result: Try[HashResponse]) extends TaskWorkerCommand

    case class DumpedToFile(result: Try[Unit]) extends TaskWorkerCommand
  }
}
