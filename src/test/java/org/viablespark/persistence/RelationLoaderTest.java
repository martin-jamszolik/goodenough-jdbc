package org.viablespark.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

class RelationLoaderTest {

  @Test
  void attachesChildrenGroupedByParentKey() {
    List<TestParent> parents = List.of(new TestParent(1L), new TestParent(2L), new TestParent(1L));

    RelationLoader.attachOneToMany(
        parents,
        ids -> {
          assertEquals(List.of(1L, 2L), ids);
          return List.of(new TestChild(1L, "a"), new TestChild(1L, "b"), new TestChild(2L, "c"));
        },
        TestParent::id,
        TestChild::parentId,
        TestParent::setChildren);

    assertEquals(
        List.of("a", "b"), parents.get(0).children().stream().map(TestChild::value).toList());
    assertEquals(List.of("c"), parents.get(1).children().stream().map(TestChild::value).toList());
    assertEquals(
        List.of("a", "b"), parents.get(2).children().stream().map(TestChild::value).toList());
    assertInstanceOf(ArrayList.class, parents.get(0).children());
    assertInstanceOf(ArrayList.class, parents.get(1).children());
    assertNotSame(parents.get(0).children(), parents.get(2).children());
  }

  @Test
  void attachesMutableEmptyListsForParentsWithoutChildren() {
    List<TestParent> parents =
        List.of(new TestParent(null), new TestParent(5L), new TestParent(6L));

    RelationLoader.attachOneToMany(
        parents,
        ids -> List.of(new TestChild(null, "ignored"), new TestChild(5L, "kept")),
        TestParent::id,
        TestChild::parentId,
        TestParent::setChildren);

    assertTrue(parents.get(0).children().isEmpty());
    assertEquals(
        List.of("kept"), parents.get(1).children().stream().map(TestChild::value).toList());
    assertTrue(parents.get(2).children().isEmpty());
    for (TestParent parent : parents) {
      assertInstanceOf(ArrayList.class, parent.children());
    }
    parents.get(0).children().add(new TestChild(null, "added"));
    parents.get(2).children().add(new TestChild(6L, "added"));
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
    assertTrue(parents.stream().allMatch(parent -> parent.children() instanceof ArrayList));
    assertNotSame(parents.get(0).children(), parents.get(1).children());
  }

  @Test
  void rejectsNullOneToManyLoaderResult() {
    NullPointerException exception =
        assertThrows(
            NullPointerException.class,
            () ->
                RelationLoader.attachOneToMany(
                    List.of(new TestParent(1L)),
                    ids -> null,
                    TestParent::id,
                    TestChild::parentId,
                    TestParent::setChildren));

    assertEquals("Child loader must not return null", exception.getMessage());
  }

  @Test
  void validatesOneToManyArguments() {
    assertNullArgument(
        "Parents must not be null",
        () ->
            RelationLoader.attachOneToMany(
                null,
                ids -> List.of(),
                TestParent::id,
                TestChild::parentId,
                TestParent::setChildren));
    assertNullArgument(
        "Child loader must not be null",
        () ->
            RelationLoader.attachOneToMany(
                List.of(), null, TestParent::id, TestChild::parentId, TestParent::setChildren));
    assertNullArgument(
        "Parent key extractor must not be null",
        () ->
            RelationLoader.attachOneToMany(
                List.of(), ids -> List.of(), null, TestChild::parentId, TestParent::setChildren));
    assertNullArgument(
        "Child parent key extractor must not be null",
        () ->
            RelationLoader.attachOneToMany(
                List.of(), ids -> List.of(), TestParent::id, null, TestParent::setChildren));
    assertNullArgument(
        "Attachment callback must not be null",
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
  void manyToOneRetainsFirstRelatedRowPerKey() {
    TestManyToOneParent parent = new TestManyToOneParent(1L);

    RelationLoader.attachManyToOne(
        List.of(parent),
        ids -> List.of(new TestOwner(1L, "first"), new TestOwner(1L, "second")),
        TestManyToOneParent::ownerId,
        TestOwner::id,
        TestManyToOneParent::setOwner);

    assertEquals("first", parent.owner().name());
  }

  @Test
  void manyToOneAttachesNullForNullAndUnmatchedKeys() {
    List<TestManyToOneParent> parents =
        List.of(new TestManyToOneParent(null), new TestManyToOneParent(2L));

    RelationLoader.attachManyToOne(
        parents,
        ids -> List.of(new TestOwner(null, "ignored")),
        TestManyToOneParent::ownerId,
        TestOwner::id,
        TestManyToOneParent::setOwner);

    assertNull(parents.get(0).owner());
    assertNull(parents.get(1).owner());
  }

  @Test
  void rejectsNullManyToOneLoaderResult() {
    NullPointerException exception =
        assertThrows(
            NullPointerException.class,
            () ->
                RelationLoader.attachManyToOne(
                    List.of(new TestManyToOneParent(1L)),
                    ids -> null,
                    TestManyToOneParent::ownerId,
                    TestOwner::id,
                    TestManyToOneParent::setOwner));

    assertEquals("Related loader must not return null", exception.getMessage());
  }

  @Test
  void validatesManyToOneArguments() {
    assertNullArgument(
        "Parents must not be null",
        () ->
            RelationLoader.attachManyToOne(
                null,
                ids -> List.of(),
                TestManyToOneParent::ownerId,
                TestOwner::id,
                TestManyToOneParent::setOwner));
    assertNullArgument(
        "Related loader must not be null",
        () ->
            RelationLoader.attachManyToOne(
                List.of(),
                null,
                TestManyToOneParent::ownerId,
                TestOwner::id,
                TestManyToOneParent::setOwner));
    assertNullArgument(
        "Foreign key extractor must not be null",
        () ->
            RelationLoader.attachManyToOne(
                List.of(), ids -> List.of(), null, TestOwner::id, TestManyToOneParent::setOwner));
    assertNullArgument(
        "Related key extractor must not be null",
        () ->
            RelationLoader.attachManyToOne(
                List.of(),
                ids -> List.of(),
                TestManyToOneParent::ownerId,
                null,
                TestManyToOneParent::setOwner));
    assertNullArgument(
        "Attachment callback must not be null",
        () ->
            RelationLoader.attachManyToOne(
                List.of(), ids -> List.of(), TestManyToOneParent::ownerId, TestOwner::id, null));
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
    assertNull(parents.get(1).detail());
  }

  @Test
  void oneToOneRejectsDuplicateRelatedRowsPerKey() {
    IllegalStateException exception =
        assertThrows(
            IllegalStateException.class,
            () ->
                RelationLoader.attachOneToOne(
                    List.of(new TestOneToOneParent(1L)),
                    ids -> List.of(new TestDetail(1L, "first"), new TestDetail(1L, "second")),
                    TestOneToOneParent::id,
                    TestDetail::parentId,
                    TestOneToOneParent::setDetail));

    assertEquals(
        "One-to-one loader returned duplicate related rows for key: 1", exception.getMessage());
  }

  @Test
  void rejectsNullOneToOneLoaderResult() {
    NullPointerException exception =
        assertThrows(
            NullPointerException.class,
            () ->
                RelationLoader.attachOneToOne(
                    List.of(new TestOneToOneParent(1L)),
                    ids -> null,
                    TestOneToOneParent::id,
                    TestDetail::parentId,
                    TestOneToOneParent::setDetail));

    assertEquals("Child loader must not return null", exception.getMessage());
  }

  @Test
  void validatesOneToOneArguments() {
    assertNullArgument(
        "Parents must not be null",
        () ->
            RelationLoader.attachOneToOne(
                null,
                ids -> List.of(),
                TestOneToOneParent::id,
                TestDetail::parentId,
                TestOneToOneParent::setDetail));
    assertNullArgument(
        "Child loader must not be null",
        () ->
            RelationLoader.attachOneToOne(
                List.of(),
                null,
                TestOneToOneParent::id,
                TestDetail::parentId,
                TestOneToOneParent::setDetail));
    assertNullArgument(
        "Parent key extractor must not be null",
        () ->
            RelationLoader.attachOneToOne(
                List.of(),
                ids -> List.of(),
                null,
                TestDetail::parentId,
                TestOneToOneParent::setDetail));
    assertNullArgument(
        "Child parent key extractor must not be null",
        () ->
            RelationLoader.attachOneToOne(
                List.of(),
                ids -> List.of(),
                TestOneToOneParent::id,
                null,
                TestOneToOneParent::setDetail));
    assertNullArgument(
        "Attachment callback must not be null",
        () ->
            RelationLoader.attachOneToOne(
                List.of(), ids -> List.of(), TestOneToOneParent::id, TestDetail::parentId, null));
  }

  @Test
  void manyToManyDeduplicatesJoinPairsAndChildRowsInInsertionOrder() {
    List<TestGroup> groups =
        List.of(new TestGroup(1L), new TestGroup(2L), new TestGroup(null), new TestGroup(3L));

    RelationLoader.attachManyToMany(
        groups,
        ids -> {
          assertEquals(List.of(1L, 2L, 3L), ids);
          return List.of(
              new TestMembership(1L, 11L),
              new TestMembership(1L, 10L),
              new TestMembership(1L, 11L),
              new TestMembership(2L, 11L),
              new TestMembership(null, 10L),
              new TestMembership(2L, null));
        },
        ids -> {
          assertEquals(List.of(11L, 10L), ids);
          return List.of(
              new TestTag(11L, "first-y"), new TestTag(10L, "x"), new TestTag(11L, "second-y"));
        },
        TestGroup::id,
        TestMembership::groupId,
        TestMembership::tagId,
        TestTag::id,
        TestGroup::setTags);

    assertEquals(
        List.of("first-y", "x"), groups.get(0).tags().stream().map(TestTag::value).toList());
    assertEquals(List.of("first-y"), groups.get(1).tags().stream().map(TestTag::value).toList());
    assertTrue(groups.get(2).tags().isEmpty());
    assertTrue(groups.get(3).tags().isEmpty());
    for (TestGroup group : groups) {
      assertInstanceOf(ArrayList.class, group.tags());
    }
    groups.get(2).tags().add(new TestTag(12L, "added"));
    groups.get(3).tags().add(new TestTag(13L, "added"));
  }

  @Test
  void manyToManyAttachesMutableEmptyListsWithoutLoadingChildrenWhenThereAreNoJoins() {
    AtomicBoolean childLoaderCalled = new AtomicBoolean(false);
    List<TestGroup> groups = List.of(new TestGroup(1L), new TestGroup(1L));

    RelationLoader.attachManyToMany(
        groups,
        ids -> List.of(),
        ids -> {
          childLoaderCalled.set(true);
          return List.of();
        },
        TestGroup::id,
        TestMembership::groupId,
        TestMembership::tagId,
        TestTag::id,
        TestGroup::setTags);

    assertFalse(childLoaderCalled.get());
    assertInstanceOf(ArrayList.class, groups.get(0).tags());
    assertInstanceOf(ArrayList.class, groups.get(1).tags());
    assertNotSame(groups.get(0).tags(), groups.get(1).tags());
    groups.get(0).tags().add(new TestTag(10L, "added"));
  }

  @Test
  void rejectsNullManyToManyLoaderResults() {
    NullPointerException joinException =
        assertThrows(
            NullPointerException.class,
            () ->
                RelationLoader.attachManyToMany(
                    List.of(new TestGroup(1L)),
                    ids -> null,
                    ids -> List.of(),
                    TestGroup::id,
                    TestMembership::groupId,
                    TestMembership::tagId,
                    TestTag::id,
                    TestGroup::setTags));
    NullPointerException childException =
        assertThrows(
            NullPointerException.class,
            () ->
                RelationLoader.attachManyToMany(
                    List.of(new TestGroup(1L)),
                    ids -> List.of(new TestMembership(1L, 10L)),
                    ids -> null,
                    TestGroup::id,
                    TestMembership::groupId,
                    TestMembership::tagId,
                    TestTag::id,
                    TestGroup::setTags));

    assertEquals("Join loader must not return null", joinException.getMessage());
    assertEquals("Child loader must not return null", childException.getMessage());
  }

  @Test
  void validatesManyToManyArguments() {
    assertNullArgument(
        "Parents must not be null",
        () ->
            RelationLoader.attachManyToMany(
                null,
                ids -> List.of(),
                ids -> List.of(),
                TestGroup::id,
                TestMembership::groupId,
                TestMembership::tagId,
                TestTag::id,
                TestGroup::setTags));
    assertNullArgument(
        "Join loader must not be null",
        () ->
            RelationLoader.attachManyToMany(
                List.of(),
                null,
                ids -> List.of(),
                TestGroup::id,
                TestMembership::groupId,
                TestMembership::tagId,
                TestTag::id,
                TestGroup::setTags));
    assertNullArgument(
        "Child loader must not be null",
        () ->
            RelationLoader.attachManyToMany(
                List.of(),
                ids -> List.of(),
                null,
                TestGroup::id,
                TestMembership::groupId,
                TestMembership::tagId,
                TestTag::id,
                TestGroup::setTags));
    assertNullArgument(
        "Parent key extractor must not be null",
        () ->
            RelationLoader.attachManyToMany(
                List.of(),
                ids -> List.of(),
                ids -> List.of(),
                null,
                TestMembership::groupId,
                TestMembership::tagId,
                TestTag::id,
                TestGroup::setTags));
    assertNullArgument(
        "Join parent key extractor must not be null",
        () ->
            RelationLoader.attachManyToMany(
                List.of(),
                ids -> List.of(),
                ids -> List.of(),
                TestGroup::id,
                null,
                TestMembership::tagId,
                TestTag::id,
                TestGroup::setTags));
    assertNullArgument(
        "Join child key extractor must not be null",
        () ->
            RelationLoader.attachManyToMany(
                List.of(),
                ids -> List.of(),
                ids -> List.of(),
                TestGroup::id,
                TestMembership::groupId,
                null,
                TestTag::id,
                TestGroup::setTags));
    assertNullArgument(
        "Child key extractor must not be null",
        () ->
            RelationLoader.attachManyToMany(
                List.of(),
                ids -> List.of(),
                ids -> List.of(),
                TestGroup::id,
                TestMembership::groupId,
                TestMembership::tagId,
                null,
                TestGroup::setTags));
    assertNullArgument(
        "Attachment callback must not be null",
        () ->
            RelationLoader.attachManyToMany(
                List.of(),
                ids -> List.of(),
                ids -> List.of(),
                TestGroup::id,
                TestMembership::groupId,
                TestMembership::tagId,
                TestTag::id,
                null));
  }

  private static void assertNullArgument(String message, Executable executable) {
    NullPointerException exception = assertThrows(NullPointerException.class, executable);
    assertEquals(message, exception.getMessage());
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
