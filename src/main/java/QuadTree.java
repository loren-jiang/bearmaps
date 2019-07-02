import java.awt.*;
import java.util.ArrayList;
import java.util.Collections;

/**
 * Created by Loren on 7/14/2016.
 */
public class QuadTree {

    private QuadTreeNode root;

    private final int depth = 7;

    public QuadTree() {
        double[] upperLeft = {MapServer.ROOT_ULLON, MapServer.ROOT_ULLAT};
        double[] lowerRight = {MapServer.ROOT_LRLON, MapServer.ROOT_LRLAT};
        root = new QuadTreeNode("root", upperLeft, lowerRight);
        makeTilesHelper(this.root, 0);
        //root = null;
    }

    public QuadTree(QuadTreeNode q) {
        root = q;
    }

    public int size() {
        if (root != null) {
            return root.size();
        }
        return 0;
    }


    /**
     * makeTiles takes the root and makes the tiles in a QuadTree structure for
     * the given images to the specified depth
     */
    private QuadTree makeTiles() {
        double[] upperLeft = {MapServer.ROOT_ULLON, MapServer.ROOT_ULLAT};
        double[] lowerRight = {MapServer.ROOT_LRLON, MapServer.ROOT_LRLAT};
        QuadTree t = new QuadTree(new QuadTreeNode("root", upperLeft, lowerRight));
        makeTilesHelper(t.root, 0);
        return t;
    }

    /**
     * makeTiles takes the root and makes the tiles in a QuadTree structure for
     * the given images; this is the helper method
     */
    private void makeTilesHelper(QuadTreeNode node, int d) {
        //String name = "";
        if (d == this.depth) {
            return;
        } else { //figure out long/lat calculations, naming concatination, pixel density
            double midLon = (node.getUpperLeft()[0] + node.getLowerRight()[0]) / 2;
            double midLat = (node.getUpperLeft()[1] + node.getLowerRight()[1]) / 2;

            /* ROOT_ULLAT = 37.892195547244356, ROOT_ULLON = -122.2998046875,
            ROOT_LRLAT = 37.82280243352756, ROOT_LRLON = -122.2119140625;
            */

            //case to cut off root from name
            String name = node.getFileName();
            if (d == 0) name = name.substring(4);

            for (int k = 0; k < node.getChildren().length; k++) {
                if (k == 0) { // the first child node
                    double[] newUpperLeft = node.getUpperLeft();
                    double[] newLowerRight = {midLon, midLat};
                    node.setChild(k, new QuadTreeNode(name + "1", newUpperLeft, newLowerRight));
                } else if (k == 1) { // the second child node
                    double[] newUpperLeft = {midLon, node.getUpperLeft()[1]};
                    double[] newLowerRight = {node.getLowerRight()[0], midLat};
                    node.setChild(k, new QuadTreeNode(name + "2", newUpperLeft, newLowerRight));
                } else if (k == 2) { // the third child node
                    double[] newUpperLeft = {node.getUpperLeft()[0], midLat};
                    double[] newLowerRight = {midLon, node.getLowerRight()[1]};
                    node.setChild(k, new QuadTreeNode(name + "3", newUpperLeft, newLowerRight));
                } else { // the fourth child node
                    double[] newUpperLeft = {midLon, midLat};
                    double[] newLowerRight = node.getLowerRight();

                    node.setChild(k, new QuadTreeNode(name + "4", newUpperLeft, newLowerRight));
                }
                makeTilesHelper(node.getChild(k), d + 1);
            }
        }
    }

    public ArrayList<QuadTreeNode> raster(double ullon, double lrlon,
                                          double ullat, double lrlat, double resolution) {
        //ArrayList<QuadTreeNode> startRaster = new ArrayList<QuadTreeNode>();
        //startRaster.add(root);
        queryNodes = new ArrayList<>();
        imageFinder(root, ullon, lrlon, ullat, lrlat, resolution);
        Collections.sort(queryNodes);
        return queryNodes;
    }

    private ArrayList<QuadTreeNode> queryNodes;

    //make sure covering images in between;
    private void imageFinder(QuadTreeNode curr, double ullon, double lrlon,
                             double ullat, double lrlat, double resolution) {
        if (curr.getPixelDistLong() <= resolution || curr.getFileName().length() == 7) {
            if (curr.isCorner(ullon, lrlon, ullat, lrlat)
                    || curr.isEdge(ullon, lrlon, ullat, lrlat)
                    || curr.isInside(ullon, lrlon, ullat, lrlat)) {
                queryNodes.add(curr);
            }

        } else {
            for (QuadTreeNode a: curr.getChildren()) {
                if (a.isCorner(ullon, lrlon, ullat, lrlat)
                        || a.isEdge(ullon, lrlon, ullat, lrlat)
                        || a.isInside(ullon, lrlon, ullat, lrlat)) {
                    imageFinder(a, ullon, lrlon, ullat, lrlat, resolution);
                }
            }
        }
        //return null;
    }

    public static void main(String[] args) {
        QuadTree q = new QuadTree();
        q.raster(-122.241632, -122.24053, 37.87655, 37.87548, (122.241632 - 122.24053) / 892);
        for (QuadTreeNode curr: q.queryNodes) {
            System.out.println(curr.getFileName());
        }
        System.out.println(q.queryNodes.size());
    }

    /*public void print(QuadTreeNode curr){
        System.out.println(curr.getFileName());

        for (QuadTreeNode b: curr.getChildren()) {
            System.out.println(b.getFileName());
        }
        for (QuadTreeNode b: curr.getChildren()) {
            if (b.getChild(1) != null) print(b);
        }


    }*/

}
