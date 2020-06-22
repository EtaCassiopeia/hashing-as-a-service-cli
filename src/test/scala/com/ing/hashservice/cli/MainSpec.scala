package com.ing.hashservice.cli

import java.nio.file.{Files, Path, Paths}
import java.util.UUID

import com.ing.hashservice.cli.client.HashingService.HashResponse
import com.ing.hashservice.cli.client.HashingServiceMockAPI
import org.scalatest.BeforeAndAfterAll
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import scala.io.Source
import scala.util.Using

class MainSpec extends AnyFlatSpec with Matchers with BeforeAndAfterAll {
  val strTmp: String = System.getProperty("java.io.tmpdir")
  val inputFile: String = getClass.getResource("/input.txt").getPath
  val outputFile: Path = Paths.get(strTmp, s"${UUID.randomUUID()}.tmp")
  val mockServer: HashingServiceMockAPI = HashingServiceMockAPI(9000)

  "Application" should "read a file and convert it to the respective hash values" in {
    println(outputFile)
    mockServer.successfulCallWithoutBody(
      HashResponse("0", List("62812ce276aa9819a2e272f94124d5a1", "13ea8b769685089ba2bed4a665a61fde")))
    Main.unsafeRun(Main.run(List(inputFile, outputFile.toString)))

    Files.exists(outputFile) shouldBe true

    val hashedLines: List[String] = Using(Source.fromFile(outputFile.toUri)) { source =>
      source.getLines().toList
    }.getOrElse(List.empty)

    hashedLines should contain allOf ("62812ce276aa9819a2e272f94124d5a1", "13ea8b769685089ba2bed4a665a61fde")
  }

  override protected def afterAll(): Unit = mockServer.close()
}
