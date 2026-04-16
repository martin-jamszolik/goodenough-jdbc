package org.viablespark.persistence;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Function;

/** Explicit helper for batching one-to-many attachment without introducing implicit loading. */
public final class RelationLoader {

  private RelationLoader() {}

  public static <P, C, K> void attachOneToMany(
      List<P> parents,
      Function<List<K>, List<C>> childLoader,
      Function<P, K> parentKeyExtractor,
      Function<C, K> childParentKeyExtractor,
      BiConsumer<P, List<C>> attachment) {
    Objects.requireNonNull(parents, "Parents must not be null");
    Objects.requireNonNull(childLoader, "Child loader must not be null");
    Objects.requireNonNull(parentKeyExtractor, "Parent key extractor must not be null");
    Objects.requireNonNull(childParentKeyExtractor, "Child parent key extractor must not be null");
    Objects.requireNonNull(attachment, "Attachment callback must not be null");

    if (parents.isEmpty()) {
      return;
    }

    Map<K, List<P>> parentsByKey = new LinkedHashMap<>();
    for (P parent : parents) {
      K key = parentKeyExtractor.apply(parent);
      if (key == null) {
        attachment.accept(parent, List.of());
        continue;
      }
      parentsByKey.computeIfAbsent(key, ignored -> new ArrayList<>()).add(parent);
    }

    if (parentsByKey.isEmpty()) {
      return;
    }

    Map<K, List<C>> childrenByParentKey = new LinkedHashMap<>();
    for (C child : childLoader.apply(new ArrayList<>(parentsByKey.keySet()))) {
      K key = childParentKeyExtractor.apply(child);
      if (key != null) {
        childrenByParentKey.computeIfAbsent(key, ignored -> new ArrayList<>()).add(child);
      }
    }

    for (Map.Entry<K, List<P>> entry : parentsByKey.entrySet()) {
      List<C> children = childrenByParentKey.getOrDefault(entry.getKey(), List.of());
      for (P parent : entry.getValue()) {
        attachment.accept(parent, new ArrayList<>(children));
      }
    }
  }
}
