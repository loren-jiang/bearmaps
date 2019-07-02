import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;
import java.util.*;


/**
 *  Parses OSM XML files using an XML SAX parser. Used to construct the graph of roads for
 *  pathfinding, under some constraints.
 *  See OSM documentation on
 *  <a href="http://wiki.openstreetmap.org/wiki/Key:highway">the highway tag</a>,
 *  <a href="http://wiki.openstreetmap.org/wiki/Way">the way XML element</a>,
 *  <a href="http://wiki.openstreetmap.org/wiki/Node">the node XML element</a>,
 *  and the java
 *  <a href="https://docs.oracle.com/javase/tutorial/jaxp/sax/parsing.html">SAX parser tutorial</a>.
 *  @author Alan Yao
 */
public class MapDBHandler extends DefaultHandler {
    /**
     * Only allow for non-service roads; this prevents going on pedestrian streets as much as
     * possible. Note that in Berkeley, many of the campus roads are tagged as motor vehicle
     * roads, but in practice we walk all over them with such impunity that we forget cars can
     * actually drive on them.
     */
    private static final Set<String> ALLOWED_HIGHWAY_TYPES = new HashSet<>(Arrays.asList
            ("motorway", "trunk", "primary", "secondary", "tertiary", "unclassified",
                    "residential", "living_street", "motorway_link", "trunk_link", "primary_link",
                    "secondary_link", "tertiary_link"));
    private String activeState = "";
    private String currId = "";
    private final GraphDB g;

    private ArrayList<String> wayNodes = new ArrayList<>();
    private boolean containsHighway = false;

    private HashMap<Long, GraphNode> nodeMap = new HashMap<Long, GraphNode>();
    private Trie pointsOfInterest = new Trie();


    public MapDBHandler(GraphDB g) {
        this.g = g;
    }

    public HashMap<Long, GraphNode> getNodeMap() {
        return nodeMap;
    }

    public Trie getPointsOfInterest() {
        return pointsOfInterest;
    }

    /**
     * Called at the beginning of an element. Typically, you will want to handle each element in
     * here, and you may want to track the parent element.
     * @param uri The Namespace URI, or the empty string if the element has no Namespace URI or
     *            if Namespace processing is not being performed.
     * @param localName The local name (without prefix), or the empty string if Namespace
     *                  processing is not being performed.
     * @param qName The qualified name (with prefix), or the empty string if qualified names are
     *              not available. This tells us which element we're looking at.
     * @param attributes The attributes attached to the element. If there are no attributes, it
     *                   shall be an empty Attributes object.
     * @throws SAXException Any SAX exception, possibly wrapping another exception.
     * @see Attributes
     */

    // Loren's notes: might be a good idea to make a hashmap of the OSM file nodes.
    // The ways only contain node reference
    // IDs so we need to some way to access the nodes to make our graph.

    @Override
    public void startElement(String uri, String localName, String qName, Attributes attributes)
            throws SAXException {
        /* Some example code on how you might begin to parse XML files. */
        if (qName.equals("node")) {
            activeState = "node";
            currId = attributes.getValue("id");
            nodeMap.put(Long.valueOf(currId), new GraphNode(attributes));
        } else if (qName.equals("way")) {
            activeState = "way";
            currId = attributes.getValue("id");
            //System.out.println("Beginning a way...");
        } else if (qName.equals("relation")) {
            activeState = "relation";
        } else if (activeState.equals("way") && qName.equals("tag")) {
            String k = attributes.getValue("k");
            String v = attributes.getValue("v");
            if (k.equals("highway")) {
                containsHighway = true;
            }
            if (k.equals("highway") && !ALLOWED_HIGHWAY_TYPES.contains(v)) {
                wayNodes = new ArrayList<>();
            }
            //System.out.println("Tag with k=" + k + ", v=" + v + ".");
        } else if (activeState.equals("way") && qName.equals("nd")) {
            String ref = attributes.getValue("ref");
            wayNodes.add(ref);
            //System.out.println("Ref id of nodes: " + ref);
        } else if (activeState.equals("node") && qName.equals("tag") && attributes.getValue("k")
                .equals("name")) {
            //System.out.println("Node with name: " + attributes.getValue("v"));
//            System.out.println(attributes.getValue("v"));
            nodeMap.get(Long.valueOf(currId)).setName(attributes.getValue("v"));
            //add to Trie as well; add names
            pointsOfInterest.addLocation(attributes.getValue("v"),
                    nodeMap.get(Long.valueOf(currId)));
        }
    }

    /**
     * Receive notification of the end of an element. You may want to take specific terminating
     * actions here, like finalizing vertices or edges found.
     * @param uri The Namespace URI, or the empty string if the element has no Namespace URI or
     *            if Namespace processing is not being performed.
     * @param localName The local name (without prefix), or the empty string if Namespace
     *                  processing is not being performed.
     * @param qName The qualified name (with prefix), or the empty string if qualified names are
     *              not available.
     * @throws SAXException  Any SAX exception, possibly wrapping another exception.
     */
    @Override
    public void endElement(String uri, String localName, String qName) throws SAXException {
        if (qName.equals("way")) {
            currId = "";
            if (!containsHighway) {
                wayNodes = new ArrayList<>();
            }
            for (int i = 0; i < wayNodes.size() && wayNodes.size() != 1; i++) {
                Long id = Long.valueOf(wayNodes.get(i));
                //nodeNames.add(nodeMap.get(id).attributes().g)
                if (i == 0) {
                    //nodeMap.get(id).getConnections().add(new Connection(id, wayNodes.get(i + 1)));
                    nodeMap.get(id).addConnection(nodeMap.get(Long.valueOf(wayNodes.get(i + 1))));
                } else if (i == wayNodes.size() - 1) {
                    //nodeMap.get(id).getConnections().add(new Connection(id, wayNodes.get(i - 1)));
                    nodeMap.get(id).addConnection(nodeMap.get(Long.valueOf(wayNodes.get(i - 1))));
                } else {
                    //nodeMap.get(id).getConnections().add(new Connection(id, wayNodes.get(i + 1)));
                    nodeMap.get(id).addConnection(nodeMap.get(Long.valueOf(wayNodes.get(i + 1))));
                    //nodeMap.get(id).getConnections().add(new Connection(id, wayNodes.get(i - 1)));
                    nodeMap.get(id).addConnection(nodeMap.get(Long.valueOf(wayNodes.get(i - 1))));
                }
            }
            wayNodes = new ArrayList<>(); //resetting wayNodes for each way
            containsHighway = false; //resetting containsHighway
            //System.out.println("Finishing a way...");
        }
    }

    public int getConnectedSize() {
        int count = 0;
        for (Long id : nodeMap.keySet()) {
            if (!nodeMap.get(id).getConnections().isEmpty()) {
                count += 1;
            }
        }
        return count;
    }

    public void removeDisconnects() {
        Iterator<Map.Entry<Long, GraphNode>> iter = nodeMap.entrySet().iterator();
        while (iter.hasNext()) {
            Map.Entry<Long, GraphNode> entry = iter.next();
            if (entry.getValue().getConnections().isEmpty()) {
                iter.remove();
            }
        }
    }
}

