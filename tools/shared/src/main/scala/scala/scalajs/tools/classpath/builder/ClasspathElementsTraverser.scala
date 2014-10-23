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
    CacheUtils.joinVersions(cp.map(readEntriesInClasspathElement _): _*)

  /** Adds the Scala.js classpath entries in a directory or jar.
   *  Returns the accumulated version
   */
  private def readEntriesInClasspathElement(element: File): String = {
    if (!exists(element))
      getDummyVersion(element)
    else if (isDirectory(element))
      traverseDir(element)
    else if (isJARFile(element))
      traverseJar(element)
    else
      sys.error(s"$element (in classpath) exists and is neither JAR or directory")
  }

}
