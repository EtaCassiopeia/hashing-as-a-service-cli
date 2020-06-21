package com.ing.hashservice.cli.io

import java.net.URL

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import zio.Runtime

import scala.io.Source
import scala.util.Using

class BlockReaderServiceSpec extends AnyFlatSpec with Matchers {
  val runtime: Runtime[zio.ZEnv] = Runtime.default
  val file: URL = getClass.getResource("/sample.txt")
  val blockReader = new BlockReaderService.Live(file.getPath)

  "BlockReaderService" should "read a block from the given file" in {
    runtime.unsafeRun(blockReader.fileLength()) shouldBe 3530

    val dataBlock: BlockReaderService.DataBlock = runtime.unsafeRun(blockReader.getDataBlock(blockSize = 1024))
    val actualLines: List[String] = Using(Source.fromURL(file)) { source =>
      source.getLines().take(12).toList
    }.getOrElse(List.empty)

    dataBlock.lines.size shouldBe 12
    dataBlock.position.blockSize shouldBe 914

    dataBlock.lines.head shouldBe actualLines.head
    dataBlock.lines.last shouldBe actualLines.last
  }

  it should "continue to read next blocks" in {
    val dataBlock: BlockReaderService.DataBlock =
      runtime.unsafeRun(blockReader.getDataBlock(startOffset = 914, blockSize = 1024))
    val actualLines: List[String] = Using(Source.fromURL(file)) { source =>
      source.getLines().take(14).toList
    }.getOrElse(List.empty)

    dataBlock.lines.head shouldBe actualLines(12)
  }
}
