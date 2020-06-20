package com.ing.hashservice.cli.io

import java.io.RandomAccessFile

import zio._

import scala.util.Try

object BlockReaderService {
  type BlockReaderService = Has[BlockReaderService.Service]
  private val defaultBlockSize: Int = 2 * 1024

  final case class BlockPosition(startOffset: Long, blockSize: Long)

  final case class DataBlock(lines: List[String], position: BlockPosition)

  trait Service {
    def fileLength(): ZIO[Any, Throwable, Long]

    def getDataBlock(startOffset: Long, blockSize: Int): ZIO[Any, Throwable, DataBlock]
  }

  final class Live(file: String) extends Service {

    def getDataBlock(startOffset: Long = 0, blockSize: Int): ZIO[Any, Throwable, DataBlock] = {
      @scala.annotation.tailrec
      def rewindToLastLineTerminator(block: String, blockSize: Int): (String, Int) = {
        if (block.length > 0) {
          val lastChar = block.charAt(block.length - 1)
          if (lastChar != '\n' && lastChar != '\r')
            rewindToLastLineTerminator(block.dropRight(1), blockSize - 1)
          else
            (block, blockSize)
        } else {
          (block, blockSize)
        }
      }

      managed(file).use { randomAccessFile =>
        val byteBuffer = new Array[Byte](blockSize)
        randomAccessFile.seek(startOffset)
        randomAccessFile.read(byteBuffer)
        val rawString = new String(byteBuffer)
        val (trimmedString, actualBlockSize) = rewindToLastLineTerminator(rawString, blockSize)
        ZIO.succeed(
          DataBlock(
            trimmedString.split(System.getProperty("line.separator")).toList,
            BlockPosition(startOffset, actualBlockSize)))
      }
    }

    def fileLength(): ZIO[Any, Throwable, Long] =
      managed(file).use(randomAccessFile => ZIO.fromTry(Try(randomAccessFile.length())))

    private def managed(file: String): ZManaged[Any, Throwable, RandomAccessFile] =
      Managed.fromAutoCloseable(ZIO.fromTry(Try(new RandomAccessFile(file, "r"))))
  }

  def live(file: String): ZLayer[Any, Throwable, BlockReaderService] =
    ZLayer.fromFunction(_ => new Live(file))

  def fileLength(): ZIO[BlockReaderService, Throwable, Long] = ZIO.accessM(_.get.fileLength())

  def getDataBlock(
    startOffset: Long,
    blockSize: Int = defaultBlockSize): ZIO[BlockReaderService, Throwable, DataBlock] =
    ZIO.accessM(_.get.getDataBlock(startOffset, blockSize))
}
