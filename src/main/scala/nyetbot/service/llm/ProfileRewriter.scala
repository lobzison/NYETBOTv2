package nyetbot.service.llm

import cats.effect.IO
import nyetbot.client.OllamaClient
import nyetbot.config.llm.ProfileRewriterConfig
import nyetbot.model.ProfileModels.*
import nyetbot.repo.ProfileRepo
import nyetbot.service.llm.context.DossierFeature.Dossier
import nyetbot.service.llm.context.DossierFeature.UserSummary

trait ProfileRewriter:
    def rewrite(dossier: Dossier): IO[Unit]

object ProfileRewriter:
    def apply(
        client: OllamaClient,
        repo: ProfileRepo,
        config: ProfileRewriterConfig
    ): ProfileRewriter =
        val request = OllamaClient.Req.from(config.modelConfig)
        new ProfileRewriter:
            override def rewrite(dossier: Dossier): IO[Unit] =
                dossier.fresh match
                    case None        => IO.unit
                    case Some(fresh) =>
                        client
                            .generate(
                              request.copy(
                                prompt = Prompt.render(
                                  dossier.profile,
                                  fresh,
                                  dossier.who,
                                  config.profileMaxChars
                                )
                              )
                            )
                            .flatMap { merged =>
                                repo.upsertProfile(
                                  dossier.who.id,
                                  dossier.who.displayName,
                                  ProfileDescription.truncate(merged)
                                )
                            }

    object Prompt:
        def render(
            oldProfile: Option[ProfileDescription],
            summary: UserSummary,
            who: UserRef,
            profileMaxChars: Int
        ): String =
            val old = oldProfile.fold("пусто")(_.value)
            s"""Есть старое досье на пользователя ${who.displayName} и свежая сводка его поведения.
Слей их в одно обновлённое досье: сохрани важное из старого, добавь новое, выкинь устаревшее.
Пиши в третьем лице, нейтрально, одним абзацем, строго не больше $profileMaxChars символов.

СТАРОЕ ДОСЬЕ:
$old

СВЕЖАЯ СВОДКА:
${summary.value}

ОБНОВЛЁННОЕ ДОСЬЕ:"""
