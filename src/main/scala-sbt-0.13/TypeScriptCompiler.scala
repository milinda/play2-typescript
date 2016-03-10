package com.github.mumoshu.play2.typescript

import java.util.Base64

import com.google.common.io.BaseEncoding
import play.PlayExceptions.AssetCompilationException
import java.io.File
import scala.collection.JavaConverters._
import scala.sys.process._
import sbt.IO
import io.Source._
import scalax.file.Path
import scalax.io.JavaConverters._
import scala.annotation.tailrec
import scalax.file.defaultfs.DefaultPath

object TypeScriptCompiler {

  val dependenciesPattern = "/// <reference path=\"([^\"]*)\"".r

  def compile(tsFile: File, options: Seq[String]): (String, Option[String], Seq[File]) = {
    try {
      val parentPath = tsFile.getParentFile.getAbsolutePath
//      val writeDeclarations = Path(tsFile).string.contains("module")
//      val writeDeclarationsOptions = if (writeDeclarations)
//        Seq("--declarations") else Seq.empty

      val dependencies = makeDependenciesList(tsFile)
      println("TypeScript compiling " + tsFile.getName + (if (!dependencies.isEmpty) ", dependencies: " + dependencies.mkString(", ") else ""))
      val writeDeclarationsOptions = Seq.empty
      val cmd = if (System.getProperty("os.name").startsWith("Windows"))
        Seq("cmd", "/C", "tsc")
      else
        Seq("tsc")
      val tempOut = createTempDir()
      val outJsFileName = tsFile.getName.replaceAll("\\.ts$", ".js")
      val outJsMapFileName = tsFile.getName.replaceAll("\\.ts$", ".js.map")

      def pairedOptions(options: Seq[String]): Seq[String] = {
        options match {
          case Seq(a, b, rest @ _*) if a.startsWith("--") && !b.startsWith("--") =>
            s"${a} ${b}" +: rest
          case Seq(a, rest @ _*) =>
            a +: pairedOptions(rest)
          case Seq() =>
            options
        }
      }
      def determineIfCompilingDynamicModules(options: Seq[String]) = options.exists(_ == "--module amd")

      val compilingDynamicModules = determineIfCompilingDynamicModules(pairedOptions(options))
      val outOption = if (compilingDynamicModules)
                        Seq("--outDir", tempOut.getPath)
                      else
                        Seq("--out", tempOut.getPath + "/" + outJsFileName)
      val tscOutput = runCompiler(
        cmd ++ options.filter( _ != "rjs" ) ++ writeDeclarationsOptions ++ outOption ++ Seq(tsFile.getAbsolutePath)
      )
      val outJsFilePaths = {
        val parents = Path.fromString(tsFile.getAbsolutePath).parents.reverse.map(_.name).filter(_.length > 0).tails
        parents.toList.sortBy(_.size).map { parent =>
          parent.foldLeft(Path(tempOut))(_ / _) / outJsFileName
        }
      }

      val outJsMapFilePaths = {
        val parents = Path.fromString(tsFile.getAbsolutePath).parents.reverse.map(_.name).filter(_.length > 0).tails
        parents.toList.sortBy(_.size).map { parent =>
          parent.foldLeft(Path(tempOut))(_ / _) / outJsMapFileName
        }
      }

      val outJsContent = outJsFilePaths.find(_.isFile).map(_.string)
      val sourceMapContent = outJsMapFilePaths.find(_.isFile).map(_.string)

      val inlineSourceMapContent = outJsMapFileName.r.replaceFirstIn(outJsContent.get, toJsonData(sourceMapContent.get))
      assert(outJsContent.isDefined, "One of those files should exist: " + outJsFilePaths)

      val tsOutput = inlineSourceMapContent.replaceAll("\\r\\n", System.getProperty("line.separator")).replaceAll("\\r", "\n")

//      val declarationsFiles = if (writeDeclarations)
//        List(new File(tsFile.getAbsolutePath.replace("\\.ts$", ".d.ts")))
//      else Nil
      val declarationsFiles = Nil
      (tsOutput, None, List(tsFile) ++ declarationsFiles ++ dependencies.map { file => new File(parentPath + "/" + file)})
    } catch {
      case e: TypeScriptCompilationException => {
        throw AssetCompilationException(e.file.orElse(Some(tsFile)), "TypeScript compiler: " + e.message, Some(e.line), Some(e.column))
      }
    }
  }

  private def toJsonData(jsonContent: String): String = {
    "data:application/json;base64," + Base64.getEncoder.encodeToString(jsonContent.getBytes) + "\n"
  }

  private def makeDependenciesList(tsFile: File): Seq[String] = {
    val text = scala.io.Source.fromFile(tsFile).getLines.mkString
    dependenciesPattern.findAllIn(text).matchData.map(_.group(1)).toList
  }

  private val DependencyLine = """^/\* line \d+, (.*) \*/$""".r

  /**
   * Runs TypeScript compiler and returns its stdout
   * @param command
   * @return Compiler's stdout
   */
  private def runCompiler(command: Seq[String]): String = {
    val err = new StringBuilder
    val out = new StringBuilder
    val all = new StringBuilder
    val capturer = ProcessLogger(
      (output: String) => {
        out.append(output + "\n")
        all.append(output + "\n")
      },
      (error: String) => {
        err.append(error + "\n")
        all.append(error + "\n")
      }
    )

    all.append(s"Capturing the output of the command `${command.mkString(" ")}`:")
    val process = Process(command, None, System.getenv().asScala.toSeq:_*).run(capturer)
    if (process.exitValue == 0) {
      out.mkString
    } else
      throw new TypeScriptCompilationException(all.toString)
  }

  private val LocationLine = """\s*on line (\d+) of (.*)""".r

  private class TypeScriptCompilationException(stderr: String) extends RuntimeException {

    val (file: Option[File], line: Int, column: Int, message: String) = parseError(stderr)

    private def parseError(error: String): (Option[File], Int, Int, String) = {
      var line = 0
      var seen = 0
      var column = 0
      var file : Option[File] = None
      var message = "Unknown error, try running tsc directly"
      for (errline: String <- augmentString(error).lines) {
        errline match {
          case LocationLine(l, f) => { line = l.toInt; file = Some(new File(f)); }
          case other if (seen == 0) => { message = other; seen += 1 }
          case other =>
        }
      }
      (file, line, column, message)
    }
  }

  def createTempDir(): File = {
    val baseDir = new File(System.getProperty("java.io.tmpdir"))
    val baseName = System.currentTimeMillis() + "-"

    val TempDirAttempts = 100
    (1 to TempDirAttempts).toStream.flatMap { counter =>
      val tempDir = new File(baseDir, baseName + counter)
      if (tempDir.mkdir()) {
        Some(tempDir)
      } else {
        None
      }
    }.headOption.getOrElse {
      throw new IllegalStateException("Failed to create directory within "
        + TempDirAttempts + " attempts (tried "
        + baseName + "0 to " + baseName + (TempDirAttempts - 1) + ')')
    }
  }
}
