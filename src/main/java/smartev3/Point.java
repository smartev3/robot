package smartev3;

public class Point
{
    public double x, y;

    public Point(double x, double y)
    {
        this.x = x;
        this.y = y;
    }

    public double distanceTo(Point other)
    {
        return MathHelper.distanceBetweenTwoPoints(this.x, this.y, other.x, other.y);
    }

    /**
     * Return a new point relative to this point.
     * @param angle Direction to the new point (degrees).
     * @param distance Distance to the new point (centimetres).
     * @return
     */
    public Point move(double angle, double distance)
    {
        double angleRadians = MathHelper.degreesToRadians(angle);
        double newX = this.x + distance * Math.sin(angleRadians);
        double newY = this.y + distance * Math.cos(angleRadians);
        return new Point(newX, newY);
    }

    public String toString()
    {
        return "(x=" + SmartRobot.formatFixed1(x) + ",y=" + SmartRobot.formatFixed1(y) + ")";
    }
}
