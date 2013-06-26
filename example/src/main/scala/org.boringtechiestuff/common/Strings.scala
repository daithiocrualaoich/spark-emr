package org.boringtechiestuff.common

import java.net.URLEncoder

trait Strings {
  implicit def string2ToLowercaseAlphabeticTokens(s: String) = new {
    lazy val toLowercaseAlphabeticTokens: List[String] = {
      val splits = s.split("\\s").toList

      val lowercase = splits map { _.toLowerCase }
      val alphabetic = lowercase map { _.replaceAll("[^a-z]", "") }
      val nonEmpty = alphabetic filter { _.nonEmpty }

      nonEmpty
    }
  }

  implicit def string2Urlencoded(s: String) = new {
    val urlencoded: String = URLEncoder.encode(s, "utf8")
  }
}

object Strings extends Strings