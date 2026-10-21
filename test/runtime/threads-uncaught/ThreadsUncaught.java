import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Uncaught exceptions in threads print "Exception in thread ..." and end only
 * that thread; one in main sets the exit status, and the program still waits
 * for the threads that are running.
 */
public class ThreadsUncaught {
	public static void main(String[] args) throws Exception {
		Thread failing = new Thread(() -> {
			throw new IllegalStateException("boom", new RuntimeException("cause"));
		});
		failing.start();
		failing.join();
		System.out.println("main continues after " + failing.getName() + " died, alive " + failing.isAlive());

		Thread handled = new Thread(() -> {
			throw new UnsupportedOperationException("handled");
		}, "with-handler");
		handled.setUncaughtExceptionHandler(
				(t, e) -> System.out.println("handler of " + t.getName() + " got " + e.getMessage()));
		handled.start();
		handled.join();

		// A task of execute() kills its pool thread, which the pool replaces
		ExecutorService pool = Executors.newFixedThreadPool(1);
		pool.execute(() -> {
			throw new ArithmeticException("in pool");
		});
		pool.execute(() -> System.out.println("next task on " + Thread.currentThread().getName()));
		pool.shutdown();
		pool.awaitTermination(10, TimeUnit.SECONDS);

		Thread.setDefaultUncaughtExceptionHandler(
				(t, e) -> System.out.println("default handler: " + t.getName() + " " + e));
		Thread defaulted = new Thread(() -> {
			throw new IllegalArgumentException("to the default");
		}, "defaulted");
		defaulted.start();
		defaulted.join();
		Thread.setDefaultUncaughtExceptionHandler(null);

		// Still running when main throws: the program waits for it
		Thread late = new Thread(() -> {
			try {
				Thread.sleep(200);
			} catch (InterruptedException e) {
				throw new RuntimeException(e);
			}
			System.out.println("late thread finished after main");
		});
		late.start();
		throw new IllegalStateException("main fails");
	}
}
