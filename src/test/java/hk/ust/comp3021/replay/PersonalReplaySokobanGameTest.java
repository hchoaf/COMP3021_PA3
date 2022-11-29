package hk.ust.comp3021.replay;

import hk.ust.comp3021.actions.*;
import hk.ust.comp3021.game.*;
import hk.ust.comp3021.utils.TestKind;
import org.junit.jupiter.api.*;

import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PersonalReplaySokobanGameTest {

    @DisplayName("Game's run method should spawn a new thread for rendering engine")
    @Tag(TestKind.PUBLIC)
    @RepeatedTest(10)
    void testRenderingEngineThread() {
        final var gameState = mock(GameState.class);
        final var inputEngine = mock(InputEngine.class);
        final var renderingEngine = mock(RenderingEngine.class);
        final var game = new TestGame(gameState, List.of(inputEngine), renderingEngine);

        final var renderThreadIds = new ConcurrentLinkedQueue<Long>();
        doAnswer(invocation -> {
            final var threadID = Thread.currentThread().getId();
            renderThreadIds.add(threadID);
            return null;
        }).when(renderingEngine).render(any());
        when(inputEngine.fetchAction())
                .thenAnswer(new RandomlyPausedActionProducer(new Move.Right(0), new Exit()));

        game.run();

        assertTrue(renderThreadIds.size() > 0);
        final var renderThreadId = renderThreadIds.poll();
        while (!renderThreadIds.isEmpty()) {
            assertEquals(renderThreadId, renderThreadIds.poll());
        }
    }

    @DisplayName("Game's run method should spawn one thread for each input engine")
    @Tag(TestKind.PUBLIC)
    @RepeatedTest(10)
    void testInputEngineThread() {
        final var gameState = mock(GameState.class);
        final var inputEngine0 = mock(InputEngine.class);
        final var inputEngine1 = mock(InputEngine.class);
        final var inputEngine2 = mock(InputEngine.class);
        final var inputEngine3 = mock(InputEngine.class);
        final var inputEngine4 = mock(InputEngine.class);
        final var renderingEngine = mock(RenderingEngine.class);
        final var game = new TestGame(gameState, List.of(inputEngine0, inputEngine1, inputEngine2, inputEngine3, inputEngine4), renderingEngine);

        final var threadIds0 = new ConcurrentLinkedQueue<Long>();
        final var threadIds1 = new ConcurrentLinkedQueue<Long>();
        final var threadIds2 = new ConcurrentLinkedQueue<Long>();
        final var threadIds3 = new ConcurrentLinkedQueue<Long>();
        final var threadIds4 = new ConcurrentLinkedQueue<Long>();
        final var actionProducer0 = new RandomlyPausedActionProducer(new Move.Right(0), new Move.Left(0), new Move.Up(0), new Undo(0), new Exit(), new Move.Left(0));
        final var actionProducer1 = new RandomlyPausedActionProducer(new Move.Right(1), new Move.Right(1), new Move.Right(1), new Move.Right(1), new Move.Right(1), new Move.Right(1), new Move.Right(1), new Move.Right(1), new Move.Right(1), new Move.Right(1), new Move.Right(1), new Move.Right(1), new Exit());
        final var actionProducer2 = new RandomlyPausedActionProducer(new Move.Right(2), new Exit());
        final var actionProducer3 = new RandomlyPausedActionProducer(new Move.Right(3), new Exit());
        final var actionProducer4 = new RandomlyPausedActionProducer(new Move.Right(4), new Exit());
        when(inputEngine0.fetchAction()).thenAnswer(invocation -> {
            final var threadID = Thread.currentThread().getId();
            threadIds0.add(threadID);
            return actionProducer0.produce();
        });
        when(inputEngine1.fetchAction()).thenAnswer(invocation -> {
            final var threadID = Thread.currentThread().getId();
            threadIds1.add(threadID);
            return actionProducer1.produce();
        });
        when(inputEngine2.fetchAction()).thenAnswer(invocation -> {
            final var threadID = Thread.currentThread().getId();
            threadIds2.add(threadID);
            return actionProducer2.produce();
        });
        when(inputEngine3.fetchAction()).thenAnswer(invocation -> {
            final var threadID = Thread.currentThread().getId();
            threadIds3.add(threadID);
            return actionProducer3.produce();
        });
        when(inputEngine4.fetchAction()).thenAnswer(invocation -> {
            final var threadID = Thread.currentThread().getId();
            threadIds4.add(threadID);
            return actionProducer4.produce();
        });
        game.run();

        assertTrue(threadIds0.size() > 0);
        assertTrue(threadIds1.size() > 0);
        assertTrue(threadIds2.size() > 0);
        assertTrue(threadIds3.size() > 0);
        assertTrue(threadIds4.size() > 0);
        final var threadIds = new HashSet<Long>();
        threadIds.add(Thread.currentThread().getId());
        final var th0 = threadIds0.poll();
        while (!threadIds0.isEmpty()) {
            assertEquals(th0, threadIds0.poll());
        }
        threadIds.add(th0);
        final var th1 = threadIds1.poll();
        while (!threadIds1.isEmpty()) {
            assertEquals(th1, threadIds1.poll());
        }
        threadIds.add(th1);
        final var th2 = threadIds2.poll();
        while (!threadIds2.isEmpty()) {
            assertEquals(th2, threadIds2.poll());
        }
        threadIds.add(th2);
        final var th3 = threadIds3.poll();
        while (!threadIds2.isEmpty()) {
            assertEquals(th3, threadIds3.poll());
        }
        threadIds.add(th3);
        final var th4 = threadIds4.poll();
        while (!threadIds4.isEmpty()) {
            assertEquals(th4, threadIds4.poll());
        }
        threadIds.add(th4);
        assertEquals(6, threadIds.size());
    }

    @DisplayName("Moves from the same input engine should be processed in the same order (multiple input engine)")
    @Tag(TestKind.PUBLIC)
    @RepeatedTest(10)
    void testMovesOrderMultiple() {
        final var gameState = mock(GameState.class);
        final var inputEngine0 = mock(StreamInputEngine.class);
        final var inputEngine1 = mock(StreamInputEngine.class);
        final var renderingEngine = mock(RenderingEngine.class);
        final var game = spy(new TestGame(gameState, List.of(inputEngine0, inputEngine1), renderingEngine));

        final var actions0 = Arrays.<Action>asList(new Move.Left(0), new Move.Right(0), new Move.Right(0), new Move.Right(0), new Move.Down(0), new Move.Up(0));
        final var actions1 = Arrays.<Action>asList(new Move.Left(1), new Move.Right(1), new Move.Right(1), new Move.Right(1), new Move.Down(1), new Move.Up(1));
        when(inputEngine0.fetchAction()).thenAnswer(new RandomlyPausedActionProducer(actions0));
        when(inputEngine1.fetchAction()).thenAnswer(new RandomlyPausedActionProducer(actions1));
        final var processedActions = new ActionList();
        doAnswer(invocation -> {
            processedActions.add(invocation.getArgument(0));
            return invocation.callRealMethod();
        }).when(game).processAction(any());

        game.run();

        assertArrayEquals(actions0.toArray(), processedActions.stream().filter(action -> action.getInitiator() == 0).toArray());
        assertArrayEquals(actions1.toArray(), processedActions.stream().filter(action -> action.getInitiator() == 1).toArray());
    }

    @DisplayName("Action order should be enforced in ROUND_ROBIN mode (all input engines have same length of actions")
    @Tag(TestKind.PUBLIC)
    @RepeatedTest(10)
    void testRoundRobinModeEqualLength() {
        final var gameState = mock(GameState.class);
        final var inputEngine0 = mock(StreamInputEngine.class);
        final var inputEngine1 = mock(StreamInputEngine.class);
        final var inputEngine2 = mock(StreamInputEngine.class);
        final var renderingEngine = mock(RenderingEngine.class);
        final var inputEngines = List.of(inputEngine0, inputEngine1, inputEngine2);
        final var game = spy(new TestGame(ReplaySokobanGame.Mode.ROUND_ROBIN, gameState, inputEngines, renderingEngine));

        final var actions0 = Arrays.<Action>asList(new Move.Down(0), new Move.Right(0), new Move.Left(0), new Move.Up(0), new Move.Down(0));
        final var actions1 = Arrays.<Action>asList(new Move.Left(1), new Move.Right(1), new Move.Right(1), new Move.Up(1), new Move.Down(1));
        final var actions2 = Arrays.<Action>asList(new Move.Left(2), new Move.Right(2), new Move.Right(2), new Move.Up(2), new Move.Down(2));
        final var actionsLists = new List[]{actions0, actions1, actions2};
        final var processActions = new ActionList();
        when(inputEngine0.fetchAction()).thenAnswer(new RandomlyPausedActionProducer(actions0));
        when(inputEngine1.fetchAction()).thenAnswer(new RandomlyPausedActionProducer(actions1));
        when(inputEngine2.fetchAction()).thenAnswer(new RandomlyPausedActionProducer(actions2));
        doAnswer(invocation -> {
            final var action = invocation.getArgument(0, Action.class);
            processActions.add(action);
            return invocation.callRealMethod();
        }).when(game).processAction(any());

        game.run();

        int i = 0;
        while (i < actions0.size() && i < actions1.size()) {
            final var round = i % inputEngines.size();
            final var index = i / inputEngines.size();
            final var actionList = actionsLists[round];
            if (index < actionList.size()) {
                assertEquals(actionList.get(index), processActions.get(i));
            }
            i++;
        }
    }

    @DisplayName("FPS parameter should specify the times render method is invoked per second")
    @Timeout(5)
    @Tag(TestKind.PUBLIC)
    @RepeatedTest(10)
    void testFPS() {
        final var fps = 50;
        final var gameState = mock(GameState.class);
        final var inputEngine = mock(InputEngine.class);
        final var renderingEngine = mock(RenderingEngine.class);
        final var game = new TestGame(ReplaySokobanGame.Mode.FREE_RACE, fps, gameState, List.of(inputEngine), renderingEngine);

        final var actions = Arrays.<Action>asList(
                new Move.Down(0),
                new Move.Right(0),
                new Move.Right(0),
                new Move.Left(0),
                new Move.Up(0)
        );
        final var renderTimes = new ArrayList<Date>();
        when(inputEngine.fetchAction()).thenAnswer(new RandomlyPausedActionProducer(90, 110, actions));
        doAnswer(invocation -> {
            renderTimes.add(new Date());
            return null;
        }).when(renderingEngine).render(any());

        game.run();

        assertTrue(renderTimes.size() > 0);
        final var timeElapsed = renderTimes.get(renderTimes.size() - 1).getTime() - renderTimes.get(0).getTime();
        final var expected = (float) timeElapsed / 1000 * fps;
        System.out.printf("%d elapsed. %f expected %n", renderTimes.size(), expected);
        assertEquals(expected, renderTimes.size(), (float) (expected * 0.1)); // 10% error tolerance
    }
}


