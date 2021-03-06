package com.hiddenswitch.spellsource.impl.util;

import com.github.fromage.quasi.fibers.Suspendable;
import com.hiddenswitch.spellsource.impl.TimerId;
import io.vertx.core.Handler;

public interface Scheduler {
	@Suspendable
	TimerId setTimer(long delay, Handler<Long> handler);

	@Suspendable
	boolean cancelTimer(TimerId id);
}
