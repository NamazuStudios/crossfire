package dev.getelements.elements.crossfire.service;

import java.util.*;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

/**
 * A List wrapper that enforces a maximum size. Attempts to add elements that would exceed the maximum size
 * will result in an exception being thrown.
 *
 * @param <E> the type of elements in the list
 */
public final class BoundedList<E> implements List<E> {

    private final int maxSize;
    private final List<E> delegate;
    private final Supplier<? extends RuntimeException> exceptionSupplier;
    private final IntSupplier sizeSupplier; // size of the logical root

    // Entry point for sublists or custom size checks
    private BoundedList(int maxSize,
                        List<E> delegate,
                        Supplier<? extends RuntimeException> exceptionSupplier,
                        IntSupplier sizeSupplier) {
        if (maxSize < 0) throw new IllegalArgumentException("maxSize must be >= 0");
        this.maxSize = maxSize;
        this.delegate = Objects.requireNonNull(delegate, "delegate list cannot be null");
        this.exceptionSupplier = Objects.requireNonNull(exceptionSupplier, "exceptionSupplier cannot be null");
        this.sizeSupplier = sizeSupplier == null ? this::size : sizeSupplier;
        if (delegate.size() > maxSize) {
            throw new IllegalArgumentException(
                    "Backing list size " + delegate.size() + " exceeds maxSize " + maxSize
            );
        }
    }

    public int maxSize() { return maxSize; }

    private void checkRoom(int toAdd) {
        if (toAdd < 0) throw new IllegalArgumentException("toAdd must be >= 0");
        int rootSize = sizeSupplier.getAsInt();
        if (rootSize > maxSize - toAdd) {
            throw exceptionSupplier.get();
        }
    }

    @Override public boolean add(E e) { checkRoom(1); return delegate.add(e); }
    @Override public void add(int index, E element) { checkRoom(1); delegate.add(index, element); }
    @Override public boolean addAll(Collection<? extends E> c) {
        Objects.requireNonNull(c, "collection cannot be null");
        checkRoom(c.size());
        return delegate.addAll(c);
    }
    @Override public boolean addAll(int index, Collection<? extends E> c) {
        Objects.requireNonNull(c, "collection cannot be null");
        checkRoom(c.size());
        return delegate.addAll(index, c);
    }

    // Delegations
    @Override public int size() { return delegate.size(); }
    @Override public boolean isEmpty() { return delegate.isEmpty(); }
    @Override public boolean contains(Object o) { return delegate.contains(o); }
    @Override public Iterator<E> iterator() { return delegate.iterator(); }
    @Override public Object[] toArray() { return delegate.toArray(); }
    @Override public <T> T[] toArray(T[] a) { return delegate.toArray(a); }
    @Override public boolean remove(Object o) { return delegate.remove(o); }
    @Override public E remove(int index) { return delegate.remove(index); }
    @Override public boolean removeAll(Collection<?> c) { return delegate.removeAll(c); }
    @Override public boolean retainAll(Collection<?> c) { return delegate.retainAll(c); }
    @Override public void clear() { delegate.clear(); }
    @Override public E get(int index) { return delegate.get(index); }
    @Override public E set(int index, E element) { return delegate.set(index, element); }
    @Override public int indexOf(Object o) { return delegate.indexOf(o); }
    @Override public int lastIndexOf(Object o) { return delegate.lastIndexOf(o); }
    @Override public ListIterator<E> listIterator() { return delegate.listIterator(); }
    @Override public ListIterator<E> listIterator(int index) { return delegate.listIterator(index); }
    @Override public boolean containsAll(Collection<?> c) { return delegate.containsAll(c); }
    @Override public boolean equals(Object o) { return delegate.equals(o); }
    @Override public int hashCode() { return delegate.hashCode(); }

    // Bounded, editable subList that shares the root size for checks
    @Override public List<E> subList(int fromIndex, int toIndex) {
        List<E> view = delegate.subList(fromIndex, toIndex);
        return new BoundedList<>(maxSize, view, exceptionSupplier, this.sizeSupplier);
    }

    @Override public String toString() { return delegate.toString(); }

    /**
     * Builder for BoundedList
     * @param <E> the type of elements in the list
     */
    public static class Builder<E> {

        private int maxSize = Integer.MAX_VALUE;

        private IntSupplier sizeSupplier;

        private Supplier<List<E>> listSupplier = ArrayList::new;

        private Supplier<? extends RuntimeException> exceptionSupplier = () -> new IllegalStateException("Exceeded max size: " + maxSize);

        public Builder<E> maxSize(final int maxSize) {
            if (maxSize < 0) throw new IllegalArgumentException("maxSize must be >= 0");
            this.maxSize = maxSize;
            return this;
        }

        public Builder<E> sizeSupplier(final IntSupplier listSupplier) {
            this.sizeSupplier = Objects.requireNonNull(listSupplier, "sizeSupplier cannot be null");
            return this;
        }

        public Builder<E> backingListSupplier(final Supplier<List<E>> supplier) {
            this.listSupplier = Objects.requireNonNull(supplier, "listSupplier cannot be null");
            return this;
        }

        public Builder<E> exceptionSupplier(final Supplier<? extends RuntimeException> supplier) {
            this.exceptionSupplier = Objects.requireNonNull(supplier, "exceptionSupplier cannot be null");
            return this;
        }

        public BoundedList<E> build() {
            final var list = Objects.requireNonNull(listSupplier.get(), "listSupplier returned null");
            return new BoundedList<>(maxSize, list, exceptionSupplier, sizeSupplier);
        }

    }

    public static <E> Builder<E> builder() { return new Builder<>(); }

}
