package com.ing.hashservice.cli.io

import java.io.{BufferedWriter, IOException}
import java.nio.file.attribute.BasicFileAttributes
import java.nio.file.{FileVisitResult, Paths, SimpleFileVisitor, StandardOpenOption}

import zio._

import scala.util.Try

object BlockWriterService {
  type BlockWriterService = Has[BlockWriterService.Service]

  import java.nio.file.{Files, Path}

  trait Service {
    def writeToFile(jobId: String, blockId: String, lines: List[String]): ZIO[Any, Throwable, Unit]

    def mergeFiles(jobId: String, outputFile: String, blockIds: List[Long]): ZIO[Any, Throwable, Unit]

    def deleteDirectory(jobId: String): ZIO[Any, Throwable, Unit]

    def createDirectory(jobId: String): ZIO[Any, Throwable, Unit]
  }

  final class Live() extends Service {
    private val strTmp: String = System.getProperty("java.io.tmpdir")

    private def managed(path: Path): ZManaged[Any, Throwable, BufferedWriter] =
      Managed.fromAutoCloseable(ZIO.fromTry(Try(Files.newBufferedWriter(path, StandardOpenOption.CREATE))))

    override def writeToFile(jobId: String, blockId: String, lines: List[String]): ZIO[Any, Throwable, Unit] = {
      val fileName = Paths.get(strTmp, jobId, s"$blockId.tmp")
      managed(fileName).use { os =>
        ZIO.effect {
          lines.foreach(line => {
            os.write(line)
            os.newLine()
          })
          os.flush()
        }
      }
    }

    override def mergeFiles(jobId: String, outputFile: String, blockIds: List[Long]): ZIO[Any, Throwable, Unit] = {
      val path = Paths.get(strTmp, jobId)
      managed(Paths.get(outputFile)).use { os =>
        ZIO.effect {
          Files.walkFileTree(
            path,
            new SimpleFileVisitor[Path] {
              override def visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult = {
                Files
                  .readAllLines(file)
                  .forEach((line: String) => {
                    os.write(line)
                    os.newLine()
                  })
                os.flush()
                FileVisitResult.CONTINUE
              }
            }
          )
        }
      }
    }

    override def createDirectory(jobId: String): ZIO[Any, Throwable, Unit] = ZIO.effect {
      val path = Paths.get(strTmp, jobId)
      Files.createDirectories(path)
    }

    override def deleteDirectory(jobId: String): ZIO[Any, Throwable, Unit] = ZIO.effect {
      def remove(root: Path): Unit = {
        Files.walkFileTree(
          root,
          new SimpleFileVisitor[Path] {
            override def visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult = {
              Files.delete(file)
              FileVisitResult.CONTINUE
            }

            override def postVisitDirectory(dir: Path, exc: IOException): FileVisitResult = {
              Files.delete(dir)
              FileVisitResult.CONTINUE
            }
          }
        )
      }

      remove(Paths.get(strTmp, jobId))
    }
  }

  def live(): ZLayer[Any, Throwable, BlockWriterService] =
    ZLayer.fromFunction(_ => new Live())

  def writeToFile(jobId: String, blockId: String, lines: List[String]): ZIO[BlockWriterService, Throwable, Unit] =
    ZIO.accessM(_.get.writeToFile(jobId, blockId, lines))

  def mergeFiles(jobId: String, outputFile: String, blockIds: List[Long]): ZIO[BlockWriterService, Throwable, Unit] =
    ZIO.accessM(_.get.mergeFiles(jobId, outputFile, blockIds))

  def deleteDirectory(jobId: String): ZIO[BlockWriterService, Throwable, Unit] =
    ZIO.accessM(_.get.deleteDirectory(jobId))

  def createDirectory(jobId: String): ZIO[BlockWriterService, Throwable, Unit] =
    ZIO.accessM(_.get.createDirectory(jobId))
}
