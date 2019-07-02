/**
 * Created by Loren on 7/14/2016.
 */
public class QuadTreeNode implements Comparable<QuadTreeNode> {

    private String fileName;
    private double[] upperLeft; //long, lat
    private double[] lowerRight;
    private QuadTreeNode[] children = new QuadTreeNode[4];
    private final double imgWidth = MapServer.TILE_SIZE;

    public QuadTreeNode(String fileName, double[] upperLeft, double[] lowerRight) {
        this.fileName = fileName;
        this.upperLeft = upperLeft;
        this.lowerRight = lowerRight;
    }

    public QuadTreeNode(String fileName, double[] upperLeft, double[] lowerRight,
                        QuadTreeNode one, QuadTreeNode two, QuadTreeNode three, QuadTreeNode four) {
        this.fileName = fileName;
        this.upperLeft = upperLeft;
        this.lowerRight = lowerRight;
        children[0] = one;
        children[1] = two;
        children[2] = three;
        children[3] = four;
    }

    public QuadTreeNode() {
        fileName = null;
        upperLeft = lowerRight = null;
    }

//    public static void main (String[] args) {
//        double[] upperLeft = {-3,1};
//        double[] lowerRight = {-1,-3};
//        QuadTreeNode q = new QuadTreeNode("test", upperLeft, lowerRight);
//        System.out.println(q.isCorner(-2, 2, 2, -2));
//    }

    public double[] getUpperLeft() {
        return upperLeft;
    }

    public double[] getLowerRight() {
        return lowerRight;
    }

    public String getFileName() {
        return fileName;
    }

    public double getPixelDistLong() {
        return (lowerRight[0] - upperLeft[0]) / imgWidth;
    }

    public double getPixelDistLat() {
        return (upperLeft[1] - lowerRight[1]) / imgWidth;
    }

    public QuadTreeNode getNode(int k) {
        return children[k];
    }

    public QuadTreeNode[] getChildren() {
        return children;
    }

    public QuadTreeNode getChild(int k) {
        return children[k];
    }

    public boolean isCorner(double ullon, double lrlon, double ullat, double lrlat) {
        if ((ullon > getUpperLeft()[0] && ullon < getLowerRight()[0])
                || (lrlon > getUpperLeft()[0] && lrlon < getLowerRight()[0])) {
            if ((lrlat < getUpperLeft()[1] && lrlat > getLowerRight()[1])
                    || (ullat < getUpperLeft()[1] && ullat > getLowerRight()[1])) {
                return true;
            }
        }
        return false;
    }

    public boolean isEdge(double ullon, double lrlon, double ullat, double lrlat) {
        if ((upperLeft[0] <= ullon && ullon <= lowerRight[0])
                || (upperLeft[0] <= lrlon && lrlon <= lowerRight[0])) {
            if (lrlat <= lowerRight[1] && upperLeft[1] <= ullat) {
                return true;
            }
        }

        if ((lowerRight[1] <= ullat && ullat <= upperLeft[1])
                || (lowerRight[1] <= lrlat && lrlat <= upperLeft[1])) {
            if (ullon <= upperLeft[0] && lowerRight[0] <= lrlon) {
                return true;
            }
        }
        return false;
    }

    public boolean isInside(double ullon, double lrlon, double ullat, double lrlat) {
        if (lrlat <= lowerRight[1] && upperLeft[1] <= ullat
                && ullon <= upperLeft[0] && lowerRight[0] <= lrlon) {
            return true;
        }
        return false;
    }

    public void setFileName(String name) {
        fileName = name;
    }

    public void setUpperLeft(double x, double y) {
        upperLeft[0] = x;
        upperLeft[1] = y;
    }

    public void setLowerRight(double x, double y) {
        lowerRight[0] = x;
        lowerRight[1] = y;
    }

    public void setChild(int k, QuadTreeNode node) {
        children[k] = node;
    }

    public void setChildren(QuadTreeNode one, QuadTreeNode two,
                            QuadTreeNode three, QuadTreeNode four) {
        children[0] = one;
        children[1] = two;
        children[2] = three;
        children[3] = four;
    }

    public int size() {
        int total = 0;
        if (getChildren()[0] == null) {
            return 1;
        } else {
            for (QuadTreeNode a : getChildren()) {
                total += a.size();
            }
        }
        return total + 1;
    }


    @Override
    /* Compare such that order of node 1's children is
    ** 11, 12, 13, 14 least to greatest
     */
    public int compareTo(QuadTreeNode o) {
        if (this.upperLeft[1] < o.upperLeft[1]) {
            return 1;
        } else if (this.upperLeft[1] > o.upperLeft[1]) {
            return -1;
        } else {
            if (this.upperLeft[0] < o.upperLeft[0]) {
                return -1;
            } else if (this.upperLeft[0] > o.upperLeft[0]) {
                return 1;
            } else {
                return 0;
            }
        }
    }
}
