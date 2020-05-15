package io.laserdisk.mysql.binlog

import cats.effect.Sync
import com.github.shyiko.mysql.binlog.BinaryLogClient
import com.github.shyiko.mysql.binlog.event.deserialization.EventDeserializer
import com.github.shyiko.mysql.binlog.event.deserialization.EventDeserializer.CompatibilityMode
import com.github.shyiko.mysql.binlog.network.SSLMode
import io.chrisdavenport.log4cats.SelfAwareStructuredLogger
import io.laserdisk.mysql.binlog.checkpoint.BinlogOffset
import io.laserdisk.mysql.binlog.config.BinLogConfig
import cats.implicits._

package object client {
  def createBinLogClient[F[_]: Sync: SelfAwareStructuredLogger](offset: F[Option[BinlogOffset]])(
    implicit binLogConfig: BinLogConfig
  ): F[BinaryLogClient] =
    for {
      client <- Sync[F]
                  .delay {
                    val client = new BinaryLogClient(
                      binLogConfig.mojoDbHost,
                      binLogConfig.port,
                      binLogConfig.dbUser,
                      binLogConfig.dbPassword
                    )
                    val eventDeserializer = new EventDeserializer()
                    eventDeserializer.setCompatibilityMode(
                      CompatibilityMode.DATE_AND_TIME_AS_LONG_MICRO,
                      CompatibilityMode.CHAR_AND_BINARY_AS_BYTE_ARRAY
                    )
                    client.setEventDeserializer(eventDeserializer)
                    client.setSSLMode(SSLMode.VERIFY_IDENTITY)
                    client
                  }
      _ <- offset.flatMap(
             _.fold(Sync[F].unit)(o =>
               Sync[F].delay {
                 client.setBinlogFilename(o.fileName)
                 client.setBinlogPosition(o.offset)
               }
             )
           )
      _ <-
        SelfAwareStructuredLogger[F].info(
          s"Binlog client created with offset ${client.getBinlogFilename} ${client.getBinlogPosition}"
        )
    } yield client
}
