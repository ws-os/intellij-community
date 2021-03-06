/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.util.containers;


import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Conditions;
import com.intellij.openapi.util.Ref;
import com.intellij.util.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * An in-house version of {@code com.google.common.collect.FluentIterable} with some Clojure-like additions.
 * As a bonus iterator instance is preserved during most transformations if it inherits JBIterator.
 *
 * <p/>
 * The original JavaDoc ('FluentIterable' replaced by 'JBIterable'):
 * <p/>
 * {@code JBIterable} provides a rich interface for manipulating {@code Iterable} instances in a
 * chained fashion. A {@code JBIterable} can be created from an {@code Iterable}, or from a set
 * of elements. The following types of methods are provided on {@code JBIterable}:
 * <ul>
 * <li>chained methods which return a new {@code JBIterable} based in some way on the contents
 * of the current one (for example {@link #transform})
 * <li>conversion methods which copy the {@code JBIterable}'s contents into a new collection or
 * array (for example {@link #toList})
 * <li>element extraction methods which facilitate the retrieval of certain elements (for example
 * {@link #last})
 * </ul>
 * <p/>
 * <p>Here is an example that merges the lists returned by two separate database calls, transforms
 * it by invoking {@code toString()} on each element, and returns the first 10 elements as an
 * {@code List}: <pre>   {@code
 *   FluentIterable
 *       .from(database.getClientList())
 *       .filter(activeInLastMonth())
 *       .transform(Functions.toStringFunction())
 *       .toList();}</pre>
 * <p/>
 * <p>Anything which can be done using {@code JBIterable} could be done in a different fashion
 * (often with {@code Iterables}), however the use of {@code JBIterable} makes many sets of
 * operations significantly more concise.
 *
 * @author Marcin Mikosik
 *
 * @noinspection unchecked
 */
public abstract class JBIterable<E> implements Iterable<E> {

  // We store 'iterable' and use it instead of 'this' to allow Iterables to perform instanceof
  // checks on the _original_ iterable when JBIterable#from is used.
  final Iterable<E> myIterable;

  /**
   * Constructor for use by subclasses.
   */
  protected JBIterable() {
    myIterable = this;
  }

  JBIterable(@NotNull Iterable<E> iterable) {
    myIterable = iterable;
  }

  /**
   * Lambda-friendly construction method.
   */
  @NotNull
  public static <E> JBIterable<E> create(@Nullable final Producer<Iterator<E>> producer) {
    if (producer == null) return empty();
    return new JBIterable<E>() {
      @NotNull
      @Override
      public Iterator<E> iterator() {
        return producer.produce();
      }
    };
  }

  /**
   * Returns a {@code JBIterable} that wraps {@code iterable}, or {@code iterable} itself if it
   * is already a {@code JBIterable}.
   */
  @NotNull
  public static <E> JBIterable<E> from(@Nullable Iterable<? extends E> iterable) {
    if (iterable == null) return empty();
    if (iterable instanceof JBIterable) return (JBIterable<E>)iterable;
    if (iterable instanceof Collection && ((Collection)iterable).isEmpty()) return empty();
    return new JBIterable<E>((Iterable<E>)iterable) {
      @Override
      public Iterator<E> iterator() {
        return myIterable.iterator();
      }
    };
  }

  /**
   * Returns a {@code JBIterable} that is generated by {@code generator} function applied to a previous element,
   * the first element is produced by the supplied {@code first} value.
   *
   * Generation stops if {@code null} is encountered.
   */
  @NotNull
  public static <E> JBIterable<E> generate(@Nullable final E first, @NotNull final Function<? super E, ? extends E> generator) {
    if (first == null) return empty();
    return new JBIterable<E>() {
      @Override
      public Iterator<E> iterator() {
        final Function<? super E, ? extends E> fun = Stateful.copy(generator);
        return new JBIterator<E>() {
          E cur = first;

          @Override
          public E nextImpl() {
            E result = cur;
            if (result == null) return stop();
            cur = fun.fun(cur);
            return result;
          }
        };
      }
    };
  }

  @NotNull
  public static <E> JBIterable<E> generate(@Nullable final E first1, @Nullable final E first2, @NotNull final PairFunction<? super E, ? super E, ? extends E> generator) {
    if (first1 == null) return empty();
    return new JBIterable<E>() {
      @Override
      public Iterator<E> iterator() {
        return new JBIterator<E>() {
          E cur1 = first1;
          E cur2 = first2;

          @Override
          public E nextImpl() {
            E result = cur1;
            cur1 = cur2;
            cur2 = generator.fun(result, cur2);
            if (result == null) return stop();
            return result;
          }
        };
      }
    };
  }

  /**
   * Returns a {@code JBIterable} containing the one {@code element} if is not null.
   */
  @NotNull
  public static <E> JBIterable<E> of(@Nullable E element) {
    return element == null ? JBIterable.<E>empty() : from(Collections.singletonList(element));
  }

  /**
   * Returns a {@code JBIterable} containing {@code elements} in the specified order.
   */
  @NotNull
  public static <E> JBIterable<E> of(@Nullable E... elements) {
    return elements == null || elements.length == 0 ? JBIterable.<E>empty() : from(ContainerUtilRt.newArrayList(elements));
  }

  private static final JBIterable EMPTY = new JBIterable() {
    @Override
    public Iterator iterator() {
      return EmptyIterator.getInstance();
    }
  };

  @NotNull
  public static <E> JBIterable<E> empty() {
    return (JBIterable<E>)EMPTY;
  }

  @NotNull
  public static <E> JBIterable<E> once(@NotNull Iterator<E> iterator) {
    return of(Ref.create(iterator)).intercept(new Function<Iterator<Ref<Iterator<E>>>, Iterator<E>>() {
      @Override
      public Iterator<E> fun(Iterator<Ref<Iterator<E>>> iterator) {
        Ref<Iterator<E>> ref = iterator.next();
        Iterator<E> result = ref.get();
        if (result == null) throw new UnsupportedOperationException();
        ref.set(null);
        return result;
      }
    });
  }

  /**
   * Returns iterator, useful for graph traversal.
   *
   * @see TreeTraversal.TracingIt
   */
  @NotNull
  public <T extends Iterator<E>> T typedIterator() {
    return (T) iterator();
  }

  public final boolean processEach(@NotNull Processor<E> processor) {
    return ContainerUtil.process(this, processor);
  }

  public final void consumeEach(@NotNull Consumer<E> consumer) {
    for (E e : this) {
      consumer.consume(e);
    }
  }

  /**
   * Returns a string representation of this iterable for debugging purposes.
   */
  @NotNull
  @Override
  public String toString() {
    return myIterable == this ? JBIterable.class.getSimpleName() : String.valueOf(myIterable);
  }

  /**
   * Returns the number of elements in this iterable.
   */
  public final int size() {
    if (myIterable instanceof Collection) {
      return ((Collection)myIterable).size();
    }
    int count = 0;
    for (E ignored : myIterable) {
      count++;
    }
    return count;
  }

  /**
   * Returns {@code true} if this iterable contains any object for which
   * {@code equals(element)} is true.
   */
  public final boolean contains(@Nullable Object element) {
    if (myIterable instanceof Collection) {
      return ((Collection)myIterable).contains(element);
    }
    for (E e : myIterable) {
      if (Comparing.equal(e, element)) return true;
    }
    return false;
  }

  /**
   * Returns element at index if it is present; otherwise {@code null}
   */
  @Nullable
  public final E get(int index) {
    List<E> list = asRandomAccess(myIterable);
    if (list != null) {
      return index >= list.size() ? null : list.get(index);
    }
    return skip(index).first();
  }

  @Nullable
  private static <E> List<E> asRandomAccess(@Nullable Iterable<E> iterable) {
    //noinspection CastConflictsWithInstanceof
    return iterable instanceof RandomAccess? (List<E>)iterable : null;
  }

  /**
   * Returns a {@code JBIterable} which iterators traverse first the elements of this iterable,
   * followed by those of {@code other}. The iterators are not polled until necessary.
   * <p/>
   * <p>The returned iterable's {@code Iterator} supports {@code remove()} when the corresponding
   * {@code Iterator} supports it.
   */
  @NotNull
  public final JBIterable<E> append(@Nullable Iterable<? extends E> other) {
    return other == null ? this : this == EMPTY ? from(other) : of(myIterable, other).flatten(Functions.<Iterable<?>, Iterable<E>>identity());
  }

  @NotNull
  public final <T> JBIterable<E> append(@Nullable Iterable<T> other, @NotNull Function<? super T, ? extends Iterable<? extends E>> fun) {
    return other == null ? this : this == EMPTY ? from(other).flatten(fun) : append(from(other).flatten(fun));
  }

  @NotNull
  public final JBIterable<E> repeat(int count) {
    Function<JBIterable<E>, JBIterable<E>> fun = Functions.identity();
    return generate(this, fun).take(count).flatten(fun);
  }

  /**
   * Returns a {@code JBIterable} which iterators traverse first the elements of this iterable,
   * followed by the {@code elements}.
   */
  @NotNull
  public final JBIterable<E> append(@NotNull E[] elements) {
    return this == EMPTY ? of(elements) : append(Arrays.asList(elements));
  }

  /**
   * Returns a {@code JBIterable} which iterators traverse first the elements of this iterable,
   * followed by {@code element} if it is not null.
   */
  @NotNull
  public final JBIterable<E> append(@Nullable E element) {
    return element == null ? this : this == EMPTY ? of(element) : append(Collections.singleton(element));
  }

  /**
   * Returns the elements from this iterable that satisfy a condition.
   */
  @NotNull
  public final JBIterable<E> filter(@NotNull final Condition<? super E> condition) {
    return intercept(new Function<Iterator<E>, Iterator<E>>() {
      @Override
      public Iterator<E> fun(Iterator<E> iterator) {
        return JBIterator.from(iterator).filter(Stateful.copy(condition));
      }
    });
  }

  /**
   * Returns the elements from this iterable that are instances of class {@code type}.
   * @param type the type of elements desired
   */
  @NotNull
  public final <T> JBIterable<T> filter(@NotNull Class<T> type) {
    return (JBIterable<T>)filter(Conditions.instanceOf(type));
  }

  @NotNull
  public final JBIterable<E> take(final int count) {
    return intercept(new Function<Iterator<E>, Iterator<E>>() {
      @Override
      public Iterator<E> fun(Iterator<E> iterator) {
        return JBIterator.from(iterator).take(count);
      }
    });
  }

  @NotNull
  public final JBIterable<E> takeWhile(@NotNull final Condition<? super E> condition) {
    return intercept(new Function<Iterator<E>, Iterator<E>>() {
      @Override
      public Iterator<E> fun(Iterator<E> iterator) {
        return JBIterator.from(iterator).takeWhile(Stateful.copy(condition));
      }
    });
  }

  @NotNull
  public final JBIterable<E> skip(final int count) {
    return intercept(new Function<Iterator<E>, Iterator<E>>() {
      @Override
      public Iterator<E> fun(Iterator<E> iterator) {
        return JBIterator.from(iterator).skip(count);
      }
    });
  }

  @NotNull
  public final JBIterable<E> skipWhile(@NotNull final Condition<? super E> condition) {
    return intercept(new Function<Iterator<E>, Iterator<E>>() {
      @Override
      public Iterator<E> fun(Iterator<E> iterator) {
        return JBIterator.from(iterator).skipWhile(Stateful.copy(condition));
      }
    });
  }

  /**
   * Returns a {@code JBIterable} that applies {@code function} to each element of this
   * iterable.
   * <p/>
   * <p>The returned iterable's iterator supports {@code remove()} if this iterable's
   * iterator does. After a successful {@code remove()} call, this iterable no longer
   * contains the corresponding element.
   */
  @NotNull
  public final <T> JBIterable<T> transform(@NotNull final Function<? super E, T> function) {
    return intercept(new Function<Iterator<E>, Iterator<T>>() {
      @Override
      public Iterator<T> fun(Iterator<E> iterator) {
        return JBIterator.from(iterator).transform(Stateful.copy(function));
      }
    });
  }

  /**
   * Returns a {@code JBIterable} that applies {@code function} to each element of this
   * iterable and concatenates the produced iterables in one.
   * Nulls are supported and silently skipped.
   */
  @NotNull
  public <T> JBIterable<T> flatten(@NotNull final Function<? super E, ? extends Iterable<? extends T>> function) {
    return intercept(new Function<Iterator<E>, Iterator<T>>() {
      @Override
      public Iterator<T> fun(final Iterator<E> iterator) {
        final Function<? super E, ? extends Iterable<? extends T>> fun = Stateful.copy(function);
        return new JBIterator<T>() {
          Iterator<? extends T> cur;

          @Override
          public T nextImpl() {
            if (cur != null && cur.hasNext()) return cur.next();
            if (!iterator.hasNext()) return stop();
            Iterable<? extends T> next = fun.fun(iterator.next());
            cur = next == null ? null : next.iterator();
            return skip();
          }
        };
      }
    });
  }

  /**
   * Filters out duplicate items.
   */
  @NotNull
  public final JBIterable<E> unique() {
    return unique(Function.ID);
  }

  /**
   * Filters out duplicate items, where an element identity is provided by the specified function.
   */
  @NotNull
  public final JBIterable<E> unique(@NotNull final Function<? super E, ?> identity) {
    return filter(new SCond<E>() {
      HashSet<Object> visited;

      @Override
      public boolean value(E e) {
        if (visited == null) visited = new HashSet<Object>();
        return visited.add(identity.fun(e));
      }
    });
  }

  /**
   * The most generic iterator transformation.
   */
  @NotNull
  public final <T, X extends Iterator<E>> JBIterable<T> intercept(@NotNull final Function<X, ? extends Iterator<T>> function) {
    if (this == EMPTY) return empty();
    final JBIterable<E> thisIterable = this;
    return new JBIterable<T>() {
      @Override
      public Iterator<T> iterator() {
        return function.fun((X)thisIterable.iterator());
      }
    };
  }

  /**
   * Returns the first element in this iterable or null.
   */
  @Nullable
  public final E first() {
    List<E> list = asRandomAccess(myIterable);
    if (list != null) {
      return list.isEmpty() ? null : list.get(0);
    }
    Iterator<E> iterator = myIterable.iterator();
    return iterator.hasNext() ? iterator.next() : null;
  }

  /**
   * Returns the first element in this iterable if it is the only one, otherwise null.
   */
  @Nullable
  public final E single() {
    List<E> list = asRandomAccess(myIterable);
    if (list != null) {
      return list.size() != 1 ? null : list.get(0);
    }
    Iterator<E> iterator = myIterable.iterator();
    E first = iterator.hasNext() ? iterator.next() : null;
    return iterator.hasNext() ? null : first;
  }

  /**
   * Returns the last element in this iterable or null.
   */
  @Nullable
  public final E last() {
    List<E> list = asRandomAccess(myIterable);
    if (list != null) {
      return list.isEmpty() ? null : list.get(list.size() - 1);
    }
    E cur = null;
    for (E e : myIterable) {
      cur = e;
    }
    return cur;
  }

  /**
   * Perform calculation over this iterable.
   */
  public final <T> T reduce(@Nullable T first, @NotNull PairFunction<T, ? super E, T> function) {
    T cur = first;
    for (E e : this) {
      cur = function.fun(cur, e);
    }
    return cur;
  }

  /**
   * Returns the index of the first matching element.
   */
  public final E find(@NotNull Condition<? super E> condition) {
    return filter(condition).first();
  }

  /**
   * Returns the index of the matching element.
   */
  public final int indexOf(@NotNull Condition<? super E> condition) {
    int index = 0;
    for (E e : this) {
      if (condition.value(e)) {
        return index;
      }
      index ++;
    }
    return -1;
  }

  /**
   * Synonym for transform().
   *
   * @see JBIterable#transform(Function)
   */
  @NotNull
  public final <T> JBIterable<T> map(@NotNull Function<? super E, T> function) {
    return transform(function);
  }

  /**
   * Synonym for map(..).filter(notNull()).
   *
   * @see JBIterable#map(Function)
   * @see JBIterable#filter(Condition)
   */
  @NotNull
  public final <T> JBIterable<T> filterMap(@NotNull Function<? super E, T> function) {
    return map(function).filter(Conditions.<T>notNull());
  }

  /**
   * "Maps" and "flattens" this iterable.
   *
   * @see JBIterable#map(Function)
   * @see JBIterable#flatten(Function)
   */
  @NotNull
  public final <T> JBIterable<T> flatMap(Function<? super E, ? extends Iterable<? extends T>> function) {
    return map(function).flatten(Function.ID);
  }

  /**
   * Returns the iterable which elements are interleaved with the separator.
   */
  @NotNull
  public final JBIterable<E> join(@Nullable final E separator) {
    return intercept(new Function<Iterator<E>, Iterator<E>>() {
      @Override
      public Iterator<E> fun(Iterator<E> iterator) {
        final Iterator<E> original = iterator;
        return new JBIterator<E>() {
          boolean flag;
          @Override
          protected E nextImpl() {
            if (!original.hasNext()) return stop();
            return (flag = !flag) ? original.next() : separator;
          }
        };
      }
    });
  }


  /**
   * Splits this {@code JBIterable} into iterable of lists of the specified size.
   * If 'strict' flag is true only groups of size 'n' are returned.
   */
  @NotNull
  public final JBIterable<List<E>> split(final int size, final boolean strict) {
    return split(size).filterMap(new Function<JBIterable<E>, List<E>>() {
      @Override
      public List<E> fun(JBIterable<E> es) {
        List<E> list = es.addAllTo(ContainerUtilRt.<E>newArrayListWithCapacity(size));
        return strict && list.size() < size ? null : list;
      }
    });
  }

  /**
   * Splits this {@code JBIterable} into iterable of iterables of the specified size.
   * All iterations are performed in-place without data copying.
   */
  @NotNull
  public final JBIterable<JBIterable<E>> split(final int size) {
    if (size <= 0) throw new IllegalArgumentException(size + " <= 0");
    return intercept(new Function<Iterator<E>, Iterator<JBIterable<E>>>() {
      @Override
      public Iterator<JBIterable<E>> fun(Iterator<E> iterator) {
        final Iterator<E> orig = iterator;
        return new JBIterator<JBIterable<E>>() {
          JBIterator<E> it;

          @Override
          protected JBIterable<E> nextImpl() {
            // iterate through the previous result fully before proceeding
            while (it != null && it.advance()) /* no-op */;
            it = null;
            return orig.hasNext() ? once((it = JBIterator.wrap(orig)).take(size)) : stop();
          }
        };
      }
    });
  }

  public enum Split {AFTER, BEFORE, AROUND, OFF, GROUP}

  /**
   * Splits this {@code JBIterable} into iterable of iterables with separators matched by the specified condition.
   * All iterations are performed in-place without data copying.
   */
  @NotNull
  public final JBIterable<JBIterable<E>> split(final Split mode, final Condition<? super E> separator) {
    return intercept(new Function<Iterator<E>, Iterator<JBIterable<E>>>() {
      @Override
      public Iterator<JBIterable<E>> fun(Iterator<E> iterator) {
        final Iterator<E> orig = iterator;
        final Condition<? super E> condition = Stateful.copy(separator);
        return new JBIterator<JBIterable<E>>() {
          JBIterator<E> it;
          E stored;
          int st; // encode transitions: -2:sep->sep, -1:val->sep, 1:sep->val, 2:val->val

          @Override
          protected JBIterable<E> nextImpl() {
            // iterate through the previous result fully before proceeding
            while (it != null && it.advance()) /* no-op */;
            it = null;
            // empty case: check hasNext() only if nothing is stored to be compatible with JBIterator#cursor()
            if (stored == null && !orig.hasNext()) {
              if (st < 0 && mode != Split.BEFORE && mode != Split.GROUP) { st = 1; return empty(); }
              return stop();
            }
            // general case: add empty between 2 separators in KEEP mode; otherwise go with some state logic
            if (st == -2 && mode == Split.AROUND) { st = -1; return empty(); }
            E tmp = stored;
            stored = null;
            return of(tmp).append(once((it = JBIterator.wrap(orig)).takeWhile(new Condition<E>() {
              @Override
              public boolean value(E e) {
                boolean sep = condition.value(e);
                int st0 = st;
                st = st0 < 0 && sep ? -2 : st0 > 0 && !sep? 2 : sep ? -1 : 1;
                boolean result;
                switch (mode) {
                  case AFTER:  result = st != -2 && (st != 1 || st0 == 0); break;
                  case BEFORE: result = st != -2 && st != -1; break;
                  case AROUND: result = st0 >= 0 && st > 0; break;
                  case GROUP:  result = st0 >= 0 && st > 0 || st0 <= 0 && st < 0; break;
                  case OFF:    result = st > 0; break;
                  default: throw new AssertionError(st);
                }
                stored = !result && mode != Split.OFF ? e : null;
                return result;
              }
            })));
          }
        };
      }
    });
  }

  /**
   * Determines whether this iterable is empty.
   */
  public final boolean isEmpty() {
    if (myIterable instanceof Collection) {
      return ((Collection)myIterable).isEmpty();
    }
    return !myIterable.iterator().hasNext();
  }

  /**
   * Determines whether this iterable is not empty.
   */
  public final boolean isNotEmpty() {
    return !isEmpty();
  }

  /**
   * Collects all items into the specified collection and returns it wrapped in a new {@code JBIterable}.
   * This is equivalent to calling {@code JBIterable.from(addAllTo(c))}.
   */
  @NotNull
  public final JBIterable<E> collect(@NotNull Collection<E> collection) {
    return from(addAllTo(collection));
  }

  /**
   * Collects all items into an {@link ArrayList} and returns them as the new {@code JBIterable}.
   * @see JBIterable#collect(Collection)
   */
  @NotNull
  public final JBIterable<E> collect() {
    if (myIterable instanceof ArrayList) return this;
    return collect(ContainerUtilRt.<E>newArrayList());
  }

  /**
   * Returns an {@code List} containing all of the elements from this iterable in
   * proper sequence.
   */
  @NotNull
  public final List<E> toList() {
    return Collections.unmodifiableList(ContainerUtilRt.newArrayList(myIterable));
  }

  /**
   * Returns an {@code Set} containing all of the elements from this iterable with
   * duplicates removed.
   */
  @NotNull
  public final Set<E> toSet() {
    return Collections.unmodifiableSet(ContainerUtilRt.newLinkedHashSet(myIterable));
  }

  /**
   * Returns an {@code Map} for which the elements of this {@code JBIterable} are the keys in
   * the same order, mapped to values by the given function. If this iterable contains duplicate
   * elements, the returned map will contain each distinct element once in the order it first
   * appears.
   */
  @NotNull
  public final <V> Map<E, V> toMap(Convertor<E, V> toValue) {
    Map<E, V> map = ContainerUtil.newLinkedHashMap();
    for (E e : this) map.put(e, toValue.convert(e));
    return map.isEmpty() ? Collections.<E, V>emptyMap() : Collections.unmodifiableMap(map);
  }

  /**
   * Returns an {@code Map} for which the elements of this {@code JBIterable} are the values in
   * the same order, mapped to values by the given function. If this iterable contains duplicate
   * elements, the returned map will contain each distinct element once in the order it first
   * appears.
   */
  @NotNull
  public final <K> Map<K, E> toReverseMap(Convertor<E, K> toKey) {
    Map<K, E> map = ContainerUtil.newLinkedHashMap();
    for (E e : this) map.put(toKey.convert(e), e);
    return map.isEmpty() ? Collections.<K, E>emptyMap() : Collections.unmodifiableMap(map);
  }

  /**
   * Copies all the elements from this iterable to {@code collection}. This is equivalent to
   * calling {@code Iterables.addAll(collection, this)}.
   *
   * @param collection the collection to copy elements to
   * @return {@code collection}, for convenience
   */
  @NotNull
  public final <C extends Collection<? super E>> C addAllTo(@NotNull C collection) {
    if (myIterable instanceof Collection) {
      collection.addAll((Collection<E>)myIterable);
    }
    else {
      for (E item : myIterable) {
        collection.add(item);
      }
    }
    return collection;
  }

  public abstract static class Stateful<Self extends Stateful> implements Cloneable {

    @NotNull
    static <T> T copy(@NotNull T o) {
      if (!(o instanceof Stateful)) return o;
      return (T)((Stateful)o).clone();
    }

    public Self clone() {
      try {
        return (Self)super.clone();
      }
      catch (CloneNotSupportedException e) {
        throw new AssertionError(e);
      }
    }
  }

  /**
   * Stateful {@link Conditions}: a separate cloned instance is used for each iterator.
   */
  public abstract static class SCond<T> extends Stateful<SCond> implements Condition<T> { }

  /**
   * Stateful {@link Function}: a separate cloned instance is used for each iterator.
   */
  public abstract static class SFun<S, T> extends Stateful<SFun> implements Function<S, T> { }

}
