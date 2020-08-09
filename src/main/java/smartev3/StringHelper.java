package smartev3;

public abstract class StringHelper
{
    public static String replaceAll(String text, String what, String with)
    {
        for (;;)
        {
            int pos = text.indexOf(what);
            if (pos == -1)
            {
                break;
            }
            text = text.substring(0, pos) + with + text.substring(pos + what.length());
        }
        return text;
    }
}