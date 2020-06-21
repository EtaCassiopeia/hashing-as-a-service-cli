package com.ing.hashservice.cli.actor

import java.util.UUID

import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, Behavior, PostStop}
import com.ing.hashservice.cli.actor.AppMaster.Protocol.{AppMasterCommand, Completed, Failed}
import com.ing.hashservice.cli.actor.IOHandler.Protocol.{AskForNewBlock, IOHandlerCommand, MergeBlocks}
import com.ing.hashservice.cli.actor.TaskMaster.Protocol._
import com.ing.hashservice.cli.actor.TaskWorker.Protocol._
import com.ing.hashservice.cli.extension.ZioFutureOps
import com.ing.hashservice.cli.io.BlockReaderService.DataBlock

import scala.concurrent.ExecutionContext

class TaskMaster(jobId: String, outputFile: String, appMasterActorRef: ActorRef[AppMasterCommand])(
  implicit zioOps: ZioFutureOps[AppMaster.AppEnvironment]) {
  import TaskMaster._

  def behavior(): Behavior[TaskMasterCommand] = Behaviors.setup { context =>
    val workers = spawnWorkers(jobId, context)
    context.spawn(IOHandler(jobId, context.self), "io-handler")
    initializing(InternalState(workers = workers.map(ref => ref -> Created).toMap))
  }

  private def initializing(state: InternalState): Behavior[TaskMasterCommand] =
    Behaviors
      .receive[TaskMasterCommand] { (context, message) =>
        implicit val ec: ExecutionContext = context.executionContext

        message match {
          case IOHandlerIsReady(ioHandlerActorRef) =>
            val newState = state.copy(ioHandler = Some(ioHandlerActorRef))
            if (state.workers.forall(_._2 == Idle)) {
              state.workers.keys.foreach(workerActorRef => workerActorRef ! StartToWork)
              active(newState)
            } else
              initializing(newState)

          case IOHandlerInitializationFailed(cause) =>
            appMasterActorRef ! Failed(cause)
            Behaviors.stopped

          case JoinWorker(workerActorRef) =>
            context.log.info(s"Worker joined ${workerActorRef.path.name}")
            val newState = state.copy(workers = state.workers + (workerActorRef -> Idle))
            if (state.workers.forall(_._2 == Idle) && state.ioHandler.isDefined) {
              state.workers.keys.foreach(workerActorRef => workerActorRef ! StartToWork)
              active(newState)
            } else
              initializing(newState)
          case _ => Behaviors.same
        }
      }

  private def active(state: InternalState): Behavior[TaskMasterCommand] =
    Behaviors
      .receive[TaskMasterCommand] { (context, message) =>
        implicit val ec: ExecutionContext = context.executionContext

        message match {
          case PullTask =>
            if (state.pendingTasks.nonEmpty) {
              val (taskId, task) = state.pendingTasks.head
              state.workers
                .find(_._2 == Idle)
                .map {
                  case (workerActorRef, _) =>
                    context.log.info(s"Assign an existing task ${task.taskId}")
                    workerActorRef ! DoWork(task)
                    active(
                      state.copy(
                        assignedTasks = state.assignedTasks + (task.taskId -> task),
                        pendingTasks = state.pendingTasks - taskId,
                        workers = state.workers + (workerActorRef -> Working)))
                }
                .getOrElse(Behaviors.same)
            } else {
              if (!state.noMoreDataToRead) {
                context.log.info(s"Asking for a new block")
                state.ioHandler.foreach(_ ! AskForNewBlock)
              }
              Behaviors.same
            }

          case TaskIsReady(task) =>
            state.workers
              .find(_._2 == Idle)
              .map {
                case (workerActorRef, _) =>
                  context.log.info(s"Assign a new task: ${task.taskId}")
                  workerActorRef ! DoWork(task)
                  active(
                    state.copy(
                      assignedTasks = state.assignedTasks + (task.taskId -> task),
                      workers = state.workers + (workerActorRef -> Working)
                    ))
              }
              .getOrElse(active(state.copy(pendingTasks = state.pendingTasks + (task.taskId -> task))))

          case TaskDone(workerActorRef, taskId) =>
            context.log.info(s"Task completed: $taskId")
            val newState = state.copy(
              assignedTasks = state.assignedTasks - taskId,
              completedTasks = state.completedTasks :+ taskId,
              workers = state.workers + (workerActorRef -> Idle))

            if (newState.noMoreDataToRead)
              context.log.info(s"Waiting for tasks, #assignedTasks: ${state.assignedTasks.size}")

            if (newState.noMoreDataToRead && newState.assignedTasks.isEmpty && newState.pendingTasks.isEmpty) {
              context.log.info(s"Sending merge request")
              state.ioHandler.foreach(_ ! MergeBlocks(jobId, outputFile, state.completedTasks))
              finalizing()
            } else
              active(newState)

          case TaskFailed(workerActorRef, taskId, cause) =>
            context.log.info(s"Task failed: $taskId ${cause.getMessage}")
            state.assignedTasks
              .find(_._1 == taskId)
              .map {
                case (id, task) =>
                  active(
                    state.copy(
                      assignedTasks = state.assignedTasks - id,
                      pendingTasks = state.pendingTasks + (id -> task),
                      workers = state.workers + (workerActorRef -> Idle)))
              }
              .getOrElse(Behaviors.same)

          case NoMoreBlockExists =>
            active(state.copy(noMoreDataToRead = true))

          case _ => Behaviors.same
        }
      }

  private def finalizing(): Behavior[TaskMasterCommand] =
    Behaviors
      .receive[TaskMasterCommand] { (context, message) =>
        message match {
          case Merged =>
            context.log.info("Merge completed")
            appMasterActorRef ! Completed
            Behaviors.stopped
          case MergeFailed(cause) =>
            context.log.error(s"Merge Failed ${cause.getMessage}")
            appMasterActorRef ! Completed
            Behaviors.stopped
          case _ => Behaviors.same
        }
      }
      .receiveSignal {
        case (context, PostStop) =>
          context.log.info("Task Master stopped")
          Behaviors.same
      }

  private def spawnWorkers(
    jobId: String,
    context: ActorContext[TaskMasterCommand],
    numberOfWorkers: Int = defaultTaskWorkers): List[ActorRef[TaskWorkerCommand]] =
    List.fill(numberOfWorkers)(context.spawn(TaskWorker(jobId, context.self), s"taskworker-${UUID.randomUUID()}"))
}

object TaskMaster {
  private val defaultTaskWorkers = 10

  def apply(jobId: String, outputFile: String, appMasterActorRef: ActorRef[AppMasterCommand])(
    implicit zioOps: ZioFutureOps[AppMaster.AppEnvironment]): Behavior[TaskMasterCommand] =
    new TaskMaster(jobId, outputFile, appMasterActorRef).behavior()

  type TaskId = Long

  case class InternalState(
    workers: Map[ActorRef[TaskWorkerCommand], WorkerState] = Map.empty,
    ioHandler: Option[ActorRef[IOHandlerCommand]] = None,
    assignedTasks: Map[TaskId, Task] = Map.empty,
    pendingTasks: Map[TaskId, Task] = Map.empty,
    completedTasks: List[TaskId] = List.empty,
    noMoreDataToRead: Boolean = false
  )

  object Protocol {
    sealed trait TaskMasterCommand

    case class JoinWorker(workerActorRef: ActorRef[TaskWorkerCommand]) extends TaskMasterCommand

    case class IOHandlerIsReady(ioHandlerActorRef: ActorRef[IOHandlerCommand]) extends TaskMasterCommand
    case class IOHandlerInitializationFailed(cause: Throwable) extends TaskMasterCommand

    case object PullTask extends TaskMasterCommand

    case class Task(taskId: TaskId, dataBlock: DataBlock)

    case class TaskIsReady(task: Task) extends TaskMasterCommand

    case class TaskDone(workerActorRef: ActorRef[TaskWorkerCommand], taskId: TaskId) extends TaskMasterCommand

    case class TaskFailed(workerActorRef: ActorRef[TaskWorkerCommand], taskId: TaskId, cause: Throwable)
        extends TaskMasterCommand

    case object NoMoreBlockExists extends TaskMasterCommand

    case object Merged extends TaskMasterCommand
    case class MergeFailed(cause: Throwable) extends TaskMasterCommand
  }
}
