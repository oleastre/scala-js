/*                     __                                               *\
**     ________ ___   / /  ___      __ ____  Scala.js tools             **
**    / __/ __// _ | / /  / _ | __ / // __/  (c) 2013-2014, LAMP/EPFL   **
**  __\ \/ /__/ __ |/ /__/ __ |/_// /_\ \    http://scala-js.org/       **
** /____/\___/_/ |_/____/_/ | |__/ /____/                               **
**                          |/____/                                     **
\*                                                                      */


package scala.scalajs.tools.classpath.builder

import scala.scalajs.tools.io._

/** A helper trait to traverse an arbitrary classpath element
 *  (i.e. a JAR or a directory).
 */
trait ClasspathElementsTraverser extends JarTraverser
                                    with DirTraverser
                                    with FileSystem {

  protected def traverseClasspathElements(cp: Seq[File]): String =
    CacheUtils.joinVersions(cp.flatMap(readEntriesInClasspathElement _): _*)

  /** Adds the Scala.js classpath entries in a directory or jar.
   *  Returns the accumulated version
   */
  private def readEntriesInClasspathElement(element: File): Option[String] = {
    if (isDirectory(element))
      Some(traverseDir(element))
    else if (isJSFile(element)) {
      handleTopLvlJS(toJSFile(element))
      Some(getGlobalVersion(element))
    } else if (isJARFile(element)) {
      // We assume it is a jar
      Some(traverseJar(element))
    } else
      None
  }

}
