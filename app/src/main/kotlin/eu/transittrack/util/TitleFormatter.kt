package eu.transittrack.util

import java.io.BufferedReader
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStreamReader
import java.util.regex.Pattern

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component

import eu.transittrack.GtfsProperties

/**
 * Tool for formatting titles in the GTFS data. Need to be able to "unshout" titles (change "MAIN
 * ST" to "Main St") yet capitalize abbreviations (e.g. "BART" or "US"). Can also make sure that
 * when using "&" or "@" that they have consistent spaces around them.
 *
 *
 * The way this is done is that first the capitalization of the title is fixed. Each word
 * starting at a delimiter is capitalized while other characters are made lower case. Then regular
 * expressions are used to do special processing. The regular expressions are put into a file so
 * that each agency can have a particular list. The file name is passed to the constructor when
 * creating a TitleFormatter.
 *
 *
 * Some examples of regular expressions and their corresponding replacement text that can be
 * useful for fixing titles are shown below. Useful documentation at
 * http://www.regular-expressions.info and at
 * http://docs.oracle.com/javase/7/docs/api/java/util/regex/Pattern.html
 *
 *
 * -- For fixing capitalization of O'shaughnessy O's=>O'S -- For fixing capitalization of
 * abbreviation Bart=>BART -- For making sure there is space after a '&'. -- Note that "(?! )" means
 * not a whitespace char -- and means the non-whitespace char -- will not actually get replaced.
 *
 *
 * &(?! )=>&_ NOTE: last char actually a space! -- For making sure there is space before a '&'.
 * (?<! )&=> &
 */
@Component
@ConditionalOnProperty(name = ["transittrack.gtfs.title-sanitizing.enabled"], havingValue = "true")
@ConditionalOnProperty(name = ["transittrack.gtfs.title-sanitizing.regex-replace-list-file-name"])
class TitleFormatter(
    gtfsProperties: GtfsProperties,
) {
    private val logger: Logger = LoggerFactory.getLogger(javaClass)
    private val titleSanitizing = gtfsProperties.titleSanitizing

    private class RegexInfo(
        // The regex pattern of what is to be replaced
        val regex: String,
        val replace: String,
    ) {
        // So don't have to compile pattern every time it is used.
        // Makes code more efficient.
        val pattern: Pattern = Pattern.compile(regex)
    }

    private val regexReplaceList: MutableList<RegexInfo> = mutableListOf()

    init {
        runCatching { processRegexFile(titleSanitizing.regexReplaceListFileName) }
            .onFailure { logger.error("Could not open regexFile {}", titleSanitizing.regexReplaceListFileName, it) }
    }

    /**
     * Goes through list of regex replacements and returns true if the title passed in matches a
     * replace string. Useful for determining if a title already replaced and shouldn't be changed
     * further.
     *
     * @param title the title to check
     * @return true if title matches a replace string
     */
    fun shouldReplace(title: String): Boolean {
        for (regexInfo in regexReplaceList) {
            if (regexInfo.replace == title) {
                // Found a match!
                return true
            }
        }

        // No match found
        return false
    }

    /**
     * Takes a title, obtained from GTFS data, and makes it more readable. First capitalized the
     * text as well as possible. Then runs the configured regexs across the title so that other
     * problems can be fixed. If many regexs are configured this could take a lot of processing time
     * if there are a large number of titles.
     *
     * @param original The original title. Can be null
     * @return The formatted title. Null if passed in null.
     */
    fun process(original: String): String {
        // First, properly capitalize the title
        val capitalizedStr = if (titleSanitizing.capitalizeNames) capitalize(original) else original

        // Now that capitalization should mostly be correct, use
        // regexs configured in file to make other adjustments.
        // By doing the regexs after capitalization the regexs can
        // also be used to fixed complicated capitalization problems.
        val processed = processRegexReplacements(capitalizedStr!!)

        // Log any changes made
        if (processed != original) {
            logger.debug("processTitle() changed title \"{}\" to \"{}\"", original, processed)
        }

        return processed
    }

    /**
     * Processes file containing a list of regular expressions along with the corresponding
     * replacement text. A line is considered a comment if it start with "--" or "//".
     *
     * @param regexReplaceListFileName
     * @throws IOException
     */
    @Throws(IOException::class)
    private fun processRegexFile(
        regexReplaceListFileName: String?,
        delimiter: String = "=>",
    ) {
        if (regexReplaceListFileName != null) {
            logger.info("Reading file {} for regex/replace pairs for titles", regexReplaceListFileName)

            val fis = FileInputStream(regexReplaceListFileName)
            val reader = BufferedReader(InputStreamReader(fis))
            var line: String?
            var lineNumber = 0
            while ((reader.readLine().also { line = it }) != null) {
                ++lineNumber

                // If line is a comment then skip to next line.
                if (line!!.startsWith("--") || line.startsWith("//")) continue

                // Also ignore blank lines.
                if (line.trim { it <= ' ' }.isEmpty()) continue

                val contents = line.split(delimiter.toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
                if (contents.size != 2) {
                    logger.error(
                        "Line #{} in file {} does not have two elements separated by the delimiter {}",
                        lineNumber,
                        regexReplaceListFileName,
                        delimiter,
                    )
                    continue
                }

                // Add the regex/replace pair to the list
                val regex = contents[0]
                val replace = contents[1]
                regexReplaceList.add(RegexInfo(regex, replace))

                // Let user know what is being used
                logger.info("Adding regex/replace pair {}{}{}", regex, delimiter, replace)
            }

            reader.close()
        }
    }

    /**
     * Goes through list of regular expression and replaces the regular expression with the
     * corresponding replacement text.
     *
     * @param original
     * @return
     */
    private fun processRegexReplacements(original: String): String {
        var result = original
        for (regexInfo in regexReplaceList) {
            // Instead of just using String.replaceAll() use the already compiled
            // pattern to improve efficiency. pattern.matcher().replaceAll() is
            // same as String.replaceAll().
            val newResult = regexInfo.pattern
                .matcher(result)
                .replaceAll(regexInfo.replace)

            result = newResult
        }
        return result
    }

    companion object {
        /**
         * Capitalizes text so that first character after delimiter is capitalized but other characters
         * are made lower case.
         *
         * @param str
         * @return
         */
        private fun capitalize(str: String?): String? {
            // Delimiters specify word dividers. The text at beginning or  after a
            // whitespace or to the right of a delimiter is capitalized. Otherwise
            // it will be in lower case.
            val delimiters = charArrayOf('-', '/', '.', '&', '@', '(', ':', ';')
            return capitalize(str, delimiters)
        }

        /**
         * This method copied from org.apache.commons.lang.WordUtils but modified to also convert upper
         * case characters to lower characters as needed.
         *
         * @param str
         * @param delimiters Characters after which should use capital letter. Don't need to add
         * whitespace chars since those are already used as delimiters in isDelimiter().
         * @return
         */
        private fun capitalize(
            str: String?,
            delimiters: CharArray?,
        ): String? {
            val delimLen = (delimiters?.size ?: -1)
            if (str.isNullOrEmpty() || delimLen == 0) {
                return str
            }

            val strLen = str.length
            val builder = StringBuilder(strLen)
            var capitalizeNext = true
            for (i in 0..<strLen) {
                val ch = str[i]

                if (isDelimiter(ch, delimiters!!)) {
                    builder.append(ch)
                    capitalizeNext = true
                } else if (capitalizeNext) {
                    builder.append(ch.titlecaseChar())
                    capitalizeNext = false
                } else {
                    builder.append(ch.lowercaseChar())
                }
            }
            return builder.toString()
        }

        /**
         * Returns true of the character ch passed in is one of the delimiters passed in. All whitespace
         * automatically included as a delimiter. This method copied from
         * org.apache.commons.lang.WordUtils
         *
         * @param ch
         * @param delimiters
         * @return
         */
        private fun isDelimiter(
            ch: Char,
            delimiters: CharArray,
        ): Boolean {
            if (Character.isWhitespace(ch)) return true

            var i = 0
            val isize = delimiters.size
            while (i < isize) {
                if (ch == delimiters[i]) {
                    return true
                }
                i++
            }
            return false
        }
    }
}
