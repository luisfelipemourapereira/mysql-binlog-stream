package db
import cats.effect.{ ContextShift, IO }
import com.dimafeng.testcontainers.{ Container, ForAllTestContainer, SingleContainer }
import io.laserdisc.mysql.binlog.config.BinLogConfig
import org.testcontainers.containers.MySQLContainer

import java.net.URI
import scala.concurrent.ExecutionContext
import scala.concurrent.ExecutionContext.Implicits
import scala.language.existentials

trait MySqlContainer {
  this: ForAllTestContainer =>
  type OTCContainer = MySQLContainer[T] forSome {
    type T <: MySQLContainer[T]
  }

  def initSQLPath: String = "init.sql"

  val mySqlContainer: OTCContainer = {

    val otc: OTCContainer = new MySQLContainer("mysql:5.7")
    otc
      .withUsername("root")
      .withPassword("")
      .withInitScript(initSQLPath)
      .withCommand("mysqld --log-bin --server-id=1 --binlog-format=ROW")
  }

  def singleMySQLContainer: SingleContainer[MySQLContainer[_]] =
    new SingleContainer[MySQLContainer[_]] {
      implicit override val container: MySQLContainer[_] = mySqlContainer
    }

  override val container: Container = singleMySQLContainer

  implicit val ec: ExecutionContext = Implicits.global
  implicit val cs: ContextShift[IO] = IO.contextShift(ec)

  def containerBinlogConfig: BinLogConfig = {

    val cleanURI = mySqlContainer.getJdbcUrl.substring(5)
    val uri      = URI.create(cleanURI)

    BinLogConfig(
      uri.getHost,
      uri.getPort,
      mySqlContainer.getUsername,
      mySqlContainer.getPassword,
      mySqlContainer.getDatabaseName,
      useSSL = false,
      poolSize = 3
    )
  }
}
