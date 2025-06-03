import sbt._

object Dependencies {

  lazy val auth0 = "com.auth0" % "java-jwt" % "4.5.0"
  lazy val auth0jwk =  "com.auth0" % "jwks-rsa" % "0.22.1"

  private val circeVersion = "0.14.13"
  lazy val circeCore       = "io.circe" %% "circe-core"    % circeVersion
  lazy val circeGeneric    = "io.circe" %% "circe-generic" % circeVersion
  lazy val circeParser     = "io.circe" %% "circe-parser"  % circeVersion

  lazy val jansi    = "org.fusesource.jansi"         % "jansi"           % "2.4.2"
  lazy val logback  = "ch.qos.logback"               % "logback-classic" % "1.5.18"
  lazy val methanol = "com.github.mizosoft.methanol" % "methanol"        % "1.8.2"
  lazy val munit    = "org.scalameta"               %% "munit"           % "1.1.1"
  lazy val picocli  = "info.picocli"                 % "picocli"         % "4.7.7"

  lazy val slf4jVersion = "2.0.17"
  lazy val slf4jApi     = "org.slf4j"      % "slf4j-api"                    % slf4jVersion
  lazy val slf4jJul     = "org.slf4j"      % "jul-to-slf4j"                 % slf4jVersion
  lazy val slf4jJdk     = "org.slf4j"      % "slf4j-jdk-platform-logging"   % slf4jVersion

  lazy val sttpCirceClient = "com.softwaremill.sttp.client3" %% "circe" % "3.11.0"

  private val tapirVersion  = "1.11.25"
  lazy val tapirStubServer  = "com.softwaremill.sttp.tapir" %% "tapir-sttp-stub-server" % tapirVersion
  lazy val tapirSwagger     = "com.softwaremill.sttp.tapir" %% "tapir-swagger-ui-bundle" % tapirVersion
  lazy val tapirCirce       = "com.softwaremill.sttp.tapir" %% "tapir-json-circe" % tapirVersion
  
  lazy val tapirVertx       = "com.softwaremill.sttp.tapir" %% "tapir-vertx-server" % tapirVersion

  lazy val typesafeConfig = "com.typesafe"   % "config"          % "1.4.3"
  lazy val zio            = "dev.zio"       %% "zio"             % "2.1.19"
  
}