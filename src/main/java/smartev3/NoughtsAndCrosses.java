package smartev3;

import java.util.*;

public class NoughtsAndCrosses
{
    private final static char NOUGHTS = 'O';
    private final static char CROSSES = 'X';

    private static String SPACES = "            ";

    private final static String[] O_LINES =
    {
        "    OOOO    ",
        "  OO    OO  ",
        " OO      OO "
    };

    private static final String[] X_LINES =
    {
        " XX      XX ",
        "   XX  XX   ",
        "     XX     "
    };

    private int[] board = new int[10];
    private int userPlayer = -1;
    private char userSymbol = '?';
    private int robotPlayer = -1;
    private char robotSymbol = '?';
    private int turnsPlayed = 0;
    private SmartRobot robot = null;
    private boolean firstGame = true;
    private int startPlayer;
    private int lastPlayer;
    private int winningPlayer;
    private boolean gameStopped;

    public NoughtsAndCrosses(SmartRobot robot)
    {
        this.robot = robot;
        displayBoard();
        String screenType = robot.useTelevision ? "TV" : "computer";
        robot.say("OK, we'll play Noughts and Crosses using the " + screenType
            + " screen to display the game board.");
        SmartRobot.InputMode mode = robot.inputMode();
        String sayOrSignal = mode == SmartRobot.InputMode.SPEECH
            ? "say"
            : "signal";
        if (robot.askYesNoQuestion("Please " + sayOrSignal
            + " Yes to play Noughts, or " + sayOrSignal + " No to play Crosses."))
        {
            userSymbol = NOUGHTS;
            robotSymbol = CROSSES;
            robot.say("OK, you will play Noughts, and I will play Crosses.");
        }
        else
        {
            userSymbol = CROSSES;
            robotSymbol = NOUGHTS;
            robot.say("OK, I will play Noughts, and you will play Crosses.");
        }
        startPlayer = 1;
        if (robot.askYesNoQuestion("Would you like to take the first turn?"))
        {
            userPlayer = 1;
        }
        else
        {
            userPlayer = 2;
        }
        robotPlayer = 3 - userPlayer;
        turnsPlayed = 0;
    }

    public void play()
    {
        while (true)
        {
            playOnce();
            if (gameStopped)
            {
                return;
            }
            if (!robot.askYesNoQuestion("Would you like to play another game of Noughts and Crosses?"))
            {
                return;
            }
            board = new int[10];
            userPlayer = 3 - userPlayer;
            robotPlayer = 3 - userPlayer;
            turnsPlayed = 0;
            lastPlayer = 0;
            winningPlayer = 0;
            displayBoard();
            if (startPlayer == userPlayer)
            {
                robot.say("OK, this time you can take the first turn.");
            }
            else
            {
                robot.say("OK, this time I will take the first turn.");
            }
            firstGame = false;
        }
    }

    public void playOnce()
    {
        while (true)
        {
            nextTurn();
            if (gameStopped)
            {
                robot.say("OK, we'll stop playing Noughts and Crosses.");
                return;
            }
            displayBoard();
            if (winningPlayer == robotPlayer)
            {
                robot.say("I win!");
                return;
            }
            else if (winningPlayer == userPlayer)
            {
                robot.say("You win!");
                return;
            }
            else if (turnsPlayed == 9)
            {
                robot.say("The game is a draw.");
                return;
            }
            if (lastPlayer == robotPlayer)
            {
                robot.say("Now it's your turn.");
            }
            else
            {
                robot.say("Now it's my turn.");
            }
        }
    }

    private void nextTurn()
    {
        if (turnsPlayed % 2 == 0)
        {
            oddTurn();
        }
        else
        {
            evenTurn();
        }
        turnsPlayed = turnsPlayed + 1;
    }

    private void oddTurn()
    {
        if (startPlayer == robotPlayer)
        {
            robotTurn();
        }
        else
        {
            userTurn();
        }
    }

    private void evenTurn()
    {
        if (startPlayer != robotPlayer)
        {
            robotTurn();
        }
        else
        {
            userTurn();
        }
    }

    private void robotTurn() 
    {
        lastPlayer = robotPlayer;
        String pieceName = robotSymbol == NOUGHTS ? "nought" : "cross";
        List<Integer> blockUserWin = new ArrayList<Integer>();
        for (int position = 1; position <= 9; position++)
        {
            if (board[position] == 0)
            {
                board[position] = userPlayer;
                checkForWinner(userPlayer);
                int winner = winningPlayer;
                board[position] = 0;
                winningPlayer = 0;
                if (userPlayer == winner)
                {
                    blockUserWin.add(position);
                }
            }
        }
        Set<Integer> triedPositions = new HashSet<Integer>();
        while (true)
        {
            boolean triedAllPositions = triedPositions.size() == 9;
            int position = robot.randomChoice(1, 9);
            if (triedAllPositions && blockUserWin.size() != 0)
            {
                // Randomly block one of the user's winning options.
                position = blockUserWin.get(robot.randomChoice(0, blockUserWin.size() - 1));
            }
            if (board[position] == 0)
            {
                board[position] = robotPlayer;
                checkForWinner(robotPlayer);
                if (winningPlayer != 0 || triedAllPositions)
                {
                    String posName = nameOfPosition(position);
                    robot.sleepForSeconds(3);
                    robot.say("I will place my " + pieceName + " in the " + posName + " position.");
                    return;
                }
                board[position] = 0;
            }
            triedPositions.add(position);
        }
    }

    private void userTurn()
    {
        lastPlayer = userPlayer;
        String pieceName = userSymbol == NOUGHTS ? "nought" : "cross";
        if (robot.inputModeAllowsMultiChoice())
        {
            List<Integer> positions = new ArrayList<Integer>(9);
            List<String> choices = new ArrayList<String>(9);
            for (int position = 1; position <= 9; position++)
            {
                if (board[position] == 0)
                {
                    positions.add(position);
                    choices.add(nameOfPosition(position));
                }
            }
            if (positions.size() == 1)
            {
                int finalPosition = positions.get(0);
                robot.say("That leaves the " + nameOfPosition(finalPosition) + " position for your turn.");
                board[finalPosition] = userPlayer;
                checkForWinner(userPlayer);
                return;
            }
            if (firstGame && turnsPlayed == 0)
            {
                StringBuilder choiceHelp = new StringBuilder("When I ask you for a position to place your " + pieceName
                    + ", your response should be one of the following: ");
                int n = choices.size();
                for (int i = 0; i < n; i++)
                {
                    String choice = choices.get(i);
                    if (i > 0) choiceHelp.append(i + 1 == n ? " or " : ", ");
                    choiceHelp.append(choice);
                }
                choiceHelp.append(".");
                robot.say(choiceHelp.toString());
            }
            WordAliases aliases = new WordAliases()
                .addTop().addCentre().addBottom().addLeft().addRight();
            choices.add("stop game");
            int choice = robot.askUserToChoose("Please say which position you wish to place your " + pieceName + " in.",
                choices, aliases);
            if (choice == choices.size())
            {
                gameStopped = true;
                return;
            }
            int positionChoice = positions.get(choice - 1);
            board[positionChoice] = userPlayer;
            checkForWinner(userPlayer);
            return;
        }
        ArrayList<Integer> rowChoices = new ArrayList<Integer>();
        if (board[1] == 0 || board[2] == 0 || board[3] == 0)
        {
            rowChoices.add(1); // Ask about top row first.
        }
        if (board[7] == 0 || board[8] == 0 || board[9] == 0)
        {
            rowChoices.add(3); // Ask about bottom row next.
        }
        if (board[4] == 0 || board[5] == 0 || board[6] == 0)
        {
            rowChoices.add(2); // Ask about centre row last..
        }
        int rowChoiceCount = rowChoices.size();
        if (rowChoiceCount == 0)
        {
            throw new IllegalStateException();
        }
        int rowChoice = rowChoices.get(0);
        if (rowChoiceCount == 1)
        {
            robot.say("Only the " + nameOfRow(rowChoice) + " row remains available for your turn.");
        }
        else
        {
            for (int rowChoiceIndex = 0; rowChoiceIndex < rowChoiceCount; rowChoiceIndex++)
            {
                rowChoice = rowChoices.get(rowChoiceIndex);
                if (rowChoiceIndex + 1 == rowChoiceCount)
                {
                    robot.say("OK, that leaves the " + nameOfRow(rowChoice) + " row for your turn.");
                    break;
                }
                else if (robot.askYesNoQuestion("Would you like to play your " + pieceName
                    + " in the " + nameOfRow(rowChoice) + " row?"))
                {
                    break;
                }
                // Otherwise continue loop for next choice.
            }
        }
        int offset = (rowChoice - 1) * 3;
        ArrayList<Integer> positionChoices = new ArrayList<Integer>();
        for (int column = 1; column <= 3; column++)
        {
            if (board[offset + column] == 0)
            {
                positionChoices.add(offset + column);
            }
        }
        int positionChoiceCount = positionChoices.size();
        if (positionChoiceCount == 0)
        {
            throw new IllegalStateException();
        }
        int positionChoice = positionChoices.get(0);
        if (positionChoiceCount == 1)
        {
            robot.say("Only the " + nameOfPosition(positionChoice) + " position remains available for your turn.");
        }
        else
        {
            for (int positionChoiceIndex = 0; positionChoiceIndex < positionChoiceCount; positionChoiceIndex++)
            {
                positionChoice = positionChoices.get(positionChoiceIndex);
                if (positionChoiceIndex + 1 == positionChoiceCount)
                {
                    robot.say("OK, that leaves the " + nameOfPosition(positionChoice) + " position for your turn.");
                    break;
                }
                else if (robot.askYesNoQuestion("Would you like to play your " + pieceName
                    + " in the " + nameOfPosition(positionChoice) + " position?"))
                {
                    break;
                }
                // Otherwise continue loop for next choice.
            }
        }
        board[positionChoice] = userPlayer;
        checkForWinner(userPlayer);
    }

    private void checkForWinner(int player)
    {
        if ((board[1] == player && board[2] == player && board[3] == player) 
            || (board[4] == player && board[5] == player && board[6] == player)
            || (board[7] == player && board[8] == player && board[9] == player)
            || (board[1] == player && board[4] == player && board[7] == player)
            || (board[2] == player && board[5] == player && board[8] == player)
            || (board[3] == player && board[6] == player && board[9] == player)
            || (board[1] == player && board[5] == player && board[9] == player)
            || (board[3] == player && board[5] == player && board[7] == player))
        {
            winningPlayer = player;
        }
    }

    private String nameOfRow(int row)
    {
        switch (row)
        {
            case 1: return "top";
            case 2: return "centre";
            case 3: return "bottom";
            default: throw new IllegalArgumentException("row = " + row);
        }
    }

    private String nameOfColumn(int column)
    {
        switch (column)
        {
            case 1: return "left";
            case 2: return "centre";
            case 3: return "right";
            default: throw new IllegalArgumentException("column = " + column);
        }
    }

    private String nameOfPosition(int position)
    {
        if (position == 5)
        {
            return "centre";
        }
        else
        {
            int row = 1 + ((position - 1) / 3);
            int column = 1 + ((position - 1) % 3);
            return nameOfRow(row) + " " + nameOfColumn(column);
        }
    }

    private void displayBoard()
    {
        robot.clearScreen();
        System.out.println("\n\n\n\n");
        for (int row = 1; row <= 3; row++)
        {
            int offset = (row - 1) * 3;
            for (int line = 1; line <= 5; line++)
            {
                System.out.print(SPACES);
                System.out.print(SPACES);
                for (int column = 1; column <= 3; column++)
                {
                    char symbol = positionSymbol(offset + column);
                    if (symbol == ' ')
                    {
                        System.out.print(SPACES);
                    }
                    else
                    {
                        String[] lines = (symbol == NOUGHTS) ? O_LINES : X_LINES;
                        String text = line == 5 ? lines[0] : (line == 4 ? lines[1] : lines[line - 1]);
                        System.out.print(text);
                    }
                    if (column < 3) System.out.print('|');
                }
                System.out.println("");
            }
            if (row < 3)
            {
                System.out.print(SPACES);
                System.out.print(SPACES);
                System.out.println("------------+------------+------------");
            }
        }
        System.out.println("\n\n\n\n");
    }

    private char positionSymbol(int pos)
    {
        int p = board[pos];
        if (p == userPlayer)
        {
            return userSymbol;
        }
        else if (p == robotPlayer)
        {
            return robotSymbol;
        }
        else
        {
            return ' ';
        }
    }
}
