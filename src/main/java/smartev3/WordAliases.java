package smartev3;

import java.util.*;

public class WordAliases
{
    private final Map<String,String> map = new HashMap<String,String>();

    private final Map<String,String> replaceMap = new HashMap<String,String>();

    public WordAliases add(String word, String... aliases)
    {
        word = word.toLowerCase();
        String wordWithSpaces = wrapWithSpaces(word);
        for (String alias : aliases)
        {
            alias = alias.toLowerCase();
            String aliasWithSpaces = wrapWithSpaces(alias);
            map.put(alias, word);
            replaceMap.put(aliasWithSpaces, wordWithSpaces);
        }
        return this;
    }

    public WordAliases addStop()
    {
        return add("stop", "stock", "stab", "stub");
    }

    public WordAliases addYes()
    {
        return add("yes", "chess", "gas", "guess", "jazz", "jess", "juice", "yay", "yea", "yeah", "year", "yep", "yup");
    }

    public WordAliases addNo()
    {
        return add("no", "nah", "nay", "nope");
    }

    public WordAliases addLeft()
    {
        return add("left", "lift", "loft");
    }

    public WordAliases addRight()
    {
        return add("right", "rad", "raid", "rat", "rate", "ray", "read",
            "red", "reed", "rid", "ride", "rite", "road", "rod", "rode",
            "rote", "roti", "route", "rye", "ryde", "wright", "write", "wrote");
    }

    public WordAliases addTop()
    {
        return add("top", "tab", "tap", "tip");
    }

    public WordAliases addCentre()
    {
        return add("centre", "center", "mad", "mat", "matt", "med", "meddle",
            "met", "metal", "mid", "middle", "mod", "mode");
    }

    public WordAliases addBottom()
    {
        return add("bottom", "button");
    }

    public WordAliases addTurn()
    {
        return add("turn", "tan", "ten", "tern", "tin", "ton", "tone", "torn", "town");
    }

    public String resolve(String phrase)
    {
        String phraseWithSpaces = wrapWithSpaces(phrase.toLowerCase());
        while (true)
        {
            boolean fixedPoint = true;
            for (Map.Entry<String,String> entry : replaceMap.entrySet())
            {
                String aliasWithSpaces = entry.getKey();
                String wordWithSpaces = entry.getValue();
                if (phraseWithSpaces.contains(aliasWithSpaces))
                {
                    phraseWithSpaces = StringHelper.replaceAll(phraseWithSpaces, aliasWithSpaces, wordWithSpaces);
                    fixedPoint = false;
                }
            }
            if (fixedPoint) break;
        }
        return phraseWithSpaces.trim();
    }

    private String wrapWithSpaces(String text)
    {
        return ' ' + text + ' ';
    }
}
