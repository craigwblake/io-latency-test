package com.gses.experiments.latency

import com.typesafe.scalalogging.LazyLogging
import java.lang.System.nanoTime
import java.io.File
import java.io.File.createTempFile
import java.io.FileOutputStream
import java.nio.ByteBuffer.wrap
import java.net.MalformedURLException
import java.net.URL
import org.rogach.scallop._
import scala.util.Random._
import scala.sys.process._

object Main extends App with LazyLogging {

	val data = Array.ofDim[Byte](1000000)
	nextBytes(data)

	val opts = new ScallopConf(args) {
		banner("""Usage: io-latency-test
			|io-latency-test conducts tests on disk IO latency for this machine
			|
			|Options:
			|""".stripMargin)

		val iterations = opt[Int]("iterations", descr = "number of writes", short = 'i', default = Some(50))
		val sync = opt[Boolean]("sync", descr = "use synced writes", short = 's', default = Some(false))
		val sleep = opt[Boolean]("sleep", descr = "force disk to sleep before each write", short = 'f', default = Some(false))
	}

	try {
		var accumulator = 0D
		logger.info(s"""running test for ${opts.iterations()} iterations with ${if (opts.sync()) "synced" else "unsynced"} writes""")
		for (i <- 0 to opts.iterations()) {
			if (opts.sleep()) sleepDrive

			if (opts.sync()) accumulator += testDiskSynced
			else accumulator += testDiskRegular
		}

		logger.info(s"completed test, average latency for IO operation is ${accumulator / opts.iterations()} milliseconds")
	} catch {
		case e: Exception =>
			logger.error("failed to complete tests", e)
	}

	def sleepDrive = "sudo hdparm -Y /dev/sda" !!

	def testDiskRegular = testDisk(false)

	def testDiskSynced = testDisk(true)

	def testDisk (sync: Boolean): Double = {
		val file = tmp
		val wait = nextInt(1000)
		val stream = new FileOutputStream(file)
		val channel = stream.getChannel

		val buffer = wrap(data)

		buffer.limit(nextInt(buffer.remaining))

		Thread.sleep(wait)
		val start = nanoTime
		channel.write(buffer)
		if (sync) {
			channel.force(true)
			stream.getFD.sync
		}
		channel.close

		val end = nanoTime
		val elapsed: Double = ((end - start) / 1000000.toDouble);

		logger.info(s"wrote ${buffer.limit} bytes in ${elapsed} milliseconds")

		elapsed
	}

	def tmp: File = createTempFile("io-latency", "data")
}
