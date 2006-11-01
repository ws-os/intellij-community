package com.intellij.localvcs;

import java.util.List;

import org.junit.Test;

public class LocalVcsBasicsTest extends TestCase {
  // todo test basic file and directory operations

  @Test
  public void testOnlyCommitThrowsException() {
    vcs.createFile(p("file"), "");
    vcs.createFile(p("file"), "");

    try {
      vcs.commit();
      fail();
    } catch (LocalVcsException e) { }
  }

  @Test
  public void testDoesNotApplyAnyChangeIfCommitFail() {
    vcs.createFile(p("file1"), "");
    vcs.createFile(p("file2"), "");
    vcs.createFile(p("file2"), "");

    try { vcs.commit(); } catch (LocalVcsException e) { }

    assertFalse(vcs.hasEntry(p("file1")));
    assertFalse(vcs.hasEntry(p("file2")));
  }

  @Test
  public void testClearingChangesOnCommit() {
    vcs.createFile(p("file"), "content");
    vcs.changeFileContent(p("file"), "new content");
    vcs.rename(p("file"), "new file");
    vcs.delete(p("new file"));

    assertFalse(vcs.isClean());

    vcs.commit();
    assertTrue(vcs.isClean());
  }

  @Test
  public void testDoesNotChangeContentBeforeCommit() {
    // todo rename to doesNotMakeAnyChangesBeforeCommit
    vcs.createFile(p("file"), "content");
    vcs.commit();

    vcs.changeFileContent(p("file"), "new content");

    assertEquals("content", vcs.getEntry(p("file")).getContent());
  }

  @Test
  public void testHistory() {
    vcs.createFile(p("file"), "content");
    vcs.commit();

    vcs.changeFileContent(p("file"), "new content");
    vcs.commit();

    assertEntiesContents(new String[]{"new content", "content"},
                         vcs.getEntryHistory(p("file")));
  }

  @Test
  public void testHistoryOfAnUnknownFile() {
    vcs.createFile(p("file"), "");
    vcs.commit();

    assertTrue(vcs.getEntryHistory(p("unknown file")).isEmpty());
  }

  @Test
  public void testHistoryOfDeletedFile() {
    vcs.createFile(p("file"), "content");
    vcs.commit();

    vcs.delete(p("file"));
    vcs.commit();

    // todo what should we return?
    assertTrue(vcs.getEntryHistory(p("file")).isEmpty());
  }

  @Test
  public void testDoesNotIncludeUncommittedChangesInHistory() {
    vcs.createFile(p("file"), "content");
    vcs.commit();

    vcs.changeFileContent(p("file"), "new content");

    assertEntiesContents(new String[]{"content"},
                         vcs.getEntryHistory(p("file")));
  }

  @Test
  public void testRenamingKeepsOldNameAndContent() {
    vcs.createFile(p("file"), "content");
    vcs.commit();

    vcs.rename(p("file"), "new file");
    vcs.commit();

    List<Entry> revs = vcs.getEntryHistory(p("new file"));

    assertEquals(2, revs.size());

    assertEquals(p("new file"), revs.get(0).getPath());
    assertEquals("content", revs.get(0).getContent());

    assertEquals(p("file"), revs.get(1).getPath());
    assertEquals("content", revs.get(1).getContent());
  }

  @Test
  public void testCreatingAndDeletingSameFileBeforeCommit() {
    vcs.createFile(p("file"), "");
    vcs.delete(p("file"));
    vcs.commit();

    assertFalse(vcs.hasEntry(p("file")));
  }

  @Test
  public void testDeletingAndAddingSameFileBeforeCommit() {
    vcs.createFile(p("file"), "");
    vcs.commit();

    vcs.delete(p("file"));
    vcs.createFile(p("file"), "");
    vcs.commit();

    assertTrue(vcs.hasEntry(p("file")));
  }

  @Test
  public void testAddingAndChangingSameFileBeforeCommit() {
    vcs.createFile(p("file"), "content");
    vcs.changeFileContent(p("file"), "new content");
    vcs.commit();

    assertEquals("new content", vcs.getEntry(p("file")).getContent());
  }

  @Test
  public void testDeletingFileAndCreatingNewOneWithSameName() {
    vcs.createFile(p("file"), "old");
    vcs.commit();

    vcs.delete(p("file"));
    vcs.createFile(p("file"), "new");
    vcs.commit();

    assertEntiesContents(new String[]{"new"}, vcs.getEntryHistory(p("file")));
  }

  @Test
  public void testRenamingFileAndCreatingNewOneWithSameName() {
    vcs.createFile(p("file1"), "content1");
    vcs.commit();

    vcs.rename(p("file1"), "file2");
    vcs.createFile(p("file1"), "content2");
    vcs.commit();

    assertEntiesContents(new String[]{"content1", "content1"},
                         vcs.getEntryHistory(p("file2")));

    assertEntiesContents(new String[]{"content2"},
                         vcs.getEntryHistory(p("file1")));
  }
}
