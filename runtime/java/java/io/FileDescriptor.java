package java.io;

public final class FileDescriptor {
	int fd;

	public FileDescriptor() {
		fd = -1;
	}

	FileDescriptor(int fd) {
		this.fd = fd;
	}

	public static final FileDescriptor in = new FileDescriptor(0);

	public static final FileDescriptor out = new FileDescriptor(1);

	public static final FileDescriptor err = new FileDescriptor(2);

	public boolean valid() {
		return fd != -1;
	}

	public void sync() throws SyncFailedException {
		if (fd == -1) {
			throw new SyncFailedException("sync failed");
		}
	}
}
