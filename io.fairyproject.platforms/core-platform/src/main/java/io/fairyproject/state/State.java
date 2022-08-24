/*
 * MIT License
 *
 * Copyright (c) 2021 Imanity
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package io.fairyproject.state;

import io.fairyproject.state.strategy.StateStrategy;
import io.fairyproject.util.terminable.Terminable;
import io.fairyproject.util.terminable.TerminableConsumer;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.function.Consumer;

public interface State extends Terminable, TerminableConsumer {
    void start();

    void update();

    void end();

    long getTimePast();

    boolean isReadyToEnd();

    void pause();

    void unpause();

    boolean isStarted();

    boolean isEnded();

    boolean isPaused();

    boolean isUpdating();

    void onSuspend();

    long getStartTimestamp();

    void addStrategy(@NotNull StateStrategy strategy, @NotNull StateStrategy.Type type);

    void removeStrategy(@NotNull StateStrategy strategy, @NotNull StateStrategy.Type type);

    @NotNull Collection<StateStrategy> strategies(@NotNull StateStrategy.Type type);

    void forEachStrategies(@NotNull Consumer<StateStrategy> consumer, @NotNull StateStrategy.Type type);
}
