import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.util.*;
import java.util.List;
import java.io.InputStream;

/* Maven is used to pull in these dependencies. */
import com.google.gson.Gson;

import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.MemoryCacheImageOutputStream;

import static spark.Spark.*;

/**
 * This MapServer class is the entry point for running the JavaSpark web server for the BearMaps
 * application project, receiving API calls, handling the API call processing, and generating
 * requested images and routes.
 * @author Alan Yao
 */
public class MapServer {
    /**
     * The root upper left/lower right longitudes and latitudes represent the bounding box of
     * the root tile, as the images in the img/ folder are scraped.
     * Longitude == x-axis; latitude == y-axis.
     */
    public static final double ROOT_ULLAT = 37.892195547244356, ROOT_ULLON = -122.2998046875,
            ROOT_LRLAT = 37.82280243352756, ROOT_LRLON = -122.2119140625;
    /** Each tile is 256x256 pixels. */
    public static final int TILE_SIZE = 256;
    /** HTTP failed response. */
    private static final int HALT_RESPONSE = 403;
    /** Route stroke information: typically roads are not more than 5px wide. */
    public static final float ROUTE_STROKE_WIDTH_PX = 5.0f;
    /** Route stroke information: Cyan with half transparency. */
    public static final Color ROUTE_STROKE_COLOR = new Color(108, 181, 230, 200);
    /** The tile images are in the IMG_ROOT folder. */
    private static final String IMG_ROOT = "img/";
    /**
     * The OSM XML file path. Downloaded from <a href="http://download.bbbike.org/osm/">here</a>
     * using custom region selection.
     **/
    private static final String OSM_DB_PATH = "berkeley.osm";
    /**
     * Each raster request to the server will have the following parameters
     * as keys in the params map accessible by,
     * i.e., params.get("ullat") inside getMapRaster(). <br>
     * ullat -> upper left corner latitude,<br> ullon -> upper left corner longitude, <br>
     * lrlat -> lower right corner latitude,<br> lrlon -> lower right corner longitude <br>
     * w -> user viewport window width in pixels,<br> h -> user viewport height in pixels.
     **/
    private static final String[] REQUIRED_RASTER_REQUEST_PARAMS = {"ullat", "ullon", "lrlat",
        "lrlon", "w", "h"};
    /**
     * Each route request to the server will have the following parameters
     * as keys in the params map.<br>
     * start_lat -> start point latitude,<br> start_lon -> start point longitude,<br>
     * end_lat -> end point latitude, <br>end_lon -> end point longitude.
     **/
    private static final String[] REQUIRED_ROUTE_REQUEST_PARAMS = {"start_lat", "start_lon",
        "end_lat", "end_lon"};
    /* Define any static variables here. Do not define any instance variables of MapServer. */
    private static GraphDB g;

    /**
     * Place any initialization statements that will be run before the server main loop here.
     * Do not place it in the main function. Do not place initialization code anywhere else.
     * This is for testing purposes, and you may fail tests otherwise.
     **/
    public static void initialize() {
        g = new GraphDB(OSM_DB_PATH); //OSM_DB_PATH);
    }

    private static int getHerokuAssignedPort() {
        ProcessBuilder processBuilder = new ProcessBuilder();
        if (processBuilder.environment().get("PORT") != null) {
            return Integer.parseInt(processBuilder.environment().get("PORT"));
        }
        return 4567; //return default port if heroku-port isn't set (i.e. on localhost)
    }
    public static void main(String[] args) {
        initialize();
        port(getHerokuAssignedPort());
        staticFileLocation("/page");
        /* Allow for all origin requests (since this is not an authenticated server, we do not
         * care about CSRF).  */
        before((request, response) -> {
            response.header("Access-Control-Allow-Origin", "*");
            response.header("Access-Control-Request-Method", "*");
            response.header("Access-Control-Allow-Headers", "*");
        });

        /* Define the raster endpoint for HTTP GET requests. I use anonymous functions to define
         * the request handlers. */
        get("/raster", (req, res) -> {
            HashMap<String, Double> rasterParams =
                    getRequestParams(req, REQUIRED_RASTER_REQUEST_PARAMS);
            /* Required to have valid raster params */
            validateRequestParameters(rasterParams, REQUIRED_RASTER_REQUEST_PARAMS);

            /* Create the Map for return parameters. */
            Map<String, Object> rasteredImgParams = new HashMap<>();
            /* getMapRaster() does almost all the work for this API call */
            BufferedImage im = getMapRaster(rasterParams, rasteredImgParams);
            /* Check if we have routing parameters. */
            HashMap<String, Double> routeParams =
                    getRequestParams(req, REQUIRED_ROUTE_REQUEST_PARAMS);
            /* If we do, draw the route too. */
            if (hasRequestParameters(routeParams, REQUIRED_ROUTE_REQUEST_PARAMS)) {
                findAndDrawRoute(routeParams, rasteredImgParams, im);
            }
            /* On an image query success, add the image data to the response */
            if (rasteredImgParams.containsKey("query_success")
                    && (Boolean) rasteredImgParams.get("query_success")) {
                ByteArrayOutputStream os = new ByteArrayOutputStream();
                writeJpgToStream(im, os);
                String encodedImage = Base64.getEncoder().encodeToString(os.toByteArray());
                rasteredImgParams.put("b64_encoded_image_data", encodedImage);
                os.flush();
                os.close();
            }
            /* Encode response to Json */
            Gson gson = new Gson();
            return gson.toJson(rasteredImgParams);
        });

        /* Define the API endpoint for search */
        get("/search", (req, res) -> {
            Set<String> reqParams = req.queryParams();
            String term = req.queryParams("term");
            Gson gson = new Gson();
            /* Search for actual location data. */
            if (reqParams.contains("full")) {
                List<Map<String, Object>> data = getLocations(term);
                return gson.toJson(data);
            } else {
                /* Search for prefix matching strings. */
                List<String> matches = getLocationsByPrefix(term);
                return gson.toJson(matches);
            }
        });

        /* Define map application redirect */
        get("/", (request, response) -> {
            response.redirect("/map.html", 301);
            return true;
        });
    }

    /**
     * Check if the computed parameter map matches the required parameters on length.
     */
    private static boolean hasRequestParameters(
            HashMap<String, Double> params, String[] requiredParams) {
        return params.size() == requiredParams.length;
    }

    /**
     * Validate that the computed parameters matches the required parameters.
     * If the parameters do not match, halt.
     */
    private static void validateRequestParameters(
            HashMap<String, Double> params, String[] requiredParams) {
        if (params.size() != requiredParams.length) {
            halt(HALT_RESPONSE, "Request failed - parameters missing.");
        }
    }

    /**
     * Return a parameter map of the required request parameters.
     * Requires that all input parameters are doubles.
     * @param req HTTP Request
     * @param requiredParams TestParams to validate
     * @return A populated map of input parameter to it's numerical value.
     */
    private static HashMap<String, Double> getRequestParams(
            spark.Request req, String[] requiredParams) {
        Set<String> reqParams = req.queryParams();
        HashMap<String, Double> params = new HashMap<>();
        for (String param : requiredParams) {
            if (reqParams.contains(param)) {
                try {
                    params.put(param, Double.parseDouble(req.queryParams(param)));
                } catch (NumberFormatException e) {
                    e.printStackTrace();
                    halt(HALT_RESPONSE, "Incorrect parameters - provide numbers.");
                }
            }
        }
        return params;
    }

    /**
     * Write a <code>BufferedImage</code> to an <code>OutputStream</code>. The image is written as
     * a lossy JPG, but with the highest quality possible.
     * @param im Image to be written.
     * @param os Stream to be written to.
     */
    static void writeJpgToStream(BufferedImage im, OutputStream os) {
        ImageWriter writer = ImageIO.getImageWritersByFormatName("jpg").next();
        ImageWriteParam param = writer.getDefaultWriteParam();
        param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
        param.setCompressionQuality(1.0F); // Highest quality of jpg possible
        writer.setOutput(new MemoryCacheImageOutputStream(os));
        try {
            writer.write(im);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Handles raster API calls, queries for tiles and rasters the full image. <br>
     * <p>
     *     The rastered photo must have the following properties:
     *     <ul>
     *         <li>Has dimensions of at least w by h, where w and h are the user viewport width
     *         and height.</li>
     *         <li>The tiles collected must cover the most longitudinal distance per pixel
     *         possible, while still covering less than or equal to the amount of
     *         longitudinal distance per pixel in the query box for the user viewport size. </li>
     *         <li>Contains all tiles that intersect the query bounding box that fulfill the
     *         above condition.</li>
     *         <li>The tiles must be arranged in-order to reconstruct the full image.</li>
     *     </ul>
     *     Additional image about the raster is returned and is to be included in the Json response.
     * </p>
     * @param inputParams Map of the HTTP GET request's query parameters - the query bounding box
     *                    and the user viewport width and height.
     * @param rasteredImageParams A map of parameters for the Json response as specified:
     * "raster_ul_lon" -> Double, the bounding upper left longitude of the rastered image <br>
     * "raster_ul_lat" -> Double, the bounding upper left latitude of the rastered image <br>
     * "raster_lr_lon" -> Double, the bounding lower right longitude of the rastered image <br>
     * "raster_lr_lat" -> Double, the bounding lower right latitude of the rastered image <br>
     * "raster_width"  -> Integer, the width of the rastered image <br>
     * "raster_height" -> Integer, the height of the rastered image <br>
     * "depth"         -> Integer, the 1-indexed quadtree depth of the nodes of the rastered image.
     * Can also be interpreted as the length of the numbers in the image string. <br>
     * "query_success" -> Boolean, whether an image was successfully rastered. <br>
     * @return a <code>BufferedImage</code>, which is the rastered result.
     * @see #REQUIRED_RASTER_REQUEST_PARAMS
     */

    //instance variable for lazy loading
    private static HashMap<String, BufferedImage> buffered = new HashMap<>();

    public static BufferedImage getMapRaster(Map<String, Double> inputParams,
                                             Map<String, Object> rasteredImageParams) {
        QuadTree t = new QuadTree();
        Double resolution = Math.abs((inputParams.get("ullon")
                - inputParams.get("lrlon")) / inputParams.get("w"));
        ArrayList<QuadTreeNode> images = t.raster(inputParams.get("ullon"),
                inputParams.get("lrlon"), inputParams.get("ullat"),
                inputParams.get("lrlat"), resolution);
        int width = (int) Math.round((Math.abs(images.get(0).getUpperLeft()[0]
                - images.get(images.size() - 1).getLowerRight()[0])
                / images.get(0).getPixelDistLong()));
        int height = (int) Math.round((Math.abs(images.get(0).getUpperLeft()[1]
                - images.get(images.size() - 1).getLowerRight()[1])
                / images.get(0).getPixelDistLat()));

        //Placing info into the rasteredImageParams
        rasteredImageParams.put("raster_ul_lon", images.get(0).getUpperLeft()[0]);
        rasteredImageParams.put("raster_ul_lat", images.get(0).getUpperLeft()[1]);
        rasteredImageParams.put("raster_lr_lon", images.get(images.size() - 1).getLowerRight()[0]);
        rasteredImageParams.put("raster_lr_lat", images.get(images.size() - 1).getLowerRight()[1]);
        rasteredImageParams.put("raster_width", width);
        rasteredImageParams.put("raster_height", height);
        rasteredImageParams.put("depth", images.get(0).getFileName().length());
        rasteredImageParams.put("query_success", true);

        //http://stackoverflow.com/questions/3922276/
        // how-to-combine-multiple-pngs-into-one-big-png-file

        BufferedImage result = new BufferedImage(
                width, height, //work these out
                BufferedImage.TYPE_INT_RGB);
        Graphics d = result.getGraphics();

        int x = 0;
        int y = 0;

        for (QuadTreeNode curr : images) {
            String path = "img/" + curr.getFileName() + ".png";
//            InputStream in = InputStream.class.getClassLoader().getResourceAsStream(path);
            BufferedImage bi  = getImageStream(path);
//            BufferedImage bi = getImage(path);
            d.drawImage(bi, x, y, null);
            x += 256;
            if (x >= result.getWidth()) {
                x = 0;
                y += bi.getHeight();
            }
        }
        return result;
    }

    /**
     * Searches for the shortest route satisfying the input request parameters, and returns a
     * <code>List</code> of the route's node ids. <br>
     * The route should start from the closest node to the start point and end at the closest node
     * to the endpoint. Distance is defined as the euclidean distance between two points
     * (lon1, lat1) and (lon2, lat2).
     * If <code>im</code> is not null, draw the route onto the image by drawing lines in between
     * adjacent points in the route. The lines should be drawn using ROUTE_STROKE_COLOR,
     * ROUTE_STROKE_WIDTH_PX, BasicStroke.CAP_ROUND and BasicStroke.JOIN_ROUND.
     * @param routeParams Params collected from the API call. Members are as
     *                    described in REQUIRED_ROUTE_REQUEST_PARAMS.
     * @param rasterImageParams parameters returned from the image rastering.
     * @param im The rastered map image to be drawn on.
     * @return A List of node ids from the start of the route to the end.
     */
    public static List<Long> findAndDrawRoute(Map<String, Double> routeParams,
                                              Map<String, Object> rasterImageParams,
                                              BufferedImage im) {
        Double distanceToStart = Double.MAX_VALUE;
        Double distanceToEnd = Double.MAX_VALUE;
        GraphNode start = null;
        GraphNode end = null;

        for (Long key: g.getMaphandler().getNodeMap().keySet()) {
            GraphNode curr = g.getMaphandler().getNodeMap().get(key);

            Double currStartDistance =
                    curr.getDistanceTo(routeParams.get("start_lon"),
                            routeParams.get("start_lat"));
            Double currEndDistance =
                    curr.getDistanceTo(routeParams.get("end_lon"),
                            routeParams.get("end_lat"));

            if (currStartDistance < distanceToStart) {
                start = curr;
                distanceToStart = currStartDistance;
            }

            if (currEndDistance < distanceToEnd) {
                end = curr;
                distanceToEnd = currEndDistance;
            }
        }

        Graphics2D currGraphic = null; 
        List<Long> path = MapServer.shortestPath(start, end);
        if (im != null) {
            currGraphic = (Graphics2D) im.getGraphics();
            currGraphic.setStroke(new BasicStroke(MapServer.ROUTE_STROKE_WIDTH_PX,
                    BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            currGraphic.setColor(ROUTE_STROKE_COLOR);
            Double xStandard = (Double) rasterImageParams.get("raster_ul_lon");
            Double yStandard = (Double) rasterImageParams.get("raster_ul_lat");
            Double xPixelDistance = Math.abs((Double) rasterImageParams.get("raster_lr_lon")
                    - (Double) rasterImageParams.get("raster_ul_lon"))
                    / ((Integer) rasterImageParams.get("raster_width")).doubleValue();
            Double yPixelDistance = Math.abs((Double) rasterImageParams.get("raster_lr_lat")
                    - (Double) rasterImageParams.get("raster_ul_lat"))
                    / ((Integer) rasterImageParams.get("raster_height")).doubleValue();

            for (int i = 0; i < path.size() - 1; i++) {
                GraphNode curr = g.getMaphandler().getNodeMap().get(path.get(i));
                GraphNode next = g.getMaphandler().getNodeMap().get(path.get(i + 1));
                int x1 = (int) ((curr.getLoc()[0] - xStandard) / xPixelDistance);
                int y1 = (int) ((yStandard - curr.getLoc()[1]) / yPixelDistance);
                int x2 = (int) ((next.getLoc()[0] - xStandard) / xPixelDistance);
                int y2 = (int) ((yStandard - next.getLoc()[1]) / yPixelDistance);
                currGraphic.drawLine(x1, y1, x2, y2);
            }

        }

        return path;
    }

    private static ArrayList<Long> shortestPath(GraphNode start, GraphNode end) {

        HashMap<GraphNode, GraphNode> prev = new HashMap<>();
        HashMap<Long, Double> shortestPath = new HashMap<>();
        HashSet<Long> visited = new HashSet<>();

        PriorityQueue<GraphNode> fringe = new PriorityQueue<>((g1, g2) -> {
            return Double.compare(shortestPath.get(g1.getID())
                    + g1.getDistanceTo(end.getLoc()[0], end.getLoc()[1]),
                    shortestPath.get(g2.getID())
                            + g2.getDistanceTo(end.getLoc()[0], end.getLoc()[1]));
        }); //define lambda comparator on shortestPath.get(node) + h(n)


        fringe.add(start);
        shortestPath.put(start.getID(), 0.0);

        while (!fringe.isEmpty()) {
            GraphNode curr = fringe.poll();
            if (curr == end) break;
            if (!visited.contains(curr.getID())) {
                visited.add(curr.getID());
                HashSet<Connection> currConnections = curr.getConnections();
                for (Connection c : currConnections) {
                    GraphNode currEnd = g.getMaphandler().getNodeMap().get(c.to());
                    Double gN = shortestPath.get(curr.getID()) + c.getDistance();
                    if (!visited.contains(c.to())) {
                        if (shortestPath.get(c.to()) == null) {
                            prev.put(currEnd, curr);
                            shortestPath.put(c.to(), gN);
                            fringe.add(currEnd);
                        } else {
                            if (shortestPath.get(c.to()) > gN) {
                                prev.put(currEnd, curr);
                                shortestPath.put(c.to(), gN);
                                fringe.add(currEnd);
                            }
                        }
                    }
                }
            }
        }

        ArrayList<Long> path = new ArrayList<>();
        path.add(end.getID());
        GraphNode next = end;
        while (!Objects.equals(next.getID(), start.getID())) {
            next = prev.get(next);
            path.add(0, next.getID());
        }
        return path;


    }

    /**
     * In linear time, collect all the names of OSM locations that prefix-match the query string.
     * @param prefix Prefix string to be searched for. Could be any case, with our without
     *               punctuation.
     * @return A <code>List</code> of the full names of locations whose cleaned name matches the
     * cleaned <code>prefix</code>.
     */
    public static List<String> getLocationsByPrefix(String prefix) {
        return g.getMaphandler().getPointsOfInterest().lookupPrefix(prefix);
    }

    /**
     * Collect all locations that match a cleaned <code>locationName</code>, and return
     * information about each node that matches.
     * @param locationName A full name of a location searched for.
     * @return A list of locations whose cleaned name matches the
     * cleaned <code>locationName</code>, and each location is a map of parameters for the Json
     * response as specified: <br>
     * "lat" -> Number, The latitude of the node. <br>
     * "lon" -> Number, The longitude of the node. <br>
     * "name" -> String, The actual name of the node. <br>
     * "id" -> Number, The id of the node. <br>
     */
    public static List<Map<String, Object>> getLocations(String locationName) {
        List<Map<String, Object>> results = new LinkedList<>();
        for (GraphNode curr: g.getMaphandler().getPointsOfInterest().lookup(locationName)) {
            HashMap<String, Object> currInfo = new HashMap<>();
            currInfo.put("lat", curr.getLoc()[1]);
            currInfo.put("lon", curr.getLoc()[0]);
            currInfo.put("name", curr.getName());
            currInfo.put("id", curr.getID()); //longValue for formatting reasons
            results.add(currInfo);
        }
        return results;
    }

    private static BufferedImage getImage(String imgPath) {
        BufferedImage bi = null;
        if (buffered.containsKey(imgPath)) {
            bi = buffered.get(imgPath);
        } else {
            try {
                bi = ImageIO.read(new File(imgPath));
                buffered.put(imgPath, bi);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return bi;
    }

        private static BufferedImage getImageStream(String imgPath) {
            InputStream in = null;
            try {
                in = MapServer.class.getClassLoader().getResourceAsStream(imgPath);
            } catch (Exception e) {
                e.printStackTrace();
            }

            BufferedImage bi = null;
            try {
                bi = ImageIO.read(in);
            } catch (IOException e) {
                e.printStackTrace();
            }
            return bi;
        }

}
