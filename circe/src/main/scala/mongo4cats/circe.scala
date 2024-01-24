/*
 * Copyright 2020 Kirill5k
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package mongo4cats

import cats.syntax.all._
import io.circe.{parser, Decoder, Encoder, Json}
import io.circe.syntax._
import mongo4cats.bson.{
  BsonDecodeError,
  BsonDecoder,
  BsonDocument,
  BsonDocumentEncoder,
  BsonEncoder
}
import org.bson._

object circe extends JsonCodecs {
  private val RootTag = "a"

  object unsafe {
    def circeDocumentEncoder[A: Encoder] = new BsonDocumentEncoder[A] {
      def apply(a: A): BsonDocument =
        BsonDocument.parse(a.asJson.noSpaces)
    }
  }

  object implicits {
    implicit def circeEncoderToEncoder[A: Encoder]: BsonEncoder[A] = new BsonEncoder[A] {
      def apply(a: A): BsonValue = {
        val json = a.asJson
        val wrapped = Json.obj(RootTag := json)
        val bson = BsonDocument.parse(wrapped.noSpaces)
        bson.get(RootTag)
      }
    }

    implicit def circeDecoderToDecoder[A: Decoder]: BsonDecoder[A] = new BsonDecoder[A] {
      def apply(b: BsonValue) = {
        val doc = BsonDocument(RootTag -> (if (b == null) new BsonNull else b)).toJson()
        val json = parser.parse(doc)
        val jsonWithoutRoot = json.flatMap(_.hcursor.get[Json](RootTag))
        val decoder = Decoder.instance[A](_.as[A])
        jsonWithoutRoot
          .flatMap(decoder.decodeJson(_))
          .leftMap(x =>
            BsonDecodeError {
              s"An error occured during decoding BsonValue ${b}: $x"
            }
          )
      }
    }
  }

  object codecs extends JsonCodecs
}
