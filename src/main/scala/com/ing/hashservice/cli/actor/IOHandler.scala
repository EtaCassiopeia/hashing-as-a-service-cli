package com.ing.hashservice.cli.actor

import akka.actor.typed.scaladsl.{Behaviors, StashBuffer}
import akka.actor.typed.{ActorRef, Behavior}
import com.ing.hashservice.cli.actor.AppMaster.AppEnvironment
import com.ing.hashservice.cli.actor.IOHandler.InternalState
import com.ing.hashservice.cli.actor.TaskMaster.Protocol._
import com.ing.hashservice.cli.actor.TaskMaster.TaskId
import com.ing.hashservice.cli.extension.ZioFutureOps
import com.ing.hashservice.cli.extension.ZioFutureOps._
import com.ing.hashservice.cli.io.BlockReaderService.{DataBlock, fileLength, _}
import com.ing.hashservice.cli.io.BlockWriterService._

import scala.concurrent.ExecutionContext
import scala.util.Try

class IOHandler(taskMasterActorRef: ActorRef[TaskMasterCommand])(
  implicit zioOps: ZioFutureOps[AppMaster.AppEnvironment]) {
  import IOHandler.Protocol._

  def behavior(): Behavior[IOHandlerCommand] = Behaviors.setup { context =>
    context.self ! GetFileSize
    initializing(InternalState())
  }

  private def initializing(state: InternalState): Behavior[IOHandlerCommand] =
    Behaviors
      .receive[IOHandlerCommand] { (context, message) =>
        implicit val ec: ExecutionContext = context.executionContext

        message match {
          case GetFileSize =>
            fileLength().pipeTo(context.self, FileSizeReady)
            Behaviors.same
          case FileSizeReady(result) =>
            result.fold(
              e => {
                taskMasterActorRef ! IOHandlerInitializationFailed(e)
                Behaviors.stopped[IOHandlerCommand]
              },
              fileSize => {
                context.log.info(s"File size : $fileSize")
                taskMasterActorRef ! IOHandlerIsReady(context.self)
                idle(state.copy(fileSize = fileSize))
              }
            )
          case _ => Behaviors.same
        }
      }

  private def idle(state: InternalState): Behavior[IOHandlerCommand] = Behaviors.withStash(100) { buffer =>
    Behaviors.receive[IOHandlerCommand] { (context, message) =>
      implicit val ec: ExecutionContext = context.executionContext
      message match {
        case AskForNewBlock =>
          context.log.info(s"Reading a new data block, start offset : ${state.nextBlockOffsetToRead}")
          getDataBlock(state.nextBlockOffsetToRead).pipeTo(context.self, NextBlockReady)
          active(state, buffer)
        case _ => Behaviors.same
      }
    }
  }

  private def active(state: InternalState, buffer: StashBuffer[IOHandlerCommand]): Behavior[IOHandlerCommand] =
    Behaviors.receive[IOHandlerCommand] { (context, message) =>
      implicit val ec: ExecutionContext = context.executionContext
      message match {
        case NextBlockReady(result) =>
          result.fold(
            e => {
              context.log.error(s"Failed to read the data block : ${e.getMessage}")
              buffer.unstashAll(idle(state))
            },
            dataBlock => {
              taskMasterActorRef ! TaskIsReady(Task(dataBlock.position.startOffset, dataBlock))
              val nextBlockOffset = state.nextBlockOffsetToRead + dataBlock.position.blockSize

              if (nextBlockOffset < state.fileSize) {
                // now we are ready to handle stashed messages if any
                context.log.info(s"Has more data to read : $nextBlockOffset - ${state.fileSize}")
                buffer.unstashAll(idle(state.copy(nextBlockOffsetToRead = nextBlockOffset)))
              } else {
                context.log.info(s"No more data left to read : $nextBlockOffset - ${state.fileSize}")
                buffer.clear()
                taskMasterActorRef ! NoMoreBlockExists
                merging()
              }
            }
          )
        case other =>
          // stash all other messages for later processing
          context.log.info(s"Stashing message: $other")
          buffer.stash(other)
          Behaviors.same
      }
    }

  private def merging(): Behavior[IOHandlerCommand] = Behaviors.receive[IOHandlerCommand] { (context, message) =>
    implicit val ec: ExecutionContext = context.executionContext
    message match {
      case MergeBlocks(jobId, outputFile, processedBlocks) =>
        context.log.info(s"Merging blocks")
        (mergeFiles(jobId, outputFile, processedBlocks.sorted) *> deleteDirectory(jobId))
          .pipeTo(context.self, MergeCompleted)
        Behaviors.same
      case MergeCompleted(result) =>
        result.fold(e => taskMasterActorRef ! MergeFailed(e), _ => taskMasterActorRef ! Merged)
        Behaviors.stopped
      case _ => Behaviors.same
    }
  }
}

object IOHandler {

  def apply(jobId: String, taskMasterActorRef: ActorRef[TaskMasterCommand])(
    implicit zioOps: ZioFutureOps[AppEnvironment]): Behavior[Protocol.IOHandlerCommand] =
    new IOHandler(taskMasterActorRef)(zioOps).behavior()

  case class InternalState(
    nextBlockOffsetToRead: Long = 0,
    fileSize: Long = 0
  )

  object Protocol {
    sealed trait IOHandlerCommand

    case object GetFileSize extends IOHandlerCommand

    final case class FileSizeReady(answer: Try[Long]) extends IOHandlerCommand

    case object AskForNewBlock extends IOHandlerCommand

    final case class NextBlockReady(block: Try[DataBlock]) extends IOHandlerCommand

    final case class MergeBlocks(jobId: String, outputFile: String, processedBlocks: List[TaskId])
        extends IOHandlerCommand

    case class MergeCompleted(result: Try[Unit]) extends IOHandlerCommand
  }
}
