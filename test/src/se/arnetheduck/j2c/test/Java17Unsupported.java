package se.arnetheduck.j2c.test;

sealed interface Java17Sealed permits Java17SealedImpl {
}

non-sealed class Java17SealedImpl implements Java17Sealed {
}

record Java17Record(int value) {
}

public class Java17Unsupported {
}
