package com.hiddenswitch.spellsource;

import com.github.fromage.quasi.fibers.SuspendExecution;
import com.github.fromage.quasi.strands.Strand;
import com.hiddenswitch.spellsource.client.ApiClient;
import com.hiddenswitch.spellsource.client.ApiException;
import com.hiddenswitch.spellsource.client.api.DefaultApi;
import com.hiddenswitch.spellsource.client.models.*;
import com.hiddenswitch.spellsource.common.DeckCreateRequest;
import com.hiddenswitch.spellsource.concurrent.SuspendableMap;
import com.hiddenswitch.spellsource.impl.GameId;
import com.hiddenswitch.spellsource.impl.SpellsourceTestBase;
import com.hiddenswitch.spellsource.impl.UserId;
import com.hiddenswitch.spellsource.models.ConfigurationRequest;
import com.hiddenswitch.spellsource.util.Sync;
import com.hiddenswitch.spellsource.util.UnityClient;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import net.demilich.metastone.game.behaviour.Behaviour;
import net.demilich.metastone.game.cards.CardCatalogue;
import net.demilich.metastone.game.cards.CardType;
import net.demilich.metastone.game.decks.DeckFormat;
import org.apache.commons.lang3.RandomStringUtils;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static io.vertx.ext.sync.Sync.awaitResult;
import static org.junit.Assert.*;

/**
 * Created by bberman on 2/18/17.
 */
public class GatewayTest extends SpellsourceTestBase {
	private static Logger logger = LoggerFactory.getLogger(GatewayTest.class);

	@Test(timeout = 15000L)
	public void testAccountFlow(TestContext context) throws InterruptedException {
		Set<String> decks = Spellsource.spellsource().getStandardDecks().stream().map(DeckCreateRequest::getName).collect(Collectors.toSet());

		final int expectedCount = 10;
		CountDownLatch latch = new CountDownLatch(expectedCount);

		for (int i = 0; i < expectedCount; i++) {
			final int j = i;

			Thread t = new Thread(() -> {
				ApiClient client = new ApiClient().setBasePath(UnityClient.basePath);
				DefaultApi api = new DefaultApi(client);
				String random = RandomStringUtils.randomAlphanumeric(36) + Integer.toString(j);
				try {
					CreateAccountResponse response1 = api.createAccount(new CreateAccountRequest()
							.name("username" + random)
							.email("email" + random + "@email.com")
							.password("password"));

					api.getApiClient().setApiKey(response1.getLoginToken());
					final String userId = response1.getAccount().getId();
					assertNotNull(userId);
					GetAccountsResponse response2 = api.getAccount(userId);
					assertTrue(response2.getAccounts().size() > 0);

					for (Account account : new Account[]{response1.getAccount(), response2.getAccounts().get(0)}) {
						assertNotNull(account.getId());
						assertNotNull(account.getEmail());
						assertNotNull(account.getName());
						assertNotNull(account.getPersonalCollection());
						assertNotNull(account.getDecks());
						assertEquals(account.getDecks().size(), Spellsource.spellsource().getStandardDecks().size());
						assertTrue(account.getDecks().stream().map(InventoryCollection::getName).collect(Collectors.toSet()).containsAll(decks));
						assertTrue(account.getPersonalCollection().getInventory().size() > 0);
					}
				} catch (ApiException e) {
					fail("API error: " + e.getMessage());
					return;
				}
				latch.countDown();
			});

			t.start();
		}

		latch.await(15L, TimeUnit.SECONDS);
		assertEquals(latch.getCount(), 0L);
	}

	@Test(timeout = 10000L)
	public void testLoginWithInvalidToken(TestContext context) throws ApiException {
		Async async = context.async();

		vertx.runOnContext(v -> {
			vertx.executeBlocking(fut -> {
				DefaultApi api = new DefaultApi(new ApiClient().setBasePath(UnityClient.basePath));
				CreateAccountResponse car = null;
				try {
					car = api.createAccount(new CreateAccountRequest()
							.email(RandomStringUtils.randomAlphanumeric(32) + "@test.com")
							.name(RandomStringUtils.randomAlphanumeric(32) + "name")
							.password("password"));
				} catch (ApiException e) {
					vertx.exceptionHandler().handle(e);
					fut.fail(e);
					return;
				}
				api.getApiClient().setApiKey(car.getLoginToken());
				GetAccountsResponse accountsResponse = null;
				try {
					accountsResponse = api.getAccount(car.getAccount().getId());
				} catch (ApiException e) {
					vertx.exceptionHandler().handle(e);
					fut.fail(e);
					return;
				}
				// Assert we have access to private information here
				context.assertNotNull(accountsResponse.getAccounts().get(0).getEmail());
				// Change the key to something differently invalid
				api.getApiClient().setApiKey("invaliduserid:invalidtoken");
				try {
					accountsResponse = api.getAccount(car.getAccount().getId());
					context.fail("Successfully received account when we shouldn't have had.");
				} catch (ApiException ex) {
					context.assertEquals(ex.getCode(), 403, "Assert not authorized");
				}
				// Change the key to something invalid
				api.getApiClient().setApiKey(car.getAccount().getId() + ":invalidtoken");
				try {
					accountsResponse = api.getAccount(car.getAccount().getId());
					context.fail("Successfully received account when we shouldn't have had.");
				} catch (ApiException ex) {
					context.assertEquals(ex.getCode(), 403, "Assert not authorized");
				}
				fut.complete();
			}, context.asyncAssertSuccess(v2 -> {
				async.complete();
			}));
		});

	}

	@Test(timeout = 15000L)
	public void testUnityClient(TestContext context) throws InterruptedException, SuspendExecution {
		// Just run once
		UnityClient client = new UnityClient(context);
		client.createUserAccount(null);
		client.matchmakeQuickPlay(null);
		client.waitUntilDone();
		assertTrue(client.isGameOver());
	}

	@Test(timeout = 165000L)
	public void testSimultaneousGames(TestContext context) throws InterruptedException, SuspendExecution {
		final int processorCount = Runtime.getRuntime().availableProcessors();
		final int count = processorCount * 2;
		final int checkpointTotal = count * 6;

		CountDownLatch latch = new CountDownLatch(count);
		ExecutorService testExecutor = Executors.newFixedThreadPool(count);

		Deque<String> verticles = new ConcurrentLinkedDeque<>();
		// Deploy processorCount-1 more gateways
		CompositeFuture.all(Stream.generate(() -> {
			Future<String> fut = Future.future();
			vertx.deployVerticle(Gateway.create(), v1 -> {
				verticles.add(v1.result());
				fut.complete();
			});
			return fut;
		}).limit(processorCount - 1).collect(Collectors.toList())).setHandler(v1 -> {
			AtomicInteger checkpoints = new AtomicInteger(0);
			for (int i = 0; i < count; i++) {
				testExecutor.submit(() -> {
					try {
						UnityClient client = new UnityClient(context);
						client.createUserAccount(null);
						String userId = client.getAccount().getId();
						logger.trace("testSimultaneousGames: {} 1st Matchmaking on {}/{} checkpoints", userId, checkpoints.incrementAndGet(), checkpointTotal);
						client.matchmakeConstructedPlay(null);
						logger.trace("testSimultaneousGames: {} 1st Starts on {}/{} checkpoints", userId, checkpoints.incrementAndGet(), checkpointTotal);
						client.waitUntilDone();
						assertTrue(client.isGameOver());
						assertFalse(client.getApi().getAccount(userId).getAccounts().get(0).isInMatch());
						logger.trace("testSimultaneousGames: {} 1st Finished {}/{} checkpoints", userId, checkpoints.incrementAndGet(), checkpointTotal);

						// Try two games in a row
						logger.trace("testSimultaneousGames: {} 2nd Matchmaking on {}/{} checkpoints", userId, checkpoints.incrementAndGet(), checkpointTotal);
						client.matchmakeConstructedPlay(null);
						logger.trace("testSimultaneousGames: {} 2nd Starts on {}/{} checkpoints", userId, checkpoints.incrementAndGet(), checkpointTotal);
						client.waitUntilDone();
						assertTrue(client.isGameOver());
						assertFalse(client.getApi().getAccount(userId).getAccounts().get(0).isInMatch());
						logger.trace("testSimultaneousGames: {} 2nd Finished {}/{} checkpoints", userId, checkpoints.incrementAndGet(), checkpointTotal);

						latch.countDown();
					} catch (ApiException t) {
						context.exceptionHandler().handle(t);
					}
				});
			}
		});

		// Random games can take quite a long time to finish so be patient...
		latch.await(165L, TimeUnit.SECONDS);
		assertEquals(0L, latch.getCount());
		testExecutor.shutdown();


		CompositeFuture.all(verticles.stream().map(deploymentId -> {
			Future<Void> fut = Future.future();
			vertx.undeploy(deploymentId, fut);
			return fut;
		}).collect(Collectors.toList())).setHandler(context.asyncAssertSuccess());

	}


	@Test(timeout = 35000L)
	public void testConcede(TestContext context) {
		sync(() -> {
			UnityClient client = new UnityClient(context);
			Sync.invoke0(() -> {
				client.createUserAccount();
				client.getTurnsToPlay().set(1);
				client.matchmakeQuickPlay(null);
			});

			Strand.sleep(3000L);
			// 1 turn was played, concede

			Future<Void> gameOver = Future.future();
			client.gameOverHandler(ignored -> gameOver.complete());
			Sync.invoke0(client::concede);
			// You must wait until you receive the end game message to be confident you can requeue
			awaitResult(gameOver::setHandler);
			client.gameOverHandler(null);
			GetAccountsResponse account = Sync.invoke(targetUserId -> {
				try {
					return client.getApi().getAccount(targetUserId);
				} catch (ApiException e) {
					throw new AssertionError(e);
				}
			}, client.getAccount().getId());
			assertFalse(account.getAccounts().get(0).isInMatch());
			SuspendableMap<UserId, GameId> games = Games.getGames();
			boolean hasUser = games.containsKey(new UserId(client.getAccount().getId()));
			context.assertFalse(hasUser);

			// Can go into another game
			Sync.invoke0(() -> {
				client.disconnect();
				try {
					Strand.sleep(200L);
				} catch (SuspendExecution | InterruptedException execution) {
					context.fail(execution);
					return;
				}

				client.getTurnsToPlay().set(999);
				client.matchmakeQuickPlay(null);
				client.waitUntilDone();
				context.assertTrue(client.isGameOver());
			});
		});
	}

	@Test(timeout = 25000L)
	public void testGameClosedAfterNoActivity(TestContext context) {
		System.setProperty("games.defaultNoActivityTimeout", "8000");
		assertEquals(Games.getDefaultNoActivityTimeout(), 8000L);

		UnityClient client = new UnityClient(context);
		client.setShouldDisconnect(true);
		client.getTurnsToPlay().set(1);
		client.createUserAccount(null);
		final String token = client.getToken();
		final String userId = client.getAccount().getId();
		client.matchmakeQuickPlay(null);

		// Assert that session was closed
		sync(() -> {
			// wait 10 seconds
			Strand.sleep(10000L);
			assertNull(Games.getGames().get(new UserId(userId)));

			Boolean done = Sync.invoke(() -> {
				UnityClient client2 = new UnityClient(context, token);
				try {
					return client2.getApi().getAccount(userId).getAccounts().get(0).isInMatch();
				} catch (ApiException e) {
					throw new AssertionError();
				}
			});

			assertEquals(false, done);
		});

		// Should not have received end game message
		assertFalse(client.isGameOver());
	}

	@Test(timeout = 45000L)
	public void testGameDoesntCloseAfterActivity(TestContext context) {
		System.setProperty("games.defaultNoActivityTimeout", "8000");
		assertEquals(Games.getDefaultNoActivityTimeout(), 8000L);
		AtomicInteger counter = new AtomicInteger();
		AtomicLong startTime = new AtomicLong();
		UnityClient client = new UnityClient(context) {
			@Override
			protected boolean onRequestAction(ServerToClientMessage message) {
				logger.trace("testGameDoesntCloseAfterActivity: Sending request {}", counter.get());
				// Spend 7 seconds waiting to send the first action
				if (counter.getAndIncrement() == 0) {
					startTime.set(System.currentTimeMillis());
					vertx.setTimer(7000L, ignored -> {
						this.respondRandomAction(message);
					});
				} else {
					// Otherwise, slow things down only a little bit
					vertx.setTimer(150L, ignored -> {
						this.respondRandomAction(message);
					});
				}
				return false;
			}
		};


		client.createUserAccount(null);
		client.matchmakeQuickPlay(null);
		client.waitUntilDone();
		assertTrue(System.currentTimeMillis() - startTime.get() > 8000L);
		assertTrue(client.isGameOver());
	}


	@Test
	public void testCardsCollection(TestContext context) throws ApiException {
		DefaultApi defaultApi = getApi();

		GetCardsResponse response1 = defaultApi.getCards(null);
		final long count = CardCatalogue.getRecords().values().stream().filter(
				cd -> DeckFormat.CUSTOM.isInFormat(cd.getDesc().getSet()) && cd.getDesc().type != CardType.GROUP && cd.getDesc().type != CardType.HERO_POWER && cd.getDesc().type != CardType.ENCHANTMENT).count();
		context.assertEquals((long) response1.getCards().size(), count);
		try {
			GetCardsResponse response2 = defaultApi.getCards(response1.getVersion());
			context.fail();
		} catch (ApiException ex) {
			context.assertEquals(ex.getCode(), 304);
		}

		GetCardsResponse response3 = defaultApi.getCards("invalid token");
		context.assertNotNull(response3);
		context.assertEquals(response3.getVersion(), response1.getVersion());
	}

	@Test
	public void testMatchmakingCancellation(TestContext context) throws ApiException, InterruptedException {
		UnityClient client1 = new UnityClient(context);
		client1.createUserAccount();
		java.util.concurrent.Future<Void> matchmaking = client1.matchmake(null, "constructed");
		Thread.sleep(2000L);
		matchmaking.cancel(true);
		UnityClient client2 = new UnityClient(context);
		client2.createUserAccount();
		java.util.concurrent.Future<Void> other = client2.matchmake(null, "constructed");
		Thread.sleep(2000L);
		sync(() -> context.assertFalse(Games.getGames().containsKey(new UserId(client1.getAccount().getId()))));
		other.cancel(true);
		Thread.sleep(2000L);
	}
}
