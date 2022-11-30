package hk.ust.comp3021.replay;


import hk.ust.comp3021.actions.ActionResult;
import hk.ust.comp3021.actions.Exit;
import hk.ust.comp3021.game.AbstractSokobanGame;
import hk.ust.comp3021.game.GameState;
import hk.ust.comp3021.game.InputEngine;
import hk.ust.comp3021.game.RenderingEngine;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

import static hk.ust.comp3021.utils.StringResources.UNDO_QUOTA_TEMPLATE;
import static hk.ust.comp3021.utils.StringResources.UNDO_QUOTA_UNLIMITED;

/**
 * A thread-safe Sokoban game.
 * The game should be able to run in a separate thread, and games running in parallel should not interfere with each other.
 * <p>
 * The game can run in two modes:
 * 1. {@link Mode#ROUND_ROBIN} mode: all input engines take turns to perform actions, starting from the first specified input engine.
 * Example: suppose there are two input engines, A and B, whose actions are [R, L], [R, L], respectively.
 * In this mode, the game will perform the following actions in order: A.R, B.R, A.L, B.L.
 * 2. {@link Mode#FREE_RACE} mode: all input engines perform actions simultaneously. The actions processed can be in any order.
 * There could be a chance that two runs of the same game process actions in different orders.
 * <p>
 * {@link hk.ust.comp3021.Sokoban#replayGame(int, String, Mode, int, String[])} runs multiple games in parallel.
 */
public class ReplaySokobanGame extends AbstractSokobanGame {
    /**
     * Mode of scheduling actions among input engines.
     */
    public enum Mode {
        /**
         * All input engines take turns to perform actions, starting from the first specified input engine.
         */
        ROUND_ROBIN,

        /**
         * All input engines perform actions concurrently without enforcing the order.
         */
        FREE_RACE,
    }

    protected final Mode mode;
    /**
     * Indicated the frame rate of the rendering engine (in FPS).
     */
    protected final int frameRate;

    /**
     * Default frame rate.
     */
    protected static final int DEFAULT_FRAME_RATE = 60;

    /**
     * The list of input engines to fetch inputs.
     */
    protected final List<? extends InputEngine> inputEngines;

    /**
     * The rendering engine to render the game status.
     */
    protected final RenderingEngine renderingEngine;

    protected int numInputEngines = 0;
    protected final AtomicInteger nextIndex = new AtomicInteger(0);

    protected final ReentrantLock freeRaceLock = new ReentrantLock(false);

    protected final AtomicBoolean checkFirst = new AtomicBoolean(false);


    protected final AtomicInteger exitCount = new AtomicInteger(0);

    private static final long SLEEP_PRECISION = TimeUnit.MILLISECONDS.toNanos(2);

    private static final long NANO_SEC = 1_000_000_000;


    /**
     * Create a new instance of ReplaySokobanGame.
     * Each input engine corresponds to an action file and will produce actions from the action file.
     *
     * @param mode            The mode of the game.
     * @param frameRate       Rendering fps.
     * @param gameState       The game state.
     * @param inputEngines    the input engines.
     * @param renderingEngine the rendering engine.
     */
    public ReplaySokobanGame(
            @NotNull Mode mode,
            int frameRate,
            @NotNull GameState gameState,
            @NotNull List<? extends InputEngine> inputEngines,
            @NotNull RenderingEngine renderingEngine
    ) {
        super(gameState);
        if (inputEngines.size() == 0)
            throw new IllegalArgumentException("No input engine specified");
        this.mode = mode;
        this.frameRate = frameRate;
        this.renderingEngine = renderingEngine;
        this.inputEngines = inputEngines;
    }

    /**
     * @param gameState       The game state.
     * @param inputEngines    the input engines.
     * @param renderingEngine the rendering engine.
     */
    public ReplaySokobanGame(
            @NotNull GameState gameState,
            @NotNull List<? extends InputEngine> inputEngines,
            @NotNull RenderingEngine renderingEngine) {
        this(Mode.FREE_RACE, DEFAULT_FRAME_RATE, gameState, inputEngines, renderingEngine);
    }

    // TODO: add any method or field you need.

    @Override
    protected boolean shouldStop() {
        return this.state.isWin();
    }

    /**
     * The implementation of the Runnable for each input engine thread.
     * Each input engine should run in a separate thread.
     * <p>
     * Assumption:
     * 1. the last action fetch-able from the input engine is always an {@link Exit} action.
     * <p>
     * Requirements:
     * 1. All actions fetched from input engine should be processed in the order they are fetched.
     * 2. All actions before (including) the first {@link Exit} action should be processed
     * (passed to {@link this#processAction} method).
     * 3. Any actions after the first {@link Exit} action should be ignored
     * (not passed to {@link this#processAction}).
     */
    private class InputEngineRunnable implements Runnable {
        private final int index;
        private final InputEngine inputEngine;

        private boolean exitFlag = false;

        private InputEngineRunnable(int index, @NotNull InputEngine inputEngine) {
            this.index = index;
            this.inputEngine = inputEngine;
            numInputEngines++;
        }

        @Override
        public void run() {
            // TODO: modify this method to implement the requirements.
            synchronized (checkFirst) {
                while (!checkFirst.get()) {
                    try {
                        checkFirst.wait();
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                }
            }

            while (!shouldStop() && (exitCount.get() != numInputEngines)) {
                try {
                    if (mode == Mode.ROUND_ROBIN) {
                        synchronized (nextIndex) {
                            while (nextIndex.get() != index) {
                                nextIndex.wait();
                            }
                        }
                    } else if (mode == Mode.FREE_RACE) {
                        freeRaceLock.lock();
                    }

                    if (!exitFlag) {
                        final var action = inputEngine.fetchAction();
                        if (action instanceof Exit) {
                            synchronized (exitCount) {
                                exitCount.getAndIncrement();
                            }
                            exitFlag = true;
                        }
                        synchronized (state) {
                            final var result = processAction(action);
                            if (result instanceof ActionResult.Failed failed) {
                                renderingEngine.message(failed.getReason());
                            }
                        }
                    }

                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                } finally {
                    if (mode == Mode.ROUND_ROBIN) {
                        synchronized (nextIndex) {
                            nextIndex.set((nextIndex.get() + 1) % numInputEngines);
                            nextIndex.notifyAll();
                        }
                    } else if (mode == Mode.FREE_RACE) {
                        freeRaceLock.unlock();
                    }


                }

            }

        }
    }

    /**
     * The implementation of the Runnable for the rendering engine thread.
     * The rendering engine should run in a separate thread.
     * <p>
     * Requirements:
     * 1. The game map should be rendered at least once before any action is processed (the initial state should be rendered).
     * 2. The game map should be rendered after the last action is processed (the final state should be rendered).
     */
    private class RenderingEngineRunnable implements Runnable {
        /**
         * NOTE: You are NOT allowed to use {@link java.util.Timer} or {@link java.util.TimerTask} in this method.
         * Please use a loop with {@link Thread#sleep(long)} instead.
         */
        @Override
        public void run() {


            // TODO: modify this method to implement the requirements.
            do {
                synchronized (state) {
                    final var undoQuotaMessage = state.getUndoQuota()
                            .map(it -> String.format(UNDO_QUOTA_TEMPLATE, it))
                            .orElse(UNDO_QUOTA_UNLIMITED);
                    renderingEngine.message(undoQuotaMessage);
                    renderingEngine.render(state);
                }
                synchronized (checkFirst) {
                    if (!checkFirst.get()) {
                        checkFirst.set(true);
                        checkFirst.notifyAll();
                    }
                }
                try {
                    sleepNanos(NANO_SEC / frameRate);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
                synchronized (exitCount) {
                    if (exitCount.get() == numInputEngines) {
                        break;
                    }
                }


            } while (!shouldStop());

            final var undoQuotaMessage = state.getUndoQuota()
                    .map(it -> String.format(UNDO_QUOTA_TEMPLATE, it))
                    .orElse(UNDO_QUOTA_UNLIMITED);
            renderingEngine.message(undoQuotaMessage);
            renderingEngine.render(state);

        }
    }

    private static void sleepNanos (long nanoDuration) throws InterruptedException {
        final long endTime = System.nanoTime() + nanoDuration;
        long timeLeft = nanoDuration;
        do {
            if (timeLeft > SLEEP_PRECISION) {
                Thread.sleep(1);
            } else {
                Thread.yield();
            }
            timeLeft = endTime - System.nanoTime();
        } while (timeLeft > 0);
    }


    /**
     * Start the game.
     * This method should spawn new threads for each input engine and the rendering engine.
     * This method should wait for all threads to finish before return.
     */
    @Override
    public void run() {

        var inputEngineThreads = new ArrayList<Thread>();
        var index = 0;
        for (InputEngine inputEngine : inputEngines) {
            var thread = new Thread(new InputEngineRunnable(index, inputEngine));
            inputEngineThreads.add(thread);
            index++;
        }

        for (var thread : inputEngineThreads) {
            thread.start();
        }
        Thread renderingThread = new Thread(new RenderingEngineRunnable());
        renderingThread.setPriority(Thread.MAX_PRIORITY);
        renderingThread.start();


        try {
            for (var thread : inputEngineThreads) {
                thread.join();
            }
            renderingThread.join();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }



    }

}
