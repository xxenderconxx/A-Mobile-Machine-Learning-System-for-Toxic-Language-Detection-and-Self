package com.example.toxiclanguagekeyboard;

import android.view.inputmethod.InputConnection;

/** Replace only the still-current suffix; some editors return "" rather than null for no selection. */
public final class EditorRewriter {
    private EditorRewriter() {}
    public static boolean replaceSuffix(InputConnection connection, String expected, String replacement) {
        if (connection == null) return false;
        CharSequence selection = connection.getSelectedText(0);
        if (selection != null && selection.length() != 0) return false;
        CharSequence before = connection.getTextBeforeCursor(expected.length() + 100, 0);
        if (before == null) return false;
        String current = before.toString();
        int end = current.length();
        if (!current.endsWith(expected)) {
            while (end > 0 && Character.isWhitespace(current.charAt(end - 1))) end--;
        }
        if (end < expected.length() || !current.substring(0, end).endsWith(expected)) return false;
        String trailing = current.substring(end);
        connection.beginBatchEdit();
        try {
            return connection.deleteSurroundingText(expected.length() + trailing.length(), 0)
                    && connection.commitText(replacement + trailing, 1);
        } finally {
            connection.endBatchEdit();
        }
    }
}
