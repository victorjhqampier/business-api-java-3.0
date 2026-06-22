package com.arify.domain.containers.memoryevents;

import com.arify.domain.entities.MicroserviceCallTraceEntity;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/* ********************************************************************************************************
 * Copyright © 2026 Arify Labs - All rights reserved.
 *
 * Info                  : In-memory queue for microservice call trace events (LinkedBlockingQueue).
 *
 * By                    : Victor Jhampier Caxi Maquera
 * Email/Mobile/Phone    : victorjhampier@gmail.com | 968991*14
 *
 * Creation date         : 22/06/2026 3:05h
 **********************************************************************************************************/


public class MicroserviceCallMemoryQueue {
    private final int capacity;
    private final LinkedBlockingQueue<MicroserviceCallTraceEntity> queue;
    private final AtomicBoolean completed;
    private final AtomicInteger length;

    public MicroserviceCallMemoryQueue() {
        this(1000);
    }

    public MicroserviceCallMemoryQueue(int capacity) {
        this.capacity = capacity;
        this.queue = new LinkedBlockingQueue<>(capacity);
        this.completed = new AtomicBoolean(false);
        this.length = new AtomicInteger(0);
    }

    public boolean pushAsync(MicroserviceCallTraceEntity item) {
        return pushAsync(item, 3.0);
    }

    public boolean pushAsync(MicroserviceCallTraceEntity item, Double timeout) {
        if (completed.get()) {
            return false;
        }

        try {
            boolean added = queue.offer(item, (long) (timeout * 1000), TimeUnit.MILLISECONDS);
            if (added) {
                length.incrementAndGet();
            }
            return added;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    public boolean tryPush(MicroserviceCallTraceEntity item) {
        if (completed.get()) {
            return false;
        }

        boolean added = queue.offer(item);
        if (added) {
            length.incrementAndGet();
        }
        return added;
    }

    public MicroserviceCallTraceEntity popAsync() {
        return popAsync(null);
    }

    public MicroserviceCallTraceEntity popAsync(Double timeout) {
        if (completed.get() && queue.isEmpty()) {
            return null;
        }

        try {
            MicroserviceCallTraceEntity item = timeout == null
                    ? queue.take()
                    : queue.poll((long) (timeout * 1000), TimeUnit.MILLISECONDS);
            if (item != null) {
                length.updateAndGet(current -> current > 0 ? current - 1 : 0);
            }
            return item;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return null;
        }
    }

    public MicroserviceCallTraceEntity tryPop() {
        MicroserviceCallTraceEntity item = queue.poll();
        if (item != null) {
            length.updateAndGet(current -> current > 0 ? current - 1 : 0);
        }
        return item;
    }

    public List<MicroserviceCallTraceEntity> readAllAsync() {
        return readAllAsync(10, null);
    }

    public List<MicroserviceCallTraceEntity> readAllAsync(int batchSize, Double timeout) {
        List<MicroserviceCallTraceEntity> items = new ArrayList<>();

        MicroserviceCallTraceEntity firstItem = popAsync(timeout);
        if (firstItem == null) {
            return items;
        }

        items.add(firstItem);

        for (int index = 0; index < batchSize - 1; index++) {
            MicroserviceCallTraceEntity item = tryPop();
            if (item == null) {
                break;
            }
            items.add(item);
        }

        return items;
    }

    public void complete() {
        completed.set(true);
    }

    public boolean isCompleted() {
        return completed.get();
    }

    public boolean isEmpty() {
        return queue.isEmpty();
    }

    public boolean isFull() {
        return queue.remainingCapacity() == 0;
    }

    public int approxLength() {
        return length.get();
    }

    public int capacity() {
        return capacity;
    }
}
