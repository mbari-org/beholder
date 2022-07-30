/*
 * Copyright (c) Monterey Bay Aquarium Research Institute 2021
 *
 * beholder code is non-public software. Unauthorized copying of this file,
 * via any medium is strictly prohibited. Proprietary and confidential.
 */

package org.mbari.beholder.api

sealed trait ErrorMsg:
  def message: String
  def responseCode: Int

/**
 * Just a simple class used to return a JSON error response
 * @param message
 *   the error message
 * @param responseCode
 *   the HTTP response code
 * @author
 *   Brian Schlining
 * @since 2021-11-23T11:00:00
 */
final case class StatusMsg(message: String, responseCode: Int)          extends ErrorMsg
final case class NotFound(message: String, responseCode: Int = 404)     extends ErrorMsg
final case class ServerError(message: String, responseCode: Int = 500)  extends ErrorMsg
final case class Unauthorized(message: String, responseCode: Int = 401) extends ErrorMsg
