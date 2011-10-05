package ep2300;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.util.regex.Pattern;

/**
 * Get lines with words from file matching pattern
 */
public class PatternReader
{

    private final static Pattern patternWords = Pattern.compile("\\S+");

    /**
     * Get all lines and their words from the specified file.
     * 
     * @param filename The filename of the file to read from
     * @return A list of lines, which is a list of words
     * @throws IOException If the file cannot be read
     */
    public static List<List<String>> getLines(String filename)
            throws IOException
    {
        return getLines(filename, patternWords);
    }

    /**
     * Get all lines and their words from the specified file matching the
     * specified pattern.
     * 
     * @param filename The filename of the file to read from
     * @param pattern The pattern to split with
     * @return A list of lines, which is a list of words.
     * @throws IOException When the file cannot be read.
     */
    public static List<List<String>> getLines(String filename, Pattern pattern)
            throws IOException
    {
        List<List<String>> lines = new ArrayList<List<String>>();

        Scanner scanner = new Scanner(new FileInputStream(filename), "UTF-8");
        try {
            while (scanner.hasNextLine()) {
                String line = scanner.nextLine();

                if (line.startsWith("     [java] ")) {
                    line = line.substring(12);
                }

                List<String> words = new ArrayList<String>();
                Scanner inner = new Scanner(line);
                while (inner.hasNext(pattern)) {
                    words.add(inner.next(pattern));
                }

                lines.add(words);
            }
        }
        finally {
            scanner.close();
        }

        return lines;
    }

}
