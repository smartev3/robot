package smartev3;

import java.util.*;

public class ConsoleGrid
{
    public static final int ROWS = 36;
    public static final int COLS = 74;

    private char[][] array;
    private String label, label2;
    private boolean joined;

    public ConsoleGrid(char[][] array, String label)
    {
        this.array = array;
        this.label = label;
    }

    public static char[][] newArray(int rows, int cols)
    {
        char[][] array = new char[rows][];
        for (int row = 0; row < rows; row++)
        {
            array[row] = new char[cols];
        }
        return array;
    }

    public ConsoleGrid join(ConsoleGrid other)
    {
        char[][] joined = newArray(ROWS, 2 * COLS);
        for (int row = 0; row < ROWS; row++)
        {
            for (int col = 0; col < COLS; col++)
            {
                joined[row][col] = array[row][col];
            }
            for (int col = 0; col < COLS; col++)
            {
                joined[row][COLS + col] = other.array[row][col];
            }
        }
        ConsoleGrid result = new ConsoleGrid(joined, null);
        result.joined = true;
        result.label = this.label;
        result.label2 = other.label;
        return result;
    }

    public String toString()
    {
        int rows = array.length;
        int cols = array[0].length;
        if (joined) cols /= 2;
        StringBuilder text = new StringBuilder();
        String head = "+" + String.join("", Collections.nCopies(cols, "-")) + "+"; // TODO: more efficient
        text.append(head);
        if (label != null)
        {
            int n = label.length();
            int labelPos = text.length() - head.length() + 4;
            text.setCharAt(labelPos - 1, ' ');
            for (int i = 0; i < n; i++)
            {
                text.setCharAt(labelPos + i, label.charAt(i));
            }
            text.setCharAt(labelPos + n, ' ');
        }
        if (joined)
        {
            text.append("   ");
            text.append(head);
            if (label2 != null)
            {
                int n = label2.length();
                int labelPos = text.length() - head.length() + 4;
                text.setCharAt(labelPos - 1, ' ');
                for (int i = 0; i < n; i++)
                {
                    text.setCharAt(labelPos + i, label2.charAt(i));
                }
                text.setCharAt(labelPos + n, ' ');
            }
        }
        text.append("\n");
        for (int row = 0; row < rows; row++)
        {
            text.append("|");
            for (int col = 0; col < cols; col++)
            {
                text.append(array[row][col]);
            }
            text.append("|");
            if (joined)
            {
                text.append("   |");
                for (int col = 0; col < cols; col++)
                {
                    text.append(array[row][cols + col]);
                }
                text.append("|");
            }
            text.append("\n");
        }
        text.append(head);
        if (joined)
        {
            text.append("   ");
            text.append(head);
        }
        text.append("\n");
        return text.toString();
    }
}
