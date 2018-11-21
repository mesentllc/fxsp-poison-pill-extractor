package com.fedex.smartpost.system.thread;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.fedex.smartpost.system.enums.Environment;

public class ThreadMain {
	private static final Log log = LogFactory.getLog(ThreadMain.class);

	public static void main(String[] args) {
		ExecutorService executorService = Executors.newFixedThreadPool(5);
		executorService.submit(new JmsMessageTopicReaderThread("Thread.1", "VS.FDXNET.IPC.CF.WTC",
															   "D.FDXNET.IPC.EVENT", Environment.PROD));
		executorService.submit(new JmsMessageTopicReaderThread("Thread.2", "VS.FDXNET.IPC.CF.WTC",
															   "D.FDXNET.IPC.EVENT", Environment.PROD));
		executorService.submit(new JmsMessageTopicReaderThread("Thread.3", "VS.FDXNET.IPC.CF.WTC",
															   "D.FDXNET.IPC.EVENT", Environment.PROD));
		executorService.submit(new JmsMessageTopicReaderThread("Thread.4", "VS.FDXNET.IPC.CF.WTC",
															   "D.FDXNET.IPC.EVENT", Environment.PROD));
		executorService.submit(new JmsMessageTopicReaderThread("Thread.5", "VS.FDXNET.IPC.CF.WTC",
															   "D.FDXNET.IPC.EVENT", Environment.PROD));
		// Really, this mean eternity... I don't want to ever terminate, until I murder it.
		try {
			executorService.awaitTermination(Long.MAX_VALUE, TimeUnit.DAYS);
		}
		catch (InterruptedException e) {
			log.info("Exception Caught: ", e);
		}
	}
}
