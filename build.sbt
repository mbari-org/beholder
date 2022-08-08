import com.typesafe.sbt.packager.docker.CmdLike
import com.typesafe.sbt.packager.docker.ExecCmd
import com.typesafe.sbt.packager.docker.Cmd
import Dependencies._

Docker / maintainer           := "Brian Schlining <brian@mbari.org>"
Docker / packageName          := "mbari/beholder"
Global / onChangedBuildSource := ReloadOnSourceChanges
Laika / sourceDirectories := Seq(baseDirectory.value / "docs")

ThisBuild / scalaVersion     := "3.1.3"
ThisBuild / licenses          := Seq(("Apache-2.0", url("http://www.apache.org/licenses/LICENSE-2.0.html")))
ThisBuild / organization     := "org.mbari"
ThisBuild / organizationName := "MBARI"
ThisBuild / startYear        := Some(2022)
ThisBuild / versionScheme    := Some("semver-spec")

// Hack to get the apt-get command in the right place in the docker file
// Inserts apt-get before user is changed to non-root
def buildDocker(cmds: Seq[CmdLike]): Seq[CmdLike] = {
  val idx = cmds.indexWhere(_ match {
    case Cmd("USER", user) => user != "root"
    case _ => false 
  })
  cmds.take(idx) ++ 
    Seq(Cmd("RUN", "apt-get update && apt-get install -y ffmpeg")) ++
    cmds.drop(idx)
}

lazy val root = project
  .in(file("."))
  .enablePlugins(
    AutomateHeaderPlugin, 
    GitBranchPrompt, 
    GitVersioning, 
    JavaAppPackaging, 
    LaikaPlugin)
  .settings(
    name := "beholder",
    dockerBaseImage    := "eclipse-temurin:17",
    dockerCommands := buildDocker(dockerCommands.value),
    dockerEntrypoint := Seq("/opt/docker/bin/beholder", "/opt/beholder/cache"),
    dockerExposedPorts := Seq(8080),
    dockerExposedVolumes := Seq("/opt/beholder/cache"),
    dockerRepository := Some("mbari"),
    dockerUpdateLatest := true,
    // Set version based on git tag. I use "0.0.0" format (no leading "v", which is the default)
    // Use `show gitCurrentTags` in sbt to update/see the tags
    git.gitTagToVersionNumber := { tag: String =>
      if(tag matches "[0-9]+\\..*") Some(tag)
      else None
    },
    git.useGitDescribe := true,
    // sbt-header
    javacOptions ++= Seq("-target", "11", "-source", "11"),
    laikaExtensions := Seq(
      laika.markdown.github.GitHubFlavor, 
      laika.parse.code.SyntaxHighlighting
    ),
    laikaIncludeAPI := true,
    // resolvers ++= Seq(
    //   Resolver.githubPackages("mbari-org", "maven")
    // ),
    libraryDependencies ++= Seq(
      circeCore,
      circeGeneric,
      circeParser,
      jansi          % Runtime,
      logback        % Runtime,
      methanol,
      munit          % Test,
      picocli,
      slf4jJdk       % Runtime,
      tapirStubServer % Test,
      tapirSwagger,
      tapirCirce,
      tapirCirceClient % Test,
      tapirVertx,
      typesafeConfig,
      zio
    ),
    scalacOptions ++= Seq(
      "-deprecation", // Emit warning and location for usages of deprecated APIs.
      "-encoding",
      "UTF-8",        // yes, this is 2 args. Specify character encoding used by source files.
      "-feature",     // Emit warning and location for usages of features that should be imported explicitly.
      "-language:existentials",
      "-language:higherKinds",
      "-language:implicitConversions",
      "-language:postfixOps",
      "-indent",
      "-rewrite",
      "-unchecked"
    )
  )

// https://stackoverflow.com/questions/22772812/using-sbt-native-packager-how-can-i-simply-prepend-a-directory-to-my-bash-scrip
bashScriptExtraDefines ++= Seq(
  """addJava "-Dconfig.file=${app_home}/../conf/application.conf"""",
  """addJava "-Dlogback.configurationFile=${app_home}/../conf/logback.xml""""
)
batScriptExtraDefines ++= Seq(
  """call :add_java "-Dconfig.file=%APP_HOME%\conf\application.conf"""",
  """call :add_java "-Dlogback.configurationFile=%APP_HOME%\conf\logback.xml""""
)

