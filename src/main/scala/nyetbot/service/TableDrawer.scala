package nyetbot.service

import eu.timepit.refined.refineV
import eu.timepit.refined.auto.*
import eu.timepit.refined.numeric.*
import eu.timepit.refined.api.{RefType, Refined}
import eu.timepit.refined.boolean.*
import eu.timepit.refined.char.*
import eu.timepit.refined.collection.*
import eu.timepit.refined.generic.*
import eu.timepit.refined.string.*

def buildTable[A](
    table: List[List[String] Refined Size[Equal[A]]] Refined Size[GreaterEqual[1]]
): String =
    val headerRow                                                  = table.head
    val numberOfColumns                                            = headerRow.size
    val valuesLength: List[List[Int]]                              = table.map(_.map(_.length))
    val maxLengths: Array[Int]                                     = valuesLength.transpose.map(_.max).toArray
    val separator                                                  = " | "
    val rowLength                                                  = maxLengths.sum + separator.length * (numberOfColumns - 1)
    val horizontalSeparator                                        = "-" * rowLength
    def buildRow(row: List[String] Refined Size[Equal[A]]): String =
        row.zipWithIndex
            .map { case (value, index) =>
                val padding = " " * (maxLengths(index) - value.length)
                s"$value$padding"
            }
            .mkString(separator)
    def buildHeader: String                                        =
        buildRow(headerRow) + "\n" + horizontalSeparator + "\n"
    buildHeader + table.map(buildRow).mkString("\n")

def buildHtmlCodeTable[A](
    table: List[List[String] Refined Size[Equal[A]]] Refined Size[GreaterEqual[1]]
): String =
    """<code>""" + buildTable[A](table) + """</code>"""

import eu.timepit.refined.*
import eu.timepit.refined.api.Refined
import eu.timepit.refined.auto.*
import eu.timepit.refined.numeric.*

val i1: Int Refined Positive = refineV[Positive](5)
