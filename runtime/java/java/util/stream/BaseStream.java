package java.util.stream;

import java.util.Iterator;

/**
 * BaseStream, written from the Java SE specification. Streams run
 * sequentially: parallel() only sets the flag isParallel reports.
 * spliterator() is not provided.
 */
public interface BaseStream<T, S extends BaseStream<T, S>> extends AutoCloseable {
	Iterator<T> iterator();

	boolean isParallel();

	S sequential();

	S parallel();

	S unordered();

	S onClose(Runnable closeHandler);

	void close();
}
