import org.xml.sax.Attributes;
import java.util.HashSet;

/**
 * Created by Loren on 7/30/2016.
 */
public class GraphNode {

    private Long id;

    private double[] loc = new double[2];
    private String name;
    private Attributes attributes;
    private HashSet<Connection> connections = new HashSet<>();

    private Double shortest;

    public GraphNode(Attributes attributes) {
        this.attributes = attributes;
        loc[0] = Double.parseDouble(attributes.getValue("lon"));
        loc[1] = Double.parseDouble(attributes.getValue("lat"));
        id = Long.valueOf(attributes.getValue("id"));

    }

    public Long getID() {
        return id;
    }

    public double[] getLoc() {
        return loc;
    }

    public Attributes attributes() {
        return attributes;
    }

    public HashSet<Connection> getConnections() {
        return connections;
    }

    public void addConnection(GraphNode x) {
        Double distance = getDistanceTo(x.getLoc()[0], x.getLoc()[1]);
        this.connections.add(new Connection(this.getID(), x.getID(), distance));
    }

    public Double getDistanceTo(Double lon, Double lat) {
        return Math.sqrt(Math.pow((this.getLoc()[0] - lon), 2)
                + Math.pow((this.getLoc()[1] - lat), 2));

    }

    public void setName(String s) {
        name = s;
    }

    public String getName() {
        return name;
    }

}
