package io.pzstorm.launcher.ui;

import io.pzstorm.launcher.*;
import java.awt.Component;
import java.nio.file.Path;
import java.util.List;
import javax.swing.*;
import javax.swing.event.*;

/** Upstream description requirement adapted to One Life's local support workflow. */
public final class SendLogsDialog {
    private SendLogsDialog() {}

    public static void open(Component parent) {
        JTextArea description = new JTextArea(5, 40);
        description.setLineWrap(true);
        description.setWrapStyleWord(true);
        JButton save = new JButton("Save report"), cancel = new JButton("Cancel");
        JLabel counter = new JLabel();
        Runnable refresh =
                () -> {
                    int length = description.getText().trim().length();
                    save.setEnabled(length >= 11 && length <= 16000);
                    counter.setText(
                            length < 11 ? (11 - length) + " more characters required" : " ");
                };
        description
                .getDocument()
                .addDocumentListener(
                        new DocumentListener() {
                            public void insertUpdate(DocumentEvent e) {
                                refresh.run();
                            }

                            public void removeUpdate(DocumentEvent e) {
                                refresh.run();
                            }

                            public void changedUpdate(DocumentEvent e) {
                                refresh.run();
                            }
                        });
        refresh.run();
        JOptionPane pane =
                new JOptionPane(
                        new Object[] {
                            "Describe what you were doing, what went wrong, and roughly when.",
                            "Saves a local ZIP with your description and recent launcher/game logs.",
                            "Nothing is uploaded. Review it before sharing with One Life support.",
                            "Logs may contain player names, server addresses and other personal information.",
                            new JScrollPane(description),
                            counter
                        },
                        JOptionPane.PLAIN_MESSAGE,
                        JOptionPane.DEFAULT_OPTION,
                        null,
                        new Object[] {save, cancel});
        JDialog dialog = pane.createDialog(parent, "One Life support report");
        cancel.addActionListener(e -> dialog.dispose());
        save.addActionListener(
                e -> {
                    String text = description.getText();
                    dialog.dispose();
                    new SwingWorker<Path, Void>() {
                        protected Path doInBackground() throws Exception {
                            return SupportReport.export(
                                    LauncherPaths.launcherDir().resolve("reports"),
                                    text,
                                    List.of(
                                            LauncherPaths.logFile(),
                                            LauncherPaths.gameLogFile(),
                                            LauncherPaths.previousGameLogFile(),
                                            LauncherPaths.zomboidDir().resolve("console.txt"),
                                            LauncherPaths.zomboidDir()
                                                    .resolve("Logs/storm/main.log")));
                        }

                        protected void done() {
                            try {
                                JOptionPane.showMessageDialog(
                                        parent,
                                        "Saved locally:\n"
                                                + get()
                                                + "\nReview the ZIP, then attach it in One Life Discord support.");
                            } catch (Exception ex) {
                                JOptionPane.showMessageDialog(
                                        parent, "Could not save report: " + ex.getMessage());
                            }
                        }
                    }.execute();
                });
        dialog.setVisible(true);
    }
}
