/*
 * Copyright 2022 MBARI
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.mbari.beholder.etc.slf4j

import java.lang.System.Logger.Level
import java.util.ResourceBundle
import java.text.MessageFormat
import scala.beans.BeanProperty

class Slf4jLogger(@BeanProperty val name: String) extends System.Logger:

    private val logger = org.slf4j.LoggerFactory.getLogger(name)

    override def isLoggable(level: Level): Boolean =
        level match
            case Level.OFF     => false
            case Level.TRACE   => logger.isTraceEnabled
            case Level.DEBUG   => logger.isDebugEnabled
            case Level.INFO    => logger.isInfoEnabled
            case Level.WARNING => logger.isWarnEnabled
            case Level.ERROR   => logger.isErrorEnabled
            case _             => true

    override def log(level: Level, bundle: ResourceBundle, msg: String, thrown: Throwable): Unit =
        if isLoggable(level) then
            level match
                case Level.OFF     => // Do nothing
                case Level.TRACE   => logger.trace(msg, thrown)
                case Level.DEBUG   => logger.debug(msg, thrown)
                case Level.INFO    => logger.info(msg, thrown)
                case Level.WARNING => logger.warn(msg, thrown)
                case Level.ERROR   => logger.error(msg, thrown)
                case _             => logger.info(msg, thrown)

    override def log(level: Level, bundle: ResourceBundle, format: String, params: Any*): Unit =
        if isLoggable(level) then
            val msg = if params != null then MessageFormat.format(format, params*) else format
            level match
                case Level.OFF     => // Do nothing
                case Level.TRACE   => logger.trace(msg)
                case Level.DEBUG   => logger.debug(msg)
                case Level.INFO    => logger.info(msg)
                case Level.WARNING => logger.warn(msg)
                case Level.ERROR   => logger.error(msg)
                case _             => logger.info(msg)
