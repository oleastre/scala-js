/*                     __                                               *\
**     ________ ___   / /  ___      __ ____  Scala.js Test Suite        **
**    / __/ __// _ | / /  / _ | __ / // __/  (c) 2013, LAMP/EPFL        **
**  __\ \/ /__/ __ |/ /__/ __ |/_// /_\ \    http://scala-js.org/       **
** /____/\___/_/ |_/____/_/ | |__/ /____/                               **
**                          |/____/                                     **
\*                                                                      */
package scala.scalajs.testsuite.javalib

import java.lang.{Long => JLong}

import org.scalajs.jasminetest.JasmineTest

/**
 * tests the implementation of the java standard library Long
 * requires jsinterop/LongTest to work to make sense
 */
object LongTest extends JasmineTest {

  describe("java.lang.Long") {
    it("should implement bitCount") {
      expect(JLong.bitCount(0L)).toEqual(0)
      expect(JLong.bitCount(35763829229342837L)).toEqual(26)
      expect(JLong.bitCount(-350003829229342837L)).toEqual(32)
    }

    it("should provide `compareTo`") {
      def compare(x: Long, y: Long): Int =
        new JLong(x).compareTo(new JLong(y))

      expect(compare(0L, 5L)).toBeLessThan(0)
      expect(compare(10L, 9L)).toBeGreaterThan(0)
      expect(compare(-2L, -1L)).toBeLessThan(0)
      expect(compare(3L, 3L)).toEqual(0)
    }

    it("should be a Comparable") {
      def compare(x: Any, y: Any): Int =
        x.asInstanceOf[Comparable[Any]].compareTo(y)

      expect(compare(0L, 5L)).toBeLessThan(0)
      expect(compare(10L, 9L)).toBeGreaterThan(0)
      expect(compare(-2L, -1L)).toBeLessThan(0)
      expect(compare(3L, 3L)).toEqual(0)
    }

    it("should parse strings") {
      def test(s: String, v: Long): Unit = {
        expect(JLong.parseLong(s)).toEqual(v)
        expect(JLong.valueOf(s).longValue()).toEqual(v)
        expect(new JLong(s).longValue()).toEqual(v)
      }

      test("0", 0L)
      test("5", 5L)
      test("127", 127L)
      test("-100", -100L)
      test("30000", 30000L)
      test("-90000", -90000L)
      test("4", 4L)
      test("-4", -4L)
      test("4000000000", 4000000000L)
      test("-18014398509482040", -18014398509482040L)
    }

    it("should reject invalid strings when parsing") {
      def test(s: String): Unit =
        expect(() => JLong.parseLong(s)).toThrow

      test("abc")
      test("asdf")
      test("")
    }

    it("should parse strings in base 16") {
      def test(s: String, v: Long): Unit = {
        expect(JLong.parseLong(s, 16)).toEqual(v)
        expect(JLong.valueOf(s, 16).longValue()).toEqual(v)
      }

      test("0", 0x0L)
      test("5", 0x5L)
      test("ff", 0xffL)
      test("-24", -0x24L)
      test("30000", 0x30000L)
      test("-90000", -0x90000L)
    }

    it("should correctly compute trailing zeros") {
      expect(JLong.numberOfTrailingZeros(0xff10000000000000L)).toEqual(52)
      expect(JLong.numberOfTrailingZeros(0xff20000000000000L)).toEqual(53)
      expect(JLong.numberOfTrailingZeros(0xff40000000000000L)).toEqual(54)
      expect(JLong.numberOfTrailingZeros(0xff80000000000000L)).toEqual(55)

      expect(JLong.numberOfTrailingZeros(0x0000010000000000L)).toEqual(40)
      expect(JLong.numberOfTrailingZeros(0x0000020000000000L)).toEqual(41)
      expect(JLong.numberOfTrailingZeros(0x0000040000000000L)).toEqual(42)
      expect(JLong.numberOfTrailingZeros(0x0000080000000000L)).toEqual(43)

      expect(JLong.numberOfTrailingZeros(0x0000000000010000L)).toEqual(16)
      expect(JLong.numberOfTrailingZeros(0x0000000000020000L)).toEqual(17)
      expect(JLong.numberOfTrailingZeros(0x0000000000040000L)).toEqual(18)
      expect(JLong.numberOfTrailingZeros(0x0000000000080000L)).toEqual(19)
    }

  }
}
