// Copyright (C) GridGain Systems Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */
package org.gridgain.grid.lang.utils;

import java.util.concurrent.atomic.*;

/**
 * This class provides atomic pair of integer variables.
 *
 * @author 2012 Copyright (C) GridGain Systems
 * @version 3.6.0c.13012012
 */
public class GridAtomicIntegerPair {
    /** Long placeholder. */
    private AtomicLong placeholder;

    /**
     * Creates an atomic pair with 0 as initial values.
     */
    public GridAtomicIntegerPair() {
        placeholder = new AtomicLong();
    }

    /**
     * Creates an atomic pair with given initial values.
     *
     * @param first First integer in a pair.
     * @param second Second integer in a pair.
     */
    public GridAtomicIntegerPair(int first, int second) {
        placeholder = new AtomicLong(value(first, second));
    }

    /**
     * Atomically sets the value to the given updated values if the current values == the expected values.
     *
     * @param firstOld Expected value for first int in pair.
     * @param secondOld Expected value for second int in pair.
     * @param firstUpdate Updated value for first int in pair.
     * @param secondUpdate Updated value for second int in pair.
     * @return {@code True} if update was successful,{@code false} otherwise.
     */
    public boolean compareAndSet(int firstOld, int secondOld, int firstUpdate, int secondUpdate) {
        return placeholder.compareAndSet(value(firstOld, secondOld), value(firstUpdate, secondUpdate));
    }

    /**
     * Atomically sets the value of first int in pair to the given updated value
     * if the current value == the expected value.
     *
     * @param old Expected value.
     * @param update New value.
     * @return {@code True} if update was successful,{@code false} otherwise.
     */
    public boolean compareAndSetFirst(int old, int update) {
        while (true) {
            long oldLong = placeholder.get();

            int prev = first(oldLong);

            if (prev != old)
                return false;

            long newLong = value(update, second(oldLong));

            if (placeholder.compareAndSet(oldLong, newLong))
                return true;
        }
    }

    /**
     * Atomically sets the value of second int in pair to the given updated value
     * if the current value == the expected value.
     *
     * @param old Expected value.
     * @param update New value.
     * @return {@code True} if update was successful,{@code false} otherwise.
     */
    public boolean compareAndSetSecond(int old, int update) {
        while (true) {
            long oldLong = placeholder.get();

            int prev = second(oldLong);

            if (prev != old)
                return false;

            long newLong = value(first(oldLong), update);

            if (placeholder.compareAndSet(oldLong, newLong))
                return true;
        }
    }

    /**
     * Atomically increments value of first int in pair and returns previous value.
     *
     * @return Value of first int before successful update.
     */
    public int getAndIncrementFirst() {
        while (true) {
            int old = first();

            if (compareAndSetFirst(old, old + 1))
                return old;
        }
    }

    /**
     * Atomically increments value of second int in pair and returns previous value.
     *
     * @return Value of second int before successful update.
     */
    public int getAndIncrementSecond() {
        while (true) {
            int old = second();

            if (compareAndSetSecond(old, old + 1))
                return old;
        }
    }

    /**
     * Atomically decrements value of first int in pair and returns previous value.
     *
     * @return Value of first int before successful update.
     */
    public int getAndDecrementFirst() {
        while (true) {
            int old = first();

            if (compareAndSetFirst(old, old - 1))
                return old;
        }
    }

    /**
     * Atomically decrements value of second int in pair and returns previous value.
     *
     * @return Value of second int before successful update.
     */
    public int getAndDecrementSecond() {
        while (true) {
            int old = second();

            if (compareAndSetSecond(old, old - 1))
                return old;
        }
    }

        /**
     * Atomically increments value of first int in pair and returns new value.
     *
     * @return Value of first int after successful update.
     */
    public int incrementAndGetFirst() {
        while (true) {
            int old = first();

            int updated = old + 1;

            if (compareAndSetFirst(old, updated))
                return updated;
        }
    }

    /**
     * Atomically increments value of second int in pair and returns new value.
     *
     * @return Value of second int after successful update.
     */
    public int incrementAndGetSecond() {
        while (true) {
            int old = second();

            int updated = old + 1;

            if (compareAndSetSecond(old, updated))
                return updated;
        }
    }

    /**
     * Atomically decrements value of first int in pair and returns new value.
     *
     * @return Value of first int after successful update.
     */
    public int decrementAndGetFirst() {
        while (true) {
            int old = first();

            int updated = old - 1;

            if (compareAndSetFirst(old, updated))
                return updated;
        }
    }

    /**
     * Atomically decrements value of second int in pair and returns new value.
     *
     * @return Value of second int after successful update.
     */
    public int decrementAndGetSecond() {
        while (true) {
            int old = second();

            int updated = old - 1;

            if (compareAndSetSecond(old, updated))
                return updated;
        }
    }

    /**
     * Unconditionally sets values.
     *
     * @param first Value of first int.
     * @param second Value of second int.
     */
    public void set(int first, int second) {
        placeholder.set(value(first, second));
    }

    /**
     * Unconditionally sets first int in pair to the given value.
     *
     * @param val New value.
     */
    public void first(int val) {
        while (true) {
            long old = placeholder.get();

            if (placeholder.compareAndSet(old, value(val, second(old))))
                return;
        }
    }

    /**
     * Unconditionally sets second int in pair to the given value.
     *
     * @param val New value.
     */
    public void second(int val) {
         while (true) {
            long old = placeholder.get();

            if (placeholder.compareAndSet(old, value(first(old), val)))
                return;
        }
    }

    /**
     * @return Value of first int.
     */
    public int first() {
        return first(placeholder.get());
    }

    /**
     * @return Value of second int.
     */
    public int second() {
        return second(placeholder.get());
    }

    /**
     * Gets value of both components and stores them into the given array. Parameter must have
     * length at least 2 elements.
     *
     * @param res Array where values should be stored.
     * @throws IllegalArgumentException If length of {@code res} is less then 2.
     */
    public void get(int[] res) {
        if (res.length < 2)
            throw new IllegalArgumentException("Result array must have at least 2 elements.");

        long val = placeholder.get();

        res[0] = first(val);
        res[1] = second(val);
    }

    /**
     * Combines a pair of integers to a single long.
     *
     * @param first First integer in a pair.
     * @param second Second integer in a pair.
     * @return Combined long.
     */
    long value(int first, int second) {
        return (((long)first) << 32) | (second & 0xFFFFFFFFL);
    }

    /**
     * @param val Long representation of pair.
     * @return First component,
     */
    int first(long val) {
        return (int)(val >>> 32);
    }

    /**
     * @param val Long representation of pair.
     * @return Second component.
     */
    int second(long val) {
        return (int)val;
    }
}
