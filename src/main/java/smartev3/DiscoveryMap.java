package smartev3;

import java.util.*;

public class DiscoveryMap
{
    public static int DEFAULT_CELL_SIZE = 10;
    public static int DEFAULT_GRID_SIZE = 100;

    private int cellSize; // length and width of square cells (centimetres)
    private int gridSize; // number of cells in each dimension: must be even
    private Cell[][] gridOfCells; // matrix of size * size
    private ArrayList<Cell> listOfCells = new ArrayList<Cell>();

    public Point robotLocation;
    public Cell targetCell;
    public ArrayList<Cell> cellsToTarget = new ArrayList<Cell>();
    public ArrayList<Point> pointsToTarget = new ArrayList<Point>();

    public DiscoveryMap()
    {
        this(DEFAULT_CELL_SIZE, DEFAULT_GRID_SIZE);
    }

    public DiscoveryMap(int cellSize, int gridSize)
    {
        if (gridSize % 2 != 0) throw new RuntimeException("Map grid size (" + gridSize + " ) is not even!");
        this.cellSize = cellSize;
        this.gridSize = gridSize;
        Cell[][] cells = new Cell[gridSize][gridSize];
        double y = cellSize * gridSize / 2.0;
        for (int row = 0; row < gridSize; row++)
        {
            double x = -(cellSize * gridSize / 2.0);
            for (int column = 0; column < gridSize; column++)
            {
                Cell cell = new Cell();
                cell.row = row;
                cell.column = column;
                cell.top = y;
                cell.bottom = y + cellSize;
                cell.left = x;
                cell.right = x + cellSize;
                cell.midpoint = new Point(x + cellSize / 2, y + cellSize / 2);
                cells[row][column] = cell;
                listOfCells.add(cell);
                x += cellSize;
            }
            y -= cellSize;
        }
        for (int row = 0; row < gridSize; row++)
        {
            for (int column = 0; column < gridSize; column++)
            {
                Cell cell = cells[row][column];
                addNeighbours(cell, cells);
            }
        }
        this.gridOfCells = cells;
        this.robotLocation = new Point(0, 0);
        this.selfTest();
    }

    public void addObstacles(ProximityMap proximityMap, int relativeTo)
    {
        Point location = robotLocation;
        int minAngle = proximityMap.minimumAngle;
        int maxAngle = proximityMap.maximumAngle;
        for (int pass = 1; pass <= 2; pass++)
        {
            for (int angle = minAngle; angle <= maxAngle; angle++)
            {
                float distance = proximityMap.getDistanceAtAngle(angle);
                if (!Float.isNaN(distance))
                {
                    int bearing = angle + relativeTo;
                    Point point = location.move(bearing, distance);
                    Cell cell = cellWithPoint(point);
                    if (cell != null)
                    {
                        if (pass == 1)
                        {
                            // First pass: set "known" flags, clear obstacles.
                            cell.setKnown(true);
                            cell.obstacles.clear();
                            for (float d = 1; d <= distance; d++)
                            {
                                Point p = location.move(bearing, d);
                                Cell c = cellWithPoint(p);
                                if (c != null)
                                {
                                    // Must "see through" cell c to see obstacle
                                    // so mark cell c as "known".
                                    c.setKnown(true);
                                    c.obstacles.clear();
                                }
                            }
                        }
                        else
                        {
                            // Second pass: set obstacles.
                            cell.obstacles.add(point);
                        }
                    }
                }
            }
        }
    }

    public Cell cellWithPoint(Point point)
    {
        int cellSize = this.cellSize;
        int gridSize = this.gridSize;
        int halfGrid = gridSize / 2;
        int row = 1 + (int)(halfGrid - point.y / cellSize);
        int column = (int)(halfGrid + point.x / cellSize);
        if (row < 0 || row >= gridSize || column < 0 || column >= gridSize)
        {
            return null;
        }
        else
        {
            return gridOfCells[row][column];
        }
    }

    public void robotMoved(float direction, float distance)
    {
        this.robotLocation = this.robotLocation.move(direction, distance);
    }

    public void chooseTarget()
    {
        for (Cell cell : listOfCells)
        {
            cell.setUnsafe(false);
            cell.setVisited(false);
            cell.pathToSource = null;
        }
        targetCell = null;
        cellsToTarget.clear();
        pointsToTarget.clear();
        Cell source = cellWithPoint(robotLocation);
        if (source == null)
        {
            return;
        }
        source.setVisited(true);
        ArrayList<Cell> lastRound = new ArrayList<Cell>();
        ArrayList<Cell> thisRound = new ArrayList<Cell>();
        source.setVisited(true);
        lastRound.add(source);
        OUTER:
        for (int round = 1;; round++)
        {
            for (Cell c : lastRound)
            {
                for (Cell n : c.neighbours)
                {
                    if (!n.isVisited())
                    {
                        n.setVisited(true);
                        if (!n.anyNeighbourHasObstacles())
                        {
                            thisRound.add(n);
                            n.pathToSource = c;
                            if (!n.isKnown())
                            {
                                targetCell = n;
                                break OUTER;
                            }
                        }
                    }
                }
            }
            ArrayList<Cell> temp = lastRound;
            lastRound = thisRound;
            thisRound = temp;
            thisRound.clear();
        }
        if (targetCell != null)
        {
            for (Cell p = targetCell; p != null; p = p.pathToSource)
            {
                cellsToTarget.add(p);
                pointsToTarget.add(p.midpoint);
            }
            Collections.reverse(cellsToTarget);
        }
    }

    public void smoothenPath()
    {
        for (;;)
        {
            boolean changed = false;
            int n = cellsToTarget.size();
            for (int i = 0; i + 2 < n; i++)
            {
                Cell a = cellsToTarget.get(i);
                Cell c = cellsToTarget.get(i + 2);
                Point ap = a.midpoint, cp = c.midpoint;
                System.out.println("compare segments " + a + " @ " + ap + ", " + c + " @ " + cp);
                double ax = ap.x, ay = ap.y;
                double cx = cp.x, cy = cp.y;
                double dx = cx - ax;
                double dy = cy - ay;
                double nr = Math.abs(c.row - a.row);
                double nc = Math.abs(c.column - a.column);
                double m = cellSize * Math.max(nr, nc);
                double ix = dx / m;
                double iy = dy / m;
                // System.out.println("    ix = " + SmartRobot.formatFixed1(ix) + ", iy = " + SmartRobot.formatFixed1(iy));
                boolean drop = true;
                for (int j = 1; j < m; j++)
                {
                    double jx = ax + j * ix;
                    double jy = ay + j * iy;
                    Cell z = cellWithPoint(new Point(jx, jy));
                    /*
                    System.out.println("        jx = " + SmartRobot.formatFixed1(jx)
                     + ", jy = " + SmartRobot.formatFixed1(jy) + ", z = " + z
                     + "; left = " + SmartRobot.formatFixed1(z.left)
                     + " right = " + SmartRobot.formatFixed1(z.right)
                     + " top = " + SmartRobot.formatFixed1(z.top)
                     + " bottom = " + SmartRobot.formatFixed1(z.bottom));
                     */
                    if (z != null)
                    {
                        if (z.anyNeighbourHasObstacles())
                        {
                            System.out.println("Cell " + z + " has neighbour obstacles");
                            drop = false;
                            break;
                        }
                    }
                    else
                    {
                        System.out.println("*** no cell for point: " + z);
                        drop = false;
                        break;
                    }
                }
                if (drop)
                {
                    System.out.println("dropping segment " + cellsToTarget.get(i + 1));
                    cellsToTarget.remove(i + 1);
                    changed = true;
                    break;
                }
            }
            if (!changed) break;
        }
        pointsToTarget.clear();
        for (Cell c : cellsToTarget)
        {
            pointsToTarget.add(c.midpoint);
        }
    }

    public ConsoleGrid consoleGrid()
    {
        return consoleGrid("Discovery Map");
    }

    public ConsoleGrid consoleGrid(String label)
    {
        int rows = ConsoleGrid.ROWS;
        int cols = ConsoleGrid.COLS;
        char[][] array = ConsoleGrid.newArray(rows, cols);
        int mapSize = this.gridSize;
        for (int row = 0; row < rows; row++)
        {
            for (int col = 0; col < cols; col++)
            {
                // Not all map cells map to console grid cells.
                array[row][col] = ' ';
            }
        }
        for (int mapRow = 0; mapRow < mapSize; mapRow++)
        {
            int row = (int)(mapRow * ((double)rows / mapSize));
            for (int mapCol = 0; mapCol < mapSize; mapCol++)
            {
                int col = (int)(mapCol * ((double)cols / mapSize));
                Cell cell = gridOfCells[mapRow][mapCol];
                if (!cell.isKnown())
                {
                    array[row][col] = '?'; // unknown cell
                }
            }
        }
        for (int mapRow = 0; mapRow < mapSize; mapRow++)
        {
            int row = (int)(mapRow * ((double)rows / mapSize));
            for (int mapCol = 0; mapCol < mapSize; mapCol++)
            {
                int col = (int)(mapCol * ((double)cols / mapSize));
                Cell cell = gridOfCells[mapRow][mapCol];
                if (!cell.obstacles.isEmpty())
                {
                    array[row][col] = 'O'; // obstacle cell
                }
            }
        }
        for (Cell cell : cellsToTarget)
        {
            int row = (int)(cell.row * ((double)rows / mapSize));
            int col = (int)(cell.column * ((double)cols / mapSize));
            array[row][col] = cell == targetCell ? '$' : ':';
        }
        Point atPoint = robotLocation;
        Cell atCell = cellWithPoint(atPoint);
        if (atCell != null)
        {
            int row = (int)(atCell.row * ((double)rows / mapSize));
            int col = (int)(atCell.column * ((double)cols / mapSize));
            array[row][col] = '#';
        }
        return new ConsoleGrid(array, label);
    }

    public ConsoleGrid nearObjects()
    {
        return nearObjects("Near Objects");
    }

    public ConsoleGrid nearObjects(String label)
    {
        int rows = 21;
        int cols = rows * 2;
        char[][] array = ConsoleGrid.newArray(rows, cols);
        int mapSize = this.gridSize;
        for (int row = 0; row < rows; row++)
        {
            for (int col = 0; col < cols; col++)
            {
                // Not all map cells map to console grid cells.
                array[row][col] = ' ';
            }
        }
        Cell robotCell = cellWithPoint(robotLocation);
        if (robotCell != null)
        {
            int robotRow = robotCell.row;
            int robotCol = robotCell.column;
            int fromRow = Math.max(0, robotRow - 10);
            int toRow = Math.min(mapSize - 1, robotRow + 10);
            int fromCol = Math.max(0, robotCol - 10);
            int toCol = Math.min(mapSize - 1, robotCol + 10);
            for (int mapRow = fromRow; mapRow <= toRow; mapRow++)
            {
                int cgRow = mapRow - (robotRow - 10);
                for (int mapCol = fromCol; mapCol <= toCol; mapCol++)
                {
                    int cgCol = mapCol - (robotCol - 10);
                    Cell cell = gridOfCells[mapRow][mapCol];
                    if (!cell.isKnown())
                    {
                        array[cgRow][2 * cgCol] = '?'; // unknown cell
                        array[cgRow][2 * cgCol + 1] = '?';
                    }
                    if (!cell.obstacles.isEmpty())
                    {
                        array[cgRow][2 * cgCol] = '('; // obstacle cell
                        array[cgRow][2 * cgCol + 1] = ')';
                    }
                }
            }
            for (Cell cell : cellsToTarget)
            {
                int mapRow = cell.row;
                int mapCol = cell.column;
                if (mapRow >= fromRow && mapRow <= toRow
                    && mapCol >= fromCol && mapCol <= toCol)
                {
                    int cgRow = mapRow - (robotRow - 10);
                    int cgCol = mapCol - (robotCol - 10);
                    char marker = cell == targetCell ? '$' : ':';
                    array[cgRow][2 * cgCol] = marker; // breadcrumb cell
                    array[cgRow][2 * cgCol + 1] = marker;
                }
            }
            array[10][20] = '#'; // robot cell
            array[10][21] = '#';
        }
        return new ConsoleGrid(array, label);
    }

    private void addNeighbours(Cell cell, Cell[][] cells)
    {
        int row = cell.row;
        int column = cell.column;
        for (int i = -1; i <= 1; i++)
        {
            for (int j = -1; j <= 1; j++)
            {
                addNeighbour(cell, row + i, column + j, cells);
            }
        }
    }

    private void addNeighbour(Cell cell, int row, int column, Cell[][] cells)
    {
        if (row == cell.row && column == cell.column)
        {
            return; // Cell is not its own neighbour.
        }
        if (row >= 0 && row < gridSize && column >= 0 && column < gridSize)
        {
            cell.neighbours.add(cells[row][column]);
        }
    }

    private void selfTest()
    {
        int n = gridSize, i = 2, j = n - 2;
        selfTest(gridOfCells[i][i]);
        selfTest(gridOfCells[j][j]);
    }

    private void selfTest(Cell cell)
    {
        Point mp = cell.midpoint;
        Cell mc = cellWithPoint(mp);
        if (mc != cell)
        {
            throw new RuntimeException("DiscoveryMap cell " + cell + " midpoint maps to cell " + mc + "!");
        }
    }
}
