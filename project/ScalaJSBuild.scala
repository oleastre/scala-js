import sbt._
import Keys._

import bintray.Plugin.bintrayPublishSettings
import bintray.Keys.{repository, bintrayOrganization, bintray}

import java.io.{
  BufferedOutputStream,
  FileOutputStream,
  BufferedWriter,
  FileWriter
}

import scala.collection.mutable
import scala.util.Properties

import scala.scalajs.ir
import scala.scalajs.sbtplugin._
import scala.scalajs.sbtplugin.env.rhino.RhinoJSEnv
import ScalaJSPlugin._
import ScalaJSKeys._
import ExternalCompile.scalaJSExternalCompileSettings
import Implicits._

import scala.scalajs.tools.sourcemap._
import scala.scalajs.tools.io.MemVirtualJSFile

import sbtassembly.Plugin.{AssemblyKeys, assemblySettings}
import AssemblyKeys.{assembly, assemblyOption}

object ScalaJSBuild extends Build {

  val fetchedScalaSourceDir = settingKey[File](
    "Directory the scala source is fetched to")
  val fetchScalaSource = taskKey[File](
    "Fetches the scala source for the current scala version")
  val shouldPartest = settingKey[Boolean](
    "Whether we should partest the current scala version (and fail if we can't)")

  val extraVersion = "-os1"
  val commonSettings = Defaults.defaultSettings ++ Seq(
      organization := "org.scala-lang.modules.scalajs",
      version := scalaJSVersion,

      normalizedName ~= {
        _.replace("scala.js", "scalajs").replace("scala-js", "scalajs")
      },

      homepage := Some(url("http://scala-js.org/")),
      licenses += ("BSD New",
          url("https://github.com/scala-js/scala-js/blob/master/LICENSE")),

      shouldPartest := {
        val testListDir = (
          (resourceDirectory in (partestSuite, Test)).value / "scala"
            / "tools" / "partest" / "scalajs" / scalaVersion.value
        )
        testListDir.exists
      }
  )

  private val snapshotsOrReleases =
    if (scalaJSIsSnapshotVersion) "snapshots" else "releases"

  private def publishToScalaJSRepoSettings = Seq(
      publishTo := {
        Seq("PUBLISH_USER", "PUBLISH_PASS").map(Properties.envOrNone) match {
          case Seq(Some(user), Some(pass)) =>
            Some(Resolver.sftp(
                s"scala-js-$snapshotsOrReleases",
                "repo.scala-js.org",
                s"/home/scalajsrepo/www/repo/$snapshotsOrReleases")(
                Resolver.ivyStylePatterns) as (user, pass))
          case _ =>
            None
        }
      }
  )

  private def publishToBintraySettings = (
      bintrayPublishSettings
  ) ++ Seq(
      repository in bintray := "scala-js-releases",
      bintrayOrganization in bintray := Some("scala-js")
  )

  val publishSettings = (
      if (Properties.envOrNone("PUBLISH_TO_BINTRAY") == Some("true"))
        publishToBintraySettings
      else
        publishToScalaJSRepoSettings
  ) ++ Seq(
      publishMavenStyle := false
  )

  val defaultSettings = commonSettings ++ Seq(
      scalaVersion := "2.11.0",
      scalacOptions ++= Seq(
          "-deprecation",
          "-unchecked",
          "-feature",
          "-encoding", "utf8"
      )
  )

  val myScalaJSSettings = ScalaJSPluginInternal.scalaJSAbstractSettings ++ Seq(
      autoCompilerPlugins := true,
      checkScalaJSIR := true
  )

  val scalaJSSourceMapSettings = scalacOptions ++= {
    if (scalaJSIsSnapshotVersion) Seq()
    else Seq(
      // Link source maps to github sources
      "-P:scalajs:mapSourceURI:" + root.base.toURI +
      "->https://raw.githubusercontent.com/scala-js/scala-js/v" +
      scalaJSVersion + "/"
    )
  }

  /** Depend library as if (exportJars in library) was set to false */
  val compileWithLibrarySetting = {
    internalDependencyClasspath in Compile ++= {
      val prods = (products in (library, Compile)).value
      val analysis = (compile in (library, Compile)).value

      prods.map(p => Classpaths.analyzed(p, analysis))
    }
  }

  // Used when compiling the compiler, adding it to scalacOptions does not help
  scala.util.Properties.setProp("scalac.patmat.analysisBudget", "1024")

  override lazy val settings = super.settings :+ {
    // Most of the projects cross-compile
    crossScalaVersions := Seq(
      "2.10.2",
      "2.10.3",
      "2.10.4",
      "2.11.0",
      "2.11.1",
      "2.11.2"
    )
  }

  lazy val root: Project = Project(
      id = "scalajs",
      base = file("."),
      settings = defaultSettings ++ Seq(
          name := "Scala.js",
          publishArtifact in Compile := false,

          clean := clean.dependsOn(
              // compiler, library and jasmineTestFramework are aggregated
              clean in tools, clean in toolsJS, clean in plugin,
              clean in javalanglib, clean in javalib, clean in scalalib,
              clean in libraryAux, clean in javalibEx,
              clean in examples, clean in helloworld,
              clean in reversi, clean in testingExample,
              clean in testSuite, clean in noIrCheckTest,
              clean in javalibExTestSuite,
              clean in partest, clean in partestSuite).value,

          publish := {},
          publishLocal := {}
      )
  ).aggregate(
      compiler, library, testBridge, jasmineTestFramework
  )

  /* This project is not really used in the build. Its sources are actually
   * added as sources of the `compiler` project (and meant to be added to the
   * `tools` project as well).
   * It exists mostly to be used in IDEs, because they don't like very much to
   * have code in a project that lives outside the project's directory, and
   * like even less that code be shared by different projects.
   */
  lazy val irProject: Project = Project(
      id = "ir",
      base = file("ir"),
      settings = defaultSettings ++ Seq(
          name := "Scala.js IR",
          exportJars := true
      )
  )

  lazy val compiler: Project = Project(
      id = "compiler",
      base = file("compiler"),
      settings = defaultSettings ++ publishSettings ++ Seq(
          name := "Scala.js compiler",
          crossVersion := CrossVersion.full, // because compiler api is not binary compatible
          unmanagedSourceDirectories in Compile +=
            (scalaSource in (irProject, Compile)).value,
          libraryDependencies ++= Seq(
              "org.scala-lang" % "scala-compiler" % scalaVersion.value,
              "org.scala-lang" % "scala-reflect" % scalaVersion.value,
              "com.novocode" % "junit-interface" % "0.9" % "test"
          ),
          testOptions += Tests.Setup { () =>
            val testOutDir = (streams.value.cacheDirectory / "scalajs-compiler-test")
            IO.createDirectory(testOutDir)
            sys.props("scala.scalajs.compiler.test.output") =
              testOutDir.getAbsolutePath
            sys.props("scala.scalajs.compiler.test.scalajslib") =
              (artifactPath in (library, Compile, packageBin)).value.getAbsolutePath
            sys.props("scala.scalajs.compiler.test.scalalib") = {

              def isScalaLib(att: Attributed[File]) = {
                att.metadata.get(moduleID.key).exists { mId =>
                  mId.organization == "org.scala-lang" &&
                  mId.name         == "scala-library"  &&
                  mId.revision     == scalaVersion.value
                }
              }

              val lib = (managedClasspath in Test).value.find(isScalaLib)
              lib.map(_.data.getAbsolutePath).getOrElse {
                streams.value.log.error("Couldn't find Scala library on the classpath. CP: " + (managedClasspath in Test).value); ""
              }
            }
          },
          exportJars := true
      )
  )

  val commonToolsSettings = Seq(
      name := "Scala.js tools",

      unmanagedSourceDirectories in Compile ++= Seq(
        baseDirectory.value.getParentFile / "shared/src/main/scala",
        (scalaSource in (irProject, Compile)).value),

      sourceGenerators in Compile <+= Def.task {
        ScalaJSEnvGenerator.generateEnvHolder(
          baseDirectory.value.getParentFile,
          (sourceManaged in Compile).value)
      }
  )

  lazy val tools: Project = Project(
      id = "tools",
      base = file("tools/jvm"),
      settings = defaultSettings ++ publishSettings ++ (
          commonToolsSettings
      ) ++ Seq(
          version := scalaJSVersion + extraVersion,
          scalaVersion := "2.10.4",

          libraryDependencies ++= Seq(
              "com.google.javascript" % "closure-compiler" % "v20130603",
              "com.googlecode.json-simple" % "json-simple" % "1.1.1",
              "com.novocode" % "junit-interface" % "0.9" % "test"
          )
      )
  )

  lazy val toolsJS: Project = Project(
      id = "toolsJS",
      base = file("tools/js"),
      settings = defaultSettings ++ myScalaJSSettings ++ publishSettings ++ (
          commonToolsSettings
      ) ++ Seq(
          crossVersion := ScalaJSCrossVersion.binary
      ) ++ inConfig(Test) {
        // Redefine test to run Node.js and link HelloWorld
        val stagedRunSetting = test := {
          val cp = {
            for (e <- (fullClasspath in Test).value)
              yield JSUtils.toJSstr(e.data.getAbsolutePath)
          }

          val runCode = """
            var framework = scala.scalajs.test.JasmineTestFramework();
            framework.setTags("typedarray")

            // Load tests (we know we only export test modules, so we can use all exports)
            var testPackage = scala.scalajs.test;
            for (var pName in testPackage)
              for (var testName in testPackage[pName])
                if (!(pName == "internal" && testName == "ConsoleTestOutput"))
                  testPackage[pName][testName]();

            var reporter = new scalajs.JasmineConsoleReporter(true);

            // Setup and run Jasmine
            var jasmineEnv = jasmine.getEnv();
            jasmineEnv.addReporter(reporter);
            jasmineEnv.execute();
          """

          val code = {
            s"""
            var lib = scalajs.QuickLinker().linkNode(${cp.mkString(", ")});
            var run = ${JSUtils.toJSstr(runCode)};

            eval("(function() { " + lib + "; " + run + "}).call(this);");
            """
          }

          val launcher = new MemVirtualJSFile("Generated launcher file")
            .withContent(code)

          jsEnv.value.runJS(scalaJSExecClasspath.value, launcher,
              streams.value.log, scalaJSConsole.value)
        }

        Seq(test := error("Can't run toolsJS/test in preLink stage")) ++
        inTask(fastOptStage)(stagedRunSetting) ++
        inTask(fullOptStage)(stagedRunSetting)
      }
  ).dependsOn(compiler % "plugin", javalibEx, testSuite % "test->test")

  lazy val plugin: Project = Project(
      id = "sbtPlugin",
      base = file("sbt-plugin"),
      settings = commonSettings ++ publishSettings ++ Seq(
          name := "Scala.js sbt plugin",
          version := scalaJSVersion + extraVersion,
          sbtPlugin := true,
          scalaBinaryVersion :=
            CrossVersion.binaryScalaVersion(scalaVersion.value),
          libraryDependencies ++= Seq(
              "org.mozilla" % "rhino" % "1.7R4",
              "org.webjars" % "envjs" % "1.2",
              "com.novocode" % "junit-interface" % "0.9" % "test"
          )
      )
  ).dependsOn(tools)

  lazy val delambdafySetting = {
    scalacOptions ++= (
        if (scalaBinaryVersion.value == "2.10") Seq()
        else Seq("-Ydelambdafy:method"))
  }

  private def serializeHardcodedIR(base: File,
      infoAndTree: (ir.Infos.ClassInfo, ir.Trees.ClassDef)): File = {
    // We assume that there are no weird characters in the full name
    val fullName = infoAndTree._1.name
    val output = base / (fullName.replace('.', '/') + ".sjsir")

    if (!output.exists()) {
      IO.createDirectory(output.getParentFile)
      val stream = new BufferedOutputStream(new FileOutputStream(output))
      try {
        ir.InfoSerializers.serialize(stream, infoAndTree._1)
        ir.Serializers.serialize(stream, infoAndTree._2)
      } finally {
        stream.close()
      }
    }
    output
  }

  lazy val javalanglib: Project = Project(
      id = "javalanglib",
      base = file("javalanglib"),
      settings = defaultSettings ++ myScalaJSSettings ++ Seq(
          name := "java.lang library for Scala.js",
          publishArtifact in Compile := false,
          delambdafySetting,
          scalacOptions += "-Yskip:cleanup,icode,jvm",
          scalaJSSourceMapSettings,
          compileWithLibrarySetting,

          resourceGenerators in Compile <+= Def.task {
            val base = (resourceManaged in Compile).value
            Seq(
                serializeHardcodedIR(base, JavaLangObject.InfoAndTree),
                serializeHardcodedIR(base, JavaLangString.InfoAndTree)
            )
          }
      ) ++ (
          scalaJSExternalCompileSettings
      )
  ).dependsOn(compiler % "plugin")

  lazy val javalib: Project = Project(
      id = "javalib",
      base = file("javalib"),
      settings = defaultSettings ++ myScalaJSSettings ++ Seq(
          name := "Java library for Scala.js",
          publishArtifact in Compile := false,
          delambdafySetting,
          scalacOptions += "-Yskip:cleanup,icode,jvm",
          scalaJSSourceMapSettings,
          compileWithLibrarySetting
      ) ++ (
          scalaJSExternalCompileSettings
      )
  ).dependsOn(compiler % "plugin")

  lazy val scalalib: Project = Project(
      id = "scalalib",
      base = file("scalalib"),
      settings = defaultSettings ++ myScalaJSSettings ++ Seq(
          name := "Scala library for Scala.js",
          publishArtifact in Compile := false,
          delambdafySetting,
          compileWithLibrarySetting,

          // The Scala lib is full of warnings we don't want to see
          scalacOptions ~= (_.filterNot(
              Set("-deprecation", "-unchecked", "-feature") contains _)),


          scalacOptions ++= List(
            // Do not generate .class files
            "-Yskip:cleanup,icode,jvm",
            // Tell plugin to hack fix bad classOf trees
            "-P:scalajs:fixClassOf",
            // Link source maps to github sources of original Scalalib
            "-P:scalajs:mapSourceURI:" +
            fetchedScalaSourceDir.value.toURI +
            "->https://raw.githubusercontent.com/scala/scala/v" +
            scalaVersion.value + "/"
            ),

          // Link sources in override directories to our GitHub repo
          scalaJSSourceMapSettings,

          fetchedScalaSourceDir := (
            baseDirectory.value / "fetchedSources" /
            scalaVersion.value
          ),

          fetchScalaSource := {
            import org.eclipse.jgit.api._

            val s = streams.value
            val ver = scalaVersion.value
            val trgDir = fetchedScalaSourceDir.value

            if (!trgDir.exists) {
              s.log.info(s"Fetching Scala source version $ver")

              // Make parent dirs and stuff
              IO.createDirectory(trgDir)

              // Clone scala source code
              new CloneCommand()
                .setDirectory(trgDir)
                .setURI("https://github.com/scala/scala.git")
                .call()

            }

            // Checkout proper ref. We do this anyway so we fail if
            // something is wrong
            val git = Git.open(trgDir)
            s.log.info(s"Checking out Scala source version $ver")
            git.checkout().setName(s"v$ver").call()

            trgDir
          },

          unmanagedSourceDirectories in Compile := {
            // Calculates all prefixes of the current Scala version
            // (including the empty prefix) to construct override
            // directories like the following:
            // - override-2.10.2-RC1
            // - override-2.10.2
            // - override-2.10
            // - override-2
            // - override
            val ver = scalaVersion.value
            val base = baseDirectory.value
            val parts = ver.split(Array('.','-'))
            val verList = parts.inits.map { ps =>
              val len = ps.mkString(".").length
              // re-read version, since we lost '.' and '-'
              ver.substring(0, len)
            }
            def dirStr(v: String) =
              if (v.isEmpty) "overrides" else s"overrides-$v"
            val dirs = verList.map(base / dirStr(_)).filter(_.exists)
            dirs.toSeq // most specific shadow less specific
          },

          // Compute sources
          // Files in earlier src dirs shadow files in later dirs
          sources in Compile := {
            // Sources coming from the sources of Scala
            val scalaSrcDir = fetchScalaSource.value / "src"
            val scalaSrcDirs = (scalaSrcDir / "library") :: (
                if (!scalaVersion.value.startsWith("2.10.")) Nil
                else (scalaSrcDir / "continuations" / "library") :: Nil)

            // All source directories (overrides shadow scalaSrcDirs)
            val sourceDirectories =
              (unmanagedSourceDirectories in Compile).value ++ scalaSrcDirs

            // Filter sources with overrides
            def normPath(f: File): String =
              f.getPath.replace(java.io.File.separator, "/")

            val sources = mutable.ListBuffer.empty[File]
            val paths = mutable.Set.empty[String]

            for {
              srcDir <- sourceDirectories
              normSrcDir = normPath(srcDir)
              src <- (srcDir ** "*.scala").get
            } {
              val normSrc = normPath(src)
              val path = normSrc.substring(normSrcDir.length)
              val useless =
                path.contains("/scala/collection/parallel/") ||
                path.contains("/scala/util/parsing/")
              if (!useless) {
                if (paths.add(path))
                  sources += src
                else
                  streams.value.log.debug(s"not including $src")
              }
            }

            sources.result()
          },

          // Continuation plugin (when using 2.10.x)
          autoCompilerPlugins := true,
          libraryDependencies ++= {
            val ver = scalaVersion.value
            if (ver.startsWith("2.10."))
              Seq(compilerPlugin("org.scala-lang.plugins" % "continuations" % ver))
            else
              Nil
          },
          scalacOptions ++= {
            if (scalaVersion.value.startsWith("2.10."))
              Seq("-P:continuations:enable")
            else
              Nil
          }
      ) ++ (
          scalaJSExternalCompileSettings
      )
  ).dependsOn(compiler % "plugin")

  lazy val libraryAux: Project = Project(
      id = "libraryAux",
      base = file("library-aux"),
      settings = defaultSettings ++ myScalaJSSettings ++ Seq(
          name := "Scala.js aux library",
          publishArtifact in Compile := false,
          delambdafySetting,
          scalacOptions += "-Yskip:cleanup,icode,jvm",
          scalaJSSourceMapSettings,
          compileWithLibrarySetting
      ) ++ (
          scalaJSExternalCompileSettings
      )
  ).dependsOn(compiler % "plugin")

  lazy val library: Project = Project(
      id = "library",
      base = file("library"),
      settings = defaultSettings ++ publishSettings ++ myScalaJSSettings ++ Seq(
          name := "Scala.js library",
          delambdafySetting,
          scalaJSSourceMapSettings,
          scalacOptions in (Compile, doc) += "-implicits",
          exportJars := true
      ) ++ (
          scalaJSExternalCompileSettings
      ) ++ inConfig(Compile)(Seq(
          /* Add the .sjsir files from other lib projects
           * (but not .class files)
           */
          mappings in packageBin ++= {
            val allProducts = (
                (products in javalanglib).value ++
                (products in javalib).value ++
                (products in scalalib).value ++
                (products in libraryAux).value)
            val filter = ("*.sjsir": NameFilter)
            allProducts.flatMap(base => Path.selectSubpaths(base, filter))
          }
      ))
  ).dependsOn(compiler % "plugin")

  lazy val javalibEx: Project = Project(
      id = "javalibEx",
      base = file("javalib-ex"),
      settings = defaultSettings ++ publishSettings ++ myScalaJSSettings ++ Seq(
          name := "Scala.js JavaLib Ex",
          delambdafySetting,
          scalacOptions += "-Yskip:cleanup,icode,jvm",
          scalaJSSourceMapSettings,
          exportJars := true,
          jsDependencies +=
            "org.webjars" % "jszip" % "2.4.0" / "jszip.min.js" commonJSName "JSZip"
      ) ++ (
          scalaJSExternalCompileSettings
      )
  ).dependsOn(compiler % "plugin", library)

  lazy val stubs: Project = Project(
      id = "stubs",
      base = file("stubs"),
      settings = defaultSettings ++ publishSettings ++ Seq(
          name := "Scala.js Stubs",
          sources in Compile ++= {
            val annotFiles = Set(
                "JSBracketAccess.scala",
                "JSExport.scala",
                "JSExportAll.scala",
                "JSExportDescendentObjects.scala",
                "JSExportNamed.scala"
            )

            val libSrcDir =
              (scalaSource in library in Compile).value.getAbsoluteFile
            val annotDir = libSrcDir / "scala/scalajs/js/annotation/"

            val filter = new SimpleFilter(annotFiles)
            (annotDir * filter).get
          }
      )
  )

  // Scala.js command line interface
  lazy val cli: Project = Project(
      id = "cli",
      base = file("cli"),
      settings = defaultSettings ++ assemblySettings ++ Seq(
          name := "Scala.js CLI",
          scalaVersion := "2.10.4", // adapt version to tools
          libraryDependencies ++= Seq(
              "com.github.scopt" %% "scopt" % "3.2.0"
          ),

          // assembly options
          mainClass in assembly := None, // don't want an executable JAR
          assemblyOption in assembly ~= { _.copy(includeScala = false) },
          AssemblyKeys.jarName in assembly :=
            s"${normalizedName.value}-assembly_${scalaBinaryVersion.value}-${version.value}.jar"
      )
  ).dependsOn(tools)

  // Test framework
  lazy val testBridge = Project(
      id = "testBridge",
      base = file("test-bridge"),
      settings = defaultSettings ++ publishSettings ++ myScalaJSSettings ++ Seq(
          name := "Scala.js test bridge",
          delambdafySetting,
          scalaJSSourceMapSettings
      )
  ).dependsOn(compiler % "plugin", library)

  lazy val jasmineTestFramework = Project(
      id = "jasmineTestFramework",
      base = file("jasmine-test-framework"),
      settings = defaultSettings ++ publishSettings ++ myScalaJSSettings ++ Seq(
          name := "Scala.js jasmine test framework",

          jsDependencies ++= Seq(
            ProvidedJS / "jasmine-polyfills.js",
            "org.webjars" % "jasmine" % "1.3.1" /
              "jasmine.js" dependsOn "jasmine-polyfills.js"
          ),
          scalaJSSourceMapSettings
      )
  ).dependsOn(compiler % "plugin", library, testBridge)

  // Examples

  lazy val examples: Project = Project(
      id = "examples",
      base = file("examples"),
      settings = defaultSettings ++ Seq(
          name := "Scala.js examples"
      )
  ).aggregate(helloworld, reversi, testingExample)

  lazy val exampleSettings = defaultSettings ++ myScalaJSSettings

  lazy val helloworld: Project = Project(
      id = "helloworld",
      base = file("examples") / "helloworld",
      settings = exampleSettings ++ Seq(
          name := "Hello World - Scala.js example",
          moduleName := "helloworld",
          persistLauncher := true
      )
  ).dependsOn(compiler % "plugin", library)

  lazy val reversi = Project(
      id = "reversi",
      base = file("examples") / "reversi",
      settings = exampleSettings ++ Seq(
          name := "Reversi - Scala.js example",
          moduleName := "reversi"
      )
  ).dependsOn(compiler % "plugin", library)

  lazy val testingExample = Project(
      id = "testingExample",
      base = file("examples") / "testing",
      settings = exampleSettings ++ Seq(
          name := "Testing - Scala.js example",
          moduleName := "testing",

          jsDependencies ++= Seq(
            RuntimeDOM % "test",
            "org.webjars" % "jquery" % "1.10.2" / "jquery.js" % "test"
          )
      )
  ).dependsOn(compiler % "plugin", library, jasmineTestFramework % "test")

  // Testing

  lazy val testSuite: Project = Project(
      id = "testSuite",
      base = file("test-suite"),
      settings = defaultSettings ++ myScalaJSSettings ++ Seq(
          name := "Scala.js test suite",
          publishArtifact in Compile := false,

          scalacOptions ~= (_.filter(_ != "-deprecation")),

          sources in Test ++= {
            if (!scalaVersion.value.startsWith("2.10") &&
                scalacOptions.value.contains("-Xexperimental")) {
              (((sourceDirectory in Test).value / "require-sam") ** "*.scala").get
            } else {
              Nil
            }
          },

          /* Generate a scala source file that throws exceptions in
             various places (while attaching the source line to the
             exception). When we catch the exception, we can then
             compare the attached source line and the source line
             calculated via the source maps.

             see test-suite/src/test/resources/SourceMapTestTemplate.scala
           */
          sourceGenerators in Test <+= Def.task {
            val dir = (sourceManaged in Test).value
            IO.createDirectory(dir)

            val template = IO.read((resourceDirectory in Test).value /
              "SourceMapTestTemplate.scala")

            def lineNo(cs: CharSequence) =
              (0 until cs.length).count(i => cs.charAt(i) == '\n') + 1

            var i = 0
            val pat = "/\\*{2,3}/".r
            val replaced = pat.replaceAllIn(template, { mat =>
              val lNo = lineNo(mat.before)
              val res =
                if (mat.end - mat.start == 5)
                  // matching a /***/
                  s"if (TC.is($i)) { throw new TestException($lNo) } else "
                else
                  // matching a /**/
                  s"; if (TC.is($i)) { throw new TestException($lNo) } ;"

              i += 1

              res
            })

            val outFile = dir / "SourceMapTest.scala"
            IO.write(outFile, replaced.replace("0/*<testCount>*/", i.toString))
            Seq(outFile)
          }
      )
  ).dependsOn(compiler % "plugin", library, jasmineTestFramework % "test")

  lazy val noIrCheckTest: Project = Project(
      id = "noIrCheckTest",
      base = file("no-ir-check-test"),
      settings = defaultSettings ++ myScalaJSSettings ++ Seq(
          name := "Scala.js not IR checked tests",
          checkScalaJSIR := false,
          publishArtifact in Compile := false
     )
  ).dependsOn(compiler % "plugin", library, jasmineTestFramework % "test")

  lazy val javalibExTestSuite: Project = Project(
      id = "javalibExTestSuite",
      base = file("javalib-ex-test-suite"),
      settings = defaultSettings ++ myScalaJSSettings ++ Seq(
          name := "JavaLib Ex Test Suite",
          publishArtifact in Compile := false,

          scalacOptions in Test ~= (_.filter(_ != "-deprecation"))
      )
  ).dependsOn(compiler % "plugin", javalibEx, jasmineTestFramework % "test")

  lazy val partest: Project = Project(
      id = "partest",
      base = file("partest"),
      settings = defaultSettings ++ Seq(
          name := "Partest for Scala.js",
          moduleName := "scalajs-partest",

          resolvers += Resolver.typesafeIvyRepo("releases"),

          libraryDependencies ++= {
            if (shouldPartest.value)
              Seq(
                "org.scala-sbt" % "sbt" % "0.13.0",
                "org.scala-lang.modules" %% "scala-partest" % "1.0.0",
                "com.google.javascript" % "closure-compiler" % "v20130603",
                "org.mozilla" % "rhino" % "1.7R4",
                "com.googlecode.json-simple" % "json-simple" % "1.1.1"
              )
            else Seq()
          },

          unmanagedSourceDirectories in Compile ++= {
            val pluginBase = ((scalaSource in (plugin, Compile)).value /
                "scala/scalajs/sbtplugin")
            Seq(
              pluginBase / "env",
              pluginBase / "sourcemap"
            )
          },

          sources in Compile := {
            if (shouldPartest.value) {
              // Partest sources and some sources of sbtplugin (see above)
              val baseSrcs = (sources in Compile).value
              // Sources for tools (and hence IR)
              val toolSrcs = (sources in (tools, Compile)).value
              // Individual sources from the sbtplugin
              val pluginSrcs = {
                val d = (scalaSource in (plugin, Compile)).value
                Seq(d / "scala/scalajs/sbtplugin/JSUtils.scala")
              }
              toolSrcs ++ baseSrcs ++ pluginSrcs
            } else Seq()
          }

      )
  ).dependsOn(compiler)

  lazy val partestSuite: Project = Project(
      id = "partestSuite",
      base = file("partest-suite"),
      settings = defaultSettings ++ Seq(
          name := "Scala.js partest suite",

          fork in Test := true,
          javaOptions in Test += "-Xmx1G",

          testFrameworks ++= {
            if (shouldPartest.value)
              Seq(new TestFramework("scala.tools.partest.scalajs.Framework"))
            else Seq()
          },

          definedTests in Test ++= {
            if (shouldPartest.value)
              Seq(new sbt.TestDefinition(
                s"partest-${scalaVersion.value}",
                // marker fingerprint since there are no test classes
                // to be discovered by sbt:
                new sbt.testing.AnnotatedFingerprint {
                  def isModule = true
                  def annotationName = "partest"
                },
                true,
                Array()
              ))
            else Seq()
          }
      )
  ).dependsOn(partest % "test", library)
}
