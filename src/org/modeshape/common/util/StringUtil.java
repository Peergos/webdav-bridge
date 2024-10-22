/*
 * ModeShape (http://www.modeshape.org)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.modeshape.common.util;

import java.io.*;
import java.lang.reflect.Array;
import java.math.BigInteger;
import java.text.CharacterIterator;
import java.text.StringCharacterIterator;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utilities for string processing and manipulation.
 */
public class StringUtil {

    public static final String[] EMPTY_STRING_ARRAY = new String[0];
    private static final Pattern NORMALIZE_PATTERN = Pattern.compile("\\s+");
    private static final Pattern PARAMETER_COUNT_PATTERN = Pattern.compile("\\{(\\d+)\\}");

    /**
     * Combine the lines into a single string, using the new line character as the delimiter. This is compatible with
     * {@link #splitLines(String)}.
     * 
     * @param lines the lines to be combined
     * @return the combined lines, or an empty string if there are no lines
     */
    public static String combineLines( String[] lines ) {
        return combineLines(lines, '\n');
    }

    /**
     * Combine the lines into a single string, using the supplied separator as the delimiter.
     * 
     * @param lines the lines to be combined
     * @param separator the separator character
     * @return the combined lines, or an empty string if there are no lines
     */
    public static String combineLines( String[] lines,
                                       char separator ) {
        if (lines == null || lines.length == 0) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i != lines.length; ++i) {
            String line = lines[i];
            if (i != 0) sb.append(separator);
            sb.append(line);
        }
        return sb.toString();
    }

    /**
     * Split the supplied content into lines, returning each line as an element in the returned list.
     * 
     * @param content the string content that is to be split
     * @return the list of lines; never null but may be an empty (unmodifiable) list if the supplied content is null or empty
     */
    public static List<String> splitLines( final String content ) {
        if (content == null || content.length() == 0) return Collections.emptyList();
        String[] lines = content.split("[\\r]?\\n");
        return Arrays.asList(lines);
    }

    /**
     * Combine the supplied values into a single string, using the supplied string to delimit values.
     * 
     * @param values the values to be combined; may not be null, but may contain null values (which are skipped)
     * @param delimiter the characters to place between each of the values; may not be null
     * @return the joined string; never null
     * @see String#split(String)
     */
    public static String join( Object[] values,
                               String delimiter ) {
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (Object segment : values) {
            if (segment == null) continue;
            if (first) first = false;
            else sb.append(delimiter);
            sb.append(segment);
        }
        return sb.toString();
    }

    /**
     * Combine the supplied values into a single string, using the supplied string to delimit values.
     * 
     * @param values the values to be combined; may not be null, but may contain null values (which are skipped)
     * @param delimiter the characters to place between each of the values; may not be null
     * @return the joined string; never null
     * @see String#split(String)
     */
    public static String join( Iterable<?> values,
                               String delimiter ) {
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (Object segment : values) {
            if (segment == null) continue;
            if (first) first = false;
            else sb.append(delimiter);
            sb.append(segment);
        }
        return sb.toString();
    }

    /**
     * Create a string by substituting the parameters into all key occurrences in the supplied format. The pattern consists of
     * zero or more keys of the form <code>{n}</code>, where <code>n</code> is an integer starting at 0. Therefore, the first
     * parameter replaces all occurrences of "{0}", the second parameter replaces all occurrences of "{1}", etc.
     * <p>
     * If any parameter is null, the corresponding key is replaced with the string "null". Therefore, consider using an empty
     * string when keys are to be removed altogether.
     * </p>
     * <p>
     * If there are no parameters, this method does nothing and returns the supplied pattern as is.
     * </p>
     * 
     * @param pattern the pattern
     * @param parameters the parameters used to replace keys
     * @return the string with all keys replaced (or removed)
     */
    public static String createString( String pattern,
                                       Object... parameters ) {
        if (parameters == null) parameters = EMPTY_STRING_ARRAY;
        Matcher matcher = PARAMETER_COUNT_PATTERN.matcher(pattern);
        // CHECKSTYLE IGNORE check FOR NEXT 1 LINES
        StringBuffer text = new StringBuffer();
        int requiredParameterCount = 0;
        boolean err = false;
        while (matcher.find()) {
            int ndx = Integer.valueOf(matcher.group(1));
            if (requiredParameterCount <= ndx) {
                requiredParameterCount = ndx + 1;
            }
            if (ndx >= parameters.length) {
                err = true;
                matcher.appendReplacement(text, matcher.group());
            } else {
                Object parameter = parameters[ndx];

                // Automatically pretty-print arrays
                if (parameter != null && parameter.getClass().isArray()) {
                    if (parameter instanceof Object[]) {
                        parameter = Arrays.asList((Object[])parameter);
                    } else {
                        int length = Array.getLength(parameter);
                        List<Object> parameterAsList = new ArrayList<Object>(length);
                        for (int i = 0; i < length; i++) {
                            parameterAsList.add(Array.get(parameter, i));
                        }
                        parameter = parameterAsList;
                    }
                }

                matcher.appendReplacement(text, Matcher.quoteReplacement(parameter == null ? "null" : parameter.toString()));
            }
        }
        if (err || requiredParameterCount < parameters.length) {
            throw new IllegalArgumentException(text.toString());
        }
        matcher.appendTail(text);

        return text.toString();
    }

    /**
     * Create a new string containing the specified character repeated a specific number of times.
     * 
     * @param charToRepeat the character to repeat
     * @param numberOfRepeats the number of times the character is to repeat in the result; must be greater than 0
     * @return the resulting string
     */
    public static String createString( final char charToRepeat,
                                       int numberOfRepeats ) {
        assert numberOfRepeats >= 0;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < numberOfRepeats; ++i) {
            sb.append(charToRepeat);
        }
        return sb.toString();
    }

    /**
     * Set the length of the string, padding with the supplied character if the supplied string is shorter than desired, or
     * truncating the string if it is longer than desired. Unlike {@link #justifyLeft(String, int, char)}, this method does not
     * remove leading and trailing whitespace.
     * 
     * @param original the string for which the length is to be set; may not be null
     * @param length the desired length; must be positive
     * @param padChar the character to use for padding, if the supplied string is not long enough
     * @return the string of the desired length
     * @see #justifyLeft(String, int, char)
     */
    public static String setLength( String original,
                                    int length,
                                    char padChar ) {
        return justifyLeft(original, length, padChar, false);
    }

    public static enum Justify {
        LEFT,
        RIGHT,
        CENTER;
    }

    /**
     * Justify the contents of the string.
     * 
     * @param justify the way in which the string is to be justified
     * @param str the string to be right justified; if null, an empty string is used
     * @param width the desired width of the string; must be positive
     * @param padWithChar the character to use for padding, if needed
     * @return the right justified string
     */
    public static String justify( Justify justify,
                                  String str,
                                  final int width,
                                  char padWithChar ) {
        switch (justify) {
            case LEFT:
                return justifyLeft(str, width, padWithChar);
            case RIGHT:
                return justifyRight(str, width, padWithChar);
            case CENTER:
                return justifyCenter(str, width, padWithChar);
        }
        assert false;
        return null;
    }

    /**
     * Right justify the contents of the string, ensuring that the string ends at the last character. If the supplied string is
     * longer than the desired width, the leading characters are removed so that the last character in the supplied string at the
     * last position. If the supplied string is shorter than the desired width, the padding character is inserted one or more
     * times such that the last character in the supplied string appears as the last character in the resulting string and that
     * the length matches that specified.
     * 
     * @param str the string to be right justified; if null, an empty string is used
     * @param width the desired width of the string; must be positive
     * @param padWithChar the character to use for padding, if needed
     * @return the right justified string
     */
    public static String justifyRight( String str,
                                       final int width,
                                       char padWithChar ) {
        assert width > 0;
        // Trim the leading and trailing whitespace ...
        str = str != null ? str.trim() : "";

        final int length = str.length();
        int addChars = width - length;
        if (addChars < 0) {
            // truncate the first characters, keep the last
            return str.subSequence(length - width, length).toString();
        }
        // Prepend the whitespace ...
        final StringBuilder sb = new StringBuilder();
        while (addChars > 0) {
            sb.append(padWithChar);
            --addChars;
        }

        // Write the content ...
        sb.append(str);
        return sb.toString();
    }

    /**
     * Left justify the contents of the string, ensuring that the supplied string begins at the first character and that the
     * resulting string is of the desired length. If the supplied string is longer than the desired width, it is truncated to the
     * specified length. If the supplied string is shorter than the desired width, the padding character is added to the end of
     * the string one or more times such that the length is that specified. All leading and trailing whitespace is removed.
     * 
     * @param str the string to be left justified; if null, an empty string is used
     * @param width the desired width of the string; must be positive
     * @param padWithChar the character to use for padding, if needed
     * @return the left justified string
     * @see #setLength(String, int, char)
     */
    public static String justifyLeft( String str,
                                      final int width,
                                      char padWithChar ) {
        return justifyLeft(str, width, padWithChar, true);
    }

    protected static String justifyLeft( String str,
                                         final int width,
                                         char padWithChar,
                                         boolean trimWhitespace ) {
        // Trim the leading and trailing whitespace ...
        str = str != null ? (trimWhitespace ? str.trim() : str) : "";

        int addChars = width - str.length();
        if (addChars < 0) {
            // truncate
            return str.subSequence(0, width).toString();
        }
        // Write the content ...
        final StringBuilder sb = new StringBuilder();
        sb.append(str);

        // Append the whitespace ...
        while (addChars > 0) {
            sb.append(padWithChar);
            --addChars;
        }

        return sb.toString();
    }

    /**
     * Center the contents of the string. If the supplied string is longer than the desired width, it is truncated to the
     * specified length. If the supplied string is shorter than the desired width, padding characters are added to the beginning
     * and end of the string such that the length is that specified; one additional padding character is prepended if required.
     * All leading and trailing whitespace is removed before centering.
     * 
     * @param str the string to be left justified; if null, an empty string is used
     * @param width the desired width of the string; must be positive
     * @param padWithChar the character to use for padding, if needed
     * @return the left justified string
     * @see #setLength(String, int, char)
     */
    public static String justifyCenter( String str,
                                        final int width,
                                        char padWithChar ) {
        // Trim the leading and trailing whitespace ...
        str = str != null ? str.trim() : "";

        int addChars = width - str.length();
        if (addChars < 0) {
            // truncate
            return str.subSequence(0, width).toString();
        }
        // Write the content ...
        int prependNumber = addChars / 2;
        int appendNumber = prependNumber;
        if ((prependNumber + appendNumber) != addChars) {
            ++prependNumber;
        }

        final StringBuilder sb = new StringBuilder();

        // Prepend the pad character(s) ...
        while (prependNumber > 0) {
            sb.append(padWithChar);
            --prependNumber;
        }

        // Add the actual content
        sb.append(str);

        // Append the pad character(s) ...
        while (appendNumber > 0) {
            sb.append(padWithChar);
            --appendNumber;
        }

        return sb.toString();
    }

    /**
     * Truncate the supplied string to be no more than the specified length. This method returns an empty string if the supplied
     * object is null.
     * 
     * @param obj the object from which the string is to be obtained using {@link Object#toString()}.
     * @param maxLength the maximum length of the string being returned
     * @return the supplied string if no longer than the maximum length, or the supplied string truncated to be no longer than the
     *         maximum length (including the suffix)
     * @throws IllegalArgumentException if the maximum length is negative
     */
    public static String truncate( Object obj,
                                   int maxLength ) {
        return truncate(obj, maxLength, null);
    }

    /**
     * Truncate the supplied string to be no more than the specified length. This method returns an empty string if the supplied
     * object is null.
     * 
     * @param obj the object from which the string is to be obtained using {@link Object#toString()}.
     * @param maxLength the maximum length of the string being returned
     * @param suffix the suffix that should be added to the content if the string must be truncated, or null if the default suffix
     *        of "..." should be used
     * @return the supplied string if no longer than the maximum length, or the supplied string truncated to be no longer than the
     *         maximum length (including the suffix)
     * @throws IllegalArgumentException if the maximum length is negative
     */
    public static String truncate( Object obj,
                                   int maxLength,
                                   String suffix ) {
        if (obj == null || maxLength == 0) {
            return "";
        }
        String str = obj.toString();
        if (str.length() <= maxLength) return str;
        if (suffix == null) suffix = "...";
        int maxNumChars = maxLength - suffix.length();
        if (maxNumChars < 0) {
            // Then the max length is actually shorter than the suffix ...
            str = suffix.substring(0, maxLength);
        } else if (str.length() > maxNumChars) {
            str = str.substring(0, maxNumChars) + suffix;
        }
        return str;
    }

    /**
     * Read and return the entire contents of the supplied {@link Reader}. This method always closes the reader when finished
     * reading.
     * 
     * @param reader the reader of the contents; may be null
     * @return the contents, or an empty string if the supplied reader is null
     * @throws IOException if there is an error reading the content
     */
    public static String read( Reader reader ) throws IOException {
        return IoUtil.read(reader);
    }

    /**
     * Read and return the entire contents of the supplied {@link InputStream}. This method always closes the stream when finished
     * reading.
     * 
     * @param stream the streamed contents; may be null
     * @return the contents, or an empty string if the supplied stream is null
     * @throws IOException if there is an error reading the content
     */
    public static String read( InputStream stream ) throws IOException {
        return IoUtil.read(stream);
    }

    /**
     * Write the entire contents of the supplied string to the given stream. This method always flushes and closes the stream when
     * finished.
     * 
     * @param content the content to write to the stream; may be null
     * @param stream the stream to which the content is to be written
     * @throws IOException
     * @throws IllegalArgumentException if the stream is null
     */
    public static void write( String content,
                              OutputStream stream ) throws IOException {
        IoUtil.write(content, stream);
    }

    /**
     * Write the entire contents of the supplied string to the given writer. This method always flushes and closes the writer when
     * finished.
     * 
     * @param content the content to write to the writer; may be null
     * @param writer the writer to which the content is to be written
     * @throws IOException
     * @throws IllegalArgumentException if the writer is null
     */
    public static void write( String content,
                              Writer writer ) throws IOException {
        IoUtil.write(content, writer);
    }

    /**
     * Get the stack trace of the supplied exception.
     * 
     * @param throwable the exception for which the stack trace is to be returned
     * @return the stack trace, or null if the supplied exception is null
     */
    public static String getStackTrace( Throwable throwable ) {
        if (throwable == null) return null;
        final ByteArrayOutputStream bas = new ByteArrayOutputStream();
        final PrintWriter pw = new PrintWriter(bas);
        throwable.printStackTrace(pw);
        pw.close();
        return bas.toString();
    }

    /**
     * Removes leading and trailing whitespace from the supplied text, and reduces other consecutive whitespace characters to a
     * single space. Whitespace includes line-feeds.
     * 
     * @param text the text to be normalized
     * @return the normalized text
     */
    public static String normalize( String text ) {
        // This could be much more efficient.
        return NORMALIZE_PATTERN.matcher(text).replaceAll(" ").trim();
    }

    private static final byte[] HEX_CHAR_TABLE = {(byte)'0', (byte)'1', (byte)'2', (byte)'3', (byte)'4', (byte)'5', (byte)'6',
        (byte)'7', (byte)'8', (byte)'9', (byte)'a', (byte)'b', (byte)'c', (byte)'d', (byte)'e', (byte)'f'};

    /**
     * Get the hexadecimal string representation of the supplied byte array.
     * 
     * @param bytes the byte array
     * @return the hex string representation of the byte array; never null
     */
    public static String getHexString( byte[] bytes ) {
        try {
            byte[] hex = new byte[2 * bytes.length];
            int index = 0;

            for (byte b : bytes) {
                int v = b & 0xFF;
                hex[index++] = HEX_CHAR_TABLE[v >>> 4];
                hex[index++] = HEX_CHAR_TABLE[v & 0xF];
            }
            return new String(hex, "ASCII");
        } catch (UnsupportedEncodingException e) {
            BigInteger bi = new BigInteger(1, bytes);
            return String.format("%0" + (bytes.length << 1) + "x", bi);
        }
    }

    public static byte[] fromHexString( String hexadecimal ) {
        int len = hexadecimal.length();
        if (len % 2 != 0) {
            hexadecimal = "0" + hexadecimal;
        }
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte)((Character.digit(hexadecimal.charAt(i), 16) << 4) + Character.digit(hexadecimal.charAt(i + 1),
                                                                                                     16));
        }
        return data;
    }

    public static boolean isHexString( String hexadecimal ) {
        int len = hexadecimal.length();
        for (int i = 0; i < len; ++i) {
            if (!isHexCharacter(hexadecimal.charAt(i))) return false;
        }
        return true;
    }

    public static boolean isHexCharacter( char c ) {
        return ((c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F'));
    }

    /**
     * Returns true if the given string is null or represents the empty string
     * 
     * @param str the string; may be null or empty
     * @return true if the string is null or contains only whitespace
     */
    public static boolean isBlank( String str ) {
        return str == null || str.trim().isEmpty();
    } 
    
    /**
     * Returns true if the given string is not null and does not represents the empty string
     * 
     * @param str the string; may be null or empty
     * @return true if the string is not null and not empty, false otherwise
     */
    public static boolean notBlank( String str ) {
        return !isBlank(str);
    }

    /**
     * Return whether the supplied string contains any of the supplied characters.
     * 
     * @param str the string to be examined; may not be null
     * @param chars the characters to be found within the supplied string; may be zero-length
     * @return true if the supplied string contains at least one of the supplied characters, or false otherwise
     */
    public static boolean containsAnyOf( String str,
                                         char... chars ) {
        CharacterIterator iter = new StringCharacterIterator(str);
        for (char c = iter.first(); c != CharacterIterator.DONE; c = iter.next()) {
            for (char match : chars) {
                if (c == match) return true;
            }
        }
        return false;
    }

    private StringUtil() {
        // Prevent construction
    }
}
