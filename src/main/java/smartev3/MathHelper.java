package smartev3;

public class MathHelper
{
    public static double degreesToRadians(double angle)
    {
        return angle * (Math.PI / 180);
    }

    public static double radiansToDegrees(double angle)
    {
        return angle / (Math.PI / 180);
    }

    public static double distanceBetweenPolarDegrees(double degrees1, double distance1,
        double degrees2, double distance2)
    {
        return distanceBetweenPolarRadians
        (
            degreesToRadians(degrees1), distance1,
            degreesToRadians(degrees2), distance2
        );
    }

    public static double distanceBetweenPolarRadians(double radians1, double distance1,
        double radians2, double distance2)
    {
        // https://math.tutorvista.com/geometry/distance-formula-for-polar-coordinates.html
        return Math.sqrt((distance1 * distance1) + (distance2 * distance2)
            - (2 * distance1 * distance2 * Math.cos(radians1 - radians2)));
    }

    public static double distanceBetweenTwoPoints(double x1, double y1, double x2, double y2)
    {
        double dx = x1 - x2;
        double dy = y1 - y2;
        return Math.hypot(dx, dy);
    }
}
