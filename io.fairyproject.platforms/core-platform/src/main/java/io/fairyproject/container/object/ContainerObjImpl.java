package io.fairyproject.container.object;

import io.fairyproject.container.ServiceDependencyType;
import io.fairyproject.container.Threading;
import io.fairyproject.container.collection.ContainerObjCollector;
import io.fairyproject.container.object.lifecycle.LifeCycleHandler;
import io.fairyproject.metadata.MetadataMap;
import io.fairyproject.util.AsyncUtils;
import io.fairyproject.util.terminable.composite.CompositeTerminable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class ContainerObjImpl implements ContainerObj {

    private final Set<ContainerObj.DependEntry> depends = ConcurrentHashMap.newKeySet();
    private final Set<LifeCycleHandler> lifeCycleHandlers = ConcurrentHashMap.newKeySet();
    private final Set<ContainerObjCollector> collectors = ConcurrentHashMap.newKeySet();
    private final CompositeTerminable compositeTerminable = CompositeTerminable.create();
    private final MetadataMap metadataMap = MetadataMap.create();
    private final Class<?> type;

    private Object instance = null;
    private LifeCycle lifeCycle = LifeCycle.NONE;

    private Threading.Mode threadingMode = Threading.Mode.SYNC;

    public ContainerObjImpl(Class<?> type) {
        this.type = type;
    }

    @Override
    public @NotNull Class<?> type() {
        return this.type;
    }

    @Override
    public @Nullable Object instance() {
        return this.instance;
    }

    @Override
    public @NotNull LifeCycle lifeCycle() {
        return this.lifeCycle;
    }

    @Override
    public @NotNull Threading.Mode threadingMode() {
        return this.threadingMode;
    }

    @Override
    public @NotNull ContainerObj setThreadingMode(Threading.@NotNull Mode threadingMode) {
        this.threadingMode = threadingMode;
        return this;
    }

    @Override
    public @NotNull CompletableFuture<?> setLifeCycle(@NotNull LifeCycle lifeCycle) {
        if (lifeCycle == this.lifeCycle) {
            return AsyncUtils.empty();
        }
        this.lifeCycle = lifeCycle;
        if (!this.lifeCycleHandlers.isEmpty()) {
            return AsyncUtils.allOf(this.lifeCycleHandlers.stream()
                    .map(lifeCycleHandler -> lifeCycleHandler.apply(lifeCycle))
                    .collect(Collectors.toList())
            );
        }
        else
            return AsyncUtils.empty();
    }

    @NotNull
    @Override
    public CompletableFuture<?> initLifeCycleHandlers() {
        return this.threadingMode.execute(() -> this.lifeCycleHandlers.forEach(LifeCycleHandler::init));
    }

    @Override
    public @NotNull ContainerObj addLifeCycleHandler(@NotNull LifeCycleHandler lifeCycleHandler) {
        this.lifeCycleHandlers.add(lifeCycleHandler);
        return this;
    }

    @Override
    public void setInstance(@NotNull Object instance) {
        this.instance = instance;
    }

    @Override
    public @NotNull Collection<Class<?>> depends() {
        return this.depends.stream()
                .map(ContainerObj.DependEntry::getDependClass)
                .collect(Collectors.toList());
    }

    @Override
    public @NotNull Collection<ContainerObj.DependEntry> dependEntries() {
        return Collections.unmodifiableSet(this.depends);
    }

    @Override
    public void addDepend(@NotNull Class<?> dependClass, @NotNull ServiceDependencyType dependType) {
        // Avoid adding duplicated dependencies
        for (DependEntry entry : this.depends) {
            if (entry.getDependClass() == dependClass) {
                // If new dependency type is stricter, overwrite it
                if (entry.getDependType().ordinal() > dependType.ordinal())
                    this.depends.remove(entry);
                else
                    return;
            }
        }
        this.depends.add(ContainerObj.DependEntry.of(dependClass, dependType));
    }

    @Override
    public @NotNull Collection<ContainerObjCollector> collectors() {
        return Collections.unmodifiableSet(this.collectors);
    }

    @Override
    public @NotNull MetadataMap metadata() {
        return this.metadataMap;
    }

    @Override
    public void addCollector(@NotNull ContainerObjCollector collector) {
        this.collectors.add(collector);
    }

    @Override
    public void removeCollector(@NotNull ContainerObjCollector collector) {
        this.collectors.remove(collector);
    }

    @NotNull
    @Override
    public <T extends AutoCloseable> T bind(@NotNull T terminable) {
        return this.compositeTerminable.bind(terminable);
    }

    @Override
    public void close() throws Exception {
        this.compositeTerminable.close();

        this.collectors.forEach(collector -> collector.remove(this));
        this.collectors.clear();
    }

    @Override
    public String toString() {
        return this.type.toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ContainerObjImpl that = (ContainerObjImpl) o;
        return type.equals(that.type);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type);
    }
}