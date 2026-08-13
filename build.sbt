import com.typesafe.sbt.packager.docker.CmdLike
import com.typesafe.sbt.packager.docker.ExecCmd
import com.typesafe.sbt.packager.docker.Cmd
import Dependencies.*

Docker / maintainer           := "Brian Schlining <brian@mbari.org>"
Docker / packageName          := "mbari/beholder"
Global / onChangedBuildSource := ReloadOnSourceChanges
licenses                      := Seq(("Apache-2.0", url("http://www.apache.org/licenses/LICENSE-2.0.html")))
organization                  := "org.mbari"
organizationName              := "MBARI"
scalaVersion                  := "3.8.4"
startYear                     := Some(2022)
versionScheme                 := Some("semver-spec")
semanticdbEnabled             := true
semanticdbVersion             := scalafixSemanticdb.revision

Test / fork    := true
Test / testOptions ++= Seq(
    Tests.Argument(TestFrameworks.MUnit, "--log=debug")
)

Compile / run / fork := true  
Compile / doc / scalacOptions ++= Seq(
    "-groups",
    "-project-footer",
    "Monterey Bay Aquarium Research Institute",
    "-siteroot",
    "src/docs",
    "-doc-root-content",
    "./src/docs/index.md"
)

// Oldest allowed ffmpeg. Container-level clean-aperture (clap) handling landed around 7.1
val minFfmpegMajorVersion = 7

// The apt step, as a single RUN. ffprobe is a hard requirement
val installFfmpegCmd: String =
    """apt-get update \
      | && apt-get install -y --no-install-recommends ffmpeg \
      | && rm -rf /var/lib/apt/lists/* \
      | && command -v ffmpeg \
      | && command -v ffprobe \
      | && ffmpeg_version="$(ffmpeg -version | head -1 | cut -d' ' -f3)" \
      | && ffmpeg_major="$(printf '%s' "$ffmpeg_version" | sed 's/^[nN]//' | cut -d. -f1 | cut -d- -f1)" \
      | && echo "ffmpeg version: $ffmpeg_version (major $ffmpeg_major)" \
      | && { case "$ffmpeg_major" in ''|*[!0-9]*) echo "ERROR: cannot parse ffmpeg version '$ffmpeg_version'" >&2; exit 1;; esac; } \
      | && { [ "$ffmpeg_major" -ge MIN_FFMPEG_MAJOR ] || { echo "ERROR: ffmpeg $ffmpeg_version is older than required major MIN_FFMPEG_MAJOR" >&2; exit 1; }; }"""
        .stripMargin('|')
        .replace("MIN_FFMPEG_MAJOR", minFfmpegMajorVersion.toString)

// Hack to get the apt-get command in the right place in the docker file
// Inserts apt-get before user is changed to non-root (apt needs root)
def buildDocker(cmds: Seq[CmdLike]): Seq[CmdLike] =
    val idx = cmds.indexWhere(_ match
        case Cmd("USER", user) => user != "root"
        case _                 => false
    )
    cmds.take(idx) ++
        Seq(Cmd("RUN", installFfmpegCmd)) ++
        cmds.drop(idx)

lazy val root = project
    .in(file("."))
    .enablePlugins(AutomateHeaderPlugin, GitBranchPrompt, GitVersioning, JavaAppPackaging)
    .settings(
        name                      := "beholder",
        // Pinned to the Ubuntu release (26.04 "resolute"); the distro is what
        // determines ffmpeg's major series (8.0.x here), Moving to a newer
        // Ubuntu/JDK is a deliberate bump; re-check ffmpeg when you make one.
        dockerBaseImage           := "eclipse-temurin:25-jdk-resolute",
        dockerCommands            := buildDocker(dockerCommands.value),
        dockerEntrypoint          := Seq("/opt/docker/bin/beholder", "/opt/beholder/cache"),
        dockerExposedPorts        := Seq(8080),
        dockerExposedVolumes      := Seq("/opt/beholder/cache"),
        dockerRepository          := Some("mbari"),
        dockerUpdateLatest        := true,
        // Set version based on git tag. I use "0.0.0" format (no leading "v", which is the default)
        // Use `show gitCurrentTags` in sbt to update/see the tags
        git.gitTagToVersionNumber := { (gitTag: String) =>
            if gitTag.matches("[0-9]+\\..*") then Some(gitTag)
            else None
        },
        git.useGitDescribe        := true,
        // sbt-header
        javacOptions ++= Seq("-target", "25", "-source", "25"),
        libraryDependencies ++= Seq(
            auth0,
            auth0jwk,
            caffeine,
            circeCore,
            circeGeneric,
            circeParser,
            jansi           % Runtime,
            logback         % Runtime,
            methanol,
            munit           % Test,
            picocli,
            slf4jApi,
            slf4jJul        % Test,
            tapirStubServer % Test,
            tapirSwagger,
            tapirCirce,
            tapirVertx,
            typesafeConfig,
            zio
        ),
        scalacOptions ++= Seq(
            "-deprecation",    // Emit warning and location for usages of deprecated APIs.
            "-encoding",
            "UTF-8",           // yes, this is 2 args. Specify character encoding used by source files.
            "-feature",        // Emit warning and location for usages of features that should be imported explicitly.
            "-language:existentials",
            "-language:higherKinds",
            "-language:implicitConversions",
            "-language:postfixOps",
            "-indent",
            "-rewrite",
            "-unchecked",
            "-Wunused:imports" // Warn if an import selector is not referenced.
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
