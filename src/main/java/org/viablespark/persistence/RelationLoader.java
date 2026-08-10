package org.viablespark.persistence;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
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
        attachment.accept(parent, new ArrayList<>());
        continue;
      }
      parentsByKey.computeIfAbsent(key, ignored -> new ArrayList<>()).add(parent);
    }

    if (parentsByKey.isEmpty()) {
      return;
    }

    Map<K, List<C>> childrenByParentKey = new LinkedHashMap<>();
    List<C> loadedChildren =
        Objects.requireNonNull(
            childLoader.apply(new ArrayList<>(parentsByKey.keySet())),
            "Child loader must not return null");
    for (C child : loadedChildren) {
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

  public static <P, C, K> void attachOneToOne(
      List<P> parents,
      Function<List<K>, List<C>> childLoader,
      Function<P, K> parentKeyExtractor,
      Function<C, K> childParentKeyExtractor,
      BiConsumer<P, C> attachment) {
    Objects.requireNonNull(parents, "Parents must not be null");
    Objects.requireNonNull(childLoader, "Child loader must not be null");
    Objects.requireNonNull(parentKeyExtractor, "Parent key extractor must not be null");
    Objects.requireNonNull(childParentKeyExtractor, "Child parent key extractor must not be null");
    Objects.requireNonNull(attachment, "Attachment callback must not be null");

    attachToOne(
        parents,
        childLoader,
        parentKeyExtractor,
        childParentKeyExtractor,
        attachment,
        true,
        "Child loader must not return null");
  }

  public static <P, C, K> void attachManyToOne(
      List<P> parents,
      Function<List<K>, List<C>> relatedLoader,
      Function<P, K> foreignKeyExtractor,
      Function<C, K> relatedKeyExtractor,
      BiConsumer<P, C> attachment) {
    Objects.requireNonNull(parents, "Parents must not be null");
    Objects.requireNonNull(relatedLoader, "Related loader must not be null");
    Objects.requireNonNull(foreignKeyExtractor, "Foreign key extractor must not be null");
    Objects.requireNonNull(relatedKeyExtractor, "Related key extractor must not be null");
    Objects.requireNonNull(attachment, "Attachment callback must not be null");

    attachToOne(
        parents,
        relatedLoader,
        foreignKeyExtractor,
        relatedKeyExtractor,
        attachment,
        false,
        "Related loader must not return null");
  }

  private static <P, C, K> void attachToOne(
      List<P> parents,
      Function<List<K>, List<C>> relatedLoader,
      Function<P, K> foreignKeyExtractor,
      Function<C, K> relatedKeyExtractor,
      BiConsumer<P, C> attachment,
      boolean rejectDuplicateKeys,
      String nullLoaderResultMessage) {
    if (parents.isEmpty()) {
      return;
    }

    Map<K, List<P>> parentsByKey = new LinkedHashMap<>();
    for (P parent : parents) {
      K key = foreignKeyExtractor.apply(parent);
      if (key == null) {
        attachment.accept(parent, null);
        continue;
      }
      parentsByKey.computeIfAbsent(key, ignored -> new ArrayList<>()).add(parent);
    }

    if (parentsByKey.isEmpty()) {
      return;
    }

    Map<K, C> relatedByKey = new LinkedHashMap<>();
    List<C> relatedRows =
        Objects.requireNonNull(
            relatedLoader.apply(new ArrayList<>(parentsByKey.keySet())), nullLoaderResultMessage);
    for (C related : relatedRows) {
      K key = relatedKeyExtractor.apply(related);
      if (key == null) {
        continue;
      }
      if (relatedByKey.containsKey(key)) {
        if (rejectDuplicateKeys) {
          throw new IllegalStateException(
              "One-to-one loader returned duplicate related rows for key: " + key);
        }
      } else {
        relatedByKey.put(key, related);
      }
    }

    for (Map.Entry<K, List<P>> entry : parentsByKey.entrySet()) {
      C related = relatedByKey.get(entry.getKey());
      for (P parent : entry.getValue()) {
        attachment.accept(parent, related);
      }
    }
  }

  public static <P, J, C, PK, CK> void attachManyToMany(
      List<P> parents,
      Function<List<PK>, List<J>> joinLoader,
      Function<List<CK>, List<C>> childLoader,
      Function<P, PK> parentKeyExtractor,
      Function<J, PK> joinParentKeyExtractor,
      Function<J, CK> joinChildKeyExtractor,
      Function<C, CK> childKeyExtractor,
      BiConsumer<P, List<C>> attachment) {
    Objects.requireNonNull(parents, "Parents must not be null");
    Objects.requireNonNull(joinLoader, "Join loader must not be null");
    Objects.requireNonNull(childLoader, "Child loader must not be null");
    Objects.requireNonNull(parentKeyExtractor, "Parent key extractor must not be null");
    Objects.requireNonNull(joinParentKeyExtractor, "Join parent key extractor must not be null");
    Objects.requireNonNull(joinChildKeyExtractor, "Join child key extractor must not be null");
    Objects.requireNonNull(childKeyExtractor, "Child key extractor must not be null");
    Objects.requireNonNull(attachment, "Attachment callback must not be null");

    if (parents.isEmpty()) {
      return;
    }

    Map<PK, List<P>> parentsByKey = new LinkedHashMap<>();
    for (P parent : parents) {
      PK key = parentKeyExtractor.apply(parent);
      if (key == null) {
        attachment.accept(parent, new ArrayList<>());
        continue;
      }
      parentsByKey.computeIfAbsent(key, ignored -> new ArrayList<>()).add(parent);
    }

    if (parentsByKey.isEmpty()) {
      return;
    }

    Map<PK, Set<CK>> childKeysByParent = new LinkedHashMap<>();
    Map<CK, Boolean> distinctChildKeys = new LinkedHashMap<>();
    List<J> joins =
        Objects.requireNonNull(
            joinLoader.apply(new ArrayList<>(parentsByKey.keySet())),
            "Join loader must not return null");
    for (J join : joins) {
      PK parentKey = joinParentKeyExtractor.apply(join);
      CK childKey = joinChildKeyExtractor.apply(join);
      if (parentKey == null || childKey == null) {
        continue;
      }
      childKeysByParent.computeIfAbsent(parentKey, ignored -> new LinkedHashSet<>()).add(childKey);
      distinctChildKeys.put(childKey, Boolean.TRUE);
    }

    if (distinctChildKeys.isEmpty()) {
      for (List<P> relatedParents : parentsByKey.values()) {
        for (P parent : relatedParents) {
          attachment.accept(parent, new ArrayList<>());
        }
      }
      return;
    }

    Map<CK, C> childrenByKey = new LinkedHashMap<>();
    List<C> loadedChildren =
        Objects.requireNonNull(
            childLoader.apply(new ArrayList<>(distinctChildKeys.keySet())),
            "Child loader must not return null");
    for (C child : loadedChildren) {
      CK childKey = childKeyExtractor.apply(child);
      if (childKey != null && !childrenByKey.containsKey(childKey)) {
        childrenByKey.put(childKey, child);
      }
    }

    for (Map.Entry<PK, List<P>> entry : parentsByKey.entrySet()) {
      List<C> children = new ArrayList<>();
      for (CK childKey : childKeysByParent.getOrDefault(entry.getKey(), Set.of())) {
        C child = childrenByKey.get(childKey);
        if (child != null) {
          children.add(child);
        }
      }
      for (P parent : entry.getValue()) {
        attachment.accept(parent, new ArrayList<>(children));
      }
    }
  }
}
