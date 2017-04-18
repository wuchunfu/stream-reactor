/*
 * Copyright 2017 Datamountaineer.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.datamountaineer.streamreactor.connect.hazelcast.config

import com.datamountaineer.connector.config.{Config, FormatType}
import com.datamountaineer.streamreactor.connect.errors.{ErrorPolicy, ErrorPolicyEnum, ThrowErrorPolicy}
import com.datamountaineer.streamreactor.connect.hazelcast.HazelCastConnection
import com.datamountaineer.streamreactor.connect.hazelcast.config.TargetType.TargetType
import com.hazelcast.core.HazelcastInstance
import org.apache.kafka.connect.errors.ConnectException

import scala.collection.JavaConversions._
import scala.util.{Failure, Success, Try}

/**
  * Created by andrew@datamountaineer.com on 08/08/16.
  * stream-reactor
  */

object TargetType extends Enumeration {
  type TargetType = Value
  val RELIABLE_TOPIC, RING_BUFFER, QUEUE, SET, LIST, IMAP, MULTI_MAP, ICACHE = Value
}

case class HazelCastStoreAsType(name: String, targetType: TargetType)

case class HazelCastSinkSettings(client: HazelcastInstance,
                                 routes: Set[Config],
                                 topicObject: Map[String, HazelCastStoreAsType],
                                 fieldsMap: Map[String, Map[String, String]],
                                 ignoreFields: Map[String, Set[String]],
                                 pks: Map[String, Set[String]],
                                 errorPolicy: ErrorPolicy = new ThrowErrorPolicy,
                                 maxRetries: Int = HazelCastSinkConfigConstants.NBR_OF_RETIRES_DEFAULT,
                                 format: Map[String, FormatType],
                                 threadPoolSize: Int,
                                 allowParallel: Boolean)

object HazelCastSinkSettings {
  def apply(config: HazelCastSinkConfig): HazelCastSinkSettings = {

    val routes = config.getRoutes
    val errorPolicy = config.getErrorPolicy
    val maxRetries = config.getNumberRetries
    val fieldMap = config.getFields(routes)
    val ignoreFields = config.getIgnoreFields(routes)
    val threadPoolSize = config.getThreadPoolSize


    //    routes.map(r => (r.getSource, r.getTarget)).toMap

    val topicTables = routes.map(r => {
      Try(TargetType.withName(r.getStoredAs.toUpperCase)) match {
        case Success(_) => (r.getSource, HazelCastStoreAsType(r.getTarget, TargetType.withName(r.getStoredAs.toUpperCase)))
        case Failure(_) => (r.getSource, HazelCastStoreAsType(r.getTarget, TargetType.RELIABLE_TOPIC))
      }
    }).toMap


    val groupName = config.getString(HazelCastSinkConfigConstants.SINK_GROUP_NAME)
    require(groupName.nonEmpty, s"No ${HazelCastSinkConfigConstants.SINK_GROUP_NAME} provided!")

    val connConfig = HazelCastConnectionConfig(config)
    val client = HazelCastConnection.buildClient(connConfig)

    val format = routes.map(r => (r.getSource, getFormatType(r.getFormatType))).toMap
    val p = routes.map(r => (r.getSource, r.getPrimaryKeys.toSet)).toMap


    val allowParallel = config.getBoolean(HazelCastSinkConfigConstants.PARALLEL_WRITE)

    new HazelCastSinkSettings(client, routes, topicTables, fieldMap, ignoreFields, p, errorPolicy, maxRetries, format, threadPoolSize, allowParallel)
  }

  private def getFormatType(format: FormatType) = {
    if (format == null) {
      FormatType.JSON
    } else {
      format match {
        case FormatType.AVRO | FormatType.JSON | FormatType.TEXT =>
        case _ => throw new ConnectException(s"Unknown WITHFORMAT type")
      }
      format
    }
  }

}


