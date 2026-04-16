package org.viablespark.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

class RelationLoaderTest {

  @Test
  void attachesChildrenGroupedByParentKey() {
    List<TestParent> parents = List.of(new TestParent(1L), new TestParent(2L), new TestParent(1L));

    RelationLoader.attachOneToMany(
        parents,
        ids -> List.of(new TestChild(1L, "a"), new TestChild(1L, "b"), new TestChild(2L, "c")),
        TestParent::id,
        TestChild::parentId,
        TestParent::setChildren);

    assertEquals(
        List.of("a", "b"),
        parents.get(0).children().stream().map(TestChild::value).toList());
    assertEquals(List.of("c"), parents.get(1).children().stream().map(TestChild::value).toList());
    assertEquals(
        List.of("a", "b"),
        parents.get(2).children().stream().map(TestChild::value).toList());
  }

  @Test
  void handlesParentsWithoutKeysAndChildrenWithoutKeys() {
    List<TestParent> parents = List.of(new TestParent(null), new TestParent(5L));

    RelationLoader.attachOneToMany(
        parents,
        ids -> List.of(new TestChild(null, "ignored"), new TestChild(5L, "kept")),
        TestParent::id,
        TestChild::parentId,
        TestParent::setChildren);

    assertTrue(parents.get(0).children().isEmpty());
    assertEquals(
        List.of("kept"),
        parents.get(1).children().stream().map(TestChild::value).toList());
  }

  @Test
  void returnsEarlyForEmptyParents() {
    AtomicBoolean loaderCalled = new AtomicBoolean(false);

    RelationLoader.attachOneToMany(
        List.of(),
        ids -> {
          loaderCalled.set(true);
          return List.of();
        },
        TestParent::id,
        TestChild::parentId,
        TestParent::setChildren);

    assertFalse(loaderCalled.get());
  }

  @Test
  void returnsEarlyWhenAllParentsHaveNullKeys() {
    AtomicBoolean loaderCalled = new AtomicBoolean(false);
    List<TestParent> parents = List.of(new TestParent(null), new TestParent(null));

    RelationLoader.attachOneToMany(
        parents,
        ids -> {
          loaderCalled.set(true);
          return List.of(new TestChild(1L, "unused"));
        },
        TestParent::id,
        TestChild::parentId,
        TestParent::setChildren);

    assertFalse(loaderCalled.get());
    assertTrue(parents.stream().allMatch(parent -> parent.children().isEmpty()));
  }

  @Test
  void validatesRequiredArguments() {
    assertThrows(
        NullPointerException.class,
        () ->
            RelationLoader.attachOneToMany(
                null, ids -> List.of(), TestParent::id, TestChild::parentId, TestParent::setChildren));
    assertThrows(
        NullPointerException.class,
        () ->
            RelationLoader.attachOneToMany(
                List.of(), null, TestParent::id, TestChild::parentId, TestParent::setChildren));
    assertThrows(
        NullPointerException.class,
        () ->
            RelationLoader.attachOneToMany(
                List.of(), ids -> List.of(), null, TestChild::parentId, TestParent::setChildren));
    assertThrows(
        NullPointerException.class,
        () ->
            RelationLoader.attachOneToMany(
                List.of(), ids -> List.of(), TestParent::id, null, TestParent::setChildren));
    assertThrows(
        NullPointerException.class,
        () ->
            RelationLoader.attachOneToMany(
                List.of(), ids -> List.of(), TestParent::id, TestChild::parentId, null));
  }

  private record TestChild(Long parentId, String value) {}

  private static final class TestParent {
    private final Long id;
    private List<TestChild> children = new ArrayList<>();

    private TestParent(Long id) {
      this.id = id;
    }

    private Long id() {
      return id;
    }

    private List<TestChild> children() {
      return children;
    }

    private void setChildren(List<TestChild> children) {
      this.children = children;
    }
  }
}
