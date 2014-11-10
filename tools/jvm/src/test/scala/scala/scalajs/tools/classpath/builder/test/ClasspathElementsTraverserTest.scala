package scala.scalajs.tools.classpath.builder.test

import scala.scalajs.tools.classpath.builder._
import scala.scalajs.tools.jsdep.JSDependencyManifest
import scala.scalajs.tools.io.VirtualScalaJSIRFile
import scala.scalajs.tools.io.VirtualJSFile

import java.io.File

import org.junit.Test
import org.junit.Assert._

class ClasspathElementsTraverserTest {
  class TestElementTraverser extends ClasspathElementsTraverser with PhysicalFileSystem {
    protected def handleDepManifest(m: => JSDependencyManifest): Unit = ???
    protected def handleIR(relPath: String, ir: => VirtualScalaJSIRFile): Unit = ???
    protected def handleJS(js: => VirtualJSFile): Unit = ???

    def test(f: File): String = traverseClasspathElements(Seq(f))
  }

  val cpElementTraverser = new TestElementTraverser()

  @Test
  def testNotExistingJarFile(): Unit = {
    val res = cpElementTraverser.test(new File("dummy.jar"))
    assertTrue(res.endsWith(cpElementTraverser.DummyVersion))
  }

  @Test
  def testExistingDirectory(): Unit = {
    val res = cpElementTraverser.test(new File("tools/jvm/src/test/resources"))
    assertTrue(res.indexOf(cpElementTraverser.DummyVersion) < 0)
  }

  @Test
  def testExistingJarFile(): Unit = {
    val res = cpElementTraverser.test(new File("tools/jvm/src/test/resources/test.jar"))
    assertTrue(res.indexOf(cpElementTraverser.DummyVersion) < 0)
  }
}

