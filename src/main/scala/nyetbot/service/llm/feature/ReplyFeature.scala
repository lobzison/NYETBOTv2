package nyetbot.service.llm.feature

import cats.effect.IO
import nyetbot.client.OllamaClient
import nyetbot.config.llm.feature.ReplyFeatureConfig
import org.slf4j.LoggerFactory

trait ReplyFeature:
    def generateReply(ctx: ReplyFeature.ReplyContext): IO[String]

object ReplyFeature:

    private val logger = LoggerFactory.getLogger(getClass)

    final case class ReplyContext(blocks: List[String])

    def apply(
        client: OllamaClient,
        config: ReplyFeatureConfig
    ): ReplyFeature =
        new ReplyFeature:
            private val request = OllamaClient.Req.from(config.modelConfig)

            override def generateReply(ctx: ReplyContext): IO[String] =
                val prompt = ReplyBlocks.render(ctx.blocks)
                logger.debug("Prompt to send to LLM")
                logger.debug(prompt)
                client.generate(request.copy(prompt = prompt))
