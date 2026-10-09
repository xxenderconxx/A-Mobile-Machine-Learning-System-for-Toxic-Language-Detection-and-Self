package com.example.toxiclanguagekeyboard;

import android.content.SharedPreferences;
import org.junit.Test;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class RevisionRegressionTest {
    @Test public void exportedModelMatchesSavedPythonReference() throws Exception {
        ToxicityClassifier classifier = new ToxicityClassifier(new java.io.FileInputStream("src/main/assets/model_weights.json"));
        assertTrue(classifier.isLoaded());
        try (java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.InputStreamReader(
                getClass().getResourceAsStream("/python-reference.jsonl"), StandardCharsets.UTF_8))) {
            String line; int count=0;
            while ((line=reader.readLine())!=null) {
                org.json.JSONObject row=new org.json.JSONObject(line);
                String text=row.getString("text");
                assertEquals(row.getString("id"),row.getString("reference_normalized"),classifier.normalizeText(text));
                double probability=classifier.calculateProbability(text,true,false);
                assertEquals(row.getString("id"),row.getDouble("reference_probability"),probability,1e-10);
                assertEquals(row.getDouble("reference_threshold"),classifier.getThreshold(false),0);
                count++;
            }
            assertEquals(1063,count);
        }
    }

    @Test public void cleanupAcceptsAnEmptySelectionAndRejectsChangedText() {
        android.view.inputmethod.InputConnection editor = mock(android.view.inputmethod.InputConnection.class);
        when(editor.getSelectedText(0)).thenReturn("");
        when(editor.getTextBeforeCursor(106, 0)).thenReturn("stupid");
        when(editor.deleteSurroundingText(6,0)).thenReturn(true);
        when(editor.commitText("uninformed",1)).thenReturn(true);
        assertTrue(EditorRewriter.replaceSuffix(editor,"stupid","uninformed"));
        verify(editor).deleteSurroundingText(6,0);
        verify(editor).commitText("uninformed",1);
        when(editor.getTextBeforeCursor(106,0)).thenReturn("edited");
        assertFalse(EditorRewriter.replaceSuffix(editor,"stupid","uninformed"));
        verify(editor,times(1)).deleteSurroundingText(6,0);
        when(editor.getTextBeforeCursor(106,0)).thenReturn("You are stupid  ");
        when(editor.deleteSurroundingText(8,0)).thenReturn(true);
        when(editor.commitText("uninformed  ",1)).thenReturn(true);
        assertTrue(EditorRewriter.replaceSuffix(editor,"stupid","uninformed"));
        verify(editor).deleteSurroundingText(8,0);
        verify(editor).commitText("uninformed  ",1);
        when(editor.getTextBeforeCursor(108,0)).thenReturn("You are stupid  ");
        when(editor.commitText("clear",1)).thenReturn(true);
        assertTrue(EditorRewriter.replaceSuffix(editor,"stupid  ","clear"));
    }
    private static ByteArrayInputStream json(String text) {
        return new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8));
    }
    @Test public void normalizedScoringIncludesIdfAndEntity() throws Exception {
        ToxicityClassifier c = new ToxicityClassifier(json("{\"intercept\":-1,\"threshold\":0.78,\"weights\":{\"stupid\":{\"w\":2,\"idf\":3},\"__ent_person__\":{\"w\":0.3,\"idf\":1}}}"));
        assertTrue(c.isLoaded());
        assertEquals(1/(1+Math.exp(-1)), c.calculateProbability("stupid"), 1e-12);
        assertEquals(1/(1+Math.exp(-(-1+6.3/Math.sqrt(10)))), c.calculateProbability("you stupid",true,false),1e-12);
        assertEquals(0.78,c.getThreshold(false),0);
        assertEquals(0.73,c.getThreshold(true),1e-12);
        assertEquals("hate",c.normalizeText("haaate")); // Existing triple repetition collapses to one.
    }
    @Test public void modelAndRulesAreIndependentlyAttributed() throws Exception {
        ToxicityClassifier c = new ToxicityClassifier(json("{\"intercept\":-10,\"weights\":{\"stupid\":{\"w\":20,\"idf\":1}}}"));
        SubstitutionEngine r = new SubstitutionEngine(json("{\"idiot\":\"uninformed\",\"hello\":\"hello\"}"));
        r.setClassifier(c);
        assertFalse("Rule lookup must not invoke ML",r.isToxicWord("stupid"));
        DetectionPipeline p = new DetectionPipeline(c,r);
        assertEquals("model",p.evaluate("stupid",Collections.emptySet(),false).reason());
        assertEquals("rules",p.evaluate("idiot",Collections.emptySet(),false).reason());
        assertFalse(p.evaluate("zxqv unfamiliarname",Collections.emptySet(),false).warning);
        assertFalse(p.evaluate("stupid",Collections.singleton("stupid"),false).warning);
        assertTrue(p.evaluate("hello idiot",Collections.emptySet(),false).warning);
    }
    @Test public void oneAlertHasAtMostOneCountedAction() {
        SharedPreferences prefs = mock(SharedPreferences.class);
        SharedPreferences.Editor editor = mock(SharedPreferences.Editor.class);
        Map<String,Integer> values = new HashMap<>();
        when(prefs.edit()).thenReturn(editor);
        when(prefs.getInt(anyString(),anyInt())).thenAnswer(a -> values.getOrDefault(a.getArgument(0),a.getArgument(1)));
        when(editor.putInt(anyString(),anyInt())).thenAnswer(a -> { values.put(a.getArgument(0),a.getArgument(1)); return editor; });
        AlertMetrics metrics = new AlertMetrics(prefs);
        AlertMetrics.Event first = metrics.shown(); first.edited(true); first.edited(true); first.continued();
        AlertMetrics.Event second = metrics.shown(); second.continued(); second.continued();
        AlertMetrics.Event third = metrics.shown(); third.edited(false); third.continued();
        AlertMetrics.Event fourth = metrics.shown(); fourth.dismissed(); fourth.edited(true);
        assertEquals(Integer.valueOf(4),values.get("count_detections"));
        assertEquals(Integer.valueOf(1),values.get("count_edits"));
        assertEquals(Integer.valueOf(1),values.get("count_ignored"));
    }
    @Test public void ordinaryDoubleLettersDoNotBecomeToxicSkeletons() throws Exception {
        SubstitutionEngine r = new SubstitutionEngine(json("{\"ass\":\"\",\"butt\":\"\",\"a\":\"\",\"stupid\":\"uninformed\"}"));
        assertFalse(r.containsToxicLanguage("as but pass a"));
        assertTrue(r.containsToxicLanguage("stuuupid"));
        assertTrue(r.containsToxicLanguage("ass"));
        r.setDynamicSafeWords(Collections.singleton("stupid"));
        assertFalse(r.containsToxicLanguage("stupid"));
    }
}
