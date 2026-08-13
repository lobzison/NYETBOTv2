package nyetbot.lab

import fs2.Pipe
import fs2.RaiseThrowable
import fs2.data.json.Token
import fs2.data.json.ast
import fs2.data.json.circe.given
import io.circe.Json

object TelegramExportPipe:

    private enum State:
        case SeekRoot
        case Root
        case AwaitChats
        case Chats
        case AwaitList
        case ChatList
        case Chat(id: Option[ChatId])
        case AwaitChatId
        case AwaitMessages(id: Option[ChatId])
        case Messages(id: Option[ChatId])
        case InMessage(id: Option[ChatId], depth: Int)
        case Skip(depth: Int, next: State)
        case Finished

    def messages[F[_]: RaiseThrowable](target: ChatId): Pipe[F, Token, Json] =
        _.mapAccumulate(State.SeekRoot: State)(step(target))
            .map(_._2)
            .unNone
            .through(ast.values[F, Json])

    private def step(target: ChatId)(state: State, token: Token): (State, Option[Token]) =
        state match
            case State.SeekRoot =>
                token match
                    case Token.StartObject => (State.Root, None)
                    case Token.StartArray  => (State.Skip(1, State.SeekRoot), None)
                    case _                 => (State.SeekRoot, None)

            case State.Root =>
                token match
                    case Token.Key("chats") => (State.AwaitChats, None)
                    case Token.Key(_)       => (State.Skip(0, State.Root), None)
                    case Token.EndObject    => (State.Finished, None)
                    case _                  => (State.Root, None)

            case State.AwaitChats =>
                token match
                    case Token.StartObject => (State.Chats, None)
                    case Token.StartArray  => (State.Skip(1, State.Root), None)
                    case _                 => (State.Root, None)

            case State.Chats =>
                token match
                    case Token.Key("list") => (State.AwaitList, None)
                    case Token.Key(_)      => (State.Skip(0, State.Chats), None)
                    case Token.EndObject   => (State.Root, None)
                    case _                 => (State.Chats, None)

            case State.AwaitList =>
                token match
                    case Token.StartArray  => (State.ChatList, None)
                    case Token.StartObject => (State.Skip(1, State.Chats), None)
                    case _                 => (State.Chats, None)

            case State.ChatList =>
                token match
                    case Token.StartObject => (State.Chat(None), None)
                    case Token.EndArray    => (State.Chats, None)
                    case _                 => (State.ChatList, None)

            case State.Chat(id) =>
                token match
                    case Token.Key("id")       => (State.AwaitChatId, None)
                    case Token.Key("messages") => (State.AwaitMessages(id), None)
                    case Token.Key(_)          => (State.Skip(0, State.Chat(id)), None)
                    case Token.EndObject       => (State.ChatList, None)
                    case _                     => (State.Chat(id), None)

            case State.AwaitChatId =>
                token match
                    case Token.NumberValue(v)                 =>
                        (State.Chat(v.toLongOption.map(ChatId(_))), None)
                    case Token.StartObject | Token.StartArray =>
                        (State.Skip(1, State.Chat(None)), None)
                    case _                                    => (State.Chat(None), None)

            case State.AwaitMessages(id) =>
                token match
                    case Token.StartArray  => (State.Messages(id), None)
                    case Token.StartObject => (State.Skip(1, State.Chat(id)), None)
                    case _                 => (State.Chat(id), None)

            case State.Messages(id) =>
                token match
                    case Token.StartObject =>
                        (State.InMessage(id, 1), Option.when(id.contains(target))(token))
                    case Token.StartArray  => (State.Skip(1, State.Messages(id)), None)
                    case Token.EndArray    => (State.Chat(id), None)
                    case _                 => (State.Messages(id), None)

            case State.InMessage(id, depth) =>
                val emit = Option.when(id.contains(target))(token)
                token match
                    case Token.StartObject | Token.StartArray =>
                        (State.InMessage(id, depth + 1), emit)
                    case Token.EndObject | Token.EndArray     =>
                        if depth == 1 then (State.Messages(id), emit)
                        else (State.InMessage(id, depth - 1), emit)
                    case _                                    => (State.InMessage(id, depth), emit)

            case State.Skip(depth, next) =>
                token match
                    case Token.StartObject | Token.StartArray => (State.Skip(depth + 1, next), None)
                    case Token.EndObject | Token.EndArray     =>
                        (if depth <= 1 then next else State.Skip(depth - 1, next), None)
                    case Token.Key(_)                         => (State.Skip(depth, next), None)
                    case _                                    =>
                        (if depth == 0 then next else State.Skip(depth, next), None)

            case State.Finished => (State.Finished, None)
