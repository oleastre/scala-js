package scala.runtime

import scala.math.ScalaNumber

object BoxesRunTime {
  def boxToBoolean(b: Boolean): java.lang.Boolean = java.lang.Boolean.valueOf(b)
  def boxToCharacter(c: Char): java.lang.Character = java.lang.Character.valueOf(c)
  def boxToByte(b: Byte): java.lang.Byte = java.lang.Byte.valueOf(b)
  def boxToShort(s: Short): java.lang.Short = java.lang.Short.valueOf(s)
  def boxToInteger(i: Int): java.lang.Integer = java.lang.Integer.valueOf(i)
  def boxToLong(l: Long): java.lang.Long = java.lang.Long.valueOf(l)
  def boxToFloat(f: Float): java.lang.Float = java.lang.Float.valueOf(f)
  def boxToDouble(d: Double): java.lang.Double = java.lang.Double.valueOf(d)

  def unboxToBoolean(b: Object): Boolean = if (b eq null) false else b.asInstanceOf[java.lang.Boolean].booleanValue()
  def unboxToChar(c: Object): Char = if (c eq null) 0 else c.asInstanceOf[java.lang.Character].charValue()
  def unboxToByte(b: Object): Byte = if (b eq null) 0 else b.asInstanceOf[java.lang.Byte].byteValue()
  def unboxToShort(s: Object): Short = if (s eq null) 0 else s.asInstanceOf[java.lang.Short].shortValue()
  def unboxToInt(i: Object): Int = if (i eq null) 0 else i.asInstanceOf[java.lang.Integer].intValue()
  def unboxToLong(l: Object): Long = if (l eq null) 0 else l.asInstanceOf[java.lang.Long].longValue()
  def unboxToFloat(f: Object): Float = if (f eq null) 0 else f.asInstanceOf[java.lang.Float].floatValue()
  def unboxToDouble(d: Object): Double = if (d eq null) 0 else d.asInstanceOf[java.lang.Double].doubleValue()

  def equals(x: Object, y: Object): Boolean =
    if (x eq y) true
    else equals2(x, y)

  @inline // only called by equals(), not by codegen
  def equals2(x: Object, y: Object): Boolean = {
    x match {
      case xn: java.lang.Number    => equalsNumObject(xn, y)
      case xc: java.lang.Character => equalsCharObject(xc, y)
      case null                    => y eq null
      case _                       => x.equals(y)
    }
  }

  def equalsNumObject(xn: java.lang.Number, y: Object): Boolean = {
    y match {
      case yn: java.lang.Number    => equalsNumNum(xn, yn)
      case yc: java.lang.Character => equalsNumChar(xn, yc)
      case _ =>
        if (xn eq null)
          y eq null
        else
          xn.equals(y)
    }
  }

  def equalsNumNum(xn: java.lang.Number, yn: java.lang.Number): Boolean = {
    (xn: Any) match {
      case xn: Double =>
        (yn: Any) match {
          case yn: Double      => xn == yn
          case yn: Long        => xn == yn
          case yn: ScalaNumber => yn.equals(xn) // xn is not a ScalaNumber
          case _               => false         // xn.equals(yn) must be false here
        }
      case xn: Long =>
        (yn: Any) match {
          case yn: Long        => xn == yn
          case yn: Double      => xn == yn
          case yn: ScalaNumber => yn.equals(xn) // xn is not a ScalaNumber
          case _               => false         // xn.equals(yn) must be false here
        }
      case null => yn eq null
      case _    => xn.equals(yn)
    }
  }

  def equalsCharObject(xc: java.lang.Character, y: Object): Boolean = {
    y match {
      case yc: java.lang.Character => xc.charValue() == yc.charValue()
      case yn: java.lang.Number    => equalsNumChar(yn, xc)
      case _ =>
        if (xc eq null)
          y eq null
        else
          false // xc.equals(y) must be false here, because y is not a Char
    }
  }

  @inline
  private def equalsNumChar(xn: java.lang.Number, yc: java.lang.Character): Boolean = {
    (xn: Any) match {
      case xn: Double => xn == yc.charValue()
      case xn: Long   => xn == yc.charValue()
      case _ =>
        if (xn eq null) yc eq null
        else xn.equals(yc)
    }
  }

  def hashFromLong(n: java.lang.Long): Int = {
    val iv = n.intValue()
    if (iv == n.longValue()) iv
    else n.hashCode()
  }

  def hashFromDouble(n: java.lang.Double): Int = {
    val iv = n.intValue()
    val dv = n.doubleValue()
    if (iv == dv) {
      iv
    } else {
      val lv = n.longValue()
      if (lv == dv) {
        java.lang.Long.valueOf(lv).hashCode()
      } else {
        // don't test the case floatValue() == dv
        n.hashCode()
      }
    }
  }

  def hashFromFloat(n: java.lang.Float): Int = {
    hashFromDouble(java.lang.Double.valueOf(n.doubleValue()))
  }

  def hashFromNumber(n: java.lang.Number): Int = {
    (n: Any) match {
      case n: Int              => n
      case n: java.lang.Long   => hashFromLong(n)
      case n: java.lang.Double => hashFromDouble(n)
      case n                   => n.hashCode()
    }
  }

  def hashFromObject(a: Object): Int = {
    a match {
      case a: java.lang.Number => hashFromNumber(a)
      case a                   => a.hashCode()
    }
  }
}
