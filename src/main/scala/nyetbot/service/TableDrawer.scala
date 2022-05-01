package nyetbot.service

import cats.MonadThrow
import cats.implicits.toFlatMapOps
import cats.implicits.toFunctorOps

case class NoHeaderRow(e: String)                                 extends Exception
case class OneOrMoreTableRowsDoNotMatchNumberOfColumns(e: String) extends Exception

sealed abstract case class TableDrawer private (table: List[List[String]]):
    def buildTable: String =
        val headerRow                           = table.head
        val valuesRows                          = table.tail
        val numberOfColumns                     = headerRow.size
        val valuesLength: List[List[Int]]       = table.map(_.map(_.length))
        val maxLengths: Array[Int]              = valuesLength.transpose.map(_.max).toArray
        val separator                           = " | "
        val rowLength                           = maxLengths.sum + separator.length * (numberOfColumns - 1)
        val horizontalSeparator                 = "-" * rowLength
        def buildRow(row: List[String]): String =
            row.zipWithIndex
                .map { case (value, index) =>
                    val padding = " " * (maxLengths(index) - value.length)
                    s"$value$padding"
                }
                .mkString(separator)
        def buildHeader: String                 =
            buildRow(headerRow) + "\n" + horizontalSeparator + "\n"
        buildHeader + valuesRows.map(buildRow).mkString("\n")

    def buildHtmlCodeTable: String =
        """<code>""" + buildTable + """</code>"""

object TableDrawer:
    def create[F[_]: MonadThrow](numberOfColumns: Int, table: List[List[String]]): F[TableDrawer] =
        val checkHeaderExists              =
            if table.size > 0
            then MonadThrow[F].unit
            else MonadThrow[F].raiseError(NoHeaderRow(table.toString))
        val checkAllRowsHaveSameColumnSize =
            if table.forall(_.size == numberOfColumns)
            then MonadThrow[F].unit
            else
                MonadThrow[F].raiseError(
                  OneOrMoreTableRowsDoNotMatchNumberOfColumns(table.toString)
                )
        for
            _ <- checkHeaderExists
            _ <- checkAllRowsHaveSameColumnSize
        yield new TableDrawer(table) {}
