package javi.typingtutor;

import java.util.List;

import javi.TextEdit;

/**
 * Tracks state of an active typing practice session.
 *
 * @param expectedLines the lines the user should type
 * @param buffer the TextEdit buffer the user types in
 * @param startTime epoch millis when the session started
 * @param mode the lesson mode used for this session
 * @param headerLines number of header lines before content pairs
 */
record TypingSession(
   List<String> expectedLines,
   TextEdit<String> buffer,
   long startTime,
   LessonMode mode,
   int headerLines
) { }
