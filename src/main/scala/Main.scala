package com.gses.experiments.latency

import com.typesafe.scalalogging.LazyLogging
import java.lang.System.nanoTime
import java.io.File
import java.io.File.createTempFile
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.ByteBuffer.{allocate, wrap}
import java.net.MalformedURLException
import java.net.URL
import org.rogach.scallop._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
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
		val contended = opt[Boolean]("contended", descr = "generate heavy IO contention in background", short = 'c', default = Some(false))
	}

	def randomFile (file: File): File = {
		val files: Array[File] = file.listFiles
		if (files.length == 0) throw new Exception(s"error, no files in $file to read from")

		val chosen = files(nextInt(files.size))
		if (chosen.isDirectory) randomFile(chosen) else chosen
	}

	try {
		var accumulator = 0D
		logger.info(s"""running test for ${opts.iterations()} iterations with ${if (opts.sync()) "synced" else "unsynced"} writes""")

		if (opts.contended()) {
			Future {
				while (true) {
					try {
						val (size, elapsed) = testDiskSynced
						logger.debug(s"contention write of $size bytes")
					} catch {
						case e: Exception => logger.debug("failed background io", e)
					}
				}
			}

			val tmp = new File(System.getProperty("user.home"))
			val buffer = allocate(10000000)

			Future {
				while (true) {
					try {
						val file = randomFile(tmp)
						val channel = new FileInputStream(file).getChannel
						val read = channel.read(buffer)
						buffer.rewind
						channel.close
						logger.debug(s"contention read of $read bytes from $file")

					} catch {
						case e: Exception => logger.debug("failed background io")
					}
				}
			}
		}

		for (i <- 0 to opts.iterations()) {
			Thread.sleep(nextInt(1000))
			if (opts.sleep()) sleepDrive

			val (size, elapsed) = if (opts.sync()) testDiskSynced
			else testDiskRegular

			logger.info(s"wrote ${size} bytes in ${elapsed} milliseconds")
			accumulator += elapsed
		}

		logger.info(s"completed test, average latency for IO operation is ${accumulator / opts.iterations()} milliseconds")
	} catch {
		case e: Exception => logger.error("failed to complete tests", e)
	}

	def sleepDrive = "sudo hdparm -Y /dev/sda" !!

	def testDiskRegular = testDisk(false)

	def testDiskSynced = testDisk(true)

	def testDisk (sync: Boolean): (Int, Double) = {
		val file = tmp
		file.deleteOnExit

		val stream = new FileOutputStream(file)
		val channel = stream.getChannel

		val buffer = wrap(data)

		val size = nextInt(buffer.remaining)
		buffer.limit(size)

		val start = nanoTime
		val wrote = channel.write(buffer)
		if (sync) {
			channel.force(true)
			stream.getFD.sync
		}
		channel.close
		file.delete

		val end = nanoTime
		(wrote, (end - start) / 1000000.toDouble)
	}

	def tmp: File = createTempFile("io-latency-", ".data")
}
