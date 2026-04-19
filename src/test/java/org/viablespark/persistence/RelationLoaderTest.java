package org.viablespark.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
        List.of("a", "b"), parents.get(0).children().stream().map(TestChild::value).toList());
    assertEquals(List.of("c"), parents.get(1).children().stream().map(TestChild::value).toList());
    assertEquals(
        List.of("a", "b"), parents.get(2).children().stream().map(TestChild::value).toList());
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
        List.of("kept"), parents.get(1).children().stream().map(TestChild::value).toList());
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
                null,
                ids -> List.of(),
                TestParent::id,
                TestChild::parentId,
                TestParent::setChildren));
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

  @Test
  void attachesManyToOneRelations() {
    List<TestManyToOneParent> parents =
        List.of(
            new TestManyToOneParent(1L), new TestManyToOneParent(2L), new TestManyToOneParent(1L));

    RelationLoader.attachManyToOne(
        parents,
        ids -> List.of(new TestOwner(1L, "A"), new TestOwner(2L, "B")),
        TestManyToOneParent::ownerId,
        TestOwner::id,
        TestManyToOneParent::setOwner);

    assertEquals("A", parents.get(0).owner().name());
    assertEquals("B", parents.get(1).owner().name());
    assertEquals("A", parents.get(2).owner().name());
  }

  @Test
  void attachesOneToOneRelations() {
    List<TestOneToOneParent> parents =
        List.of(new TestOneToOneParent(1L), new TestOneToOneParent(null));

    RelationLoader.attachOneToOne(
        parents,
        ids -> List.of(new TestDetail(1L, "detail")),
        TestOneToOneParent::id,
        TestDetail::parentId,
        TestOneToOneParent::setDetail);

    assertEquals("detail", parents.get(0).detail().value());
    assertEquals(null, parents.get(1).detail());
  }

  @Test
  void attachesManyToManyRelations() {
    List<TestGroup> groups = List.of(new TestGroup(1L), new TestGroup(2L), new TestGroup(null));

    RelationLoader.attachManyToMany(
        groups,
        ids ->
            List.of(
                new TestMembership(1L, 10L),
                new TestMembership(1L, 11L),
                new TestMembership(2L, 11L)),
        ids -> List.of(new TestTag(10L, "x"), new TestTag(11L, "y")),
        TestGroup::id,
        TestMembership::groupId,
        TestMembership::tagId,
        TestTag::id,
        TestGroup::setTags);

    assertEquals(List.of("x", "y"), groups.get(0).tags().stream().map(TestTag::value).toList());
    assertEquals(List.of("y"), groups.get(1).tags().stream().map(TestTag::value).toList());
    assertTrue(groups.get(2).tags().isEmpty());
  }

  private record TestChild(Long parentId, String value) {}

  private record TestOwner(Long id, String name) {}

  private record TestDetail(Long parentId, String value) {}

  private record TestMembership(Long groupId, Long tagId) {}

  private record TestTag(Long id, String value) {}

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

  private static final class TestManyToOneParent {
    private final Long ownerId;
    private TestOwner owner;

    private TestManyToOneParent(Long ownerId) {
      this.ownerId = ownerId;
    }

    private Long ownerId() {
      return ownerId;
    }

    private TestOwner owner() {
      return owner;
    }

    private void setOwner(TestOwner owner) {
      this.owner = owner;
    }
  }

  private static final class TestOneToOneParent {
    private final Long id;
    private TestDetail detail;

    private TestOneToOneParent(Long id) {
      this.id = id;
    }

    private Long id() {
      return id;
    }

    private TestDetail detail() {
      return detail;
    }

    private void setDetail(TestDetail detail) {
      this.detail = detail;
    }
  }

  private static final class TestGroup {
    private final Long id;
    private List<TestTag> tags = new ArrayList<>();

    private TestGroup(Long id) {
      this.id = id;
    }

    private Long id() {
      return id;
    }

    private List<TestTag> tags() {
      return tags;
    }

    private void setTags(List<TestTag> tags) {
      this.tags = tags;
    }
  }
}
