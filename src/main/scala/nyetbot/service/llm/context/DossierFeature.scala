package nyetbot.service.llm.context

import io.github.iltotore.iron.RefinedType
import io.github.iltotore.iron.constraint.all.Empty
import io.github.iltotore.iron.constraint.any.Not
import nyetbot.client.OllamaClient
import nyetbot.config.llm.DossierConfig
import nyetbot.model.LlmContextMessage
import nyetbot.model.ProfileModels.*
import nyetbot.repo.ProfileRepo
import nyetbot.util.Text

object DossierFeature:
    type UserSummary = UserSummary.T
    object UserSummary extends RefinedType[String, Not[Empty]]

    final case class Dossier(
        who: UserRef,
        profile: Option[ProfileDescription],
        fresh: Option[UserSummary]
    )

    def apply(
        client: OllamaClient,
        repo: ProfileRepo,
        config: DossierConfig
    ): ContextFeature[Dossier] =
        val request = OllamaClient.Req.from(config.modelConfig)
        ContextFeature.io("dossier", config.enabled) { in =>
            for
                profile <- repo.getProfile(in.target.id).map(_.map(_.description))
                fresh   <-
                    client
                        .generate(
                          request.copy(
                            prompt =
                                Prompt.render(in.recentUserMsgs, in.target, config.summaryMaxChars)
                          )
                        )
                        .map(raw =>
                            UserSummary.either(Text.truncate(raw, config.summaryMaxChars)).toOption
                        )
            yield (profile, fresh) match
                case (None, None) => None
                case _            => Some(Dossier(in.target, profile, fresh))
        }

    object Prompt:
        def render(
            recent: List[LlmContextMessage],
            who: UserRef,
            summaryMaxChars: Int
        ): String =
            s"""Ниже последние сообщения пользователя ${who.displayName} из чата.
Составь сжатую нейтральную сводку: о чём он пишет, какая позиция, манера, повторяющиеся темы.
Только описание поведения, без ролей, без оценок, без обращений. Не больше $summaryMaxChars символов.

СООБЩЕНИЯ:
${LlmContextMessage.render(recent)}

СВОДКА:"""
