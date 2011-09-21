package com.typesafe.webwords.indexer

import akka.actor.{ Index => _, _ }
import akka.dispatch._
import com.typesafe.webwords.common._
import java.net.URL

/**
 * This actor listens to the work queue, spiders and caches results.
 */
class WorkerActor(amqpUrl: Option[String], mongoUrl: Option[String])
    extends WorkQueueWorkerActor(amqpUrl) {
    private val spider = Actor.actorOf(new SpiderActor)
    private val cache = Actor.actorOf(new IndexStorageActor(mongoUrl))

    override def handleRequest(request: WorkQueueRequest): Future[WorkQueueReply] = {
        request match {
            case SpiderAndCache(url) =>
                val futureIndex = spider ? Spider(new URL(url)) map {
                    _ match { case Spidered(url, index) => index }
                }
                futureIndex flatMap { index =>
                    cache ? CacheIndex(url, index) map { cacheAck =>
                        SpideredAndCached(url)
                    }
                }
        }
    }

    override def preStart = {
        super.preStart
        spider.start
        cache.start
    }

    override def postStop = {
        super.postStop
        spider.stop
        cache.stop
    }
}
