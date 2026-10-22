import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/** java.util.concurrent: atomics, executors, latches, locks and the concurrent map */
public class Concurrent {
	static void runAll(int threads, Runnable body) throws InterruptedException {
		List<Thread> list = new ArrayList<>();
		for (int i = 0; i < threads; i++) {
			list.add(new Thread(body));
		}
		for (Thread t : list) {
			t.start();
		}
		for (Thread t : list) {
			t.join();
		}
	}

	public static void main(String[] args) throws Exception {
		// Atomics under contention
		AtomicInteger ai = new AtomicInteger();
		AtomicLong al = new AtomicLong(10);
		AtomicInteger max = new AtomicInteger(Integer.MIN_VALUE);
		runAll(4, () -> {
			for (int i = 0; i < 25000; i++) {
				ai.incrementAndGet();
				al.addAndGet(2);
				int v = i;
				max.accumulateAndGet(v, Math::max);
			}
		});
		System.out.println("atomics " + ai.get() + " " + al.get() + " " + max);
		System.out.println("cas " + ai.compareAndSet(100000, 5) + " " + ai.compareAndSet(100000, 6) + " " + ai
				+ " getAndSet " + ai.getAndSet(9) + " " + ai.getAndUpdate(x -> x * 2) + " " + ai.updateAndGet(x -> x + 1));
		System.out.println("overflow " + new AtomicInteger(Integer.MAX_VALUE).incrementAndGet()
				+ " " + new AtomicLong(Long.MIN_VALUE).decrementAndGet());
		AtomicBoolean flag = new AtomicBoolean();
		AtomicInteger winners = new AtomicInteger();
		runAll(8, () -> {
			if (flag.compareAndSet(false, true)) {
				winners.incrementAndGet();
			}
		});
		System.out.println("one winner " + winners + " " + flag + " " + flag.getAndSet(false) + " " + flag.get());
		AtomicReference<String> ref = new AtomicReference<>("a");
		String a = ref.get();
		System.out.println("ref " + ref.compareAndSet(a, "b") + " " + ref.compareAndSet("a", "c") + " " + ref
				+ " " + ref.getAndSet("d") + " " + ref.updateAndGet(s -> s + "!") + " " + new AtomicReference<>());
		AtomicReference<List<Integer>> list = new AtomicReference<>(new ArrayList<>());
		runAll(4, () -> {
			for (int i = 0; i < 100; i++) {
				list.updateAndGet(old -> {
					List<Integer> copy = new ArrayList<>(old);
					copy.add(1);
					return copy;
				});
			}
		});
		System.out.println("copy on write " + list.get().size());

		// An executor summing ranges, in submission order
		ExecutorService pool = Executors.newFixedThreadPool(4);
		List<Future<Long>> parts = new ArrayList<>();
		for (int i = 0; i < 10; i++) {
			final long from = i * 100000L;
			parts.add(pool.submit(() -> {
				long sum = 0;
				for (long n = from; n < from + 100000L; n++) {
					sum += n;
				}
				return sum;
			}));
		}
		long total = 0;
		for (Future<Long> f : parts) {
			total += f.get();
		}
		System.out.println("executor sum " + total + " done " + parts.get(9).isDone());
		Future<?> failing = pool.submit(() -> {
			throw new IllegalStateException("task failed");
		});
		try {
			failing.get();
		} catch (ExecutionException e) {
			System.out.println("execution: " + e.getMessage() + " / " + e.getCause().getMessage());
		}
		Future<String> named = pool.submit(() -> Thread.currentThread().getName().substring(0, 12));
		System.out.println("pool thread " + named.get());
		Future<Integer> slow = pool.submit(() -> {
			Thread.sleep(2000);
			return 1;
		});
		try {
			slow.get(10, TimeUnit.MILLISECONDS);
		} catch (TimeoutException e) {
			System.out.println("timed out " + slow.isDone());
		}
		System.out.println("cancel " + slow.cancel(true) + " " + slow.isCancelled() + " " + slow.isDone());
		List<Callable<Integer>> calls = new ArrayList<>();
		for (int i = 1; i <= 5; i++) {
			final int n = i;
			calls.add(() -> n * n);
		}
		int squares = 0;
		for (Future<Integer> f : pool.invokeAll(calls)) {
			squares += f.get();
		}
		// invokeAny may return any successful result, so only one task succeeds
		List<Callable<Integer>> anyCalls = new ArrayList<>();
		for (int i = 1; i <= 5; i++) {
			final int n = i;
			anyCalls.add(() -> {
				if (n != 3) {
					throw new IllegalStateException("task " + n);
				}
				return n * n;
			});
		}
		System.out.println("invokeAll " + squares + " invokeAny " + pool.invokeAny(anyCalls));
		pool.shutdown();
		System.out.println("terminated " + pool.awaitTermination(10, TimeUnit.SECONDS) + " " + pool.isShutdown()
				+ " " + pool.isTerminated());
		try {
			pool.submit(() -> 1);
		} catch (RejectedExecutionException e) {
			System.out.println("rejected after shutdown");
		}
		ExecutorService single = Executors.newSingleThreadExecutor();
		List<Integer> order = Collections.synchronizedList(new ArrayList<>());
		for (int i = 0; i < 5; i++) {
			final int n = i;
			single.execute(() -> order.add(n));
		}
		single.shutdown();
		single.awaitTermination(10, TimeUnit.SECONDS);
		System.out.println("single thread order " + order);

		// A latch: the workers wait for the start signal, main for all of them
		CountDownLatch start = new CountDownLatch(1);
		CountDownLatch done = new CountDownLatch(3);
		AtomicInteger started = new AtomicInteger();
		for (int i = 0; i < 3; i++) {
			new Thread(() -> {
				try {
					start.await();
					started.incrementAndGet();
					done.countDown();
				} catch (InterruptedException e) {
					throw new RuntimeException(e);
				}
			}).start();
		}
		System.out.println("before start " + started.get() + " " + done.getCount());
		start.countDown();
		done.await();
		System.out.println("after latch " + started.get() + " " + done.getCount() + " "
				+ done.await(1, TimeUnit.MILLISECONDS) + " " + new CountDownLatch(1).await(5, TimeUnit.MILLISECONDS));

		// ReentrantLock with a condition: a bounded counter
		ReentrantLock lock = new ReentrantLock();
		Condition changed = lock.newCondition();
		int[] value = new int[1];
		Thread waiter = new Thread(() -> {
			lock.lock();
			try {
				while (value[0] < 3) {
					changed.await();
				}
				System.out.println("condition saw " + value[0] + " holds " + lock.getHoldCount());
			} catch (InterruptedException e) {
				throw new RuntimeException(e);
			} finally {
				lock.unlock();
			}
		});
		waiter.start();
		for (int i = 0; i < 3; i++) {
			lock.lock();
			try {
				value[0]++;
				changed.signalAll();
			} finally {
				lock.unlock();
			}
		}
		waiter.join();
		int[] guarded = new int[1];
		runAll(4, () -> {
			for (int i = 0; i < 10000; i++) {
				lock.lock();
				lock.lock();
				try {
					guarded[0]++;
				} finally {
					lock.unlock();
					lock.unlock();
				}
			}
		});
		System.out.println("lock count " + guarded[0] + " locked " + lock.isLocked() + " tryLock " + lock.tryLock()
				+ " " + lock.isHeldByCurrentThread());
		lock.unlock();
		try {
			lock.unlock();
		} catch (IllegalMonitorStateException e) {
			System.out.println("unlock without lock: IllegalMonitorStateException");
		}

		// ConcurrentHashMap: counts from threads, then the JVM's order
		ConcurrentHashMap<String, Integer> counts = new ConcurrentHashMap<>();
		runAll(4, () -> {
			for (int i = 0; i < 1000; i++) {
				counts.merge("k" + (i % 20), 1, Integer::sum);
			}
		});
		System.out.println("concurrent map " + counts);
		Map<String, Integer> letters = new ConcurrentHashMap<>();
		for (char c = 'a'; c <= 'l'; c++) {
			letters.put(String.valueOf(c), (int) c);
		}
		System.out.println("twelve " + letters.keySet());
		ConcurrentHashMap<Integer, String> sized = new ConcurrentHashMap<>(16);
		for (int i = 0; i < 40; i += 3) {
			sized.put(i * 7, "v" + i);
		}
		System.out.println("sized " + sized);
		System.out.println("copied " + new ConcurrentHashMap<>(new HashMap<>(Map.of("x", 1))));
		for (Map.Entry<String, Integer> e : letters.entrySet()) {
			if (e.getValue() % 2 == 0) {
				letters.remove(e.getKey());
			}
		}
		System.out.println("removed while iterating " + letters);
		System.out.println("compute " + counts.computeIfAbsent("new", k -> 7) + " " + counts.putIfAbsent("new", 8)
				+ " " + counts.getOrDefault("missing", -1) + " " + counts.mappingCount());
		try {
			counts.put(null, 1);
		} catch (NullPointerException e) {
			System.out.println("null key: NullPointerException");
		}
		try {
			counts.put("x", null);
		} catch (NullPointerException e) {
			System.out.println("null value: NullPointerException");
		}
		java.util.Set<String> keys = ConcurrentHashMap.newKeySet();
		keys.add("b");
		keys.add("a");
		System.out.println("key set " + keys + " " + keys.add("a"));

		// Synchronized views
		List<Integer> syncList = Collections.synchronizedList(new ArrayList<>());
		Map<Integer, Integer> syncMap = Collections.synchronizedMap(new HashMap<>());
		runAll(4, () -> {
			for (int i = 0; i < 1000; i++) {
				syncList.add(i);
				syncMap.merge(i % 10, 1, Integer::sum);
			}
		});
		System.out.println("synchronized views " + syncList.size() + " " + syncMap);
	}
}
