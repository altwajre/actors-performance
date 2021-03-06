
package com.github.altwajre.actors

import com.github.altwajre.actors.BenchmarkSpec._
import java.util.concurrent._
import akka.dispatch.ForkJoinExecutorConfigurator.AkkaForkJoinPool
import scalaz.concurrent.{Actor, Strategy}
import scalaz.concurrent.Actor._

class ScalazActorSpec extends BenchmarkSpec {
  private val executorService = createExecutorService()
  private implicit val actorStrategy = executorService match {
    case p: AkkaForkJoinPool => new Strategy {
      def apply[A](a: => A): () => A = {
        new AkkaForkJoinTask(p) {
          def exec(): Boolean = {
            a
            false
          }
        }
        null
      }
    }
    case p: ForkJoinPool => new Strategy {
      def apply[A](a: => A): () => A = {
        new JavaForkJoinTask(p) {
          def exec(): Boolean = {
            a
            false
          }
        }
        null
      }
    }
    case p => new Strategy {
      def apply[A](a: => A): () => A = {
        p.execute(new Runnable {
          def run(): Unit = a
        })
        null
      }
    }
  }

  "Enqueueing" in {
    val n = 40000000
    val l1 = new CountDownLatch(1)
    val l2 = new CountDownLatch(1)
    val a = blockableCountActor(l1, l2, n)
    footprintedAndTimed(n) {
      sendMessages(a, n)
    }
    l1.countDown()
    l2.await()
  }

  "Dequeueing" in {
    val n = 40000000
    val l1 = new CountDownLatch(1)
    val l2 = new CountDownLatch(1)
    val a = blockableCountActor(l1, l2, n)
    sendMessages(a, n)
    timed(n) {
      l1.countDown()
      l2.await()
    }
  }

  "Initiation" in {
    footprintedAndTimedCollect(10000000)(() => actor((_: Message) => ()))
  }

  "Single-producer sending" in {
    val n = 15000000
    val l = new CountDownLatch(1)
    val a = countActor(l, n)
    timed(n) {
      sendMessages(a, n)
      l.await()
    }
  }

  "Multi-producer sending" in {
    val n = roundToParallelism(15000000)
    val l = new CountDownLatch(1)
    val a = countActor(l, n)
    val r = new ParRunner((1 to parallelism).map(_ => () => sendMessages(a, n / parallelism)))
    timed(n) {
      r.start()
      l.await()
    }
  }

  "Max throughput" in {
    val n = roundToParallelism(30000000)
    val l = new CountDownLatch(parallelism)
    val r = new ParRunner((1 to parallelism).map {
      _ =>
        val a = countActor(l, n / parallelism)
        () => sendMessages(a, n / parallelism)
    })
    timed(n) {
      r.start()
      l.await()
    }
  }

  "Ping latency" in {
    pingLatency(3000000)
  }

  "Ping throughput 10K" in {
    pingThroughput(6000000, 10000)
  }

  def shutdown(): Unit = fullShutdown(executorService)

  private def pingLatency(n: Int): Unit =
    latencyTimed(n) {
      h =>
        val l = new CountDownLatch(2)
        var a1: Actor[Message] = null
        val a2 = actor {
          var i = n / 2
          (m: Message) =>
            h.record()
            if (i > 0) a1 ! m
            i -= 1
            if (i == 0) l.countDown()
        }
        a1 = actor {
          var i = n / 2
          (m: Message) =>
            h.record()
            if (i > 0) a2 ! m
            i -= 1
            if (i == 0) l.countDown()
        }
        a2 ! Message()
        l.await()
    }

  private def pingThroughput(n: Int, p: Int): Unit = {
    val l = new CountDownLatch(p * 2)
    val as = (1 to p).map {
      _ =>
        var a1: Actor[Message] = null
        val a2 = actor {
          var i = n / p / 2
          (m: Message) =>
            if (i > 0) a1 ! m
            i -= 1
            if (i == 0) l.countDown()
        }
        a1 = actor {
          var i = n / p / 2
          (m: Message) =>
            if (i > 0) a2 ! m
            i -= 1
            if (i == 0) l.countDown()
        }
        a2
    }
    timed(n) {
      as.foreach(_ ! Message())
      l.await()
    }
  }

  private def blockableCountActor(l1: CountDownLatch, l2: CountDownLatch, n: Int): Actor[Message] =
    actor {
      var blocked = true
      var i = n - 1
      (_: Message) =>
        if (blocked) {
          l1.await()
          blocked = false
        } else {
          i -= 1
          if (i == 0) l2.countDown()
        }
    }

  private def countActor(l: CountDownLatch, n: Int): Actor[Message] =
    actor {
      var i = n
      (_: Message) =>
        i -= 1
        if (i == 0) l.countDown()
    }

  private def sendMessages(a: Actor[Message], n: Int): Unit = {
    val m = Message()
    var i = n
    while (i > 0) {
      a ! m
      i -= 1
    }
  }
}
