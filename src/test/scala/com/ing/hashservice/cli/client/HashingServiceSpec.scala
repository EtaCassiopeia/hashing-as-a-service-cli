package com.ing.hashservice.cli.client

import com.ing.hashservice.cli.client.HashingService.{HashRequest, HashResponse}
import com.ing.hashservice.cli.config.ServiceConfig
import org.scalatest.BeforeAndAfterAll
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import sttp.client.HttpError
import sttp.client.SttpClientException.ReadException
import sttp.client.asynchttpclient.zio.AsyncHttpClientZioBackend
import zio._

class HashingServiceSpec extends AnyFlatSpec with Matchers with BeforeAndAfterAll {
  val mockServer: HashingServiceMockAPI = HashingServiceMockAPI(8080)
  val serviceConfig: ServiceConfig = ServiceConfig("http", "localhost", 8080)
  val service = new HashingService.Live(serviceConfig)
  val runtime: Runtime[zio.ZEnv] = Runtime.default

  "HashingService" should "get HashResponse using a single sent request" in {
    val request = HashRequest("id", List("a", "b"))
    val expectedResponse = HashResponse("id", List("hash-a", "hash-b"))
    mockServer.successfulCall(request, expectedResponse)
    runtime
      .unsafeRun(service.send(request).provideCustomLayer(AsyncHttpClientZioBackend.layer()))
      .lines should contain allOf ("hash-a", "hash-b")
  }

  it should "be able to parse failure responses" in {
    val request = HashRequest("id", List("a", "b"))
    mockServer.failedCall(request)

    runtime.unsafeRun(service.send(request).provideCustomLayer(AsyncHttpClientZioBackend.layer()).flip) shouldBe a[
      HttpError]
  }

  it should "be able to handle request timeouts" in {
    val request = HashRequest("id", List("a", "b"))
    mockServer.timeoutCall(request)

    runtime.unsafeRun(service.send(request).provideCustomLayer(AsyncHttpClientZioBackend.layer()).flip) shouldBe a[
      ReadException]
  }

  override protected def afterAll(): Unit = mockServer.close()
}
