package smartev3;

import java.util.*;

/**
 * A square cell with side of 50 cm (half metre).
 */
public class Cell
{
    private boolean known = false, unsafe = false, visited = false;

    public int row, column;

    /**
     * Coordinates of the bounding box for this cell (centimetres from origin of containing map; may be negative).
     */
    public double top, bottom, left, right;

    public Point midpoint;

    public Cell pathToSource;

    /**
     * Neighbouring cells.
     */
    public final ArrayList<Cell> neighbours = new ArrayList<Cell>();

    /**
     * Locations of obstacles within this cell.
     * Points are relative to the origin of the map containing the cell.
     */
    public final ArrayList<Point> obstacles = new ArrayList<Point>();

    public boolean anyNeighbourHasObstacles()
    {
        for (Cell n : neighbours)
        {
            if (n.obstacles.size() != 0)
            {
                return true;
            }
        }
        return false;
    }

    public Cell anyNeighbourIsUnknown()
    {
        for (Cell n : neighbours)
        {
            if (!n.known)
            {
                return n;
            }
        }
        return null;
    }

    public boolean isKnown()
    {
        return this.known || obstacles.size() != 0;
    }

    public void setKnown(boolean known)
    {
        this.known = known;
    }

    public boolean isUnsafe()
    {
        return this.unsafe;
    }

    public void setUnsafe(boolean unsafe)
    {
        this.unsafe = unsafe;
    }

    public boolean isVisited()
    {
        return this.visited;
    }

    public void setVisited(boolean visited)
    {
        this.visited = visited;
    }

    public String toString()
    {
        return "(r=" + row + ",c=" + column + ")";
    }
}
