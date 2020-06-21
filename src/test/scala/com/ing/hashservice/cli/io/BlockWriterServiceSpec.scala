package com.ing.hashservice.cli.io

import java.nio.file.{Files, Paths}
import java.util.UUID

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import zio.Runtime

import scala.io.Source
import scala.util.Using

class BlockWriterServiceSpec extends AnyFlatSpec with Matchers {
  val runtime: Runtime[zio.ZEnv] = Runtime.default
  val blockWriter = new BlockWriterService.Live()
  val strTmp: String = System.getProperty("java.io.tmpdir")

  "BlockWriterService" should "write a data block into a temp file" in {
    val jobId = UUID.randomUUID().toString
    val blockId = "0"

    val file = Paths.get(strTmp, jobId, s"$blockId.tmp")

    runtime.unsafeRun(blockWriter.createDirectory(jobId) *> blockWriter.writeToFile(jobId, blockId, List("a", "b")))
    Files.exists(file) shouldBe true

    val actualLines: List[String] = Using(Source.fromFile(file.toUri)) { source =>
      source.getLines().toList
    }.getOrElse(List.empty)

    actualLines should contain allOf ("a", "b")
    runtime.unsafeRun(blockWriter.deleteDirectory(jobId))
  }

  it should "merge data blocks files" in {
    val jobId = UUID.randomUUID().toString
    val blockIds: List[Long] = List(0L, 1L)

    val outputFile = Paths.get(strTmp, jobId, "output.tmp")

    runtime.unsafeRun(
      blockWriter.createDirectory(jobId) *> blockWriter.writeToFile(jobId, "0", List("a", "b")) *> blockWriter
        .writeToFile(jobId, "1", List("c", "d")))

    runtime.unsafeRun(blockWriter.mergeFiles(jobId, outputFile.toString, blockIds))

    val actualLines: List[String] = Using(Source.fromFile(outputFile.toUri)) { source =>
      source.getLines().toList
    }.getOrElse(List.empty)

    actualLines should contain allOf ("a", "b", "c", "d")

    runtime.unsafeRun(blockWriter.deleteDirectory(jobId))
  }
}
