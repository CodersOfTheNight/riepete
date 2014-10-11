package io.simao.riepete.server

import java.net.InetSocketAddress

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import akka.io.{IO, Udp}
import io.simao.riepete.messages.StatsdMetric
import io.simao.riepete.metric_receivers.StatsReceiver

object RiepeteServer {
  def props()(implicit config: Config) = {
    Props(new RiepeteServer)
  }
}

class RiepeteServer(implicit config: Config) extends Actor with ActorLogging {
  import context.system

  override def preStart(): Unit = {
    val localAddress = new InetSocketAddress(config.bind_ip, config.bind_port)
    IO(Udp) ! Udp.Bind(self, localAddress)
  }

  val statsdHandler = {
    system.actorOf(StatsReceiver.props(), "StatsDHandler")
  }

  def receive: Receive = {
    case Udp.Bound(local) =>
      log.info(s"UDP server ready on ${local.getHostName}:${local.getPort}")
      context become ready(sender())

    case Udp.CommandFailed(cmd)  =>
      log.error("Failed to start server: " + cmd)
      context stop self
  }

  def ready(socket: ActorRef): Receive = {
    case Udp.Received(data, remote) =>
      val dataStr = data.decodeString("utf-8")
      log.debug(s"Received UDP MSG: $dataStr from ${remote.getAddress}")
      statsdHandler ! StatsdMetric(dataStr)

    case Udp.Unbound =>
      log.error("UDP Unbound from server socket")
      context stop self
  }
}


object RiepeteServerApp extends App {
  import akka.actor.ActorSystem

  override def main(args: Array[String]) {
    implicit val riepete_config = args.lift(0).map(Config(_)).getOrElse(Config.default)
    implicit val system = ActorSystem("riepeteActorSystem")

    system.actorOf(RiepeteServer.props(), "StatsDServer")
  }
}