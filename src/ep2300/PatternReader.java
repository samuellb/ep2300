package ep2300;

import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.io.IOException;
import java.util.regex.Pattern;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

public class PatternReader
{

    private final static Pattern patternWords = Pattern.compile("\\S+");

    public static List<List<String>> getLines(String filename)
            throws IOException
    {
        return getLines(filename, patternWords);
    }

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


