package com.kooritea.fcmfix.util;
import org.junit.Test;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;
import java.io.File;
import java.nio.file.Files;
import static org.junit.Assert.*;
public class BoundedJournalTest {
    @Rule public TemporaryFolder temp = new TemporaryFolder();
    @Test public void rotatesAndReopensWithinFixedBudget() throws Exception {
        File dir = temp.newFolder();
        BoundedJournal journal = new BoundedJournal(dir, 4096);
        for (int i = 0; i < 80; i++) journal.append("event=" + i + " " + "x".repeat(900));
        new BoundedJournal(dir, 4096).append("new-session");
        assertEquals(2, dir.listFiles().length);
        for (File file : dir.listFiles()) assertTrue(file.length() <= 4096);
        assertTrue(new String(Files.readAllBytes(new File(dir, "night-current.log").toPath()), java.nio.charset.StandardCharsets.UTF_8).contains("new-session"));
    }
    @Test public void oversizedAndMultilineEventsCannotEscapeBudgetOrForgeLines() throws Exception {
        File dir = temp.newFolder();
        BoundedJournal journal = new BoundedJournal(dir, 4096);
        journal.append("中".repeat(10000));
        journal.append("abc\ninjected\rline");
        File file = new File(dir, "night-current.log");
        assertTrue(file.length() <= 4096);
        String text = new String(Files.readAllBytes(file.toPath()), java.nio.charset.StandardCharsets.UTF_8);
        assertTrue(text.contains("abc\\ninjected\\rline"));
        assertEquals(2, text.lines().count());
    }
}
