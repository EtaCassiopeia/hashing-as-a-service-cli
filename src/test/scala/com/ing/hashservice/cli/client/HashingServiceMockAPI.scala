package com.ing.hashservice.cli.client

import com.ing.hashservice.cli.client.HashingService.{HashRequest, HashResponse}
import io.circe.syntax._
import org.mockserver.integration.ClientAndServer
import org.mockserver.model.MediaType.APPLICATION_JSON_UTF_8
import org.mockserver.model.{Delay, HttpRequest, HttpResponse}
import io.circe.generic.auto._

class HashingServiceMockAPI private (mockServerPort: Int) {
  private val mockServer: ClientAndServer = ClientAndServer.startClientAndServer(mockServerPort)

  def successfulCall(request: HashRequest, expectedResponse: HashResponse): Unit = {
    mockServer
      .reset()
      .when(getRequest(request))
      .respond(successfulResponse(expectedResponse))
  }

  def failedCall(request: HashRequest): Unit = {
    mockServer
      .reset()
      .when(getRequest(request))
      .respond(failedResponse())
  }

  def timeoutCall(request: HashRequest): Unit = {
    mockServer
      .reset()
      .when(getRequest(request))
      .respond(HttpResponse.response().withDelay(Delay.seconds(15)))
  }

  def close(): Unit = mockServer.close()

  private def getRequest(request: HashRequest): HttpRequest =
    HttpRequest
      .request(request.asJson.noSpaces)
      .withContentType(APPLICATION_JSON_UTF_8)
      .withMethod("POST")
      .withPath(s"/api/service")

  private def successfulResponse(expectedResponse: HashResponse) = {
    HttpResponse
      .response(expectedResponse.asJson.noSpaces)
      .withContentType(APPLICATION_JSON_UTF_8)
      .withStatusCode(200)
  }

  private def failedResponse() = {
    HttpResponse
      .response("""{
                  |   "error": "Something went wrong!"
                  |}""".stripMargin)
      .withContentType(APPLICATION_JSON_UTF_8)
      .withStatusCode(500)
  }
}

object HashingServiceMockAPI {

  def apply(mockServerPort: Int): HashingServiceMockAPI =
    new HashingServiceMockAPI(mockServerPort)
}
