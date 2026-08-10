package nyetbot.model

import io.github.iltotore.iron.RefinedType
import io.github.iltotore.iron.constraint.any.Not
import io.github.iltotore.iron.constraint.all.Empty

type NonEmptyString = NonEmptyString.T
object NonEmptyString extends RefinedType[String, Not[Empty]]
