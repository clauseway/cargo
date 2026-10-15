package org.clauseway.shipping;

// ABOUTME: A command's outcome: refused with every rule it would violate named
// ABOUTME: together, or its effect on the transaction.

import java.util.List;
import java.util.function.Supplier;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.clauseway.pldb.transaction.Transaction;
import org.clauseway.vavr.control.Try;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class Command {

	/** The effect when nothing denies it; otherwise the denials, joined, as the failure. */
	public static Try<Transaction> guarded(List<String> denials, Supplier<Transaction> effect) {
		if (!denials.isEmpty()) {
			return Try.failure(new IllegalStateException(String.join("; ", denials)));
		}
		return Try.success(effect.get());
	}
}
