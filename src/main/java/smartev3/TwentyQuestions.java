package smartev3;

import java.io.*;
import java.net.*;
import java.util.*;

public class TwentyQuestions
{
    // Play "20 questions" using the AI at "20q.net" as a helper.

    private static final boolean DEBUG = false;

    private static final String START_ADDRESS = "http://y.20q.net/gsq-enUK";
    private static final String START_PAYLOAD = "age=&ccode=23118&cctkr=NZ%2CAU%2CUS%2CJP&submit=++Play++";
    private static final String START_REFERER = "http://20q.net/play.html";

    private static final String ACCEPT = "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,image/apng,*/*;q=0.8";
    private static final String ACCEPT_LANGUAGE = "en-NZ,en-GB;q=0.9,en-US;q=0.8,en;q=0.7";

    private static class WebLink
    {
        String address;
        String label;
    }

    private static class WebPage
    {
        String question;
        String action;
        List<WebLink> links = new ArrayList<WebLink>();
    }

    private SmartRobot robot = null;
    private String referer = START_REFERER;
    private boolean testMode = false;

    public TwentyQuestions(SmartRobot robot)
    {
        this.robot = robot;
    }

    public static void main(String[] args)
    {
        new TwentyQuestions(null).test();
    }

    public void test()
    {
        testMode = true;
        playOnce();
    }

    public void play()
    {
        say("Please think of an object, and I'll ask you twenty questions to try to work out what it is.");
        say("The object you think of should be something that most people would know about, but not a proper noun or a specific person, place, or thing.");
        say("Please don't choose anything too tricky. Remember that I am just a small robot.");
        if (robot.inputMode() == SmartRobot.InputMode.SPEECH)
        {
            robot.sayHowToGetSpeechHelp();
            robot.say("If you want to stop playing, say: stop game.");
        }
        robot.sleepForSeconds(2);
        for (int pass = 1;; pass++)
        {
            if (pass > 1 && !robot.askYesNoQuestion("Do you want to play Twenty Questions again?"))
            {
                return;
            }
            say("OK, let's play.");
            if (!playOnce())
            {
                robot.say("OK, we'll stop playing Twenty Questions.");
                break;
            }
            robot.sleepForSeconds(4);
        }
    }

    public boolean playOnce()
    {
        String method = "GET";
        String address = START_ADDRESS;
        String payload = null;
        int questionNumber = 1;
        NEXT_PAGE:
        for (;;)
        {
            if (questionNumber == 21)
            {
                say("You win.");
                return true;
            }
            WebPage page = getWebPage(method, address, payload);
            referer = address;
            if (page.action != null)
            {
                method = "POST";
                address = followLink(address, page.action);
                payload = START_PAYLOAD;
            }
            else
            {
                if (page.links.size() == 0)
                {
                    malfunction("No links in page.");
                    return false;
                }
                if (page.question.equals("Is it classified as Animal, Vegetable or Mineral?"))
                {
                    page.question = "Which one of the following categories does the object belong to: Animal, Vegetable or Mineral?";
                }
                for (;;)
                {
                    if (testMode)
                    {
                        for (WebLink link : page.links)
                        {
                            System.out.println("Option: " + link.label);
                            if (DEBUG)
                            {
                                logDebug("Option URL: " + link.address);
                            }
                        }
                    }
                    boolean guessingTime = false;
                    if (page.links.size() == 3
                        && page.links.get(0).label.equals("Right")
                        && page.links.get(1).label.equals("Wrong")
                        && page.links.get(2).label.equals("Close"))
                    {
                        // Stick to Yes/No questions at "guessing" time.
                        guessingTime = true;
                        if (page.question.startsWith("I am guessing that it is "))
                        {
                            page.question = "Is it " + page.question.substring(25);
                        }
                        page.links.get(0).label = "Yes";
                        page.links.get(1).label = "No";
                        page.links.remove(2);
                    }
                    List<String> choices = new ArrayList<String>();
                    for (WebLink link : page.links)
                    {
                        choices.add(link.label);
                    }
                    if (choices.contains("Yes")
                        && choices.contains("No")
                        && choices.contains("Maybe")
                        && choices.contains("Sometimes"))
                    {
                        // 20Q asks: Yes, No, Unknown, Irrelevant, Sometimes, Maybe, Probably, Doubtful, Usually, Depends, Rarely, Partly.
                        // Too many options to present for voice control, so limit the choices.
                        choices.clear();
                        choices.add("Yes");
                        choices.add("No");
                        choices.add("Maybe");
                        choices.add("Sometimes");
                    }
                    if (choices.contains("Animal")
                        && choices.contains("Vegetable")
                        && choices.contains("Mineral"))
                    {
                        // 20Q asks: Animal, Vegetable, Mineral, Other, Unknown.
                        // Too many options to present for voice control, so limit the choices.
                        choices.clear();
                        choices.add("Animal");
                        choices.add("Vegetable");
                        choices.add("Mineral");
                    }
                    choices.add("Stop Game");
                    String answer = ask("Question " + questionNumber + ". " + page.question, choices);
                    if (answer.equals("Stop Game"))
                    {
                        return false;
                    }
                    for (WebLink link : page.links)
                    {
                        if (answer.equals(link.label))
                        {
                            if (guessingTime && answer.equals("Yes"))
                            {
                                say("I win.");
                                return true;
                            }
                            method = "GET";
                            address = link.address;
                            payload = null;
                            questionNumber++;
                            continue NEXT_PAGE;
                        }
                    }
                }
            }
            if (testMode)
            {
                say("Press Enter to continue.");
                System.console().readLine();
            }
        }
    }

    private void say(String text)
    {
        if (testMode)
        {
            System.out.println(text);
        }
        else
        {
            robot.say(text);
        }
    }

    private String ask(String question, List<String> choices)
    {
        if (testMode)
        {
            return System.console().readLine();
        }
        else
        {
            WordAliases aliases = new WordAliases()
                .addYes()
                .addNo()
                .addStop();
            int choice = robot.askUserToChoose(question, choices, aliases);
            return choices.get(choice - 1);
        }
    }

    private WebPage getWebPage(String method, String url, String payload)
    {
        if (DEBUG) logDebug("20Q Request: " + method + " " + url);
        WebPage page = new WebPage();
        try
        {
            HttpURLConnection conn = (HttpURLConnection)(new URL(url).openConnection());
            conn.setRequestMethod(method);
            conn.setRequestProperty("Accept", ACCEPT);
            conn.setRequestProperty("Accept-Language", ACCEPT_LANGUAGE);
            conn.setRequestProperty("Cache-Control", "max-age=0");
            conn.setRequestProperty("Host", "20q.net");
            conn.setRequestProperty("Referer", referer);
            if (method.equals("POST"))
            {
                conn.setDoOutput(true);
                OutputStream output = conn.getOutputStream();
                Writer writer = new OutputStreamWriter(output);
                writer.write(payload);
                writer.flush();
            }
            InputStream input = conn.getInputStream();
            BufferedReader reader = new BufferedReader(new InputStreamReader(input));
            StringBuilder buffer = new StringBuilder();
            while (true)
            {
                String line = reader.readLine();
                if (line == null)
                {
                    break;
                }
                else
                {
                    buffer.append(line);
                    buffer.append(' ');
                }
            }
            String text = buffer.toString();
            text = StringHelper.replaceAll(text, "&nbsp;", " ");
            text = StringHelper.replaceAll(text, "  ", " ");
            if (DEBUG) logDebug("20Q Response: " + text);
            int actionPos = text.indexOf("<form method=post action=\""); // 27
            if (actionPos != -1)
            {
                actionPos += 27;
                int actionEnd = text.indexOf("\"", actionPos);
                if (actionEnd == -1) malfunction("Cannot find end of form action.");
                page.action = text.substring(actionPos, actionEnd);
            }
            else
            {
                int questionPos = text.indexOf("<b>Q");
                if (questionPos == -1) malfunction("Cannot find beginning of question.");
                questionPos += 4;
                int questionEnd = text.indexOf("<br>", questionPos);
                if (questionPos == -1) malfunction("Cannot find end of question.");
                String question = text.substring(questionPos, questionEnd);
                int dotPos = question.indexOf(".");
                if (dotPos != -1) question = question.substring(dotPos + 1).trim();
                int tagPos = question.indexOf("<");
                if (tagPos != -1) question = question.substring(0, tagPos).trim();
                page.question = question;
                int backPos = text.indexOf("It is classified as");
                int linkStart = 0;
                for (;;)
                {
                    int linkPos = text.indexOf("<a ", linkStart);
                    if (linkPos == -1 || (backPos != -1 && linkPos >= backPos)) break;
                    int linkEnd = text.indexOf("</a>", linkPos);
                    if (linkEnd == -1) malfunction("Cannot find end of link.");
                    String a = text.substring(linkPos, linkEnd + 4);
                    int urlPos = a.indexOf(" href=\"");
                    if (urlPos == -1) malfunction("Cannot find beginning of URL in link.");
                    urlPos += 7;
                    int urlEnd = a.indexOf("\"", urlPos);
                    if (urlEnd == -1) malfunction("Cannot find end of URL in link.");
                    int labelPos = a.indexOf(">");
                    if (labelPos == -1) malfunction("Cannot find beginning of label in link.");
                    labelPos++;
                    int labelEnd = a.indexOf("<", labelPos);
                    if (labelEnd == -1) malfunction("Cannot find end of URL in link.");;
                    String label = StringHelper.replaceAll(a.substring(labelPos, labelEnd), "&nbsp;", " ").trim();
                    WebLink link = new WebLink();
                    link.address = followLink(url, a.substring(urlPos, urlEnd));
                    link.label = label;
                    linkStart = linkEnd + 4;
                    if (label.length() != 0)
                    {
                        page.links.add(link);
                    }
                }
            }
            referer = url;
        }
        catch (Exception ex)
        {
            throw new RuntimeException(ex);
        }
        return page;
    }

    private String followLink(String fromURL, String linkURL)
    {
        if (linkURL.contains("://"))
        {
            // Absolute URL
            return linkURL;
        }
        else
        {
            // Relative URL
            int lastSlash = fromURL.lastIndexOf('/');
            if (lastSlash == -1) malfunction("Cannot find last slash in URL.");
            if (linkURL.startsWith("/")) linkURL = linkURL.substring(1);
            return fromURL.substring(0, lastSlash) + "/" + linkURL;
        }
    }

    private void logDebug(String message)
    {
        if (DEBUG)
        {
            System.out.println("DEBUG " + message);
        }
    }

    private void malfunction(String reason)
    {
        robot.fail("Malfunction! Need input! " + reason);
    }
}
